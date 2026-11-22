package com.example.data.network

import com.example.data.model.BalanceChangeEntry
import com.example.data.model.DeltaType
import com.example.data.model.PatchUpdateRecord
import com.example.data.model.TftEnvironment
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class TftipsFetchResult(
    val livePatchVersion: String?,
    val pbePatchVersion: String?,
    val livePatchRecords: List<PatchUpdateRecord>,
    val pbePatchRecords: List<PatchUpdateRecord>,
    val fetchedFromNetwork: Boolean
)

/**
 * Fetches and parses actual LIVE and PBE patch notes and balance changes
 * directly from https://tftips.app/en/patches and its detail pages
 * (e.g. /en/patches/18.7, /en/patches/18.6, /en/patches/pbe).
 */
class TftipsPatchScraper(
    private val client: OkHttpClient = RiotApiClientFactory.publicOkHttp
) {
    fun fetchPatchesFromTftips(): TftipsFetchResult {
        val mainHtml = fetchUrl(TFTIPS_PATCHES_URL)
            ?: return TftipsFetchResult(
                livePatchVersion = null,
                pbePatchVersion = null,
                livePatchRecords = emptyList(),
                pbePatchRecords = emptyList(),
                fetchedFromNetwork = false
            )

        val parsedFromJson = parseNextDataPatches(mainHtml)
        val parsedCards = parseHtmlPatchCards(mainHtml)

        val enrichedRecords = parsedCards.mapIndexed { index, cardRecord ->
            if (index < 4 && cardRecord.sourceUrl.isNotBlank() && cardRecord.sourceUrl != TFTIPS_PATCHES_URL) {
                val detailHtml = fetchUrl(cardRecord.sourceUrl)
                if (detailHtml != null) {
                    enrichPatchWithDetailHtml(cardRecord, detailHtml)
                } else {
                    cardRecord
                }
            } else {
                cardRecord
            }
        }

        val seenIds = HashSet<String>()
        val seenNormVersions = HashSet<String>()
        val combined = (enrichedRecords + parsedFromJson).filter { rec ->
            val normId = rec.id.trim().lowercase()
            val normVer = "${rec.environment.name}_${rec.version.lowercase().replace(Regex("[^a-z0-9]"), "")}"
            val idAdded = normId.isEmpty() || seenIds.add(normId)
            val verAdded = seenNormVersions.add(normVer)
            idAdded && verAdded
        }

        val livePatches = combined.filter { it.environment == TftEnvironment.LIVE }
        val pbePatches = combined.filter { it.environment == TftEnvironment.PBE }

        return TftipsFetchResult(
            livePatchVersion = livePatches.firstOrNull()?.version,
            pbePatchVersion = pbePatches.firstOrNull()?.version,
            livePatchRecords = livePatches,
            pbePatchRecords = pbePatches,
            fetchedFromNetwork = combined.isNotEmpty()
        )
    }

    private fun fetchUrl(url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
                )
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

    internal fun parseHtmlPatchSections(html: String): List<PatchUpdateRecord> =
        parseHtmlPatchCards(html)

    /**
     * Parses the patch cards on https://tftips.app/en/patches.
     * Each card starts with `<a href="/patches/...">...</a>` and contains `<time>` plus
     * `<ul aria-label="Buffs|Nerfs|Adjustments|Modified|Added|Removed">` icon lists with
     * `<li title="TargetName"><a href="/champs|traits|items|augs/...">`.
     */
    internal fun parseHtmlPatchCards(html: String): List<PatchUpdateRecord> {
        val records = mutableListOf<PatchUpdateRecord>()
        val cardAnchors = PATCH_CARD_ANCHOR_REGEX.findAll(html).toList()
        if (cardAnchors.isEmpty()) {
            return parseGenericHeadingPatchSections(html)
        }

        for (i in cardAnchors.indices) {
            val match = cardAnchors[i]
            val rawHref = match.groupValues[1].trim()
            val anchorText = cleanText(match.groupValues[2])
            if (anchorText.isBlank()) continue

            val nextStart = if (i + 1 < cardAnchors.size) {
                cardAnchors[i + 1].range.first
            } else {
                (match.range.last + 20000).coerceAtMost(html.length)
            }
            val cardChunk = html.substring(match.range.first, nextStart)

            val slug = rawHref.substringAfterLast("/").trim()
            val isPbe = slug.equals("pbe", ignoreCase = true) ||
                rawHref.contains("/pbe", ignoreCase = true) ||
                anchorText.contains("pbe", ignoreCase = true) ||
                SET_TRANSITION_REGEX.containsMatchIn(anchorText)

            val env = if (isPbe) TftEnvironment.PBE else TftEnvironment.LIVE

            val version = when {
                isPbe -> {
                    val verInText = PATCH_VERSION_REGEX.find("$anchorText $slug")?.value
                    val targetSet = SET_TRANSITION_REGEX.find(anchorText)?.groupValues?.getOrNull(2)
                    when {
                        verInText != null -> {
                            val normalizedPbeVer = verInText
                                .replace(Regex("""[\s/(]+PBE\)?$""", RegexOption.IGNORE_CASE), "-PBE")
                                .replace(Regex("""\s+"""), "")
                            if (normalizedPbeVer.contains("PBE", ignoreCase = true)) normalizedPbeVer else "$normalizedPbeVer-PBE"
                        }
                        targetSet != null -> "$targetSet.1-PBE"
                        else -> "19.1-PBE"
                    }
                }
                else -> {
                    PATCH_VERSION_REGEX.find("$slug $anchorText")?.value ?: slug.ifBlank { continue }
                }
            }

            val timeText = TIME_TAG_REGEX.find(cardChunk)?.groupValues?.getOrNull(1)?.let(::cleanText).orEmpty()
            val badgeText = when {
                cardChunk.contains(">Latest<", ignoreCase = true) -> "Latest"
                cardChunk.contains(">Micro patch<", ignoreCase = true) -> "Micro patch"
                cardChunk.contains(">Set launch<", ignoreCase = true) -> "Set launch"
                else -> ""
            }
            val releaseDate = listOf(badgeText, timeText).filter { it.isNotBlank() }.joinToString(" • ")
                .ifBlank { if (isPbe) "PBE Cycle" else "Live Release" }

            val groupedTargets = LinkedHashMap<String, MutableList<Pair<String, String>>>()
            for (ulMatch in UL_ARIA_REGEX.findAll(cardChunk)) {
                val sectionLabel = cleanText(ulMatch.groupValues[1])
                val ulBody = ulMatch.groupValues[2]
                val list = groupedTargets.getOrPut(sectionLabel) { mutableListOf() }
                for (liMatch in LI_TITLE_REGEX.findAll(ulBody)) {
                    val targetName = unescapeHtml(liMatch.groupValues[1]).trim()
                    if (targetName.isBlank()) continue
                    val liInner = liMatch.groupValues[2]
                    val category = detectCategoryFromHtml(liInner)
                    if (list.none { it.first.equals(targetName, ignoreCase = true) }) {
                        list.add(targetName to category)
                    }
                }
            }

            val highlights = mutableListOf<String>()
            val balanceChanges = mutableListOf<BalanceChangeEntry>()
            var changeIdx = 0

            for ((sectionLabel, targets) in groupedTargets) {
                if (targets.isEmpty()) continue
                val previewNames = targets.take(10).joinToString(", ") { it.first }
                val overflow = if (targets.size > 10) " +${targets.size - 10} more" else ""
                highlights.add("$sectionLabel (${targets.size}): $previewNames$overflow")

                val deltaType = mapSectionToDelta(sectionLabel)
                for ((targetName, category) in targets) {
                    changeIdx++
                    balanceChanges.add(
                        BalanceChangeEntry(
                            id = "tftips_bc_${env.name.lowercase()}_${version.replace(Regex("[^a-zA-Z0-9]"), "_")}_$changeIdx",
                            targetName = targetName,
                            category = category,
                            deltaType = deltaType,
                            summary = "$category ${sectionLabel.lowercase().removeSuffix("s")} in Patch $version",
                            previousValue = "",
                            newValue = ""
                        )
                    )
                }
            }

            val totalBuffs = groupedTargets.entries.firstOrNull { it.key.equals("Buffs", ignoreCase = true) }?.value?.size ?: 0
            val totalNerfs = groupedTargets.entries.firstOrNull { it.key.equals("Nerfs", ignoreCase = true) }?.value?.size ?: 0
            val totalAdjust = groupedTargets.entries
                .filter { !it.key.equals("Buffs", ignoreCase = true) && !it.key.equals("Nerfs", ignoreCase = true) }
                .sumOf { it.value.size }

            val summaryParts = mutableListOf<String>()
            if (totalBuffs > 0) summaryParts.add("$totalBuffs Buff${if (totalBuffs > 1) "s" else ""}")
            if (totalNerfs > 0) summaryParts.add("$totalNerfs Nerf${if (totalNerfs > 1) "s" else ""}")
            if (totalAdjust > 0) summaryParts.add("$totalAdjust Adjustment${if (totalAdjust > 1) "s" else ""}")

            val topTargetsPreview = groupedTargets.values.flatten().take(6).joinToString(", ") { it.first }
            val headline = if (summaryParts.isNotEmpty()) {
                "${summaryParts.joinToString(", ")} — $topTargetsPreview"
            } else {
                "TFT $version patch balance updates."
            }

            val fullUrl = when {
                rawHref.startsWith("http") -> rawHref
                rawHref.startsWith("/en/") -> "https://tftips.app$rawHref"
                rawHref.startsWith("/") -> "https://tftips.app/en$rawHref"
                else -> "https://tftips.app/en/$rawHref"
            }

            val cleanTitle = when {
                isPbe && !anchorText.contains("Patch", ignoreCase = true) -> "TFT Patch $version — $anchorText"
                !anchorText.startsWith("TFT", ignoreCase = true) -> "TFT $anchorText Changes"
                else -> anchorText
            }

            val bareVer = version.removeSuffix("-PBE").trim()
            val isMajor = bareVer.isNotEmpty() && !bareVer.last().isLetter() && !badgeText.contains("Micro", ignoreCase = true)

            records.add(
                PatchUpdateRecord(
                    id = "tftips_${env.name.lowercase()}_${version.replace(Regex("[^a-zA-Z0-9]"), "_")}",
                    environment = env,
                    version = version,
                    title = cleanTitle,
                    releaseDate = releaseDate,
                    isMajorPatch = isMajor,
                    headline = headline,
                    highlights = highlights.ifEmpty {
                        listOf("Patch $version balance updates")
                    },
                    balanceChanges = balanceChanges,
                    trendingCompIds = emptyList(),
                    sourceUrl = fullUrl
                )
            )
            if (records.size >= 8) break
        }

        return records
    }

    /**
     * Enriches a patch card with exact per-target stat changes (`oldValue -> newValue` and ability/trait summaries)
     * parsed from its detail page (e.g., https://tftips.app/en/patches/18.7 or /en/patches/pbe).
     */
    internal fun enrichPatchWithDetailHtml(
        baseRecord: PatchUpdateRecord,
        detailHtml: String
    ): PatchUpdateRecord {
        val h1Text = TITLE_REGEX.find(detailHtml)?.groupValues?.getOrNull(1)?.let(::cleanText).orEmpty()
        var resolvedVersion = baseRecord.version
        if (h1Text.isNotBlank()) {
            val h1Ver = PATCH_VERSION_REGEX.find(h1Text)?.value
            if (h1Ver != null) {
                resolvedVersion = if (baseRecord.environment == TftEnvironment.PBE && !h1Ver.contains("PBE", ignoreCase = true)) {
                    "$h1Ver-PBE"
                } else {
                    h1Ver.replace(Regex("""\s*\(\s*PBE\s*\)""", RegexOption.IGNORE_CASE), "-PBE")
                        .replace(" ", "")
                }
            }
        }

        val resolvedTitle = when {
            h1Text.isNotBlank() && baseRecord.environment == TftEnvironment.PBE &&
                baseRecord.title.contains("Set", ignoreCase = true) && !h1Text.contains("Set", ignoreCase = true) -> {
                val suffix = baseRecord.title.substringAfter("—", "").trim()
                if (suffix.isNotBlank()) "$h1Text — $suffix" else h1Text
            }
            h1Text.isNotBlank() -> h1Text
            else -> baseRecord.title
        }

        val detailMap = parseDetailTargetChanges(detailHtml)
        if (detailMap.isEmpty()) {
            return baseRecord.copy(
                id = "tftips_${baseRecord.environment.name.lowercase()}_${resolvedVersion.replace(Regex("[^a-zA-Z0-9]"), "_")}",
                version = resolvedVersion,
                title = resolvedTitle
            )
        }

        val updatedChanges = if (baseRecord.balanceChanges.isNotEmpty()) {
            baseRecord.balanceChanges.map { entry ->
                val detail = detailMap[entry.targetName.lowercase()]
                if (detail != null) {
                    entry.copy(
                        summary = cleanText(detail.summary).ifBlank { cleanText(entry.summary) },
                        previousValue = sanitizeCompactStatValue(detail.previousValue),
                        newValue = sanitizeCompactStatValue(detail.newValue),
                        deltaType = detail.deltaType ?: entry.deltaType
                    )
                } else {
                    entry.copy(
                        summary = cleanText(entry.summary),
                        previousValue = sanitizeCompactStatValue(entry.previousValue),
                        newValue = sanitizeCompactStatValue(entry.newValue)
                    )
                }
            }
        } else {
            detailMap.values.mapIndexed { idx, d ->
                BalanceChangeEntry(
                    id = "tftips_bc_${baseRecord.environment.name.lowercase()}_${resolvedVersion.replace(Regex("[^a-zA-Z0-9]"), "_")}_${idx + 1}",
                    targetName = cleanText(d.targetName),
                    category = d.category,
                    deltaType = d.deltaType ?: DeltaType.ADJUST,
                    summary = cleanText(d.summary),
                    previousValue = sanitizeCompactStatValue(d.previousValue),
                    newValue = sanitizeCompactStatValue(d.newValue)
                )
            }
        }.distinctBy { "${it.targetName.lowercase()}|${it.category.lowercase()}" }

        return baseRecord.copy(
            id = "tftips_${baseRecord.environment.name.lowercase()}_${resolvedVersion.replace(Regex("[^a-zA-Z0-9]"), "_")}",
            version = resolvedVersion,
            title = cleanText(resolvedTitle),
            headline = cleanText(baseRecord.headline),
            highlights = baseRecord.highlights.map(::cleanText).filter { it.isNotBlank() },
            balanceChanges = updatedChanges
        )
    }

    internal data class ParsedTargetDetail(
        val targetName: String,
        val category: String,
        val deltaType: DeltaType?,
        val summary: String,
        val previousValue: String,
        val newValue: String
    )

    internal fun parseDetailTargetChanges(detailHtml: String): Map<String, ParsedTargetDetail> {
        val result = LinkedHashMap<String, ParsedTargetDetail>()

        // 1. Parse rendered HTML <h2> sections (Buffs, Nerfs, Modified, Adjustments) and <h3> target blocks
        val h2Matches = H2_SECTION_REGEX.findAll(detailHtml).toList()
        for (i in h2Matches.indices) {
            val sectionName = cleanText(h2Matches[i].groupValues[1])
            val delta = mapSectionToDelta(sectionName)
            val sectionStart = h2Matches[i].range.last + 1
            val sectionEnd = if (i + 1 < h2Matches.size) h2Matches[i + 1].range.first else detailHtml.length
            val sectionHtml = detailHtml.substring(sectionStart.coerceAtMost(detailHtml.length), sectionEnd.coerceAtMost(detailHtml.length))

            val h3Matches = H3_TARGET_REGEX.findAll(sectionHtml).toList()
            for (j in h3Matches.indices) {
                val targetName = cleanText(h3Matches[j].groupValues[1])
                if (targetName.isBlank()) continue
                val blockStart = h3Matches[j].range.last + 1
                val blockEnd = if (j + 1 < h3Matches.size) h3Matches[j + 1].range.first else sectionHtml.length
                val blockHtml = sectionHtml.substring(blockStart.coerceAtMost(sectionHtml.length), blockEnd.coerceAtMost(sectionHtml.length))

                val detail = extractTargetDetailFromHtmlBlock(targetName, delta, blockHtml)
                if (detail != null) {
                    result[targetName.lowercase()] = detail
                }
            }
        }

        // 2. Parse RSC payload scripts for additional tabs (e.g. Traits, Augments, Items)
        val unescapedRsc = detailHtml
            .replace("\\\"", "\"")
            .replace("\\n", " ")
            .replace("\\u0026", "&")
            .replace("\\u003c", "<")
            .replace("\\u003e", ">")

        val rscTargetMatches = RSC_H3_TARGET_REGEX.findAll(unescapedRsc).toList()
        for (k in rscTargetMatches.indices) {
            val targetName = unescapeHtml(rscTargetMatches[k].groupValues[1]).trim()
            if (targetName.isBlank() || result.containsKey(targetName.lowercase())) continue
            val start = rscTargetMatches[k].range.last + 1
            val end = if (k + 1 < rscTargetMatches.size) {
                rscTargetMatches[k + 1].range.first.coerceAtMost(start + 3500)
            } else {
                (start + 3500).coerceAtMost(unescapedRsc.length)
            }
            val snippet = unescapedRsc.substring(start.coerceAtMost(unescapedRsc.length), end.coerceAtMost(unescapedRsc.length))
            val detail = extractTargetDetailFromRscSnippet(targetName, snippet)
            if (detail != null) {
                result[targetName.lowercase()] = detail
            }
        }

        return result
    }

    private fun extractTargetDetailFromHtmlBlock(
        targetName: String,
        deltaType: DeltaType,
        blockHtml: String
    ): ParsedTargetDetail? {
        val diffPairs = mutableListOf<Pair<String, String>>()
        for (m in DIFF_CHANGE_REGEX.findAll(blockHtml)) {
            val oldVal = cleanText(m.groupValues[1])
            val newVal = cleanText(m.groupValues[2])
            if (oldVal.isNotBlank() && newVal.isNotBlank()) {
                diffPairs.add(oldVal to newVal)
            }
        }

        val firstOld = diffPairs.firstOrNull()?.first.orEmpty()
        val firstNew = diffPairs.firstOrNull()?.second.orEmpty()

        val insVal = if (firstNew.isBlank()) {
            DIFF_INS_REGEX.find(blockHtml)?.groupValues?.getOrNull(1)?.let(::cleanText).orEmpty()
        } else ""
        val delVal = if (firstOld.isBlank()) {
            DIFF_DEL_REGEX.find(blockHtml)?.groupValues?.getOrNull(1)?.let(::cleanText).orEmpty()
        } else ""

        // Convert old/new spans into "old -> new" inline before stripping tags so summary is crystal clear
        val readableHtml = blockHtml
            .replace(DIFF_CHANGE_REGEX) { m ->
                val o = cleanText(m.groupValues[1])
                val n = cleanText(m.groupValues[2])
                " $o → $n "
            }
            .replace(Regex("""</(?:div|p|li|h4)>""", RegexOption.IGNORE_CASE), " • ")

        val rawLines = cleanText(readableHtml)
            .split("•")
            .map { it.trim() }
            .filter { line ->
                line.length >= 4 &&
                    !line.equals("Passive", ignoreCase = true) &&
                    !line.equals("Active", ignoreCase = true) &&
                    !line.equals("Stats", ignoreCase = true)
            }

        val bestLines = rawLines.filter { it.contains("→") || it.contains("+") || it.contains("%") || it.contains("Trait") }
            .ifEmpty { rawLines }

        val summary = bestLines.take(2).joinToString(" | ").take(130)
            .ifBlank { "Balance adjustment in latest patch" }

        val compactOld = sanitizeCompactStatValue(firstOld.ifBlank { delVal })
        val compactNew = sanitizeCompactStatValue(firstNew.ifBlank { insVal })

        return ParsedTargetDetail(
            targetName = targetName,
            category = detectCategoryFromHtml(blockHtml),
            deltaType = deltaType,
            summary = summary,
            previousValue = compactOld,
            newValue = compactNew
        )
    }

    private fun extractTargetDetailFromRscSnippet(
        targetName: String,
        snippet: String
    ): ParsedTargetDetail? {
        val oldVal = RSC_OLD_VAL_REGEX.find(snippet)?.groupValues?.getOrNull(1)?.let(::cleanText).orEmpty()
        val newVal = RSC_NEW_VAL_REGEX.find(snippet)?.groupValues?.getOrNull(1)?.let(::cleanText).orEmpty()
        val insVal = RSC_INS_VAL_REGEX.find(snippet)?.groupValues?.getOrNull(1)?.let(::cleanText).orEmpty()

        val textTokens = RSC_CHILDREN_TEXT_REGEX.findAll(snippet)
            .map { unescapeHtml(it.groupValues[1]).trim() }
            .filter { token ->
                token.length in 2..90 &&
                    !token.startsWith("$") &&
                    !token.contains("text-") &&
                    !token.contains("flex") &&
                    !token.contains("border-") &&
                    !token.contains("bg-") &&
                    !token.equals("span", ignoreCase = true) &&
                    !token.equals("div", ignoreCase = true)
            }
            .toList()

        val summary = when {
            oldVal.isNotBlank() && newVal.isNotBlank() -> {
                val contextLabel = textTokens.firstOrNull {
                    it != oldVal && it != newVal && it != "→" && it.length >= 3
                }?.trimEnd(':')?.trim()
                if (!contextLabel.isNullOrBlank()) {
                    "$contextLabel: $oldVal → $newVal"
                } else {
                    "$oldVal → $newVal"
                }
            }
            insVal.isNotBlank() -> {
                val contextLabel = textTokens.firstOrNull { it != insVal && it.length >= 3 }?.trimEnd(':')?.trim()
                if (!contextLabel.isNullOrBlank()) "$contextLabel: $insVal" else insVal
            }
            textTokens.isNotEmpty() -> textTokens.take(3).joinToString(" ").take(110)
            else -> return null
        }

        return ParsedTargetDetail(
            targetName = targetName,
            category = detectCategoryFromHtml(snippet),
            deltaType = null,
            summary = cleanText(summary).take(130),
            previousValue = sanitizeCompactStatValue(oldVal),
            newValue = sanitizeCompactStatValue(newVal.ifBlank { insVal })
        )
    }

    private fun detectCategoryFromHtml(rawHtml: String): String {
        return when {
            rawHtml.contains("/champs/", ignoreCase = true) || rawHtml.contains("icon-champ", ignoreCase = true) -> "Champion"
            rawHtml.contains("/traits/", ignoreCase = true) || rawHtml.contains("icon-trait", ignoreCase = true) -> "Trait"
            rawHtml.contains("/items/", ignoreCase = true) || rawHtml.contains("icon-item", ignoreCase = true) -> "Item"
            rawHtml.contains("/augs/", ignoreCase = true) || rawHtml.contains("icon-aug", ignoreCase = true) -> "Augment"
            else -> "Patch Note"
        }
    }

    private fun mapSectionToDelta(sectionLabel: String): DeltaType {
        return when {
            sectionLabel.contains("buff", ignoreCase = true) -> DeltaType.BUFF
            sectionLabel.contains("nerf", ignoreCase = true) -> DeltaType.NERF
            sectionLabel.contains("add", ignoreCase = true) || sectionLabel.contains("new", ignoreCase = true) -> DeltaType.NEW
            else -> DeltaType.ADJUST
        }
    }

    internal fun parseNextDataPatches(html: String): List<PatchUpdateRecord> {
        val nextDataMatch = NEXT_DATA_REGEX.find(html) ?: return emptyList()
        val jsonText = nextDataMatch.groupValues.getOrNull(1) ?: return emptyList()
        val results = mutableListOf<PatchUpdateRecord>()
        runCatching {
            val root = JSONObject(jsonText)
            collectPatchObjectsFromJson(root, results)
        }
        return results
    }

    private fun collectPatchObjectsFromJson(node: Any?, out: MutableList<PatchUpdateRecord>, depth: Int = 0) {
        if (node == null || depth > 8 || out.size >= 12) return
        when (node) {
            is JSONObject -> {
                val versionCandidate = node.optString("version")
                    .ifBlank { node.optString("patch") }
                    .ifBlank { node.optString("patchVersion") }
                    .ifBlank { node.optString("tag") }
                val titleCandidate = node.optString("title")
                    .ifBlank { node.optString("name") }
                val slugCandidate = node.optString("slug")
                val combinedText = "$versionCandidate $titleCandidate $slugCandidate"

                if (versionCandidate.isNotBlank() && PATCH_VERSION_REGEX.containsMatchIn(combinedText)) {
                    val isPbe = combinedText.contains("pbe", ignoreCase = true) ||
                        node.optBoolean("isPbe", false) ||
                        node.optBoolean("pbe", false)
                    val env = if (isPbe) TftEnvironment.PBE else TftEnvironment.LIVE
                    val rawVer = PATCH_VERSION_REGEX.find(combinedText)?.value ?: versionCandidate
                    val normalizedVer = if (isPbe && !rawVer.contains("PBE", ignoreCase = true)) "$rawVer-PBE" else rawVer
                    val dateStr = node.optString("releaseDate")
                        .ifBlank { node.optString("publishedAt") }
                        .ifBlank { node.optString("date") }
                        .ifBlank { if (isPbe) "PBE Live Feed" else "Live Patch" }
                        .take(24)
                    val summary = cleanText(
                        node.optString("summary")
                            .ifBlank { node.optString("description") }
                            .ifBlank { node.optString("headline") }
                    ).ifBlank {
                        "Patch $normalizedVer balance updates."
                    }
                    val url = if (slugCandidate.isNotBlank()) {
                        "$TFTIPS_PATCHES_URL/${slugCandidate.trimStart('/')}"
                    } else TFTIPS_PATCHES_URL

                    out.add(
                        PatchUpdateRecord(
                            id = "tftips_${env.name.lowercase()}_${normalizedVer.replace(Regex("[^a-zA-Z0-9]"), "_")}",
                            environment = env,
                            version = normalizedVer,
                            title = cleanText(titleCandidate).ifBlank { "TFT Patch $normalizedVer" },
                            releaseDate = dateStr,
                            isMajorPatch = !normalizedVer.removeSuffix("-PBE").last().isLetter(),
                            headline = summary,
                            highlights = extractJsonHighlights(node).ifEmpty {
                                listOf("Patch $normalizedVer balance updates")
                            },
                            balanceChanges = emptyList(),
                            trendingCompIds = emptyList(),
                            sourceUrl = url
                        )
                    )
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    collectPatchObjectsFromJson(node.opt(k), out, depth + 1)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length().coerceAtMost(40)) {
                    collectPatchObjectsFromJson(node.opt(i), out, depth + 1)
                }
            }
        }
    }

    private fun extractJsonHighlights(obj: JSONObject): List<String> {
        val list = mutableListOf<String>()
        val arr = obj.optJSONArray("highlights") ?: obj.optJSONArray("changes") ?: obj.optJSONArray("notes")
        if (arr != null) {
            for (i in 0 until arr.length().coerceAtMost(6)) {
                val item = arr.opt(i)
                val text = when (item) {
                    is String -> cleanText(item)
                    is JSONObject -> cleanText(item.optString("title").ifBlank { item.optString("summary") })
                    else -> ""
                }
                if (text.length in 6..180) list.add(text)
            }
        }
        return list
    }

    private fun parseGenericHeadingPatchSections(html: String): List<PatchUpdateRecord> {
        val records = mutableListOf<PatchUpdateRecord>()
        for (match in HEADING_REGEX.findAll(html)) {
            val rawBlock = cleanText(match.groupValues[1])
            if (rawBlock.length !in 3..140) continue
            val verMatch = PATCH_VERSION_REGEX.find(rawBlock) ?: continue
            val ver = verMatch.value.trim()
            val isPbe = rawBlock.contains("pbe", ignoreCase = true)
            val env = if (isPbe) TftEnvironment.PBE else TftEnvironment.LIVE
            val normalizedVer = if (isPbe && !ver.contains("PBE", ignoreCase = true)) "$ver-PBE" else ver

            val snippetStart = (match.range.last + 1).coerceAtMost(html.length)
            val snippetEnd = (snippetStart + 1200).coerceAtMost(html.length)
            val snippetHtml = html.substring(snippetStart, snippetEnd)
            val bulletHighlights = LIST_ITEM_REGEX.findAll(snippetHtml)
                .map { cleanText(it.groupValues[1]) }
                .filter { it.length in 8..180 }
                .take(6)
                .toList()

            val balanceChanges = bulletHighlights.mapIndexedNotNull { idx, line ->
                val arrowMatch = ARROW_CHANGE_REGEX.find(line)
                val delta = mapSectionToDelta(line)
                val target = line.substringBefore(":").substringBefore("—").trim().take(28).ifBlank { "Balance Update" }
                BalanceChangeEntry(
                    id = "tftips_bc_${env.name.lowercase()}_${normalizedVer}_$idx",
                    targetName = target,
                    category = "Champion",
                    deltaType = delta,
                    summary = line.take(110),
                    previousValue = arrowMatch?.groupValues?.getOrNull(1)?.trim().orEmpty(),
                    newValue = arrowMatch?.groupValues?.getOrNull(2)?.trim().orEmpty()
                )
            }

            records.add(
                PatchUpdateRecord(
                    id = "tftips_${env.name.lowercase()}_${normalizedVer.replace(Regex("[^a-zA-Z0-9]"), "_")}",
                    environment = env,
                    version = normalizedVer,
                    title = rawBlock,
                    releaseDate = if (isPbe) "PBE Update" else "Live Release",
                    isMajorPatch = !ver.last().isLetter(),
                    headline = bulletHighlights.firstOrNull()
                        ?: "Latest ${env.label} patch $normalizedVer balance updates.",
                    highlights = bulletHighlights.ifEmpty {
                        listOf("Patch $normalizedVer balance updates")
                    },
                    balanceChanges = balanceChanges,
                    trendingCompIds = emptyList(),
                    sourceUrl = TFTIPS_PATCHES_URL
                )
            )
            if (records.size >= 8) break
        }
        return records
    }

    private fun unescapeHtml(raw: String): String {
        return raw
            .replace("&amp;", "&")
            .replace("&#39;", "'")
            .replace("&#x27;", "'")
            .replace("&apos;", "'")
            .replace("&quot;", "\"")
            .replace("&rarr;", "→")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
    }

    private fun cleanText(raw: String): String {
        return unescapeHtml(raw.replace(HTML_TAG_REGEX, " "))
            .replace(WHITESPACE_REGEX, " ")
            .trim()
    }

    /**
     * Keeps only short numeric/stat diff tokens (e.g. "280/420/650", "1.5s", "35%")
     * for inline stat pills. Full multi-word PBE <ins>/<del> descriptions remain in
     * `summary` so they never stretch patch rows or create vertical blank gaps.
     */
    private fun sanitizeCompactStatValue(raw: String): String {
        val cleaned = cleanText(raw)
        if (cleaned.isBlank()) return ""
        if (cleaned.length > 28) return ""
        val words = cleaned.split(' ').count { it.isNotBlank() }
        if (words > 4) return ""
        return cleaned
    }

    companion object {
        const val TFTIPS_PATCHES_URL = "https://tftips.app/en/patches"

        private val NEXT_DATA_REGEX = Regex(
            """<script[^>]*id=["']__NEXT_DATA__["'][^>]*>(.*?)</script>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val HEADING_REGEX = Regex(
            """<(?:h1|h2|h3|h4)[^>]*>(.*?)</(?:h1|h2|h3|h4)>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val LIST_ITEM_REGEX = Regex(
            """<li[^>]*>(.*?)</li>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val ARROW_CHANGE_REGEX = Regex("""([0-9.%+/\-]+)\s*(?:->|→|⇒)\s*([0-9.%+/\-]+)""")
        private val PATCH_CARD_ANCHOR_REGEX = Regex(
            """<a[^>]*href=["']((?:/en)?/patches/[^"'#?]+)["'][^>]*>(.*?)</a>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val TIME_TAG_REGEX = Regex(
            """<time[^>]*>(.*?)</time>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val UL_ARIA_REGEX = Regex(
            """<ul[^>]*aria-label=["']([^"']+)["'][^>]*>(.*?)</ul>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val LI_TITLE_REGEX = Regex(
            """<li[^>]*title=["']([^"']+)["'][^>]*>(.*?)</li>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val TITLE_REGEX = Regex(
            """<h1[^>]*>(.*?)</h1>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val H2_SECTION_REGEX = Regex(
            """<h2[^>]*>(Buffs|Nerfs|Modified|Adjustments|Added|Removed|New|Reworked)</h2>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val H3_TARGET_REGEX = Regex(
            """<h3[^>]*class=["'][^"']*font-bold[^"']*["'][^>]*>(.*?)</h3>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val DIFF_CHANGE_REGEX = Regex(
            """<span[^>]*class=["'][^"']*tft-diff-change\b[^"']*["'][^>]*>.*?<span[^>]*class=["'][^"']*tft-diff-change-old[^"']*["'][^>]*>(.*?)</span>.*?<span[^>]*class=["'][^"']*tft-diff-change-new[^"']*["'][^>]*>(.*?)</span>.*?</span>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val DIFF_INS_REGEX = Regex(
            """<ins[^>]*class=["'][^"']*tft-diff-ins[^"']*["'][^>]*>(.*?)</ins>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val DIFF_DEL_REGEX = Regex(
            """<del[^>]*class=["'][^"']*tft-diff-del[^"']*["'][^>]*>(.*?)</del>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val RSC_H3_TARGET_REGEX = Regex(
            """"h3"\s*,\s*null\s*,\s*\{\s*"className"\s*:\s*"text-sm font-bold leading-tight"\s*,\s*"children"\s*:\s*"([^"]+)""""
        )
        private val RSC_OLD_VAL_REGEX = Regex(
            """"tft-diff-change-old"\s*,\s*"children"\s*:\s*"([^"]+)""""
        )
        private val RSC_NEW_VAL_REGEX = Regex(
            """"tft-diff-change-new"\s*,\s*"children"\s*:\s*"([^"]+)""""
        )
        private val RSC_INS_VAL_REGEX = Regex(
            """"tft-diff-ins[^"]*"\s*,\s*"children"\s*:\s*\[\s*"\+"\s*,\s*"([^"]+)""""
        )
        private val RSC_CHILDREN_TEXT_REGEX = Regex(
            """"children"\s*:\s*"([^"]+)""""
        )
        private val SET_TRANSITION_REGEX = Regex(
            """Set\s*(\d+)\s*(?:→|->)\s*Set\s*(\d+)""",
            RegexOption.IGNORE_CASE
        )
        private val PATCH_VERSION_REGEX = Regex(
            """\b(?:1[3-9]|2[0-9])\.\d+[a-z]?(?:\s*[-/(]?\s*PBE\)?)?\b""",
            RegexOption.IGNORE_CASE
        )
        private val HTML_TAG_REGEX = Regex("""<[^>]+>""")
        private val WHITESPACE_REGEX = Regex("""\s+""")
    }
}
