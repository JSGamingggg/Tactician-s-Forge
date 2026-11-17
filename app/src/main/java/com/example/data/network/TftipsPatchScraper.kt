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
 * Fetches and parses LIVE and PBE patch notes and newly emerging compositions
 * directly from https://tftips.app/en/patches (with HTML + Next.js JSON extraction).
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
        val parsedFromHtml = parseHtmlPatchSections(mainHtml)

        val allParsed = (parsedFromJson + parsedFromHtml)
            .distinctBy { "${it.environment.name}_${it.version}" }

        // Optionally enrich the top LIVE and top PBE detail pages if linked on /en/patches
        val detailLinks = extractPatchDetailLinks(mainHtml).take(2)
        val detailRecords = detailLinks.mapNotNull { link ->
            val detailHtml = fetchUrl(link.url) ?: return@mapNotNull null
            parseDetailPage(detailHtml, link.url, link.hintEnv, link.hintVersion)
        }

        val combined = (detailRecords + allParsed)
            .distinctBy { "${it.environment.name}_${it.version}" }

        val livePatches = combined.filter { it.environment == TftEnvironment.LIVE }
        val pbePatches = combined.filter { it.environment == TftEnvironment.PBE }

        return TftipsFetchResult(
            livePatchVersion = livePatches.firstOrNull()?.version,
            pbePatchVersion = pbePatches.firstOrNull()?.version,
            livePatchRecords = livePatches,
            pbePatchRecords = pbePatches,
            fetchedFromNetwork = true
        )
    }

    private fun fetchUrl(url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "TacticiansForge-Android/1.0 (+https://tftips.app/en/patches)")
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
                        "Synced from tftips.app/en/patches ($normalizedVer)."
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
                            isMajorPatch = !normalizedVer.last().isLetter(),
                            headline = summary,
                            highlights = extractJsonHighlights(node).ifEmpty {
                                listOf("Synced live from https://tftips.app/en/patches")
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

    internal fun parseHtmlPatchSections(html: String): List<PatchUpdateRecord> {
        val records = mutableListOf<PatchUpdateRecord>()
        val headingMatches = HEADING_OR_LINK_REGEX.findAll(html).toList()

        for (match in headingMatches) {
            val rawBlock = cleanText(match.value)
            if (rawBlock.length < 3 || rawBlock.length > 140) continue
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
                .take(4)
                .toList()

            val balanceChanges = extractBalanceChangesFromText(bulletHighlights, env, normalizedVer)

            records.add(
                PatchUpdateRecord(
                    id = "tftips_${env.name.lowercase()}_${normalizedVer.replace(Regex("[^a-zA-Z0-9]"), "_")}",
                    environment = env,
                    version = normalizedVer,
                    title = rawBlock,
                    releaseDate = if (isPbe) "PBE Update (TFTips)" else "Live Release (TFTips)",
                    isMajorPatch = !ver.last().isLetter(),
                    headline = bulletHighlights.firstOrNull()
                        ?: "Latest ${env.label} patch $normalizedVer details synced from tftips.app/en/patches.",
                    highlights = bulletHighlights.ifEmpty {
                        listOf("Patch $normalizedVer tracked via https://tftips.app/en/patches")
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

    private data class PatchDetailLink(
        val url: String,
        val hintEnv: TftEnvironment,
        val hintVersion: String
    )

    private fun extractPatchDetailLinks(html: String): List<PatchDetailLink> {
        val links = mutableListOf<PatchDetailLink>()
        for (m in PATCH_HREF_REGEX.findAll(html)) {
            val href = m.groupValues[1]
            val contextText = cleanText(m.groupValues[2])
            val verMatch = PATCH_VERSION_REGEX.find("$href $contextText") ?: continue
            val isPbe = href.contains("pbe", ignoreCase = true) || contextText.contains("pbe", ignoreCase = true)
            val env = if (isPbe) TftEnvironment.PBE else TftEnvironment.LIVE
            val fullUrl = if (href.startsWith("http")) href else "https://tftips.app${if (href.startsWith("/")) href else "/$href"}"
            links.add(PatchDetailLink(fullUrl, env, verMatch.value))
        }
        return links.distinctBy { it.url }
    }

    private fun parseDetailPage(
        html: String,
        url: String,
        env: TftEnvironment,
        fallbackVersion: String
    ): PatchUpdateRecord? {
        val nextRecords = parseNextDataPatches(html)
        if (nextRecords.isNotEmpty()) {
            return nextRecords.first().copy(environment = env, sourceUrl = url)
        }
        val titleMatch = TITLE_REGEX.find(html)?.groupValues?.getOrNull(1)?.let(::cleanText)
            ?: "Patch $fallbackVersion"
        val bullets = LIST_ITEM_REGEX.findAll(html)
            .map { cleanText(it.groupValues[1]) }
            .filter { it.length in 10..180 }
            .take(5)
            .toList()
        if (bullets.isEmpty()) return null
        val normalizedVer = if (env == TftEnvironment.PBE && !fallbackVersion.contains("PBE", ignoreCase = true)) {
            "$fallbackVersion-PBE"
        } else fallbackVersion
        return PatchUpdateRecord(
            id = "tftips_${env.name.lowercase()}_${normalizedVer.replace(Regex("[^a-zA-Z0-9]"), "_")}",
            environment = env,
            version = normalizedVer,
            title = titleMatch,
            releaseDate = if (env == TftEnvironment.PBE) "PBE (TFTips)" else "Live (TFTips)",
            isMajorPatch = !fallbackVersion.last().isLetter(),
            headline = bullets.first(),
            highlights = bullets,
            balanceChanges = extractBalanceChangesFromText(bullets, env, normalizedVer),
            trendingCompIds = emptyList(),
            sourceUrl = url
        )
    }

    private fun extractBalanceChangesFromText(
        lines: List<String>,
        env: TftEnvironment,
        ver: String
    ): List<BalanceChangeEntry> {
        return lines.mapIndexedNotNull { idx, line ->
            val arrowMatch = ARROW_CHANGE_REGEX.find(line)
            val delta = when {
                line.contains("buff", ignoreCase = true) || line.contains("increase", ignoreCase = true) -> DeltaType.BUFF
                line.contains("nerf", ignoreCase = true) || line.contains("reduce", ignoreCase = true) -> DeltaType.NERF
                line.contains("new", ignoreCase = true) || line.contains("rework", ignoreCase = true) -> DeltaType.NEW
                else -> DeltaType.ADJUST
            }
            val target = line.substringBefore(":").substringBefore("—").trim().take(28).ifBlank { "Balance Update" }
            if (arrowMatch != null || delta != DeltaType.ADJUST) {
                BalanceChangeEntry(
                    id = "tftips_bc_${env.name.lowercase()}_${ver}_$idx",
                    targetName = target,
                    category = "Patch Note",
                    deltaType = delta,
                    summary = line.take(90),
                    previousValue = arrowMatch?.groupValues?.getOrNull(1)?.trim() ?: "Prev",
                    newValue = arrowMatch?.groupValues?.getOrNull(2)?.trim() ?: "Updated"
                )
            } else null
        }
    }

    private fun cleanText(raw: String): String {
        return raw
            .replace(HTML_TAG_REGEX, " ")
            .replace("&amp;", "&")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&quot;", "\"")
            .replace("&rarr;", "->")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(WHITESPACE_REGEX, " ")
            .trim()
    }

    companion object {
        const val TFTIPS_PATCHES_URL = "https://tftips.app/en/patches"

        private val NEXT_DATA_REGEX = Regex(
            """<script[^>]*id=["']__NEXT_DATA__["'][^>]*>(.*?)</script>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val HEADING_OR_LINK_REGEX = Regex(
            """<(?:h1|h2|h3|h4|a)[^>]*>(.*?)</(?:h1|h2|h3|h4|a)>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val PATCH_HREF_REGEX = Regex(
            """<a[^>]*href=["']([^"']*patches[^"']+)["'][^>]*>(.*?)</a>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val LIST_ITEM_REGEX = Regex(
            """<li[^>]*>(.*?)</li>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val TITLE_REGEX = Regex(
            """<h1[^>]*>(.*?)</h1>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val PATCH_VERSION_REGEX = Regex("""\b(?:1[3-9]|2[0-9])\.\d+[a-z]?(?:\s*[-/]?\s*PBE)?\b""", RegexOption.IGNORE_CASE)
        private val ARROW_CHANGE_REGEX = Regex("""([0-9.%+/\-]+)\s*(?:->|→|⇒)\s*([0-9.%+/\-]+)""")
        private val HTML_TAG_REGEX = Regex("""<[^>]+>""")
        private val WHITESPACE_REGEX = Regex("""\s+""")
    }
}
