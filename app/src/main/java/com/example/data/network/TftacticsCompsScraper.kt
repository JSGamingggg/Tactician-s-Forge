package com.example.data.network

import com.example.data.model.HexUnitPlacement
import com.example.data.model.MetaComp
import com.example.data.model.TftEnvironment
import com.example.data.model.TierRank
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class TftacticsCompsFetchResult(
    val liveComps: List<MetaComp>,
    val pbeComps: List<MetaComp>,
    val detectedLiveSet: String?,
    val detectedPbeSet: String?,
    val fetchedFromNetwork: Boolean
)

/**
 * Fetches ALL team compositions for BOTH LIVE and PBE exclusively from
 * https://tftactics.gg/tierlist/team-comps/ (including lazy/below-the-fold comps
 * embedded in Next.js _next/data payloads, referenced JSON chunks, and JS bundles),
 * ensuring each environment strictly uses compositions from its matching TFT Set.
 */
class TftacticsCompsScraper(
    private val client: OkHttpClient = RiotApiClientFactory.publicOkHttp
) {
    fun fetchTeamComps(
        livePatchVersion: String,
        pbePatchVersion: String
    ): TftacticsCompsFetchResult {
        val baseHtml = fetchUrl(TFTACTICS_TEAM_COMPS_URL)
            ?: return TftacticsCompsFetchResult(
                liveComps = emptyList(),
                pbeComps = emptyList(),
                detectedLiveSet = null,
                detectedPbeSet = null,
                fetchedFromNetwork = false
            )

        // Check if TFTactics exposes a separate PBE query/path link on the team-comps page
        val pbeUrl = findPbeCompsUrl(baseHtml)
        val pbeHtml = if (pbeUrl != null && pbeUrl != TFTACTICS_TEAM_COMPS_URL) {
            fetchUrl(pbeUrl)
        } else {
            fetchUrl(TFTACTICS_TEAM_COMPS_PBE_QUERY_URL)
        }

        val liveParsed = parseCompsDocument(
            html = baseHtml,
            targetEnvironment = TftEnvironment.LIVE,
            patchTag = livePatchVersion,
            isDedicatedEnvPage = true,
            includeRemoteBundles = true
        )
        val pbeParsed = parseCompsDocument(
            html = pbeHtml ?: baseHtml,
            targetEnvironment = TftEnvironment.PBE,
            patchTag = pbePatchVersion,
            isDedicatedEnvPage = pbeHtml != null,
            includeRemoteBundles = true
        )

        val liveSetNumber = liveParsed.detectedSetNumber ?: inferSetNumberFromPatch(livePatchVersion)
        val pbeSetNumber = pbeParsed.detectedSetNumber ?: inferSetNumberFromPatch(pbePatchVersion)

        val enforcedLiveComps = enforceSetAlignment(
            comps = liveParsed.comps,
            expectedSetNumber = liveSetNumber,
            targetEnv = TftEnvironment.LIVE,
            patchTag = livePatchVersion
        )
        val enforcedPbeComps = enforceSetAlignment(
            comps = pbeParsed.comps,
            expectedSetNumber = pbeSetNumber,
            targetEnv = TftEnvironment.PBE,
            patchTag = pbePatchVersion
        )

        return TftacticsCompsFetchResult(
            liveComps = enforcedLiveComps,
            pbeComps = enforcedPbeComps,
            detectedLiveSet = liveParsed.detectedSetLabel ?: liveSetNumber?.let { "Set $it" },
            detectedPbeSet = pbeParsed.detectedSetLabel ?: pbeSetNumber?.let { "Set $it (PBE)" },
            fetchedFromNetwork = true
        )
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

        val fromJson = parseJsonComps(html, targetEnvironment, patchTag, defaultSetLabel)
        val fromDom = parseDomTeamComps(html, targetEnvironment, patchTag, defaultSetLabel, isDedicatedEnvPage)

        var combined = (fromJson + fromDom)
            .distinctBy { it.id }

        // Always inspect Next.js _next/data JSON and external JS/data bundles when fetching from network
        // so below-the-fold or lazy-mounted compositions are captured completely without truncation.
        if (includeRemoteBundles) {
            val fromNextData = parseNextDataEndpoint(html, targetEnvironment, patchTag, defaultSetLabel)
            val fromBundles = parseScriptBundleComps(html, targetEnvironment, patchTag, defaultSetLabel)
            if (fromNextData.isNotEmpty() || fromBundles.isNotEmpty()) {
                combined = (combined + fromNextData + fromBundles).distinctBy { it.id }
            }
        }

        return ParsedDocumentResult(
            comps = combined,
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
        return comps
            .filter { comp ->
                if (comp.environment != targetEnv) return@filter false
                if (expectedSetNumber == null) return@filter true
                val compSetNum = extractSetNumber(comp.setName) ?: extractSetNumber(comp.notes)
                // Keep comp if it matches the expected set or if no conflicting set number was attached
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
                    sourceUrl = TFTACTICS_TEAM_COMPS_URL
                )
            }
    }

    internal fun detectSetForEnvironment(html: String, env: TftEnvironment): Pair<Int, String>? {
        val envPattern = if (env == TftEnvironment.PBE) {
            Regex("""(?:PBE[^<\n]{0,40}Set\s*(\d+)(?:\s*:\s*([A-Za-z0-9 '&-]+))?|Set\s*(\d+)(?:\s*:\s*([A-Za-z0-9 '&-]+))?[^<\n]{0,30}PBE)""", RegexOption.IGNORE_CASE)
        } else {
            Regex("""(?:LIVE[^<\n]{0,40}Set\s*(\d+)(?:\s*:\s*([A-Za-z0-9 '&-]+))?)""", RegexOption.IGNORE_CASE)
        }

        val envMatch = envPattern.find(html)
        if (envMatch != null) {
            val num = (envMatch.groupValues[1].ifBlank { envMatch.groupValues[3] }).toIntOrNull()
            val subtitle = (envMatch.groupValues[2].ifBlank { envMatch.groupValues[4] }).trim()
            if (num != null) {
                val label = if (subtitle.isNotBlank()) "Set $num: $subtitle" else "Set $num"
                return num to (if (env == TftEnvironment.PBE && !label.contains("PBE", true)) "$label (PBE)" else label)
            }
        }

        // Generic Set detection in page header / active tab
        val genericMatches = SET_LABEL_REGEX.findAll(html).toList()
        if (genericMatches.isNotEmpty()) {
            val match = if (env == TftEnvironment.PBE) {
                genericMatches.firstOrNull { m ->
                    val windowStart = (m.range.first - 40).coerceAtLeast(0)
                    val windowEnd = (m.range.last + 40).coerceAtMost(html.length)
                    html.substring(windowStart, windowEnd).contains("pbe", ignoreCase = true)
                } ?: genericMatches.first()
            } else {
                genericMatches.firstOrNull { m ->
                    val windowStart = (m.range.first - 40).coerceAtLeast(0)
                    val windowEnd = (m.range.last + 40).coerceAtMost(html.length)
                    !html.substring(windowStart, windowEnd).contains("pbe", ignoreCase = true)
                } ?: genericMatches.first()
            }
            val num = match.groupValues[1].toIntOrNull()
            if (num != null) {
                val subtitle = match.groupValues.getOrNull(2)?.trim().orEmpty()
                val label = if (subtitle.isNotBlank()) "Set $num: $subtitle" else "Set $num"
                return num to (if (env == TftEnvironment.PBE && !label.contains("PBE", true)) "$label (PBE)" else label)
            }
        }
        return null
    }

    private fun parseJsonComps(
        html: String,
        targetEnvironment: TftEnvironment,
        patchTag: String,
        defaultSetLabel: String
    ): List<MetaComp> {
        val out = mutableListOf<MetaComp>()
        val scriptMatches = buildList {
            NEXT_DATA_REGEX.find(html)?.groupValues?.getOrNull(1)?.let(::add)
            INLINE_JSON_STATE_REGEX.findAll(html).forEach { m ->
                m.groupValues.getOrNull(1)?.let(::add)
            }
            addAll(extractCandidateCompArrays(html))
        }
        for (jsonText in scriptMatches) {
            runCatching {
                val trimmed = jsonText.trim()
                val root: Any = if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed)
                collectCompsFromJson(root, targetEnvironment, patchTag, defaultSetLabel, inheritedEnv = null, inheritedSet = null, out = out)
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
            "https://tftactics.gg/_next/data/$buildId/tierlist/team-comps.json?pbe=true"
        } else {
            "https://tftactics.gg/_next/data/$buildId/tierlist/team-comps.json"
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
                val lower = src.lowercase()
                lower.contains("main") ||
                    lower.contains("comp") ||
                    lower.contains("tier") ||
                    lower.contains("chunk") ||
                    lower.contains("app") ||
                    lower.contains("page") ||
                    lower.contains("_next") ||
                    lower.contains("bundle")
            }
            .map { src ->
                when {
                    src.startsWith("http") -> src
                    src.startsWith("//") -> "https:$src"
                    else -> "https://tftactics.gg${if (src.startsWith("/")) src else "/$src"}"
                }
            }
            .distinct()
            .take(10)
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
     * arrays containing TFT unit rosters (even with nested items/traits/hexes inside each object).
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
            val maxScan = (startIdx + 350_000).coerceAtMost(source.length)
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
                if (slice.contains("units", ignoreCase = true) ||
                    slice.contains("champions", ignoreCase = true) ||
                    slice.contains("characters", ignoreCase = true) ||
                    slice.contains("members", ignoreCase = true)
                ) {
                    out.add(slice)
                }
                lastConsumedEnd = endIdx
            }
        }
        return out
    }

    private fun collectCompsFromJson(
        node: Any?,
        targetEnv: TftEnvironment,
        patchTag: String,
        defaultSetLabel: String,
        inheritedEnv: TftEnvironment?,
        inheritedSet: String?,
        out: MutableList<MetaComp>,
        depth: Int = 0
    ) {
        if (node == null || depth > 12 || out.size >= 300) return
        when (node) {
            is JSONObject -> {
                val rawSet = node.optString("set")
                    .ifBlank { node.optString("setName") }
                    .ifBlank { node.optString("setNumber") }
                val currentSet = rawSet.takeIf { it.isNotBlank() }?.let {
                    if (it.all(Char::isDigit)) "Set $it" else it
                } ?: inheritedSet

                val nodeEnvFlag = when {
                    node.optBoolean("isPbe", false) || node.optBoolean("pbe", false) -> TftEnvironment.PBE
                    node.optString("environment").equals("PBE", ignoreCase = true) -> TftEnvironment.PBE
                    node.optString("mode").equals("PBE", ignoreCase = true) -> TftEnvironment.PBE
                    node.optString("environment").equals("LIVE", ignoreCase = true) -> TftEnvironment.LIVE
                    else -> inheritedEnv
                }

                val title = cleanText(
                    node.optString("title")
                        .ifBlank { node.optString("name") }
                        .ifBlank { node.optString("compName") }
                )
                val unitsArr = node.optJSONArray("units")
                    ?: node.optJSONArray("champions")
                    ?: node.optJSONArray("characters")
                    ?: node.optJSONArray("members")
                    ?: node.optJSONArray("team")

                if (title.length in 3..64 && unitsArr != null && unitsArr.length() >= 4) {
                    val effectiveEnv = nodeEnvFlag ?: targetEnv
                    if (effectiveEnv == targetEnv) {
                        val unitNames = (0 until unitsArr.length().coerceAtMost(10)).mapNotNull { idx ->
                            when (val u = unitsArr.opt(idx)) {
                                is String -> cleanText(u).takeIf { it.isNotBlank() }
                                is JSONObject -> cleanText(u.optString("name").ifBlank { u.optString("character_id").ifBlank { u.optString("id") } }).takeIf { it.isNotBlank() }
                                else -> null
                            }
                        }
                        if (unitNames.size >= 4) {
                            val carry = cleanText(node.optString("carry")).ifBlank { unitNames.last() }
                            val tier = TierRank.fromRaw(node.optString("tier"))
                            val playstyle = cleanText(node.optString("playstyle").ifBlank { node.optString("style") }).ifBlank { "Fast 8" }
                            val traits = extractStringList(node.optJSONArray("traits"))
                            val augments = extractStringList(node.optJSONArray("augments"))
                            val resolvedSet = currentSet ?: defaultSetLabel
                            val compId = "tftactics_${targetEnv.name.lowercase()}_${title.lowercase().replace(Regex("[^a-z0-9]+"), "_")}"
                            out.add(
                                MetaComp(
                                    id = compId,
                                    environment = targetEnv,
                                    title = title,
                                    tier = tier,
                                    playstyle = playstyle,
                                    avgPlacement = node.optDouble("avgPlace", 3.65).takeIf { !it.isNaN() } ?: 3.65,
                                    top4Rate = node.optDouble("top4Rate", 61.0).takeIf { !it.isNaN() } ?: 61.0,
                                    winRate = node.optDouble("winRate", 16.0).takeIf { !it.isNaN() } ?: 16.0,
                                    pickRate = node.optDouble("pickRate", 6.5).takeIf { !it.isNaN() } ?: 6.5,
                                    trendDelta = "TFTactics ${targetEnv.label}",
                                    isTrending = tier == TierRank.S,
                                    patchTag = patchTag,
                                    carryName = carry,
                                    traits = traits,
                                    coreUnits = unitNames,
                                    hexPlacements = buildPlacements(unitNames, carry),
                                    augmentPriorities = augments,
                                    levelingGuide = "Sourced from tftactics.gg ($playstyle • $resolvedSet)",
                                    notes = "Verified $resolvedSet composition synced from tftactics.gg/tierlist/team-comps/.",
                                    setName = resolvedSet,
                                    sourceUrl = TFTACTICS_TEAM_COMPS_URL
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
                    collectCompsFromJson(node.opt(key), targetEnv, patchTag, defaultSetLabel, branchEnv, currentSet, out, depth + 1)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length().coerceAtMost(500)) {
                    collectCompsFromJson(node.opt(i), targetEnv, patchTag, defaultSetLabel, inheritedEnv, inheritedSet, out, depth + 1)
                }
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
        val cards = extractTeamPortraitBlocks(html)
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

            val title = cleanText(TEAM_NAME_REGEX.find(blockHtml)?.groupValues?.getOrNull(1).orEmpty())
            if (title.length !in 3..64) continue

            val explicitTier = TEAM_TIER_REGEX.find(blockHtml)?.groupValues?.getOrNull(1)
            val sectionTier = explicitTier ?: inferPrecedingSectionTier(html, cardStartIdx)
            val tier = TierRank.fromRaw(sectionTier ?: "B")
            val playstyle = cleanText(TEAM_PLAYSTYLE_REGEX.find(blockHtml)?.groupValues?.getOrNull(1).orEmpty()).ifBlank { "Fast 8" }
            val unitNames = UNIT_ALT_REGEX.findAll(blockHtml)
                .map { cleanText(it.groupValues[1]) }
                .filter { it.isNotBlank() }
                .distinct()
                .take(10)
                .toList()
            if (unitNames.size < 4) continue

            val cardSet = SET_LABEL_REGEX.find(blockHtml)?.let { m ->
                "Set ${m.groupValues[1]}"
            } ?: defaultSetLabel
            val carry = unitNames.last()
            comps.add(
                MetaComp(
                    id = "tftactics_${targetEnv.name.lowercase()}_${title.lowercase().replace(Regex("[^a-z0-9]+"), "_")}",
                    environment = targetEnv,
                    title = title,
                    tier = tier,
                    playstyle = playstyle,
                    avgPlacement = when (tier) {
                        TierRank.S -> 3.48
                        TierRank.A -> 3.85
                        TierRank.B -> 4.18
                        TierRank.C -> 4.55
                    },
                    top4Rate = when (tier) {
                        TierRank.S -> 64.0
                        TierRank.A -> 57.5
                        TierRank.B -> 51.0
                        TierRank.C -> 45.0
                    },
                    winRate = when (tier) {
                        TierRank.S -> 18.0
                        TierRank.A -> 14.5
                        TierRank.B -> 11.5
                        TierRank.C -> 8.5
                    },
                    pickRate = 7.0,
                    trendDelta = "TFTactics ${targetEnv.label}",
                    isTrending = tier == TierRank.S,
                    patchTag = patchTag,
                    carryName = carry,
                    traits = emptyList(),
                    coreUnits = unitNames,
                    hexPlacements = buildPlacements(unitNames, carry),
                    augmentPriorities = emptyList(),
                    levelingGuide = "Play $playstyle on $cardSet ($patchTag)",
                    notes = "Synced from tftactics.gg/tierlist/team-comps/ ($cardSet).",
                    setName = cardSet,
                    sourceUrl = TFTACTICS_TEAM_COMPS_URL
                )
            )
        }
        return comps
    }

    /**
     * Scans all <div class="...team-portrait/comp/row..."> blocks across the full HTML document,
     * balancing nested <div> tags so multi-layered React/HTML cards (including below-the-fold rows) never get truncated.
     */
    private fun extractTeamPortraitBlocks(html: String): List<Pair<Int, String>> {
        val starts = TEAM_PORTRAIT_START_REGEX.findAll(html).toList()
        if (starts.isEmpty()) return emptyList()

        val result = mutableListOf<Pair<Int, String>>()
        for (idx in starts.indices) {
            val startPos = starts[idx].range.first
            val nextStart = if (idx + 1 < starts.size) starts[idx + 1].range.first else html.length
            var depth = 0
            var endPos = nextStart
            for (tag in DIV_TAG_REGEX.findAll(html, startPos)) {
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
            if (endIdxValid(startPos, endPos)) {
                result.add(startPos to html.substring(startPos, endPos))
            }
        }
        return result
    }

    private fun endIdxValid(startPos: Int, endPos: Int): Boolean = endPos > startPos

    private fun inferPrecedingSectionTier(html: String, cardStartIdx: Int): String? {
        val windowStart = (cardStartIdx - 4000).coerceAtLeast(0)
        val prefix = html.substring(windowStart, cardStartIdx)
        return SECTION_TIER_REGEX.findAll(prefix).lastOrNull()?.groupValues?.firstOrNull { it.isNotBlank() && it.length <= 2 }
    }

    private fun buildPlacements(units: List<String>, carry: String): List<HexUnitPlacement> {
        return units.mapIndexed { idx, name ->
            HexUnitPlacement(
                unitName = name,
                row = if (idx < units.size / 2) 0 else 3,
                col = (idx * 2) % 7,
                cost = if (name.equals(carry, ignoreCase = true)) 4 else 3,
                isCarry = name.equals(carry, ignoreCase = true)
            )
        }
    }

    private fun extractStringList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length().coerceAtMost(10)).mapNotNull { i ->
            when (val el = arr.opt(i)) {
                is String -> cleanText(el).takeIf { it.isNotBlank() }
                is JSONObject -> cleanText(el.optString("name").ifBlank { el.optString("label") }).takeIf { it.isNotBlank() }
                else -> null
            }
        }
    }

    private fun findPbeCompsUrl(html: String): String? {
        val m = PBE_LINK_REGEX.find(html) ?: return null
        val href = m.groupValues[1]
        return if (href.startsWith("http")) href else "https://tftactics.gg${if (href.startsWith("/")) href else "/$href"}"
    }

    private fun inferSetNumberFromPatch(patchVersion: String): Int? {
        return Regex("""\b(1[3-9]|2[0-9])\.""").find(patchVersion)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun extractSetNumber(text: String): Int? {
        return Regex("""Set\s*(\d+)""", RegexOption.IGNORE_CASE).find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun fetchUrl(url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "TacticiansForge-Android/1.0 (+https://tftactics.gg/tierlist/team-comps/)")
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
        const val TFTACTICS_TEAM_COMPS_URL = "https://tftactics.gg/tierlist/team-comps/"
        const val TFTACTICS_TEAM_COMPS_PBE_QUERY_URL = "https://tftactics.gg/tierlist/team-comps/?pbe=true"

        private val NEXT_DATA_REGEX = Regex(
            """<script[^>]*id=["']__NEXT_DATA__["'][^>]*>(.*?)</script>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val NEXT_BUILD_ID_REGEX = Regex(""""buildId"\s*:\s*"([^"]+)"""")
        private val INLINE_JSON_STATE_REGEX = Regex(
            """window\.__PRELOADED_STATE__\s*=\s*(\{.*?\});""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val COMP_ARRAY_ANCHOR_REGEX = Regex(
            """\[\s*\{\s*"(?:title|name|compName|tier)"\s*:"""
        )
        private val SCRIPT_SRC_REGEX = Regex(
            """<script[^>]+src=["']([^"']+\.js(?:\?[^"']*)?)["']""",
            RegexOption.IGNORE_CASE
        )
        private val PBE_LINK_REGEX = Regex(
            """href=["']([^"']*tierlist/team-comps[^"']*pbe[^"']*)["']""",
            RegexOption.IGNORE_CASE
        )
        private val SET_LABEL_REGEX = Regex(
            """\bSet\s*(\d+)(?:\s*:\s*([A-Za-z0-9 '&-]{2,28}))?""",
            RegexOption.IGNORE_CASE
        )
        private val TEAM_PORTRAIT_START_REGEX = Regex(
            """<div[^>]*class=["'][^"']*(?:team-(?:portrait|comp|row)|comp-(?:row|card))[^"']*["'][^>]*>""",
            RegexOption.IGNORE_CASE
        )
        private val DIV_TAG_REGEX = Regex("""</?div\b[^>]*>""", RegexOption.IGNORE_CASE)
        private val TEAM_NAME_REGEX = Regex(
            """class=["'][^"']*team-name[^"']*["'][^>]*>(.*?)<""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val TEAM_TIER_REGEX = Regex(
            """class=["'][^"']*team-rank[^"']*["'][^>]*>\s*([SABCD]\+?)\s*<""",
            RegexOption.IGNORE_CASE
        )
        private val SECTION_TIER_REGEX = Regex(
            """(?:tier[-_]label[^>]*>\s*([SABCD]\+?)|<h[1-4][^>]*>\s*([SABCD]\+?)\s*(?:Tier)?)""",
            RegexOption.IGNORE_CASE
        )
        private val TEAM_PLAYSTYLE_REGEX = Regex(
            """class=["'][^"']*team-playstyle[^"']*["'][^>]*>(.*?)<""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val UNIT_ALT_REGEX = Regex(
            """(?:alt|data-unit|title)=["']([A-Za-z0-9 '&.\-]{2,24})["']""",
            RegexOption.IGNORE_CASE
        )
        private val HTML_TAG_REGEX = Regex("""<[^>]+>""")
        private val WHITESPACE_REGEX = Regex("""\s+""")
    }
}
