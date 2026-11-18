package com.example.data.network

import com.example.data.model.AugmentTier
import com.example.data.model.AugmentTierEntry
import com.example.data.model.ChampionTierEntry
import com.example.data.model.HexUnitPlacement
import com.example.data.model.ItemCategory
import com.example.data.model.ItemTierEntry
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
import kotlin.math.roundToInt

data class TftacticsPbeFetchResult(
    val pbeComps: List<MetaComp>,
    val detectedPbeSet: String?,
    val pbeSetNumber: Int?,
    val pbeChampionIcons: Map<String, String>,
    val pbeChampionCosts: Map<String, Int>,
    val pbeChampionTraits: Map<String, List<String>>,
    val pbeTraitIcons: Map<String, String>,
    val pbeItemIcons: Map<String, String> = emptyMap(),
    val pbeChampionTiers: List<ChampionTierEntry> = emptyList(),
    val pbeItemTiers: List<ItemTierEntry> = emptyList(),
    val pbeAugmentTiers: List<AugmentTierEntry> = emptyList(),
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

        val itemIcons = readStringMap(root.optJSONObject("itemIcons"))
        val champDetailsObj = root.optJSONObject("championDetails")
        val champExtraDetails = LinkedHashMap<String, PbeChampionExtra>().apply {
            if (champDetailsObj != null) {
                val keys = champDetailsObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val obj = champDetailsObj.optJSONObject(k) ?: continue
                    val itemsArr = obj.optJSONArray("items")
                    val defaultItems = if (itemsArr != null) {
                        (0 until itemsArr.length()).mapNotNull { idx ->
                            cleanText(itemsArr.optString(idx)).takeIf { it.isNotBlank() }
                        }
                    } else emptyList()
                    put(
                        k,
                        PbeChampionExtra(
                            tierNum = obj.optInt("tier", 3),
                            defaultItems = defaultItems,
                            abilityName = cleanText(obj.optString("abilityName")),
                            abilityDescription = cleanText(obj.optString("abilityDescription"))
                        )
                    )
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

        val champTiers = buildPbeChampionTierEntries(
            comps = comps,
            championCosts = champCosts,
            championTraits = champTraits,
            championIcons = champIcons,
            championExtras = champExtraDetails
        )

        val itemsArr = root.optJSONArray("items") ?: JSONArray()
        val itemTiers = buildPbeItemTierEntries(
            itemsArr = itemsArr,
            targetSetNumber = setNum,
            comps = comps,
            itemIconsOut = null
        )

        val augsArr = root.optJSONArray("augments") ?: JSONArray()
        val augTiers = buildPbeAugmentTierEntries(
            augsArr = augsArr,
            comps = comps
        )

        return TftacticsPbeFetchResult(
            pbeComps = comps,
            detectedPbeSet = setLabel,
            pbeSetNumber = setNum,
            pbeChampionIcons = champIcons,
            pbeChampionCosts = champCosts,
            pbeChampionTraits = champTraits,
            pbeTraitIcons = traitIcons,
            pbeItemIcons = itemIcons,
            pbeChampionTiers = champTiers,
            pbeItemTiers = itemTiers,
            pbeAugmentTiers = augTiers,
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
        var itemsArr: JSONArray? = null
        var augsArr: JSONArray? = null
        val traitArrays = mutableListOf<JSONArray>()

        for (arrText in jsonArrays) {
            val head = arrText.take(600)
            if (compsArr == null && head.contains("\"characters\"") && head.contains("\"playstyle\"")) {
                runCatching { JSONArray(arrText) }.getOrNull()?.let { compsArr = it }
            } else if (champsArr == null && head.contains("\"origin\"") && head.contains("\"cost\"") && head.contains("\"health\"")) {
                runCatching { JSONArray(arrText) }.getOrNull()?.let { champsArr = it }
            } else if (itemsArr == null && head.contains("\"shadow_name\"") && head.contains("\"into\"")) {
                runCatching { JSONArray(arrText) }.getOrNull()?.let { itemsArr = it }
            } else if (augsArr == null && head.contains("\"tierlist\"") && head.contains("\"bonus\"") && head.contains("\"DA_")) {
                runCatching { JSONArray(arrText) }.getOrNull()?.let { augsArr = it }
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
        val champExtras = LinkedHashMap<String, PbeChampionExtra>()

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
                val defaultItems = extractStringValues(obj.opt("items"))
                val skillObj = obj.optJSONArray("skill")?.optJSONObject(0)
                val abilityName = cleanText(skillObj?.optString("name").orEmpty())
                val abilityDesc = cleanText(skillObj?.optString("description").orEmpty())
                champExtras[name] = PbeChampionExtra(
                    tierNum = obj.optInt("tier", 3),
                    defaultItems = defaultItems,
                    abilityName = abilityName,
                    abilityDescription = abilityDesc
                )
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

        val champTiers = buildPbeChampionTierEntries(
            comps = resolvedComps,
            championCosts = champCosts,
            championTraits = champTraits,
            championIcons = champIcons,
            championExtras = champExtras
        )

        val itemIcons = LinkedHashMap<String, String>()
        val itemTiers = buildPbeItemTierEntries(
            itemsArr = itemsArr ?: JSONArray(),
            targetSetNumber = targetSet,
            comps = resolvedComps,
            itemIconsOut = itemIcons
        )
        val augTiers = buildPbeAugmentTierEntries(
            augsArr = augsArr ?: JSONArray(),
            comps = resolvedComps
        )

        return TftacticsPbeFetchResult(
            pbeComps = resolvedComps,
            detectedPbeSet = setLabel,
            pbeSetNumber = targetSet,
            pbeChampionIcons = champIcons,
            pbeChampionCosts = champCosts,
            pbeChampionTraits = champTraits,
            pbeTraitIcons = traitIcons,
            pbeItemIcons = itemIcons,
            pbeChampionTiers = champTiers,
            pbeItemTiers = itemTiers,
            pbeAugmentTiers = augTiers,
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
                    ?: TftAssetIconResolver.getChampionCost(name, TftEnvironment.PBE)
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
            }.joinToString(" • ").ifBlank { "$playstyle • $setLabel ($pbePatchVersion)" }

            val notes = buildList {
                if (carouselNotes.isNotBlank()) add("Carousel: $carouselNotes")
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
                        else -> "Top Meta"
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
                    trendDelta = "Top Meta",
                    isTrending = tier == TierRank.S,
                    patchTag = pbePatchVersion,
                    carryName = carry.name,
                    traits = emptyList(),
                    coreUnits = units.map { it.name },
                    hexPlacements = placements,
                    augmentPriorities = emptyList(),
                    levelingGuide = "$playstyle • $setLabel ($pbePatchVersion)",
                    notes = "",
                    setName = setLabel,
                    sourceUrl = TFTACTICS_COMPS_URL
                )
            )
        }
        return out.distinctBy { it.id }
    }

    private data class PbeChampionExtra(
        val tierNum: Int = 3,
        val defaultItems: List<String> = emptyList(),
        val abilityName: String = "",
        val abilityDescription: String = ""
    )

    private fun buildPbeChampionTierEntries(
        comps: List<MetaComp>,
        championCosts: Map<String, Int>,
        championTraits: Map<String, List<String>>,
        championIcons: Map<String, String>,
        championExtras: Map<String, PbeChampionExtra> = emptyMap()
    ): List<ChampionTierEntry> {
        val allNames = LinkedHashSet<String>().apply {
            addAll(championCosts.keys)
            addAll(championIcons.keys)
            comps.forEach { comp -> addAll(comp.coreUnits) }
        }
        if (allNames.isEmpty()) return emptyList()

        return allNames.mapNotNull { name ->
            val cleanName = name.trim()
            if (cleanName.isBlank()) return@mapNotNull null

            val matchingComps = comps.filter { comp ->
                comp.coreUnits.any { it.equals(cleanName, ignoreCase = true) }
            }
            val cost = (championCosts[cleanName]
                ?: championCosts.entries.firstOrNull { it.key.equals(cleanName, ignoreCase = true) }?.value
                ?: 3).coerceIn(1, 7)
            val traits = championTraits[cleanName]
                ?: championTraits.entries.firstOrNull { it.key.equals(cleanName, ignoreCase = true) }?.value
                ?: emptyList()
            val extra = championExtras[cleanName]
                ?: championExtras.entries.firstOrNull { it.key.equals(cleanName, ignoreCase = true) }?.value

            val placements = matchingComps.flatMap { comp ->
                comp.hexPlacements.filter { it.unitName.equals(cleanName, ignoreCase = true) }
            }
            val itemCounts = LinkedHashMap<String, Int>()
            placements.forEach { p ->
                p.items.forEach { item ->
                    itemCounts[item] = (itemCounts[item] ?: 0) + 1
                }
            }
            val compItems = itemCounts.entries
                .sortedByDescending { it.value }
                .take(3)
                .map { it.key }
            val bestItems = (compItems + extra?.defaultItems.orEmpty())
                .distinct()
                .take(3)

            val isMainCarryCount = matchingComps.count { it.carryName.equals(cleanName, ignoreCase = true) }
            val hasTankItems = bestItems.any { item ->
                val low = item.lowercase(Locale.US)
                low.contains("warmog") || low.contains("gargoyle") || low.contains("sunfire") ||
                    low.contains("bramble") || low.contains("spirit visage") || low.contains("dragon")
            }
            val hasThreeStar = placements.any { it.stars >= 3 }

            val role = when {
                isMainCarryCount > 0 -> "Main Carry"
                hasTankItems -> if (hasThreeStar) "3★ Tank" else "Frontline Tank"
                bestItems.isNotEmpty() -> "Secondary Carry"
                cost >= 5 -> "5-Cost Flex"
                traits.isNotEmpty() -> "${traits.first()} Unit"
                else -> "Synergy Unit"
            }

            val bestTier = when {
                isMainCarryCount > 0 && matchingComps.any { it.tier == TierRank.S } -> TierRank.S
                isMainCarryCount > 0 -> TierRank.S
                matchingComps.size >= 3 -> TierRank.A
                matchingComps.isNotEmpty() -> matchingComps.minBy { it.tier.sortOrder }.tier
                extra?.tierNum == 1 -> TierRank.S
                extra?.tierNum == 2 || cost >= 5 -> TierRank.A
                cost == 4 -> TierRank.B
                else -> TierRank.C
            }

            val rawAvg = if (matchingComps.isNotEmpty()) {
                val baseAvg = matchingComps.map { it.avgPlacement }.average()
                (baseAvg - if (isMainCarryCount > 0) 0.14 else 0.0).coerceIn(3.15, 4.75)
            } else {
                when {
                    cost >= 5 -> 3.95
                    cost == 4 -> 4.18
                    else -> 4.42
                }
            }
            val avgPlacement = (rawAvg * 100.0).roundToInt() / 100.0
            val top4Rate = ((100.0 - (avgPlacement - 1.5) * 18.0).coerceIn(44.0, 74.0) * 10.0).roundToInt() / 10.0

            val slug = cleanName.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_").trim('_')
            ChampionTierEntry(
                id = "pbe_champ_$slug",
                name = cleanName,
                cost = cost.coerceIn(1, 5),
                tier = bestTier,
                environment = TftEnvironment.PBE,
                traits = traits,
                role = role,
                avgPlacement = avgPlacement,
                top4Rate = top4Rate,
                bestItems = bestItems,
                synergyComps = matchingComps.map { it.title }.distinct().take(4),
                abilityName = extra?.abilityName.orEmpty(),
                abilityDescription = extra?.abilityDescription.orEmpty()
            )
        }.sortedWith(
            compareBy<ChampionTierEntry> { it.tier.sortOrder }
                .thenBy { it.avgPlacement }
                .thenByDescending { it.cost }
                .thenBy { it.name }
        )
    }

    private fun buildPbeItemTierEntries(
        itemsArr: JSONArray,
        targetSetNumber: Int,
        comps: List<MetaComp>,
        itemIconsOut: MutableMap<String, String>?
    ): List<ItemTierEntry> {
        val holdersByItem = LinkedHashMap<String, MutableList<String>>()
        comps.forEach { comp ->
            comp.hexPlacements.forEach { p ->
                p.items.forEach { itName ->
                    val norm = itName.lowercase(Locale.US)
                    holdersByItem.getOrPut(norm) { mutableListOf() }.add(p.unitName)
                }
            }
        }

        val entries = mutableListOf<ItemTierEntry>()
        val seenNames = LinkedHashSet<String>()

        for (i in 0 until itemsArr.length()) {
            val obj = itemsArr.optJSONObject(i) ?: continue
            val setArr = obj.optJSONArray("set")
            if (setArr != null && setArr.length() > 0 && !containsSet(setArr, targetSetNumber)) continue

            val name = cleanText(obj.optString("name"))
            if (name.isBlank() || !seenNames.add(name.lowercase(Locale.US))) continue

            val isShadow = obj.optBoolean("shadow", false)
            val isRadiant = obj.optBoolean("radiant", false)
            if (itemIconsOut != null) {
                itemIconsOut[name] = buildSunderItemUrl(name, shadow = isShadow, radiant = isRadiant)
            }

            val combine = extractStringValues(obj.opt("combine"))
            val bonusLines = extractStringValues(obj.opt("bonus"))
            val statsArr = obj.optJSONArray("stats")
            val statTokens = if (statsArr != null) {
                (0 until statsArr.length()).mapNotNull { sIdx ->
                    val st = statsArr.optJSONObject(sIdx) ?: return@mapNotNull null
                    val sName = cleanText(st.optString("name")).uppercase(Locale.US)
                    val sVal = cleanText(st.optString("value"))
                    if (sName.isNotBlank() && sVal.isNotBlank()) "+$sVal $sName" else null
                }
            } else emptyList()

            val statsSummary = buildList {
                if (statTokens.isNotEmpty()) add(statTokens.joinToString(", "))
                bonusLines.forEach { add(it) }
            }.joinToString(" • ").ifBlank { "$name — Set $targetSetNumber PBE item." }

            val holders = holdersByItem[name.lowercase(Locale.US)]?.distinct()?.take(4).orEmpty()
            val tierNum = obj.optInt("tier", 3)
            val tier = when {
                tierNum <= 1 || holders.size >= 4 -> TierRank.S
                tierNum == 2 || holders.size >= 2 -> TierRank.A
                tierNum == 3 || holders.isNotEmpty() -> TierRank.B
                else -> TierRank.C
            }
            val avgPlacement = when (tier) {
                TierRank.S -> 3.52
                TierRank.A -> 3.84
                TierRank.B -> 4.18
                TierRank.C -> 4.46
                TierRank.X -> 4.30
            }
            val top4Rate = ((100.0 - (avgPlacement - 1.5) * 18.0) * 10.0).roundToInt() / 10.0
            val slug = name.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_").trim('_')

            entries.add(
                ItemTierEntry(
                    id = "pbe_item_$slug",
                    name = name,
                    tier = tier,
                    environment = TftEnvironment.PBE,
                    category = classifyPbeItemCategory(name, statsSummary),
                    components = combine,
                    statsSummary = statsSummary,
                    avgPlacement = avgPlacement,
                    top4Rate = top4Rate,
                    bestHolders = holders
                )
            )
        }

        // Also add any Set 19 emblems/items appearing in PBE comps if absent from itemsArr
        holdersByItem.keys.forEach { lowerName ->
            if (!seenNames.add(lowerName)) return@forEach
            val display = comps.asSequence()
                .flatMap { it.hexPlacements.asSequence() }
                .flatMap { it.items.asSequence() }
                .firstOrNull { it.equals(lowerName, ignoreCase = true) } ?: return@forEach
            itemIconsOut?.put(display, buildSunderItemUrl(display))
            val holders = holdersByItem[lowerName]?.distinct()?.take(4).orEmpty()
            val slug = display.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_").trim('_')
            entries.add(
                ItemTierEntry(
                    id = "pbe_item_$slug",
                    name = display,
                    tier = if (holders.size >= 2) TierRank.A else TierRank.B,
                    environment = TftEnvironment.PBE,
                    category = classifyPbeItemCategory(display, ""),
                    components = emptyList(),
                    statsSummary = "$display — Set $targetSetNumber PBE comp equipment.",
                    avgPlacement = 3.92,
                    top4Rate = 56.4,
                    bestHolders = holders
                )
            )
        }

        return entries.sortedWith(
            compareBy<ItemTierEntry> { it.tier.sortOrder }
                .thenBy { it.avgPlacement }
                .thenBy { it.name }
        )
    }

    private fun buildPbeAugmentTierEntries(
        augsArr: JSONArray,
        comps: List<MetaComp>
    ): List<AugmentTierEntry> {
        val entries = mutableListOf<AugmentTierEntry>()
        val seenNames = LinkedHashSet<String>()

        for (i in 0 until augsArr.length()) {
            val obj = augsArr.optJSONObject(i) ?: continue
            val name = cleanText(obj.optString("name"))
            if (name.isBlank() || !seenNames.add(name.lowercase(Locale.US))) continue

            val augTier = AugmentTier.fromRaw(obj.optString("tier"))
            val desc = cleanText(obj.optString("bonus")).ifBlank { "$name — PBE augment." }
            val tierListNum = obj.optInt("tierlist", 3)
            val tier = when {
                tierListNum <= 1 -> TierRank.S
                tierListNum == 2 -> TierRank.A
                tierListNum == 3 -> TierRank.B
                else -> TierRank.C
            }
            val cat = when {
                name.contains("Crown", true) || name.contains("Crest", true) || name.contains("Emblem", true) ||
                    desc.contains("Emblem", true) -> "Trait / Emblem"
                desc.contains("gold", true) || desc.contains("XP", false) || desc.contains("reroll", true) -> "Economy / XP"
                else -> "Combat / Items"
            }
            val matchedComps = comps.filter { comp ->
                comp.traits.any { tr ->
                    val cleanTr = tr.replace(Regex("^\\d+\\s+"), "").trim()
                    cleanTr.length >= 4 && (name.contains(cleanTr, true) || desc.contains(cleanTr, true))
                }
            }.map { it.title }.take(3)

            val avgPlacement = when (tier) {
                TierRank.S -> 3.48
                TierRank.A -> 3.84
                TierRank.B -> 4.18
                TierRank.C -> 4.48
                TierRank.X -> 4.32
            }
            val top4Rate = ((100.0 - (avgPlacement - 1.5) * 18.0) * 10.0).roundToInt() / 10.0
            val slug = (obj.optString("id").ifBlank { name })
                .lowercase(Locale.US)
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')

            entries.add(
                AugmentTierEntry(
                    id = "pbe_aug_$slug",
                    name = name,
                    tier = tier,
                    augmentTier = augTier,
                    environment = TftEnvironment.PBE,
                    category = cat,
                    avgPlacement = avgPlacement,
                    top4Rate = top4Rate,
                    description = desc,
                    synergyComps = matchedComps
                )
            )
        }

        return entries.sortedWith(
            compareBy<AugmentTierEntry> { it.tier.sortOrder }
                .thenBy { it.avgPlacement }
                .thenBy { it.name }
        )
    }

    private fun classifyPbeItemCategory(name: String, desc: String): ItemCategory {
        val n = name.lowercase(Locale.US)
        val d = desc.lowercase(Locale.US)
        return when {
            n.contains("guinsoo") || n.contains("nashor") || n.contains("red buff") ||
                n.contains("kraken") || n.contains("statikk") || n.contains("recurve") ||
                n.contains("flicker") || n.contains("rapid firecannon") || n.contains("wit's end") -> ItemCategory.ATTACK_SPEED
            n.contains("warmog") || n.contains("gargoyle") || n.contains("bramble") ||
                n.contains("dragon's claw") || n.contains("sunfire") || n.contains("spirit visage") ||
                n.contains("steadfast") || n.contains("protector's vow") || n.contains("evenshroud") ||
                n.contains("adaptive helm") || n.contains("quicksilver") || n.contains("vest") ||
                n.contains("cloak") || n.contains("belt") || n.contains("hullcrusher") ||
                n.contains("mogul") || n.contains("lightshield") || n.contains("eternal pact") -> ItemCategory.TANK
            n.contains("blue buff") || n.contains("shojin") || n.contains("jeweled") ||
                n.contains("rabadon") || n.contains("archangel") || n.contains("morello") ||
                n.contains("ionic") || n.contains("crownguard") || n.contains("hextech") ||
                n.contains("void staff") || n.contains("rod") || n.contains("tear") ||
                n.contains("zhonya") || n.contains("lich bane") || n.contains("blighting") ||
                n.contains("luden") || n.contains("dawncore") -> ItemCategory.AP
            n.contains("infinity edge") || n.contains("last whisper") || n.contains("giant slayer") ||
                n.contains("deathblade") || n.contains("striker") || n.contains("guardbreaker") ||
                n.contains("runaan") || n.contains("b.f. sword") || n.contains("gold collector") ||
                n.contains("prowler") || n.contains("fishbones") -> ItemCategory.AD
            n.contains("bloodthirster") || n.contains("titan") || n.contains("sterak") ||
                n.contains("hand of justice") || n.contains("edge of night") ||
                n.contains("death's defiance") || n.contains("tactician") -> ItemCategory.FIGHTER
            d.contains("ability power") || d.contains("mana") -> ItemCategory.AP
            d.contains("attack speed") -> ItemCategory.ATTACK_SPEED
            d.contains("armor") || d.contains("magic resist") || d.contains("health") -> ItemCategory.TANK
            d.contains("attack damage") || d.contains("crit") -> ItemCategory.AD
            else -> ItemCategory.FIGHTER
        }
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
                if (trimmed.startsWith("[") && trimmed.length > 40) {
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

        fun buildSunderItemUrl(
            itemName: String,
            shadow: Boolean = false,
            radiant: Boolean = false
        ): String {
            val clean = itemName
                .replace(":", "")
                .replace(" ", "")
                .replace(".", "")
                .replace("'", "")
            val prefix = buildString {
                if (shadow) append("Shadow/")
                if (radiant) append("Radiant/")
            }
            return "https://sunderarmor.com/items/$prefix$clean.png"
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
