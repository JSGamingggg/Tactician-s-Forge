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

data class TeamPlannerChampionEntry(
    val characterId: String,
    val displayName: String,
    val plannerCodeInt: Int,
    val hex3: String
)

data class TeamPlannerSetMapping(
    val setKey: String,
    val setNumber: Int?,
    val entries: List<TeamPlannerChampionEntry>,
    val lookupByNormalizedKey: Map<String, TeamPlannerChampionEntry>
)

data class TeamPlannerCatalog(
    val sets: Map<String, TeamPlannerSetMapping>,
    val isFromRiotCommunityDragon: Boolean
)

data class TeamPlannerCodeResult(
    val code: String,
    val setKey: String,
    val matchedChampions: List<String>,
    val skippedUnits: List<String>,
    val isVerifiedRiotMapping: Boolean
)

/**
 * Generates official Riot in-game TFT Team Planner import codes (Version 02):
 *
 *   `02` + (10 slots × 3 lowercase hex digits of `team_planner_code`, padded with `000`) + `<TFTSetKey>`
 *
 * Champion codes and exact set identifiers are sourced directly from Riot's
 * CommunityDragon `tftchampions-teamplanner.json` (LIVE & PBE) and cached locally
 * so codes paste directly into the in-game TFT Team Planner both online and offline.
 */
object TftTeamPlannerCodeEncoder {

    const val VERSION_PREFIX = "02"
    const val MAX_PLANNER_SLOTS = 10
    const val EMPTY_SLOT_HEX = "000"

    const val CDRAGON_LIVE_TEAMPLANNER_URL =
        "https://raw.communitydragon.org/latest/plugins/rcp-be-lol-game-data/global/default/v1/tftchampions-teamplanner.json"
    const val CDRAGON_PBE_TEAMPLANNER_URL =
        "https://raw.communitydragon.org/pbe/plugins/rcp-be-lol-game-data/global/default/v1/tftchampions-teamplanner.json"

    private const val LIVE_CACHE_FILE = "tft_teamplanner_live.json"
    private const val PBE_CACHE_FILE = "tft_teamplanner_pbe.json"
    private const val PRELOAD_SET18_ASSET = "set18_catalog_preload.json"
    private const val PRELOAD_SET19_PBE_ASSET = "set19_pbe_catalog_preload.json"

    private val VALID_RIOT_TEAM_CODE_REGEX = Regex("""^0[12][0-9a-fA-F]{20,30}TFTSet[A-Za-z0-9_]+$""")
    private val SET_NUMBER_REGEX = Regex("""(?:TFTSet|Set\s*)(\d+)""", RegexOption.IGNORE_CASE)

    private val memoryCatalogs = ConcurrentHashMap<TftEnvironment, TeamPlannerCatalog>()

    /**
     * Explicit aliases between Set 18/19 display names and Riot internal `character_id` tokens
     * (e.g., TFTAcademy displays "Pebbles" for `DA_18_Sentry`, "Mama Beak" for `DA_CrimsonRaptor18`).
     */
    private val championLookupAliases: Map<String, List<String>> = mapOf(
        "pebbles" to listOf("sentry", "da18sentry", "tft18sentry"),
        "mamabeak" to listOf("crimsonraptor", "dacrimsonraptor18", "tft18crimsonraptor"),
        "gnar" to listOf("gnarsmall", "da18gnarsmall", "tft18gnarsmall"),
        "lux" to listOf("luxbase", "dalux18base", "tft18lux"),
        "masteryi" to listOf("masteryiad", "da18masteryiad", "tft18masteryi"),
        "kogmaw" to listOf("kogmawad", "dakogmaw18ad", "tft18kogmaw"),
        "nidalee" to listOf("nidaleeap", "danidalee18ap", "tft18nidalee"),
        "gromp" to listOf("grompap", "dagromp18ap", "tft18gromp"),
        "akali" to listOf("akaliad", "da18akaliad", "tft18akali"),
        "reksai" to listOf("reksai", "da18reksai", "tft18reksai"),
        "khazix" to listOf("khazix", "da18khazix", "tft18khazix"),
        "drmundo" to listOf("drmundo", "mundo", "tft19drmundo"),
        "ksante" to listOf("ksante", "tft19ksante"),
        "kaisa" to listOf("kaisa", "tft19kaisa"),
        "leesin" to listOf("leesin", "tft19leesin"),
        "missfortune" to listOf("missfortune", "tft19missfortune"),
        "renataglasc" to listOf("renataglasc", "renata", "tft19renata"),
        "twistedfate" to listOf("twistedfate", "tft19twistedfate"),
        "elderdragon" to listOf("elderdragon", "da18elderdragon", "tft18elderdragon"),
        "baronnashor" to listOf("baronnashor", "baron", "tft19baronnashor"),
        "akalikda" to listOf("akalikda", "akali", "tft19akalikda"),
        "akalitruedamage" to listOf("akalitruedamage", "akali", "tft19akalitruedamage")
    )

    fun isValidTeamPlannerCode(raw: String?): Boolean {
        val trimmed = raw?.trim().orEmpty()
        return trimmed.isNotBlank() && VALID_RIOT_TEAM_CODE_REGEX.matches(trimmed)
    }

    /**
     * Refreshes LIVE and PBE CommunityDragon `tftchampions-teamplanner.json` caches in background.
     */
    suspend fun refreshMappingsFromNetwork(
        context: Context,
        client: OkHttpClient = RiotApiClientFactory.publicOkHttp
    ): Boolean = withContext(Dispatchers.IO) {
        val liveOk = fetchAndPersistForEnvironment(context, TftEnvironment.LIVE, client) != null
        val pbeOk = fetchAndPersistForEnvironment(context, TftEnvironment.PBE, client) != null
        liveOk || pbeOk
    }

    /**
     * Ensures cached mapping is loaded in memory (or fetched silently if not yet on disk).
     */
    suspend fun ensureMappingLoaded(
        context: Context,
        environment: TftEnvironment,
        client: OkHttpClient = RiotApiClientFactory.publicOkHttp
    ): TeamPlannerCatalog? = withContext(Dispatchers.IO) {
        memoryCatalogs[environment]?.let { cached ->
            if (cached.isFromRiotCommunityDragon) return@withContext cached
        }
        loadFromDiskCache(context, environment)?.let { diskCatalog ->
            memoryCatalogs[environment] = diskCatalog
            return@withContext diskCatalog
        }
        fetchAndPersistForEnvironment(context, environment, client)?.let { netCatalog ->
            return@withContext netCatalog
        }
        buildOfflinePreloadFallbackCatalog(context, environment)?.also { fallback ->
            memoryCatalogs.putIfAbsent(environment, fallback)
        }
    }

    /**
     * Resolves an in-game pasteable TFT Team Planner code for [comp].
     * Uses explicit [MetaComp.teamPlannerCode] when valid, otherwise encodes from Riot's
     * official `tftchampions-teamplanner.json` (LIVE or PBE) with offline preload fallback.
     */
    suspend fun resolveTeamCode(
        context: Context,
        comp: MetaComp,
        client: OkHttpClient = RiotApiClientFactory.publicOkHttp
    ): TeamPlannerCodeResult = withContext(Dispatchers.IO) {
        if (isValidTeamPlannerCode(comp.teamPlannerCode)) {
            val cleaned = comp.teamPlannerCode.trim()
            val setKey = extractSetKeyFromCode(cleaned) ?: defaultSetKeyForComp(comp)
            return@withContext TeamPlannerCodeResult(
                code = cleaned,
                setKey = setKey,
                matchedChampions = comp.coreUnits.distinct().take(MAX_PLANNER_SLOTS),
                skippedUnits = emptyList(),
                isVerifiedRiotMapping = true
            )
        }

        val primaryCatalog = ensureMappingLoaded(context, comp.environment, client)
        if (primaryCatalog != null) {
            val encoded = encodeCompWithCatalog(comp, primaryCatalog)
            if (encoded.matchedChampions.isNotEmpty()) {
                return@withContext encoded
            }
        }

        // Cross-check the sibling environment file (e.g., when a PBE set is already in LIVE CDragon or vice versa)
        val alternateEnv = if (comp.environment == TftEnvironment.LIVE) TftEnvironment.PBE else TftEnvironment.LIVE
        val altCatalog = ensureMappingLoaded(context, alternateEnv, client)
        if (altCatalog != null) {
            val encodedAlt = encodeCompWithCatalog(comp, altCatalog)
            if (encodedAlt.matchedChampions.isNotEmpty()) {
                return@withContext encodedAlt
            }
        }

        // Final deterministic offline fallback
        val fallbackCatalog = buildOfflinePreloadFallbackCatalog(context, comp.environment)
        if (fallbackCatalog != null) {
            return@withContext encodeCompWithCatalog(comp, fallbackCatalog)
        }

        val fallbackSetKey = defaultSetKeyForComp(comp)
        val emptySlots = EMPTY_SLOT_HEX.repeat(MAX_PLANNER_SLOTS)
        TeamPlannerCodeResult(
            code = "$VERSION_PREFIX$emptySlots$fallbackSetKey",
            setKey = fallbackSetKey,
            matchedChampions = emptyList(),
            skippedUnits = comp.coreUnits,
            isVerifiedRiotMapping = false
        )
    }

    /**
     * Pure deterministic encoder given a parsed [TeamPlannerCatalog].
     */
    fun encodeCompWithCatalog(
        comp: MetaComp,
        catalog: TeamPlannerCatalog
    ): TeamPlannerCodeResult {
        val orderedCandidates = buildOrderedUnitCandidates(comp)
        val targetSetNum = extractSetNumber(comp.setName)
            ?: extractSetNumber(comp.patchTag)
            ?: if (comp.environment == TftEnvironment.PBE) 19 else 18

        val chosenSet = selectBestSetMapping(
            catalog = catalog,
            targetSetNumber = targetSetNum,
            unitCandidates = orderedCandidates
        )

        if (chosenSet == null) {
            val fallbackSetKey = defaultSetKeyForComp(comp)
            return TeamPlannerCodeResult(
                code = "$VERSION_PREFIX${EMPTY_SLOT_HEX.repeat(MAX_PLANNER_SLOTS)}$fallbackSetKey",
                setKey = fallbackSetKey,
                matchedChampions = emptyList(),
                skippedUnits = orderedCandidates,
                isVerifiedRiotMapping = false
            )
        }

        val matchedEntries = mutableListOf<TeamPlannerChampionEntry>()
        val seenCodes = HashSet<String>()
        val skipped = mutableListOf<String>()

        for (unitName in orderedCandidates) {
            if (matchedEntries.size >= MAX_PLANNER_SLOTS) break
            val entry = matchChampionInSet(unitName, chosenSet)
            if (entry != null) {
                if (seenCodes.add(entry.hex3)) {
                    matchedEntries.add(entry)
                }
            } else {
                skipped.add(unitName)
            }
        }

        val codeBuilder = StringBuilder(2 + MAX_PLANNER_SLOTS * 3 + chosenSet.setKey.length)
        codeBuilder.append(VERSION_PREFIX)
        matchedEntries.forEach { entry ->
            codeBuilder.append(entry.hex3)
        }
        repeat((MAX_PLANNER_SLOTS - matchedEntries.size).coerceAtLeast(0)) {
            codeBuilder.append(EMPTY_SLOT_HEX)
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
     * Parses Riot's CommunityDragon `tftchampions-teamplanner.json` format.
     * Supports both explicit integer/string `team_planner_code` fields (v02 12-bit codes)
     * and sorted-index fallback if an entry omits `team_planner_code`.
     */
    fun parseCommunityDragonJson(
        rawJson: String,
        isFromRiotCommunityDragon: Boolean = true
    ): TeamPlannerCatalog? {
        return runCatching {
            val root = JSONObject(rawJson)
            val setsMap = LinkedHashMap<String, TeamPlannerSetMapping>()
            val setKeys = root.keys()
            while (setKeys.hasNext()) {
                val setKey = setKeys.next()
                val arr = root.optJSONArray(setKey) ?: continue
                val mapping = parseSetArray(setKey, arr)
                if (mapping.entries.isNotEmpty()) {
                    setsMap[setKey] = mapping
                }
            }
            if (setsMap.isEmpty()) null
            else TeamPlannerCatalog(sets = setsMap, isFromRiotCommunityDragon = isFromRiotCommunityDragon)
        }.getOrNull()
    }

    private fun parseSetArray(setKey: String, arr: JSONArray): TeamPlannerSetMapping {
        data class RawEntry(
            val characterId: String,
            val displayName: String,
            val explicitCode: Int?
        )

        val rawEntries = mutableListOf<RawEntry>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val charId = obj.optString("character_id")
                .ifBlank { obj.optString("apiName") }
                .ifBlank { obj.optString("id") }
                .trim()
            val displayName = obj.optString("display_name")
                .ifBlank { obj.optString("name") }
                .ifBlank { charId }
                .trim()
            if (charId.isBlank() && displayName.isBlank()) continue
            val explicitCode = parsePlannerCodeInt(obj)
            rawEntries.add(
                RawEntry(
                    characterId = charId.ifBlank { displayName },
                    displayName = displayName,
                    explicitCode = explicitCode
                )
            )
        }

        // If Riot provided explicit `team_planner_code` values, use them directly.
        // Otherwise sort alphabetically by character_id (1-based index).
        val hasExplicitCodes = rawEntries.any { it.explicitCode != null && it.explicitCode > 0 }
        val sortedForFallback = rawEntries.sortedBy { it.characterId.lowercase(Locale.US) }
        val fallbackIndexByCharId = sortedForFallback.mapIndexed { idx, e -> e.characterId to (idx + 1) }.toMap()

        val entries = mutableListOf<TeamPlannerChampionEntry>()
        val lookup = HashMap<String, TeamPlannerChampionEntry>(rawEntries.size * 6)

        rawEntries.forEach { raw ->
            val codeInt = if (hasExplicitCodes) {
                raw.explicitCode?.takeIf { it > 0 } ?: return@forEach
            } else {
                fallbackIndexByCharId[raw.characterId] ?: return@forEach
            }
            val hex3 = formatSlotHex3(codeInt)
            val entry = TeamPlannerChampionEntry(
                characterId = raw.characterId,
                displayName = raw.displayName,
                plannerCodeInt = codeInt,
                hex3 = hex3
            )
            entries.add(entry)
            indexEntryKeys(entry, lookup)
        }

        return TeamPlannerSetMapping(
            setKey = setKey,
            setNumber = extractSetNumber(setKey),
            entries = entries,
            lookupByNormalizedKey = lookup
        )
    }

    private fun parsePlannerCodeInt(obj: JSONObject): Int? {
        if (!obj.has("team_planner_code") || obj.isNull("team_planner_code")) return null
        val rawVal = obj.opt("team_planner_code") ?: return null
        return when (rawVal) {
            is Number -> rawVal.toInt().takeIf { it in 1..0xFFF }
            is String -> {
                val cleaned = rawVal.trim()
                when {
                    cleaned.startsWith("0x", ignoreCase = true) ->
                        cleaned.substring(2).toIntOrNull(16)?.takeIf { it in 1..0xFFF }
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

    private fun indexEntryKeys(
        entry: TeamPlannerChampionEntry,
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
        val strippedVariant = stripped
            .removeSuffix("ad")
            .removeSuffix("ap")
            .removeSuffix("base")
            .removeSuffix("small")
        if (strippedVariant.length >= 2) {
            lookup.putIfAbsent(strippedVariant, entry)
        }
        // Also strip parenthetical Lux forms like "Lux (Elderwood)" -> fallback to "lux" if base not taken
        val beforeParen = entry.displayName.substringBefore('(').trim()
        val normBeforeParen = normalizeKey(beforeParen)
        if (normBeforeParen.length >= 2 && normBeforeParen != normDisplay) {
            lookup.putIfAbsent(normBeforeParen, entry)
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

        // 2. Stripped Riot API prefix match (e.g. DA_18_Ezreal -> ezreal)
        val stripped = stripRiotChampionPrefix(unitName)
        if (stripped.isNotBlank()) {
            setMapping.lookupByNormalizedKey[stripped]?.let { return it }
        }

        // 3. Explicit alias table (e.g. Pebbles -> sentry, Mama Beak -> crimsonraptor)
        championLookupAliases[norm]?.forEach { alias ->
            setMapping.lookupByNormalizedKey[alias]?.let { return it }
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

    private fun selectBestSetMapping(
        catalog: TeamPlannerCatalog,
        targetSetNumber: Int,
        unitCandidates: List<String>
    ): TeamPlannerSetMapping? {
        if (catalog.sets.isEmpty()) return null

        // Score every set by champion matches, breaking ties by matching targetSetNumber & exact TFTSet<N> key
        val scored = catalog.sets.values.map { mapping ->
            val matchCount = unitCandidates.count { matchChampionInSet(it, mapping) != null }
            val exactKeyBonus = if (mapping.setKey.equals("TFTSet$targetSetNumber", ignoreCase = true)) 100 else 0
            val numberMatchBonus = if (mapping.setNumber == targetSetNumber) 40 else 0
            val nonRevivalBonus = if (!mapping.setKey.contains("Event", ignoreCase = true) &&
                !mapping.setKey.contains("Stage", ignoreCase = true)
            ) 5 else 0
            val totalScore = matchCount * 1000 + exactKeyBonus + numberMatchBonus + nonRevivalBonus
            mapping to (matchCount to totalScore)
        }

        // Prefer sets that actually match champions from this comp
        val bestMatching = scored
            .filter { it.second.first > 0 }
            .maxByOrNull { it.second.second }
            ?.first
        if (bestMatching != null) return bestMatching

        // Fallback to exact target set key if no units matched
        return catalog.sets.values.firstOrNull {
            it.setKey.equals("TFTSet$targetSetNumber", ignoreCase = true)
        } ?: catalog.sets.values.firstOrNull {
            it.setNumber == targetSetNumber
        } ?: catalog.sets.values.maxByOrNull { it.setNumber ?: 0 }
    }

    private fun buildOrderedUnitCandidates(comp: MetaComp): List<String> {
        val ordered = LinkedHashSet<String>()
        if (comp.carryName.isNotBlank()) {
            ordered.add(comp.carryName.trim())
        }
        // Prioritize itemized units and higher-cost units from hex placements
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

    private fun fetchAndPersistForEnvironment(
        context: Context,
        environment: TftEnvironment,
        client: OkHttpClient
    ): TeamPlannerCatalog? {
        val url = if (environment == TftEnvironment.PBE) {
            CDRAGON_PBE_TEAMPLANNER_URL
        } else {
            CDRAGON_LIVE_TEAMPLANNER_URL
        }
        val body = runCatching {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "HexTome-Android/1.0")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        }.getOrNull() ?: return null

        val parsed = parseCommunityDragonJson(body, isFromRiotCommunityDragon = true) ?: return null
        memoryCatalogs[environment] = parsed
        runCatching {
            cacheFileFor(context, environment).writeText(body, Charsets.UTF_8)
        }
        return parsed
    }

    private fun loadFromDiskCache(context: Context, environment: TftEnvironment): TeamPlannerCatalog? {
        val file = cacheFileFor(context, environment)
        if (!file.exists()) return null
        val raw = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return null
        return parseCommunityDragonJson(raw, isFromRiotCommunityDragon = true)
    }

    private fun cacheFileFor(context: Context, environment: TftEnvironment): File {
        val name = if (environment == TftEnvironment.PBE) PBE_CACHE_FILE else LIVE_CACHE_FILE
        return File(context.applicationContext.filesDir, name)
    }

    private fun buildOfflinePreloadFallbackCatalog(
        context: Context,
        environment: TftEnvironment
    ): TeamPlannerCatalog? {
        return runCatching {
            if (environment == TftEnvironment.PBE) {
                val raw = context.applicationContext.assets.open(PRELOAD_SET19_PBE_ASSET)
                    .bufferedReader(Charsets.UTF_8).use { it.readText() }
                val root = JSONObject(raw)
                val setNum = root.optInt("pbeSetNumber", 19)
                val costsObj = root.optJSONObject("championCosts") ?: return null
                val names = mutableListOf<String>()
                val keys = costsObj.keys()
                while (keys.hasNext()) {
                    names.add(keys.next())
                }
                val syntheticArr = JSONArray()
                names.sortedBy { it.lowercase(Locale.US) }.forEachIndexed { idx, name ->
                    syntheticArr.put(
                        JSONObject().apply {
                            put("character_id", "TFT${setNum}_${normalizeKey(name)}")
                            put("display_name", name)
                            put("team_planner_code", idx + 1)
                        }
                    )
                }
                val setKey = "TFTSet$setNum"
                val wrapper = JSONObject().put(setKey, syntheticArr)
                parseCommunityDragonJson(wrapper.toString(), isFromRiotCommunityDragon = false)
            } else {
                val raw = context.applicationContext.assets.open(PRELOAD_SET18_ASSET)
                    .bufferedReader(Charsets.UTF_8).use { it.readText() }
                val root = JSONObject(raw)
                val champsArr = root.optJSONArray("champions") ?: return null
                val pairs = mutableListOf<Pair<String, String>>()
                for (i in 0 until champsArr.length()) {
                    val obj = champsArr.optJSONObject(i) ?: continue
                    val id = obj.optString("id").removePrefix("live_").trim()
                    val name = obj.optString("name").trim()
                    if (name.isNotBlank()) {
                        pairs.add((id.ifBlank { name }) to name)
                    }
                }
                val syntheticArr = JSONArray()
                pairs.sortedBy { it.first.lowercase(Locale.US) }.forEachIndexed { idx, (charId, name) ->
                    syntheticArr.put(
                        JSONObject().apply {
                            put("character_id", charId)
                            put("display_name", name)
                            put("team_planner_code", idx + 1)
                        }
                    )
                }
                val wrapper = JSONObject().put("TFTSet18", syntheticArr)
                parseCommunityDragonJson(wrapper.toString(), isFromRiotCommunityDragon = false)
            }
        }.getOrNull()
    }

    internal fun clearMemoryCacheForTests() {
        memoryCatalogs.clear()
    }

    internal fun formatSlotHex3(codeInt: Int): String =
        codeInt.coerceIn(0, 0xFFF).toString(16).lowercase(Locale.US).padStart(3, '0')

    internal fun normalizeKey(raw: String): String =
        raw.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")

    private fun stripRiotChampionPrefix(raw: String): String {
        val stripped = raw
            .replace(Regex("""^(?:TFT|DA)[\s_]*\d*[\s_]*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""(?:18|19)(?=(?:_|$))"""), "")
        return normalizeKey(stripped)
    }

    private fun extractSetNumber(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        val match = SET_NUMBER_REGEX.find(text)
        if (match != null) return match.groupValues[1].toIntOrNull()
        // Patch tag fallback like "18.7" or "19.1-PBE"
        val patchMajor = Regex("""^(\d{2})\.""").find(text.trim())?.groupValues?.getOrNull(1)?.toIntOrNull()
        return patchMajor?.takeIf { it in 10..30 }
    }

    private fun extractSetKeyFromCode(code: String): String? {
        val idx = code.indexOf("TFTSet", ignoreCase = true)
        return if (idx >= 0) code.substring(idx) else null
    }

    private fun defaultSetKeyForComp(comp: MetaComp): String {
        val num = extractSetNumber(comp.setName)
            ?: extractSetNumber(comp.patchTag)
            ?: if (comp.environment == TftEnvironment.PBE) 19 else 18
        return "TFTSet$num"
    }
}
