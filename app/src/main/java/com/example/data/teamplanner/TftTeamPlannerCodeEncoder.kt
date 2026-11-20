package com.example.data.teamplanner

import android.content.Context
import com.example.data.model.MetaComp
import com.example.data.model.TftEnvironment
import com.example.data.network.RiotApiClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class TeamPlannerChampionEntry(
    val characterId: String,
    val displayName: String,
    val plannerCodeInt: Int,
    val slotHex: String,
    val isVariant: Boolean = false
)

data class TeamPlannerSetMapping(
    val setKey: String,
    val setNumber: Int?,
    val usesVersion02: Boolean,
    val entries: List<TeamPlannerChampionEntry>,
    val lookupByNormalizedKey: Map<String, TeamPlannerChampionEntry>
)

data class TeamPlannerCatalog(
    val sets: Map<String, TeamPlannerSetMapping>,
    val isFromRiotCommunityDragon: Boolean,
    val loadedAtMillis: Long = System.currentTimeMillis()
)

data class TeamPlannerCodeResult(
    val code: String,
    val setKey: String,
    val matchedChampions: List<String>,
    val skippedUnits: List<String>,
    val isVerifiedRiotMapping: Boolean,
    val errorMessage: String? = null
) {
    val isSuccess: Boolean
        get() = code.isNotBlank() && isVerifiedRiotMapping && matchedChampions.isNotEmpty()
}

/**
 * Generates official Riot in-game TFT Team Planner import codes for LIVE (Set 18) and PBE (Set 19):
 *
 *   Version 02 (Set 13+ / Set 18 LIVE / Set 19 PBE):
 *   `02` + (10 slots × 3 lowercase hex digits of Riot's 12-bit `team_planner_code`, padded with `000`) + `<TFTSetKey>`
 *
 *   Legacy Version 01 (only if an older set omits `team_planner_code`):
 *   `01` + (10 slots × 2 lowercase hex digits of 1-based alphabetical index, padded with `00`) + `<TFTSetKey>`
 *
 * Champion codes and exact set identifiers are fetched directly from Riot's official
 * CommunityDragon `tftchampions-teamplanner.json` (LIVE `latest` & PBE `pbe` with CDN mirror fallback)
 * and disk-cached per environment with strict set-number validation so LIVE and PBE never cross-contaminate.
 */
object TftTeamPlannerCodeEncoder {

    const val VERSION_02_PREFIX = "02"
    const val VERSION_01_PREFIX = "01"
    const val MAX_PLANNER_SLOTS = 10
    const val EMPTY_SLOT_HEX_V2 = "000"
    const val EMPTY_SLOT_HEX_V1 = "00"

    const val CDRAGON_LIVE_TEAMPLANNER_URL =
        "https://raw.communitydragon.org/latest/plugins/rcp-be-lol-game-data/global/default/v1/tftchampions-teamplanner.json"
    const val CDRAGON_LIVE_CHAMPIONS_FALLBACK_URL =
        "https://raw.communitydragon.org/latest/plugins/rcp-be-lol-game-data/global/default/v1/tftchampions.json"

    const val CDRAGON_PBE_TEAMPLANNER_URL =
        "https://raw.communitydragon.org/pbe/plugins/rcp-be-lol-game-data/global/default/v1/tftchampions-teamplanner.json"
    const val CDRAGON_PBE_CHAMPIONS_FALLBACK_URL =
        "https://raw.communitydragon.org/pbe/plugins/rcp-be-lol-game-data/global/default/v1/tftchampions.json"
    const val CDRAGON_PBE_TFT_DATA_URL =
        "https://raw.communitydragon.org/pbe/cdragon/tft/en_us.json"

    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/131.0 Mobile Safari/537.36"

    private const val LIVE_CACHE_FILE = "tft_teamplanner_live_v4.json"
    private const val PBE_CACHE_FILE = "tft_teamplanner_pbe_v4.json"
    private const val PRELOAD_SET18_ASSET = "set18_catalog_preload.json"
    private const val PRELOAD_SET19_ASSET = "set19_pbe_catalog_preload.json"
    private const val CACHE_TTL_MS = 6L * 60L * 60L * 1000L // 6 hours

    private val VALID_V02_CODE_REGEX = Regex("""^02[0-9a-fA-F]{30}(?:TFTSet|TFT)[A-Za-z0-9_]+$""")
    private val VALID_V01_CODE_REGEX = Regex("""^01[0-9a-fA-F]{20}(?:TFTSet|TFT)[A-Za-z0-9_]+$""")
    private val EXPLICIT_SET_NUMBER_REGEX = Regex("""(?:TFTSet|Set[\s_-]*)(\d+)""", RegexOption.IGNORE_CASE)
    private val FLEXIBLE_SET_KEY_NUMBER_REGEX = Regex("""^(?:TFTSet|Set|TFT|DA|PT)[\s_-]*(\d+)(?:[^0-9].*)?$""", RegexOption.IGNORE_CASE)
    private val PURE_SET_NUMBER_REGEX = Regex("""^(\d{1,2})$""")
    private val CHAR_ID_SET_NUMBER_REGEX = Regex("""^(?:TFT|DA|PT)[\s_]*(\d{1,2})_|^DA_[A-Za-z]+(\d{2})(?:_|$)""", RegexOption.IGNORE_CASE)
    private val NON_PLAYABLE_UNIT_REGEX = Regex(
        """(?:dummy|prop|minion|summon|golem|training|chest|turret|herald|carousel|portal|crystal|tree|lifeblossom|stonebark|protector)""",
        RegexOption.IGNORE_CASE
    )

    private val memoryCatalogs = ConcurrentHashMap<TftEnvironment, TeamPlannerCatalog>()
    @Volatile
    private var preloadApiToDisplayCache: Map<String, String>? = null

    /**
     * Variant suffixes on multi-form champions (like Set 18 Avatar Lux trait forms
     * or Set 19 Seraphine upgraded forms) that must not shadow the base champion entry
     * in Team Planner lookup.
     */
    private val variantKeywords = listOf(
        "blackthorn", "blossom", "coven", "elderwood", "fae", "inferno",
        "moonbeam", "primal", "sunbeam", "lunar", "solar",
        "risingstar", "superstar"
    )

    /**
     * Explicit bidirectional aliases between in-game display names and Riot internal `character_id`
     * tokens across LIVE (Set 18: Enchanted Wilds) and PBE (Set 19: Prismatic Tour).
     */
    private val championLookupAliases: Map<String, List<String>> = mapOf(
        // Set 18 LIVE (Enchanted Wilds / Riftbeast / DA_18_ internal names)
        "pebbles" to listOf("sentry", "pebbles", "da18sentry", "tft18sentry", "tft18pebbles"),
        "sentry" to listOf("pebbles", "sentry", "da18sentry", "tft18sentry", "tft18pebbles"),
        "mamabeak" to listOf("crimsonraptor", "mamabeak", "dacrimsonraptor18", "tft18crimsonraptor", "tft18mamabeak"),
        "crimsonraptor" to listOf("mamabeak", "crimsonraptor", "dacrimsonraptor18", "tft18crimsonraptor"),
        "scuttlecrab" to listOf("scuttlecrab", "scuttle", "dascuttlecrab18", "tft18scuttlecrab"),
        "cinderling" to listOf("cinderling", "dacinderling18", "tft18cinderling"),
        "brambleback" to listOf("brambleback", "dabrambleback18", "tft18brambleback"),
        "murkwolf" to listOf("murkwolf", "damurkwolf18", "tft18murkwolf"),
        "sentinel" to listOf("sentinel", "dasentinel18", "tft18sentinel"),
        "krug" to listOf("krug", "dakrug18", "tft18krug"),
        "elderdragon" to listOf("elderdragon", "da18elderdragon", "tft18elderdragon"),
        "gnar" to listOf("gnar", "gnarsmall", "da18gnarsmall", "tft18gnar", "tft18gnarsmall", "tft19gnar"),
        "lux" to listOf("lux", "luxbase", "dalux18base", "tft18lux", "tft19lux"),
        "masteryi" to listOf("masteryi", "masteryiad", "da18masteryiad", "tft18masteryi"),
        "kogmaw" to listOf("kogmaw", "kogmawad", "dakogmaw18ad", "tft18kogmaw"),
        "nidalee" to listOf("nidalee", "nidaleeap", "danidalee18ap", "tft18nidalee", "tft19nidalee"),
        "gromp" to listOf("gromp", "grompap", "dagromp18ap", "tft18gromp"),
        "akali" to listOf("akali", "akaliad", "da18akaliad", "tft18akali", "tft19akali", "akalikda"),
        "reksai" to listOf("reksai", "da18reksai", "tft18reksai"),
        "khazix" to listOf("khazix", "da18khazix", "tft18khazix"),
        "leblanc" to listOf("leblanc", "da18leblanc", "tft18leblanc"),
        "fiddlesticks" to listOf("fiddlesticks", "dafiddlesticks18", "tft18fiddlesticks"),
        "karma" to listOf("karma", "dakarma18", "tft18karma"),
        "amumu" to listOf("amumu", "daamumu18", "tft18amumu", "tft19amumu"),
        "taric" to listOf("taric", "dataric18", "tft18taric", "tft19taric"),
        "draven" to listOf("draven", "dadraven18", "tft18draven", "tft19draven"),
        "vi" to listOf("vi", "davi18", "tft18vi", "tft19vi"),

        // Set 19 PBE (Prismatic Tour / Riot internal character_ids)
        "wukong" to listOf("monkeyking", "wukong", "tft19monkeyking", "tft19wukong"),
        "monkeyking" to listOf("wukong", "monkeyking", "tft19monkeyking", "tft19wukong"),
        "akalikda" to listOf("akalikda", "tft19akalikda", "akali", "tft19akali"),
        "akalitruedamage" to listOf("akalitruedamage", "tft19akalitruedamage", "akalitd", "tft19akalitd"),
        "renataglasc" to listOf("renataglasc", "renata", "tft19renataglasc", "tft19renata"),
        "renata" to listOf("renata", "renataglasc", "tft19renata", "tft19renataglasc"),
        "drmundo" to listOf("drmundo", "mundo", "tft19drmundo", "tft19mundo"),
        "nunu" to listOf("nunu", "nunuwillump", "tft19nunu", "tft19nunuwillump"),
        "nunuwillump" to listOf("nunu", "nunuwillump", "tft19nunu", "tft19nunuwillump"),
        "ksante" to listOf("ksante", "tft19ksante"),
        "kaisa" to listOf("kaisa", "tft19kaisa"),
        "leesin" to listOf("leesin", "tft19leesin"),
        "missfortune" to listOf("missfortune", "tft19missfortune"),
        "twistedfate" to listOf("twistedfate", "tft19twistedfate"),
        "baronnashor" to listOf("baronnashor", "baron", "tft19baronnashor", "tft19baron"),
        "tibbers" to listOf("tibbers", "tft19tibbers"),
        "smeech" to listOf("smeech", "tft19smeech"),
        "locke" to listOf("locke", "tft19locke"),
        "yunara" to listOf("yunara", "tft19yunara", "da18yunara", "tft18yunara"),
        "alune" to listOf("alune", "tft19alune", "da18alune", "tft18alune"),
        "kobuko" to listOf("kobuko", "tft19kobuko", "da18kobuko", "tft18kobuko"),
        "mel" to listOf("mel", "tft19mel"),
        "aurora" to listOf("aurora", "tft19aurora"),
        "jarvaniv" to listOf("jarvaniv", "jarvan", "tft19jarvaniv"),
        "tahmkench" to listOf("tahmkench", "tft19tahmkench"),
        "xinzhao" to listOf("xinzhao", "tft19xinzhao"),
        "aurelionsol" to listOf("aurelionsol", "tft19aurelionsol"),
        "chogath" to listOf("chogath", "tft19chogath"),
        "belveth" to listOf("belveth", "tft19belveth"),
        "seraphine" to listOf("seraphine", "seraphineindie", "tft19seraphine", "tft19seraphineindie", "tft19seraphinerisingstar", "tft19seraphinesuperstar"),
        "indie" to listOf("seraphine", "seraphineindie", "tft19seraphine", "tft19seraphineindie"),
        "seraphineindie" to listOf("seraphine", "seraphineindie", "tft19seraphine", "tft19seraphineindie"),
        "seraphinerisingstar" to listOf("seraphine", "seraphineindie", "tft19seraphine", "tft19seraphinerisingstar"),
        "seraphinesuperstar" to listOf("seraphine", "seraphineindie", "tft19seraphine", "tft19seraphinesuperstar")
    )

    fun isValidTeamPlannerCode(raw: String?): Boolean {
        val trimmed = raw?.trim().orEmpty()
        return trimmed.isNotBlank() &&
            (VALID_V02_CODE_REGEX.matches(trimmed) || VALID_V01_CODE_REGEX.matches(trimmed))
    }

    /**
     * Refreshes both LIVE and PBE CommunityDragon `tftchampions-teamplanner.json` caches from network.
     */
    suspend fun refreshMappingsFromNetwork(
        context: Context,
        client: OkHttpClient = RiotApiClientFactory.publicOkHttp
    ): Boolean = withContext(Dispatchers.IO) {
        val extraNames = loadPreloadApiToDisplayMap(context)
        val liveOk = fetchAndPersistForEnvironment(
            context = context,
            environment = TftEnvironment.LIVE,
            client = client,
            extraDisplayNames = extraNames,
            requiredSetNumber = 18
        ).catalog != null
        val pbeOk = fetchAndPersistForEnvironment(
            context = context,
            environment = TftEnvironment.PBE,
            client = client,
            extraDisplayNames = extraNames,
            requiredSetNumber = 19
        ).catalog != null
        liveOk || pbeOk
    }

    /**
     * Ensures a verified Riot CommunityDragon mapping is loaded in memory for [environment],
     * automatically re-fetching if the cache is stale or missing [requiredSetNumber].
     */
    suspend fun ensureMappingLoaded(
        context: Context,
        environment: TftEnvironment,
        client: OkHttpClient = RiotApiClientFactory.publicOkHttp,
        requiredSetNumber: Int? = null
    ): TeamPlannerCatalog? = withContext(Dispatchers.IO) {
        ensureMappingLoadedWithDiagnostics(
            context = context,
            environment = environment,
            client = client,
            requiredSetNumber = requiredSetNumber,
            unitCandidates = emptyList()
        ).catalog
    }

    private data class FetchCatalogOutcome(
        val catalog: TeamPlannerCatalog?,
        val diagnostics: List<String> = emptyList()
    )

    private fun ensureMappingLoadedWithDiagnostics(
        context: Context,
        environment: TftEnvironment,
        client: OkHttpClient,
        requiredSetNumber: Int?,
        unitCandidates: List<String>
    ): FetchCatalogOutcome {
        val extraNames = loadPreloadApiToDisplayMap(context)
        val now = System.currentTimeMillis()

        fun TeamPlannerCatalog.satisfiesRequirement(): Boolean {
            if (!isFromRiotCommunityDragon) return false
            if (requiredSetNumber == null) return sets.isNotEmpty()
            return selectStrictSetMapping(this, requiredSetNumber, unitCandidates) != null
        }

        memoryCatalogs[environment]?.let { cached ->
            val isFresh = (now - cached.loadedAtMillis) < CACHE_TTL_MS
            if (isFresh && cached.satisfiesRequirement()) {
                return FetchCatalogOutcome(cached)
            }
        }

        val diskCatalog = loadFromDiskCache(context, environment, extraNames)
        if (diskCatalog != null) {
            val isFresh = (now - diskCatalog.loadedAtMillis) < CACHE_TTL_MS
            if (isFresh && diskCatalog.satisfiesRequirement()) {
                memoryCatalogs[environment] = diskCatalog
                return FetchCatalogOutcome(diskCatalog)
            }
        }

        // Fetch fresh official CommunityDragon JSON across primary + fallback endpoints
        val netOutcome = fetchAndPersistForEnvironment(
            context = context,
            environment = environment,
            client = client,
            extraDisplayNames = extraNames,
            requiredSetNumber = requiredSetNumber,
            unitCandidates = unitCandidates
        )
        if (netOutcome.catalog != null && netOutcome.catalog.satisfiesRequirement()) {
            return netOutcome
        }

        // If offline or upstream missing set, still prefer any verified disk cache that contains the set
        if (diskCatalog != null && diskCatalog.satisfiesRequirement()) {
            memoryCatalogs[environment] = diskCatalog
            return FetchCatalogOutcome(diskCatalog, netOutcome.diagnostics)
        }

        val fallbackMem = memoryCatalogs[environment]?.takeIf { it.satisfiesRequirement() }
        return FetchCatalogOutcome(
            catalog = fallbackMem ?: netOutcome.catalog,
            diagnostics = netOutcome.diagnostics
        )
    }

    /**
     * Resolves an in-game pasteable TFT Team Planner code for [comp] (LIVE Set 18 or PBE Set 19).
     *
     * Guarantees:
     * - Never mixes LIVE (`TFTSet18`) and PBE (`TFTSet19`) sets.
     * - Never parses League client patch tags (like `16.23`) as a TFT Set number.
     * - Uses Riot's verified CommunityDragon 12-bit `team_planner_code` values (never bogus `001/002/003` indices).
     */
    suspend fun resolveTeamCode(
        context: Context,
        comp: MetaComp,
        client: OkHttpClient = RiotApiClientFactory.publicOkHttp
    ): TeamPlannerCodeResult = withContext(Dispatchers.IO) {
        val targetSetNum = extractTargetSetNumber(comp)
        val expectedSetKey = "TFTSet$targetSetNum"
        val unitCandidates = buildOrderedUnitCandidates(comp)

        // 1. Direct valid teamPlannerCode passthrough ONLY if its set matches targetSetNum
        if (isValidTeamPlannerCode(comp.teamPlannerCode)) {
            val cleaned = comp.teamPlannerCode.trim()
            val codeSetKey = extractSetKeyFromCode(cleaned)
            val codeSetNum = extractSetNumberFromKey(codeSetKey)
            if (codeSetNum == null || codeSetNum == targetSetNum) {
                return@withContext TeamPlannerCodeResult(
                    code = normalizeCodeCasing(cleaned),
                    setKey = codeSetKey ?: expectedSetKey,
                    matchedChampions = comp.coreUnits.distinct().take(MAX_PLANNER_SLOTS),
                    skippedUnits = emptyList(),
                    isVerifiedRiotMapping = true
                )
            }
        }

        val diagnostics = mutableListOf<String>()

        // 2. Load verified CommunityDragon mapping for the comp's environment (strictly requiring targetSetNum)
        val primaryOutcome = ensureMappingLoadedWithDiagnostics(
            context = context,
            environment = comp.environment,
            client = client,
            requiredSetNumber = targetSetNum,
            unitCandidates = unitCandidates
        )
        diagnostics.addAll(primaryOutcome.diagnostics)
        primaryOutcome.catalog?.let { primaryCatalog ->
            val encoded = encodeCompWithCatalog(comp, primaryCatalog)
            if (encoded.isSuccess) {
                return@withContext encoded
            }
            if (!encoded.errorMessage.isNullOrBlank()) {
                diagnostics.add(encoded.errorMessage)
            }
        }

        // 3. Cross-check sibling CommunityDragon endpoint ONLY if it actually contains targetSetNum
        val alternateEnv = if (comp.environment == TftEnvironment.LIVE) TftEnvironment.PBE else TftEnvironment.LIVE
        val altOutcome = ensureMappingLoadedWithDiagnostics(
            context = context,
            environment = alternateEnv,
            client = client,
            requiredSetNumber = targetSetNum,
            unitCandidates = unitCandidates
        )
        altOutcome.catalog?.let { altCatalog ->
            val encodedAlt = encodeCompWithCatalog(comp, altCatalog)
            if (encodedAlt.isSuccess) {
                return@withContext encodedAlt
            }
        }

        // 4. Do NOT emit bogus synthetic `001/002/003` codes that fail in Riot's TFT Team Planner
        val detail = diagnostics
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(3)
            .joinToString(" • ")
            .takeIf { it.isNotBlank() }
        val userError = if (detail != null) {
            "Could not load Riot $expectedSetKey IDs ($detail)."
        } else {
            "Could not load Riot $expectedSetKey IDs. Check internet connection and try again."
        }
        TeamPlannerCodeResult(
            code = "",
            setKey = expectedSetKey,
            matchedChampions = emptyList(),
            skippedUnits = unitCandidates,
            isVerifiedRiotMapping = false,
            errorMessage = userError
        )
    }

    /**
     * Pure deterministic encoder given a parsed [TeamPlannerCatalog].
     * Strictly enforces [extractTargetSetNumber] so LIVE Set 18 and PBE Set 19 never cross-encode.
     */
    fun encodeCompWithCatalog(
        comp: MetaComp,
        catalog: TeamPlannerCatalog
    ): TeamPlannerCodeResult {
        val orderedCandidates = buildOrderedUnitCandidates(comp)
        val targetSetNum = extractTargetSetNumber(comp)
        val expectedSetKey = "TFTSet$targetSetNum"

        val chosenSet = selectStrictSetMapping(
            catalog = catalog,
            targetSetNumber = targetSetNum,
            unitCandidates = orderedCandidates
        )

        if (chosenSet == null) {
            return TeamPlannerCodeResult(
                code = "",
                setKey = expectedSetKey,
                matchedChampions = emptyList(),
                skippedUnits = orderedCandidates,
                isVerifiedRiotMapping = false,
                errorMessage = "Riot $expectedSetKey mapping not found in Team Planner catalog."
            )
        }

        val matchedEntries = mutableListOf<TeamPlannerChampionEntry>()
        val seenCodes = HashSet<String>()
        val skipped = mutableListOf<String>()

        for (unitName in orderedCandidates) {
            if (matchedEntries.size >= MAX_PLANNER_SLOTS) break
            val entry = matchChampionInSet(unitName, chosenSet)
            if (entry != null) {
                if (seenCodes.add(entry.slotHex)) {
                    matchedEntries.add(entry)
                }
            } else {
                skipped.add(unitName)
            }
        }

        if (matchedEntries.isEmpty()) {
            return TeamPlannerCodeResult(
                code = "",
                setKey = chosenSet.setKey,
                matchedChampions = emptyList(),
                skippedUnits = skipped,
                isVerifiedRiotMapping = false,
                errorMessage = "No ${chosenSet.setKey} champions matched for ${comp.title}."
            )
        }

        val prefix = if (chosenSet.usesVersion02) VERSION_02_PREFIX else VERSION_01_PREFIX
        val emptySlot = if (chosenSet.usesVersion02) EMPTY_SLOT_HEX_V2 else EMPTY_SLOT_HEX_V1
        val slotWidth = if (chosenSet.usesVersion02) 3 else 2

        val codeBuilder = StringBuilder(2 + MAX_PLANNER_SLOTS * slotWidth + chosenSet.setKey.length)
        codeBuilder.append(prefix)
        matchedEntries.forEach { entry ->
            codeBuilder.append(entry.slotHex)
        }
        repeat((MAX_PLANNER_SLOTS - matchedEntries.size).coerceAtLeast(0)) {
            codeBuilder.append(emptySlot)
        }
        codeBuilder.append(chosenSet.setKey)

        return TeamPlannerCodeResult(
            code = codeBuilder.toString(),
            setKey = chosenSet.setKey,
            matchedChampions = matchedEntries.map { it.displayName.ifBlank { it.characterId } },
            skippedUnits = skipped,
            isVerifiedRiotMapping = catalog.isFromRiotCommunityDragon
        )
    }

    /**
     * Parses Riot's CommunityDragon `tftchampions-teamplanner.json` (and `tftchampions.json` fallback).
     * Handles:
     * - Top-level `{ "TFTSet18": [...], "TFTSet19": [...] }`, `{ "sets": { ... } }`, `{ "data": { ... } }`
     * - Nested `{ "TFTSet19": { "champions": [...] } }` AND object maps `{ "TFTSet19": { "TFT19_Ziggs": { ... } } }`
     * - Top-level `JSONArray` of champion objects grouped by set key or `character_id` prefix
     * - Both `snake_case` (`character_id`, `team_planner_code`, `display_name`) and `camelCase` keys
     * - Both JSON integer (`1015` -> `3f7`) and hex/decimal string `team_planner_code` values (with set-wide radix detection!)
     * - Base-first indexing so base champions (`DA_Lux18_Base`, `TFT19_Seraphine_Indie`) always take priority over variants
     */
    fun parseCommunityDragonJson(
        rawJson: String,
        isFromRiotCommunityDragon: Boolean = true,
        extraDisplayNameByNormalizedId: Map<String, String> = emptyMap(),
        loadedAtMillis: Long = System.currentTimeMillis(),
        requireExplicitCodes: Boolean = false
    ): TeamPlannerCatalog? {
        return runCatching {
            val trimmed = rawJson.trim()
            if (trimmed.isEmpty()) return@runCatching null
            val setsMap = LinkedHashMap<String, TeamPlannerSetMapping>()

            if (trimmed.startsWith("[")) {
                val rootArray = JSONArray(trimmed)
                val grouped = LinkedHashMap<String, JSONArray>()
                for (i in 0 until rootArray.length()) {
                    val rawObj = rootArray.optJSONObject(i) ?: continue
                    val rec = rawObj.optJSONObject("character_record")
                        ?: rawObj.optJSONObject("characterRecord")
                        ?: rawObj
                    val charId = rec.optString("character_id")
                        .ifBlank { rec.optString("characterId") }
                        .ifBlank { rec.optString("apiName") }
                        .ifBlank { rec.optString("id") }
                        .ifBlank { rawObj.optString("character_id") }
                        .ifBlank { rawObj.optString("apiName") }
                        .trim()
                    val explicitSetKey = rec.optString("set")
                        .ifBlank { rec.optString("set_key") }
                        .ifBlank { rec.optString("setKey") }
                        .ifBlank { rec.optString("set_id") }
                        .ifBlank { rawObj.optString("set") }
                        .ifBlank { rawObj.optString("set_key") }
                        .trim()
                    val resolvedKey = when {
                        explicitSetKey.isNotBlank() -> {
                            val num = extractSetNumberFromKey(explicitSetKey)
                            if (num != null && !explicitSetKey.startsWith("TFTSet", ignoreCase = true)) {
                                "TFTSet$num"
                            } else {
                                explicitSetKey
                            }
                        }
                        else -> extractSetNumberFromCharacterId(charId)?.let { "TFTSet$it" }
                    } ?: continue
                    grouped.getOrPut(resolvedKey) { JSONArray() }.put(rawObj)
                }
                grouped.forEach { (setKey, arr) ->
                    val mapping = parseSetArray(setKey, arr, extraDisplayNameByNormalizedId, requireExplicitCodes)
                    if (mapping != null && mapping.entries.isNotEmpty()) {
                        setsMap[setKey] = mapping
                    }
                }
            } else {
                val root = JSONObject(trimmed)
                // Support cdragon/tft/en_us.json `setData` array: [{ "mutator": "TFTSet19", "number": 19, "champions": [...] }]
                val setDataArr = root.optJSONArray("setData")
                if (setDataArr != null) {
                    for (i in 0 until setDataArr.length()) {
                        val setObj = setDataArr.optJSONObject(i) ?: continue
                        val champs = setObj.optJSONArray("champions") ?: continue
                        val mutator = setObj.optString("mutator").trim()
                        val setNum = setObj.optInt("number", -1).takeIf { it in 1..99 }
                        val resolvedKey = mutator.ifBlank { setNum?.let { "TFTSet$it" }.orEmpty() }
                        if (resolvedKey.isBlank()) continue
                        val mapping = parseSetArray(resolvedKey, champs, extraDisplayNameByNormalizedId, requireExplicitCodes)
                        if (mapping != null && mapping.entries.isNotEmpty()) {
                            setsMap[mapping.setKey] = mapping
                        }
                    }
                }
                val containerObj = root.optJSONObject("sets")
                    ?: root.optJSONObject("data")
                    ?: root
                val setKeys = containerObj.keys()
                while (setKeys.hasNext()) {
                    val setKey = setKeys.next()
                    if (setKey == "setData" || setKey == "items") continue
                    val rawNode = containerObj.opt(setKey) ?: continue
                    val arr = extractChampionArrayFromSetNode(rawNode) ?: continue
                    val mapping = parseSetArray(setKey, arr, extraDisplayNameByNormalizedId, requireExplicitCodes)
                    if (mapping != null && mapping.entries.isNotEmpty()) {
                        // Prefer v02 explicit-code mapping over v01 fallback if same key exists from setData
                        val existing = setsMap[mapping.setKey]
                        if (existing == null || (!existing.usesVersion02 && mapping.usesVersion02)) {
                            setsMap[mapping.setKey] = mapping
                        }
                    }
                }
            }

            if (setsMap.isEmpty()) null
            else TeamPlannerCatalog(
                sets = setsMap,
                isFromRiotCommunityDragon = isFromRiotCommunityDragon,
                loadedAtMillis = loadedAtMillis
            )
        }.getOrNull()
    }

    private fun extractChampionArrayFromSetNode(rawNode: Any): JSONArray? {
        return when (rawNode) {
            is JSONArray -> rawNode
            is JSONObject -> {
                rawNode.optJSONArray("champions")
                    ?: rawNode.optJSONArray("units")
                    ?: rawNode.optJSONArray("entries")
                    ?: rawNode.optJSONArray("items")
                    ?: rawNode.optJSONArray("data")
                    ?: run {
                        // Check any nested JSONArray whose elements look like champion objects
                        val arrayKeys = rawNode.keys()
                        while (arrayKeys.hasNext()) {
                            val k = arrayKeys.next()
                            val candidateArr = rawNode.optJSONArray(k) ?: continue
                            if (candidateArr.length() > 0) {
                                val firstObj = candidateArr.optJSONObject(0)
                                if (firstObj != null && (
                                        firstObj.has("character_id") ||
                                            firstObj.has("characterId") ||
                                            firstObj.has("character_record") ||
                                            firstObj.has("apiName") ||
                                            firstObj.has("team_planner_code") ||
                                            firstObj.has("teamPlannerCode")
                                        )
                                ) {
                                    return candidateArr
                                }
                            }
                        }
                        // Support map-style set objects keyed by character_id: { "TFT19_Ziggs": { "team_planner_code": 1152 } }
                        val synthesized = JSONArray()
                        val keys = rawNode.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            val child = rawNode.optJSONObject(k) ?: continue
                            if (
                                child.has("team_planner_code") ||
                                child.has("teamPlannerCode") ||
                                child.has("planner_code") ||
                                child.has("plannerCode") ||
                                child.has("character_id") ||
                                child.has("characterId") ||
                                child.has("character_record") ||
                                child.has("display_name") ||
                                child.has("name") ||
                                child.has("path")
                            ) {
                                val clone = JSONObject(child.toString())
                                if (clone.optString("character_id").isBlank() &&
                                    clone.optString("characterId").isBlank() &&
                                    !clone.has("character_record")
                                ) {
                                    clone.put("character_id", k)
                                }
                                synthesized.put(clone)
                            }
                        }
                        synthesized.takeIf { it.length() > 0 }
                    }
            }
            else -> null
        }
    }

    private fun parseSetArray(
        setKey: String,
        arr: JSONArray,
        extraDisplayNameByNormalizedId: Map<String, String>,
        requireExplicitCodes: Boolean = false
    ): TeamPlannerSetMapping? {
        data class RawEntry(
            val characterId: String,
            val displayName: String,
            val pathSlug: String,
            val rawCodeValue: Any?,
            val isVariant: Boolean,
            val isPlayableShopUnit: Boolean
        )

        val rawEntries = mutableListOf<RawEntry>()
        for (i in 0 until arr.length()) {
            val rawItem = arr.opt(i) ?: continue
            if (rawItem is String) {
                val charId = rawItem.trim()
                if (charId.isBlank()) continue
                val enrichedDisplayName = extraDisplayNameByNormalizedId[normalizeKey(charId)]
                    ?: extraDisplayNameByNormalizedId[stripRiotChampionPrefix(charId)]
                    ?: charId
                rawEntries.add(
                    RawEntry(
                        characterId = charId,
                        displayName = enrichedDisplayName,
                        pathSlug = "",
                        rawCodeValue = null,
                        isVariant = isChampionTraitVariant(charId, enrichedDisplayName),
                        isPlayableShopUnit = true
                    )
                )
                continue
            }
            val outerObj = rawItem as? JSONObject ?: continue
            // Unwrap Riot's `v1/tftchampions.json` `character_record` wrapper if present
            val rec = outerObj.optJSONObject("character_record")
                ?: outerObj.optJSONObject("characterRecord")
                ?: outerObj
            val charId = rec.optString("character_id")
                .ifBlank { rec.optString("characterId") }
                .ifBlank { rec.optString("apiName") }
                .ifBlank { rec.optString("id") }
                .ifBlank { outerObj.optString("character_id") }
                .ifBlank { outerObj.optString("apiName") }
                .trim()
            val pathStr = rec.optString("path")
                .ifBlank { outerObj.optString("path") }
                .ifBlank { rec.optString("squareIconPath") }
                .ifBlank { rec.optString("icon") }
                .trim()
            val pathSlug = extractSlugFromAssetPath(pathStr)
            val rawDisplayName = rec.optString("display_name")
                .ifBlank { rec.optString("displayName") }
                .ifBlank { rec.optString("name") }
                .ifBlank { outerObj.optString("display_name") }
                .ifBlank { outerObj.optString("name") }
                .trim()
            val enrichedDisplayName = rawDisplayName.ifBlank {
                extraDisplayNameByNormalizedId[normalizeKey(charId)]
                    ?: extraDisplayNameByNormalizedId[stripRiotChampionPrefix(charId)]
                    ?: charId
            }
            if (charId.isBlank() && enrichedDisplayName.isBlank()) continue

            val rawCodeVal = extractRawPlannerCodeValue(rec) ?: extractRawPlannerCodeValue(outerObj)
            val isVariant = isChampionTraitVariant(charId, enrichedDisplayName)
            val traitsArr = rec.optJSONArray("traits")
            val costVal = if (rec.has("cost")) rec.optInt("cost", -1) else -1
            val hasExplicitEmptyTraits = traitsArr != null && traitsArr.length() == 0
            val hasInvalidCost = costVal == 0 || costVal >= 11
            val isNonShopProp = NON_PLAYABLE_UNIT_REGEX.containsMatchIn(charId) && (traitsArr == null || traitsArr.length() == 0)
            val isPlayableShopUnit = !hasExplicitEmptyTraits && !hasInvalidCost && !isNonShopProp
            rawEntries.add(
                RawEntry(
                    characterId = charId.ifBlank { enrichedDisplayName },
                    displayName = enrichedDisplayName,
                    pathSlug = pathSlug,
                    rawCodeValue = rawCodeVal,
                    isVariant = isVariant,
                    isPlayableShopUnit = isPlayableShopUnit
                )
            )
        }

        if (rawEntries.isEmpty()) return null

        // Detect whether string `team_planner_code` values in this set are hex strings (e.g. "3f7", "429", "01a", "04a2")
        // vs decimal strings (e.g. "1015", "1065") so all-digit hex strings like "429" are never misread as base 10!
        val stringCodeSamples = rawEntries.mapNotNull { (it.rawCodeValue as? String)?.trim()?.takeIf { s -> s.isNotEmpty() } }
        val stringsAreHex = stringCodeSamples.any { s ->
            s.startsWith("0x", ignoreCase = true) ||
                s.any { ch -> ch.lowercaseChar() in 'a'..'f' } ||
                (s.length in 2..4 && s.startsWith('0'))
        }

        val parsedExplicitCodes = rawEntries.map { raw ->
            parsePlannerCodeInt(raw.rawCodeValue, treatStringsAsHex = stringsAreHex)
        }
        val hasExplicitCodes = parsedExplicitCodes.any { it != null && it > 0 }
        if (requireExplicitCodes && !hasExplicitCodes) {
            return null
        }

        val effectiveRawEntries = if (hasExplicitCodes) {
            rawEntries
        } else {
            val playable = rawEntries.filter { it.isPlayableShopUnit }
            if (playable.isNotEmpty()) playable else rawEntries
        }
        val effectiveExplicitCodes = if (hasExplicitCodes) parsedExplicitCodes else emptyList()

        val setNum = extractSetNumberFromKey(setKey)
            ?: inferSetNumberFromCharacterIds(effectiveRawEntries.map { it.characterId })
        // Use Version 02 (3-hex-digit 12-bit Riot IDs) whenever explicit `team_planner_code` values are present;
        // if Riot omits `team_planner_code`, use Version 01 (2-hex-digit 1-based alphabetical index) instead of bogus 02/001.
        val usesVersion02 = hasExplicitCodes

        val sortedForFallback = effectiveRawEntries.sortedBy { it.characterId.lowercase(Locale.US) }
        val fallbackIndexByCharId = sortedForFallback.mapIndexed { idx, e -> e.characterId to (idx + 1) }.toMap()

        val entries = mutableListOf<TeamPlannerChampionEntry>()
        effectiveRawEntries.forEachIndexed { idx, raw ->
            val codeInt = if (hasExplicitCodes) {
                effectiveExplicitCodes[idx]?.takeIf { it > 0 } ?: return@forEachIndexed
            } else {
                fallbackIndexByCharId[raw.characterId] ?: return@forEachIndexed
            }
            val slotHex = if (usesVersion02) formatSlotHex3(codeInt) else formatSlotHex2(codeInt)
            entries.add(
                TeamPlannerChampionEntry(
                    characterId = raw.characterId,
                    displayName = raw.displayName,
                    plannerCodeInt = codeInt,
                    slotHex = slotHex,
                    isVariant = raw.isVariant
                )
            )
        }

        if (entries.isEmpty()) return null

        // Index base champions FIRST so base Lux (DA_Lux18_Base) and base Seraphine (TFT19_Seraphine_Indie)
        // always take precedence over upgraded/trait forms.
        val lookup = HashMap<String, TeamPlannerChampionEntry>(entries.size * 8)
        val orderedForIndexing = entries.sortedWith(
            compareBy<TeamPlannerChampionEntry> { it.isVariant }
                .thenByDescending {
                    it.characterId.contains("base", ignoreCase = true) ||
                        it.characterId.endsWith("_indie", ignoreCase = true)
                }
        )
        orderedForIndexing.forEach { entry ->
            val matchingRaw = rawEntries.firstOrNull { it.characterId == entry.characterId }
            indexEntryKeys(entry, matchingRaw?.pathSlug.orEmpty(), lookup)
        }

        val canonicalSetKey = if (PURE_SET_NUMBER_REGEX.matches(setKey.trim())) {
            "TFTSet${setKey.trim()}"
        } else {
            setKey
        }
        return TeamPlannerSetMapping(
            setKey = canonicalSetKey,
            setNumber = setNum,
            usesVersion02 = usesVersion02,
            entries = entries,
            lookupByNormalizedKey = lookup
        )
    }

    private fun extractRawPlannerCodeValue(obj: JSONObject): Any? {
        val candidateKeys = listOf(
            "team_planner_code",
            "teamPlannerCode",
            "planner_code",
            "plannerCode",
            "team_planner_id",
            "teamPlannerId",
            "code"
        )
        for (key in candidateKeys) {
            if (obj.has(key) && !obj.isNull(key)) {
                return obj.opt(key)
            }
        }
        return null
    }

    private fun parsePlannerCodeInt(rawVal: Any?, treatStringsAsHex: Boolean): Int? {
        if (rawVal == null) return null
        return when (rawVal) {
            is Number -> rawVal.toInt().takeIf { it in 1..0xFFF }
            is String -> {
                val cleaned = rawVal.trim()
                if (cleaned.isEmpty()) return null
                when {
                    cleaned.startsWith("0x", ignoreCase = true) ->
                        cleaned.substring(2).toIntOrNull(16)?.takeIf { it in 1..0xFFF }
                    treatStringsAsHex ->
                        cleaned.toIntOrNull(16)?.takeIf { it in 1..0xFFF }
                    cleaned.all { it.isDigit() } ->
                        cleaned.toIntOrNull(10)?.takeIf { it in 1..0xFFF }
                    cleaned.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' } ->
                        cleaned.toIntOrNull(16)?.takeIf { it in 1..0xFFF }
                    else -> null
                }
            }
            else -> null
        }
    }

    private fun isChampionTraitVariant(charId: String, displayName: String): Boolean {
        if (displayName.contains('(') && !displayName.contains("(base)", ignoreCase = true)) {
            return true
        }
        val lowerId = charId.lowercase(Locale.US)
        if (lowerId.contains("lux") && !lowerId.contains("base") && variantKeywords.any { lowerId.contains(it) }) {
            return true
        }
        if (lowerId.contains("seraphine") && (lowerId.contains("risingstar") || lowerId.contains("superstar"))) {
            return true
        }
        return false
    }

    private fun extractSlugFromAssetPath(path: String): String {
        if (path.isBlank()) return ""
        val fileName = path.substringAfterLast('/').substringBeforeLast('.')
        return fileName
            .removePrefix("tft_")
            .replace(Regex("""_(?:square|tile|hud|splash|icon).*$""", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    private fun indexEntryKeys(
        entry: TeamPlannerChampionEntry,
        pathSlug: String,
        lookup: MutableMap<String, TeamPlannerChampionEntry>
    ) {
        val normDisplay = normalizeKey(entry.displayName)
        val normCharId = normalizeKey(entry.characterId)
        if (normDisplay.isNotBlank()) {
            lookup.putIfAbsent(normDisplay, entry)
        }
        if (normCharId.isNotBlank()) {
            lookup.putIfAbsent(normCharId, entry)
        }
        val stripped = stripRiotChampionPrefix(entry.characterId)
        if (stripped.isNotBlank()) {
            lookup.putIfAbsent(stripped, entry)
        }
        val strippedVariant = stripChampionFormSuffix(stripped)
        if (strippedVariant.length >= 2) {
            lookup.putIfAbsent(strippedVariant, entry)
        }
        if (pathSlug.isNotBlank()) {
            val normSlug = stripRiotChampionPrefix(pathSlug)
            if (normSlug.length >= 2) {
                lookup.putIfAbsent(normSlug, entry)
            }
            val slugVariant = stripChampionFormSuffix(normSlug)
            if (slugVariant.length >= 2) {
                lookup.putIfAbsent(slugVariant, entry)
            }
        }
        // Only bind parenthetical-stripped key (e.g., "Lux") if no base entry already claimed it
        val beforeParen = entry.displayName.substringBefore('(').trim()
        val normBeforeParen = normalizeKey(beforeParen)
        if (normBeforeParen.length >= 2 && normBeforeParen != normDisplay) {
            lookup.putIfAbsent(normBeforeParen, entry)
        }
    }

    private fun stripChampionFormSuffix(norm: String): String {
        val base = norm
            .removeSuffix("ad")
            .removeSuffix("ap")
            .removeSuffix("base")
            .removeSuffix("small")
        return when {
            base == "seraphineindie" || base == "seraphinerisingstar" || base == "seraphinesuperstar" -> "seraphine"
            else -> base
        }
    }

    private fun matchChampionInSet(
        unitName: String,
        setMapping: TeamPlannerSetMapping
    ): TeamPlannerChampionEntry? {
        val norm = normalizeKey(unitName)
        if (norm.isBlank()) return null

        // 1. Direct normalized match (display_name or character_id)
        setMapping.lookupByNormalizedKey[norm]?.let { return it }

        // 2. Stripped Riot API prefix match (e.g. DA_18_Ezreal -> ezreal, TFT19_Seraphine_Indie -> seraphine)
        val stripped = stripRiotChampionPrefix(unitName)
        if (stripped.isNotBlank()) {
            setMapping.lookupByNormalizedKey[stripped]?.let { return it }
            val strippedVariant = stripChampionFormSuffix(stripped)
            if (strippedVariant.length >= 2) {
                setMapping.lookupByNormalizedKey[strippedVariant]?.let { return it }
            }
        }

        // 3. Explicit alias table (e.g. Pebbles -> sentry, Indie -> seraphine, Wukong -> monkeyking)
        championLookupAliases[norm]?.forEach { alias ->
            setMapping.lookupByNormalizedKey[alias]?.let { return it }
        }
        if (stripped.isNotBlank() && stripped != norm) {
            championLookupAliases[stripped]?.forEach { alias ->
                setMapping.lookupByNormalizedKey[alias]?.let { return it }
            }
        }

        // 4. Parenthetical strip (e.g. "Lux (Lunar)" -> "lux")
        val beforeParen = normalizeKey(unitName.substringBefore('('))
        if (beforeParen.isNotBlank() && beforeParen != norm) {
            setMapping.lookupByNormalizedKey[beforeParen]?.let { return it }
            championLookupAliases[beforeParen]?.forEach { alias ->
                setMapping.lookupByNormalizedKey[alias]?.let { return it }
            }
        }

        return null
    }

    /**
     * Strictly selects a set mapping matching [targetSetNumber] so LIVE Set 18 and PBE Set 19
     * NEVER fall back to an unrelated numbered set like `TFTSet10`, `TFTSet15`, `TFTSet16`, or `TFTSet18`.
     *
     * Supports:
     * 1. Exact canonical key (`TFTSet18`, `TFTSet19`)
     * 2. Same set number via key (`TFTSet19_Act1`, `TFT19`, `19`) OR `character_id` prefix (`TFT19_*`)
     * 3. Unnumbered Riot PBE codename sets (e.g. `TFTSetPrismatic`, `TFTSetTour`) that match >= 2 comp units
     */
    private fun selectStrictSetMapping(
        catalog: TeamPlannerCatalog,
        targetSetNumber: Int,
        unitCandidates: List<String>
    ): TeamPlannerSetMapping? {
        if (catalog.sets.isEmpty()) return null

        // 1. Exact canonical Riot set key first (e.g., "TFTSet18" or "TFTSet19")
        val exactMatch = catalog.sets.values.firstOrNull {
            it.setKey.equals("TFTSet$targetSetNumber", ignoreCase = true)
        }
        if (exactMatch != null) return exactMatch

        // 2. Keys with the exact same set number (from key name OR inferred from character_id prefixes like TFT19_)
        val sameSetCandidates = catalog.sets.values.filter { it.setNumber == targetSetNumber }
        if (sameSetCandidates.isNotEmpty()) {
            return sameSetCandidates.maxByOrNull { mapping ->
                val matchCount = unitCandidates.count { matchChampionInSet(it, mapping) != null }
                val nonEventBonus = if (!isAuxiliarySetKey(mapping.setKey)) 500 else 0
                matchCount * 10 + nonEventBonus
            }
        }

        // 3. Unnumbered codename set key (setNumber == null, never an older numbered set like TFTSet10/TFTSet18)
        if (unitCandidates.isNotEmpty()) {
            val minRequiredMatches = minOf(2, unitCandidates.size)
            val codenameCandidates = catalog.sets.values
                .filter { it.setNumber == null && !isAuxiliarySetKey(it.setKey) }
                .map { mapping -> mapping to unitCandidates.count { matchChampionInSet(it, mapping) != null } }
                .filter { (_, matchCount) -> matchCount >= minRequiredMatches }
            if (codenameCandidates.isNotEmpty()) {
                return codenameCandidates.maxByOrNull { it.second }?.first
            }
        }

        return null
    }

    private fun isAuxiliarySetKey(setKey: String): Boolean =
        setKey.contains("Event", ignoreCase = true) ||
            setKey.contains("Tutorial", ignoreCase = true) ||
            setKey.contains("Revival", ignoreCase = true) ||
            setKey.contains("Choncc", ignoreCase = true)

    private fun TeamPlannerCatalog.containsSetNumber(targetSetNumber: Int): Boolean =
        sets.values.any {
            it.setKey.equals("TFTSet$targetSetNumber", ignoreCase = true) ||
                it.setNumber == targetSetNumber
        }

    private fun buildOrderedUnitCandidates(comp: MetaComp): List<String> {
        val ordered = LinkedHashSet<String>()
        if (comp.carryName.isNotBlank()) {
            ordered.add(comp.carryName.trim())
        }
        // Prioritize carry, itemized units, and higher-cost units from hex placements
        comp.hexPlacements
            .sortedWith(
                compareByDescending<com.example.data.model.HexUnitPlacement> { it.isCarry }
                    .thenByDescending { it.items.size }
                    .thenByDescending { it.cost }
            )
            .forEach { placement ->
                if (placement.unitName.isNotBlank()) {
                    ordered.add(placement.unitName.trim())
                }
            }
        comp.coreUnits.forEach { unit ->
            if (unit.isNotBlank()) {
                ordered.add(unit.trim())
            }
        }
        return ordered.toList()
    }

    private data class CandidateEndpoint(
        val url: String,
        val label: String,
        val requireExplicitCodes: Boolean = false
    )

    private fun fetchAndPersistForEnvironment(
        context: Context,
        environment: TftEnvironment,
        client: OkHttpClient,
        extraDisplayNames: Map<String, String>,
        requiredSetNumber: Int? = null,
        unitCandidates: List<String> = emptyList()
    ): FetchCatalogOutcome {
        val endpoints = if (environment == TftEnvironment.PBE) {
            listOf(
                CandidateEndpoint(CDRAGON_PBE_TEAMPLANNER_URL, "pbe-planner"),
                CandidateEndpoint(CDRAGON_PBE_CHAMPIONS_FALLBACK_URL, "pbe-champs"),
                CandidateEndpoint(CDRAGON_LIVE_TEAMPLANNER_URL, "live-planner"),
                CandidateEndpoint(CDRAGON_LIVE_CHAMPIONS_FALLBACK_URL, "live-champs"),
                CandidateEndpoint(CDRAGON_PBE_TFT_DATA_URL, "pbe-tft")
            )
        } else {
            listOf(
                CandidateEndpoint(CDRAGON_LIVE_TEAMPLANNER_URL, "live-planner"),
                CandidateEndpoint(CDRAGON_PBE_TEAMPLANNER_URL, "pbe-planner"),
                CandidateEndpoint(CDRAGON_LIVE_CHAMPIONS_FALLBACK_URL, "live-champs")
            )
        }

        val httpClient = client.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        val diagnostics = mutableListOf<String>()
        var firstParsedFallback: TeamPlannerCatalog? = null
        var bestV01Match: Pair<TeamPlannerCatalog, String>? = null

        for (endpoint in endpoints) {
            val req = Request.Builder()
                .url(endpoint.url)
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("Accept", "application/json, text/plain, */*")
                .build()

            var statusCode = -1
            var failureReason: String? = null
            val body = try {
                httpClient.newCall(req).execute().use { resp ->
                    statusCode = resp.code
                    if (resp.isSuccessful) {
                        resp.body?.string()?.takeIf { it.isNotBlank() }
                    } else {
                        failureReason = "HTTP ${resp.code}"
                        null
                    }
                }
            } catch (e: Exception) {
                failureReason = e.javaClass.simpleName
                null
            }

            if (body == null) {
                diagnostics.add("${endpoint.label}: ${failureReason ?: "HTTP $statusCode"}")
                continue
            }

            val now = System.currentTimeMillis()
            val parsed = parseCommunityDragonJson(
                rawJson = body,
                isFromRiotCommunityDragon = true,
                extraDisplayNameByNormalizedId = extraDisplayNames,
                loadedAtMillis = now,
                requireExplicitCodes = endpoint.requireExplicitCodes
            )
            if (parsed == null) {
                diagnostics.add("${endpoint.label}: unparseable")
                continue
            }

            if (firstParsedFallback == null) {
                firstParsedFallback = parsed
            }

            val matchedSet = if (requiredSetNumber == null) {
                parsed.sets.values.firstOrNull()
            } else {
                selectStrictSetMapping(parsed, requiredSetNumber, unitCandidates)
            }

            if (matchedSet != null) {
                if (matchedSet.usesVersion02 || requiredSetNumber == null) {
                    memoryCatalogs[environment] = parsed
                    runCatching {
                        cacheFileFor(context, environment).writeText(body, Charsets.UTF_8)
                    }
                    return FetchCatalogOutcome(parsed, diagnostics)
                } else if (bestV01Match == null) {
                    // Keep verified Riot v01 alphabetical mapping while checking if a later endpoint has v02 IDs
                    bestV01Match = parsed to body
                }
            } else {
                val availableKeys = parsed.sets.keys.toList().takeLast(4).joinToString(",")
                diagnostics.add("${endpoint.label}: sets [$availableKeys]")
            }
        }

        if (bestV01Match != null) {
            val (v01Catalog, v01Body) = bestV01Match
            memoryCatalogs[environment] = v01Catalog
            runCatching {
                cacheFileFor(context, environment).writeText(v01Body, Charsets.UTF_8)
            }
            return FetchCatalogOutcome(v01Catalog, diagnostics)
        }

        if (firstParsedFallback != null) {
            memoryCatalogs[environment] = firstParsedFallback
        }
        return FetchCatalogOutcome(firstParsedFallback, diagnostics)
    }

    private fun loadFromDiskCache(
        context: Context,
        environment: TftEnvironment,
        extraDisplayNames: Map<String, String>
    ): TeamPlannerCatalog? {
        val file = cacheFileFor(context, environment)
        if (!file.exists()) return null
        val raw = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return null
        return parseCommunityDragonJson(
            rawJson = raw,
            isFromRiotCommunityDragon = true,
            extraDisplayNameByNormalizedId = extraDisplayNames,
            loadedAtMillis = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
        )
    }

    private fun cacheFileFor(context: Context, environment: TftEnvironment): File {
        val name = if (environment == TftEnvironment.PBE) PBE_CACHE_FILE else LIVE_CACHE_FILE
        return File(context.applicationContext.filesDir, name)
    }

    private fun loadPreloadApiToDisplayMap(context: Context): Map<String, String> {
        preloadApiToDisplayCache?.let { return it }
        val map = HashMap<String, String>()
        // 1. Set 18 LIVE preload names
        runCatching {
            val raw = context.applicationContext.assets.open(PRELOAD_SET18_ASSET)
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(raw)
            val champsArr = root.optJSONArray("champions") ?: return@runCatching
            for (i in 0 until champsArr.length()) {
                val obj = champsArr.optJSONObject(i) ?: continue
                val id = obj.optString("id").removePrefix("live_").trim()
                val name = obj.optString("name").trim()
                if (id.isNotBlank() && name.isNotBlank()) {
                    map[normalizeKey(id)] = name
                    val stripped = stripRiotChampionPrefix(id)
                    if (stripped.isNotBlank()) {
                        map.putIfAbsent(stripped, name)
                    }
                }
            }
        }
        // 2. Set 19 PBE preload names
        runCatching {
            val raw = context.applicationContext.assets.open(PRELOAD_SET19_ASSET)
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(raw)
            val iconsObj = root.optJSONObject("championIcons") ?: return@runCatching
            val keys = iconsObj.keys()
            while (keys.hasNext()) {
                val name = keys.next().trim()
                if (name.isNotBlank()) {
                    val norm = normalizeKey(name)
                    if (norm.isNotBlank()) {
                        map.putIfAbsent(norm, name)
                        map.putIfAbsent("tft19$norm", name)
                    }
                }
            }
        }
        preloadApiToDisplayCache = map
        return map
    }

    internal fun clearMemoryCacheForTests() {
        memoryCatalogs.clear()
    }

    internal fun formatSlotHex3(codeInt: Int): String =
        codeInt.coerceIn(0, 0xFFF).toString(16).lowercase(Locale.US).padStart(3, '0')

    internal fun formatSlotHex2(codeInt: Int): String =
        codeInt.coerceIn(0, 0xFF).toString(16).lowercase(Locale.US).padStart(2, '0')

    internal fun normalizeKey(raw: String): String =
        raw.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")

    internal fun extractTargetSetNumber(comp: MetaComp): Int {
        // NEVER parse comp.patchTag (e.g. "16.23" is LoL Season 16 patch 23, NOT TFT Set 16!)
        return extractExplicitSetNumber(comp.setName)
            ?: extractExplicitSetNumber(comp.id)
            ?: extractExplicitSetNumber(comp.sourceUrl)
            ?: if (comp.environment == TftEnvironment.PBE) 19 else 18
    }

    private fun stripRiotChampionPrefix(raw: String): String {
        val stripped = raw
            .replace(Regex("""^(?:TFT|DA|PT)[\s_]*\d*[\s_]*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""(?:18|19)(?=(?:_|$))"""), "")
        return normalizeKey(stripped)
    }

    private fun extractExplicitSetNumber(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        val match = EXPLICIT_SET_NUMBER_REGEX.find(text) ?: return null
        return match.groupValues[1].toIntOrNull()?.takeIf { it in 1..99 }
    }

    private fun extractSetNumberFromKey(setKey: String?): Int? {
        if (setKey.isNullOrBlank()) return null
        val trimmed = setKey.trim()
        extractExplicitSetNumber(trimmed)?.let { return it }
        FLEXIBLE_SET_KEY_NUMBER_REGEX.find(trimmed)?.groupValues?.getOrNull(1)
            ?.toIntOrNull()?.takeIf { it in 1..99 }?.let { return it }
        PURE_SET_NUMBER_REGEX.find(trimmed)?.groupValues?.getOrNull(1)
            ?.toIntOrNull()?.takeIf { it in 1..99 }?.let { return it }
        return null
    }

    private fun extractSetNumberFromCharacterId(charId: String): Int? {
        if (charId.isBlank()) return null
        val m = CHAR_ID_SET_NUMBER_REGEX.find(charId.trim()) ?: return null
        val rawNum = m.groupValues[1].ifBlank { m.groupValues[2] }
        return rawNum.toIntOrNull()?.takeIf { it in 1..99 }
    }

    private fun inferSetNumberFromCharacterIds(charIds: List<String>): Int? {
        if (charIds.isEmpty()) return null
        val counts = HashMap<Int, Int>()
        charIds.forEach { id ->
            extractSetNumberFromCharacterId(id)?.let { num ->
                counts[num] = (counts[num] ?: 0) + 1
            }
        }
        val best = counts.maxByOrNull { it.value } ?: return null
        val minThreshold = minOf(3, charIds.size).coerceAtLeast(1)
        return if (best.value >= minThreshold && best.value * 10 >= charIds.size * 3) {
            best.key
        } else {
            null
        }
    }

    private fun extractSetKeyFromCode(code: String): String? {
        val idx = code.indexOf("TFTSet", ignoreCase = true)
            .takeIf { it >= 0 }
            ?: code.indexOf("TFT", ignoreCase = true)
        return if (idx >= 0) code.substring(idx) else null
    }

    private fun normalizeCodeCasing(code: String): String {
        val idx = code.indexOf("TFTSet", ignoreCase = true)
            .takeIf { it >= 0 }
            ?: code.indexOf("TFT", ignoreCase = true)
        if (idx < 0) return code
        val hexPart = code.substring(0, idx).lowercase(Locale.US)
        val setSuffix = code.substring(idx)
        return hexPart + setSuffix
    }
}
