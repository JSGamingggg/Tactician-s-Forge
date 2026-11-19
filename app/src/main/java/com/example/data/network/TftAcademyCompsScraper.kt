package com.example.data.network

import com.example.data.model.AugmentTier
import com.example.data.model.HexUnitPlacement
import com.example.data.model.MetaComp
import com.example.data.model.TftEnvironment
import com.example.data.model.TierRank
import com.example.ui.components.TftAssetIconResolver
import com.example.ui.components.formatRiotAssetLabel
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale

data class TftAcademyCompsFetchResult(
    val liveComps: List<MetaComp>,
    val pbeComps: List<MetaComp>,
    val detectedLiveSet: String?,
    val detectedPbeSet: String?,
    val fetchedFromNetwork: Boolean
)

data class TftAcademyCatalogTiers(
    val itemTiersByKey: Map<String, TierRank>,
    val itemTypesByKey: Map<String, String>,
    val augmentTiersByKey: Map<String, TierRank>,
    val augmentRanksByKey: Map<String, AugmentTier>,
    val fetchedFromNetwork: Boolean
)

/**
 * Fetches ALL team compositions for LIVE (https://tftacademy.com/tierlist/comps)
 * and PBE (https://tftacademy.com/tierlist/comps/set19), including SvelteKit
 * `data-sveltekit-fetched` envelopes, Next.js App Router RSC `__next_f` payloads,
 * `__NEXT_DATA__`, `_next/data` JSON routes, API/CDN JSON endpoints, JS bundles,
 * and full-document DOM cards/links, using no-cache headers so updates appear
 * immediately whenever anything changes on TFTAcademy.
 */
class TftAcademyCompsScraper(
    private val client: OkHttpClient = RiotApiClientFactory.publicOkHttp
) {
    fun fetchTeamComps(
        livePatchVersion: String,
        pbePatchVersion: String
    ): TftAcademyCompsFetchResult {
        val baseHtml = fetchUrl(TFTACADEMY_COMPS_URL)
            ?: return TftAcademyCompsFetchResult(
                liveComps = emptyList(),
                pbeComps = emptyList(),
                detectedLiveSet = null,
                detectedPbeSet = null,
                fetchedFromNetwork = false
            )

        // Fetch PBE Set 19 comps from https://tftacademy.com/tierlist/comps/set19
        val pbeUrl = findPbeCompsUrl(baseHtml) ?: TFTACADEMY_PBE_COMPS_URL
        val pbeHtml = if (pbeUrl != TFTACADEMY_COMPS_URL) {
            fetchUrl(pbeUrl)
        } else {
            null
        }

        val liveParsed = parseCompsDocument(
            html = baseHtml,
            targetEnvironment = TftEnvironment.LIVE,
            patchTag = livePatchVersion,
            isDedicatedEnvPage = true,
            includeRemoteBundles = true
        )
        val pbeParsed = if (pbeHtml != null) {
            parseCompsDocument(
                html = pbeHtml,
                targetEnvironment = TftEnvironment.PBE,
                patchTag = pbePatchVersion,
                isDedicatedEnvPage = true,
                includeRemoteBundles = true
            )
        } else {
            // Only extract explicitly PBE-flagged nodes from LIVE HTML; never clone untagged LIVE comps into PBE
            parseCompsDocument(
                html = baseHtml,
                targetEnvironment = TftEnvironment.PBE,
                patchTag = pbePatchVersion,
                isDedicatedEnvPage = false,
                includeRemoteBundles = false
            )
        }

        val liveSetNumber = liveParsed.detectedSetNumber ?: inferSetNumberFromPatch(livePatchVersion)
        val rawPbeSetNumber = pbeParsed.detectedSetNumber
        val candidatePbeSetNumber = rawPbeSetNumber?.takeIf { liveSetNumber == null || it > liveSetNumber }
            ?: inferSetNumberFromPatch(pbePatchVersion)?.takeIf { liveSetNumber == null || it > liveSetNumber }
            ?: 19

        val probedPbeComps = if (pbeParsed.comps.isEmpty()) {
            fetchApiSetComps(
                setNumber = candidatePbeSetNumber,
                targetEnvironment = TftEnvironment.PBE,
                patchTag = pbePatchVersion,
                defaultSetLabel = "Set $candidatePbeSetNumber (PBE)"
            )
        } else {
            emptyList()
        }

        val pbeSetNumber = rawPbeSetNumber?.takeIf { liveSetNumber == null || it > liveSetNumber }
            ?: candidatePbeSetNumber

        val enforcedLiveComps = enforceSetAlignment(
            comps = liveParsed.comps,
            expectedSetNumber = liveSetNumber,
            targetEnv = TftEnvironment.LIVE,
            patchTag = livePatchVersion
        )
        val candidatePbeComps = if (probedPbeComps.isNotEmpty()) probedPbeComps else pbeParsed.comps
        val enforcedPbeComps = enforceSetAlignment(
            comps = candidatePbeComps,
            expectedSetNumber = pbeSetNumber,
            targetEnv = TftEnvironment.PBE,
            patchTag = patchPatchSafe(pbePatchVersion)
        )

        return TftAcademyCompsFetchResult(
            liveComps = enforcedLiveComps,
            pbeComps = enforcedPbeComps,
            detectedLiveSet = liveParsed.detectedSetLabel ?: liveSetNumber?.let { "Set $it" },
            detectedPbeSet = if (enforcedPbeComps.isNotEmpty()) {
                pbeParsed.detectedSetLabel ?: "Set $pbeSetNumber (PBE)"
            } else {
                null
            },
            fetchedFromNetwork = true
        )
    }

    /**
     * Fetches LIVE team compositions from https://tftacademy.com/tierlist/comps.
     */
    fun fetchLiveTeamComps(livePatchVersion: String): TftAcademyCompsFetchResult {
        val baseHtml = fetchUrl(TFTACADEMY_COMPS_URL)
            ?: return TftAcademyCompsFetchResult(
                liveComps = emptyList(),
                pbeComps = emptyList(),
                detectedLiveSet = null,
                detectedPbeSet = null,
                fetchedFromNetwork = false
            )

        val liveParsed = parseCompsDocument(
            html = baseHtml,
            targetEnvironment = TftEnvironment.LIVE,
            patchTag = livePatchVersion,
            isDedicatedEnvPage = true,
            includeRemoteBundles = true
        )
        val liveSetNumber = liveParsed.detectedSetNumber ?: inferSetNumberFromPatch(livePatchVersion)
        val enforcedLiveComps = enforceSetAlignment(
            comps = liveParsed.comps,
            expectedSetNumber = liveSetNumber,
            targetEnv = TftEnvironment.LIVE,
            patchTag = livePatchVersion
        )

        return TftAcademyCompsFetchResult(
            liveComps = enforcedLiveComps,
            pbeComps = emptyList(),
            detectedLiveSet = liveParsed.detectedSetLabel ?: liveSetNumber?.let { "Set $it" },
            detectedPbeSet = null,
            fetchedFromNetwork = true
        )
    }

    /**
     * Fetches PBE team compositions exclusively from https://tftacademy.com/tierlist/comps/set19.
     */
    fun fetchPbeTeamComps(
        pbePatchVersion: String,
        defaultPbeSetNumber: Int = 19
    ): TftAcademyCompsFetchResult {
        val pbeHtml = fetchUrl(TFTACADEMY_PBE_COMPS_URL)
            ?: return TftAcademyCompsFetchResult(
                liveComps = emptyList(),
                pbeComps = emptyList(),
                detectedLiveSet = null,
                detectedPbeSet = null,
                fetchedFromNetwork = false
            )

        val pbeParsed = parseCompsDocument(
            html = pbeHtml,
            targetEnvironment = TftEnvironment.PBE,
            patchTag = pbePatchVersion,
            isDedicatedEnvPage = true,
            includeRemoteBundles = true
        )
        val pbeSetNumber = pbeParsed.detectedSetNumber?.takeIf { it >= defaultPbeSetNumber }
            ?: inferSetNumberFromPatch(pbePatchVersion)?.takeIf { it >= defaultPbeSetNumber }
            ?: defaultPbeSetNumber

        val apiFallbackComps = if (pbeParsed.comps.isEmpty()) {
            fetchApiSetComps(
                setNumber = pbeSetNumber,
                targetEnvironment = TftEnvironment.PBE,
                patchTag = pbePatchVersion,
                defaultSetLabel = pbeParsed.detectedSetLabel ?: "Set $pbeSetNumber (PBE)"
            )
        } else {
            emptyList()
        }

        val enforcedPbeComps = enforceSetAlignment(
            comps = pbeParsed.comps.ifEmpty { apiFallbackComps },
            expectedSetNumber = pbeSetNumber,
            targetEnv = TftEnvironment.PBE,
            patchTag = patchPatchSafe(pbePatchVersion)
        )

        val detectedLabel = pbeParsed.detectedSetLabel?.let { lbl ->
            if (lbl.contains("PBE", ignoreCase = true)) lbl else "$lbl (PBE)"
        } ?: "Set $pbeSetNumber (PBE)"

        return TftAcademyCompsFetchResult(
            liveComps = emptyList(),
            pbeComps = enforcedPbeComps,
            detectedLiveSet = null,
            detectedPbeSet = detectedLabel,
            fetchedFromNetwork = true
        )
    }

    /**
     * Fetches current LIVE Item & Augment tierlists from TFTAcademy's JSON APIs:
     * - https://tftacademy.com/api/tierlist/items?set=<setNumber>
     * - https://tftacademy.com/api/tierlist/augments?set=<setNumber>
     */
    fun fetchLiveItemsAndAugmentsTierlist(setNumber: Int = 18): TftAcademyCatalogTiers {
        val itemsJson = fetchUrl("$TFTACADEMY_ORIGIN/api/tierlist/items?set=$setNumber")
        val augmentsJson = fetchUrl("$TFTACADEMY_ORIGIN/api/tierlist/augments?set=$setNumber")
        if (itemsJson.isNullOrBlank() && augmentsJson.isNullOrBlank()) {
            return TftAcademyCatalogTiers(
                itemTiersByKey = emptyMap(),
                itemTypesByKey = emptyMap(),
                augmentTiersByKey = emptyMap(),
                augmentRanksByKey = emptyMap(),
                fetchedFromNetwork = false
            )
        }
        return parseLiveItemsAndAugmentsJson(itemsJson, augmentsJson)
            .copy(fetchedFromNetwork = true)
    }

    internal fun parseLiveItemsAndAugmentsJson(
        itemsJson: String?,
        augmentsJson: String?
    ): TftAcademyCatalogTiers {
        val itemTiers = LinkedHashMap<String, TierRank>()
        val itemTypes = LinkedHashMap<String, String>()
        val augTiers = LinkedHashMap<String, TierRank>()
        val augRanks = LinkedHashMap<String, AugmentTier>()

        if (!itemsJson.isNullOrBlank()) {
            runCatching {
                val root = JSONObject(itemsJson)
                val lists = root.optJSONArray("items_tierlists") ?: JSONArray()
                for (i in 0 until lists.length()) {
                    val grp = lists.optJSONObject(i) ?: continue
                    val itemType = grp.optString("type").trim()
                    val tierObj = grp.optJSONObject("tier") ?: continue
                    val tierKeys = tierObj.keys()
                    while (tierKeys.hasNext()) {
                        val tierKey = tierKeys.next()
                        val rank = TierRank.fromRaw(tierKey)
                        val arr = tierObj.optJSONArray(tierKey) ?: continue
                        for (j in 0 until arr.length()) {
                            val rawId = arr.optString(j).trim()
                            if (rawId.isBlank()) continue
                            val normRaw = normalizeCatalogKey(rawId)
                            val normLabel = normalizeCatalogKey(formatRiotAssetLabel(rawId))
                            itemTiers[rawId] = rank
                            if (normRaw.isNotBlank()) itemTiers[normRaw] = rank
                            if (normLabel.isNotBlank()) itemTiers[normLabel] = rank
                            if (itemType.isNotBlank()) {
                                itemTypes[rawId] = itemType
                                if (normRaw.isNotBlank()) itemTypes[normRaw] = itemType
                                if (normLabel.isNotBlank()) itemTypes[normLabel] = itemType
                            }
                        }
                    }
                }
            }
        }

        if (!augmentsJson.isNullOrBlank()) {
            runCatching {
                val root = JSONObject(augmentsJson)
                val lists = root.optJSONArray("augments_tierlists") ?: JSONArray()
                for (i in 0 until lists.length()) {
                    val grp = lists.optJSONObject(i) ?: continue
                    val augTier = when (grp.optInt("augmenttier", 2)) {
                        1 -> AugmentTier.SILVER
                        3 -> AugmentTier.PRISMATIC
                        else -> AugmentTier.GOLD
                    }
                    val tierObj = grp.optJSONObject("tier") ?: continue
                    val tierKeys = tierObj.keys()
                    while (tierKeys.hasNext()) {
                        val tierKey = tierKeys.next()
                        val rank = TierRank.fromRaw(tierKey)
                        val arr = tierObj.optJSONArray(tierKey) ?: continue
                        for (j in 0 until arr.length()) {
                            val rawId = arr.optString(j).trim()
                            if (rawId.isBlank()) continue
                            val normRaw = normalizeCatalogKey(rawId)
                            val normLabel = normalizeCatalogKey(formatRiotAssetLabel(rawId))
                            augTiers[rawId] = rank
                            if (normRaw.isNotBlank()) augTiers[normRaw] = rank
                            if (normLabel.isNotBlank()) augTiers[normLabel] = rank
                            augRanks[rawId] = augTier
                            if (normRaw.isNotBlank()) augRanks[normRaw] = augTier
                            if (normLabel.isNotBlank()) augRanks[normLabel] = augTier
                        }
                    }
                }
            }
        }

        return TftAcademyCatalogTiers(
            itemTiersByKey = itemTiers,
            itemTypesByKey = itemTypes,
            augmentTiersByKey = augTiers,
            augmentRanksByKey = augRanks,
            fetchedFromNetwork = false
        )
    }

    private fun normalizeCatalogKey(raw: String): String =
        raw.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")

    private fun patchPatchSafe(patchTag: String): String = patchTag

    private fun fetchApiSetComps(
        setNumber: Int,
        targetEnvironment: TftEnvironment,
        patchTag: String,
        defaultSetLabel: String
    ): List<MetaComp> {
        val body = fetchUrl("$TFTACADEMY_ORIGIN/api/tierlist/comps?set=$setNumber") ?: return emptyList()
        val out = mutableListOf<MetaComp>()
        runCatching {
            val trimmed = body.trim()
            val root: Any = if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed)
            collectCompsFromJson(
                node = root,
                targetEnv = targetEnvironment,
                patchTag = patchTag,
                defaultSetLabel = defaultSetLabel,
                inheritedEnv = targetEnvironment,
                inheritedSet = "Set $setNumber",
                out = out
            )
        }
        return out.distinctBy { it.id }
    }

    internal data class ParsedDocumentResult(
        val comps: List<MetaComp>,
        val detectedSetNumber: Int?,
        val detectedSetLabel: String?
    )

    internal fun parseCompsDocument(
        html: String,
        targetEnvironment: TftEnvironment,
        patchTag: String,
        isDedicatedEnvPage: Boolean = true,
        includeRemoteBundles: Boolean = false
    ): ParsedDocumentResult {
        val detectedSet = detectSetForEnvironment(html, targetEnvironment)
        val defaultSetLabel = detectedSet?.second ?: if (targetEnvironment == TftEnvironment.LIVE) {
            TftEnvironment.LIVE.setName
        } else {
            TftEnvironment.PBE.setName
        }

        val fromJson = parseJsonComps(html, targetEnvironment, patchTag, defaultSetLabel, isDedicatedEnvPage)
        val fromRsc = parseNextAppRouterFlightComps(html, targetEnvironment, patchTag, defaultSetLabel, isDedicatedEnvPage)
        val fromDom = parseDomTeamComps(html, targetEnvironment, patchTag, defaultSetLabel, isDedicatedEnvPage)

        var combined = (fromJson + fromRsc + fromDom)
            .distinctBy { it.id }

        if (includeRemoteBundles && combined.isEmpty()) {
            val fromNextData = parseNextDataEndpoint(html, targetEnvironment, patchTag, defaultSetLabel)
            val fromApiUrls = parseDiscoveredDataEndpoints(html, targetEnvironment, patchTag, defaultSetLabel)
            val fromBundles = parseScriptBundleComps(html, targetEnvironment, patchTag, defaultSetLabel)
            if (fromNextData.isNotEmpty() || fromApiUrls.isNotEmpty() || fromBundles.isNotEmpty()) {
                combined = (combined + fromNextData + fromApiUrls + fromBundles).distinctBy { it.id }
            }
        }

        val sorted = combined.sortedWith(
            compareBy<MetaComp> { it.tier.sortOrder }
                .thenBy { it.avgPlacement }
                .thenBy { it.title.lowercase(Locale.US) }
        )

        return ParsedDocumentResult(
            comps = sorted,
            detectedSetNumber = detectedSet?.first,
            detectedSetLabel = detectedSet?.second
        )
    }

    internal fun enforceSetAlignment(
        comps: List<MetaComp>,
        expectedSetNumber: Int?,
        targetEnv: TftEnvironment,
        patchTag: String
    ): List<MetaComp> {
        val envSourceUrl = compUrlForEnvironment(targetEnv)
        return comps
            .filter { comp ->
                if (comp.environment != targetEnv) return@filter false
                if (expectedSetNumber == null) return@filter true
                val compSetNum = extractSetNumber(comp.setName) ?: extractSetNumber(comp.notes)
                compSetNum == null || compSetNum == expectedSetNumber
            }
            .map { comp ->
                val resolvedSetName = if (expectedSetNumber != null && extractSetNumber(comp.setName) == null) {
                    if (targetEnv == TftEnvironment.PBE) "Set $expectedSetNumber (PBE)" else "Set $expectedSetNumber"
                } else {
                    comp.setName
                }
                comp.copy(
                    environment = targetEnv,
                    patchTag = patchTag,
                    setName = resolvedSetName,
                    sourceUrl = envSourceUrl
                )
            }
    }

    internal fun detectSetForEnvironment(html: String, env: TftEnvironment): Pair<Int, String>? {
        fun cleanSetSubtitle(raw: String): String {
            return raw
                .replace(Regex("""\s+(?:Tier\s*List|Team\s*Comps|Comps|Guides?|TFTAcademy|PBE)\b.*$""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\s*[-|•].*$"""), "")
                .trim()
        }

        val envPattern = if (env == TftEnvironment.PBE) {
            Regex(
                """(?:PBE[^<\n]{0,40}Set\s*(\d+)(?:\s*:\s*([A-Za-z0-9 '&-]+))?|Set\s*(\d+)(?:\s*:\s*([A-Za-z0-9 '&-]+))?[^<\n]{0,30}PBE)""",
                RegexOption.IGNORE_CASE
            )
        } else {
            Regex(
                """(?:LIVE[^<\n]{0,40}Set\s*(\d+)(?:\s*:\s*([A-Za-z0-9 '&-]+))?)""",
                RegexOption.IGNORE_CASE
            )
        }

        val envMatch = envPattern.find(html)
        if (envMatch != null) {
            val num = (envMatch.groupValues[1].ifBlank { envMatch.groupValues[3] }).toIntOrNull()
            val subtitle = cleanSetSubtitle(envMatch.groupValues[2].ifBlank { envMatch.groupValues[4] })
            if (num != null && (env != TftEnvironment.PBE || num >= 19)) {
                val label = if (subtitle.isNotBlank()) "Set $num: $subtitle" else "Set $num"
                return num to (if (env == TftEnvironment.PBE && !label.contains("PBE", true)) "$label (PBE)" else label)
            }
        }

        val genericMatches = SET_LABEL_REGEX.findAll(html).toList()
        if (genericMatches.isNotEmpty()) {
            val match = if (env == TftEnvironment.PBE) {
                genericMatches.firstOrNull { m ->
                    val windowStart = (m.range.first - 48).coerceAtLeast(0)
                    val windowEnd = (m.range.last + 40).coerceAtMost(html.length)
                    val window = html.substring(windowStart, windowEnd)
                    val nearPbe = window.contains("pbe", ignoreCase = true) || window.contains("/set19", ignoreCase = true)
                    val num = m.groupValues[1].toIntOrNull() ?: 0
                    nearPbe && num >= 19
                } ?: genericMatches
                    .filter { (it.groupValues[1].toIntOrNull() ?: 0) >= 19 }
                    .maxByOrNull { it.groupValues[1].toIntOrNull() ?: 0 }
                    ?: genericMatches.firstOrNull { m ->
                        val windowStart = (m.range.first - 40).coerceAtLeast(0)
                        val windowEnd = (m.range.last + 40).coerceAtMost(html.length)
                        html.substring(windowStart, windowEnd).contains("pbe", ignoreCase = true)
                    }
                    ?: genericMatches.first()
            } else {
                genericMatches.firstOrNull { m ->
                    val windowStart = (m.range.first - 40).coerceAtLeast(0)
                    val windowEnd = (m.range.last + 40).coerceAtMost(html.length)
                    !html.substring(windowStart, windowEnd).contains("pbe", ignoreCase = true)
                } ?: genericMatches.first()
            }
            val num = match.groupValues[1].toIntOrNull()
            if (num != null) {
                val subtitle = cleanSetSubtitle(match.groupValues.getOrNull(2).orEmpty())
                val label = if (subtitle.isNotBlank()) "Set $num: $subtitle" else "Set $num"
                return num to (if (env == TftEnvironment.PBE && !label.contains("PBE", true)) "$label (PBE)" else label)
            }
        }
        if (env == TftEnvironment.PBE) {
            SET_PATH_OR_JSON_REGEX.findAll(html)
                .mapNotNull { m -> m.groupValues.drop(1).firstOrNull { it.isNotBlank() }?.toIntOrNull() }
                .filter { it >= 19 }
                .maxOrNull()
                ?.let { setNum -> return setNum to "Set $setNum (PBE)" }
        }
        if (env == TftEnvironment.LIVE) {
            API_SET_QUERY_REGEX.find(html)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { setNum ->
                return setNum to "Set $setNum"
            }
        }
        return null
    }

    /**
     * Unescapes Next.js App Router RSC flight chunks (`self.__next_f.push([1,"..."])`)
     * and scans them for serialized TFTAcademy guide/comp JSON arrays or objects.
     */
    internal fun parseNextAppRouterFlightComps(
        html: String,
        targetEnvironment: TftEnvironment,
        patchTag: String,
        defaultSetLabel: String,
        isDedicatedEnvPage: Boolean = true
    ): List<MetaComp> {
        val out = mutableListOf<MetaComp>()
        val flightBuffer = StringBuilder()
        NEXT_FLIGHT_CHUNK_REGEX.findAll(html).forEach { match ->
            val rawLiteral = match.groupValues.getOrNull(1) ?: return@forEach
            val decoded = runCatching {
                JSONObject("{\"v\":\"$rawLiteral\"}").getString("v")
            }.getOrElse {
                rawLiteral
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .replace("\\n", "\n")
            }
            if (decoded.contains("\"units\"") ||
                decoded.contains("\"champions\"") ||
                decoded.contains("\"finalComp\"") ||
                decoded.contains("\"tier\"") ||
                decoded.contains("\"comps\"") ||
                decoded.contains("\"guides\"")
            ) {
                flightBuffer.append(decoded).append('\n')
            }
        }
        val combinedFlight = flightBuffer.toString()
        if (combinedFlight.isNotBlank()) {
            extractCandidateCompArrays(combinedFlight).forEach { arrText ->
                runCatching {
                    collectCompsFromJson(
                        node = JSONArray(arrText),
                        targetEnv = targetEnvironment,
                        patchTag = patchTag,
                        defaultSetLabel = defaultSetLabel,
                        inheritedEnv = null,
                        inheritedSet = null,
                        out = out,
                        requireExplicitEnvMatch = !isDedicatedEnvPage
                    )
                }
            }
            extractCandidateCompObjects(combinedFlight).forEach { objText ->
                runCatching {
                    collectCompsFromJson(
                        node = JSONObject(objText),
                        targetEnv = targetEnvironment,
                        patchTag = patchTag,
                        defaultSetLabel = defaultSetLabel,
                        inheritedEnv = null,
                        inheritedSet = null,
                        out = out,
                        requireExplicitEnvMatch = !isDedicatedEnvPage
                    )
                }
            }
        }
        return out.distinctBy { it.id }
    }

    private fun parseJsonComps(
        source: String,
        targetEnvironment: TftEnvironment,
        patchTag: String,
        defaultSetLabel: String,
        isDedicatedEnvPage: Boolean = true
    ): List<MetaComp> {
        val out = mutableListOf<MetaComp>()
        val candidates = buildList {
            // Unwrap SvelteKit SSR `data-sveltekit-fetched` script envelopes (escaped JSON in `"body"`)
            SVELTEKIT_FETCHED_SCRIPT_REGEX.findAll(source).forEach { m ->
                val rawEnvelope = m.groupValues.getOrNull(1)?.trim().orEmpty()
                if (rawEnvelope.isNotEmpty()) {
                    runCatching {
                        val envelope = JSONObject(rawEnvelope)
                        val bodyStr = envelope.optString("body")
                        if (bodyStr.isNotBlank()) {
                            add(bodyStr)
                        } else {
                            add(rawEnvelope)
                        }
                    }
                }
            }
            NEXT_DATA_REGEX.find(source)?.groupValues?.getOrNull(1)?.let(::add)
            INLINE_JSON_STATE_REGEX.findAll(source).forEach { m ->
                m.groupValues.getOrNull(1)?.let(::add)
            }
            val trimmedSource = source.trim()
            if (trimmedSource.startsWith("{") || trimmedSource.startsWith("[")) {
                add(trimmedSource)
            }
            addAll(extractCandidateCompArrays(source))
        }
        for (jsonText in candidates) {
            runCatching {
                val trimmed = jsonText.trim()
                val root: Any = if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed)
                collectCompsFromJson(
                    node = root,
                    targetEnv = targetEnvironment,
                    patchTag = patchTag,
                    defaultSetLabel = defaultSetLabel,
                    inheritedEnv = null,
                    inheritedSet = null,
                    out = out,
                    requireExplicitEnvMatch = !isDedicatedEnvPage
                )
            }
        }
        return out
    }

    private fun parseNextDataEndpoint(
        html: String,
        targetEnvironment: TftEnvironment,
        patchTag: String,
        defaultSetLabel: String
    ): List<MetaComp> {
        val buildId = NEXT_BUILD_ID_REGEX.find(html)?.groupValues?.getOrNull(1)?.trim()
            ?.takeIf { it.isNotBlank() } ?: return emptyList()
        val path = if (targetEnvironment == TftEnvironment.PBE) {
            "$TFTACADEMY_ORIGIN/_next/data/$buildId/tierlist/comps/set19.json"
        } else {
            "$TFTACADEMY_ORIGIN/_next/data/$buildId/tierlist/comps.json"
        }
        val body = fetchUrl(path) ?: return emptyList()
        val out = mutableListOf<MetaComp>()
        runCatching {
            collectCompsFromJson(
                node = JSONObject(body),
                targetEnv = targetEnvironment,
                patchTag = patchTag,
                defaultSetLabel = defaultSetLabel,
                inheritedEnv = null,
                inheritedSet = null,
                out = out
            )
        }
        return out
    }

    private fun parseDiscoveredDataEndpoints(
        source: String,
        targetEnvironment: TftEnvironment,
        patchTag: String,
        defaultSetLabel: String
    ): List<MetaComp> {
        val out = mutableListOf<MetaComp>()
        val urls = buildList {
            DATA_URL_REGEX.findAll(source)
                .map { it.value.trim('"', '\'', ' ') }
                .filter { url ->
                    val lower = url.lowercase(Locale.US)
                    lower.contains("tftacademy") &&
                        (lower.contains("comp") || lower.contains("tier") || lower.contains("guide") || lower.contains("meta") || lower.endsWith(".json"))
                }
                .forEach(::add)
            RELATIVE_API_URL_REGEX.findAll(source)
                .map { it.groupValues[1].trim() }
                .forEach { rel -> add("$TFTACADEMY_ORIGIN$rel") }
        }
            .distinct()
            .take(8)

        for (url in urls) {
            val body = fetchUrl(url) ?: continue
            runCatching {
                val trimmed = body.trim()
                val root: Any = if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed)
                collectCompsFromJson(root, targetEnvironment, patchTag, defaultSetLabel, inheritedEnv = null, inheritedSet = null, out = out)
            }
        }
        return out.distinctBy { it.id }
    }

    private fun parseScriptBundleComps(
        html: String,
        targetEnvironment: TftEnvironment,
        patchTag: String,
        defaultSetLabel: String
    ): List<MetaComp> {
        val out = mutableListOf<MetaComp>()
        val scriptUrls = SCRIPT_SRC_REGEX.findAll(html)
            .mapNotNull { it.groupValues.getOrNull(1)?.trim() }
            .filter { src ->
                val lower = src.lowercase(Locale.US)
                lower.contains("comp") ||
                    lower.contains("tft") ||
                    lower.contains("tier") ||
                    lower.contains("guide") ||
                    lower.contains("chunk") ||
                    lower.contains("main") ||
                    lower.contains("app") ||
                    lower.contains("page") ||
                    lower.contains("_next")
            }
            .map { src ->
                when {
                    src.startsWith("http") -> src
                    src.startsWith("//") -> "https:$src"
                    else -> "$TFTACADEMY_ORIGIN${if (src.startsWith("/")) src else "/$src"}"
                }
            }
            .distinct()
            .take(12)
            .toList()

        for (url in scriptUrls) {
            val jsBody = fetchUrl(url) ?: continue
            extractCandidateCompArrays(jsBody).forEach { arrText ->
                runCatching {
                    collectCompsFromJson(
                        node = JSONArray(arrText),
                        targetEnv = targetEnvironment,
                        patchTag = patchTag,
                        defaultSetLabel = defaultSetLabel,
                        inheritedEnv = null,
                        inheritedSet = null,
                        out = out
                    )
                }
            }
        }
        return out.distinctBy { it.id }
    }

    /**
     * Walks bracket depth with string/escape awareness to extract full top-level JSON/JS object
     * arrays containing TFT unit rosters (even with nested items, traits, or hex coordinates).
     */
    internal fun extractCandidateCompArrays(source: String): List<String> {
        val out = mutableListOf<String>()
        val anchors = COMP_ARRAY_ANCHOR_REGEX.findAll(source).toList()
        var lastConsumedEnd = -1
        for (anchor in anchors) {
            val startIdx = anchor.range.first
            if (startIdx < lastConsumedEnd) continue
            var depth = 0
            var inString = false
            var stringChar = '"'
            var escaped = false
            var endIdx = -1
            val maxScan = (startIdx + 400_000).coerceAtMost(source.length)
            for (i in startIdx until maxScan) {
                val ch = source[i]
                if (escaped) {
                    escaped = false
                    continue
                }
                if (inString) {
                    if (ch == '\\') {
                        escaped = true
                    } else if (ch == stringChar) {
                        inString = false
                    }
                    continue
                }
                when (ch) {
                    '"', '\'' -> {
                        inString = true
                        stringChar = ch
                    }
                    '[' -> depth++
                    ']' -> {
                        depth--
                        if (depth == 0) {
                            endIdx = i + 1
                            break
                        }
                    }
                }
            }
            if (endIdx > startIdx) {
                val slice = source.substring(startIdx, endIdx)
                if (containsUnitListKey(slice)) {
                    out.add(slice)
                }
                lastConsumedEnd = endIdx
            }
        }
        return out
    }

    private fun extractCandidateCompObjects(source: String): List<String> {
        val out = mutableListOf<String>()
        val anchors = COMP_WRAPPER_KEY_REGEX.findAll(source).toList()
        var lastConsumedEnd = -1
        for (anchor in anchors) {
            val startIdx = source.indexOf('{', anchor.range.first)
            if (startIdx < 0 || startIdx < lastConsumedEnd) continue
            var depth = 0
            var inString = false
            var escaped = false
            var endIdx = -1
            val maxScan = (startIdx + 400_000).coerceAtMost(source.length)
            for (i in startIdx until maxScan) {
                val ch = source[i]
                if (escaped) {
                    escaped = false
                    continue
                }
                if (inString) {
                    if (ch == '\\') escaped = true
                    else if (ch == '"') inString = false
                    continue
                }
                when (ch) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            endIdx = i + 1
                            break
                        }
                    }
                }
            }
            if (endIdx > startIdx) {
                val slice = source.substring(startIdx, endIdx)
                if (containsUnitListKey(slice)) {
                    out.add(slice)
                }
                lastConsumedEnd = endIdx
            }
        }
        return out
    }

    private fun containsUnitListKey(text: String): Boolean =
        text.contains("\"units\"", ignoreCase = true) ||
            text.contains("\"champions\"", ignoreCase = true) ||
            text.contains("\"characters\"", ignoreCase = true) ||
            text.contains("\"members\"", ignoreCase = true) ||
            text.contains("\"board\"", ignoreCase = true) ||
            text.contains("\"finalComp\"", ignoreCase = true) ||
            text.contains("\"endGameBoard\"", ignoreCase = true) ||
            text.contains("\"coreUnits\"", ignoreCase = true)

    private fun collectCompsFromJson(
        node: Any?,
        targetEnv: TftEnvironment,
        patchTag: String,
        defaultSetLabel: String,
        inheritedEnv: TftEnvironment?,
        inheritedSet: String?,
        out: MutableList<MetaComp>,
        depth: Int = 0,
        requireExplicitEnvMatch: Boolean = false
    ) {
        if (node == null || depth > 14 || out.size >= 300) return
        when (node) {
            is JSONObject -> {
                // TFTAcademy marks unpublished drafts with `isPublic = false` and archived entries with `displayIndex >= 1000`
                if ((node.has("isPublic") && !node.optBoolean("isPublic", true)) ||
                    (node.has("displayIndex") && node.optInt("displayIndex", 0) >= 1000)
                ) {
                    return
                }

                val rawSet = listOf(
                    node.optString("set"),
                    node.optString("setName"),
                    node.optString("setNumber"),
                    node.optString("set_id"),
                    node.optString("setSlug")
                ).firstOrNull { it.isNotBlank() && !it.equals("null", ignoreCase = true) }.orEmpty()
                val currentSet = rawSet.takeIf { it.isNotBlank() }?.let {
                    if (it.all(Char::isDigit)) "Set $it" else it
                } ?: inheritedSet

                val nodeEnvFlag = when {
                    node.optBoolean("isPbe", false) || node.optBoolean("pbe", false) -> TftEnvironment.PBE
                    node.optString("environment").equals("PBE", ignoreCase = true) -> TftEnvironment.PBE
                    node.optString("queue").equals("PBE", ignoreCase = true) -> TftEnvironment.PBE
                    node.optString("mode").equals("PBE", ignoreCase = true) -> TftEnvironment.PBE
                    node.optString("environment").equals("LIVE", ignoreCase = true) -> TftEnvironment.LIVE
                    else -> inheritedEnv
                }

                val title = cleanText(
                    node.optString("title")
                        .ifBlank { node.optString("compTitle") }
                        .ifBlank { node.optString("guideTitle") }
                        .ifBlank { node.optString("name") }
                        .ifBlank { node.optString("compName") }
                        .ifBlank { node.optString("comp_name") }
                        .ifBlank { node.optString("displayName") }
                )
                val unitsArr = node.optJSONArray("units")
                    ?: node.optJSONArray("champions")
                    ?: node.optJSONArray("characters")
                    ?: node.optJSONArray("members")
                    ?: node.optJSONArray("board")
                    ?: node.optJSONArray("finalComp")
                    ?: node.optJSONArray("endGameBoard")
                    ?: node.optJSONArray("coreUnits")
                    ?: node.optJSONArray("team")
                    ?: node.optJSONArray("roster")

                if (title.length in 3..72 && unitsArr != null && unitsArr.length() >= 4) {
                    val effectiveEnv = if (requireExplicitEnvMatch) {
                        nodeEnvFlag
                    } else {
                        nodeEnvFlag ?: targetEnv
                    }
                    if (effectiveEnv == targetEnv) {
                        val mainChampObj = node.optJSONObject("mainChampion")
                        val mainChampRaw = mainChampObj?.optString("apiName").orEmpty()
                            .ifBlank { mainChampObj?.optString("name").orEmpty() }
                            .takeUnless { it.equals("null", ignoreCase = true) }
                            .orEmpty()
                        val mainChampCost = mainChampObj?.optInt("cost", 4)?.coerceIn(1, 5) ?: 4
                        val explicitCarryRaw = cleanText(
                            node.optString("carry")
                                .ifBlank { node.optString("carryName") }
                                .ifBlank { node.optString("mainCarry") }
                                .ifBlank { node.optString("primaryCarry") }
                                .ifBlank { mainChampRaw }
                        )
                        val explicitCarry = if (explicitCarryRaw.isNotBlank()) {
                            formatRiotAssetLabel(explicitCarryRaw)
                        } else {
                            ""
                        }

                        val parsedUnits = parseUnitsFromArray(
                            unitsArr = unitsArr,
                            mainChampName = explicitCarry,
                            mainChampCost = mainChampCost,
                            targetEnv = targetEnv
                        )
                        val unitNames = parsedUnits.map { it.name }
                        if (unitNames.size >= 4) {
                            val carry = explicitCarry.ifBlank {
                                parsedUnits.firstOrNull { it.isCarry }?.name
                                    ?: parsedUnits.maxByOrNull { it.items.size * 10 + it.cost }?.name
                                    ?: unitNames.last()
                            }
                            val tierRaw = node.optString("tier")
                                .ifBlank { node.optString("tierRank") }
                                .ifBlank { node.optString("rank") }
                            val tier = TierRank.fromRaw(tierRaw)
                            val playstyle = cleanText(
                                node.optString("playstyle")
                                    .ifBlank { node.optString("style") }
                                    .ifBlank { node.optString("leveling") }
                                    .ifBlank { node.optString("tempo") }
                                    .ifBlank { node.optString("compType") }
                            ).ifBlank { "Fast 8" }
                            val rawTraits = extractStringList(node.optJSONArray("traits") ?: node.optJSONArray("synergies"))
                                .map { formatRiotAssetLabel(it) }
                            val traits = if (rawTraits.isNotEmpty()) {
                                rawTraits
                            } else {
                                deriveTraitsFromUnits(unitNames, targetEnv = targetEnv)
                            }
                            val augments = extractStringList(
                                node.optJSONArray("augments")
                                    ?: node.optJSONArray("recommendedAugments")
                                    ?: node.optJSONArray("augmentPriorities")
                            ).map { formatRiotAssetLabel(it) }
                            val resolvedSet = currentSet ?: defaultSetLabel
                            val displayOrderOffset = if (node.has("displayIndex")) {
                                node.optInt("displayIndex", 0).coerceIn(0, 500) * 0.002
                            } else {
                                0.0
                            }
                            val statsObj = node.optJSONObject("stats")
                            val avgPlace = firstValidDouble(
                                node.optDouble("avgPlace"),
                                node.optDouble("avgPlacement"),
                                node.optDouble("avg_placement"),
                                statsObj?.optDouble("avgPlacement") ?: Double.NaN,
                                statsObj?.optDouble("avgPlace") ?: Double.NaN,
                                fallback = when (tier) {
                                    TierRank.S -> 3.45
                                    TierRank.A -> 3.82
                                    TierRank.B -> 4.18
                                    TierRank.C -> 4.55
                                    TierRank.X -> 4.35
                                } + displayOrderOffset
                            )
                            val top4 = firstValidDouble(
                                node.optDouble("top4Rate"),
                                node.optDouble("top4"),
                                node.optDouble("top_4_rate"),
                                statsObj?.optDouble("top4Rate") ?: Double.NaN,
                                fallback = when (tier) {
                                    TierRank.S -> 65.0
                                    TierRank.A -> 58.0
                                    TierRank.B -> 51.5
                                    TierRank.C -> 45.0
                                    TierRank.X -> 48.0
                                }
                            ).let { if (it in 0.0..1.0) it * 100.0 else it }
                            val win = firstValidDouble(
                                node.optDouble("winRate"),
                                node.optDouble("win_rate"),
                                statsObj?.optDouble("winRate") ?: Double.NaN,
                                fallback = when (tier) {
                                    TierRank.S -> 18.5
                                    TierRank.A -> 14.5
                                    TierRank.B -> 11.5
                                    TierRank.C -> 8.5
                                    TierRank.X -> 10.5
                                }
                            ).let { if (it in 0.0..1.0) it * 100.0 else it }
                            val pick = firstValidDouble(
                                node.optDouble("pickRate"),
                                node.optDouble("pick_rate"),
                                statsObj?.optDouble("pickRate") ?: Double.NaN,
                                fallback = 7.0
                            ).let { if (it in 0.0..1.0) it * 100.0 else it }

                            val placements = buildPlacements(parsedUnits, carry)
                            val slugPart = node.optString("compSlug")
                                .ifBlank { node.optString("slug") }
                                .ifBlank { title }
                                .lowercase(Locale.US)
                                .replace(Regex("[^a-z0-9]+"), "_")
                                .trim('_')
                            val compId = "tftacademy_${targetEnv.name.lowercase(Locale.US)}_$slugPart"
                            val leveling = cleanText(
                                node.optString("levelingGuide")
                                    .ifBlank { node.optString("gameplan") }
                                    .ifBlank { node.optString("howToPlay") }
                                    .ifBlank { formatTipsArray(node.optJSONArray("tips")) }
                            ).ifBlank { "$playstyle • $resolvedSet ($patchTag)" }
                            val compNotes = cleanText(
                                node.optString("notes")
                                    .ifBlank { node.optString("summary") }
                                    .ifBlank { node.optString("description") }
                                    .ifBlank { node.optString("augmentsTip") }
                            )

                            out.add(
                                MetaComp(
                                    id = compId,
                                    environment = targetEnv,
                                    title = title,
                                    tier = tier,
                                    playstyle = playstyle,
                                    avgPlacement = avgPlace,
                                    top4Rate = top4,
                                    winRate = win,
                                    pickRate = pick,
                                    trendDelta = "Top Meta",
                                    isTrending = tier == TierRank.S,
                                    patchTag = patchTag,
                                    carryName = carry,
                                    traits = traits,
                                    coreUnits = unitNames.distinct(),
                                    hexPlacements = placements,
                                    augmentPriorities = augments,
                                    levelingGuide = leveling,
                                    notes = compNotes,
                                    setName = resolvedSet,
                                    sourceUrl = compUrlForEnvironment(targetEnv)
                                )
                            )
                        }
                    }
                }

                val keys = node.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val branchEnv = when {
                        key.equals("pbe", ignoreCase = true) || key.equals("pbeComps", ignoreCase = true) -> TftEnvironment.PBE
                        key.equals("live", ignoreCase = true) || key.equals("liveComps", ignoreCase = true) -> TftEnvironment.LIVE
                        else -> nodeEnvFlag
                    }
                    collectCompsFromJson(
                        node = node.opt(key),
                        targetEnv = targetEnv,
                        patchTag = patchTag,
                        defaultSetLabel = defaultSetLabel,
                        inheritedEnv = branchEnv,
                        inheritedSet = currentSet,
                        out = out,
                        depth = depth + 1,
                        requireExplicitEnvMatch = requireExplicitEnvMatch
                    )
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length().coerceAtMost(500)) {
                    collectCompsFromJson(
                        node = node.opt(i),
                        targetEnv = targetEnv,
                        patchTag = patchTag,
                        defaultSetLabel = defaultSetLabel,
                        inheritedEnv = inheritedEnv,
                        inheritedSet = inheritedSet,
                        out = out,
                        depth = depth + 1,
                        requireExplicitEnvMatch = requireExplicitEnvMatch
                    )
                }
            }
        }
    }

    private fun deriveTraitsFromUnits(
        unitNames: List<String>,
        targetEnv: TftEnvironment = TftEnvironment.LIVE
    ): List<String> {
        val counts = LinkedHashMap<String, Int>()
        unitNames.distinct().forEach { unit ->
            TftAssetIconResolver.getChampionTraits(unit, targetEnv).forEach { trait ->
                counts[trait] = (counts[trait] ?: 0) + 1
            }
        }
        return counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .filter { it.value >= 2 }
            .take(5)
            .map { "${it.value} ${it.key}" }
            .ifEmpty {
                counts.entries
                    .sortedByDescending { it.value }
                    .take(4)
                    .map { "${it.value} ${it.key}" }
            }
    }

    private data class ParsedJsonUnit(
        val name: String,
        val cost: Int,
        val items: List<String>,
        val row: Int?,
        val col: Int?,
        val isCarry: Boolean,
        val stars: Int
    )

    private fun parseUnitsFromArray(
        unitsArr: JSONArray,
        mainChampName: String = "",
        mainChampCost: Int = 3,
        targetEnv: TftEnvironment = TftEnvironment.LIVE
    ): List<ParsedJsonUnit> {
        return (0 until unitsArr.length().coerceAtMost(16)).mapNotNull { idx ->
            when (val u = unitsArr.opt(idx)) {
                is String -> cleanText(u).takeIf { it.isNotBlank() }?.let { rawName ->
                    val name = formatRiotAssetLabel(rawName)
                    val catalogCost = TftAssetIconResolver.getChampionCost(rawName, targetEnv)
                        ?: TftAssetIconResolver.getChampionCost(name, targetEnv)
                    ParsedJsonUnit(
                        name = name,
                        cost = (catalogCost ?: if (name.equals(mainChampName, ignoreCase = true)) mainChampCost else 3).coerceIn(1, 5),
                        items = emptyList(),
                        row = null,
                        col = null,
                        isCarry = name.equals(mainChampName, ignoreCase = true),
                        stars = 2
                    )
                }
                is JSONObject -> {
                    val rawName = cleanText(
                        u.optString("name")
                            .ifBlank { u.optString("champion") }
                            .ifBlank { u.optString("unit") }
                            .ifBlank { u.optString("apiName") }
                            .ifBlank { u.optString("character_id") }
                            .ifBlank { u.optString("championId") }
                            .ifBlank { u.optString("id") }
                    ).takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val name = formatRiotAssetLabel(rawName)
                    val matchesMainChamp = mainChampName.isNotBlank() && name.equals(mainChampName, ignoreCase = true)
                    val catalogCost = TftAssetIconResolver.getChampionCost(rawName, targetEnv)
                        ?: TftAssetIconResolver.getChampionCost(name, targetEnv)
                    val defaultCost = catalogCost ?: if (matchesMainChamp) mainChampCost else 3
                    val explicitCost = if (u.has("cost")) u.optInt("cost", defaultCost) else if (u.has("tier")) u.optInt("tier", defaultCost) else defaultCost
                    val cost = explicitCost.coerceIn(1, 5)
                    val items = extractStringList(
                        u.optJSONArray("items")
                            ?: u.optJSONArray("itemNames")
                            ?: u.optJSONArray("recommendedItems")
                    ).map { formatRiotAssetLabel(it) }
                    val posObj = u.optJSONObject("position") ?: u.optJSONObject("coord") ?: u.optJSONObject("hex")
                    val rawRow = u.optInt("row", u.optInt("y", posObj?.optInt("row", posObj.optInt("y", -1)) ?: -1))
                    val rawCol = u.optInt("col", u.optInt("x", posObj?.optInt("col", posObj.optInt("x", -1)) ?: -1))
                    val cellIdx = u.optInt("boardIndex", u.optInt("cell", u.optInt("slot", -1)))
                    val row = when {
                        rawRow in 0..3 -> rawRow
                        cellIdx in 0..27 -> (cellIdx / 7).coerceIn(0, 3)
                        else -> null
                    }
                    val col = when {
                        rawCol in 0..6 -> rawCol
                        cellIdx in 0..27 -> (cellIdx % 7).coerceIn(0, 6)
                        else -> null
                    }
                    val isCarry = matchesMainChamp ||
                        u.optBoolean("isCarry", false) ||
                        u.optBoolean("carry", false) ||
                        u.optBoolean("isMainCarry", false)
                    val rawStars = u.optInt("stars", if (u.optBoolean("threeStar", false) || u.optBoolean("isThreeStar", false)) 3 else 2)
                    val stars = (if (rawStars <= 1 && isCarry) 2 else rawStars).coerceIn(1, 3)
                    ParsedJsonUnit(name = name, cost = cost, items = items, row = row, col = col, isCarry = isCarry, stars = stars)
                }
                else -> null
            }
        }
    }

    private fun parseDomTeamComps(
        html: String,
        targetEnv: TftEnvironment,
        patchTag: String,
        defaultSetLabel: String,
        isDedicatedEnvPage: Boolean = true
    ): List<MetaComp> {
        val comps = mutableListOf<MetaComp>()
        val cards = extractCompBlocks(html)
        for ((cardStartIdx, blockHtml) in cards) {
            val isPbeCard = blockHtml.contains("data-env=\"pbe\"", ignoreCase = true) ||
                blockHtml.contains("pbe-badge", ignoreCase = true) ||
                blockHtml.contains("class=\"pbe", ignoreCase = true)
            val cardEnv = when {
                isPbeCard -> TftEnvironment.PBE
                targetEnv == TftEnvironment.PBE && !isDedicatedEnvPage -> TftEnvironment.LIVE
                else -> targetEnv
            }
            if (cardEnv != targetEnv) continue

            val title = cleanText(
                COMP_NAME_REGEX.find(blockHtml)?.groupValues?.getOrNull(1)
                    ?: HEADING_TITLE_REGEX.find(blockHtml)?.groupValues?.getOrNull(1)
                    ?: ""
            )
            if (title.length !in 3..72) continue

            val explicitTier = COMP_TIER_REGEX.find(blockHtml)?.groupValues?.getOrNull(1)
                ?: DATA_TIER_ATTR_REGEX.find(blockHtml)?.groupValues?.getOrNull(1)
            val sectionTier = explicitTier ?: inferPrecedingSectionTier(html, cardStartIdx)
            val tier = TierRank.fromRaw(sectionTier ?: "B")
            val playstyle = cleanText(COMP_PLAYSTYLE_REGEX.find(blockHtml)?.groupValues?.getOrNull(1).orEmpty()).ifBlank { "Fast 8" }

            val unitNames = UNIT_ALT_REGEX.findAll(blockHtml)
                .map { cleanText(it.groupValues[1]) }
                .filter { candidate ->
                    candidate.isNotBlank() &&
                        !candidate.equals(title, ignoreCase = true) &&
                        !looksLikeItemOrUiLabel(candidate)
                }
                .distinct()
                .take(10)
                .toList()
            if (unitNames.size < 4) continue

            val cardSet = SET_LABEL_REGEX.find(blockHtml)?.let { m ->
                "Set ${m.groupValues[1]}"
            } ?: defaultSetLabel
            val carry = unitNames.last()
            val placements = buildPlacements(
                unitNames.map {
                    val cost = TftAssetIconResolver.getChampionCost(it, targetEnv) ?: if (it == carry) 4 else 3
                    ParsedJsonUnit(it, cost.coerceIn(1, 5), emptyList(), null, null, it == carry, 2)
                },
                carry
            )
            val derivedTraits = deriveTraitsFromUnits(unitNames, targetEnv = targetEnv)
            val slugId = title.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_").trim('_')
            comps.add(
                MetaComp(
                    id = "tftacademy_${targetEnv.name.lowercase(Locale.US)}_$slugId",
                    environment = targetEnv,
                    title = title,
                    tier = tier,
                    playstyle = playstyle,
                    avgPlacement = when (tier) {
                        TierRank.S -> 3.45
                        TierRank.A -> 3.82
                        TierRank.B -> 4.18
                        TierRank.C -> 4.55
                        TierRank.X -> 4.35
                    },
                    top4Rate = when (tier) {
                        TierRank.S -> 65.0
                        TierRank.A -> 58.0
                        TierRank.B -> 51.5
                        TierRank.C -> 45.0
                        TierRank.X -> 48.0
                    },
                    winRate = when (tier) {
                        TierRank.S -> 18.5
                        TierRank.A -> 14.5
                        TierRank.B -> 11.5
                        TierRank.C -> 8.5
                        TierRank.X -> 10.5
                    },
                    pickRate = 7.0,
                    trendDelta = "Top Meta",
                    isTrending = tier == TierRank.S,
                    patchTag = patchTag,
                    carryName = carry,
                    traits = derivedTraits,
                    coreUnits = unitNames,
                    hexPlacements = placements,
                    augmentPriorities = emptyList(),
                    levelingGuide = "$playstyle • $cardSet ($patchTag)",
                    notes = "",
                    setName = cardSet,
                    sourceUrl = compUrlForEnvironment(targetEnv)
                )
            )
        }
        return comps
    }

    /**
     * Scans all matching comp card `<div>` and `<a href="/tierlist/comps/...">` blocks
     * across the full HTML document, balancing nested tags so every tier and below-the-fold row is preserved.
     */
    private fun extractCompBlocks(html: String): List<Pair<Int, String>> {
        val starts = COMP_BLOCK_START_REGEX.findAll(html).toList()
        if (starts.isEmpty()) return emptyList()

        val result = mutableListOf<Pair<Int, String>>()
        for (idx in starts.indices) {
            val startPos = starts[idx].range.first
            val nextStart = if (idx + 1 < starts.size) starts[idx + 1].range.first else html.length
            val isAnchor = starts[idx].value.startsWith("<a", ignoreCase = true)
            val tagRegex = if (isAnchor) ANCHOR_TAG_REGEX else DIV_TAG_REGEX

            var depth = 0
            var endPos = nextStart
            for (tag in tagRegex.findAll(html, startPos)) {
                if (tag.range.first >= nextStart) break
                val tagText = tag.value
                if (tagText.startsWith("</", ignoreCase = true)) {
                    depth--
                    if (depth <= 0) {
                        endPos = (tag.range.last + 1).coerceAtMost(nextStart)
                        break
                    }
                } else if (!tagText.endsWith("/>")) {
                    depth++
                }
            }
            if (endPos > startPos) {
                result.add(startPos to html.substring(startPos, endPos))
            }
        }
        return result
    }

    private fun inferPrecedingSectionTier(html: String, cardStartIdx: Int): String? {
        val windowStart = (cardStartIdx - 4500).coerceAtLeast(0)
        val prefix = html.substring(windowStart, cardStartIdx)
        val lastMatch = SECTION_TIER_REGEX.findAll(prefix).lastOrNull() ?: return null
        return lastMatch.groupValues.drop(1).firstOrNull { it.isNotBlank() && it.length <= 2 }
    }

    private fun buildPlacements(units: List<ParsedJsonUnit>, carry: String): List<HexUnitPlacement> {
        return units.mapIndexed { idx, unit ->
            val isCarryUnit = unit.isCarry || unit.name.equals(carry, ignoreCase = true)
            HexUnitPlacement(
                unitName = unit.name,
                row = unit.row ?: if (idx < units.size / 2) 0 else 3,
                col = unit.col ?: ((idx * 2) % 7),
                cost = if (isCarryUnit && unit.cost <= 3) 4 else unit.cost,
                items = unit.items,
                isCarry = isCarryUnit,
                stars = unit.stars
            )
        }
    }

    private fun extractStringList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length().coerceAtMost(10)).mapNotNull { i ->
            when (val el = arr.opt(i)) {
                is String -> cleanText(el).takeIf { it.isNotBlank() }
                is JSONObject -> {
                    if (el.optBoolean("disabled", false)) return@mapNotNull null
                    cleanText(
                        el.optString("name")
                            .ifBlank { el.optString("label") }
                            .ifBlank { el.optString("title") }
                            .ifBlank { el.optString("apiName") }
                    ).takeIf { it.isNotBlank() }
                }
                else -> null
            }
        }
    }

    private fun formatTipsArray(arr: JSONArray?): String {
        if (arr == null || arr.length() == 0) return ""
        return (0 until arr.length().coerceAtMost(5)).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            val stage = cleanText(obj.optString("stage"))
            val tip = cleanText(obj.optString("tip"))
            when {
                stage.isNotBlank() && tip.isNotBlank() -> "$stage: $tip"
                tip.isNotBlank() -> tip
                else -> null
            }
        }.joinToString(" • ")
    }

    private fun firstValidDouble(vararg values: Double, fallback: Double): Double {
        return values.firstOrNull { !it.isNaN() && it > 0.0 } ?: fallback
    }

    private fun findPbeCompsUrl(html: String): String? {
        val m = PBE_LINK_REGEX.find(html) ?: return null
        val href = m.groupValues[1]
        return if (href.startsWith("http")) href else "$TFTACADEMY_ORIGIN${if (href.startsWith("/")) href else "/$href"}"
    }

    private fun inferSetNumberFromPatch(patchVersion: String): Int? {
        return Regex("""\b(1[3-9]|2[0-9])\.""").find(patchVersion)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun extractSetNumber(text: String): Int? {
        return Regex("""Set\s*(\d+)""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun looksLikeItemOrUiLabel(label: String): Boolean {
        val lower = label.lowercase(Locale.US)
        return lower.contains("tier") ||
            lower.contains("tftacademy") ||
            lower.contains("arrow") ||
            lower.contains("icon") ||
            lower.contains("item") ||
            lower.contains("augment")
    }

    private fun fetchUrl(url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .cacheControl(CacheControl.FORCE_NETWORK)
                .header("Cache-Control", "no-cache, no-store, max-age=0")
                .header("Pragma", "no-cache")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/131.0 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string()?.takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun cleanText(raw: String): String {
        return raw
            .replace(HTML_TAG_REGEX, " ")
            .replace("&amp;", "&")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&quot;", "\"")
            .replace(WHITESPACE_REGEX, " ")
            .trim()
    }

    companion object {
        const val TFTACADEMY_ORIGIN = "https://tftacademy.com"
        const val TFTACADEMY_COMPS_URL = "https://tftacademy.com/tierlist/comps"
        const val TFTACADEMY_PBE_COMPS_URL = "https://tftacademy.com/tierlist/comps/set19"

        fun compUrlForEnvironment(environment: TftEnvironment): String =
            if (environment == TftEnvironment.PBE) TFTACADEMY_PBE_COMPS_URL else TFTACADEMY_COMPS_URL

        /**
         * Stable SHA-256 fingerprint across structural composition fields (new comps, removals,
         * tier shifts, unit/item/augment edits, positioning changes, or guide updates) while
         * ignoring volatile live decimal stat ticks and injected patch strings.
         */
        fun computeCompsSignature(liveComps: List<MetaComp>, pbeComps: List<MetaComp>): String {
            val digest = MessageDigest.getInstance("SHA-256")
            fun appendComps(prefix: String, list: List<MetaComp>) {
                list.sortedBy { it.id }.forEach { c ->
                    val line = buildString {
                        append(prefix).append('|')
                        append(c.id).append('|')
                        append(c.title).append('|')
                        append(c.tier.name).append('|')
                        append(c.playstyle).append('|')
                        append(c.carryName).append('|')
                        append(c.traits.joinToString(",")).append('|')
                        append(c.coreUnits.joinToString(",")).append('|')
                        append(c.augmentPriorities.joinToString(",")).append('|')
                        append(c.hexPlacements.joinToString(";") { "${it.unitName}:${it.row},${it.col}:${it.stars}:${it.items.joinToString("+")}" }).append('|')
                        append(c.levelingGuide).append('|')
                        append(c.notes).append('\n')
                    }
                    digest.update(line.toByteArray(Charsets.UTF_8))
                }
            }
            appendComps("LIVE", liveComps)
            appendComps("PBE", pbeComps)
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        private val SVELTEKIT_FETCHED_SCRIPT_REGEX = Regex(
            """<script[^>]*data-sveltekit-fetched[^>]*>(.*?)</script>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val API_SET_QUERY_REGEX = Regex(
            """/api/tierlist/comps\?[^"'\s]*set=(\d+)""",
            RegexOption.IGNORE_CASE
        )
        private val NEXT_DATA_REGEX = Regex(
            """<script[^>]*id=["']__NEXT_DATA__["'][^>]*>(.*?)</script>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val NEXT_FLIGHT_CHUNK_REGEX = Regex(
            """self\.__next_f\.push\(\s*\[\s*\d+\s*,\s*"((?:[^"\\]|\\.)*)"\s*\]\s*\)""",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )
        private val NEXT_BUILD_ID_REGEX = Regex(""""buildId"\s*:\s*"([^"]+)"""")
        private val INLINE_JSON_STATE_REGEX = Regex(
            """window\.__(?:PRELOADED|INITIAL|APOLLO|REACT_QUERY|NUXT)_STATE__\s*=\s*(\{.*?\});""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val COMP_ARRAY_ANCHOR_REGEX = Regex(
            """\[\s*\{\s*"(?:title|compTitle|guideTitle|name|compName|comp_name|displayName|tier|tierRank|slug|compSlug)"\s*:"""
        )
        private val COMP_WRAPPER_KEY_REGEX = Regex(
            """"(?:comps|guides|tierlist|metaComps|builds)"\s*:\s*\{"""
        )
        private val DATA_URL_REGEX = Regex(
            """https://[A-Za-z0-9._/-]+(?:comps|tft|meta|tier|guide)[^"'\s<>]*\.json(?:\?[^"'\s<>]*)?""",
            RegexOption.IGNORE_CASE
        )
        private val RELATIVE_API_URL_REGEX = Regex(
            """["'](/(?:api|_next/data|data)/[^"'\s<>]*(?:comp|tier|guide|meta)[^"'\s<>]*)["']""",
            RegexOption.IGNORE_CASE
        )
        private val SCRIPT_SRC_REGEX = Regex(
            """<script[^>]+src=["']([^"']+\.js(?:\?[^"']*)?)["']""",
            RegexOption.IGNORE_CASE
        )
        private val PBE_LINK_REGEX = Regex(
            """href=["']([^"']*tierlist/comps(?:/set19|[^"']*pbe)[^"']*)["']""",
            RegexOption.IGNORE_CASE
        )
        private val SET_LABEL_REGEX = Regex(
            """\bSet\s+(\d+)(?:\s*:\s*([A-Za-z0-9 '&-]{2,28}))?""",
            RegexOption.IGNORE_CASE
        )
        private val SET_PATH_OR_JSON_REGEX = Regex(
            """(?:/tierlist/comps/set(\d+)|"set"\s*:\s*(\d+)|/api/tierlist/comps\?[^"'\s]*set=(\d+))""",
            RegexOption.IGNORE_CASE
        )
        private val COMP_BLOCK_START_REGEX = Regex(
            """<(?:div[^>]*(?:class=["'][^"']*(?:comp-(?:row|card|item|container)|guide-(?:card|row)|tier-(?:card|row|comp)|tierlist-comp|tftacademy-comp)[^"']*["']|data-testid=["'][^"']*comp[^"']*["'])|a[^>]*href=["'][^"']*?/tierlist/comps/[^"']+["'])[^>]*>""",
            RegexOption.IGNORE_CASE
        )
        private val DIV_TAG_REGEX = Regex("""</?div\b[^>]*>""", RegexOption.IGNORE_CASE)
        private val ANCHOR_TAG_REGEX = Regex("""</?a\b[^>]*>""", RegexOption.IGNORE_CASE)
        private val COMP_NAME_REGEX = Regex(
            """(?:class=["'][^"']*(?:comp-name|comp-title|guide-title|team-name|title)[^"']*["']|data-testid=["']comp-name["'])[^>]*>(.*?)<""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val HEADING_TITLE_REGEX = Regex(
            """<h[2-5][^>]*>(.*?)</h[2-5]>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val COMP_TIER_REGEX = Regex(
            """class=["'][^"']*(?:tier-badge|team-rank|comp-tier|tier-pill)[^"']*["'][^>]*>\s*([SABCDX]\+?)\s*<""",
            RegexOption.IGNORE_CASE
        )
        private val DATA_TIER_ATTR_REGEX = Regex(
            """data-tier=["']([SABCDX]\+?)["']""",
            RegexOption.IGNORE_CASE
        )
        private val SECTION_TIER_REGEX = Regex(
            """(?:tier[-_]label[^>]*>\s*([SABCDX]\+?)|<h[1-4][^>]*>\s*([SABCDX]\+?)\s*(?:Tier)?)""",
            RegexOption.IGNORE_CASE
        )
        private val COMP_PLAYSTYLE_REGEX = Regex(
            """class=["'][^"']*(?:playstyle|team-playstyle|comp-style|comp-tempo|guide-tempo)[^"']*["'][^>]*>(.*?)<""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val UNIT_ALT_REGEX = Regex(
            """(?:alt|data-unit|data-champion|title)=["']([A-Za-z0-9 '&.\-]{2,24})["']""",
            RegexOption.IGNORE_CASE
        )
        private val HTML_TAG_REGEX = Regex("""<[^>]+>""")
        private val WHITESPACE_REGEX = Regex("""\s+""")
    }
}
