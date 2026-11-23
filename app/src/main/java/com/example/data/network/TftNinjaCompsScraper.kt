package com.example.data.network

import com.example.data.model.HexUnitPlacement
import com.example.data.model.LevelTransitionStage
import com.example.data.model.MetaComp
import com.example.data.model.TftEnvironment
import com.example.data.model.TierRank
import com.example.data.teamplanner.TftTeamPlannerCodeEncoder
import com.example.ui.components.TftAssetIconResolver
import com.example.ui.components.formatChampionDisplayName
import com.example.ui.components.formatRiotAssetLabel
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Per-champion recommendations scraped from tft.ninja (both from tft.ninja compositions
 * and tft.ninja champion/unit stats pages when available).
 */
data class TftNinjaChampionRecommendation(
    val championName: String,
    val recommendedItems: List<String> = emptyList(),
    val recommendedCompTitles: List<String> = emptyList(),
    val avgPlacement: Double? = null,
    val top4Rate: Double? = null,
    val tier: TierRank? = null
)

data class TftNinjaCompsFetchResult(
    val comps: List<MetaComp>,
    val championRecommendations: Map<String, TftNinjaChampionRecommendation> = emptyMap(),
    val detectedSet: Int? = null,
    val fetchedFromNetwork: Boolean = false
)

/**
 * Dedicated scraper for tft.ninja team compositions ("armies") and per-champion recommendations.
 *
 * LIVE comps source: https://tft.ninja/comps
 * PBE comps source:  https://tft.ninja/comps?e=pbe
 *
 * Supports:
 * 1. Direct JSON API responses (arrays, objects, or tier-keyed maps).
 * 2. Next.js App Router RSC flight chunks (`self.__next_f.push([1,"..."])`), concatenated in order
 *    so payloads split across chunks parse cleanly.
 * 3. Next.js Pages Router `__NEXT_DATA__` and `/_next/data/<buildId>/comps.json`.
 * 4. Inline JSON/state `<script>` blocks and discovered Next.js JS chunk API endpoints.
 * 5. Per-comp detail page enrichment for full hex boards, per-unit items, augments, and tips.
 * 6. Full-document HTML DOM fallback when comps are rendered directly in markup.
 */
class TftNinjaCompsScraper(
    private val urlFetcher: (String) -> String? = { url -> defaultFetchUrl(url) },
    private val rscFetcher: ((String) -> String?)? = null
) {

    /**
     * Fetches LIVE team compositions exclusively from https://tft.ninja/comps.
     */
    fun fetchLiveTeamComps(
        livePatchVersion: String = "18.x"
    ): TftNinjaCompsFetchResult {
        return fetchCompsFromUrl(
            pageUrl = TFTNINJA_LIVE_COMPS_URL,
            targetEnv = TftEnvironment.LIVE,
            patchTag = livePatchVersion,
            defaultSetNumber = 18
        )
    }

    /**
     * Fetches PBE team compositions exclusively from https://tft.ninja/comps?e=pbe.
     */
    fun fetchPbeTeamComps(
        pbePatchVersion: String = "19.1-PBE",
        defaultPbeSetNumber: Int = 19
    ): TftNinjaCompsFetchResult {
        return fetchCompsFromUrl(
            pageUrl = TFTNINJA_PBE_COMPS_URL,
            targetEnv = TftEnvironment.PBE,
            patchTag = pbePatchVersion,
            defaultSetNumber = defaultPbeSetNumber
        )
    }

    private fun fetchCompsFromUrl(
        pageUrl: String,
        targetEnv: TftEnvironment,
        patchTag: String,
        defaultSetNumber: Int
    ): TftNinjaCompsFetchResult {
        val isPbe = targetEnv == TftEnvironment.PBE
        val html = urlFetcher(pageUrl)
        val fetchedAny = !html.isNullOrBlank()
        var detectedSet = html?.let { detectSetNumber(it) } ?: defaultSetNumber

        val collected = LinkedHashMap<String, MetaComp>()
        val pageChampRecs = LinkedHashMap<String, TftNinjaChampionRecommendation>()

        if (!html.isNullOrBlank()) {
            val parsedDirect = parseCompsDocument(
                rawBody = html,
                targetEnv = targetEnv,
                patchTag = patchTag,
                setNumber = detectedSet,
                defaultSourceUrl = pageUrl
            )
            parsedDirect.forEach { mergeComp(collected, it) }
            mergeChampRecs(pageChampRecs, extractChampionRecommendationsFromDocument(html))

            // Next.js Pages Router /_next/data/<buildId>/comps.json if buildId is present
            if (collected.isEmpty()) {
                extractNextBuildId(html)?.let { buildId ->
                    val nextDataUrl = if (isPbe) {
                        "$TFTNINJA_ORIGIN/_next/data/$buildId/comps.json?e=pbe"
                    } else {
                        "$TFTNINJA_ORIGIN/_next/data/$buildId/comps.json"
                    }
                    val nextJson = urlFetcher(nextDataUrl)
                    if (!nextJson.isNullOrBlank()) {
                        detectSetNumber(nextJson)?.let { detectedSet = it }
                        parseCompsDocument(nextJson, targetEnv, patchTag, detectedSet, pageUrl)
                            .forEach { mergeComp(collected, it) }
                        mergeChampRecs(pageChampRecs, extractChampionRecommendationsFromDocument(nextJson))
                    }
                }
            }

            // Next.js App Router RSC direct flight request fallback
            if (collected.isEmpty()) {
                val rscBody = (rscFetcher ?: { url -> defaultFetchUrl(url, rscHeaders = true) }).invoke(pageUrl)
                if (!rscBody.isNullOrBlank()) {
                    detectSetNumber(rscBody)?.let { detectedSet = it }
                    parseCompsDocument(rscBody, targetEnv, patchTag, detectedSet, pageUrl)
                        .forEach { mergeComp(collected, it) }
                    mergeChampRecs(pageChampRecs, extractChampionRecommendationsFromDocument(rscBody))
                }
            }

            // Discover API / data JSON endpoints referenced in HTML or Next.js static chunks
            if (collected.isEmpty()) {
                val candidateEndpoints = LinkedHashSet<String>()
                candidateEndpoints.addAll(discoverApiEndpoints(html, isPbe))

                val chunkUrls = discoverStaticJsChunks(html).take(6)
                for (chunkUrl in chunkUrls) {
                    val jsBody = urlFetcher(chunkUrl) ?: continue
                    candidateEndpoints.addAll(discoverApiEndpoints(jsBody, isPbe))
                }

                // Common tft.ninja API route candidates as last-resort network discovery
                candidateEndpoints.addAll(defaultApiProbeUrls(isPbe))

                for (endpoint in candidateEndpoints.take(10)) {
                    val apiBody = urlFetcher(endpoint) ?: continue
                    if (apiBody.isBlank()) continue
                    detectSetNumber(apiBody)?.let { detectedSet = it }
                    val parsedApi = parseCompsDocument(
                        rawBody = apiBody,
                        targetEnv = targetEnv,
                        patchTag = patchTag,
                        setNumber = detectedSet,
                        defaultSourceUrl = pageUrl
                    )
                    if (parsedApi.isNotEmpty()) {
                        parsedApi.forEach { mergeComp(collected, it) }
                        mergeChampRecs(pageChampRecs, extractChampionRecommendationsFromDocument(apiBody))
                        break
                    }
                }
            }
        }

        // Enrich any comp with a dedicated tft.ninja detail page URL when missing full build details
        val enrichedList = enrichCompsIfNeeded(
            comps = collected.values.toList(),
            targetEnv = targetEnv,
            patchTag = patchTag,
            setNumber = detectedSet,
            defaultSourceUrl = pageUrl
        )

        // Optionally fetch tft.ninja champion/unit recommendations page for richer BIS items per champion
        if (fetchedAny) {
            val champUrls = if (isPbe) TFTNINJA_PBE_CHAMPIONS_URLS else TFTNINJA_LIVE_CHAMPIONS_URLS
            for (champUrl in champUrls) {
                val body = urlFetcher(champUrl)
                if (!body.isNullOrBlank()) {
                    val extracted = extractChampionRecommendationsFromDocument(body)
                    if (extracted.isNotEmpty()) {
                        mergeChampRecs(pageChampRecs, extracted)
                        break
                    }
                }
            }
        }

        val sortedComps = sortCompsTierSDown(enrichedList)

        val finalChampRecs = buildAggregatedChampionRecommendations(
            comps = sortedComps,
            siteChampionRecs = pageChampRecs
        )

        // Enrich ONLY the composition's carries with additional viable items recommended on tft.ninja;
        // non-carry units must not display items or Other Items.
        val compsWithOtherItems = sortedComps.map { comp ->
            val carryKey = normKey(comp.carryName)
            val updatedPlacements = comp.hexPlacements.map { placement ->
                val champKey = normKey(placement.unitName)
                val isCompCarry = placement.isCarry ||
                    (carryKey.isNotBlank() && champKey == carryKey) ||
                    placement.items.isNotEmpty()
                if (!isCompCarry) {
                    if (placement.otherItems.isEmpty()) placement else placement.copy(otherItems = emptyList())
                } else {
                    val primaryKeys = placement.items.map { normKey(it) }.toSet()
                    val recItems = finalChampRecs[champKey]?.recommendedItems.orEmpty()
                    val mergedOthers = (placement.otherItems + recItems)
                        .map { cleanItemLabel(it) }
                        .filter { it.isNotBlank() && normKey(it) !in primaryKeys }
                        .distinct()
                        .take(5)
                    if (mergedOthers == placement.otherItems) placement else placement.copy(otherItems = mergedOthers)
                }
            }
            comp.copy(hexPlacements = updatedPlacements)
        }

        return TftNinjaCompsFetchResult(
            comps = compsWithOtherItems,
            championRecommendations = finalChampRecs,
            detectedSet = detectedSet,
            fetchedFromNetwork = fetchedAny
        )
    }

    /**
     * Parses raw HTML, Next.js RSC flight stream, `__NEXT_DATA__`, or JSON API response from tft.ninja.
     */
    fun parseCompsDocument(
        rawBody: String,
        targetEnv: TftEnvironment,
        patchTag: String,
        setNumber: Int,
        defaultSourceUrl: String = if (targetEnv == TftEnvironment.PBE) TFTNINJA_PBE_COMPS_URL else TFTNINJA_LIVE_COMPS_URL
    ): List<MetaComp> {
        val trimmed = rawBody.trim()
        if (trimmed.isBlank()) return emptyList()

        val comps = LinkedHashMap<String, MetaComp>()

        // 1. Direct JSON root (API / _next/data JSON)
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            extractCompsFromJsonText(trimmed, targetEnv, patchTag, setNumber, defaultSourceUrl)
                .forEach { mergeComp(comps, it) }
            if (comps.isNotEmpty()) return comps.values.toList()
        }

        // 2. Next.js Pages Router __NEXT_DATA__
        NEXT_DATA_REGEX.find(rawBody)?.groupValues?.getOrNull(1)?.let { jsonStr ->
            extractCompsFromJsonText(jsonStr, targetEnv, patchTag, setNumber, defaultSourceUrl)
                .forEach { mergeComp(comps, it) }
        }

        // 3. Next.js App Router RSC flight chunks (concatenated in order!)
        val flightStream = extractConcatenatedNextFlightPayload(rawBody)
        if (flightStream.isNotBlank()) {
            extractCompsFromFlightOrText(flightStream, targetEnv, patchTag, setNumber, defaultSourceUrl)
                .forEach { mergeComp(comps, it) }
        }

        // 4. Raw RSC response body (if fetched directly with RSC: 1 header)
        if (comps.isEmpty() && rawBody.contains(":[\"$\"")) {
            extractCompsFromFlightOrText(rawBody, targetEnv, patchTag, setNumber, defaultSourceUrl)
                .forEach { mergeComp(comps, it) }
        }

        // 5. Generic inline <script type="application/json"> or window state assignments
        if (comps.isEmpty()) {
            SCRIPT_JSON_REGEX.findAll(rawBody).forEach { match ->
                val scriptContent = match.groupValues.getOrNull(1)?.trim().orEmpty()
                if (scriptContent.length >= 20) {
                    extractCompsFromJsonText(scriptContent, targetEnv, patchTag, setNumber, defaultSourceUrl)
                        .forEach { mergeComp(comps, it) }
                }
            }
        }
        if (comps.isEmpty()) {
            WINDOW_STATE_REGEX.findAll(rawBody).forEach { match ->
                val startIdx = match.range.last
                val jsonSlice = extractBalancedJson(rawBody, startIdx)
                if (!jsonSlice.isNullOrBlank()) {
                    extractCompsFromJsonText(jsonSlice, targetEnv, patchTag, setNumber, defaultSourceUrl)
                        .forEach { mergeComp(comps, it) }
                }
            }
        }

        // 6. Full-document HTML DOM fallback (cards, links, tables)
        if (comps.isEmpty()) {
            parseHtmlDomCards(rawBody, targetEnv, patchTag, setNumber, defaultSourceUrl)
                .forEach { mergeComp(comps, it) }
        }

        return comps.values.toList()
    }

    /**
     * Extracts individual champion item/comp recommendations from tft.ninja HTML/JSON/RSC payloads.
     */
    fun extractChampionRecommendationsFromDocument(rawBody: String): Map<String, TftNinjaChampionRecommendation> {
        val result = LinkedHashMap<String, TftNinjaChampionRecommendation>()
        val trimmed = rawBody.trim()
        if (trimmed.isBlank()) return result

        val candidateJsonTexts = mutableListOf<String>()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            candidateJsonTexts.add(trimmed)
        }
        NEXT_DATA_REGEX.find(rawBody)?.groupValues?.getOrNull(1)?.let { candidateJsonTexts.add(it) }
        val flight = extractConcatenatedNextFlightPayload(rawBody)
        if (flight.isNotBlank()) candidateJsonTexts.add(flight)
        SCRIPT_JSON_REGEX.findAll(rawBody).forEach { m ->
            val s = m.groupValues.getOrNull(1)?.trim().orEmpty()
            if (s.length >= 20) candidateJsonTexts.add(s)
        }

        for (text in candidateJsonTexts) {
            if (text.startsWith("{") || text.startsWith("[")) {
                parseJsonRootSafely(text)?.let { root ->
                    collectChampionRecsFromJson(root, result, depth = 0)
                }
            } else {
                // Scan embedded JSON arrays/objects inside flight stream for unit/champion arrays
                for (anchor in CHAMPION_FLIGHT_ANCHOR_REGEX.findAll(text)) {
                    val bracketStart = findEnclosingJsonStart(text, anchor.range.first)
                    if (bracketStart >= 0) {
                        val slice = extractBalancedJson(text, bracketStart)
                        if (!slice.isNullOrBlank()) {
                            parseJsonRootSafely(slice)?.let { root ->
                                collectChampionRecsFromJson(root, result, depth = 0)
                            }
                        }
                    }
                }
            }
        }

        return result
    }

    private fun buildAggregatedChampionRecommendations(
        comps: List<MetaComp>,
        siteChampionRecs: Map<String, TftNinjaChampionRecommendation>
    ): Map<String, TftNinjaChampionRecommendation> {
        val itemFreqByChamp = LinkedHashMap<String, MutableMap<String, Int>>()
        val compTitlesByChamp = LinkedHashMap<String, LinkedHashSet<String>>()
        val displayNameByKey = LinkedHashMap<String, String>()

        siteChampionRecs.forEach { (key, rec) ->
            displayNameByKey[key] = rec.championName
            val itemMap = itemFreqByChamp.getOrPut(key) { LinkedHashMap() }
            // Give site-level champion BIS items high base weight so tft.ninja champion recommendations stay prioritized
            rec.recommendedItems.forEachIndexed { idx, rawItem ->
                val clean = cleanItemLabel(rawItem)
                if (clean.isNotBlank()) {
                    val weight = (10 - idx).coerceAtLeast(3)
                    itemMap[clean] = (itemMap[clean] ?: 0) + weight
                }
            }
            rec.recommendedCompTitles.forEach { title ->
                if (title.isNotBlank()) {
                    compTitlesByChamp.getOrPut(key) { LinkedHashSet() }.add(title)
                }
            }
        }

        comps.forEach { comp ->
            val carryKey = normKey(comp.carryName)
            comp.coreUnits.forEach { unit ->
                val cleanUnit = cleanUnitName(unit)
                val key = normKey(cleanUnit)
                if (key.isNotBlank()) {
                    displayNameByKey.putIfAbsent(key, cleanUnit)
                    compTitlesByChamp.getOrPut(key) { LinkedHashSet() }.add(comp.title)
                }
            }
            comp.hexPlacements.forEach { placement ->
                val cleanUnit = cleanUnitName(placement.unitName)
                val key = normKey(cleanUnit)
                if (key.isBlank()) return@forEach
                displayNameByKey.putIfAbsent(key, cleanUnit)
                compTitlesByChamp.getOrPut(key) { LinkedHashSet() }.add(comp.title)

                val itemMap = itemFreqByChamp.getOrPut(key) { LinkedHashMap() }
                val weightBoost = if (placement.isCarry || key == carryKey) 3 else 2
                placement.items.forEach { rawItem ->
                    val clean = cleanItemLabel(rawItem)
                    if (clean.isNotBlank()) {
                        itemMap[clean] = (itemMap[clean] ?: 0) + weightBoost
                    }
                }
            }
        }

        val merged = LinkedHashMap<String, TftNinjaChampionRecommendation>()
        val allKeys = LinkedHashSet<String>().apply {
            addAll(siteChampionRecs.keys)
            addAll(displayNameByKey.keys)
        }
        for (key in allKeys) {
            val baseSite = siteChampionRecs[key]
            val name = baseSite?.championName ?: displayNameByKey[key] ?: continue
            val rankedItems = itemFreqByChamp[key]?.entries
                ?.sortedByDescending { it.value }
                ?.map { it.key }
                ?.distinct()
                ?.take(6)
                .orEmpty()
            val compTitles = compTitlesByChamp[key]?.toList().orEmpty()
            merged[key] = TftNinjaChampionRecommendation(
                championName = name,
                recommendedItems = rankedItems,
                recommendedCompTitles = compTitles,
                avgPlacement = baseSite?.avgPlacement,
                top4Rate = baseSite?.top4Rate,
                tier = baseSite?.tier
            )
        }
        return merged
    }

    private fun enrichCompsIfNeeded(
        comps: List<MetaComp>,
        targetEnv: TftEnvironment,
        patchTag: String,
        setNumber: Int,
        defaultSourceUrl: String
    ): List<MetaComp> {
        if (comps.isEmpty()) return emptyList()
        var enrichBudget = 12
        return comps.map { comp ->
            val hasDetailUrl = comp.sourceUrl.contains("tft.ninja/comps/", ignoreCase = true) &&
                !comp.sourceUrl.equals(defaultSourceUrl, ignoreCase = true)
            val missingDetails = comp.hexPlacements.none { it.items.isNotEmpty() } ||
                comp.augmentPriorities.isEmpty() ||
                comp.levelingGuide.length < 24
            if (!hasDetailUrl || !missingDetails || enrichBudget <= 0) {
                comp
            } else {
                enrichBudget--
                val detailBody = runCatching { urlFetcher(comp.sourceUrl) }.getOrNull()
                if (detailBody.isNullOrBlank()) {
                    comp
                } else {
                    val detailParsed = parseCompsDocument(
                        rawBody = detailBody,
                        targetEnv = targetEnv,
                        patchTag = patchTag,
                        setNumber = setNumber,
                        defaultSourceUrl = comp.sourceUrl
                    ).firstOrNull {
                        normKey(it.title) == normKey(comp.title) || it.hexPlacements.isNotEmpty()
                    }
                    if (detailParsed != null) mergeCompDetail(comp, detailParsed) else comp
                }
            }
        }
    }

    private fun mergeCompDetail(base: MetaComp, detail: MetaComp): MetaComp {
        val mergedPlacements = if (detail.hexPlacements.sumOf { it.items.size } > base.hexPlacements.sumOf { it.items.size } ||
            detail.hexPlacements.size > base.hexPlacements.size
        ) {
            detail.hexPlacements
        } else {
            base.hexPlacements
        }
        val mergedUnits = if (detail.coreUnits.size > base.coreUnits.size) detail.coreUnits else base.coreUnits
        val mergedTraits = if (detail.traits.size > base.traits.size) detail.traits else base.traits
        val mergedAugments = if (detail.augmentPriorities.size > base.augmentPriorities.size) {
            detail.augmentPriorities
        } else {
            base.augmentPriorities
        }
        val mergedGuide = if (detail.levelingGuide.length > base.levelingGuide.length) {
            detail.levelingGuide
        } else {
            base.levelingGuide
        }
        val mergedNotes = if (detail.notes.length > base.notes.length) detail.notes else base.notes
        val mergedCode = base.teamPlannerCode.ifBlank { detail.teamPlannerCode }
        val mergedStages = if (detail.levelGuideStages.size > base.levelGuideStages.size) {
            detail.levelGuideStages
        } else {
            base.levelGuideStages
        }

        return base.copy(
            coreUnits = mergedUnits,
            traits = mergedTraits,
            hexPlacements = mergedPlacements,
            augmentPriorities = mergedAugments,
            levelingGuide = mergedGuide,
            notes = mergedNotes,
            teamPlannerCode = mergedCode,
            levelGuideStages = mergedStages
        )
    }

    private fun extractConcatenatedNextFlightPayload(html: String): String {
        val sb = StringBuilder()
        NEXT_FLIGHT_CHUNK_REGEX.findAll(html).forEach { match ->
            val rawJsString = match.groupValues.getOrNull(1).orEmpty()
            if (rawJsString.isNotBlank()) {
                sb.append(unescapeJsString(rawJsString))
            }
        }
        return sb.toString()
    }

    private fun extractCompsFromFlightOrText(
        stream: String,
        targetEnv: TftEnvironment,
        patchTag: String,
        setNumber: Int,
        defaultSourceUrl: String
    ): List<MetaComp> {
        val comps = LinkedHashMap<String, MetaComp>()
        val visitedStarts = HashSet<Int>()

        // 1. Scan for key anchors like "comps":[...], "clusters":[...], "compositions":[...], "tierlist":[...]
        for (match in COMP_ARRAY_KEY_REGEX.findAll(stream)) {
            val valueStart = match.range.last
            val jsonStart = findNextJsonOpen(stream, valueStart)
            if (jsonStart >= 0 && visitedStarts.add(jsonStart)) {
                val slice = extractBalancedJson(stream, jsonStart)
                if (!slice.isNullOrBlank()) {
                    extractCompsFromJsonText(slice, targetEnv, patchTag, setNumber, defaultSourceUrl)
                        .forEach { mergeComp(comps, it) }
                }
            }
        }

        // 2. Scan RSC line roots (`1a:[...]` or `1a:{...}`) if still empty
        if (comps.isEmpty()) {
            for (match in RSC_LINE_JSON_REGEX.findAll(stream)) {
                val jsonStart = match.range.last
                if (visitedStarts.add(jsonStart)) {
                    val slice = extractBalancedJson(stream, jsonStart)
                    if (!slice.isNullOrBlank() && slice.length >= 60) {
                        extractCompsFromJsonText(slice, targetEnv, patchTag, setNumber, defaultSourceUrl)
                            .forEach { mergeComp(comps, it) }
                    }
                }
            }
        }

        // 3. Scan around comp-characteristic fields (`avgPlacement`/`avp` + `units`/`champions`)
        if (comps.isEmpty()) {
            for (match in COMP_OBJECT_ANCHOR_REGEX.findAll(stream)) {
                val containerStart = findEnclosingJsonStart(stream, match.range.first)
                if (containerStart >= 0 && visitedStarts.add(containerStart)) {
                    val slice = extractBalancedJson(stream, containerStart)
                    if (!slice.isNullOrBlank() && slice.length >= 50) {
                        extractCompsFromJsonText(slice, targetEnv, patchTag, setNumber, defaultSourceUrl)
                            .forEach { mergeComp(comps, it) }
                    }
                }
            }
        }

        return comps.values.toList()
    }

    private fun extractCompsFromJsonText(
        jsonText: String,
        targetEnv: TftEnvironment,
        patchTag: String,
        setNumber: Int,
        defaultSourceUrl: String
    ): List<MetaComp> {
        val root = parseJsonRootSafely(jsonText) ?: return emptyList()
        val out = LinkedHashMap<String, MetaComp>()
        walkJsonForComps(
            node = root,
            targetEnv = targetEnv,
            patchTag = patchTag,
            setNumber = setNumber,
            defaultSourceUrl = defaultSourceUrl,
            inheritedTier = null,
            out = out,
            depth = 0
        )
        return out.values.toList()
    }

    private fun walkJsonForComps(
        node: Any?,
        targetEnv: TftEnvironment,
        patchTag: String,
        setNumber: Int,
        defaultSourceUrl: String,
        inheritedTier: TierRank?,
        out: LinkedHashMap<String, MetaComp>,
        depth: Int
    ) {
        if (node == null || depth > 12) return

        when (node) {
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    val elem = node.opt(i) ?: continue
                    if (elem is JSONObject) {
                        val parsed = parseCompJsonObject(
                            obj = elem,
                            targetEnv = targetEnv,
                            patchTag = patchTag,
                            setNumber = setNumber,
                            defaultSourceUrl = defaultSourceUrl,
                            inheritedTier = inheritedTier
                        )
                        if (parsed != null) {
                            mergeComp(out, parsed)
                            continue
                        }
                    }
                    walkJsonForComps(elem, targetEnv, patchTag, setNumber, defaultSourceUrl, inheritedTier, out, depth + 1)
                }
            }
            is JSONObject -> {
                val direct = parseCompJsonObject(
                    obj = node,
                    targetEnv = targetEnv,
                    patchTag = patchTag,
                    setNumber = setNumber,
                    defaultSourceUrl = defaultSourceUrl,
                    inheritedTier = inheritedTier
                )
                if (direct != null) {
                    mergeComp(out, direct)
                    return
                }

                val keys = node.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val child = node.opt(key) ?: continue
                    val tierHint = parseTierKeyOrNull(key) ?: inheritedTier
                    walkJsonForComps(child, targetEnv, patchTag, setNumber, defaultSourceUrl, tierHint, out, depth + 1)
                }
            }
        }
    }

    private fun parseCompJsonObject(
        obj: JSONObject,
        targetEnv: TftEnvironment,
        patchTag: String,
        setNumber: Int,
        defaultSourceUrl: String,
        inheritedTier: TierRank?
    ): MetaComp? {
        if (obj.optBoolean("isArchived", false) || obj.optBoolean("hidden", false)) return null
        if (obj.has("isPublic") && !obj.optBoolean("isPublic", true)) return null

        // Extract units & hex placements from any supported key
        val parsedUnits = extractUnitsAndPlacements(obj, targetEnv)
        if (parsedUnits.size < 3) return null

        val rawTitle = firstNonBlankString(
            obj,
            "title", "name", "compName", "comp_name", "displayName", "label", "clusterName", "heading"
        )
        val slug = firstNonBlankString(obj, "slug", "compSlug", "urlSlug", "id", "clusterId", "cluster_id")

        // Traits
        val traits = extractTraits(obj, parsedUnits, targetEnv)

        // Carry resolution
        val explicitCarry = firstNonBlankString(
            obj,
            "carry", "carryName", "carry_name", "mainCarry", "primaryCarry", "headliner"
        )?.let { cleanUnitName(it) }.orEmpty()
        val carryUnit = when {
            explicitCarry.isNotBlank() && parsedUnits.any { normKey(it.name) == normKey(explicitCarry) } ->
                parsedUnits.first { normKey(it.name) == normKey(explicitCarry) }
            parsedUnits.any { it.isCarry } ->
                parsedUnits.first { it.isCarry }
            parsedUnits.any { it.items.isNotEmpty() } ->
                parsedUnits.filter { it.items.isNotEmpty() }
                    .sortedWith(compareByDescending<ParsedUnit> { it.items.size }.thenByDescending { it.stars }.thenByDescending { it.cost })
                    .first()
            else ->
                parsedUnits.maxWithOrNull(compareBy<ParsedUnit> { it.stars }.thenBy { it.cost })
        }
        val carryName = carryUnit?.name ?: explicitCarry.ifBlank { parsedUnits.first().name }

        // Build clean title if site only gives cluster/units/traits
        val title = rawTitle?.let { cleanCompTitle(it) }
            ?.takeIf { it.length >= 3 && !it.all { ch -> ch.isDigit() } }
            ?: synthesizeCompTitle(traits, carryName)

        // Stats (flat or nested inside `stats`/`metrics`/`performance`)
        val statsObj = obj.optJSONObject("stats")
            ?: obj.optJSONObject("metrics")
            ?: obj.optJSONObject("performance")
            ?: obj.optJSONObject("summary")
        val rawAvg = firstDouble(obj, statsObj, "avgPlacement", "avg_placement", "avg", "avp", "averagePlacement", "meanPlacement", "placement")
        val rawTop4 = firstDouble(obj, statsObj, "top4Rate", "top4_rate", "top4", "top_4", "topFour", "topFourRate")
        val rawWin = firstDouble(obj, statsObj, "winRate", "win_rate", "win", "firstRate", "top1", "top1Rate")
        val rawPick = firstDouble(obj, statsObj, "pickRate", "pick_rate", "playRate", "play_rate", "pick", "frequency")

        val explicitTier = firstNonBlankString(obj, "tier", "rank", "grade", "tierRank", "tier_rank")
            ?.let { parseTierValue(it) }
            ?: inheritedTier

        val avgPlacement = rawAvg?.coerceIn(1.5, 7.5)?.let { round2(it) }
            ?: when (explicitTier) {
                TierRank.S -> 3.82
                TierRank.A -> 4.18
                TierRank.B -> 4.48
                TierRank.C -> 4.78
                else -> 4.20
            }

        val tier = explicitTier ?: when {
            avgPlacement <= 4.05 -> TierRank.S
            avgPlacement <= 4.35 -> TierRank.A
            avgPlacement <= 4.65 -> TierRank.B
            else -> TierRank.C
        }

        val top4Rate = normalizePercent(rawTop4) ?: round1(((100.0 - (avgPlacement - 1.5) * 18.0).coerceIn(38.0, 76.0)))
        val winRate = normalizePercent(rawWin) ?: round1(((26.0 - (avgPlacement - 3.0) * 7.5).coerceIn(6.0, 32.0)))
        val pickRate = normalizePercent(rawPick) ?: 4.2

        // Assign clean 4x7 hex placements
        val hexPlacements = buildHexUnitPlacements(parsedUnits, carryName)
        val coreUnits = parsedUnits.map { it.name }.distinct()

        // Playstyle / tempo
        val playstyle = firstNonBlankString(
            obj,
            "playstyle", "playStyle", "style", "tempo", "rollStyle", "levelingStyle", "difficulty"
        )?.let { cleanPlaystyle(it) }
            ?: inferPlaystyle(parsedUnits, carryUnit)

        // Augments
        val augments = extractStringList(
            obj,
            "augments", "augmentPriorities", "recommendedAugments", "bestAugments", "topAugments"
        ).map { formatRiotAssetLabel(it).ifBlank { it } }
            .filter { it.isNotBlank() }
            .distinct()
            .take(6)

        // Leveling guide, structured transition stages & strategy notes
        val levelingGuide = buildLevelingGuide(obj, playstyle, setNumber, patchTag)
        val levelGuideStages = extractLevelGuideStages(obj, parsedUnits, carryName, playstyle)
        val notes = buildCompNotes(obj, parsedUnits, statsObj)

        // Optional direct v02 team planner code embedded by tft.ninja
        val teamPlannerCode = findEmbeddedTeamPlannerCode(obj)

        // Preserve tft.ninja source URL (or dedicated comp detail URL on tft.ninja)
        val sourceUrl = resolveNinjaCompSourceUrl(
            explicitUrl = firstNonBlankString(obj, "url", "href", "link", "sourceUrl", "path"),
            slug = slug,
            defaultSourceUrl = defaultSourceUrl,
            targetEnv = targetEnv
        )

        val compSlug = (slug ?: title)
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .take(48)
            .ifBlank { "comp_${coreUnits.first().lowercase(Locale.US)}" }

        val envPrefix = targetEnv.name.lowercase(Locale.US)
        val setLabel = "Set $setNumber"

        return MetaComp(
            id = "tftninja_${envPrefix}_$compSlug",
            title = title,
            tier = tier,
            environment = targetEnv,
            setName = setLabel,
            patchTag = patchTag,
            playstyle = playstyle,
            avgPlacement = avgPlacement,
            top4Rate = top4Rate,
            winRate = winRate,
            pickRate = pickRate,
            traits = traits,
            coreUnits = coreUnits,
            carryName = carryName,
            augmentPriorities = augments,
            levelingGuide = levelingGuide,
            notes = notes,
            hexPlacements = hexPlacements,
            sourceUrl = sourceUrl,
            teamPlannerCode = teamPlannerCode,
            isTrending = tier == TierRank.S || avgPlacement <= 4.05,
            trendDelta = if (tier == TierRank.S) "Top Meta" else "",
            levelGuideStages = levelGuideStages
        )
    }

    private data class ParsedUnit(
        val name: String,
        val cost: Int,
        val stars: Int,
        val isCarry: Boolean,
        val row: Int?,
        val col: Int?,
        val items: List<String>,
        val otherItems: List<String> = emptyList()
    )

    private fun extractUnitsAndPlacements(
        obj: JSONObject,
        targetEnv: TftEnvironment
    ): List<ParsedUnit> {
        val byKey = LinkedHashMap<String, ParsedUnit>()

        // Optional top-level item maps by unit name: { "unitItems": { "Ahri": [...] } } and alt/other item maps
        val extraItemsByUnit = LinkedHashMap<String, MutableList<String>>()
        val extraOtherItemsByUnit = LinkedHashMap<String, MutableList<String>>()
        listOf("unitItems", "itemMap", "itemsByUnit", "championItems", "builds").forEach { mapKey ->
            val mapObj = obj.optJSONObject(mapKey)
            if (mapObj != null) {
                val keys = mapObj.keys()
                while (keys.hasNext()) {
                    val uKey = keys.next()
                    val uNorm = normKey(cleanUnitName(uKey))
                    val rawVal = mapObj.opt(uKey)
                    val parsedItems = parseItemListValue(rawVal)
                    if (uNorm.isNotBlank() && parsedItems.isNotEmpty()) {
                        extraItemsByUnit.getOrPut(uNorm) { mutableListOf() }.addAll(parsedItems)
                    }
                }
            }
        }
        listOf("otherItems", "other_items", "altItems", "alt_items", "alternativeItems", "flexItems", "backupItems").forEach { mapKey ->
            val mapObj = obj.optJSONObject(mapKey)
            if (mapObj != null) {
                val keys = mapObj.keys()
                while (keys.hasNext()) {
                    val uKey = keys.next()
                    val uNorm = normKey(cleanUnitName(uKey))
                    val parsedItems = parseItemListValue(mapObj.opt(uKey))
                    if (uNorm.isNotBlank() && parsedItems.isNotEmpty()) {
                        extraOtherItemsByUnit.getOrPut(uNorm) { mutableListOf() }.addAll(parsedItems)
                    }
                }
            }
        }
        val holderUnits = mutableListOf<ParsedUnit>()
        listOf("itemHolders", "carries", "carryBuilds", "builds").forEach { arrKey ->
            val arr = obj.optJSONArray(arrKey) ?: return@forEach
            for (i in 0 until arr.length()) {
                val holder = arr.optJSONObject(i) ?: continue
                val uName = firstNonBlankString(holder, "champion", "unit", "unitName", "name", "character_id", "apiName", "id")
                    ?.let { cleanUnitName(it) } ?: continue
                val uNorm = normKey(uName)
                val (primaryItems, altItems) = extractUnitPrimaryAndOtherItems(holder)
                if (uNorm.isNotBlank() && primaryItems.isNotEmpty()) {
                    extraItemsByUnit.getOrPut(uNorm) { mutableListOf() }.addAll(primaryItems)
                }
                if (uNorm.isNotBlank() && altItems.isNotEmpty()) {
                    extraOtherItemsByUnit.getOrPut(uNorm) { mutableListOf() }.addAll(altItems)
                }
                parseSingleUnit(holder, targetEnv, extraItemsByUnit, extraOtherItemsByUnit)?.let { parsedHolder ->
                    holderUnits.add(parsedHolder)
                }
            }
        }

        // Primary & flex/late unit arrays — preserve ALL champions listed on the website without truncation
        val unitArrayKeys = listOf(
            "hexPlacements", "board", "finalBoard", "finalComp", "units", "champions",
            "coreUnits", "core_units", "roster", "hexes", "positions", "team", "members",
            "flexUnits", "flex_units", "flex", "optionalUnits", "extraUnits", "subUnits",
            "lateUnits", "level9Units", "level10Units", "additionalUnits", "allUnits"
        )
        for (key in unitArrayKeys) {
            val raw = obj.opt(key) ?: continue
            when (raw) {
                is JSONArray -> {
                    for (i in 0 until raw.length()) {
                        val item = raw.opt(i) ?: continue
                        parseSingleUnit(item, targetEnv, extraItemsByUnit, extraOtherItemsByUnit)?.let { u ->
                            mergeParsedUnit(byKey, u)
                        }
                    }
                }
                is JSONObject -> {
                    // Support nested board `{ units: [...] }` or map of hexIndex -> unitObj
                    val nestedArr = raw.optJSONArray("units")
                        ?: raw.optJSONArray("champions")
                        ?: raw.optJSONArray("hexes")
                        ?: raw.optJSONArray("board")
                    if (nestedArr != null) {
                        for (i in 0 until nestedArr.length()) {
                            parseSingleUnit(nestedArr.opt(i), targetEnv, extraItemsByUnit, extraOtherItemsByUnit)?.let { u ->
                                mergeParsedUnit(byKey, u)
                            }
                        }
                    } else {
                        val keys = raw.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            val v = raw.opt(k)
                            val indexFromKey = k.toIntOrNull()
                            parseSingleUnit(
                                v,
                                targetEnv,
                                extraItemsByUnit,
                                extraOtherItemsByUnit,
                                fallbackBoardIndex = indexFromKey
                            )?.let { u ->
                                mergeParsedUnit(byKey, u)
                            }
                        }
                    }
                }
            }
        }

        // Ensure any unit present in carries/itemHolders/builds is also included in the comp roster
        holderUnits.forEach { mergeParsedUnit(byKey, it) }

        // Attach any remaining extra items & overflow alternative items to matching units
        extraItemsByUnit.forEach { (uNorm, items) ->
            val existing = byKey[uNorm]
            if (existing != null) {
                val allCombined = (existing.items + items).distinct()
                val primary = allCombined.take(3)
                val overflow = allCombined.drop(3)
                val primaryKeys = primary.map { normKey(it) }.toSet()
                val others = (existing.otherItems + overflow)
                    .filter { normKey(it) !in primaryKeys }
                    .distinct()
                    .take(5)
                byKey[uNorm] = existing.copy(items = primary, otherItems = others)
            }
        }
        extraOtherItemsByUnit.forEach { (uNorm, otherList) ->
            val existing = byKey[uNorm]
            if (existing != null) {
                val primaryKeys = existing.items.map { normKey(it) }.toSet()
                val mergedOthers = (existing.otherItems + otherList)
                    .filter { normKey(it) !in primaryKeys }
                    .distinct()
                    .take(5)
                byKey[uNorm] = existing.copy(otherItems = mergedOthers)
            }
        }

        return byKey.values.toList()
    }

    private fun parseSingleUnit(
        raw: Any?,
        targetEnv: TftEnvironment,
        extraItemsByUnit: Map<String, List<String>>,
        extraOtherItemsByUnit: Map<String, List<String>> = emptyMap(),
        fallbackBoardIndex: Int? = null
    ): ParsedUnit? {
        if (raw == null) return null
        return when (raw) {
            is String -> {
                if (TftAssetIconResolver.isKnownNonChampionAsset(raw, targetEnv)) return null
                val name = cleanUnitName(raw)
                if (!isPlausibleUnitName(name, targetEnv)) return null
                val cost = TftAssetIconResolver.getChampionCost(name, targetEnv) ?: 3
                val allExtra = extraItemsByUnit[normKey(name)].orEmpty().distinct()
                val primary = allExtra.take(3)
                val primaryKeys = primary.map { normKey(it) }.toSet()
                val others = (allExtra.drop(3) + extraOtherItemsByUnit[normKey(name)].orEmpty())
                    .filter { normKey(it) !in primaryKeys }
                    .distinct()
                    .take(5)
                ParsedUnit(
                    name = name,
                    cost = cost,
                    stars = 2,
                    isCarry = false,
                    row = fallbackBoardIndex?.let { (it / 7).coerceIn(0, 3) },
                    col = fallbackBoardIndex?.let { (it % 7).coerceIn(0, 6) },
                    items = primary,
                    otherItems = others
                )
            }
            is JSONObject -> {
                val rawName = firstNonBlankString(
                    raw,
                    "unitName", "name", "champion", "championName", "unit",
                    "character_id", "characterId", "apiName", "api_name", "id", "slug"
                ) ?: return null
                if (TftAssetIconResolver.isKnownNonChampionAsset(rawName, targetEnv)) return null
                val name = cleanUnitName(rawName)
                if (!isPlausibleUnitName(name, targetEnv)) return null

                val cost = firstInt(raw, "cost", "gold", "rarity", "price")
                    ?.coerceIn(1, 5)
                    ?: TftAssetIconResolver.getChampionCost(name, targetEnv)
                    ?: 3

                val threeStar = raw.optBoolean("threeStar", false) ||
                    raw.optBoolean("isThreeStar", false) ||
                    raw.optBoolean("tier3", false) ||
                    raw.optBoolean("shouldThreeStar", false)
                val stars = firstInt(raw, "stars", "star", "starLevel", "tier", "level")
                    ?.takeIf { it in 1..3 }
                    ?: if (threeStar) 3 else 2

                val isCarry = raw.optBoolean("isCarry", false) ||
                    raw.optBoolean("carry", false) ||
                    raw.optBoolean("isMainCarry", false) ||
                    raw.optBoolean("mainCarry", false) ||
                    raw.optString("role").contains("carry", ignoreCase = true)

                val boardIdx = firstInt(raw, "hex", "hexIndex", "boardIndex", "cell", "slot", "index", "pos")
                    ?: fallbackBoardIndex
                val posObj = raw.optJSONObject("position") ?: raw.optJSONObject("coords")
                val rawRow = firstInt(raw, "row", "y") ?: posObj?.let { firstInt(it, "row", "y") }
                val rawCol = firstInt(raw, "col", "column", "x") ?: posObj?.let { firstInt(it, "col", "column", "x") }

                val row = rawRow?.coerceIn(0, 3) ?: boardIdx?.takeIf { it in 0..27 }?.let { it / 7 }
                val col = rawCol?.coerceIn(0, 6) ?: boardIdx?.takeIf { it in 0..27 }?.let { it % 7 }

                val (rawPrimary, rawOther) = extractUnitPrimaryAndOtherItems(raw)
                val allPrimary = (rawPrimary + extraItemsByUnit[normKey(name)].orEmpty()).distinct()
                val unitItems = allPrimary.take(3)
                val primaryKeys = unitItems.map { normKey(it) }.toSet()
                val unitOtherItems = (rawOther + allPrimary.drop(3) + extraOtherItemsByUnit[normKey(name)].orEmpty())
                    .filter { normKey(it) !in primaryKeys }
                    .distinct()
                    .take(5)

                ParsedUnit(
                    name = name,
                    cost = cost,
                    stars = stars,
                    isCarry = isCarry,
                    row = row,
                    col = col,
                    items = unitItems,
                    otherItems = unitOtherItems
                )
            }
            else -> null
        }
    }

    private fun extractUnitPrimaryAndOtherItems(unitObj: JSONObject): Pair<List<String>, List<String>> {
        val primaryOut = mutableListOf<String>()
        val primaryKeys = listOf(
            "items", "itemNames", "item_names", "bestItems", "best_items",
            "recommendedItems", "recommended_items", "bis", "bisItems",
            "itemIds", "item_ids", "equippedItems", "coreItems", "builds"
        )
        for (k in primaryKeys) {
            if (!unitObj.has(k)) continue
            primaryOut.addAll(parseItemListValue(unitObj.opt(k)))
        }
        val distinctPrimary = primaryOut.filter { it.isNotBlank() }.distinct()
        val primaryTop3 = distinctPrimary.take(3)
        val overflowOthers = distinctPrimary.drop(3)

        val explicitOtherOut = mutableListOf<String>()
        val otherKeys = listOf(
            "otherItems", "other_items", "altItems", "alt_items",
            "alternativeItems", "alternative_items", "flexItems", "flex_items",
            "backupItems", "backup_items", "secondaryItems", "situationalItems",
            "viableItems", "itemPool", "goodItems", "substitutes"
        )
        for (k in otherKeys) {
            if (!unitObj.has(k)) continue
            explicitOtherOut.addAll(parseItemListValue(unitObj.opt(k)))
        }

        val top3Keys = primaryTop3.map { normKey(it) }.toSet()
        val cleanOthers = (explicitOtherOut + overflowOthers)
            .filter { it.isNotBlank() && normKey(it) !in top3Keys }
            .distinct()
            .take(5)
        return primaryTop3 to cleanOthers
    }

    private fun extractUnitItems(unitObj: JSONObject): List<String> {
        val (primary, others) = extractUnitPrimaryAndOtherItems(unitObj)
        return (primary + others).distinct().take(6)
    }

    private fun parseItemListValue(raw: Any?): List<String> {
        if (raw == null) return emptyList()
        val items = mutableListOf<String>()
        when (raw) {
            is JSONArray -> {
                for (i in 0 until raw.length()) {
                    val elem = raw.opt(i) ?: continue
                    when (elem) {
                        is String -> cleanItemLabel(elem).takeIf { it.isNotBlank() }?.let { items.add(it) }
                        is JSONObject -> {
                            val label = firstNonBlankString(elem, "name", "itemName", "label", "apiName", "id", "item")
                            val iconCandidate = firstNonBlankString(elem, "icon", "image", "imageUrl", "img", "src", "iconUrl")
                            if (!label.isNullOrBlank()) {
                                val cleaned = cleanItemLabel(label)
                                if (cleaned.isNotBlank()) {
                                    items.add(cleaned)
                                    val resolvedIcon = resolveScrapedImageUrl(iconCandidate.orEmpty())
                                    if (!resolvedIcon.isNullOrBlank()) {
                                        TftAssetIconResolver.registerScrapedItemIcon(cleaned, resolvedIcon)
                                        if (label != cleaned) {
                                            TftAssetIconResolver.registerScrapedItemIcon(label, resolvedIcon)
                                        }
                                    }
                                }
                            } else {
                                // Nested build object: { items: [...] }
                                elem.optJSONArray("items")?.let { nested ->
                                    items.addAll(parseItemListValue(nested))
                                }
                            }
                        }
                        is JSONArray -> items.addAll(parseItemListValue(elem))
                    }
                }
            }
            is String -> {
                raw.split(",", "|").forEach { token ->
                    cleanItemLabel(token).takeIf { it.isNotBlank() }?.let { items.add(it) }
                }
            }
        }
        return items.distinct()
    }

    private fun mergeParsedUnit(map: LinkedHashMap<String, ParsedUnit>, incoming: ParsedUnit) {
        val key = normKey(incoming.name)
        val existing = map[key]
        if (existing == null) {
            map[key] = incoming
        } else {
            val combinedPrimary = (existing.items + incoming.items).distinct()
            val top3 = combinedPrimary.take(3)
            val top3Keys = top3.map { normKey(it) }.toSet()
            val mergedOthers = (existing.otherItems + incoming.otherItems + combinedPrimary.drop(3))
                .filter { normKey(it) !in top3Keys }
                .distinct()
                .take(5)
            map[key] = existing.copy(
                cost = if (existing.cost in 1..5) existing.cost else incoming.cost,
                stars = maxOf(existing.stars, incoming.stars),
                isCarry = existing.isCarry || incoming.isCarry,
                row = existing.row ?: incoming.row,
                col = existing.col ?: incoming.col,
                items = top3,
                otherItems = mergedOthers
            )
        }
    }

    private fun buildHexUnitPlacements(
        units: List<ParsedUnit>,
        carryName: String
    ): List<HexUnitPlacement> {
        val carryKey = normKey(carryName)
        val occupied = HashSet<Pair<Int, Int>>()
        val resolved = mutableListOf<HexUnitPlacement>()

        // Place units with explicit coordinates first
        val unplaced = mutableListOf<ParsedUnit>()
        units.forEach { u ->
            val isCarry = u.isCarry || normKey(u.name) == carryKey
            if (u.row != null && u.col != null && occupied.add(u.row to u.col)) {
                resolved.add(
                    HexUnitPlacement(
                        unitName = u.name,
                        row = u.row,
                        col = u.col,
                        cost = u.cost,
                        stars = u.stars,
                        isCarry = isCarry,
                        items = u.items,
                        otherItems = u.otherItems
                    )
                )
            } else {
                unplaced.add(u)
            }
        }

        // Auto-place remaining units cleanly
        val backlineSlots = listOf(3 to 0, 3 to 6, 3 to 1, 3 to 5, 3 to 2, 3 to 4, 3 to 3, 2 to 0, 2 to 6, 2 to 3)
        val frontlineSlots = listOf(0 to 3, 0 to 2, 0 to 4, 0 to 1, 0 to 5, 0 to 0, 0 to 6, 1 to 2, 1 to 4, 1 to 3)

        unplaced.forEach { u ->
            val isCarry = u.isCarry || normKey(u.name) == carryKey
            val prefersBackline = isCarry || u.items.any { item ->
                val l = item.lowercase(Locale.US)
                l.contains("guinsoo") || l.contains("shojin") || l.contains("blue buff") ||
                    l.contains("infinity edge") || l.contains("jeweled") || l.contains("rabadon") ||
                    l.contains("last whisper") || l.contains("nashor")
            }
            val preferred = if (prefersBackline) backlineSlots + frontlineSlots else frontlineSlots + backlineSlots
            val slot = preferred.firstOrNull { occupied.add(it) } ?: (0 to 0)
            resolved.add(
                HexUnitPlacement(
                    unitName = u.name,
                    row = slot.first,
                    col = slot.second,
                    cost = u.cost,
                    stars = u.stars,
                    isCarry = isCarry,
                    items = u.items,
                    otherItems = u.otherItems
                )
            )
        }

        return resolved
    }

    private fun extractTraits(
        obj: JSONObject,
        units: List<ParsedUnit>,
        targetEnv: TftEnvironment
    ): List<String> {
        val scored = mutableListOf<Pair<Int, String>>()
        val traitKeys = listOf("traits", "synergies", "activeTraits", "active_traits", "traitSummary")
        for (k in traitKeys) {
            val raw = obj.opt(k) ?: continue
            when (raw) {
                is JSONArray -> {
                    for (i in 0 until raw.length()) {
                        val elem = raw.opt(i) ?: continue
                        when (elem) {
                            is String -> {
                                val cleaned = cleanTraitLabel(elem)
                                if (cleaned.isNotBlank()) {
                                    val count = Regex("""^(\d+)\s+""").find(cleaned)?.groupValues?.get(1)?.toIntOrNull() ?: 2
                                    scored.add(count to cleaned)
                                }
                            }
                            is JSONObject -> {
                                val rawName = firstNonBlankString(elem, "name", "trait", "traitName", "apiName", "id") ?: continue
                                val name = formatRiotAssetLabel(rawName).ifBlank { rawName.trim() }
                                val count = firstInt(elem, "num_units", "numUnits", "count", "units", "tier_current", "value") ?: 0
                                val label = if (count > 0 && !name.first().isDigit()) "$count $name" else name
                                if (label.isNotBlank()) scored.add(count to label)
                            }
                        }
                    }
                }
                is JSONObject -> {
                    val keys = raw.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val count = raw.optInt(key, 0)
                        val name = formatRiotAssetLabel(key).ifBlank { key.trim() }
                        if (name.isNotBlank()) {
                            val label = if (count > 0) "$count $name" else name
                            scored.add(count to label)
                        }
                    }
                }
            }
            if (scored.isNotEmpty()) break
        }

        if (scored.isEmpty()) {
            // Derive active traits directly from unit trait metadata
            val counts = LinkedHashMap<String, Int>()
            units.forEach { u ->
                TftAssetIconResolver.getChampionTraits(u.name, targetEnv).forEach { tr ->
                    counts[tr] = (counts[tr] ?: 0) + 1
                }
            }
            counts.entries
                .filter { it.value >= 2 }
                .sortedByDescending { it.value }
                .take(6)
                .forEach { (tr, c) -> scored.add(c to "$c $tr") }
        }

        return scored
            .sortedByDescending { it.first }
            .map { it.second }
            .distinctBy { normKey(it.replace(Regex("""^\d+\s+"""), "")) }
            .take(6)
    }

    private fun buildLevelingGuide(
        obj: JSONObject,
        playstyle: String,
        setNumber: Int,
        patchTag: String
    ): String {
        val parts = mutableListOf<String>()
        listOf("levelingGuide", "leveling_guide", "leveling", "gameplan", "strategy", "guide").forEach { k ->
            val v = obj.opt(k)
            when (v) {
                is String -> v.trim().takeIf { it.isNotBlank() }?.let { parts.add(it) }
                is JSONArray -> {
                    val joined = (0 until v.length())
                        .mapNotNull { i ->
                            val elem = v.opt(i)
                            when (elem) {
                                is String -> elem.trim().takeIf { it.isNotBlank() }
                                is JSONObject -> {
                                    val stage = firstNonBlankString(elem, "stage", "level", "title")
                                    val text = firstNonBlankString(elem, "text", "description", "tip", "note")
                                    listOfNotNull(stage, text).joinToString(": ").takeIf { it.isNotBlank() }
                                }
                                else -> null
                            }
                        }
                        .joinToString(" • ")
                    if (joined.isNotBlank()) parts.add(joined)
                }
            }
        }
        val early = firstNonBlankString(obj, "earlyGame", "early_game", "early")
        val mid = firstNonBlankString(obj, "midGame", "mid_game", "mid")
        val late = firstNonBlankString(obj, "lateGame", "late_game", "late")
        listOfNotNull(
            early?.let { "Early: $it" },
            mid?.let { "Mid: $it" },
            late?.let { "Late: $it" }
        ).forEach { parts.add(it) }

        return parts.distinct().joinToString("\n")
            .ifBlank { "$playstyle • Set $setNumber ($patchTag)" }
    }

    private fun extractLevelGuideStages(
        obj: JSONObject,
        parsedUnits: List<ParsedUnit>,
        carryName: String,
        playstyle: String
    ): List<LevelTransitionStage> {
        val stages = mutableListOf<LevelTransitionStage>()

        fun extractUnitNamesFromNode(raw: Any?): List<String> {
            if (raw == null) return emptyList()
            val out = mutableListOf<String>()
            when (raw) {
                is JSONArray -> {
                    for (i in 0 until raw.length()) {
                        when (val elem = raw.opt(i)) {
                            is String -> {
                                if (!TftAssetIconResolver.isKnownNonChampionAsset(elem)) {
                                    val cleaned = cleanUnitName(elem)
                                    if (isPlausibleUnitName(cleaned)) out.add(cleaned)
                                }
                            }
                            is JSONObject -> {
                                val rawNm = firstNonBlankString(
                                    elem,
                                    "unitName", "name", "champion", "championName", "unit", "character_id", "apiName"
                                )
                                val nm = rawNm?.takeIf { !TftAssetIconResolver.isKnownNonChampionAsset(it) }?.let { cleanUnitName(it) }
                                if (!nm.isNullOrBlank() && isPlausibleUnitName(nm)) out.add(nm)
                            }
                        }
                    }
                }
                is JSONObject -> {
                    val nested = raw.optJSONArray("units")
                        ?: raw.optJSONArray("champions")
                        ?: raw.optJSONArray("board")
                        ?: raw.optJSONArray("hexes")
                    if (nested != null) out.addAll(extractUnitNamesFromNode(nested))
                }
            }
            return out.distinct()
        }

        // 1. Structured stages/boards/levelGuides/transitions arrays from tft.ninja
        val stageArrayKeys = listOf("levelGuideStages", "stages", "boards", "levelGuides", "leveling", "transitions", "phases")
        for (k in stageArrayKeys) {
            val arr = obj.optJSONArray(k) ?: continue
            for (i in 0 until arr.length()) {
                val elem = arr.optJSONObject(i) ?: continue
                val levelVal = firstNonBlankString(elem, "levelLabel", "level", "title", "phase", "name")
                    ?: firstInt(elem, "level", "lvl")?.let { "Level $it" }
                val timingVal = firstNonBlankString(elem, "stageTiming", "stage", "timing", "round", "when")
                val tipVal = firstNonBlankString(elem, "actionTip", "tip", "description", "text", "note", "transition", "guide")
                val stageUnits = extractUnitNamesFromNode(
                    elem.opt("units") ?: elem.opt("champions") ?: elem.opt("board") ?: elem.opt("comp")
                )
                if (!levelVal.isNullOrBlank() || !timingVal.isNullOrBlank() || stageUnits.isNotEmpty() || !tipVal.isNullOrBlank()) {
                    stages.add(
                        LevelTransitionStage(
                            levelLabel = levelVal?.let { if (it.all { ch -> ch.isDigit() }) "Level $it" else it }
                                ?: "Stage ${i + 1}",
                            stageTiming = timingVal.orEmpty(),
                            units = stageUnits,
                            actionTip = tipVal.orEmpty()
                        )
                    )
                }
            }
            if (stages.isNotEmpty()) break
        }

        // 2. Named early / mid / late board or gameplan fields from tft.ninja
        if (stages.isEmpty()) {
            val earlyUnits = extractUnitNamesFromNode(obj.opt("earlyBoard") ?: obj.opt("earlyComp") ?: obj.opt("earlyUnits"))
            val midUnits = extractUnitNamesFromNode(obj.opt("midBoard") ?: obj.opt("midComp") ?: obj.opt("midUnits"))
            val lateUnits = extractUnitNamesFromNode(obj.opt("lateBoard") ?: obj.opt("lateComp") ?: obj.opt("lateUnits"))
            val earlyTip = firstNonBlankString(obj, "earlyGame", "early_game", "earlyTip", "early")
            val midTip = firstNonBlankString(obj, "midGame", "mid_game", "midTip", "mid", "transition", "whenToTransition")
            val lateTip = firstNonBlankString(obj, "lateGame", "late_game", "lateTip", "late", "endGame")

            if (earlyUnits.isNotEmpty() || !earlyTip.isNullOrBlank()) {
                stages.add(
                    LevelTransitionStage(
                        levelLabel = "Level 4–5 (Early)",
                        stageTiming = "Stage 2-1 → 2-6",
                        units = earlyUnits.ifEmpty { parsedUnits.filter { it.cost <= 2 }.map { it.name }.take(5) },
                        actionTip = earlyTip ?: "Play strongest early opener and hold items for $carryName."
                    )
                )
            }
            if (midUnits.isNotEmpty() || !midTip.isNullOrBlank()) {
                stages.add(
                    LevelTransitionStage(
                        levelLabel = "Level 6–7 (Mid & Transition)",
                        stageTiming = "Stage 3-2 → 4-1",
                        units = midUnits.ifEmpty { parsedUnits.filter { it.cost <= 3 }.map { it.name }.take(7) },
                        actionTip = midTip ?: "Stabilize mid-game synergies and prepare transition ($playstyle)."
                    )
                )
            }
            if (lateUnits.isNotEmpty() || !lateTip.isNullOrBlank()) {
                stages.add(
                    LevelTransitionStage(
                        levelLabel = "Level 8–9 (Final Board)",
                        stageTiming = "Stage 4-2+",
                        units = lateUnits.ifEmpty { parsedUnits.map { it.name } },
                        actionTip = lateTip ?: "Complete transition onto $carryName and cap the final board."
                    )
                )
            }
        }

        val nonEmptySets = stages
            .map { stage ->
                stage.units
                    .map { it.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "") }
                    .filter { it.isNotEmpty() }
                    .toSet()
            }
            .filter { it.isNotEmpty() }
        if (nonEmptySets.size >= 2 && nonEmptySets.distinct().size < nonEmptySets.size) {
            return emptyList()
        }

        return stages
    }

    private fun buildCompNotes(
        obj: JSONObject,
        units: List<ParsedUnit>,
        statsObj: JSONObject?
    ): String {
        val parts = mutableListOf<String>()
        listOf("notes", "tips", "description", "summary", "whenToPlay", "when_to_play", "positioning").forEach { k ->
            val v = obj.opt(k)
            when (v) {
                is String -> v.trim().takeIf { it.isNotBlank() }?.let { parts.add(it) }
                is JSONArray -> {
                    val listText = (0 until v.length())
                        .mapNotNull { i -> v.optString(i).trim().takeIf { it.isNotBlank() } }
                        .joinToString(" • ")
                    if (listText.isNotBlank()) parts.add(listText)
                }
            }
        }
        val gamesCount = firstInt(obj, "games", "sampleSize", "sample_size", "matches", "count")
            ?: statsObj?.let { firstInt(it, "games", "sampleSize", "sample_size", "matches", "count") }
        if (gamesCount != null && gamesCount > 0) {
            parts.add("Sample size: ${String.format(Locale.US, "%,d", gamesCount)} games")
        }
        val threeStars = units.filter { it.stars >= 3 }.map { "${it.name} 3★" }
        if (threeStars.isNotEmpty()) {
            parts.add("3★ Priority: ${threeStars.joinToString(", ")}")
        }
        return parts.distinct().joinToString(" • ")
    }

    private fun findEmbeddedTeamPlannerCode(obj: JSONObject): String {
        val candidateKeys = listOf("teamPlannerCode", "team_planner_code", "teamCode", "team_code", "plannerCode", "copyCode", "code")
        for (k in candidateKeys) {
            val raw = obj.optString(k).trim()
            if (isValidV02PlannerCode(raw)) return raw
            TftTeamPlannerCodeEncoder.extractFirstValidV02Code(raw)?.let { return it }
        }
        val nested = obj.optJSONObject("teamPlanner") ?: obj.optJSONObject("planner")
        if (nested != null) {
            for (k in candidateKeys) {
                val raw = nested.optString(k).trim()
                if (isValidV02PlannerCode(raw)) return raw
                TftTeamPlannerCodeEncoder.extractFirstValidV02Code(raw)?.let { return it }
            }
        }
        return ""
    }

    private fun isValidV02PlannerCode(raw: String): Boolean {
        return TftTeamPlannerCodeEncoder.isValidTeamPlannerCode(raw)
    }

    private fun collectChampionRecsFromJson(
        node: Any?,
        out: LinkedHashMap<String, TftNinjaChampionRecommendation>,
        depth: Int
    ) {
        if (node == null || depth > 10) return
        when (node) {
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    collectChampionRecsFromJson(node.opt(i), out, depth + 1)
                }
            }
            is JSONObject -> {
                // Does this object represent a champion stats / recommendation entry?
                val rawName = firstNonBlankString(
                    node,
                    "champion", "championName", "unitName", "character_id", "characterId", "apiName", "name"
                )
                val items = extractUnitItems(node)
                val compsList = extractStringList(node, "comps", "compositions", "recommendedComps", "topComps", "synergyComps")
                val hasUnitMetrics = node.has("avg") || node.has("avp") || node.has("avgPlacement") ||
                    node.has("cost") || node.has("traits")
                // Exclude full comp objects (those have a `units`/`board`/`hexPlacements` array)
                val looksLikeComp = node.optJSONArray("units") != null ||
                    node.optJSONArray("champions") != null ||
                    node.optJSONArray("board") != null ||
                    node.optJSONArray("hexPlacements") != null

                if (!looksLikeComp && !rawName.isNullOrBlank() && (items.isNotEmpty() || (hasUnitMetrics && compsList.isNotEmpty()))) {
                    val cleanName = cleanUnitName(rawName)
                    if (isPlausibleUnitName(cleanName)) {
                        val key = normKey(cleanName)
                        val statsObj = node.optJSONObject("stats")
                        val avg = firstDouble(node, statsObj, "avgPlacement", "avg_placement", "avg", "avp")
                        val top4 = normalizePercent(firstDouble(node, statsObj, "top4Rate", "top4_rate", "top4"))
                        val tier = firstNonBlankString(node, "tier", "rank", "grade")?.let { parseTierValue(it) }
                        val incoming = TftNinjaChampionRecommendation(
                            championName = cleanName,
                            recommendedItems = items,
                            recommendedCompTitles = compsList,
                            avgPlacement = avg?.let { round2(it) },
                            top4Rate = top4,
                            tier = tier
                        )
                        mergeChampRec(out, key, incoming)
                    }
                }

                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val child = node.opt(k)
                    if (child is JSONObject || child is JSONArray) {
                        collectChampionRecsFromJson(child, out, depth + 1)
                    }
                }
            }
        }
    }

    private fun mergeChampRecs(
        target: LinkedHashMap<String, TftNinjaChampionRecommendation>,
        incoming: Map<String, TftNinjaChampionRecommendation>
    ) {
        incoming.forEach { (k, v) -> mergeChampRec(target, k, v) }
    }

    private fun mergeChampRec(
        target: LinkedHashMap<String, TftNinjaChampionRecommendation>,
        key: String,
        incoming: TftNinjaChampionRecommendation
    ) {
        val existing = target[key]
        if (existing == null) {
            target[key] = incoming
        } else {
            target[key] = existing.copy(
                recommendedItems = (existing.recommendedItems + incoming.recommendedItems).distinct().take(6),
                recommendedCompTitles = (existing.recommendedCompTitles + incoming.recommendedCompTitles).distinct().take(8),
                avgPlacement = existing.avgPlacement ?: incoming.avgPlacement,
                top4Rate = existing.top4Rate ?: incoming.top4Rate,
                tier = existing.tier ?: incoming.tier
            )
        }
    }

    private fun parseHtmlDomCards(
        html: String,
        targetEnv: TftEnvironment,
        patchTag: String,
        setNumber: Int,
        defaultSourceUrl: String
    ): List<MetaComp> {
        val results = LinkedHashMap<String, MetaComp>()
        val anchors = DOM_COMP_BLOCK_REGEX.findAll(html).toList()
        if (anchors.isEmpty()) return emptyList()

        for (i in anchors.indices) {
            val start = anchors[i].range.first
            val end = if (i + 1 < anchors.size) {
                minOf(anchors[i + 1].range.first, start + 14_000)
            } else {
                minOf(html.length, start + 14_000)
            }
            val block = html.substring(start, end)
            val href = HREF_COMP_REGEX.find(block)?.let { m ->
                m.groupValues.getOrNull(1)?.ifEmpty { m.groupValues.getOrNull(2).orEmpty() }
            }

            // Extract ONLY real champions into units, attach item images to their owning champion,
            // and collect any augment images into augmentPriorities (never as units).
            val units = LinkedHashSet<String>()
            val itemsByUnit = LinkedHashMap<String, MutableList<String>>()
            val orphanItems = LinkedHashSet<String>()
            val domAugments = LinkedHashSet<String>()
            var lastSeenChampion: String? = null
            IMG_TAG_REGEX.findAll(block).forEach { imgMatch ->
                val tag = imgMatch.value
                val alt = ATTR_ALT_REGEX.find(tag)?.let { m ->
                    m.groupValues.getOrNull(1)?.ifEmpty { m.groupValues.getOrNull(2).orEmpty() }
                }?.trim().orEmpty()
                val rawSrc = ATTR_SRC_REGEX.find(tag)?.let { m ->
                    m.groupValues.getOrNull(1)?.ifEmpty { m.groupValues.getOrNull(2).orEmpty() }
                }?.trim().orEmpty()
                val decodedSrc = decodeImageSrc(rawSrc)
                val lowerSrc = decodedSrc.lowercase(Locale.US)
                val fileNameToken = extractFileNameFromImageSrc(decodedSrc)

                val isAugmentImage = lowerSrc.contains("/augment") ||
                    lowerSrc.contains("tft-augment") ||
                    lowerSrc.contains("tft_augment")

                val isTraitOrUi = lowerSrc.contains("/trait") ||
                    lowerSrc.contains("tft-trait") ||
                    lowerSrc.contains("tft_trait") ||
                    lowerSrc.contains("/synerg") ||
                    lowerSrc.contains("/tier") ||
                    lowerSrc.contains("logo") ||
                    lowerSrc.contains("star")

                val isItemImage = !isAugmentImage && (
                    lowerSrc.contains("/item") ||
                        lowerSrc.contains("tft_item") ||
                        lowerSrc.contains("tft-item") ||
                        lowerSrc.contains("_item_") ||
                        lowerSrc.contains("/artifact") ||
                        lowerSrc.contains("/component") ||
                        lowerSrc.contains("da_component") ||
                        lowerSrc.contains("emblem") ||
                        (alt.isNotBlank() && TftAssetIconResolver.isKnownItem(alt, targetEnv)) ||
                        (fileNameToken.isNotBlank() && TftAssetIconResolver.isKnownItem(fileNameToken, targetEnv))
                    )

                when {
                    isAugmentImage -> {
                        val augRaw = alt.ifBlank { fileNameToken }
                        val augLabel = formatRiotAssetLabel(augRaw).trim()
                        if (augLabel.length in 2..48 && !TftAssetIconResolver.isKnownChampion(augLabel, targetEnv)) {
                            domAugments.add(augLabel)
                        }
                    }
                    isItemImage -> {
                        val itemRaw = alt.ifBlank { fileNameToken }
                        val item = cleanItemLabel(itemRaw)
                        if (item.isNotBlank() && !TftAssetIconResolver.isKnownChampion(item, targetEnv)) {
                            val scrapedIconUrl = resolveScrapedImageUrl(decodedSrc)
                            if (!scrapedIconUrl.isNullOrBlank()) {
                                TftAssetIconResolver.registerScrapedItemIcon(item, scrapedIconUrl)
                                if (fileNameToken.isNotBlank()) {
                                    TftAssetIconResolver.registerScrapedItemIcon(fileNameToken, scrapedIconUrl)
                                }
                            }
                            val owner = lastSeenChampion
                            if (owner != null) {
                                val bucket = itemsByUnit.getOrPut(owner) { mutableListOf() }
                                if (bucket.none { normKey(it) == normKey(item) }) {
                                    bucket.add(item)
                                }
                            } else {
                                orphanItems.add(item)
                            }
                        }
                    }
                    isTraitOrUi -> {
                        // Never treat trait/UI icons as champions
                    }
                    else -> {
                        val rawCandidate = alt.ifBlank { fileNameToken }
                        if (rawCandidate.isNotBlank() && !TftAssetIconResolver.isKnownNonChampionAsset(rawCandidate, targetEnv)) {
                            val candidate = cleanUnitName(rawCandidate)
                            val hasChampionPathHint = lowerSrc.contains("/champ") ||
                                lowerSrc.contains("/unit") ||
                                lowerSrc.contains("/character") ||
                                lowerSrc.contains("tft-champion") ||
                                lowerSrc.contains("/skin/") ||
                                (lowerSrc.contains("tft$setNumber") && !lowerSrc.contains("item"))
                            if ((hasChampionPathHint || TftAssetIconResolver.isKnownChampion(candidate, targetEnv)) &&
                                isPlausibleUnitName(candidate, targetEnv)
                            ) {
                                units.add(candidate)
                                lastSeenChampion = candidate
                            }
                        }
                    }
                }
            }
            if (units.size < 3) continue

            val plainText = block
                .replace(Regex("<script[^>]*>.*?</script>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), " ")
                .replace(Regex("<[^>]+>"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()

            val heading = HEADING_TAG_REGEX.find(block)?.groupValues?.getOrNull(1)
                ?.replace(Regex("<[^>]+>"), "")
                ?.trim()
                ?.takeIf { it.length in 3..60 }

            val avg = Regex("""(?:Avg|AVP)\s*[:#]?\s*(\d\.\d{1,2})""", RegexOption.IGNORE_CASE)
                .find(plainText)?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 4.15
            val top4 = Regex("""Top\s*4\s*[:#]?\s*(\d{1,2}(?:\.\d)?)\s*%""", RegexOption.IGNORE_CASE)
                .find(plainText)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
                ?: round1(((100.0 - (avg - 1.5) * 18.0).coerceIn(40.0, 74.0)))
            val win = Regex("""Win\s*(?:Rate)?\s*[:#]?\s*(\d{1,2}(?:\.\d)?)\s*%""", RegexOption.IGNORE_CASE)
                .find(plainText)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
                ?: round1(((26.0 - (avg - 3.0) * 7.5).coerceIn(6.0, 30.0)))
            val pick = Regex("""(?:Pick|Play)\s*(?:Rate)?\s*[:#]?\s*(\d{1,2}(?:\.\d)?)\s*%""", RegexOption.IGNORE_CASE)
                .find(plainText)?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 4.0

            val explicitTier = Regex("""\b([SABCX]\+?)\s*(?:Tier)?\b""")
                .find(plainText.take(80))?.groupValues?.getOrNull(1)?.let { parseTierValue(it) }
            val tier = explicitTier ?: when {
                avg <= 4.05 -> TierRank.S
                avg <= 4.35 -> TierRank.A
                avg <= 4.65 -> TierRank.B
                else -> TierRank.C
            }

            val unitList = units.toList()
            val primaryCarryName = itemsByUnit.entries
                .maxByOrNull { (uName, list) -> list.size * 10 + (TftAssetIconResolver.getChampionCost(uName, targetEnv) ?: 3) }
                ?.key
                ?: unitList.maxByOrNull { TftAssetIconResolver.getChampionCost(it, targetEnv) ?: 3 }
                ?: unitList.first()

            val parsedUnits = unitList.map { uName ->
                val cost = TftAssetIconResolver.getChampionCost(uName, targetEnv) ?: 3
                val attached = itemsByUnit[uName].orEmpty()
                val fallbackForCarry = if (itemsByUnit.isEmpty() && uName == primaryCarryName) orphanItems.toList() else emptyList()
                val allItems = (attached + fallbackForCarry).distinct()
                val primaryItems = allItems.take(3)
                val overflowOthers = allItems.drop(3).take(5)
                val isUnitCarry = uName == primaryCarryName || primaryItems.isNotEmpty()
                ParsedUnit(
                    name = uName,
                    cost = cost,
                    stars = 2,
                    isCarry = isUnitCarry,
                    row = null,
                    col = null,
                    items = primaryItems,
                    otherItems = overflowOthers
                )
            }
            val carry = parsedUnits.firstOrNull { it.name == primaryCarryName }
                ?: parsedUnits.maxByOrNull { it.cost }
                ?: parsedUnits.first()
            val traits = extractTraits(JSONObject(), parsedUnits, targetEnv)
            val title = heading?.let { cleanCompTitle(it) } ?: synthesizeCompTitle(traits, carry.name)
            val sourceUrl = resolveNinjaCompSourceUrl(href, null, defaultSourceUrl, targetEnv)
            val slugId = title.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_").trim('_').take(48)
            val envPrefix = targetEnv.name.lowercase(Locale.US)

            val embeddedDomCode = TftTeamPlannerCodeEncoder.extractFirstValidV02Code(block, expectedSetNumber = setNumber).orEmpty()
            val comp = MetaComp(
                id = "tftninja_${envPrefix}_$slugId",
                title = title,
                tier = tier,
                environment = targetEnv,
                setName = "Set $setNumber",
                patchTag = patchTag,
                playstyle = inferPlaystyle(parsedUnits, carry),
                avgPlacement = round2(avg),
                top4Rate = top4,
                winRate = win,
                pickRate = pick,
                traits = traits,
                coreUnits = unitList,
                carryName = carry.name,
                augmentPriorities = domAugments.toList().take(6),
                levelingGuide = "${inferPlaystyle(parsedUnits, carry)} • Set $setNumber ($patchTag)",
                notes = "",
                hexPlacements = buildHexUnitPlacements(parsedUnits, carry.name),
                sourceUrl = sourceUrl,
                teamPlannerCode = embeddedDomCode,
                isTrending = tier == TierRank.S,
                trendDelta = if (tier == TierRank.S) "Top Meta" else ""
            )
            mergeComp(results, comp)
        }

        return results.values.toList()
    }

    private fun mergeComp(map: LinkedHashMap<String, MetaComp>, incoming: MetaComp) {
        val key = normKey(incoming.title).ifBlank { incoming.id }
        val existing = map[key]
        if (existing == null) {
            map[key] = incoming
        } else {
            val existingScore = existing.hexPlacements.sumOf { it.items.size } * 3 +
                existing.coreUnits.size + existing.augmentPriorities.size * 2 +
                if (existing.teamPlannerCode.isNotBlank()) 5 else 0
            val incomingScore = incoming.hexPlacements.sumOf { it.items.size } * 3 +
                incoming.coreUnits.size + incoming.augmentPriorities.size * 2 +
                if (incoming.teamPlannerCode.isNotBlank()) 5 else 0
            if (incomingScore > existingScore) {
                map[key] = mergeCompDetail(incoming, existing)
            } else {
                map[key] = mergeCompDetail(existing, incoming)
            }
        }
    }

    private fun discoverApiEndpoints(text: String, isPbe: Boolean): List<String> {
        val endpoints = LinkedHashSet<String>()
        API_URL_REGEX.findAll(text).forEach { match ->
            val raw = match.value.trim('"', '\'', '`', ' ', ')')
            val lower = raw.lowercase(Locale.US)
            if (lower.endsWith(".png") || lower.endsWith(".webp") || lower.endsWith(".svg") ||
                lower.endsWith(".css") || lower.endsWith(".js") || lower.endsWith(".woff2")
            ) {
                return@forEach
            }
            if (lower.contains("comp") || lower.contains("cluster") || lower.contains("meta") || lower.contains("tier")) {
                val full = when {
                    raw.startsWith("https://") -> raw
                    raw.startsWith("/") -> "$TFTNINJA_ORIGIN$raw"
                    else -> return@forEach
                }
                if (full.contains("tft.ninja", ignoreCase = true)) {
                    endpoints.add(ensurePbeQuery(full, isPbe))
                }
            }
        }
        return endpoints.toList()
    }

    private fun discoverStaticJsChunks(html: String): List<String> {
        val chunks = LinkedHashSet<String>()
        NEXT_JS_CHUNK_REGEX.findAll(html).forEach { m ->
            val path = m.groupValues.getOrNull(1).orEmpty()
            if (path.contains("comps", ignoreCase = true) || path.contains("app/comps", ignoreCase = true) ||
                path.contains("pages/comps", ignoreCase = true) || path.contains("chunks/", ignoreCase = true)
            ) {
                val full = if (path.startsWith("http")) path else "$TFTNINJA_ORIGIN$path"
                chunks.add(full)
            }
        }
        // Prioritize chunks explicitly referencing "comps"
        return chunks.sortedByDescending { it.contains("comp", ignoreCase = true) }
    }

    private fun defaultApiProbeUrls(isPbe: Boolean): List<String> {
        val suffix = if (isPbe) "?e=pbe" else ""
        return listOf(
            "$TFTNINJA_ORIGIN/api/comps$suffix",
            "$TFTNINJA_ORIGIN/api/v1/comps$suffix",
            "$TFTNINJA_ORIGIN/api/meta/comps$suffix",
            "$TFTNINJA_ORIGIN/api/clusters$suffix"
        )
    }

    private fun resolveNinjaCompSourceUrl(
        explicitUrl: String?,
        slug: String?,
        defaultSourceUrl: String,
        targetEnv: TftEnvironment
    ): String {
        val isPbe = targetEnv == TftEnvironment.PBE
        val cleanExplicit = explicitUrl?.trim().orEmpty()
        val resolved = when {
            cleanExplicit.startsWith("https://tft.ninja", ignoreCase = true) -> cleanExplicit
            cleanExplicit.startsWith("/comps", ignoreCase = true) -> "$TFTNINJA_ORIGIN$cleanExplicit"
            !slug.isNullOrBlank() && slug.matches(Regex("[a-zA-Z0-9_-]{3,80}")) &&
                !slug.startsWith("tftninja_") -> "$TFTNINJA_ORIGIN/comps/$slug"
            else -> defaultSourceUrl
        }
        return ensurePbeQuery(resolved, isPbe)
    }

    private fun ensurePbeQuery(url: String, isPbe: Boolean): String {
        if (!isPbe) return url
        if (url.contains("e=pbe", ignoreCase = true)) return url
        val sep = if (url.contains("?")) "&" else "?"
        return "$url${sep}e=pbe"
    }

    private fun extractNextBuildId(html: String): String? {
        return Regex(""""buildId"\s*:\s*"([^"]{4,64})"""").find(html)?.groupValues?.getOrNull(1)
    }

    private fun detectSetNumber(body: String): Int? {
        Regex("""TFTSet(\d{2})""", RegexOption.IGNORE_CASE).find(body)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
            if (it in 10..30) return it
        }
        Regex(""""(?:set|setNumber|currentSet)"\s*:\s*"?(?:set\s*)?(\d{2})"?""", RegexOption.IGNORE_CASE)
            .find(body)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
                if (it in 10..30) return it
            }
        return null
    }

    private fun parseJsonRootSafely(text: String): Any? {
        val trimmed = text.trim()
        return runCatching {
            when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                trimmed.startsWith("{") -> JSONObject(trimmed)
                else -> null
            }
        }.getOrNull()
    }

    private fun extractBalancedJson(source: String, startIndex: Int): String? {
        if (startIndex !in source.indices) return null
        val open = source[startIndex]
        val close = when (open) {
            '{' -> '}'
            '[' -> ']'
            else -> return null
        }
        var depth = 0
        var inString = false
        var escape = false
        val limit = minOf(source.length, startIndex + 400_000)
        for (i in startIndex until limit) {
            val ch = source[i]
            if (inString) {
                if (escape) {
                    escape = false
                } else if (ch == '\\') {
                    escape = true
                } else if (ch == '"') {
                    inString = false
                }
            } else {
                when (ch) {
                    '"' -> inString = true
                    open -> depth++
                    close -> {
                        depth--
                        if (depth == 0) return source.substring(startIndex, i + 1)
                    }
                }
            }
        }
        return null
    }

    private fun findNextJsonOpen(source: String, fromIndex: Int): Int {
        val end = minOf(source.length, fromIndex + 24)
        for (i in fromIndex until end) {
            if (source[i] == '[' || source[i] == '{') return i
        }
        return -1
    }

    private fun findEnclosingJsonStart(source: String, anchorPos: Int): Int {
        var braceBalance = 0
        var bracketBalance = 0
        val minPos = maxOf(0, anchorPos - 8_000)
        for (i in anchorPos downTo minPos) {
            when (source[i]) {
                '}' -> braceBalance++
                '{' -> {
                    if (braceBalance == 0 && bracketBalance == 0) {
                        // Look further back briefly for an enclosing '[' array if this is a list element
                        var j = i - 1
                        while (j >= minPos && source[j].isWhitespace()) j--
                        if (j >= minPos && (source[j] == '[' || source[j] == ',')) {
                            // keep climbing to the array start
                            braceBalance--
                        } else {
                            return i
                        }
                    } else {
                        braceBalance--
                    }
                }
                ']' -> bracketBalance++
                '[' -> {
                    if (bracketBalance == 0) return i
                    bracketBalance--
                }
            }
        }
        return -1
    }

    private fun unescapeJsString(raw: String): String {
        val sb = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val ch = raw[i]
            if (ch == '\\' && i + 1 < raw.length) {
                when (val next = raw[i + 1]) {
                    '"' -> { sb.append('"'); i += 2 }
                    '\\' -> { sb.append('\\'); i += 2 }
                    '/' -> { sb.append('/'); i += 2 }
                    'n' -> { sb.append('\n'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    'u' -> {
                        if (i + 5 < raw.length) {
                            val code = raw.substring(i + 2, i + 6).toIntOrNull(16)
                            if (code != null) {
                                sb.append(code.toChar())
                                i += 6
                                continue
                            }
                        }
                        sb.append(ch)
                        i++
                    }
                    else -> { sb.append(next); i += 2 }
                }
            } else {
                sb.append(ch)
                i++
            }
        }
        return sb.toString()
    }

    private fun extractStringList(obj: JSONObject, vararg keys: String): List<String> {
        val out = mutableListOf<String>()
        for (k in keys) {
            val arr = obj.optJSONArray(k) ?: continue
            for (i in 0 until arr.length()) {
                when (val elem = arr.opt(i)) {
                    is String -> elem.trim().takeIf { it.isNotBlank() }?.let { out.add(it) }
                    is JSONObject -> {
                        firstNonBlankString(elem, "name", "title", "label", "apiName", "id")
                            ?.let { out.add(it) }
                    }
                }
            }
            if (out.isNotEmpty()) break
        }
        return out
    }

    private fun firstNonBlankString(obj: JSONObject, vararg keys: String): String? {
        for (k in keys) {
            val v = obj.opt(k)
            if (v is String) {
                val trimmed = v.trim()
                if (trimmed.isNotBlank() && !trimmed.equals("null", ignoreCase = true)) return trimmed
            }
        }
        return null
    }

    private fun firstDouble(primary: JSONObject, secondary: JSONObject?, vararg keys: String): Double? {
        for (k in keys) {
            parseNumericValue(primary.opt(k))?.let { return it }
            if (secondary != null) parseNumericValue(secondary.opt(k))?.let { return it }
        }
        return null
    }

    private fun firstInt(obj: JSONObject, vararg keys: String): Int? {
        for (k in keys) {
            val v = obj.opt(k)
            when (v) {
                is Number -> return v.toInt()
                is String -> v.trim().toIntOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun parseNumericValue(raw: Any?): Double? = when (raw) {
        is Number -> raw.toDouble().takeIf { it.isFinite() }
        is String -> raw.trim().removeSuffix("%").toDoubleOrNull()?.takeIf { it.isFinite() }
        else -> null
    }

    private fun normalizePercent(raw: Double?): Double? {
        if (raw == null || !raw.isFinite() || raw < 0.0) return null
        val pct = if (raw in 0.0..1.0) raw * 100.0 else raw
        return round1(pct.coerceIn(0.0, 100.0))
    }

    private fun parseTierKeyOrNull(key: String): TierRank? {
        val clean = key.trim().uppercase(Locale.US)
        return when (clean) {
            "S", "S+", "S_TIER", "TIER_S" -> TierRank.S
            "A", "A+", "A_TIER", "TIER_A" -> TierRank.A
            "B", "B_TIER", "TIER_B" -> TierRank.B
            "C", "D", "C_TIER", "TIER_C" -> TierRank.C
            "X", "X_TIER", "TIER_X" -> TierRank.X
            else -> null
        }
    }

    private fun parseTierValue(raw: String): TierRank? {
        val clean = raw.trim().uppercase(Locale.US)
            .removePrefix("TIER")
            .trim(' ', '-', '_', ':')
        return when {
            clean.startsWith("S") -> TierRank.S
            clean.startsWith("A") -> TierRank.A
            clean.startsWith("B") -> TierRank.B
            clean.startsWith("C") || clean.startsWith("D") -> TierRank.C
            clean.startsWith("X") -> TierRank.X
            else -> null
        }
    }

    private fun cleanUnitName(raw: String): String {
        val stripped = raw.trim()
            .replace(Regex("""^TFT\d+_(?:Unit|Champ(?:ion)?)?_?""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^TFT_""", RegexOption.IGNORE_CASE), "")
        return formatChampionDisplayName(stripped).ifBlank { stripped }
    }

    private fun cleanItemLabel(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        // Resolve official Riot codenames FIRST before stripping TFT_Item_ (e.g. TFT_Item_RedBuff -> Sunfire Cape,
        // TFT_Item_RapidFireCannon -> Red Buff, TFT_Item_Leviathan -> Nashor's Tooth)
        TftAssetIconResolver.resolveDisplayName(trimmed)?.let { return it }
        val formattedFull = formatRiotAssetLabel(trimmed)
        if (formattedFull.isNotBlank() && formattedFull != trimmed) {
            return formattedFull
        }
        val stripped = trimmed.replace(Regex("""^TFT\d*_Item_""", RegexOption.IGNORE_CASE), "")
        return formatRiotAssetLabel(stripped).ifBlank { stripped }
    }

    private fun cleanTraitLabel(raw: String): String {
        val stripped = raw.trim()
            .replace(Regex("""^Set\d+_""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^TFT\d+_""", RegexOption.IGNORE_CASE), "")
        return stripped
    }

    private fun cleanCompTitle(raw: String): String {
        return raw.trim()
            .replace(Regex("""\s+"""), " ")
            .replace(Regex("""\s*[\-|•]\s*tft\.ninja.*$""", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    private fun synthesizeCompTitle(traits: List<String>, carryName: String): String {
        val leadTrait = traits.firstOrNull()?.replace(Regex("""^\d+\s+"""), "")?.trim().orEmpty()
        return when {
            leadTrait.isNotBlank() && carryName.isNotBlank() -> "$leadTrait $carryName"
            carryName.isNotBlank() -> "$carryName Carry"
            leadTrait.isNotBlank() -> "$leadTrait Comp"
            else -> "Meta Composition"
        }
    }

    private fun cleanPlaystyle(raw: String): String {
        val lower = raw.lowercase(Locale.US)
        return when {
            lower.contains("fast 9") || lower.contains("fast9") -> "Fast 9"
            lower.contains("fast 8") || lower.contains("fast8") -> "Fast 8"
            lower.contains("slow roll") || lower.contains("slowroll") -> "Slow Roll"
            lower.contains("hyper") -> "Hyper Roll"
            lower.contains("reroll") || lower.contains("re-roll") -> "Reroll"
            lower.contains("lvl 7") || lower.contains("level 7") -> "Level 7 Reroll"
            lower.contains("lvl 6") || lower.contains("level 6") -> "Level 6 Reroll"
            else -> raw.trim().replaceFirstChar { it.titlecase(Locale.US) }
        }
    }

    private fun inferPlaystyle(units: List<ParsedUnit>, carry: ParsedUnit?): String {
        val threeStars = units.filter { it.stars >= 3 }
        return when {
            threeStars.any { it.cost <= 2 } -> "Slow Roll"
            threeStars.any { it.cost == 3 } -> "Level 7 Reroll"
            carry != null && carry.cost >= 5 -> "Fast 9"
            carry != null && carry.cost == 4 -> "Fast 8"
            else -> "Standard"
        }
    }

    private fun decodeImageSrc(rawSrc: String): String {
        if (rawSrc.isBlank()) return ""
        val firstPass = runCatching { URLDecoder.decode(rawSrc, "UTF-8") }.getOrDefault(rawSrc)
        return runCatching { URLDecoder.decode(firstPass, "UTF-8") }.getOrDefault(firstPass)
    }

    private fun extractFileNameFromImageSrc(decodedSrc: String): String {
        if (decodedSrc.isBlank()) return ""
        val embeddedUrl = Regex("""[?&]url=([^&]+)""", RegexOption.IGNORE_CASE)
            .find(decodedSrc)?.groupValues?.getOrNull(1)
        val effective = embeddedUrl ?: decodedSrc
        return effective
            .substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('/')
            .substringBeforeLast('.')
            .trim()
    }

    private fun resolveScrapedImageUrl(decodedSrc: String): String? {
        val trimmed = decodedSrc.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("data:", ignoreCase = true)) return null
        val embeddedUrl = Regex("""[?&]url=([^&]+)""", RegexOption.IGNORE_CASE)
            .find(trimmed)?.groupValues?.getOrNull(1)?.trim()
        val effective = embeddedUrl?.takeIf { it.isNotBlank() } ?: trimmed
        return when {
            effective.startsWith("https://", ignoreCase = true) -> effective
            effective.startsWith("//") -> "https:$effective"
            effective.startsWith("/") -> "$TFTNINJA_ORIGIN$effective"
            else -> null
        }
    }

    private fun isPlausibleUnitName(name: String, targetEnv: TftEnvironment? = null): Boolean {
        if (name.length !in 2..30) return false
        val lower = name.lowercase(Locale.US)
        if (lower in NON_UNIT_LABELS) return false
        if (lower.startsWith("http") || lower.contains("tft.ninja") || lower.contains("tier")) return false
        if (TftAssetIconResolver.isKnownNonChampionAsset(name, targetEnv)) return false
        return name.any { it.isLetter() }
    }

    private fun round2(v: Double): Double = (v * 100.0).roundToInt() / 100.0
    private fun round1(v: Double): Double = (v * 10.0).roundToInt() / 10.0
    private fun normKey(raw: String): String = raw.lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")

    companion object {
        const val TFTNINJA_ORIGIN = "https://tft.ninja"
        const val TFTNINJA_LIVE_COMPS_URL = "https://tft.ninja/comps"
        const val TFTNINJA_PBE_COMPS_URL = "https://tft.ninja/comps?e=pbe"

        val TFTNINJA_LIVE_CHAMPIONS_URLS = listOf(
            "https://tft.ninja/champions",
            "https://tft.ninja/units"
        )
        val TFTNINJA_PBE_CHAMPIONS_URLS = listOf(
            "https://tft.ninja/champions?e=pbe",
            "https://tft.ninja/units?e=pbe"
        )

        private val NEXT_DATA_REGEX = Regex(
            """<script[^>]*id=["']__NEXT_DATA__["'][^>]*>(.*?)</script>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val NEXT_FLIGHT_CHUNK_REGEX = Regex(
            """self\.__next_f\.push\(\[\s*1\s*,\s*"((?:\\.|[^"\\])*)"\s*\]\)""",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )
        private val SCRIPT_JSON_REGEX = Regex(
            """<script[^>]*type=["']application/json["'][^>]*>(.*?)</script>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val WINDOW_STATE_REGEX = Regex(
            """window\.__(?:INITIAL_STATE|PRELOADED_STATE|DATA|NUXT)__\s*=\s*""",
            RegexOption.IGNORE_CASE
        )
        private val COMP_ARRAY_KEY_REGEX = Regex(
            """"(?:comps|compositions|clusters|tierlist|tierList|metaComps|teamComps|teams|builds|guides)"\s*:\s*""",
            RegexOption.IGNORE_CASE
        )
        private val RSC_LINE_JSON_REGEX = Regex("""(?:^|\n)[0-9a-fA-F]{1,6}:(?=\[|\{)""")
        private val COMP_OBJECT_ANCHOR_REGEX = Regex(
            """"(?:avgPlacement|avg_placement|avp|top4Rate|top4_rate|hexPlacements|coreUnits)"\s*:""",
            RegexOption.IGNORE_CASE
        )
        private val CHAMPION_FLIGHT_ANCHOR_REGEX = Regex(
            """"(?:bestItems|best_items|recommendedItems|bisItems|championName|character_id)"\s*:""",
            RegexOption.IGNORE_CASE
        )
        private val API_URL_REGEX = Regex(
            """(?:https://tft\.ninja)?/(?:api|_next/data|data)/[a-zA-Z0-9_./?=&%-]+"""
        )
        private val NEXT_JS_CHUNK_REGEX = Regex(
            """src=["']((?:https://tft\.ninja)?/_next/static/chunks/[^"']+\.js)["']"""
        )
        private val DOM_COMP_BLOCK_REGEX = Regex(
            """<(?:a[^>]*href=["'][^"']*?/comps/[^"']+["']|article\b[^>]*|tr\b[^>]*data-[^>]*|div[^>]*class=["'][^"']*(?:comp[-_](?:card|row|item)|tier[-_](?:row|card)|cluster[-_](?:card|row))[^"']*["'])[^>]*>""",
            RegexOption.IGNORE_CASE
        )
        private val HREF_COMP_REGEX = Regex("""href=(?:"([^"]*?/comps/[^"]+)"|'([^']*?/comps/[^']+)')""", RegexOption.IGNORE_CASE)
        private val IMG_TAG_REGEX = Regex("""<img\b[^>]+>""", RegexOption.IGNORE_CASE)
        private val ATTR_ALT_REGEX = Regex("""(?:alt|title)=(?:"([^"]+)"|'([^']+)')""", RegexOption.IGNORE_CASE)
        private val ATTR_SRC_REGEX = Regex("""src=(?:"([^"]+)"|'([^']+)')""", RegexOption.IGNORE_CASE)
        private val HEADING_TAG_REGEX = Regex("""<h[1-4][^>]*>(.*?)</h[1-4]>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

        private val NON_UNIT_LABELS = setOf(
            "logo", "icon", "badge", "tier", "copy", "share", "expand", "collapse",
            "arrow", "close", "menu", "search", "filter", "live", "pbe", "comps",
            "tft", "ninja", "gold", "star", "stars", "augment", "trait", "item"
        )

        /**
         * Orders compositions strictly from Tier S down (S -> A -> B -> C -> X),
         * then by avg placement ascending, top-4 rate descending, and title.
         */
        fun sortCompsTierSDown(comps: List<MetaComp>): List<MetaComp> =
            comps.sortedWith(
                compareBy<MetaComp> { it.tier.sortOrder }
                    .thenBy { it.avgPlacement }
                    .thenByDescending { it.top4Rate }
                    .thenBy { it.title }
            )

        /**
         * Stable fingerprint across LIVE + PBE comps to detect meta composition updates.
         */
        fun computeCompsSignature(liveComps: List<MetaComp>, pbeComps: List<MetaComp>): String {
            return (liveComps + pbeComps)
                .sortedBy { "${it.environment.name}_${it.id}" }
                .joinToString("|") { comp ->
                    val itemsSig = comp.hexPlacements
                        .sortedBy { it.unitName }
                        .joinToString(",") { "${it.unitName}:${it.items.joinToString("+")}" }
                    "${comp.environment.name}:${comp.title}:${comp.tier.name}:${comp.avgPlacement}:${comp.coreUnits.joinToString(",")}:$itemsSig"
                }
                .hashCode()
                .toString()
        }

        private fun defaultFetchUrl(urlString: String, rscHeaders: Boolean = false): String? {
            var connection: HttpURLConnection? = null
            return try {
                val url = URL(urlString)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 12_000
                    instanceFollowRedirects = true
                    useCaches = false
                    requestMethod = "GET"
                    setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
                    )
                    setRequestProperty("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
                    setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                    setRequestProperty("Cache-Control", "no-cache, no-store, max-age=0")
                    setRequestProperty("Pragma", "no-cache")
                    setRequestProperty("Referer", TFTNINJA_LIVE_COMPS_URL)
                    if (rscHeaders) {
                        setRequestProperty("RSC", "1")
                        setRequestProperty(
                            "Next-Router-State-Tree",
                            "%5B%22%22%2C%7B%22children%22%3A%5B%22comps%22%2C%7B%22children%22%3A%5B%22__PAGE__%22%2C%7B%7D%5D%7D%5D%7D%2Cnull%2Cnull%2Ctrue%5D"
                        )
                    }
                }
                val code = connection.responseCode
                if (code !in 200..299) return null
                BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            } catch (_: Exception) {
                null
            } finally {
                connection?.disconnect()
            }
        }
    }
}
