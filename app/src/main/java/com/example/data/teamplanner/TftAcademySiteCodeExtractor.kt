package com.example.data.teamplanner

import com.example.data.model.MetaComp
import com.example.data.model.TftEnvironment
import com.example.data.network.RiotApiClientFactory
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

data class TftAcademySiteCodeBundle(
    val directCodesByKey: Map<String, String> = emptyMap(),
    val siteCatalog: TeamPlannerCatalog? = null,
    val detectedSetSuffix: String? = null
) {
    fun lookupDirectCode(compSlug: String?, title: String?, compId: String? = null): String? {
        val candidates = buildList {
            compSlug?.trim()?.takeIf { it.isNotBlank() }?.let { add(TftTeamPlannerCodeEncoder.normalizeKey(it)) }
            title?.trim()?.takeIf { it.isNotBlank() }?.let { add(TftTeamPlannerCodeEncoder.normalizeKey(it)) }
            compId?.trim()?.takeIf { it.isNotBlank() }?.let { id ->
                val stripped = id
                    .removePrefix("tftacademy_pbe_")
                    .removePrefix("tftacademy_live_")
                add(TftTeamPlannerCodeEncoder.normalizeKey(stripped))
                add(TftTeamPlannerCodeEncoder.normalizeKey(id))
            }
        }
        for (key in candidates) {
            val found = directCodesByKey[key]
            if (!found.isNullOrBlank()) return found
        }
        return null
    }

    fun isEmpty(): Boolean = directCodesByKey.isEmpty() && siteCatalog == null
}

/**
 * Extracts the exact Team Planner copy codes directly from TFTAcademy (`tftacademy.com/tierlist/comps`
 * and `tftacademy.com/tierlist/comps/set19`) — matching the site's own Copy Team Code button.
 *
 * Inspects (in priority order):
 * 1. Already-fetched page HTML (`<script data-sveltekit-fetched>`, inline SvelteKit hydration `data = [...]`,
 *    and DOM copy-code attributes).
 * 2. SvelteKit `/__data.json` devalue payloads (layout + page load nodes).
 * 3. SvelteKit `/_app/immutable/` ES module JS chunks (`<link rel="modulepreload">`, `<script src>`, `import(...)`)
 *    for embedded champion-to-planner-code tables, set suffixes, or upstream JSON endpoints the site fetches.
 */
object TftAcademySiteCodeExtractor {

    private const val TFTACADEMY_ORIGIN = "https://tftacademy.com"
    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/131.0 Mobile Safari/537.36"

    private val SVELTEKIT_FETCHED_REGEX = Regex(
        """<script[^>]*data-sveltekit-fetched[^>]*>(.*?)</script>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    private val SVELTEKIT_FETCHED_URL_REGEX = Regex(
        """<script[^>]*data-sveltekit-fetched[^>]*data-url=["']([^"']+)["']""",
        RegexOption.IGNORE_CASE
    )
    private val MODULE_PRELOAD_REGEX = Regex(
        """<link[^>]+rel=["']modulepreload["'][^>]+href=["']([^"']+\.js(?:\?[^"']*)?)["']|<link[^>]+href=["']([^"']+\.js(?:\?[^"']*)?)["'][^>]+rel=["']modulepreload["']""",
        RegexOption.IGNORE_CASE
    )
    private val SCRIPT_SRC_REGEX = Regex(
        """<script[^>]+src=["']([^"']+\.js(?:\?[^"']*)?)["']""",
        RegexOption.IGNORE_CASE
    )
    private val DYNAMIC_IMPORT_REGEX = Regex(
        """import\(\s*["']((?:\./|\.\./|/)?[^"']*?_app/immutable/[^"']+\.js(?:\?[^"']*)?)["']\s*\)"""
    )
    private val INLINE_SCRIPT_REGEX = Regex(
        """<script(?![^>]*\bsrc=)[^>]*>(.*?)</script>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )
    private val EMBEDDED_V02_REGEX = Regex(
        """0[2-9][0-9a-fA-F]{30}(?:TFTSet|TFT)[A-Za-z0-9_-]+"""
    )
    private val SET_SUFFIX_REGEX = Regex(
        """["'](TFTSet\d+[A-Za-z0-9_-]*)["']"""
    )
    private val JS_DIRECT_CODE_MAP_ENTRY_REGEX = Regex(
        """["']?((?:TFT|DA|PT)\d{2}_[A-Za-z0-9_]+|DA_[A-Za-z0-9_]+(?:18|19))["']?\s*:\s*(?:["']([0-9a-fA-F]{2,3})["']|0x([0-9a-fA-F]{2,3})\b|(\d{2,4})\b)"""
    )
    private val JS_CHAMP_OBJECT_BLOCK_REGEX = Regex(
        """\{[^{}]{0,420}?["']?(?:apiName|character_id|characterId|id)["']?\s*:\s*["']((?:TFT|DA|PT)\d{2}_[A-Za-z0-9_]+|DA_[A-Za-z0-9_]+(?:18|19))["'][^{}]{0,420}?\}"""
    )
    private val JS_OBJ_PLANNER_CODE_FIELD_REGEX = Regex(
        """["']?(?:team_planner_code|teamPlannerCode|planner_code|plannerCode|team_planner_id|teamPlannerId|plannerId|hexCode|hex_code|code)["']?\s*:\s*(?:["']([0-9a-fA-F]{2,3})["']|0x([0-9a-fA-F]{2,3})\b|(\d{2,4})\b)"""
    )
    private val JS_OBJ_NAME_FIELD_REGEX = Regex(
        """["']?(?:display_name|displayName|name)["']?\s*:\s*["']([^"']{2,40})["']"""
    )
    private val JS_COMP_TITLE_OR_SLUG_REGEX = Regex(
        """["']?(?:compSlug|slug|title|compTitle|guideTitle|name)["']?\s*:\s*["']([^"']{3,80})["']"""
    )
    private val JS_DATA_URL_REGEX = Regex(
        """["'](https://[A-Za-z0-9._/-]+(?:teamplanner|planner|tftchampion|champions|units|set1[89]|TFTSet1[89]|lookups|comps)[^"'\s<>]{0,160}|/(?:api|data)/[A-Za-z0-9._/?=&-]{3,140})["']""",
        RegexOption.IGNORE_CASE
    )
    private val PRIORITY_CODE_KEYS = listOf(
        "teamCode",
        "team_code",
        "teamPlannerCode",
        "team_planner_code",
        "plannerCode",
        "planner_code",
        "teamBuilderCode",
        "finalCompCode",
        "boardCode",
        "compCode",
        "riotCode",
        "importCode",
        "copyCode",
        "inGameCode",
        "code"
    )

    /**
     * Full site extraction using already-fetched [pageHtml] plus SvelteKit `/__data.json`
     * and (if still needed) SvelteKit JS chunks / referenced data endpoints.
     */
    fun extractFromPageAndSite(
        pageUrl: String,
        pageHtml: String?,
        expectedSetNumber: Int,
        client: OkHttpClient = RiotApiClientFactory.publicOkHttp,
        extraDisplayNames: Map<String, String> = emptyMap(),
        includeRemoteAssets: Boolean = true
    ): TftAcademySiteCodeBundle {
        val directCodes = LinkedHashMap<String, String>()
        var bestCatalog: TeamPlannerCatalog? = null
        var detectedSuffix: String? = null

        fun considerCatalog(candidate: TeamPlannerCatalog?) {
            if (candidate == null) return
            val hasExpectedSet = hasV02Set(candidate, expectedSetNumber)
            if (hasExpectedSet) {
                if (bestCatalog == null || !hasV02Set(bestCatalog!!, expectedSetNumber)) {
                    bestCatalog = candidate
                }
            } else if (bestCatalog == null && candidate.sets.values.any { it.usesVersion02 }) {
                bestCatalog = candidate
            }
        }

        fun scanStructuredNode(root: Any?) {
            if (root == null) return
            extractDirectCompCodesFromJson(root, expectedSetNumber, directCodes)
            if (!hasV02Set(bestCatalog, expectedSetNumber)) {
                val jsonText = when (root) {
                    is JSONObject -> root.toString()
                    is JSONArray -> root.toString()
                    else -> null
                }
                if (jsonText != null) {
                    considerCatalog(
                        TftTeamPlannerCodeEncoder.parseCommunityDragonJson(
                            rawJson = jsonText,
                            isFromRiotCommunityDragon = true,
                            extraDisplayNameByNormalizedId = extraDisplayNames,
                            requireExplicitCodes = true
                        )
                    )
                }
            }
        }

        // 1. Inspect already-fetched HTML (zero extra network cost)
        if (!pageHtml.isNullOrBlank()) {
            detectedSuffix = detectSetSuffixForSet(pageHtml, expectedSetNumber) ?: detectedSuffix
            extractSvelteKitFetchedPayloads(pageHtml).forEach { jsonStr ->
                parseJsonRoot(jsonStr)?.let(::scanStructuredNode)
            }
            INLINE_SCRIPT_REGEX.findAll(pageHtml).forEach { m ->
                val scriptBody = m.groupValues.getOrNull(1).orEmpty()
                if (scriptBody.length > 40) {
                    extractDirectCodesFromJsText(scriptBody, expectedSetNumber, directCodes)
                    if (!hasV02Set(bestCatalog, expectedSetNumber)) {
                        considerCatalog(
                            extractCatalogFromJsText(
                                jsText = scriptBody,
                                expectedSetNumber = expectedSetNumber,
                                detectedSetSuffix = detectedSuffix,
                                extraDisplayNames = extraDisplayNames
                            )
                        )
                    }
                }
            }
        }

        if (!includeRemoteAssets) {
            return TftAcademySiteCodeBundle(
                directCodesByKey = directCodes,
                siteCatalog = bestCatalog,
                detectedSetSuffix = detectedSuffix
            )
        }

        val httpClient = client.newBuilder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        fun fetchText(url: String): String? = runCatching {
            val req = Request.Builder()
                .url(url)
                .cacheControl(CacheControl.FORCE_NETWORK)
                .header("Cache-Control", "no-cache")
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("Accept", "application/json, text/javascript, application/javascript, text/html, */*")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string()?.takeIf { it.isNotBlank() } else null
            }
        }.getOrNull()

        // 2. Fetch SvelteKit `/__data.json` for this page (returns server load() payload including layout & page data)
        val cleanPageUrl = pageUrl.substringBefore('?').trimEnd('/')
        if (cleanPageUrl.contains("tftacademy.com", ignoreCase = true) && !cleanPageUrl.endsWith("__data.json")) {
            val dataJsonUrl = "$cleanPageUrl/__data.json"
            fetchText(dataJsonUrl)?.let { body ->
                detectedSuffix = detectSetSuffixForSet(body, expectedSetNumber) ?: detectedSuffix
                decodeSvelteKitDataJsonNodes(body).forEach(::scanStructuredNode)
            }
        }

        // 3. If we already have a verified v02 catalog for expectedSetNumber OR abundant direct codes, we're done!
        if (hasV02Set(bestCatalog, expectedSetNumber)) {
            return TftAcademySiteCodeBundle(
                directCodesByKey = directCodes,
                siteCatalog = bestCatalog,
                detectedSetSuffix = detectedSuffix
            )
        }

        // 4. Inspect SvelteKit JS chunks (`<link rel="modulepreload">` / `<script src>` / `import(...)`)
        val htmlToInspect = pageHtml ?: fetchText(pageUrl)
        if (!htmlToInspect.isNullOrBlank()) {
            val discoveredUrls = LinkedHashSet<String>()
            SVELTEKIT_FETCHED_URL_REGEX.findAll(htmlToInspect).forEach { m ->
                val rawUrl = m.groupValues.getOrNull(1)?.trim().orEmpty()
                if (rawUrl.isNotBlank() && !rawUrl.contains("/api/tierlist/comps")) {
                    discoveredUrls.add(resolveUrl(pageUrl, rawUrl))
                }
            }

            val chunkUrls = collectPrioritizedJsChunkUrls(pageUrl, htmlToInspect)
            val secondaryChunkUrls = LinkedHashSet<String>()

            for (chunkUrl in chunkUrls.take(14)) {
                val jsBody = fetchText(chunkUrl) ?: continue
                detectedSuffix = detectSetSuffixForSet(jsBody, expectedSetNumber) ?: detectedSuffix
                extractDirectCodesFromJsText(jsBody, expectedSetNumber, directCodes)
                if (!hasV02Set(bestCatalog, expectedSetNumber)) {
                    considerCatalog(
                        extractCatalogFromJsText(
                            jsText = jsBody,
                            expectedSetNumber = expectedSetNumber,
                            detectedSetSuffix = detectedSuffix,
                            extraDisplayNames = extraDisplayNames
                        )
                    )
                }
                // Discover any upstream JSON/API URLs TFTAcademy's copy button fetches
                JS_DATA_URL_REGEX.findAll(jsBody).forEach { m ->
                    val raw = m.groupValues.getOrNull(1)?.trim().orEmpty()
                    if (raw.isNotBlank() && !raw.endsWith(".png") && !raw.endsWith(".webp") && !raw.endsWith(".svg")) {
                        discoveredUrls.add(resolveUrl(pageUrl, raw))
                    }
                }
                // Discover dynamic chunk imports inside entry/page modules
                DYNAMIC_IMPORT_REGEX.findAll(jsBody).forEach { m ->
                    val raw = m.groupValues.getOrNull(1)?.trim().orEmpty()
                    if (raw.isNotBlank()) {
                        secondaryChunkUrls.add(resolveUrl(chunkUrl, raw))
                    }
                }
                if (hasV02Set(bestCatalog, expectedSetNumber)) break
            }

            if (!hasV02Set(bestCatalog, expectedSetNumber)) {
                for (chunkUrl in secondaryChunkUrls.filterNot { it in chunkUrls }.take(6)) {
                    val jsBody = fetchText(chunkUrl) ?: continue
                    detectedSuffix = detectSetSuffixForSet(jsBody, expectedSetNumber) ?: detectedSuffix
                    extractDirectCodesFromJsText(jsBody, expectedSetNumber, directCodes)
                    considerCatalog(
                        extractCatalogFromJsText(
                            jsText = jsBody,
                            expectedSetNumber = expectedSetNumber,
                            detectedSetSuffix = detectedSuffix,
                            extraDisplayNames = extraDisplayNames
                        )
                    )
                    JS_DATA_URL_REGEX.findAll(jsBody).forEach { m ->
                        val raw = m.groupValues.getOrNull(1)?.trim().orEmpty()
                        if (raw.isNotBlank() && !raw.endsWith(".png") && !raw.endsWith(".webp") && !raw.endsWith(".svg")) {
                            discoveredUrls.add(resolveUrl(pageUrl, raw))
                        }
                    }
                    if (hasV02Set(bestCatalog, expectedSetNumber)) break
                }
            }

            if (!hasV02Set(bestCatalog, expectedSetNumber)) {
                val prioritizedDataUrls = discoveredUrls
                    .filter { url ->
                        val lower = url.lowercase(Locale.US)
                        lower.contains("planner") ||
                            lower.contains("champion") ||
                            lower.contains("unit") ||
                            lower.contains("set$expectedSetNumber") ||
                            lower.contains("tftset$expectedSetNumber") ||
                            lower.contains("communitydragon") ||
                            lower.contains("metatft") ||
                            lower.contains("/api/") ||
                            lower.contains("/data/")
                    }
                    .take(6)

                for (dataUrl in prioritizedDataUrls) {
                    val dataBody = fetchText(dataUrl) ?: continue
                    parseJsonRoot(dataBody)?.let(::scanStructuredNode)
                    if (hasV02Set(bestCatalog, expectedSetNumber)) break
                }
            }
        }

        return TftAcademySiteCodeBundle(
            directCodesByKey = directCodes,
            siteCatalog = bestCatalog,
            detectedSetSuffix = detectedSuffix
        )
    }

    /**
     * Fetches a single comp's dedicated TFTAcademy guide page & `/__data.json` to extract
     * the site's exact copy code for [comp].
     */
    fun extractSingleCompCode(
        comp: MetaComp,
        expectedSetNumber: Int,
        client: OkHttpClient = RiotApiClientFactory.publicOkHttp
    ): String? {
        val httpClient = client.newBuilder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
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
            .removePrefix("tftacademy_pbe_")
            .removePrefix("tftacademy_live_")
            .replace('_', '-')
            .trim('-')
        val titleSlug = comp.title.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
        val normTitle = TftTeamPlannerCodeEncoder.normalizeKey(comp.title)
        val normSlug = TftTeamPlannerCodeEncoder.normalizeKey(rawSlug)

        val candidatePages = LinkedHashSet<String>()
        val cleanSource = comp.sourceUrl.trim().substringBefore('?').trimEnd('/')
        if (cleanSource.contains("tftacademy.com/tierlist/comps/", ignoreCase = true) &&
            !cleanSource.endsWith("/tierlist/comps", ignoreCase = true) &&
            !cleanSource.endsWith("/tierlist/comps/set$expectedSetNumber", ignoreCase = true)
        ) {
            candidatePages.add(cleanSource)
        }
        listOf(rawSlug, titleSlug).filter { it.length >= 3 }.distinct().forEach { slug ->
            if (comp.environment == TftEnvironment.PBE) {
                candidatePages.add("$TFTACADEMY_ORIGIN/tierlist/comps/set$expectedSetNumber/$slug")
            }
            candidatePages.add("$TFTACADEMY_ORIGIN/tierlist/comps/$slug")
        }

        for (page in candidatePages.take(3)) {
            // Try SvelteKit `/__data.json` first (fast, structured JSON)
            fetchText("$page/__data.json")?.let { dataBody ->
                val map = LinkedHashMap<String, String>()
                decodeSvelteKitDataJsonNodes(dataBody).forEach { node ->
                    extractDirectCompCodesFromJson(node, expectedSetNumber, map)
                }
                map[normSlug]?.let { return it }
                map[normTitle]?.let { return it }
                if (map.size == 1) return map.values.first()
                val uniqueCodes = EMBEDDED_V02_REGEX.findAll(dataBody)
                    .map { it.value.trim() }
                    .filter { TftTeamPlannerCodeEncoder.isValidTeamPlannerCode(it, expectedSetNumber) }
                    .distinct()
                    .toList()
                if (uniqueCodes.size == 1) return uniqueCodes.first()
            }

            // Try guide HTML page
            fetchText(page)?.let { html ->
                val map = LinkedHashMap<String, String>()
                extractSvelteKitFetchedPayloads(html).forEach { payload ->
                    parseJsonRoot(payload)?.let { node ->
                        extractDirectCompCodesFromJson(node, expectedSetNumber, map)
                    }
                }
                extractDirectCodesFromJsText(html, expectedSetNumber, map)
                map[normSlug]?.let { return it }
                map[normTitle]?.let { return it }
                val uniqueCodes = EMBEDDED_V02_REGEX.findAll(html)
                    .map { it.value.trim() }
                    .filter { TftTeamPlannerCodeEncoder.isValidTeamPlannerCode(it, expectedSetNumber) }
                    .distinct()
                    .toList()
                if (uniqueCodes.size == 1) return uniqueCodes.first()
            }
        }
        return null
    }

    /**
     * Decodes a SvelteKit `/__data.json` response (`{"type":"data","nodes":[{"type":"data","data":[...]}]}`)
     * by unflattening every devalue pool into standard `JSONObject` / `JSONArray` trees.
     */
    internal fun decodeSvelteKitDataJsonNodes(rawJson: String): List<Any> {
        val out = mutableListOf<Any>()
        runCatching {
            val root = JSONObject(rawJson.trim())
            val nodes = root.optJSONArray("nodes") ?: return@runCatching
            for (i in 0 until nodes.length()) {
                val nodeObj = nodes.optJSONObject(i) ?: continue
                val dataPool = nodeObj.optJSONArray("data") ?: continue
                if (dataPool.length() > 0) {
                    unflattenSvelteKitDevalue(dataPool)?.let { out.add(it) }
                }
            }
        }
        return out
    }

    /**
     * Spec-compliant SvelteKit `devalue.unflatten` decoder for `JSONArray` pools.
     * Index 0 is the root value; integer values inside objects/arrays reference indices in [pool].
     */
    internal fun unflattenSvelteKitDevalue(pool: JSONArray): Any? {
        if (pool.length() == 0) return null
        val memo = HashMap<Int, Any?>()
        val visiting = HashSet<Int>()

        fun hydrate(index: Int): Any? {
            if (index < 0 || index >= pool.length()) return null
            if (memo.containsKey(index)) return memo[index]
            if (!visiting.add(index)) return null

            val raw = pool.opt(index)
            val result: Any? = when (raw) {
                null, JSONObject.NULL -> null
                is JSONObject -> {
                    val obj = JSONObject()
                    val keys = raw.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val ref = raw.opt(k)
                        val hydratedVal = if (ref is Number) {
                            hydrate(ref.toInt())
                        } else {
                            ref
                        }
                        if (hydratedVal != null) {
                            obj.put(k, hydratedVal)
                        }
                    }
                    obj
                }
                is JSONArray -> {
                    if (raw.length() >= 2 && raw.opt(0) is String) {
                        val typeTag = raw.optString(0)
                        when (typeTag) {
                            "Date", "URL", "BigInt" -> raw.opt(1)?.toString()
                            "Set" -> {
                                val arr = JSONArray()
                                for (i in 1 until raw.length()) {
                                    val el = raw.opt(i)
                                    val v = if (el is Number) hydrate(el.toInt()) else el
                                    if (v != null) arr.put(v)
                                }
                                arr
                            }
                            "Map" -> {
                                val obj = JSONObject()
                                var i = 1
                                while (i + 1 < raw.length()) {
                                    val kRef = raw.opt(i)
                                    val vRef = raw.opt(i + 1)
                                    val k = (if (kRef is Number) hydrate(kRef.toInt()) else kRef)?.toString()
                                    val v = if (vRef is Number) hydrate(vRef.toInt()) else vRef
                                    if (!k.isNullOrBlank() && v != null) {
                                        obj.put(k, v)
                                    }
                                    i += 2
                                }
                                obj
                            }
                            else -> {
                                val arr = JSONArray()
                                for (i in 0 until raw.length()) {
                                    val el = raw.opt(i)
                                    val v = if (el is Number) hydrate(el.toInt()) else el
                                    if (v != null) arr.put(v)
                                }
                                arr
                            }
                        }
                    } else {
                        val arr = JSONArray()
                        for (i in 0 until raw.length()) {
                            val el = raw.opt(i)
                            val v = if (el is Number) hydrate(el.toInt()) else el
                            if (v != null) arr.put(v)
                        }
                        arr
                    }
                }
                else -> raw
            }

            visiting.remove(index)
            memo[index] = result
            return result
        }

        return hydrate(0)
    }

    /**
     * Extracts champion-to-planner-code tables embedded inside SvelteKit / Vite JS bundles
     * (supporting both unquoted minified JS keys and quoted JSON keys).
     */
    internal fun extractCatalogFromJsText(
        jsText: String,
        expectedSetNumber: Int,
        detectedSetSuffix: String? = null,
        extraDisplayNames: Map<String, String> = emptyMap()
    ): TeamPlannerCatalog? {
        if (jsText.isBlank()) return null
        val setKey = detectedSetSuffix
            ?.takeIf { it.contains(expectedSetNumber.toString()) }
            ?: "TFTSet$expectedSetNumber"

        val entriesByCharId = LinkedHashMap<String, Pair<String, Any>>()

        // Pattern A: Direct map `{ TFT19_Ziggs: "4b0", ... }` or `{ "TFT19_Ziggs": 1200, ... }`
        JS_DIRECT_CODE_MAP_ENTRY_REGEX.findAll(jsText).forEach { m ->
            val charId = m.groupValues[1].trim()
            if (!matchesSetNumberPrefix(charId, expectedSetNumber)) return@forEach
            val hexStr = m.groupValues[2].trim()
            val hexLit = m.groupValues[3].trim()
            val numStr = m.groupValues[4].trim()
            val codeVal: Any = when {
                hexStr.isNotEmpty() -> hexStr
                hexLit.isNotEmpty() -> hexLit.toIntOrNull(16) ?: return@forEach
                else -> numStr.toIntOrNull() ?: return@forEach
            }
            entriesByCharId.putIfAbsent(charId, charId to codeVal)
        }

        // Pattern B: Minified champion objects `{ apiName: "TFT19_Ziggs", name: "Ziggs", teamPlannerCode: "4b0" }`
        JS_CHAMP_OBJECT_BLOCK_REGEX.findAll(jsText).forEach { m ->
            val block = m.value
            val charId = m.groupValues[1].trim()
            if (!matchesSetNumberPrefix(charId, expectedSetNumber)) return@forEach
            val codeMatch = JS_OBJ_PLANNER_CODE_FIELD_REGEX.find(block) ?: return@forEach
            val hexStr = codeMatch.groupValues[1].trim()
            val hexLit = codeMatch.groupValues[2].trim()
            val numStr = codeMatch.groupValues[3].trim()
            val codeVal: Any = when {
                hexStr.isNotEmpty() -> hexStr
                hexLit.isNotEmpty() -> hexLit.toIntOrNull(16) ?: return@forEach
                else -> numStr.toIntOrNull() ?: return@forEach
            }
            val displayName = JS_OBJ_NAME_FIELD_REGEX.find(block)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            entriesByCharId[charId] = displayName.ifBlank { charId } to codeVal
        }

        if (entriesByCharId.size < 4) return null

        val arr = JSONArray()
        entriesByCharId.forEach { (charId, pair) ->
            val (displayName, codeVal) = pair
            arr.put(
                JSONObject().apply {
                    put("character_id", charId)
                    put("display_name", displayName)
                    put("team_planner_code", codeVal)
                }
            )
        }
        val wrapper = JSONObject().apply {
            put(setKey, arr)
        }
        return TftTeamPlannerCodeEncoder.parseCommunityDragonJson(
            rawJson = wrapper.toString(),
            isFromRiotCommunityDragon = true,
            extraDisplayNameByNormalizedId = extraDisplayNames,
            requireExplicitCodes = true
        )
    }

    /**
     * Walks a JSON tree and maps every comp's normalized `compSlug` and `title` to its exact
     * v02 Team Planner copy code if present inside that comp object.
     */
    internal fun extractDirectCompCodesFromJson(
        node: Any?,
        expectedSetNumber: Int,
        out: MutableMap<String, String>,
        depth: Int = 0
    ) {
        if (node == null || depth > 14) return
        when (node) {
            is JSONObject -> {
                val title = listOf(
                    node.optString("title"),
                    node.optString("compTitle"),
                    node.optString("guideTitle"),
                    node.optString("name"),
                    node.optString("compName")
                ).firstOrNull { it.isNotBlank() && !it.equals("null", ignoreCase = true) }.orEmpty()
                val slug = listOf(
                    node.optString("compSlug"),
                    node.optString("slug")
                ).firstOrNull { it.isNotBlank() && !it.equals("null", ignoreCase = true) }.orEmpty()

                if (title.isNotBlank() || slug.isNotBlank()) {
                    val code = findCodeInCompObject(node, expectedSetNumber, 0)
                    if (code != null) {
                        if (slug.isNotBlank()) {
                            out.putIfAbsent(TftTeamPlannerCodeEncoder.normalizeKey(slug), code)
                        }
                        if (title.isNotBlank()) {
                            out.putIfAbsent(TftTeamPlannerCodeEncoder.normalizeKey(title), code)
                        }
                    }
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    extractDirectCompCodesFromJson(node.opt(keys.next()), expectedSetNumber, out, depth + 1)
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length().coerceAtMost(500)) {
                    extractDirectCompCodesFromJson(node.opt(i), expectedSetNumber, out, depth + 1)
                }
            }
        }
    }

    private fun findCodeInCompObject(node: Any?, expectedSetNumber: Int, depth: Int): String? {
        if (node == null || depth > 4) return null
        return when (node) {
            is String -> TftTeamPlannerCodeEncoder.extractFirstValidV02Code(node, expectedSetNumber)
            is JSONObject -> {
                for (key in PRIORITY_CODE_KEYS) {
                    val v = node.optString(key).trim()
                    if (v.isNotEmpty() && TftTeamPlannerCodeEncoder.isValidTeamPlannerCode(v, expectedSetNumber)) {
                        return v
                    }
                }
                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    // Don't descend into sibling arrays of other comps
                    if (k == "guides" || k == "comps" || k == "relatedComps") continue
                    findCodeInCompObject(node.opt(k), expectedSetNumber, depth + 1)?.let { return it }
                }
                null
            }
            is JSONArray -> {
                for (i in 0 until node.length().coerceAtMost(24)) {
                    findCodeInCompObject(node.opt(i), expectedSetNumber, depth + 1)?.let { return it }
                }
                null
            }
            else -> null
        }
    }

    /**
     * Scans JS / inline SvelteKit hydration text for `02...TFTSet<N>` codes and pairs each code
     * with the nearest preceding/nearby `compSlug`, `slug`, or `title` property.
     */
    internal fun extractDirectCodesFromJsText(
        jsText: String,
        expectedSetNumber: Int,
        out: MutableMap<String, String>
    ) {
        for (match in EMBEDDED_V02_REGEX.findAll(jsText)) {
            val code = match.value.trim()
            if (!TftTeamPlannerCodeEncoder.isValidTeamPlannerCode(code, expectedSetNumber)) continue
            val start = (match.range.first - 600).coerceAtLeast(0)
            val end = (match.range.last + 240).coerceAtMost(jsText.length)
            val beforeWindow = jsText.substring(start, match.range.first)
            val nearbyMatches = JS_COMP_TITLE_OR_SLUG_REGEX.findAll(beforeWindow).toList()
            if (nearbyMatches.isNotEmpty()) {
                nearbyMatches.takeLast(2).forEach { m ->
                    val label = m.groupValues[1].trim()
                    if (label.isNotBlank()) {
                        out.putIfAbsent(TftTeamPlannerCodeEncoder.normalizeKey(label), code)
                    }
                }
            } else {
                val afterWindow = jsText.substring(match.range.last + 1, end)
                JS_COMP_TITLE_OR_SLUG_REGEX.find(afterWindow)?.groupValues?.getOrNull(1)?.trim()?.let { label ->
                    if (label.isNotBlank()) {
                        out.putIfAbsent(TftTeamPlannerCodeEncoder.normalizeKey(label), code)
                    }
                }
            }
        }
    }

    private fun extractSvelteKitFetchedPayloads(html: String): List<String> {
        val payloads = mutableListOf<String>()
        SVELTEKIT_FETCHED_REGEX.findAll(html).forEach { m ->
            val rawEnvelope = m.groupValues.getOrNull(1)?.trim().orEmpty()
            if (rawEnvelope.isEmpty()) return@forEach
            runCatching {
                val envelope = JSONObject(rawEnvelope)
                val inner = envelope.optString("body").trim()
                if (inner.startsWith("{") || inner.startsWith("[")) {
                    payloads.add(inner)
                } else {
                    payloads.add(rawEnvelope)
                }
            }
        }
        return payloads
    }

    private fun collectPrioritizedJsChunkUrls(pageUrl: String, html: String): List<String> {
        val rawUrls = LinkedHashSet<String>()
        MODULE_PRELOAD_REGEX.findAll(html).forEach { m ->
            val href = m.groupValues[1].ifBlank { m.groupValues[2] }.trim()
            if (href.isNotBlank()) rawUrls.add(resolveUrl(pageUrl, href))
        }
        SCRIPT_SRC_REGEX.findAll(html).forEach { m ->
            val src = m.groupValues[1].trim()
            if (src.isNotBlank()) rawUrls.add(resolveUrl(pageUrl, src))
        }
        DYNAMIC_IMPORT_REGEX.findAll(html).forEach { m ->
            val imp = m.groupValues[1].trim()
            if (imp.isNotBlank()) rawUrls.add(resolveUrl(pageUrl, imp))
        }

        return rawUrls
            .filter { url ->
                val lower = url.lowercase(Locale.US)
                lower.contains("tftacademy.com") &&
                    !lower.contains("analytics") &&
                    !lower.contains("gtag") &&
                    !lower.contains("ads") &&
                    !lower.contains("clarity")
            }
            .sortedByDescending { url ->
                val lower = url.lowercase(Locale.US)
                var score = 0
                if (lower.contains("planner") || lower.contains("builder") || lower.contains("code")) score += 50
                if (lower.contains("comp") || lower.contains("tierlist") || lower.contains("guide")) score += 40
                if (lower.contains("champ") || lower.contains("unit") || lower.contains("set19") || lower.contains("set18")) score += 40
                if (lower.contains("/nodes/")) score += 30
                if (lower.contains("/chunks/")) score += 20
                if (lower.contains("/entry/")) score += 5
                score
            }
    }

    private fun detectSetSuffixForSet(text: String, expectedSetNumber: Int): String? {
        return SET_SUFFIX_REGEX.findAll(text)
            .map { it.groupValues[1] }
            .firstOrNull { suffix ->
                Regex("""TFTSet(\d+)""", RegexOption.IGNORE_CASE)
                    .find(suffix)?.groupValues?.getOrNull(1)?.toIntOrNull() == expectedSetNumber
            }
    }

    private fun matchesSetNumberPrefix(charId: String, expectedSetNumber: Int): Boolean {
        val target = expectedSetNumber.toString()
        val upper = charId.uppercase(Locale.US)
        return upper.startsWith("TFT${target}_") ||
            upper.startsWith("DA_${target}_") ||
            upper.startsWith("PT${target}_") ||
            (upper.startsWith("DA_") && upper.endsWith(target))
    }

    private fun hasV02Set(catalog: TeamPlannerCatalog?, expectedSetNumber: Int): Boolean {
        if (catalog == null) return false
        return catalog.sets.values.any { mapping ->
            mapping.usesVersion02 && mapping.setNumber == expectedSetNumber && mapping.entries.size >= 4
        }
    }

    private fun parseJsonRoot(text: String): Any? = runCatching {
        val trimmed = text.trim()
        when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            trimmed.startsWith("{") -> JSONObject(trimmed)
            else -> null
        }
    }.getOrNull()

    private fun resolveUrl(baseUrl: String, ref: String): String {
        val clean = ref.trim()
        if (clean.startsWith("http://", ignoreCase = true) || clean.startsWith("https://", ignoreCase = true)) {
            return clean
        }
        if (clean.startsWith("//")) return "https:$clean"
        if (clean.startsWith("/")) return "$TFTACADEMY_ORIGIN$clean"
        val baseDir = baseUrl.substringBefore('?').substringBeforeLast('/')
        return runCatching {
            java.net.URI("$baseDir/").resolve(clean).toString()
        }.getOrElse { "$TFTACADEMY_ORIGIN/$clean" }
    }
}
