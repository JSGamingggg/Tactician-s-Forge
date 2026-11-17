package com.example.data.network

import com.example.data.model.HexUnitPlacement
import com.example.data.model.MetaComp
import com.example.data.model.TftEnvironment
import com.example.data.model.TierRank
import com.example.ui.components.TftAssetIconResolver
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class TftacticsPbeFetchResult(
    val pbeComps: List<MetaComp>,
    val detectedPbeSet: String?,
    val pbeSetNumber: Int?,
    val pbeChampionIcons: Map<String, String>,
    val pbeChampionCosts: Map<String, Int>,
    val pbeChampionTraits: Map<String, List<String>>,
    val pbeTraitIcons: Map<String, String>,
    val fetchedFromNetwork: Boolean
)

/**
 * Fetches PBE team compositions (and Set champion/trait metadata & icon URLs)
 * exclusively from https://tftactics.gg/tierlist/team-comps/ and its linked React bundle.
 */
class TftacticsCompsScraper(
    private val client: OkHttpClient = RiotApiClientFactory.publicOkHttp
) {
    fun fetchPbeTeamComps(
        pbePatchVersion: String,
        liveSetNumber: Int? = 18
    ): TftacticsPbeFetchResult {
        val html = fetchUrl(TFTACTICS_COMPS_URL)
            ?: return TftacticsPbeFetchResult(
                pbeComps = emptyList(),
                detectedPbeSet = null,
                pbeSetNumber = null,
                pbeChampionIcons = emptyMap(),
                pbeChampionCosts = emptyMap(),
                pbeChampionTraits = emptyMap(),
                pbeTraitIcons = emptyMap(),
                fetchedFromNetwork = false
            )

        val mainJsUrl = findMainBundleUrl(html)
        val mainJs = mainJsUrl?.let(::fetchUrl)

        val parsed = if (!mainJs.isNullOrBlank()) {
            parseBundleAndHtml(
                html = html,
                jsBundle = mainJs,
                pbePatchVersion = pbePatchVersion,
                liveSetNumber = liveSetNumber
            )
        } else {
            val htmlComps = parseHtmlTeamComps(html, pbePatchVersion, targetSetNumber = null)
            TftacticsPbeFetchResult(
                pbeComps = htmlComps,
                detectedPbeSet = detectSetBannerLabel(html, null),
                pbeSetNumber = detectTargetPbeSet(html, emptyList(), liveSetNumber),
                pbeChampionIcons = emptyMap(),
                pbeChampionCosts = emptyMap(),
                pbeChampionTraits = emptyMap(),
                pbeTraitIcons = emptyMap(),
                fetchedFromNetwork = true
            )
        }

        return parsed.copy(fetchedFromNetwork = true)
    }

    internal fun parsePreloadJson(
        rawJson: String,
        pbePatchVersion: String
    ): TftacticsPbeFetchResult {
        val root = JSONObject(rawJson)
        val setNum = root.optInt("pbeSetNumber", 19)
        val setLabel = root.optString("pbeSetLabel").ifBlank { "Set $setNum (PBE)" }

        fun readStringMap(obj: JSONObject?): Map<String, String> {
            if (obj == null) return emptyMap()
            val out = LinkedHashMap<String, String>(obj.length())
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = obj.optString(k)
                if (v.isNotBlank()) out[k] = v
            }
            return out
        }

        val champIcons = readStringMap(root.optJSONObject("championIcons"))
        val traitIcons = readStringMap(root.optJSONObject("traitIcons"))
        val champCosts = LinkedHashMap<String, Int>().apply {
            root.optJSONObject("championCosts")?.let { obj ->
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val c = obj.optInt(k, 0)
                    if (c in 1..9) put(k, c)
                }
            }
        }
        val champTraits = LinkedHashMap<String, List<String>>().apply {
            root.optJSONObject("championTraits")?.let { obj ->
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val arr = obj.optJSONArray(k) ?: continue
                    val list = (0 until arr.length()).mapNotNull { i ->
                        arr.optString(i).takeIf { it.isNotBlank() }
                    }
                    if (list.isNotEmpty()) put(k, list)
                }
            }
        }

        val compsArr = root.optJSONArray("comps") ?: JSONArray()
        val comps = parseTeamCompsArray(
            compsArr = compsArr,
            targetSetNumber = setNum,
            setLabel = setLabel,
            pbePatchVersion = pbePatchVersion,
            championCosts = champCosts
        )

        return TftacticsPbeFetchResult(
            pbeComps = comps,
            detectedPbeSet = setLabel,
            pbeSetNumber = setNum,
            pbeChampionIcons = champIcons,
            pbeChampionCosts = champCosts,
            pbeChampionTraits = champTraits,
            pbeTraitIcons = traitIcons,
            fetchedFromNetwork = false
        )
    }

    internal fun parseBundleAndHtml(
        html: String,
        jsBundle: String,
        pbePatchVersion: String,
        liveSetNumber: Int? = 18
    ): TftacticsPbeFetchResult {
        val jsonArrays = extractJsonParseArrays(jsBundle)

        var compsArr: JSONArray? = null
        var champsArr: JSONArray? = null
        val traitArrays = mutableListOf<JSONArray>()

        for (arrText in jsonArrays) {
            val head = arrText.take(600)
            if (compsArr == null && head.contains("\"characters\"") && head.contains("\"playstyle\"")) {
                runCatching { JSONArray(arrText) }.getOrNull()?.let { compsArr = it }
            } else if (champsArr == null && head.contains("\"origin\"") && head.contains("\"cost\"") && head.contains("\"health\"")) {
                runCatching { JSONArray(arrText) }.getOrNull()?.let { champsArr = it }
            } else if ((head.contains("\"Origin\"") || head.contains("\"Class\"") || head.contains("\"bonus\"")) &&
                head.contains("\"set\"") && !head.contains("\"characters\"") && !head.contains("\"tierlist\"")
            ) {
                runCatching { JSONArray(arrText) }.getOrNull()?.let { traitArrays.add(it) }
            }
        }

        val availableSets = collectAvailableSets(compsArr)
        val targetSet = detectTargetPbeSet(html, availableSets, liveSetNumber) ?: 19
        val setLabel = detectSetBannerLabel(html, targetSet) ?: "Set $targetSet (PBE)"

        val champIcons = LinkedHashMap<String, String>()
        val champCosts = LinkedHashMap<String, Int>()
        val champTraits = LinkedHashMap<String, List<String>>()

        champsArr?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                if (!containsSet(obj.optJSONArray("set"), targetSet)) continue
                val name = cleanText(obj.optString("name"))
                if (name.isBlank()) continue
                champIcons[name] = buildSunderChampionUrl(targetSet, name)
                val cost = obj.optInt("cost", 3).coerceIn(1, 9)
                champCosts[name] = cost
                val traits = buildList {
                    extractStringValues(obj.opt("origin")).forEach { if (it != "-") add(it) }
                    extractStringValues(obj.opt("type")).forEach { if (it != "-") add(it) }
                }.distinct()
                if (traits.isNotEmpty()) {
                    champTraits[name] = traits
                }
            }
        }

        val traitIcons = LinkedHashMap<String, String>()
        traitArrays.forEach { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                if (!containsSet(obj.optJSONArray("set"), targetSet)) continue
                val name = cleanText(obj.optString("name"))
                if (name.isNotBlank()) {
                    traitIcons[name] = buildSunderTraitUrl(name)
                }
            }
        }

        val bundleComps = compsArr?.let { arr ->
            parseTeamCompsArray(
                compsArr = arr,
                targetSetNumber = targetSet,
                setLabel = setLabel,
                pbePatchVersion = pbePatchVersion,
                championCosts = champCosts
            )
        }.orEmpty()

        bundleComps.forEach { comp ->
            comp.traits.forEach { traitEntry ->
                val cleanTrait = traitEntry.replace(Regex("^\\d+\\s+"), "").trim()
                if (cleanTrait.isNotBlank() && !traitIcons.containsKey(cleanTrait)) {
                    traitIcons[cleanTrait] = buildSunderTraitUrl(cleanTrait)
                }
            }
            comp.coreUnits.forEach { unitName ->
                if (!champIcons.containsKey(unitName)) {
                    champIcons[unitName] = buildSunderChampionUrl(targetSet, unitName)
                }
            }
        }

        val resolvedComps = if (bundleComps.isNotEmpty()) {
            bundleComps
        } else {
            parseHtmlTeamComps(html, pbePatchVersion, targetSet)
        }

        return TftacticsPbeFetchResult(
            pbeComps = resolvedComps,
            detectedPbeSet = setLabel,
            pbeSetNumber = targetSet,
            pbeChampionIcons = champIcons,
            pbeChampionCosts = champCosts,
            pbeChampionTraits = champTraits,
            pbeTraitIcons = traitIcons,
            fetchedFromNetwork = true
        )
    }

    private fun parseTeamCompsArray(
        compsArr: JSONArray,
        targetSetNumber: Int,
        setLabel: String,
        pbePatchVersion: String,
        championCosts: Map<String, Int>
    ): List<MetaComp> {
        val comps = mutableListOf<MetaComp>()
        for (i in 0 until compsArr.length()) {
            val obj = compsArr.optJSONObject(i) ?: continue
            val setArr = obj.optJSONArray("set")
            if (setArr != null && !containsSet(setArr, targetSetNumber)) continue

            val title = cleanText(obj.optString("name").ifBlank { obj.optString("name_ch") })
            if (title.length !in 3..72) continue

            val tier = when (val rawTier = obj.opt("tier")) {
                is Number -> when (rawTier.toInt()) {
                    1 -> TierRank.S
                    2 -> TierRank.A
                    3 -> TierRank.B
                    4 -> TierRank.C
                    5 -> TierRank.X
                    else -> TierRank.B
                }
                is String -> TierRank.fromRaw(rawTier)
                else -> TierRank.A
            }

            val playstyle = cleanText(obj.optString("playstyle")).ifBlank { "Fast 8" }
            val charsArr = obj.optJSONArray("characters") ?: continue
            if (charsArr.length() < 4) continue

            val parsedUnits = (0 until charsArr.length().coerceAtMost(14)).mapNotNull { uIdx ->
                val ch = charsArr.optJSONObject(uIdx) ?: return@mapNotNull null
                val name = cleanText(ch.optString("name"))
                if (name.isBlank()) return@mapNotNull null

                val posStr = ch.optString("position")
                val posNum = POS_REGEX.find(posStr)?.groupValues?.getOrNull(1)?.toIntOrNull()
                val row = posNum?.let { ((it - 1) / 7).coerceIn(0, 3) }
                val col = posNum?.let { ((it - 1) % 7).coerceIn(0, 6) }
                val stars = ch.optInt("level", 2).coerceIn(1, 3)
                val items = extractStringValues(ch.opt("items"))
                val catalogCost = championCosts[name]
                    ?: championCosts.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
                    ?: TftAssetIconResolver.getChampionCost(name)
                    ?: 3
                ParsedTftacticsUnit(
                    name = name,
                    cost = catalogCost.coerceIn(1, 5),
                    items = items,
                    row = row,
                    col = col,
                    stars = stars
                )
            }

            if (parsedUnits.size < 4) continue

            val carryUnit = pickCarryUnit(parsedUnits)
            val placements = parsedUnits.mapIndexed { idx, u ->
                val isCarry = u.name.equals(carryUnit.name, ignoreCase = true) && u.items.size == carryUnit.items.size
                HexUnitPlacement(
                    unitName = u.name,
                    row = u.row ?: if (idx < parsedUnits.size / 2) 0 else 3,
                    col = u.col ?: ((idx * 2) % 7),
                    cost = if (isCarry && u.cost <= 2 && u.stars < 3) 4 else u.cost,
                    items = u.items,
                    isCarry = isCarry,
                    stars = u.stars
                )
            }

            val synergyTraits = parseSynergyTraits(obj.optJSONArray("synergy"), obj.optJSONArray("traits"))

            val description = cleanText(obj.optString("description"))
            val midUnits = extractStringValues(obj.opt("mid"))
            val replacements = parseReplacements(obj.optJSONArray("replacements"))
            val carouselNotes = parseCarousel(obj.optJSONArray("carrousel"))

            val levelingGuide = buildList {
                if (description.isNotBlank()) add(description)
                if (midUnits.isNotEmpty()) add("Early/Mid: ${midUnits.joinToString(", ")}")
                if (replacements.isNotBlank()) add("Subs: $replacements")
            }.joinToString(" • ").ifBlank { "TFTactics $playstyle • $setLabel ($pbePatchVersion)" }

            val notes = buildList {
                if (carouselNotes.isNotBlank()) add("Carousel: $carouselNotes")
                add("Synced from tftactics.gg/tierlist/team-comps/ ($setLabel)")
            }.joinToString(" • ")

            val slug = title.lowercase(Locale.US)
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')

            comps.add(
                MetaComp(
                    id = "tftactics_pbe_$slug",
                    environment = TftEnvironment.PBE,
                    title = title,
                    tier = tier,
                    playstyle = playstyle,
                    avgPlacement = when (tier) {
                        TierRank.S -> 3.45
                        TierRank.A -> 3.82 + (i % 15) * 0.01
                        TierRank.B -> 4.18 + (i % 15) * 0.01
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
                    trendDelta = when {
                        obj.optBoolean("tier_up", false) -> "Rising PBE"
                        else -> "TFTactics PBE"
                    },
                    isTrending = tier == TierRank.S || obj.optBoolean("tier_up", false),
                    patchTag = pbePatchVersion,
                    carryName = carryUnit.name,
                    traits = synergyTraits,
                    coreUnits = parsedUnits.map { it.name },
                    hexPlacements = placements,
                    augmentPriorities = emptyList(),
                    levelingGuide = levelingGuide,
                    notes = notes,
                    setName = setLabel,
                    sourceUrl = TFTACTICS_COMPS_URL
                )
            )
        }

        return comps.distinctBy { it.id }.sortedWith(
            compareBy<MetaComp> { it.tier.sortOrder }
                .thenBy { it.avgPlacement }
                .thenBy { it.title.lowercase(Locale.US) }
        )
    }

    internal fun parseHtmlTeamComps(
        html: String,
        pbePatchVersion: String,
        targetSetNumber: Int?
    ): List<MetaComp> {
        val rawBlocks = html.split("""<div class="team-portrait">""")
        if (rawBlocks.size <= 1) return emptyList()

        val setLabel = detectSetBannerLabel(html, targetSetNumber)
            ?: targetSetNumber?.let { "Set $it (PBE)" }
            ?: TftEnvironment.PBE.setName

        val out = mutableListOf<MetaComp>()
        for (idx in 1 until rawBlocks.size) {
            val block = rawBlocks[idx].take(12_000)
            val rankText = HTML_RANK_REGEX.find(block)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            val tier = TierRank.fromRaw(rankText.ifBlank { "A" })

            val nameSegment = HTML_NAME_REGEX.find(block)?.groupValues?.getOrNull(1).orEmpty()
            val title = cleanText(nameSegment.substringBefore("<div class=\"team-name-extra\""))
            if (title.length !in 3..72) continue

            val playstyle = cleanText(HTML_PLAYSTYLE_REGEX.find(block)?.groupValues?.getOrNull(1).orEmpty())
                .ifBlank { "Fast 8" }

            val charsSection = block.substringAfter("""<div class="team-characters">""", "")
                .substringBefore("""<div class="team-more">""")
            if (charsSection.isBlank()) continue

            val units = HTML_CHAR_REGEX.findAll(charsSection).mapNotNull { m ->
                val classTokens = m.groupValues[1]
                val unitChunk = m.groupValues[2]
                val name = cleanText(m.groupValues[3])
                if (name.isBlank()) return@mapNotNull null
                val unitSet = Regex("""\bs(\d+)\b""").find(classTokens)?.groupValues?.getOrNull(1)?.toIntOrNull()
                if (targetSetNumber != null && unitSet != null && unitSet != targetSetNumber) {
                    return@mapNotNull null
                }
                val cost = Regex("""\bc(\d)\b""").find(classTokens)?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 5) ?: 3
                val stars = if (classTokens.contains("l3")) 3 else 2
                val items = HTML_ITEM_NAME_REGEX.findAll(unitChunk).mapNotNull { im ->
                    cleanText(im.groupValues[1]).takeIf { it.isNotBlank() }
                }.toList()
                ParsedTftacticsUnit(name = name, cost = cost, items = items, row = null, col = null, stars = stars)
            }.toList()

            if (units.size < 4) continue
            val carry = pickCarryUnit(units)
            val placements = units.mapIndexed { uIdx, u ->
                val isCarry = u.name.equals(carry.name, ignoreCase = true)
                HexUnitPlacement(
                    unitName = u.name,
                    row = if (uIdx < units.size / 2) 0 else 3,
                    col = (uIdx * 2) % 7,
                    cost = u.cost,
                    items = u.items,
                    isCarry = isCarry,
                    stars = u.stars
                )
            }
            val slug = title.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_").trim('_')
            out.add(
                MetaComp(
                    id = "tftactics_pbe_$slug",
                    environment = TftEnvironment.PBE,
                    title = title,
                    tier = tier,
                    playstyle = playstyle,
                    avgPlacement = when (tier) {
                        TierRank.S -> 3.45
                        TierRank.A -> 3.82 + (idx % 15) * 0.01
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
                    winRate = 14.0,
                    pickRate = 7.0,
                    trendDelta = "TFTactics PBE",
                    isTrending = tier == TierRank.S,
                    patchTag = pbePatchVersion,
                    carryName = carry.name,
                    traits = emptyList(),
                    coreUnits = units.map { it.name },
                    hexPlacements = placements,
                    augmentPriorities = emptyList(),
                    levelingGuide = "TFTactics $playstyle • $setLabel ($pbePatchVersion)",
                    notes = "Synced from tftactics.gg/tierlist/team-comps/ ($setLabel).",
                    setName = setLabel,
                    sourceUrl = TFTACTICS_COMPS_URL
                )
            )
        }
        return out.distinctBy { it.id }
    }

    private data class ParsedTftacticsUnit(
        val name: String,
        val cost: Int,
        val items: List<String>,
        val row: Int?,
        val col: Int?,
        val stars: Int
    )

    private fun pickCarryUnit(units: List<ParsedTftacticsUnit>): ParsedTftacticsUnit {
        return units.maxByOrNull { u ->
            val offensiveItems = u.items.count { item ->
                val lower = item.lowercase(Locale.US)
                !lower.contains("warmog") &&
                    !lower.contains("gargoyle") &&
                    !lower.contains("sunfire") &&
                    !lower.contains("bramble") &&
                    !lower.contains("dragon's claw") &&
                    !lower.contains("dragons claw") &&
                    !lower.contains("evenshroud") &&
                    !lower.contains("steadfast") &&
                    !lower.contains("protector") &&
                    !lower.contains("redemption") &&
                    !lower.contains("spirit visage")
            }
            val backlineBonus = if ((u.row ?: 3) >= 2) 5 else 0
            val starBonus = if (u.stars >= 3) 8 else 0
            offensiveItems * 25 + u.items.size * 12 + u.cost * 4 + backlineBonus + starBonus
        } ?: units.last()
    }

    private fun parseSynergyTraits(synergyArr: JSONArray?, fallbackTraitsArr: JSONArray?): List<String> {
        if (synergyArr != null && synergyArr.length() > 0) {
            val ranked = (0 until synergyArr.length()).mapNotNull { i ->
                val obj = synergyArr.optJSONObject(i) ?: return@mapNotNull null
                val name = cleanText(obj.optString("name"))
                val count = obj.optInt("count", 0)
                if (name.isBlank() || count <= 0) return@mapNotNull null
                Triple(count, obj.optString("rank"), "$count $name")
            }.sortedWith(compareByDescending<Triple<Int, String, String>> { it.first })
            if (ranked.isNotEmpty()) {
                return ranked.take(6).map { it.third }
            }
        }
        return extractStringValues(fallbackTraitsArr).take(6)
    }

    private fun parseReplacements(arr: JSONArray?): String {
        if (arr == null || arr.length() == 0) return ""
        val parts = mutableListOf<String>()
        for (i in 0 until arr.length().coerceAtMost(3)) {
            val obj = arr.optJSONObject(i) ?: continue
            val outNames = extractStringValues(obj.opt("out"))
            val inNames = extractStringValues(obj.opt("in"))
            if (inNames.isNotEmpty() && outNames.isNotEmpty()) {
                parts.add("${outNames.joinToString("/")} → ${inNames.joinToString("/")}")
            } else if (inNames.isNotEmpty()) {
                parts.add("+${inNames.joinToString(", +")}")
            }
        }
        return parts.joinToString("; ")
    }

    private fun parseCarousel(arr: JSONArray?): String {
        if (arr == null || arr.length() == 0) return ""
        val items = mutableListOf<String>()
        for (i in 0 until arr.length().coerceAtMost(3)) {
            val obj = arr.optJSONObject(i) ?: continue
            val item = cleanText(obj.optString("item"))
            val comp = cleanText(obj.optString("component"))
            when {
                item.isNotBlank() && comp.isNotBlank() -> items.add("$item ($comp)")
                item.isNotBlank() -> items.add(item)
                comp.isNotBlank() -> items.add(comp)
            }
        }
        return items.joinToString(", ")
    }

    internal fun extractJsonParseArrays(js: String): List<String> {
        val out = mutableListOf<String>()
        var searchFrom = 0
        val marker = "JSON.parse('"
        while (searchFrom < js.length) {
            val idx = js.indexOf(marker, searchFrom)
            if (idx < 0) break
            val contentStart = idx + marker.length
            val decoded = decodeJsSingleQuotedLiteral(js, contentStart)
            if (decoded != null) {
                val (literalText, nextPos) = decoded
                val trimmed = literalText.trim()
                if (trimmed.startsWith("[") && trimmed.length > 500) {
                    out.add(trimmed)
                }
                searchFrom = nextPos
            } else {
                searchFrom = contentStart
            }
        }
        return out
    }

    private fun decodeJsSingleQuotedLiteral(source: String, startIdx: Int): Pair<String, Int>? {
        val sb = StringBuilder()
        var i = startIdx
        while (i < source.length) {
            val ch = source[i]
            if (ch == '\'') {
                return sb.toString() to (i + 1)
            }
            if (ch == '\\' && i + 1 < source.length) {
                val next = source[i + 1]
                when (next) {
                    '\\' -> { sb.append('\\'); i += 2 }
                    '\'' -> { sb.append('\''); i += 2 }
                    '"' -> { sb.append('"'); i += 2 }
                    'n' -> { sb.append('\n'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    'b' -> { sb.append('\b'); i += 2 }
                    'f' -> { sb.append('\u000C'); i += 2 }
                    'u' -> {
                        if (i + 5 < source.length) {
                            val hex = source.substring(i + 2, i + 6)
                            val code = hex.toIntOrNull(16)
                            if (code != null) {
                                sb.append(code.toChar())
                                i += 6
                            } else {
                                sb.append(next)
                                i += 2
                            }
                        } else {
                            sb.append(next)
                            i += 2
                        }
                    }
                    'x' -> {
                        if (i + 3 < source.length) {
                            val hex = source.substring(i + 2, i + 4)
                            val code = hex.toIntOrNull(16)
                            if (code != null) {
                                sb.append(code.toChar())
                                i += 4
                            } else {
                                sb.append(next)
                                i += 2
                            }
                        } else {
                            sb.append(next)
                            i += 2
                        }
                    }
                    else -> {
                        sb.append(next)
                        i += 2
                    }
                }
            } else {
                sb.append(ch)
                i += 1
            }
        }
        return null
    }

    private fun collectAvailableSets(compsArr: JSONArray?): List<Int> {
        if (compsArr == null) return emptyList()
        val sets = LinkedHashSet<Int>()
        for (i in 0 until compsArr.length()) {
            val setArr = compsArr.optJSONObject(i)?.optJSONArray("set") ?: continue
            for (j in 0 until setArr.length()) {
                val s = setArr.optInt(j, -1)
                if (s > 0) sets.add(s)
            }
        }
        return sets.sorted()
    }

    private fun detectTargetPbeSet(
        html: String,
        availableJsonSets: List<Int>,
        liveSetNumber: Int?
    ): Int? {
        val dropdownSets = HTML_SET_OPTION_REGEX.findAll(html)
            .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
            .toList()
        val bannerSet = BANNER_SET_REGEX.find(html)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val allCandidates = (availableJsonSets + dropdownSets + listOfNotNull(bannerSet))
            .filter { it >= 10 }
            .distinct()
            .sorted()

        if (liveSetNumber != null) {
            val newerThanLive = allCandidates.filter { it > liveSetNumber }
            if (newerThanLive.isNotEmpty()) {
                return newerThanLive.max()
            }
        }
        return bannerSet ?: allCandidates.maxOrNull()
    }

    private fun detectSetBannerLabel(html: String, targetSet: Int?): String? {
        val m = BANNER_SET_REGEX.find(html)
        if (m != null) {
            val num = m.groupValues[1].toIntOrNull()
            val subtitle = cleanText(m.groupValues[2])
            if (targetSet == null || num == targetSet) {
                return if (subtitle.isNotBlank()) "Set $num: $subtitle (PBE)" else "Set $num (PBE)"
            }
        }
        return targetSet?.let { "Set $it (PBE)" }
    }

    private fun containsSet(arr: JSONArray?, targetSet: Int): Boolean {
        if (arr == null) return false
        for (i in 0 until arr.length()) {
            if (arr.optInt(i, -1) == targetSet) return true
        }
        return false
    }

    private fun extractStringValues(raw: Any?): List<String> {
        return when (raw) {
            is JSONArray -> (0 until raw.length()).mapNotNull { i ->
                cleanText(raw.optString(i)).takeIf { it.isNotBlank() }
            }
            is String -> listOfNotNull(cleanText(raw).takeIf { it.isNotBlank() })
            else -> emptyList()
        }
    }

    private fun findMainBundleUrl(html: String): String? {
        val match = MAIN_BUNDLE_REGEX.find(html) ?: CHUNK_JS_REGEX.findAll(html).firstOrNull {
            it.groupValues[1].contains("main", ignoreCase = true)
        } ?: return null
        val src = match.groupValues[1].trim()
        return when {
            src.startsWith("http") -> src
            src.startsWith("//") -> "https:$src"
            else -> "$TFTACTICS_ORIGIN${if (src.startsWith("/")) src else "/$src"}"
        }
    }

    private fun fetchUrl(url: String): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .cacheControl(CacheControl.FORCE_NETWORK)
                .header("Cache-Control", "no-cache, no-store, max-age=0")
                .header("Pragma", "no-cache")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/131.0 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/javascript,application/json;q=0.9,*/*;q=0.8")
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
        const val TFTACTICS_ORIGIN = "https://tftactics.gg"
        const val TFTACTICS_COMPS_URL = "https://tftactics.gg/tierlist/team-comps/"

        fun buildSunderChampionUrl(setNumber: Int, championName: String): String {
            val clean = championName
                .replace(Regex("""\([^)]*\)"""), "")
                .replace("/", "")
                .replace(" ", "")
                .replace("'", "")
                .replace(".", "")
            return "https://sunderarmor.com/characters/Skin/$setNumber/$clean.png"
        }

        fun buildSunderTraitUrl(traitName: String): String {
            val clean = traitName
                .replace("/", "")
                .replace(":", "")
                .replace("'", "")
                .replace(".", "")
                .replace("-", "")
                .replace(" ", "")
                .lowercase(Locale.US)
            return "https://sunderarmor.com/icons/$clean.png"
        }

        private val MAIN_BUNDLE_REGEX = Regex(
            """<script[^>]+src=["']([^"']*static/js/main\.[^"']+\.js(?:\?[^"']*)?)["']""",
            RegexOption.IGNORE_CASE
        )
        private val CHUNK_JS_REGEX = Regex(
            """<script[^>]+src=["']([^"']+\.chunk\.js(?:\?[^"']*)?)["']""",
            RegexOption.IGNORE_CASE
        )
        private val HTML_SET_OPTION_REGEX = Regex("""\bset=["'](\d+)["']""", RegexOption.IGNORE_CASE)
        private val BANNER_SET_REGEX = Regex(
            """<div class="update-title">\s*Set\s*(\d+)\s*(?::\s*([^<\n]+?))?\s+is now live on TFTactics!?""",
            RegexOption.IGNORE_CASE
        )
        private val POS_REGEX = Regex("""p(\d+)""", RegexOption.IGNORE_CASE)
        private val HTML_RANK_REGEX = Regex("""<div class="team-rank[^"]*">\s*([SABCDX]\+?)\s*</div>""", RegexOption.IGNORE_CASE)
        private val HTML_NAME_REGEX = Regex("""<div class="team-name-elipsis">(.*?)</div>\s*</div>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        private val HTML_PLAYSTYLE_REGEX = Regex("""<div class="team-playstyle">(.*?)</div>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        private val HTML_CHAR_REGEX = Regex(
            """<a[^>]*class="characters-item\s+([^"]+)"[^>]*>(.*?)<div class="team-character-name">([^<]+)</div>\s*</a>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        private val HTML_ITEM_NAME_REGEX = Regex("""<div class="character-wrapper"[^>]*name=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        private val HTML_TAG_REGEX = Regex("""<[^>]+>""")
        private val WHITESPACE_REGEX = Regex("""\s+""")
    }
}
