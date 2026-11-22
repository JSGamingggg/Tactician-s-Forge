package com.example.data.teamplanner

import android.content.Context
import com.example.data.model.MetaComp
import com.example.data.model.TftEnvironment
import com.example.data.network.RiotApiClientFactory
import com.example.ui.components.TftAssetIconResolver
import com.example.ui.components.formatChampionDisplayName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
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
    val isVariant: Boolean = false,
    val isPlayableShopUnit: Boolean = true
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
    val loadedAtMillis: Long = System.currentTimeMillis(),
    val isThirdPartySiteFallback: Boolean = false
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
    const val CDRAGON_LIVE_CDN_TEAMPLANNER_URL =
        "https://cdn.communitydragon.org/latest/plugins/rcp-be-lol-game-data/global/default/v1/tftchampions-teamplanner.json"
    const val CDRAGON_LIVE_TFT_PLUGIN_TEAMPLANNER_URL =
        "https://raw.communitydragon.org/latest/plugins/rcp-be-tft-game-data/global/default/v1/tftchampions-teamplanner.json"
    const val CDRAGON_LIVE_CHAMPIONS_FALLBACK_URL =
        "https://raw.communitydragon.org/latest/plugins/rcp-be-lol-game-data/global/default/v1/tftchampions.json"

    const val CDRAGON_PBE_TEAMPLANNER_URL =
        "https://raw.communitydragon.org/pbe/plugins/rcp-be-lol-game-data/global/default/v1/tftchampions-teamplanner.json"
    const val CDRAGON_PBE_CDN_TEAMPLANNER_URL =
        "https://cdn.communitydragon.org/pbe/plugins/rcp-be-lol-game-data/global/default/v1/tftchampions-teamplanner.json"
    const val CDRAGON_PBE_TFT_PLUGIN_TEAMPLANNER_URL =
        "https://raw.communitydragon.org/pbe/plugins/rcp-be-tft-game-data/global/default/v1/tftchampions-teamplanner.json"
    const val CDRAGON_PBE_CHAMPIONS_FALLBACK_URL =
        "https://raw.communitydragon.org/pbe/plugins/rcp-be-lol-game-data/global/default/v1/tftchampions.json"
    const val CDRAGON_PBE_TFT_DATA_URL =
        "https://raw.communitydragon.org/pbe/cdragon/tft/en_us.json"
    const val METATFT_LOOKUPS_BASE_URL =
        "https://data.metatft.com/lookups"
    const val TFTACADEMY_ORIGIN = "https://tftacademy.com"

    /** Modern TFT sets (Set 14+, including Set 18 LIVE & Set 19 PBE) require Version 02 (12-bit 3-hex-digit IDs). */
    private const val MIN_V02_REQUIRED_SET = 14

    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/131.0 Mobile Safari/537.36"

    private const val LIVE_CACHE_FILE = "tft_teamplanner_live_v5.json"
    private const val PBE_CACHE_FILE = "tft_teamplanner_pbe_v9.json"
    private const val PRELOAD_SET18_ASSET = "set18_catalog_preload.json"
    private const val PRELOAD_SET19_ASSET = "set19_pbe_catalog_preload.json"
    private const val LIVE_CACHE_TTL_MS = 6L * 60L * 60L * 1000L // 6 hours
    private const val PBE_CACHE_TTL_MS = 3L * 60L * 1000L // 3 minutes (PBE updates frequently)

    /** Recognized placeholder suffix used by Riot PBE builds when a set tag is unset (`None`). */
    const val PBE_PLACEHOLDER_SUFFIX = "None"

    private val BARE_SET_SUFFIX_REGEX = Regex("""^Set(\d+)$""", RegexOption.IGNORE_CASE)
    private val VALID_V02_CODE_REGEX = Regex("""^0[2-9][0-9a-fA-F]{30}(?:TFTSet[A-Za-z0-9_-]+|TFT[A-Za-z0-9_-]+|Set\d+[A-Za-z0-9_-]*|None)$""")
    private val VALID_V01_CODE_REGEX = Regex("""^01[0-9a-fA-F]{20}(?:TFTSet[A-Za-z0-9_-]+|TFT[A-Za-z0-9_-]+|Set\d+[A-Za-z0-9_-]*|None)$""")
    private val EMBEDDED_V02_CODE_REGEX = Regex("""0[2-9][0-9a-fA-F]{30}(?:TFTSet[A-Za-z0-9_-]+|TFT[A-Za-z0-9_-]+|Set\d+[A-Za-z0-9_-]*|None)""")
    private val NUMERIC_OR_HEX_CODE_TOKEN_REGEX = Regex("""^(?:0x)?[0-9a-fA-F]{1,4}$""", RegexOption.IGNORE_CASE)
    private val SVELTEKIT_FETCHED_REGEX = Regex(
        """<script[^>]*data-sveltekit-fetched[^>]*>(.*?)</script>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    private val EXPLICIT_SET_NUMBER_REGEX = Regex("""(?:TFTSet|Set[\s_-]*)(\d+)""", RegexOption.IGNORE_CASE)
    private val FLEXIBLE_SET_KEY_NUMBER_REGEX = Regex("""^(?:TFTSet|Set|TFT|DA|PT)[\s_-]*(\d+)(?:[^0-9].*)?$""", RegexOption.IGNORE_CASE)
    private val PURE_SET_NUMBER_REGEX = Regex("""^(\d{1,2})$""")
    private val CHAR_ID_SET_NUMBER_REGEX = Regex("""^(?:TFTSet|TFT|DA|PT|Set)[\s_]*(\d{1,2})_|^DA_[A-Za-z]+(\d{2})(?:_|$)|^[A-Za-z]{2,6}_?(\d{2})_""", RegexOption.IGNORE_CASE)
    private val NON_PLAYABLE_UNIT_REGEX = Regex(
        """(?:dummy|prop|minion|summon|golem|training|chest|turret|herald|carousel|portal|crystal|tree|lifeblossom|stonebark|protector)""",
        RegexOption.IGNORE_CASE
    )
    private val GENERIC_CONTAINER_KEYS = setOf(
        "units", "champions", "characters", "entries", "items", "data", "results", "list", "roster", "setdata"
    )

    private val memoryCatalogs = ConcurrentHashMap<TftEnvironment, TeamPlannerCatalog>()
    private val siteFallbackCatalogs = ConcurrentHashMap<TftEnvironment, TeamPlannerCatalog>()
    private val directCompCodeCache = ConcurrentHashMap<String, String>()
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
        "graves" to listOf("graves", "tft19graves", "da19graves", "dagraves19"),
        "mordekaiser" to listOf("mordekaiser", "tft19mordekaiser", "da19mordekaiser", "damordekaiser19"),
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
        "risingstar" to listOf("seraphine", "seraphineindie", "tft19seraphine", "tft19seraphinerisingstar"),
        "superstar" to listOf("seraphine", "seraphineindie", "tft19seraphine", "tft19seraphinesuperstar"),
        "seraphineindie" to listOf("seraphine", "seraphineindie", "tft19seraphine", "tft19seraphineindie"),
        "seraphinerisingstar" to listOf("seraphine", "seraphineindie", "tft19seraphine", "tft19seraphinerisingstar"),
        "seraphinesuperstar" to listOf("seraphine", "seraphineindie", "tft19seraphine", "tft19seraphinesuperstar")
    )

    fun isValidTeamPlannerCode(raw: String?, expectedSetNumber: Int? = null): Boolean {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isBlank()) return false
        val suffix = extractSetKeyFromCode(trimmed)
        if (suffix.equals(PBE_PLACEHOLDER_SUFFIX, ignoreCase = true)) {
            if (expectedSetNumber != null && expectedSetNumber != 19) return false
            return VALID_V02_CODE_REGEX.matches(trimmed)
        }
        val codeSetNum = extractSetNumberFromKey(suffix)
        if (expectedSetNumber != null && codeSetNum != null && codeSetNum != expectedSetNumber) {
            return false
        }
        if (VALID_V02_CODE_REGEX.matches(trimmed)) {
            return true
        }
        if (VALID_V01_CODE_REGEX.matches(trimmed)) {
            // Reject legacy v01 codes for modern sets (Set 14+, including Set 18 LIVE & Set 19 PBE)
            val effectiveSet = expectedSetNumber ?: codeSetNum
            return effectiveSet == null || effectiveSet < MIN_V02_REQUIRED_SET
        }
        return false
    }

    /**
     * Returns the exact Set suffix currently published in Riot's live CommunityDragon
     * `tftchampions-teamplanner.json` for [environment] and [targetSetNum], if cached in memory.
     */
    fun getCachedAuthoritativeSetKey(environment: TftEnvironment, targetSetNum: Int): String? {
        val catalog = memoryCatalogs[environment] ?: return null
        if (!catalog.isFromRiotCommunityDragon || catalog.isThirdPartySiteFallback) return null
        return selectStrictSetMapping(catalog, targetSetNum, emptyList())?.setKey
    }

    /**
     * Extracts the set of non-empty 3-hex-digit champion slot IDs from a v02 Team Planner code.
     */
    internal fun extractNonEmptySlotHexes(code: String): Set<String> {
        val trimmed = code.trim()
        if (!VALID_V02_CODE_REGEX.matches(trimmed)) return emptySet()
        val slots = LinkedHashSet<String>(MAX_PLANNER_SLOTS)
        for (slot in 0 until MAX_PLANNER_SLOTS) {
            val start = 2 + slot * 3
            val hex = trimmed.substring(start, start + 3).lowercase(Locale.US)
            if (hex != EMPTY_SLOT_HEX_V2) {
                slots.add(hex)
            }
        }
        return slots
    }

    /**
     * Checks that every non-empty 3-hex-digit champion slot in a v02 Team Planner code
     * exists in [setMapping] (prevents pasting stale champion IDs after a PBE game update).
     */
    internal fun isCodeCompatibleWithSetMapping(
        code: String,
        setMapping: TeamPlannerSetMapping
    ): Boolean {
        val trimmed = code.trim()
        if (!VALID_V02_CODE_REGEX.matches(trimmed) || !setMapping.usesVersion02) return false
        val validSlots = setMapping.entries.mapTo(HashSet(setMapping.entries.size)) { it.slotHex }
        if (validSlots.isEmpty()) return false
        var nonEmptyCount = 0
        for (slot in 0 until MAX_PLANNER_SLOTS) {
            val start = 2 + slot * 3
            val hex = trimmed.substring(start, start + 3).lowercase(Locale.US)
            if (hex == EMPTY_SLOT_HEX_V2) continue
            nonEmptyCount++
            if (!validSlots.contains(hex)) return false
        }
        return nonEmptyCount > 0
    }

    /**
     * Verifies that a direct/cached v02 Team Planner code BOTH uses only valid slot IDs from [setMapping]
     * AND decodes to the exact same champion set as [unitCandidates].
     *
     * This catches subtle PBE renumbering / third-party site mismatches where a champion's old hex ID
     * (e.g. Graves) was reassigned to a different valid Set 19 champion (e.g. Mordekaiser).
     */
    internal fun doesCodeMatchCompUnits(
        code: String,
        unitCandidates: List<String>,
        setMapping: TeamPlannerSetMapping
    ): Boolean {
        if (!isCodeCompatibleWithSetMapping(code, setMapping)) return false
        val codeSlots = extractNonEmptySlotHexes(code)
        if (codeSlots.isEmpty()) return false

        val expectedSlots = LinkedHashSet<String>(MAX_PLANNER_SLOTS)
        var unmatchedCount = 0
        for (unitName in unitCandidates) {
            if (expectedSlots.size >= MAX_PLANNER_SLOTS) break
            val entry = matchChampionInSet(unitName, setMapping)
            if (entry != null) {
                expectedSlots.add(entry.slotHex)
            } else {
                unmatchedCount++
            }
        }
        if (expectedSlots.isEmpty()) return false
        if (unmatchedCount > 0 && codeSlots.size != expectedSlots.size) return false
        return codeSlots == expectedSlots
    }

    /**
     * Normalizes a valid Team Planner code for [environment] and [targetSetNum].
     *
     * Uses Riot's live authoritative set key from `tftchampions-teamplanner.json` when available
     * (automatically adapting if PBE switches between `None`, `TFTSet19`, or a codename across updates),
     * and upgrades bare site shorthand suffixes like `Set19` to `TFTSet19`.
     */
    fun normalizeCodeForTarget(
        rawCode: String,
        environment: TftEnvironment,
        targetSetNum: Int,
        authoritativeSetKey: String? = null
    ): String {
        val trimmed = rawCode.trim()
        if (!VALID_V02_CODE_REGEX.matches(trimmed)) {
            return normalizeCodeCasing(trimmed)
        }
        val hexPart = trimmed.substring(0, 32).lowercase(Locale.US)
        val rawSuffix = trimmed.substring(32)
        val resolvedAuthoritativeKey = authoritativeSetKey?.trim()?.takeIf { it.isNotBlank() }
            ?: getCachedAuthoritativeSetKey(environment, targetSetNum)
        val finalSuffix = when {
            !resolvedAuthoritativeKey.isNullOrBlank() -> resolvedAuthoritativeKey
            BARE_SET_SUFFIX_REGEX.matches(rawSuffix) -> {
                val num = BARE_SET_SUFFIX_REGEX.matchEntire(rawSuffix)?.groupValues?.getOrNull(1)
                if (!num.isNullOrBlank()) "TFTSet$num" else rawSuffix
            }
            else -> rawSuffix
        }
        return hexPart + finalSuffix
    }

    /**
     * Extracts the first valid Version 02 (`02` + 30 hex chars + `TFTSet<N>...`) Team Planner code
     * embedded in arbitrary text/JSON, optionally enforcing [expectedSetNumber].
     */
    fun extractFirstValidV02Code(text: String?, expectedSetNumber: Int? = null): String? {
        if (text.isNullOrBlank()) return null
        for (match in EMBEDDED_V02_CODE_REGEX.findAll(text)) {
            val candidate = match.value.trim()
            if (isValidTeamPlannerCode(candidate, expectedSetNumber)) {
                return normalizeCodeCasing(candidate)
            }
        }
        return null
    }

    /**
     * Refreshes both LIVE and PBE CommunityDragon `tftchampions-teamplanner.json` caches from network.
     */
    suspend fun refreshMappingsFromNetwork(
        context: Context,
        client: OkHttpClient = RiotApiClientFactory.publicOkHttp
    ): Boolean = withContext(Dispatchers.IO) {
        directCompCodeCache.clear()
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
     * Caches a verified v02 champion-to-planner-code catalog extracted from TFTAcademy
     * ONLY in the isolated third-party fallback map so it can NEVER poison or block
     * Riot's official CommunityDragon LIVE/PBE catalog cache.
     */
    fun cacheVerifiedCatalogFromSite(
        environment: TftEnvironment,
        catalog: TeamPlannerCatalog
    ) {
        if (catalog.sets.values.any { it.usesVersion02 && it.entries.isNotEmpty() }) {
            siteFallbackCatalogs[environment] = catalog.copy(isThirdPartySiteFallback = true)
        }
    }

    /**
     * Automatically attaches verified Version 02 Riot Team Planner codes to every composition
     * in [comps] whenever compositions are added or updated (in both LIVE and PBE).
     *
     * Priority order:
     * 1. Existing valid v02 code already pulled directly from TFTAcademy.
     * 2. Site-direct copy codes & champion planner table extracted from TFTAcademy (`/__data.json`,
     *    SvelteKit payloads, and JS chunks).
     * 3. Official Riot CommunityDragon / MetaTFT verified v02 fallback.
     */
    suspend fun enrichCompsWithTeamCodes(
        context: Context,
        comps: List<MetaComp>,
        environment: TftEnvironment,
        client: OkHttpClient = RiotApiClientFactory.publicOkHttp
    ): List<MetaComp> = withContext(Dispatchers.IO) {
        if (comps.isEmpty()) return@withContext comps
        val defaultSetNum = if (environment == TftEnvironment.PBE) 19 else 18
        val requiredSetNum = comps.firstNotNullOfOrNull { extractTargetSetNumber(it) } ?: defaultSetNum
        val extraNames = loadPreloadApiToDisplayMap(context)

        // Prioritize Riot's official CommunityDragon / MetaTFT catalog FIRST for BOTH LIVE and PBE
        // so all tft.ninja compositions receive authoritative v02 codes matching the live/PBE game client
        // without blocking on unrelated TFTAcademy site scrapes.
        val officialCatalog = ensureMappingLoaded(
            context = context,
            environment = environment,
            client = client,
            requiredSetNumber = requiredSetNum
        )
        val officialSetMapping = officialCatalog?.let { selectStrictSetMapping(it, requiredSetNum, emptyList()) }
        if (officialCatalog != null && officialSetMapping != null) {
            return@withContext comps.map { comp ->
                val targetSetNum = extractTargetSetNumber(comp)
                val unitCandidates = buildOrderedUnitCandidates(comp)
                val existing = comp.teamPlannerCode.trim()
                if (isValidTeamPlannerCode(existing, expectedSetNumber = targetSetNum) &&
                    doesCodeMatchCompUnits(existing, unitCandidates, officialSetMapping)
                ) {
                    val synced = normalizeCodeForTarget(
                        rawCode = existing,
                        environment = environment,
                        targetSetNum = targetSetNum,
                        authoritativeSetKey = officialSetMapping.setKey
                    )
                    directCompCodeCache[comp.id] = synced
                    if (synced == comp.teamPlannerCode) comp else comp.copy(teamPlannerCode = synced)
                } else {
                    val encoded = encodeCompWithCatalog(comp, officialCatalog)
                    if (encoded.isSuccess && isValidTeamPlannerCode(encoded.code, expectedSetNumber = targetSetNum)) {
                        directCompCodeCache[comp.id] = encoded.code
                        comp.copy(teamPlannerCode = encoded.code)
                    } else if (environment == TftEnvironment.LIVE &&
                        isValidTeamPlannerCode(existing, expectedSetNumber = targetSetNum) &&
                        isCodeCompatibleWithSetMapping(existing, officialSetMapping)
                    ) {
                        val synced = normalizeCodeForTarget(
                            rawCode = existing,
                            environment = environment,
                            targetSetNum = targetSetNum,
                            authoritativeSetKey = officialSetMapping.setKey
                        )
                        directCompCodeCache[comp.id] = synced
                        comp.copy(teamPlannerCode = synced)
                    } else if (environment == TftEnvironment.PBE && existing.isNotBlank()) {
                        // Never retain a PBE site code whose decoded slots contradict Riot's live PBE unit mapping
                        // (e.g. a stale code that inserts Mordekaiser instead of Graves).
                        comp.copy(teamPlannerCode = "")
                    } else {
                        comp
                    }
                }
            }
        }

        val needsCode = comps.any { comp ->
            val targetSetNum = extractTargetSetNumber(comp)
            !isValidTeamPlannerCode(comp.teamPlannerCode, expectedSetNumber = targetSetNum)
        }
        if (!needsCode) {
            return@withContext comps.map { comp ->
                val targetSetNum = extractTargetSetNumber(comp)
                val normalized = normalizeCodeForTarget(comp.teamPlannerCode, comp.environment, targetSetNum)
                directCompCodeCache[comp.id] = normalized
                if (normalized == comp.teamPlannerCode) comp else comp.copy(teamPlannerCode = normalized)
            }
        }

        // Fallback: check TFTAcademy site bundle if CommunityDragon & MetaTFT do not have this set yet
        val pageUrl = if (environment == TftEnvironment.PBE) {
            "$TFTACADEMY_ORIGIN/tierlist/comps/set$requiredSetNum"
        } else {
            "$TFTACADEMY_ORIGIN/tierlist/comps"
        }
        val siteBundle = runCatching {
            TftAcademySiteCodeExtractor.extractFromPageAndSite(
                pageUrl = pageUrl,
                pageHtml = null,
                expectedSetNumber = requiredSetNum,
                client = client,
                extraDisplayNames = extraNames,
                includeRemoteAssets = true
            )
        }.getOrNull()
        siteBundle?.siteCatalog?.let { cacheVerifiedCatalogFromSite(environment, it) }

        comps.map { comp ->
            val targetSetNum = extractTargetSetNumber(comp)
            if (isValidTeamPlannerCode(comp.teamPlannerCode, expectedSetNumber = targetSetNum)) {
                val normalized = normalizeCodeForTarget(comp.teamPlannerCode, comp.environment, targetSetNum)
                directCompCodeCache[comp.id] = normalized
                if (normalized == comp.teamPlannerCode) comp else comp.copy(teamPlannerCode = normalized)
            } else {
                val slugFromUrl = comp.sourceUrl.substringAfterLast('/').takeIf {
                    it.isNotBlank() && !it.equals("comps", true) && !it.equals("set$targetSetNum", true)
                }
                val directFromSite = siteBundle?.lookupDirectCode(
                    compSlug = slugFromUrl,
                    title = comp.title,
                    compId = comp.id
                )?.takeIf { isValidTeamPlannerCode(it, expectedSetNumber = targetSetNum) }
                if (directFromSite != null) {
                    val norm = normalizeCodeForTarget(directFromSite, comp.environment, targetSetNum)
                    directCompCodeCache[comp.id] = norm
                    comp.copy(teamPlannerCode = norm)
                } else {
                    val encodedFromSite = siteBundle?.siteCatalog?.let { encodeCompWithCatalog(comp, it) }
                    if (encodedFromSite != null && encodedFromSite.isSuccess &&
                        isValidTeamPlannerCode(encodedFromSite.code, expectedSetNumber = targetSetNum)
                    ) {
                        val norm = normalizeCodeForTarget(encodedFromSite.code, comp.environment, targetSetNum)
                        directCompCodeCache[comp.id] = norm
                        comp.copy(teamPlannerCode = norm)
                    } else {
                        comp
                    }
                }
            }
        }
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
        val diagnostics: List<String> = emptyList(),
        val cameFromNetwork: Boolean = false
    )

    private fun ensureMappingLoadedWithDiagnostics(
        context: Context,
        environment: TftEnvironment,
        client: OkHttpClient,
        requiredSetNumber: Int?,
        unitCandidates: List<String>,
        forceNetworkRefresh: Boolean = false
    ): FetchCatalogOutcome {
        val extraNames = loadPreloadApiToDisplayMap(context)
        val now = System.currentTimeMillis()
        if (environment == TftEnvironment.PBE) {
            purgeLegacyPbeCacheFiles(context)
        }

        fun TeamPlannerCatalog.satisfiesRequirement(): Boolean {
            if (!isFromRiotCommunityDragon || isThirdPartySiteFallback) return false
            if (requiredSetNumber == null) return sets.isNotEmpty()
            return selectStrictSetMapping(this, requiredSetNumber, unitCandidates) != null
        }

        val ttlMs = if (environment == TftEnvironment.PBE) PBE_CACHE_TTL_MS else LIVE_CACHE_TTL_MS

        if (!forceNetworkRefresh) {
            memoryCatalogs[environment]?.let { cached ->
                val isFresh = (now - cached.loadedAtMillis) < ttlMs
                if (isFresh && cached.satisfiesRequirement()) {
                    return FetchCatalogOutcome(cached, cameFromNetwork = false)
                }
            }
        }

        val diskCatalog = loadFromDiskCache(context, environment, extraNames)
        if (!forceNetworkRefresh && diskCatalog != null) {
            val isFresh = (now - diskCatalog.loadedAtMillis) < ttlMs
            if (isFresh && diskCatalog.satisfiesRequirement()) {
                memoryCatalogs[environment] = diskCatalog
                return FetchCatalogOutcome(diskCatalog, cameFromNetwork = false)
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
            return netOutcome.copy(cameFromNetwork = true)
        }

        // If offline or upstream missing set, still prefer any verified disk cache that contains the set
        if (diskCatalog != null && diskCatalog.satisfiesRequirement()) {
            memoryCatalogs[environment] = diskCatalog
            return FetchCatalogOutcome(diskCatalog, netOutcome.diagnostics, cameFromNetwork = false)
        }

        val fallbackMem = memoryCatalogs[environment]?.takeIf { it.satisfiesRequirement() }
        return FetchCatalogOutcome(
            catalog = fallbackMem ?: netOutcome.catalog,
            diagnostics = netOutcome.diagnostics,
            cameFromNetwork = netOutcome.cameFromNetwork
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

        val directCandidate = comp.teamPlannerCode.trim().takeIf {
            isValidTeamPlannerCode(it, expectedSetNumber = targetSetNum)
        } ?: directCompCodeCache[comp.id]?.takeIf {
            isValidTeamPlannerCode(it, expectedSetNumber = targetSetNum)
        }

        val diagnostics = mutableListOf<String>()
        val extraDisplayNames = loadPreloadApiToDisplayMap(context)

        // For PBE (where Riot updates champion IDs and set suffixes frequently across game patches),
        // always check Riot's live PBE tftchampions-teamplanner.json FIRST so codes match the current PBE client.
        if (comp.environment == TftEnvironment.PBE) {
            var pbeOutcome = ensureMappingLoadedWithDiagnostics(
                context = context,
                environment = TftEnvironment.PBE,
                client = client,
                requiredSetNumber = targetSetNum,
                unitCandidates = unitCandidates
            )
            diagnostics.addAll(pbeOutcome.diagnostics)

            // If we served a cached PBE mapping and either a comp unit wasn't matched OR the site code disagrees
            // with our cached mapping, force a fresh network pull from Riot PBE CommunityDragon right away.
            if (!pbeOutcome.cameFromNetwork && pbeOutcome.catalog != null) {
                val cachedSet = selectStrictSetMapping(pbeOutcome.catalog!!, targetSetNum, unitCandidates)
                val cachedEncoded = encodeCompWithCatalog(comp, pbeOutcome.catalog!!)
                val shouldForceRefresh = cachedSet == null ||
                    !cachedEncoded.isSuccess ||
                    cachedEncoded.skippedUnits.isNotEmpty() ||
                    (directCandidate != null && !doesCodeMatchCompUnits(directCandidate, unitCandidates, cachedSet))
                if (shouldForceRefresh) {
                    val refreshed = ensureMappingLoadedWithDiagnostics(
                        context = context,
                        environment = TftEnvironment.PBE,
                        client = client,
                        requiredSetNumber = targetSetNum,
                        unitCandidates = unitCandidates,
                        forceNetworkRefresh = true
                    )
                    if (refreshed.catalog != null) {
                        pbeOutcome = refreshed
                        diagnostics.addAll(refreshed.diagnostics)
                    }
                }
            }

            pbeOutcome.catalog?.let { pbeCatalog ->
                val riotSet = selectStrictSetMapping(pbeCatalog, targetSetNum, unitCandidates)
                if (riotSet != null) {
                    // Always encode authoritatively from Riot's live PBE build FIRST so renumbered/swapped
                    // third-party site codes (like Mordekaiser replacing Graves) are never pasted.
                    val encoded = encodeCompWithCatalog(comp, pbeCatalog)
                    if (encoded.isSuccess && encoded.skippedUnits.isEmpty()) {
                        if (directCandidate != null && doesCodeMatchCompUnits(directCandidate, unitCandidates, riotSet)) {
                            val synced = normalizeCodeForTarget(
                                rawCode = directCandidate,
                                environment = TftEnvironment.PBE,
                                targetSetNum = targetSetNum,
                                authoritativeSetKey = riotSet.setKey
                            )
                            directCompCodeCache[comp.id] = synced
                            return@withContext TeamPlannerCodeResult(
                                code = synced,
                                setKey = extractSetKeyFromCode(synced) ?: riotSet.setKey,
                                matchedChampions = encoded.matchedChampions,
                                skippedUnits = emptyList(),
                                isVerifiedRiotMapping = true
                            )
                        }
                        directCompCodeCache[comp.id] = encoded.code
                        return@withContext encoded
                    }
                    if (encoded.isSuccess) {
                        directCompCodeCache[comp.id] = encoded.code
                        return@withContext encoded
                    }
                    if (!encoded.errorMessage.isNullOrBlank()) {
                        diagnostics.add(encoded.errorMessage)
                    }
                }
            }

            // Fallback when CDragon PBE does not yet publish this set (or offline): use direct site code / tft.ninja page / TFTAcademy
            if (directCandidate != null) {
                val normalized = normalizeCodeForTarget(directCandidate, comp.environment, targetSetNum)
                val codeSetKey = extractSetKeyFromCode(normalized)
                directCompCodeCache[comp.id] = normalized
                return@withContext TeamPlannerCodeResult(
                    code = normalized,
                    setKey = codeSetKey ?: expectedSetKey,
                    matchedChampions = unitCandidates.take(MAX_PLANNER_SLOTS),
                    skippedUnits = emptyList(),
                    isVerifiedRiotMapping = true
                )
            }

            extractCodeFromCompSourceUrl(comp, targetSetNum, expectedSetKey, unitCandidates, client)?.let { ninjaRes ->
                directCompCodeCache[comp.id] = ninjaRes.code
                return@withContext ninjaRes
            }

            siteFallbackCatalogs[TftEnvironment.PBE]?.let { cachedSiteCatalog ->
                val siteEncoded = encodeCompWithCatalog(comp, cachedSiteCatalog)
                if (siteEncoded.isSuccess) {
                    directCompCodeCache[comp.id] = siteEncoded.code
                    return@withContext siteEncoded
                }
            }

            val academyResult = resolveFromTftAcademyLive(
                context = context,
                comp = comp,
                targetSetNum = targetSetNum,
                expectedSetKey = expectedSetKey,
                client = client,
                extraDisplayNames = extraDisplayNames,
                diagnostics = diagnostics
            )
            if (academyResult != null && academyResult.isSuccess) {
                directCompCodeCache[comp.id] = academyResult.code
                return@withContext academyResult
            }
        } else {
            // LIVE: 1. Direct valid v02 teamPlannerCode passthrough (or cached code) matching targetSetNum
            if (directCandidate != null) {
                val normalized = normalizeCodeForTarget(directCandidate, comp.environment, targetSetNum)
                val codeSetKey = extractSetKeyFromCode(normalized)
                directCompCodeCache[comp.id] = normalized
                return@withContext TeamPlannerCodeResult(
                    code = normalized,
                    setKey = codeSetKey ?: expectedSetKey,
                    matchedChampions = unitCandidates.take(MAX_PLANNER_SLOTS),
                    skippedUnits = emptyList(),
                    isVerifiedRiotMapping = true
                )
            }

            // LIVE: 2. Prioritize Riot's official CommunityDragon / MetaTFT verified v02 mapping FIRST
            // (fast, authoritative, works for every tft.ninja LIVE composition without slow TFTAcademy scrapes)
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
                    directCompCodeCache[comp.id] = encoded.code
                    return@withContext encoded
                }
                if (!encoded.errorMessage.isNullOrBlank()) {
                    diagnostics.add(encoded.errorMessage)
                }
            }

            // LIVE: 3. Check comp's own tft.ninja sourceUrl if it is a dedicated comp detail page
            extractCodeFromCompSourceUrl(comp, targetSetNum, expectedSetKey, unitCandidates, client)?.let { ninjaRes ->
                directCompCodeCache[comp.id] = ninjaRes.code
                return@withContext ninjaRes
            }

            // LIVE: 4. Final site fallback to TFTAcademy if Riot/MetaTFT don't have this set yet
            val academyResult = resolveFromTftAcademyLive(
                context = context,
                comp = comp,
                targetSetNum = targetSetNum,
                expectedSetKey = expectedSetKey,
                client = client,
                extraDisplayNames = extraDisplayNames,
                diagnostics = diagnostics
            )
            if (academyResult != null && academyResult.isSuccess) {
                directCompCodeCache[comp.id] = academyResult.code
                return@withContext academyResult
            }
        }

        // Cross-check sibling environment endpoint ONLY if it actually contains verified v02 targetSetNum
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
                directCompCodeCache[comp.id] = encodedAlt.code
                return@withContext encodedAlt
            }
        }

        // 5. NEVER emit legacy v01 or bogus synthetic codes for Set 14+ that fail to paste in Riot's TFT Team Planner
        val detail = diagnostics
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(3)
            .joinToString(" • ")
            .takeIf { it.isNotBlank() }
        val userError = if (detail != null) {
            "Riot $expectedSetKey v02 IDs not live yet ($detail)."
        } else {
            "Riot $expectedSetKey v02 IDs could not be loaded. Check connection and try again."
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
        val outputSetSuffix = chosenSet.setKey

        val codeBuilder = StringBuilder(2 + MAX_PLANNER_SLOTS * slotWidth + outputSetSuffix.length)
        codeBuilder.append(prefix)
        matchedEntries.forEach { entry ->
            codeBuilder.append(entry.slotHex)
        }
        repeat((MAX_PLANNER_SLOTS - matchedEntries.size).coerceAtLeast(0)) {
            codeBuilder.append(emptySlot)
        }
        codeBuilder.append(outputSetSuffix)

        return TeamPlannerCodeResult(
            code = codeBuilder.toString(),
            setKey = outputSetSuffix,
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
        requireExplicitCodes: Boolean = false,
        isThirdPartySiteFallback: Boolean = false
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
                loadedAtMillis = loadedAtMillis,
                isThirdPartySiteFallback = isThirdPartySiteFallback
            )
        }.getOrNull()
    }

    private fun extractChampionArrayFromSetNode(rawNode: Any, depth: Int = 0): JSONArray? {
        if (depth > 3) return null
        return when (rawNode) {
            is JSONArray -> rawNode
            is JSONObject -> {
                rawNode.optJSONArray("champions")
                    ?: rawNode.optJSONArray("units")
                    ?: rawNode.optJSONArray("entries")
                    ?: rawNode.optJSONArray("items")
                    ?: rawNode.optJSONArray("data")
                    ?: run {
                        // Recursively unwrap nested map containers (e.g. { "champions": { "TFT19_Ziggs": { ... } } })
                        val nestedContainerKeys = listOf("champions", "units", "entries", "data")
                        for (containerKey in nestedContainerKeys) {
                            val nestedObj = rawNode.optJSONObject(containerKey) ?: continue
                            val unwrapped = extractChampionArrayFromSetNode(nestedObj, depth + 1)
                            if (unwrapped != null && unwrapped.length() > 0) {
                                return unwrapped
                            }
                        }
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
                                            firstObj.has("teamPlannerCode") ||
                                            firstObj.has("planner_code") ||
                                            firstObj.has("plannerCode")
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
                                child.has("team_planner_id") ||
                                child.has("teamPlannerId") ||
                                child.has("character_id") ||
                                child.has("characterId") ||
                                child.has("character_record") ||
                                child.has("apiName") ||
                                child.has("display_name") ||
                                child.has("name") ||
                                child.has("path")
                            ) {
                                val clone = JSONObject(child.toString())
                                if (clone.optString("character_id").isBlank() &&
                                    clone.optString("characterId").isBlank() &&
                                    clone.optString("apiName").isBlank() &&
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
            val isPlayableShopUnit: Boolean,
            val hasNonEmptyTraits: Boolean = false,
            val hasValidShopTier: Boolean = false
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
            val tierVal = if (rec.has("tier")) rec.optInt("tier", -1) else -1
            val hasNonEmptyTraits = traitsArr != null && traitsArr.length() > 0
            val hasValidShopTier = costVal in 1..10 || tierVal in 1..10
            val hasExplicitEmptyTraits = traitsArr != null && traitsArr.length() == 0
            val hasInvalidCost = costVal == 0 || costVal >= 11 || tierVal == 0 || tierVal >= 11
            val isExplicitlyDisabled = rec.optBoolean("disabled", false) ||
                rec.optBoolean("hidden", false) ||
                outerObj.optBoolean("disabled", false) ||
                outerObj.optBoolean("hidden", false) ||
                (rec.has("enabled") && !rec.optBoolean("enabled", true)) ||
                (rec.has("is_playable") && !rec.optBoolean("is_playable", true)) ||
                (rec.has("playable") && !rec.optBoolean("playable", true))
            val isNonShopProp = NON_PLAYABLE_UNIT_REGEX.containsMatchIn(charId) && !hasNonEmptyTraits
            val isPlayableShopUnit = !hasExplicitEmptyTraits && !hasInvalidCost && !isNonShopProp && !isExplicitlyDisabled
            rawEntries.add(
                RawEntry(
                    characterId = charId.ifBlank { enrichedDisplayName },
                    displayName = enrichedDisplayName,
                    pathSlug = pathSlug,
                    rawCodeValue = rawCodeVal,
                    isVariant = isVariant,
                    isPlayableShopUnit = isPlayableShopUnit,
                    hasNonEmptyTraits = hasNonEmptyTraits,
                    hasValidShopTier = hasValidShopTier
                )
            )
        }

        if (rawEntries.isEmpty()) return null

        // Detect whether string `team_planner_code` values in this set are hex strings (e.g. "3f7", "429", "01a", "04a2")
        // vs decimal strings (e.g. "1015", "1065", "1173") using strictly valid numeric/hex tokens so unrelated strings
        // never flip the radix for the whole set.
        val stringCodeSamples = rawEntries.mapNotNull { raw ->
            (raw.rawCodeValue as? String)?.trim()?.takeIf { s ->
                s.isNotEmpty() && NUMERIC_OR_HEX_CODE_TOKEN_REGEX.matches(s)
            }
        }
        val hasFourDigitDecimalOnlySample = stringCodeSamples.any { s ->
            !s.startsWith("0x", ignoreCase = true) &&
                s.length == 4 &&
                s.all { it.isDigit() } &&
                (s.toIntOrNull(16) ?: Int.MAX_VALUE) > 0xFFF &&
                (s.toIntOrNull(10) ?: -1) in 1..0xFFF
        }
        val allStringsCompactHexLength = stringCodeSamples.isNotEmpty() && stringCodeSamples.all { it.length <= 3 }
        val stringsAreHex = !hasFourDigitDecimalOnlySample && stringCodeSamples.any { s ->
            s.startsWith("0x", ignoreCase = true) ||
                s.any { ch -> ch.lowercaseChar() in 'a'..'f' } ||
                (allStringsCompactHexLength && s.length in 2..3 && s.startsWith('0'))
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

        data class IndexedChampion(
            val entry: TeamPlannerChampionEntry,
            val pathSlug: String,
            val hasNonEmptyTraits: Boolean,
            val hasValidShopTier: Boolean,
            val isCanonicalBaseId: Boolean
        )

        val indexedChampions = mutableListOf<IndexedChampion>()
        effectiveRawEntries.forEachIndexed { idx, raw ->
            val codeInt = if (hasExplicitCodes) {
                effectiveExplicitCodes[idx]?.takeIf { it > 0 } ?: return@forEachIndexed
            } else {
                fallbackIndexByCharId[raw.characterId] ?: return@forEachIndexed
            }
            val slotHex = if (usesVersion02) formatSlotHex3(codeInt) else formatSlotHex2(codeInt)
            val entry = TeamPlannerChampionEntry(
                characterId = raw.characterId,
                displayName = raw.displayName,
                plannerCodeInt = codeInt,
                slotHex = slotHex,
                isVariant = raw.isVariant,
                isPlayableShopUnit = raw.isPlayableShopUnit
            )
            val underscoreCount = raw.characterId.count { it == '_' }
            val isCanonicalBaseId = raw.characterId.contains("base", ignoreCase = true) ||
                raw.characterId.endsWith("_indie", ignoreCase = true) ||
                underscoreCount <= 1
            indexedChampions.add(
                IndexedChampion(
                    entry = entry,
                    pathSlug = raw.pathSlug,
                    hasNonEmptyTraits = raw.hasNonEmptyTraits,
                    hasValidShopTier = raw.hasValidShopTier,
                    isCanonicalBaseId = isCanonicalBaseId
                )
            )
        }

        if (indexedChampions.isEmpty()) return null
        val entries = indexedChampions.map { it.entry }

        // Index playable base champions FIRST using strict 3-pass registration:
        // Pass 1: Authoritative identity keys (`character_id`, stripped `character_id`, `display_name`) for ALL entries.
        // Pass 2: Form-stripped / parenthetical-stripped keys for ALL entries.
        // Pass 3: Asset `pathSlug` fallback ONLY after every champion's own identity keys are claimed,
        // so a reused/placeholder PBE asset path on Mordekaiser can NEVER hijack Graves's lookup key!
        val lookup = HashMap<String, TeamPlannerChampionEntry>(entries.size * 8)
        val orderedForIndexing = indexedChampions.sortedWith(
            compareByDescending<IndexedChampion> { it.entry.isPlayableShopUnit }
                .thenBy { it.entry.isVariant }
                .thenByDescending { it.hasNonEmptyTraits }
                .thenByDescending { it.hasValidShopTier }
                .thenByDescending { it.isCanonicalBaseId }
        )
        orderedForIndexing.forEach { item ->
            indexAuthoritativeKeys(item.entry, lookup)
        }
        orderedForIndexing.forEach { item ->
            indexSecondaryNameKeys(item.entry, lookup)
        }
        orderedForIndexing.forEach { item ->
            if (item.pathSlug.isNotBlank()) {
                indexPathSlugKeys(item.entry, item.pathSlug, lookup)
            }
        }

        val trimmedSetKey = setKey.trim()
        val canonicalSetKey = when {
            PURE_SET_NUMBER_REGEX.matches(trimmedSetKey) -> "TFTSet$trimmedSetKey"
            setNum != null && trimmedSetKey.lowercase(Locale.US) in GENERIC_CONTAINER_KEYS -> "TFTSet$setNum"
            else -> trimmedSetKey
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
        val explicitKeys = listOf(
            "team_planner_code",
            "teamPlannerCode",
            "team_planner_code_v2",
            "teamPlannerCodeV2",
            "planner_code",
            "plannerCode",
            "team_planner_id",
            "teamPlannerId",
            "planner_id",
            "plannerId",
            "unit_code",
            "unitCode",
            "hex_code",
            "hexCode"
        )
        for (key in explicitKeys) {
            if (obj.has(key) && !obj.isNull(key)) {
                val value = obj.opt(key)
                if (value !is JSONObject && value !is JSONArray) {
                    return value
                }
            }
        }
        val nestedPlanner = obj.optJSONObject("team_planner") ?: obj.optJSONObject("teamPlanner")
        if (nestedPlanner != null) {
            for (nestedKey in listOf("team_planner_code", "teamPlannerCode", "code", "id", "value")) {
                if (nestedPlanner.has(nestedKey) && !nestedPlanner.isNull(nestedKey)) {
                    val value = nestedPlanner.opt(nestedKey)
                    if (value !is JSONObject && value !is JSONArray) {
                        return value
                    }
                }
            }
        }
        // Only accept generic `"code"` fallback when it is a valid numeric/hex code token (never arbitrary text)
        if (obj.has("code") && !obj.isNull("code")) {
            val value = obj.opt("code")
            val validGeneric = when (value) {
                is Number -> value.toInt() in 1..0xFFF
                is String -> NUMERIC_OR_HEX_CODE_TOKEN_REGEX.matches(value.trim())
                else -> false
            }
            if (validGeneric) return value
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

    private fun indexAuthoritativeKeys(
        entry: TeamPlannerChampionEntry,
        lookup: MutableMap<String, TeamPlannerChampionEntry>
    ) {
        val normCharId = normalizeKey(entry.characterId)
        if (normCharId.isNotBlank()) {
            lookup.putIfAbsent(normCharId, entry)
        }
        val stripped = stripRiotChampionPrefix(entry.characterId)
        if (stripped.isNotBlank()) {
            lookup.putIfAbsent(stripped, entry)
        }
        val normDisplay = normalizeKey(entry.displayName)
        if (normDisplay.isNotBlank()) {
            lookup.putIfAbsent(normDisplay, entry)
        }
    }

    private fun indexSecondaryNameKeys(
        entry: TeamPlannerChampionEntry,
        lookup: MutableMap<String, TeamPlannerChampionEntry>
    ) {
        val stripped = stripRiotChampionPrefix(entry.characterId)
        val strippedVariant = stripChampionFormSuffix(stripped)
        if (strippedVariant.length >= 2) {
            lookup.putIfAbsent(strippedVariant, entry)
        }
        val normDisplay = normalizeKey(entry.displayName)
        // Only bind parenthetical-stripped key (e.g., "Lux") if no base entry already claimed it
        val beforeParen = entry.displayName.substringBefore('(').trim()
        val normBeforeParen = normalizeKey(beforeParen)
        if (normBeforeParen.length >= 2 && normBeforeParen != normDisplay) {
            lookup.putIfAbsent(normBeforeParen, entry)
        }
    }

    private fun indexPathSlugKeys(
        entry: TeamPlannerChampionEntry,
        pathSlug: String,
        lookup: MutableMap<String, TeamPlannerChampionEntry>
    ) {
        val normSlug = stripRiotChampionPrefix(pathSlug)
        if (normSlug.length >= 2) {
            lookup.putIfAbsent(normSlug, entry)
        }
        val slugVariant = stripChampionFormSuffix(normSlug)
        if (slugVariant.length >= 2) {
            lookup.putIfAbsent(slugVariant, entry)
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

        val stripped = stripRiotChampionPrefix(unitName)

        // 0. Exact set-prefixed Riot `character_id` lookup FIRST (e.g. "tft19graves" for Set 19 Graves)
        // so no other champion's display alias or fallback slug can ever shadow an exact TFT<Set>_<Champion> ID.
        val setNum = setMapping.setNumber
        if (setNum != null && stripped.isNotBlank()) {
            setMapping.lookupByNormalizedKey["tft$setNum$stripped"]?.let { return it }
            setMapping.lookupByNormalizedKey["da$setNum$stripped"]?.let { return it }
        }

        // 1. Direct normalized match (display_name or character_id)
        setMapping.lookupByNormalizedKey[norm]?.let { return it }

        // 2. Stripped Riot API prefix match (e.g. DA_18_Ezreal -> ezreal, TFT19_Seraphine_Indie -> seraphine)
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
     * 1. Exact canonical key (`TFTSet18`, `TFTSet19`) — preferring the set that matches the most comp units
     * 2. Same set number via key (`TFTSet19_Act1`, `TFT19`, `19`) OR `character_id` prefix (`TFT19_*`)
     * 3. Riot PBE Set 19 placeholder set key (`None`)
     * 4. Unnumbered Riot PBE codename sets (e.g. `TFTSetPrismatic`, `TFTSetTour`) that match >= 2 comp units
     */
    private fun selectStrictSetMapping(
        catalog: TeamPlannerCatalog,
        targetSetNumber: Int,
        unitCandidates: List<String>
    ): TeamPlannerSetMapping? {
        if (catalog.sets.isEmpty()) return null
        val mustUseVersion02 = targetSetNumber >= MIN_V02_REQUIRED_SET
        val eligibleSets = if (mustUseVersion02) {
            catalog.sets.values.filter { it.usesVersion02 }
        } else {
            catalog.sets.values.toList()
        }
        if (eligibleSets.isEmpty()) return null

        // 1 & 2. Gather all keys belonging to targetSetNumber (exact `TFTSet<N>`, inferred `TFT<N>_*`,
        // or Set 19's PBE placeholder `None`) and score them by comp unit coverage + canonical key preference.
        val sameSetCandidates = eligibleSets.filter { mapping ->
            mapping.setKey.equals("TFTSet$targetSetNumber", ignoreCase = true) ||
                mapping.setNumber == targetSetNumber ||
                (targetSetNumber == 19 &&
                    mapping.setKey.equals(PBE_PLACEHOLDER_SUFFIX, ignoreCase = true) &&
                    mapping.entries.isNotEmpty())
        }
        if (sameSetCandidates.isNotEmpty()) {
            return sameSetCandidates.maxByOrNull { mapping ->
                val matchCount = unitCandidates.count { matchChampionInSet(it, mapping) != null }
                val exactKeyBonus = if (mapping.setKey.equals("TFTSet$targetSetNumber", ignoreCase = true)) 150 else 0
                val nonEventBonus = if (!isAuxiliarySetKey(mapping.setKey)) 500 else 0
                val nonPlaceholderBonus = if (!mapping.setKey.equals(PBE_PLACEHOLDER_SUFFIX, ignoreCase = true)) 80 else 0
                val v2Bonus = if (mapping.usesVersion02) 1000 else 0
                val rosterBonus = mapping.entries.size.coerceAtMost(200)
                matchCount * 250 + exactKeyBonus + nonEventBonus + nonPlaceholderBonus + v2Bonus + rosterBonus
            }
        }

        // 3. Unnumbered codename set key (setNumber == null, never an older numbered set like TFTSet10/TFTSet18)
        if (unitCandidates.isNotEmpty()) {
            val minRequiredMatches = minOf(2, unitCandidates.size)
            val codenameCandidates = eligibleSets
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
        val seenNorm = HashSet<String>()
        fun addIfChampion(raw: String) {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return
            if (TftAssetIconResolver.isKnownNonChampionAsset(trimmed, comp.environment)) return
            val clean = formatChampionDisplayName(trimmed).ifBlank { trimmed }
            val norm = normalizeKey(clean)
            if (norm.isNotEmpty() && seenNorm.add(norm)) {
                ordered.add(clean)
            }
        }
        if (comp.carryName.isNotBlank()) {
            addIfChampion(comp.carryName)
        }
        // Prioritize carry, itemized units, and primary board units (first 10 slots) ahead of late-appended
        // flex/optional units so a 5-cost flex unit (e.g. Mordekaiser) can never push a core unit (e.g. Graves)
        // out of the 10 Team Planner slots.
        comp.hexPlacements
            .withIndex()
            .sortedWith(
                compareByDescending<IndexedValue<com.example.data.model.HexUnitPlacement>> { it.value.isCarry }
                    .thenByDescending { it.value.items.size }
                    .thenByDescending { it.index < MAX_PLANNER_SLOTS }
                    .thenByDescending { it.value.cost }
                    .thenBy { it.index }
            )
            .forEach { indexed -> addIfChampion(indexed.value.unitName) }
        comp.coreUnits.forEach { unit -> addIfChampion(unit) }
        return ordered.toList()
    }

    /**
     * Checks a dedicated comp detail URL (such as `https://tft.ninja/comps/<slug>` or
     * `https://tft.ninja/comps/<slug>?e=pbe`) for an embedded v02 Team Planner code.
     */
    private fun extractCodeFromCompSourceUrl(
        comp: MetaComp,
        targetSetNum: Int,
        expectedSetKey: String,
        unitCandidates: List<String>,
        client: OkHttpClient
    ): TeamPlannerCodeResult? {
        val url = comp.sourceUrl.trim()
        if (!url.startsWith("https://tft.ninja/comps/", ignoreCase = true)) return null
        val httpClient = client.newBuilder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
        val body = runCatching {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("Accept", "text/html,application/json,*/*")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string()?.takeIf { it.isNotBlank() } else null
            }
        }.getOrNull() ?: return null
        val found = extractFirstValidV02Code(body, expectedSetNumber = targetSetNum) ?: return null
        val normalized = normalizeCodeForTarget(found, comp.environment, targetSetNum)
        return TeamPlannerCodeResult(
            code = normalized,
            setKey = extractSetKeyFromCode(normalized) ?: expectedSetKey,
            matchedChampions = unitCandidates.take(MAX_PLANNER_SLOTS),
            skippedUnits = emptyList(),
            isVerifiedRiotMapping = true
        )
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
        val targetSet = requiredSetNumber ?: if (environment == TftEnvironment.PBE) 19 else 18
        val requireV02 = targetSet >= MIN_V02_REQUIRED_SET
        val endpoints = if (environment == TftEnvironment.PBE) {
            listOf(
                CandidateEndpoint(CDRAGON_PBE_TEAMPLANNER_URL, "pbe-planner", requireV02),
                CandidateEndpoint(CDRAGON_PBE_CDN_TEAMPLANNER_URL, "pbe-cdn", requireV02),
                CandidateEndpoint(CDRAGON_PBE_TFT_PLUGIN_TEAMPLANNER_URL, "pbe-tft-plugin", requireV02),
                CandidateEndpoint(CDRAGON_PBE_CHAMPIONS_FALLBACK_URL, "pbe-champs", requireExplicitCodes = true),
                CandidateEndpoint("$METATFT_LOOKUPS_BASE_URL/TFTSet${targetSet}_pbe_en_us.json", "metatft-pbe", requireExplicitCodes = true),
                CandidateEndpoint("$METATFT_LOOKUPS_BASE_URL/TFTSet${targetSet}_latest_en_us.json", "metatft-latest", requireExplicitCodes = true),
                CandidateEndpoint(CDRAGON_LIVE_TEAMPLANNER_URL, "live-planner", requireV02),
                CandidateEndpoint(CDRAGON_LIVE_CDN_TEAMPLANNER_URL, "live-cdn", requireV02)
            )
        } else {
            listOf(
                CandidateEndpoint(CDRAGON_LIVE_TEAMPLANNER_URL, "live-planner", requireV02),
                CandidateEndpoint(CDRAGON_LIVE_CDN_TEAMPLANNER_URL, "live-cdn", requireV02),
                CandidateEndpoint(CDRAGON_LIVE_TFT_PLUGIN_TEAMPLANNER_URL, "live-tft-plugin", requireV02),
                CandidateEndpoint(CDRAGON_PBE_TEAMPLANNER_URL, "pbe-planner", requireV02),
                CandidateEndpoint("$METATFT_LOOKUPS_BASE_URL/TFTSet${targetSet}_latest_en_us.json", "metatft-latest", requireExplicitCodes = true),
                CandidateEndpoint(CDRAGON_LIVE_CHAMPIONS_FALLBACK_URL, "live-champs", requireExplicitCodes = true)
            )
        }

        val httpClient = client.newBuilder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        val diagnostics = mutableListOf<String>()
        var firstParsedFallback: TeamPlannerCatalog? = null
        var bestPartialCatalog: TeamPlannerCatalog? = null
        var bestPartialBody: String? = null
        var bestPartialMatchCount = -1
        val targetMatchGoal = minOf(unitCandidates.size, MAX_PLANNER_SLOTS)

        for (endpoint in endpoints) {
            // Never let live fallback endpoints override an already-matched PBE catalog
            if (environment == TftEnvironment.PBE &&
                bestPartialCatalog != null &&
                endpoint.label.startsWith("live-")
            ) {
                break
            }

            val req = Request.Builder()
                .url(endpoint.url)
                .cacheControl(CacheControl.FORCE_NETWORK)
                .header("Cache-Control", "no-cache, no-store, max-age=0")
                .header("Pragma", "no-cache")
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
                diagnostics.add("${endpoint.label}: no v02 IDs")
                continue
            }

            if (firstParsedFallback == null) {
                firstParsedFallback = parsed
            }

            val matchedSet = if (requiredSetNumber == null) {
                parsed.sets.values.firstOrNull { !requireV02 || it.usesVersion02 }
            } else {
                selectStrictSetMapping(parsed, requiredSetNumber, unitCandidates)
            }

            if (matchedSet != null && (!requireV02 || matchedSet.usesVersion02)) {
                val unitMatchCount = if (targetMatchGoal > 0) {
                    unitCandidates.take(MAX_PLANNER_SLOTS).count { matchChampionInSet(it, matchedSet) != null }
                } else {
                    0
                }
                if (targetMatchGoal == 0 || unitMatchCount >= targetMatchGoal) {
                    memoryCatalogs[environment] = parsed
                    runCatching {
                        cacheFileFor(context, environment).writeText(body, Charsets.UTF_8)
                    }
                    return FetchCatalogOutcome(parsed, diagnostics, cameFromNetwork = true)
                }
                // Partial unit match (e.g. a PBE mirror missing a newly-added champion): record and check
                // remaining PBE mirrors to see if another official source has the complete roster.
                if (unitMatchCount > bestPartialMatchCount) {
                    bestPartialMatchCount = unitMatchCount
                    bestPartialCatalog = parsed
                    bestPartialBody = body
                }
            } else {
                val availableKeys = parsed.sets.keys.toList().takeLast(4).joinToString(",")
                diagnostics.add("${endpoint.label}: sets [$availableKeys]")
            }
        }

        if (bestPartialCatalog != null) {
            memoryCatalogs[environment] = bestPartialCatalog
            if (!bestPartialBody.isNullOrBlank()) {
                runCatching {
                    cacheFileFor(context, environment).writeText(bestPartialBody, Charsets.UTF_8)
                }
            }
            return FetchCatalogOutcome(bestPartialCatalog, diagnostics, cameFromNetwork = true)
        }

        if (firstParsedFallback != null) {
            memoryCatalogs[environment] = firstParsedFallback
        }
        return FetchCatalogOutcome(firstParsedFallback, diagnostics, cameFromNetwork = firstParsedFallback != null)
    }

    /**
     * Live on-demand fallback to TFTAcademy when CommunityDragon/MetaTFT have not yet published
     * v02 `team_planner_code` IDs for a brand-new PBE set (like Set 19).
     *
     * Checks:
     * 1. Dedicated comp guide page (`comp.sourceUrl` or slug URL) for a direct `02...TFTSet<N>` code.
     * 2. TFTAcademy Set tierlist page / APIs (`/api/tierlist/comps?set=<N>`, `/api/units?set=<N>`,
     *    `/api/champions?set=<N>`) for EITHER a matching comp's `02...TFTSet<N>` code OR a Set
     *    champion table containing explicit v02 `team_planner_code` IDs.
     */
    private fun resolveFromTftAcademyLive(
        context: Context,
        comp: MetaComp,
        targetSetNum: Int,
        expectedSetKey: String,
        client: OkHttpClient,
        extraDisplayNames: Map<String, String>,
        diagnostics: MutableList<String>
    ): TeamPlannerCodeResult? {
        val httpClient = client.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        fun fetchText(url: String): String? = runCatching {
            val req = Request.Builder()
                .url(url)
                .cacheControl(CacheControl.FORCE_NETWORK)
                .header("Cache-Control", "no-cache")
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("Accept", "application/json, text/html, */*")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string()?.takeIf { it.isNotBlank() } else null
            }
        }.getOrNull()

        val rawSlug = comp.id
            .removePrefix("tftninja_pbe_")
            .removePrefix("tftninja_live_")
            .removePrefix("tftacademy_pbe_")
            .removePrefix("tftacademy_live_")
            .replace('_', '-')
            .trim('-')
        val titleSlug = comp.title.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')

        // 1. Check dedicated single-comp guide page & `/__data.json` first (exact 1:1 match to site's copy button)
        val singleGuideCode = runCatching {
            TftAcademySiteCodeExtractor.extractSingleCompCode(
                comp = comp,
                expectedSetNumber = targetSetNum,
                client = client
            )
        }.getOrNull()
        if (!singleGuideCode.isNullOrBlank() && isValidTeamPlannerCode(singleGuideCode, expectedSetNumber = targetSetNum)) {
            val normalized = normalizeCodeForTarget(singleGuideCode, comp.environment, targetSetNum)
            directCompCodeCache[comp.id] = normalized
            return TeamPlannerCodeResult(
                code = normalized,
                setKey = extractSetKeyFromCode(normalized) ?: expectedSetKey,
                matchedChampions = comp.coreUnits.distinct().take(MAX_PLANNER_SLOTS),
                skippedUnits = emptyList(),
                isVerifiedRiotMapping = true
            )
        }

        // 2. Inspect TFTAcademy's SvelteKit tierlist page, `/__data.json`, and JS chunks for exact site code or champion table
        val tierlistPageUrl = if (comp.environment == TftEnvironment.PBE) {
            "$TFTACADEMY_ORIGIN/tierlist/comps/set$targetSetNum"
        } else {
            "$TFTACADEMY_ORIGIN/tierlist/comps"
        }
        val siteBundle = runCatching {
            TftAcademySiteCodeExtractor.extractFromPageAndSite(
                pageUrl = tierlistPageUrl,
                pageHtml = null,
                expectedSetNumber = targetSetNum,
                client = client,
                extraDisplayNames = extraDisplayNames,
                includeRemoteAssets = true
            )
        }.getOrNull()
        if (siteBundle != null) {
            val slugFromUrl = comp.sourceUrl.substringAfterLast('/').takeIf {
                it.isNotBlank() && !it.equals("comps", true) && !it.equals("set$targetSetNum", true)
            }
            val directSiteCode = siteBundle.lookupDirectCode(
                compSlug = slugFromUrl,
                title = comp.title,
                compId = comp.id
            )?.takeIf { isValidTeamPlannerCode(it, expectedSetNumber = targetSetNum) }
            if (directSiteCode != null) {
                val normalized = normalizeCodeForTarget(directSiteCode, comp.environment, targetSetNum)
                directCompCodeCache[comp.id] = normalized
                return TeamPlannerCodeResult(
                    code = normalized,
                    setKey = extractSetKeyFromCode(normalized) ?: expectedSetKey,
                    matchedChampions = comp.coreUnits.distinct().take(MAX_PLANNER_SLOTS),
                    skippedUnits = emptyList(),
                    isVerifiedRiotMapping = true
                )
            }
            siteBundle.siteCatalog?.let { siteCatalog ->
                cacheVerifiedCatalogFromSite(comp.environment, siteCatalog)
                val encoded = encodeCompWithCatalog(comp, siteCatalog)
                if (encoded.isSuccess) {
                    return encoded
                }
            }
        }

        // 3. Check TFTAcademy Set JSON APIs for explicit v02 champion codes or matching comp team code
        val normalizedTargetTitle = normalizeKey(comp.title)
        val normalizedTargetSlug = normalizeKey(rawSlug)
        val setUrls = listOf(
            "$TFTACADEMY_ORIGIN/api/tierlist/comps?set=$targetSetNum",
            "$TFTACADEMY_ORIGIN/api/units?set=$targetSetNum",
            "$TFTACADEMY_ORIGIN/api/champions?set=$targetSetNum"
        )

        for (url in setUrls) {
            val body = fetchText(url) ?: continue
            val jsonPayloads = extractJsonPayloadsFromBody(body)
            for (payload in jsonPayloads) {
                // 2a. Check if payload contains a v02 champion planner code table for targetSetNum
                val parsedCatalog = parseCommunityDragonJson(
                    rawJson = payload,
                    isFromRiotCommunityDragon = true,
                    extraDisplayNameByNormalizedId = extraDisplayNames,
                    requireExplicitCodes = true,
                    isThirdPartySiteFallback = true
                )
                if (parsedCatalog != null) {
                    val encoded = encodeCompWithCatalog(comp, parsedCatalog)
                    if (encoded.isSuccess) {
                        cacheVerifiedCatalogFromSite(comp.environment, parsedCatalog)
                        return encoded
                    }
                }

                // 2b. Check if payload contains a matching comp JSON node with an embedded v02 code
                val matchedCode = findCompV02CodeInJsonPayload(
                    payload = payload,
                    normalizedTargetTitle = normalizedTargetTitle,
                    normalizedTargetSlug = normalizedTargetSlug,
                    expectedSetNumber = targetSetNum
                )
                if (matchedCode != null) {
                    val normalized = normalizeCodeForTarget(matchedCode, comp.environment, targetSetNum)
                    directCompCodeCache[comp.id] = normalized
                    return TeamPlannerCodeResult(
                        code = normalized,
                        setKey = extractSetKeyFromCode(normalized) ?: expectedSetKey,
                        matchedChampions = comp.coreUnits.distinct().take(MAX_PLANNER_SLOTS),
                        skippedUnits = emptyList(),
                        isVerifiedRiotMapping = true
                    )
                }
            }
        }

        diagnostics.add("tftacademy: no $expectedSetKey v02 code")
        return null
    }

    private fun extractJsonPayloadsFromBody(body: String): List<String> {
        val trimmed = body.trim()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return listOf(trimmed)
        }
        val payloads = mutableListOf<String>()
        SVELTEKIT_FETCHED_REGEX.findAll(body).forEach { m ->
            val rawEnvelope = m.groupValues.getOrNull(1)?.trim().orEmpty()
            if (rawEnvelope.isNotEmpty()) {
                runCatching {
                    val envelope = JSONObject(rawEnvelope)
                    val innerBody = envelope.optString("body").trim()
                    if (innerBody.startsWith("{") || innerBody.startsWith("[")) {
                        payloads.add(innerBody)
                    } else {
                        payloads.add(rawEnvelope)
                    }
                }
            }
        }
        return payloads
    }

    private fun findCompV02CodeInJsonPayload(
        payload: String,
        normalizedTargetTitle: String,
        normalizedTargetSlug: String,
        expectedSetNumber: Int
    ): String? {
        val root: Any = runCatching {
            val t = payload.trim()
            if (t.startsWith("[")) JSONArray(t) else JSONObject(t)
        }.getOrNull() ?: return null

        fun scanForCode(node: Any?, depth: Int): String? {
            if (node == null || depth > 5) return null
            return when (node) {
                is String -> extractFirstValidV02Code(node, expectedSetNumber)
                is JSONObject -> {
                    val keys = node.keys()
                    while (keys.hasNext()) {
                        scanForCode(node.opt(keys.next()), depth + 1)?.let { return it }
                    }
                    null
                }
                is JSONArray -> {
                    for (i in 0 until node.length().coerceAtMost(40)) {
                        scanForCode(node.opt(i), depth + 1)?.let { return it }
                    }
                    null
                }
                else -> null
            }
        }

        fun walk(node: Any?, depth: Int): String? {
            if (node == null || depth > 12) return null
            when (node) {
                is JSONObject -> {
                    val title = listOf(
                        node.optString("title"),
                        node.optString("compTitle"),
                        node.optString("guideTitle"),
                        node.optString("name"),
                        node.optString("compName")
                    ).firstOrNull { it.isNotBlank() }.orEmpty()
                    val slug = listOf(
                        node.optString("compSlug"),
                        node.optString("slug")
                    ).firstOrNull { it.isNotBlank() }.orEmpty()
                    val titleMatches = title.isNotBlank() && normalizeKey(title) == normalizedTargetTitle
                    val slugMatches = slug.isNotBlank() && normalizeKey(slug) == normalizedTargetSlug
                    if (titleMatches || slugMatches) {
                        scanForCode(node, 0)?.let { return it }
                    }
                    val keys = node.keys()
                    while (keys.hasNext()) {
                        walk(node.opt(keys.next()), depth + 1)?.let { return it }
                    }
                }
                is JSONArray -> {
                    for (i in 0 until node.length().coerceAtMost(400)) {
                        walk(node.opt(i), depth + 1)?.let { return it }
                    }
                }
            }
            return null
        }

        return walk(root, 0)
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

    private fun purgeLegacyPbeCacheFiles(context: Context) {
        runCatching {
            context.applicationContext.filesDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("tft_teamplanner_pbe_") && file.name != PBE_CACHE_FILE) {
                    file.delete()
                }
            }
        }
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
        siteFallbackCatalogs.clear()
        directCompCodeCache.clear()
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
            .replace(Regex("""^(?:TFTSet|TFT|DA|PT)[\s_]*\d*[\s_]*""", RegexOption.IGNORE_CASE), "")
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
        val rawNum = m.groupValues[1].ifBlank { m.groupValues[2] }.ifBlank { m.groupValues[3] }
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
        val trimmed = code.trim()
        if (VALID_V02_CODE_REGEX.matches(trimmed) && trimmed.length > 32) {
            return trimmed.substring(32)
        }
        if (VALID_V01_CODE_REGEX.matches(trimmed) && trimmed.length > 22) {
            return trimmed.substring(22)
        }
        val idx = trimmed.indexOf("TFTSet", ignoreCase = true)
            .takeIf { it >= 0 }
            ?: trimmed.indexOf("TFT", ignoreCase = true)
                .takeIf { it >= 0 }
            ?: trimmed.indexOf("Set", ignoreCase = true)
                .takeIf { it >= 22 }
            ?: trimmed.indexOf(PBE_PLACEHOLDER_SUFFIX, ignoreCase = true)
                .takeIf { it >= 22 }
        return if (idx != null && idx >= 0) trimmed.substring(idx) else null
    }

    private fun normalizeCodeCasing(code: String): String {
        val trimmed = code.trim()
        if (VALID_V02_CODE_REGEX.matches(trimmed) && trimmed.length > 32) {
            return trimmed.substring(0, 32).lowercase(Locale.US) + trimmed.substring(32)
        }
        if (VALID_V01_CODE_REGEX.matches(trimmed) && trimmed.length > 22) {
            return trimmed.substring(0, 22).lowercase(Locale.US) + trimmed.substring(22)
        }
        val idx = trimmed.indexOf("TFTSet", ignoreCase = true)
            .takeIf { it >= 0 }
            ?: trimmed.indexOf("TFT", ignoreCase = true)
        if (idx < 0) return trimmed
        val hexPart = trimmed.substring(0, idx).lowercase(Locale.US)
        val setSuffix = trimmed.substring(idx)
        return hexPart + setSuffix
    }
}
