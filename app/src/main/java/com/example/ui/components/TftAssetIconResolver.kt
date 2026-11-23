package com.example.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.data.model.TftEnvironment
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves exact Riot Data Dragon (16.22.1+) and TFTAcademy asset URLs, display names,
 * Set 18 champion traits, champion costs, and trait descriptions for all TFT champions,
 * items, augments, and traits.
 */
object TftAssetIconResolver {
    const val DEFAULT_DDRAGON_VERSION = "16.22.1"

    var ddragonVersion: String by mutableStateOf(DEFAULT_DDRAGON_VERSION)
        private set

    /**
     * Incremented every time catalog maps refresh so icon composables recompose cleanly
     * even if the version string itself stayed constant.
     */
    var catalogRevision: Int by mutableIntStateOf(0)
        private set

    private var dynamicChampionImages: Map<String, String> by mutableStateOf(emptyMap())
    private var dynamicItemImages: Map<String, String> by mutableStateOf(emptyMap())
    private var dynamicAugmentImages: Map<String, String> by mutableStateOf(emptyMap())
    private var dynamicTraitImages: Map<String, String> by mutableStateOf(emptyMap())
    private var dynamicApiDisplayNames: Map<String, String> = emptyMap()

    private var preloadedChampionImages: Map<String, String> = emptyMap()
    private var preloadedItemImages: Map<String, String> = emptyMap()
    private var preloadedAugmentImages: Map<String, String> = emptyMap()
    private var preloadedTraitImages: Map<String, String> = emptyMap()

    private var apiDisplayNameMap: Map<String, String> = emptyMap()
    private var championTraitsCatalog: Map<String, List<String>> = emptyMap()
    private var championCostsCatalog: Map<String, Int> = emptyMap()
    private var traitDescriptionCatalog: Map<String, String> = emptyMap()

    private var pbeChampionImages: Map<String, String> = emptyMap()
    private var pbeTraitImages: Map<String, String> = emptyMap()
    private var pbeItemImages: Map<String, String> = emptyMap()
    private var pbeAugmentImages: Map<String, String> = emptyMap()
    private var pbeChampionCosts: Map<String, Int> = emptyMap()
    private var pbeChampionTraits: Map<String, List<String>> = emptyMap()
    private var liveCatalogChampions: List<CatalogChampionInfo> = emptyList()
    private var pbeCatalogChampions: List<CatalogChampionInfo> = emptyList()
    private var liveChampionAbilities: Map<String, Pair<String, String>> = emptyMap()
    private var pbeChampionAbilities: Map<String, Pair<String, String>> = emptyMap()
    private var liveItemDescriptions: Map<String, String> = emptyMap()
    private var pbeItemDescriptions: Map<String, String> = emptyMap()
    private var liveItemComponents: Map<String, List<String>> = emptyMap()
    private var pbeItemComponents: Map<String, List<String>> = emptyMap()
    private var liveAugmentDescriptions: Map<String, String> = emptyMap()
    private var pbeAugmentDescriptions: Map<String, String> = emptyMap()

    private val scrapedItemImages = ConcurrentHashMap<String, String>()

    @Volatile
    private var preloadedCatalogLoaded: Boolean = false

    @Volatile
    private var pbeCatalogLoaded: Boolean = false

    data class CatalogChampionInfo(
        val name: String,
        val cost: Int,
        val traits: List<String>
    )

    data class ParsedDdragonCatalog(
        val version: String?,
        val championImages: Map<String, String>?,
        val itemImages: Map<String, String>?,
        val augmentImages: Map<String, String>?,
        val traitImages: Map<String, String>? = null,
        val apiDisplayNames: Map<String, String> = emptyMap()
    )

    data class ParsedPreloadedSetCatalog(
        val version: String?,
        val championImages: Map<String, String>,
        val itemImages: Map<String, String>,
        val augmentImages: Map<String, String>,
        val traitImages: Map<String, String>,
        val apiDisplayNames: Map<String, String>,
        val championTraits: Map<String, List<String>>,
        val championCosts: Map<String, Int>,
        val traitDescriptions: Map<String, String>,
        val catalogChampions: List<CatalogChampionInfo> = emptyList(),
        val championAbilities: Map<String, Pair<String, String>> = emptyMap(),
        val itemDescriptions: Map<String, String> = emptyMap(),
        val itemComponents: Map<String, List<String>> = emptyMap(),
        val augmentDescriptions: Map<String, String> = emptyMap()
    )

    fun isPreloadedCatalogApplied(): Boolean = preloadedCatalogLoaded

    fun isPbeCatalogApplied(): Boolean = pbeCatalogLoaded

    fun hasSameDynamicVersion(
        version: String?,
        hasChampJson: Boolean,
        hasItemJson: Boolean,
        hasAugmentJson: Boolean
    ): Boolean {
        if (!version.isNullOrBlank() && version != ddragonVersion) return false
        if (hasChampJson && dynamicChampionImages.isEmpty()) return false
        if (hasItemJson && dynamicItemImages.isEmpty()) return false
        if (hasAugmentJson && dynamicAugmentImages.isEmpty()) return false
        return true
    }

    fun isPlaceholderIconUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return true
        val lower = url.lowercase()
        return lower.contains("/missing-") ||
            lower.contains("missing-t1") ||
            lower.contains("missing-t2") ||
            lower.contains("missing-t3") ||
            lower.contains("missing_") ||
            lower.contains("placeholder") ||
            lower.endsWith("/empty.png")
    }

    fun updatePbeSetCatalog(
        championIcons: Map<String, String>,
        championCosts: Map<String, Int>,
        championTraits: Map<String, List<String>>,
        traitIcons: Map<String, String>,
        itemIcons: Map<String, String> = emptyMap(),
        augmentIcons: Map<String, String> = emptyMap(),
        championAbilities: Map<String, Pair<String, String>> = emptyMap(),
        itemDescriptions: Map<String, String> = emptyMap(),
        itemComponents: Map<String, List<String>> = emptyMap(),
        augmentDescriptions: Map<String, String> = emptyMap()
    ) {
        if (championIcons.isEmpty() && traitIcons.isEmpty() && itemIcons.isEmpty() && augmentIcons.isEmpty()) return
        val normChamps = HashMap<String, String>(championIcons.size * 2)
        championIcons.forEach { (k, v) ->
            if (v.isNotBlank() && !isPlaceholderIconUrl(v)) normChamps[normalizeLookupKey(k)] = v
        }
        val normTraits = HashMap<String, String>(traitIcons.size * 2)
        traitIcons.forEach { (k, v) ->
            if (v.isNotBlank() && !isPlaceholderIconUrl(v)) normTraits[normalizeLookupKey(k)] = v
        }
        val normItems = HashMap<String, String>(itemIcons.size * 2)
        itemIcons.forEach { (k, v) ->
            if (v.isNotBlank() && !isPlaceholderIconUrl(v)) normItems[normalizeLookupKey(k)] = v
        }
        val normAugments = HashMap<String, String>(augmentIcons.size * 2)
        augmentIcons.forEach { (k, v) ->
            if (v.isNotBlank() && !isPlaceholderIconUrl(v)) normAugments[normalizeLookupKey(k)] = v
        }
        val normCosts = HashMap<String, Int>(championCosts.size * 2)
        championCosts.forEach { (k, v) ->
            if (v in 1..9) normCosts[normalizeLookupKey(k)] = v
        }
        val normChampTraits = HashMap<String, List<String>>(championTraits.size * 2)
        championTraits.forEach { (k, v) ->
            if (v.isNotEmpty()) normChampTraits[normalizeLookupKey(k)] = v
        }
        val normChampAbilities = HashMap<String, Pair<String, String>>(championAbilities.size * 2)
        championAbilities.forEach { (k, v) ->
            if (v.first.isNotBlank() || v.second.isNotBlank()) normChampAbilities[normalizeLookupKey(k)] = v
        }
        val normItemDescs = HashMap<String, String>(itemDescriptions.size * 2)
        itemDescriptions.forEach { (k, v) ->
            if (v.isNotBlank()) normItemDescs[normalizeLookupKey(k)] = v
        }
        val normItemComps = HashMap<String, List<String>>(itemComponents.size * 2)
        itemComponents.forEach { (k, v) ->
            if (v.isNotEmpty()) normItemComps[normalizeLookupKey(k)] = v
        }
        val normAugDescs = HashMap<String, String>(augmentDescriptions.size * 2)
        augmentDescriptions.forEach { (k, v) ->
            if (v.isNotBlank()) normAugDescs[normalizeLookupKey(k)] = v
        }
        var changed = !pbeCatalogLoaded
        if (normChamps.isNotEmpty() && normChamps != pbeChampionImages) {
            pbeChampionImages = normChamps
            changed = true
        }
        if (normTraits.isNotEmpty() && normTraits != pbeTraitImages) {
            pbeTraitImages = normTraits
            changed = true
        }
        if (normItems.isNotEmpty() && normItems != pbeItemImages) {
            pbeItemImages = normItems
            changed = true
        }
        if (normAugments.isNotEmpty() && normAugments != pbeAugmentImages) {
            pbeAugmentImages = normAugments
            changed = true
        }
        if (normCosts.isNotEmpty() && normCosts != pbeChampionCosts) {
            pbeChampionCosts = normCosts
            changed = true
        }
        if (normChampTraits.isNotEmpty() && normChampTraits != pbeChampionTraits) {
            pbeChampionTraits = normChampTraits
            changed = true
        }
        if (normChampAbilities.isNotEmpty() && normChampAbilities != pbeChampionAbilities) {
            pbeChampionAbilities = normChampAbilities
            changed = true
        }
        if (normItemDescs.isNotEmpty() && normItemDescs != pbeItemDescriptions) {
            pbeItemDescriptions = normItemDescs
            changed = true
        }
        if (normItemComps.isNotEmpty() && normItemComps != pbeItemComponents) {
            pbeItemComponents = normItemComps
            changed = true
        }
        if (normAugDescs.isNotEmpty() && normAugDescs != pbeAugmentDescriptions) {
            pbeAugmentDescriptions = normAugDescs
            changed = true
        }
        val builtPbeCatalog = LinkedHashMap<String, CatalogChampionInfo>()
        championCosts.forEach { (rawName, cost) ->
            val clean = rawName.trim()
            if (clean.length < 2 || cost !in 1..9) return@forEach
            if (clean.contains('_') || clean.startsWith("TFT", ignoreCase = true) ||
                clean.startsWith("DA_", ignoreCase = true) || clean.startsWith("DA18", ignoreCase = true) ||
                clean.startsWith("DA19", ignoreCase = true)
            ) {
                return@forEach
            }
            val normKey = normalizeLookupKey(clean)
            if (normKey.isEmpty() || builtPbeCatalog.containsKey(normKey)) return@forEach
            val traits = championTraits[rawName]
                ?: normChampTraits[normKey]
                ?: emptyList()
            if (traits.isNotEmpty()) {
                builtPbeCatalog[normKey] = CatalogChampionInfo(
                    name = clean,
                    cost = cost,
                    traits = traits
                )
            }
        }
        if (builtPbeCatalog.isNotEmpty()) {
            val nextList = builtPbeCatalog.values.toList()
            if (nextList != pbeCatalogChampions) {
                pbeCatalogChampions = nextList
                changed = true
            }
        }
        pbeCatalogLoaded = true
        if (changed) {
            catalogRevision += 1
        }
    }

    fun parsePreloadedSetCatalogInBackground(rawJson: String): ParsedPreloadedSetCatalog? {
        return runCatching {
            val root = JSONObject(rawJson)
            val version = root.optString("ddragonVersion").takeIf { it.isNotBlank() }

            fun readStringMap(obj: JSONObject?, filterPlaceholders: Boolean = false): Map<String, String> {
                if (obj == null) return emptyMap()
                val out = HashMap<String, String>(obj.length() * 2)
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = obj.optString(k)
                    if (v.isNotBlank() && (!filterPlaceholders || !isPlaceholderIconUrl(v))) {
                        out[normalizeLookupKey(k)] = v
                    }
                }
                return out
            }

            val traitsMap = HashMap<String, List<String>>()
            root.optJSONObject("championTraits")?.let { obj ->
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val arr = obj.optJSONArray(k) ?: continue
                    val list = (0 until arr.length()).mapNotNull { i ->
                        arr.optString(i).takeIf { it.isNotBlank() }
                    }
                    if (list.isNotEmpty()) {
                        traitsMap[normalizeLookupKey(k)] = list
                    }
                }
            }

            val costsMap = HashMap<String, Int>()
            root.optJSONObject("championCosts")?.let { obj ->
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val cost = obj.optInt(k, 0)
                    if (cost in 1..5) {
                        costsMap[normalizeLookupKey(k)] = cost
                    }
                }
            }

            val traitDescMap = HashMap<String, String>()
            root.optJSONArray("traits")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val name = obj.optString("name")
                    val api = obj.optString("apiName")
                    val desc = obj.optString("description")
                    if (desc.isNotBlank()) {
                        if (name.isNotBlank()) traitDescMap[normalizeLookupKey(name)] = desc
                        if (api.isNotBlank()) traitDescMap[normalizeLookupKey(api)] = desc
                    }
                }
            }

            val apiNamesMap = HashMap<String, String>(2048)
            apiNamesMap.putAll(readStringMap(root.optJSONObject("apiIdToName")))

            fun isCleanHumanLabel(raw: String): Boolean {
                val trimmed = raw.trim()
                if (trimmed.length < 2) return false
                if (trimmed.contains('_')) return false
                if (trimmed.startsWith("TFT", ignoreCase = true) ||
                    trimmed.startsWith("DA_", ignoreCase = true) ||
                    trimmed.startsWith("DA18", ignoreCase = true) ||
                    trimmed.startsWith("DA19", ignoreCase = true) ||
                    trimmed.startsWith("Set", ignoreCase = true)
                ) {
                    return false
                }
                return trimmed.any { it.isLetter() }
            }

            fun crossIndexDisplayNamesFromIconObject(obj: JSONObject?) {
                if (obj == null) return
                val byUrl = HashMap<String, MutableList<String>>(obj.length())
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = obj.optString(k)
                    if (v.isNotBlank()) {
                        byUrl.getOrPut(v) { mutableListOf() }.add(k)
                    }
                }
                byUrl.values.forEach { groupKeys ->
                    if (groupKeys.size < 2) return@forEach
                    val preferredHuman = groupKeys
                        .filter { isCleanHumanLabel(it) }
                        .maxByOrNull { label ->
                            var score = 0
                            if (label.contains(' ')) score += 10
                            if (label.contains('\'')) score += 4
                            if (label.first().isUpperCase()) score += 3
                            score += label.length.coerceAtMost(24)
                            score
                        }
                        ?: return@forEach
                    groupKeys.forEach { rawKey ->
                        // Only map raw internal Riot API IDs (e.g. TFT_Item_*, DA_*) to human names;
                        // never remap an already human-readable label onto a sibling sharing an icon URL.
                        if (isCleanHumanLabel(rawKey)) return@forEach
                        val normKey = normalizeLookupKey(rawKey)
                        if (normKey.isNotEmpty() && !apiNamesMap.containsKey(normKey)) {
                            apiNamesMap[normKey] = preferredHuman
                        }
                    }
                }
            }

            crossIndexDisplayNamesFromIconObject(root.optJSONObject("itemIcons"))

            val parsedCatalogChamps = LinkedHashMap<String, CatalogChampionInfo>()
            val parsedChampAbilities = HashMap<String, Pair<String, String>>()
            root.optJSONArray("champions")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val name = obj.optString("name").trim()
                    val cost = obj.optInt("cost", 0)
                    val normKey = normalizeLookupKey(name)
                    val abName = obj.optString("abilityName").trim()
                    val abDesc = sanitizeTftDescription(obj.optString("abilityDescription").trim())
                    if (normKey.isNotEmpty() && (abName.isNotBlank() || abDesc.isNotBlank())) {
                        parsedChampAbilities[normKey] = abName to abDesc
                    }
                    if (name.length < 2 || cost !in 1..7 || name.contains("(")) continue
                    val traitsArr = obj.optJSONArray("traits")
                    val traits = if (traitsArr != null) {
                        (0 until traitsArr.length()).mapNotNull { tIdx ->
                            traitsArr.optString(tIdx).trim().takeIf { it.isNotEmpty() }
                        }
                    } else {
                        emptyList()
                    }
                    if (normKey.isNotEmpty() && traits.isNotEmpty() && !parsedCatalogChamps.containsKey(normKey)) {
                        parsedCatalogChamps[normKey] = CatalogChampionInfo(
                            name = name,
                            cost = cost,
                            traits = traits
                        )
                    }
                }
            }

            val parsedItemDescs = HashMap<String, String>()
            val parsedItemComps = HashMap<String, List<String>>()
            root.optJSONArray("items")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val name = obj.optString("name").trim()
                    val normKey = normalizeLookupKey(name)
                    if (normKey.isEmpty()) continue
                    val summary = sanitizeTftDescription(obj.optString("statsSummary").trim())
                    if (summary.isNotBlank()) parsedItemDescs[normKey] = summary
                    val compArr = obj.optJSONArray("components")
                    if (compArr != null && compArr.length() > 0) {
                        val list = (0 until compArr.length()).mapNotNull { cIdx ->
                            compArr.optString(cIdx).trim().takeIf { it.isNotEmpty() }
                        }
                        if (list.isNotEmpty()) parsedItemComps[normKey] = list
                    }
                }
            }

            val parsedAugDescs = HashMap<String, String>()
            root.optJSONArray("augments")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val name = obj.optString("name").trim()
                    val normKey = normalizeLookupKey(name)
                    if (normKey.isEmpty()) continue
                    val desc = sanitizeTftDescription(obj.optString("description").trim())
                    if (desc.isNotBlank()) parsedAugDescs[normKey] = desc
                }
            }

            ParsedPreloadedSetCatalog(
                version = version,
                championImages = readStringMap(root.optJSONObject("championIcons"), filterPlaceholders = true),
                itemImages = readStringMap(root.optJSONObject("itemIcons"), filterPlaceholders = true),
                augmentImages = readStringMap(root.optJSONObject("augmentIcons"), filterPlaceholders = true),
                traitImages = readStringMap(root.optJSONObject("traitIcons"), filterPlaceholders = true),
                apiDisplayNames = apiNamesMap,
                championTraits = traitsMap,
                championCosts = costsMap,
                traitDescriptions = traitDescMap,
                catalogChampions = parsedCatalogChamps.values.toList(),
                championAbilities = parsedChampAbilities,
                itemDescriptions = parsedItemDescs,
                itemComponents = parsedItemComps,
                augmentDescriptions = parsedAugDescs
            )
        }.getOrNull()
    }

    fun applyPreloadedSetCatalog(parsed: ParsedPreloadedSetCatalog) {
        if (!parsed.version.isNullOrBlank() && dynamicChampionImages.isEmpty()) {
            ddragonVersion = parsed.version
        }
        preloadedChampionImages = parsed.championImages
        preloadedItemImages = parsed.itemImages
        preloadedAugmentImages = parsed.augmentImages
        preloadedTraitImages = parsed.traitImages
        apiDisplayNameMap = parsed.apiDisplayNames
        championTraitsCatalog = parsed.championTraits
        championCostsCatalog = parsed.championCosts
        traitDescriptionCatalog = parsed.traitDescriptions
        if (parsed.catalogChampions.isNotEmpty()) {
            liveCatalogChampions = parsed.catalogChampions
        }
        if (parsed.championAbilities.isNotEmpty()) {
            liveChampionAbilities = parsed.championAbilities
        }
        if (parsed.itemDescriptions.isNotEmpty()) {
            liveItemDescriptions = parsed.itemDescriptions
        }
        if (parsed.itemComponents.isNotEmpty()) {
            liveItemComponents = parsed.itemComponents
        }
        if (parsed.augmentDescriptions.isNotEmpty()) {
            liveAugmentDescriptions = parsed.augmentDescriptions
        }
        preloadedCatalogLoaded = true
        catalogRevision += 1
    }

    fun registerScrapedItemIcon(nameOrId: String, rawUrl: String?) {
        val url = rawUrl?.trim().orEmpty()
        if (!url.startsWith("https://", ignoreCase = true)) return
        val raw = nameOrId.trim()
        if (raw.isEmpty()) return
        val norm = normalizeLookupKey(raw)
        val cleanNorm = normalizeLookupKey(formatRiotAssetLabel(raw))
        var updated = false
        if (norm.isNotEmpty() && scrapedItemImages.putIfAbsent(norm, url) == null) {
            updated = true
        }
        if (cleanNorm.isNotEmpty() && cleanNorm != norm && scrapedItemImages.putIfAbsent(cleanNorm, url) == null) {
            updated = true
        }
        if (updated) {
            catalogRevision += 1
        }
    }

    fun parseDdragonCatalogInBackground(
        version: String?,
        championJson: String? = null,
        itemJson: String? = null,
        augmentJson: String? = null,
        traitJson: String? = null
    ): ParsedDdragonCatalog {
        val namesOut = HashMap<String, String>(1024)
        val champs = championJson?.takeIf { it.isNotBlank() }
            ?.let { parseDdragonAssetJson(it, defaultGroup = "tft-champion", displayNamesOut = namesOut).takeIf { m -> m.isNotEmpty() } }
        val items = itemJson?.takeIf { it.isNotBlank() }
            ?.let { parseDdragonAssetJson(it, defaultGroup = "tft-item", displayNamesOut = namesOut).takeIf { m -> m.isNotEmpty() } }
        val augments = augmentJson?.takeIf { it.isNotBlank() }
            ?.let {
                parseDdragonAssetJson(
                    jsonText = it,
                    defaultGroup = "tft-augment",
                    skipMissingPlaceholderImages = true,
                    displayNamesOut = namesOut
                ).takeIf { m -> m.isNotEmpty() }
            }
        val traits = traitJson?.takeIf { it.isNotBlank() }
            ?.let { parseDdragonAssetJson(it, defaultGroup = "tft-trait", displayNamesOut = namesOut).takeIf { m -> m.isNotEmpty() } }
        return ParsedDdragonCatalog(
            version = version?.takeIf { it.isNotBlank() },
            championImages = champs,
            itemImages = items,
            augmentImages = augments,
            traitImages = traits,
            apiDisplayNames = namesOut
        )
    }

    fun applyParsedDdragonCatalog(parsed: ParsedDdragonCatalog) {
        var changed = false
        if (!parsed.version.isNullOrBlank() && parsed.version != ddragonVersion) {
            ddragonVersion = parsed.version
            changed = true
        }
        parsed.championImages?.let {
            if (it != dynamicChampionImages) {
                dynamicChampionImages = it
                changed = true
            }
        }
        parsed.itemImages?.let {
            if (it != dynamicItemImages) {
                dynamicItemImages = it
                changed = true
            }
        }
        parsed.augmentImages?.let {
            if (it != dynamicAugmentImages) {
                dynamicAugmentImages = it
                changed = true
            }
        }
        parsed.traitImages?.let {
            if (it != dynamicTraitImages) {
                dynamicTraitImages = it
                changed = true
            }
        }
        if (parsed.apiDisplayNames.isNotEmpty() && parsed.apiDisplayNames != dynamicApiDisplayNames) {
            dynamicApiDisplayNames = parsed.apiDisplayNames
            changed = true
        }
        if (changed) {
            catalogRevision += 1
        }
    }

    fun updateDdragonCatalog(
        version: String?,
        championJson: String? = null,
        itemJson: String? = null,
        augmentJson: String? = null,
        traitJson: String? = null
    ) {
        applyParsedDdragonCatalog(
            parseDdragonCatalogInBackground(
                version = version,
                championJson = championJson,
                itemJson = itemJson,
                augmentJson = augmentJson,
                traitJson = traitJson
            )
        )
    }

    private fun lookupCleanCatalogDisplayName(normKey: String): String? {
        if (normKey.isEmpty()) return null
        BUILTIN_DISPLAY_NAME_OVERRIDES[normKey]?.let { return it }
        dynamicApiDisplayNames[normKey]
            ?.takeIf { isValidResolvedDisplayName(it) }
            ?.let { return it }
        apiDisplayNameMap[normKey]
            ?.takeIf { isValidResolvedDisplayName(it) }
            ?.let { return it }
        return null
    }

    private fun isValidResolvedDisplayName(candidate: String): Boolean {
        return candidate.isNotBlank() &&
            !candidate.contains('_') &&
            !candidate.startsWith("TFT", ignoreCase = true) &&
            !candidate.startsWith("DA_", ignoreCase = true) &&
            !candidate.startsWith("DA18", ignoreCase = true) &&
            !candidate.startsWith("DA19", ignoreCase = true)
    }

    fun resolveDisplayName(rawId: String): String? {
        val raw = rawId.trim()
        if (raw.isEmpty()) return null
        // Never strip '+' / '++' tier suffixes from human-readable augment names (e.g. "Advanced Loan+")
        if (raw.endsWith("+") && !raw.contains('_')) return null
        val norm = normalizeLookupKey(raw)
        lookupCleanCatalogDisplayName(norm)?.let { return it }

        // Resolve Radiant item variants (e.g. TFT5_Item_RapidFirecannonRadiant -> Radiant Red Buff,
        // DA_BlueBuffRadiant -> Radiant Blue Buff, radiant-jeweled-gauntlet -> Radiant Jeweled Gauntlet)
        val isRadiantVariant = (norm.endsWith("radiant") && norm.length > 9) ||
            (norm.startsWith("radiant") && norm.length > 9 && !norm.startsWith("radiantvirtue"))
        if (isRadiantVariant) {
            val baseNorm = norm
                .removePrefix("tft5item")
                .removePrefix("tftitem")
                .removePrefix("da18")
                .removePrefix("da19")
                .removePrefix("da")
                .removePrefix("radiant")
                .removeSuffix("radiant")
            if (baseNorm.isNotEmpty()) {
                val baseResolved = lookupCleanCatalogDisplayName("tftitem$baseNorm")
                    ?: lookupCleanCatalogDisplayName(baseNorm)
                if (!baseResolved.isNullOrBlank()) {
                    return if (baseResolved.startsWith("Radiant ", ignoreCase = true)) {
                        baseResolved
                    } else {
                        "Radiant $baseResolved"
                    }
                }
            }
        }
        return null
    }

    fun getChampionTraits(nameOrId: String, environment: TftEnvironment? = null): List<String> {
        val norm = normalizeLookupKey(nameOrId)
        val cleanNorm = normalizeLookupKey(formatChampionDisplayName(nameOrId))
        if (environment == TftEnvironment.PBE) {
            (pbeChampionTraits[norm] ?: pbeChampionTraits[cleanNorm])?.let { return it }
        }
        return championTraitsCatalog[norm]
            ?: championTraitsCatalog[cleanNorm]
            ?: pbeChampionTraits[norm]
            ?: pbeChampionTraits[cleanNorm]
            ?: emptyList()
    }

    fun getChampionCost(nameOrId: String, environment: TftEnvironment? = null): Int? {
        val norm = normalizeLookupKey(nameOrId)
        val cleanNorm = normalizeLookupKey(formatChampionDisplayName(nameOrId))
        if (environment == TftEnvironment.PBE) {
            (pbeChampionCosts[norm] ?: pbeChampionCosts[cleanNorm])?.let { return it }
        }
        return championCostsCatalog[norm]
            ?: championCostsCatalog[cleanNorm]
            ?: pbeChampionCosts[norm]
            ?: pbeChampionCosts[cleanNorm]
    }

    fun getCatalogChampions(environment: TftEnvironment): List<CatalogChampionInfo> =
        when (environment) {
            TftEnvironment.LIVE -> liveCatalogChampions
            TftEnvironment.PBE -> pbeCatalogChampions
        }

    fun isKnownChampion(nameOrId: String, environment: TftEnvironment? = null): Boolean {
        val raw = nameOrId.trim()
        if (raw.isEmpty()) return false
        val lower = raw.lowercase()
        if (lower.contains("_item_") || lower.contains("tft_item") || lower.contains("tft-item") ||
            lower.contains("_augment_") || lower.contains("tft_augment") || lower.contains("tft-augment") ||
            lower.contains("_trait_") || lower.contains("tft_trait") || lower.contains("tft-trait") ||
            lower.contains("da_component")
        ) {
            return false
        }
        val norm = normalizeLookupKey(raw)
        val cleanName = formatChampionDisplayName(raw)
        val cleanNorm = normalizeLookupKey(cleanName)
        val baseCleanNorm = if (cleanName.contains("(")) {
            normalizeLookupKey(cleanName.substringBefore("(").trim())
        } else {
            cleanNorm
        }
        val keys = listOf(norm, cleanNorm, baseCleanNorm).filter { it.isNotEmpty() }
        return keys.any { k ->
            championCostsCatalog.containsKey(k) ||
                pbeChampionCosts.containsKey(k) ||
                preloadedChampionImages.containsKey(k) ||
                dynamicChampionImages.containsKey(k) ||
                pbeChampionImages.containsKey(k) ||
                championTraitsCatalog.containsKey(k) ||
                pbeChampionTraits.containsKey(k) ||
                SET18_SQUARE_PORTRAIT_KEYS.containsKey(k) ||
                SPECIAL_CHAMPION_KEYS.containsKey(k) ||
                NON_LOL_CHAMPION_KEYS.contains(k)
        }
    }

    fun isKnownItem(nameOrId: String, environment: TftEnvironment? = null): Boolean {
        val raw = nameOrId.trim()
        if (raw.isEmpty()) return false
        val lower = raw.lowercase()
        if (lower.contains("_item_") || lower.contains("tft_item") || lower.contains("tft-item") ||
            lower.contains("/item") || lower.contains("da_component") || lower.contains("tft_assist_")
        ) {
            return true
        }
        if (isKnownChampion(raw, environment)) return false
        val norm = normalizeLookupKey(raw)
        val cleanName = formatRiotAssetLabel(raw)
        val cleanNorm = normalizeLookupKey(cleanName)
        val keys = listOf(norm, cleanNorm).filter { it.isNotEmpty() }
        if (keys.any { k ->
                ITEM_FILENAME_MAP.containsKey(k) ||
                    preloadedItemImages.containsKey(k) ||
                    dynamicItemImages.containsKey(k) ||
                    pbeItemImages.containsKey(k)
            }
        ) {
            return true
        }
        val cleanLower = cleanName.lowercase()
        return KNOWN_ITEM_NAME_KEYWORDS.any { kw -> cleanLower.contains(kw) }
    }

    fun isKnownNonChampionAsset(nameOrId: String, environment: TftEnvironment? = null): Boolean {
        val raw = nameOrId.trim()
        if (raw.isEmpty()) return true
        val lower = raw.lowercase()
        if (lower.contains("_item_") || lower.contains("tft_item") || lower.contains("tft-item") ||
            lower.contains("/item") || lower.contains("da_component") ||
            lower.contains("_augment_") || lower.contains("tft_augment") || lower.contains("tft-augment") ||
            lower.contains("/augment") ||
            lower.contains("_trait_") || lower.contains("tft_trait") || lower.contains("tft-trait") ||
            lower.contains("/trait")
        ) {
            return true
        }
        if (isKnownItem(raw, environment)) return true
        if (isKnownChampion(raw, environment)) return false

        val norm = normalizeLookupKey(raw)
        val cleanName = formatRiotAssetLabel(raw)
        val cleanNorm = normalizeLookupKey(cleanName)
        val strippedAugNorm = normalizeLookupKey(
            cleanName.replace("+", "").replace(REGEX_AUGMENT_TIER_SUFFIX, "")
        )
        val keys = listOf(norm, cleanNorm, strippedAugNorm).filter { it.isNotEmpty() }
        return keys.any { k ->
            AUGMENT_FILENAME_MAP.containsKey(k) ||
                preloadedAugmentImages.containsKey(k) ||
                dynamicAugmentImages.containsKey(k) ||
                preloadedTraitImages.containsKey(k) ||
                dynamicTraitImages.containsKey(k) ||
                pbeTraitImages.containsKey(k) ||
                traitDescriptionCatalog.containsKey(k)
        }
    }

    fun resolveTraitDescription(traitNameOrId: String): String? {
        val clean = traitNameOrId.replace(REGEX_LEADING_COUNT, "").trim()
        val norm = normalizeLookupKey(clean)
        val raw = traitDescriptionCatalog[norm] ?: traitDescriptionCatalog[normalizeLookupKey(traitNameOrId)]
        return raw?.let { sanitizeTftDescription(it) }?.takeIf { it.isNotBlank() }
    }

    fun resolveChampionAbility(nameOrId: String, environment: TftEnvironment? = null): Pair<String, String>? {
        val norm = normalizeLookupKey(nameOrId)
        val cleanNorm = normalizeLookupKey(formatChampionDisplayName(nameOrId))
        val keys = listOf(cleanNorm, norm).filter { it.isNotEmpty() }
        if (environment == TftEnvironment.PBE) {
            keys.firstNotNullOfOrNull { pbeChampionAbilities[it] }?.let { return it }
            keys.firstNotNullOfOrNull { liveChampionAbilities[it] }?.let { return it }
        } else {
            keys.firstNotNullOfOrNull { liveChampionAbilities[it] }?.let { return it }
            keys.firstNotNullOfOrNull { pbeChampionAbilities[it] }?.let { return it }
        }
        return null
    }

    fun resolveItemDescription(nameOrId: String, environment: TftEnvironment? = null): String? {
        val cleanName = formatRiotAssetLabel(nameOrId)
        val cleanNorm = normalizeLookupKey(cleanName)
        val rawNorm = normalizeLookupKey(nameOrId)
        val keys = listOf(cleanNorm, rawNorm).filter { it.isNotEmpty() }
        val primary = if (environment == TftEnvironment.PBE) pbeItemDescriptions else liveItemDescriptions
        val secondary = if (environment == TftEnvironment.PBE) liveItemDescriptions else pbeItemDescriptions
        keys.firstNotNullOfOrNull { primary[it] }?.takeIf { it.isNotBlank() }?.let { return it }
        keys.firstNotNullOfOrNull { secondary[it] }?.takeIf { it.isNotBlank() }?.let { return it }
        if (cleanName.endsWith(" Emblem", ignoreCase = true)) {
            val trait = cleanName.removeSuffix(" Emblem").removeSuffix(" emblem").trim()
            if (trait.isNotBlank()) {
                return "Wearer gains the $trait trait."
            }
        }
        if (cleanName.startsWith("Radiant ", ignoreCase = true)) {
            val baseName = cleanName.removePrefix("Radiant ").trim()
            val baseDesc = resolveItemDescription(baseName, environment)
            if (!baseDesc.isNullOrBlank()) {
                return "Radiant upgrade of $baseName: $baseDesc"
            }
        }
        return null
    }

    fun resolveItemComponents(nameOrId: String, environment: TftEnvironment? = null): List<String> {
        val cleanNorm = normalizeLookupKey(formatRiotAssetLabel(nameOrId))
        val rawNorm = normalizeLookupKey(nameOrId)
        val keys = listOf(cleanNorm, rawNorm).filter { it.isNotEmpty() }
        val primary = if (environment == TftEnvironment.PBE) pbeItemComponents else liveItemComponents
        val secondary = if (environment == TftEnvironment.PBE) liveItemComponents else pbeItemComponents
        return keys.firstNotNullOfOrNull { primary[it] }
            ?: keys.firstNotNullOfOrNull { secondary[it] }
            ?: emptyList()
    }

    fun resolveAugmentDescription(nameOrId: String, environment: TftEnvironment? = null): String? {
        val cleanName = formatRiotAssetLabel(nameOrId)
        val cleanNorm = normalizeLookupKey(cleanName)
        val rawNorm = normalizeLookupKey(nameOrId)
        val strippedNorm = normalizeLookupKey(
            cleanName.replace("+", "").replace(REGEX_AUGMENT_TIER_SUFFIX, "")
        )
        val keys = listOf(cleanNorm, rawNorm, strippedNorm).filter { it.isNotEmpty() }
        // Prefer PBE's exact numeric descriptions when LIVE only has placeholder text, or match requested env first
        val primary = if (environment == TftEnvironment.PBE) pbeAugmentDescriptions else liveAugmentDescriptions
        val secondary = if (environment == TftEnvironment.PBE) liveAugmentDescriptions else pbeAugmentDescriptions
        val primaryMatch = keys.firstNotNullOfOrNull { primary[it] }?.takeIf { it.isNotBlank() }
        val secondaryMatch = keys.firstNotNullOfOrNull { secondary[it] }?.takeIf { it.isNotBlank() }
        if (primaryMatch != null && !containsUnresolvedPlaceholderX(primaryMatch)) {
            return primaryMatch
        }
        if (secondaryMatch != null && !containsUnresolvedPlaceholderX(secondaryMatch)) {
            return secondaryMatch
        }
        if (primaryMatch != null) return sanitizeTftDescription(primaryMatch)
        if (secondaryMatch != null) return sanitizeTftDescription(secondaryMatch)
        if (cleanName.endsWith(" Crest", ignoreCase = true) || cleanName.endsWith(" Crown", ignoreCase = true)) {
            val trait = cleanName
                .removeSuffix(" Crest").removeSuffix(" crest")
                .removeSuffix(" Crown").removeSuffix(" crown")
                .trim()
            if (trait.isNotBlank()) {
                return if (cleanName.endsWith(" Crown", ignoreCase = true)) {
                    "Gain a $trait Emblem, a core item, and a $trait champion."
                } else {
                    "Gain a $trait Emblem and a $trait champion."
                }
            }
        }
        return null
    }

    private fun containsUnresolvedPlaceholderX(text: String): Boolean =
        Regex("""(?:^|[\s(+-])X(?=$|[\s%.,;:)/+-])""").containsMatchIn(text)

    fun resolveTraitIconUrl(traitNameOrId: String, environment: TftEnvironment? = null): String? {
        val clean = traitNameOrId.replace(REGEX_LEADING_COUNT, "").trim()
        if (clean.isEmpty()) return null
        val norm = normalizeLookupKey(clean)
        val rawNorm = normalizeLookupKey(traitNameOrId)
        if (environment == TftEnvironment.PBE) {
            val pbeUrl = pbeTraitImages[norm] ?: pbeTraitImages[rawNorm]
            if (!pbeUrl.isNullOrBlank()) return pbeUrl
        }
        val dynamicPath = dynamicTraitImages[norm]
            ?: dynamicTraitImages[rawNorm]
            ?: dynamicTraitImages["da18$norm"]
        if (!dynamicPath.isNullOrBlank()) {
            return toDdragonImgUrl(dynamicPath)
        }
        val preloaded = preloadedTraitImages[norm] ?: preloadedTraitImages[rawNorm]
        if (!preloaded.isNullOrBlank()) {
            return preloaded
        }
        if (environment == TftEnvironment.LIVE) return null
        return pbeTraitImages[norm] ?: pbeTraitImages[rawNorm]
    }

    fun resolveChampionIconUrl(nameOrId: String, environment: TftEnvironment? = null): String? {
        val raw = nameOrId.trim()
        if (raw.isEmpty()) return null

        val norm = normalizeLookupKey(raw)
        val cleanName = formatChampionDisplayName(raw)
        val cleanNorm = normalizeLookupKey(cleanName)

        // 0. For PBE comps/champions, prefer verified Set 19 portraits first
        if (environment == TftEnvironment.PBE) {
            val pbeUrl = pbeChampionImages[norm] ?: pbeChampionImages[cleanNorm]
            if (!pbeUrl.isNullOrBlank()) {
                return pbeUrl
            }
        }

        // 1. Check dynamically fetched Riot tft-champion.json map
        val dynamicPath = dynamicChampionImages[norm] ?: dynamicChampionImages[cleanNorm]
        if (!dynamicPath.isNullOrBlank()) {
            return toDdragonImgUrl(dynamicPath)
        }

        // 2. Check preloaded Set 18 exact DDragon/TFTAcademy champion icon catalog
        val preloaded = preloadedChampionImages[norm] ?: preloadedChampionImages[cleanNorm]
        if (!preloaded.isNullOrBlank()) {
            return preloaded
        }

        // 3. Skip invalid League CDN URL guesses for TFT-exclusive creatures/NPCs
        //    (and allow Set 19 PBE fallback for non-LoL units only when not strictly LIVE)
        if (NON_LOL_CHAMPION_KEYS.contains(cleanNorm) || NON_LOL_CHAMPION_KEYS.contains(norm)) {
            if (environment != TftEnvironment.LIVE) {
                val pbeOnlyUrl = pbeChampionImages[norm] ?: pbeChampionImages[cleanNorm]
                if (!pbeOnlyUrl.isNullOrBlank()) return pbeOnlyUrl
            }
            return null
        }

        // 4. Fall back to standard League Data Dragon champion portrait URL
        val key = SPECIAL_CHAMPION_KEYS[cleanNorm]
            ?: SPECIAL_CHAMPION_KEYS[norm]
            ?: toChampionKey(cleanName)
        if (key.isNotBlank()) {
            return "https://ddragon.leagueoflegends.com/cdn/$ddragonVersion/img/champion/$key.png"
        }

        // 5. Final PBE fallback when environment is unspecified
        return if (environment != TftEnvironment.LIVE) {
            pbeChampionImages[norm] ?: pbeChampionImages[cleanNorm]
        } else null
    }

    /**
     * Resolves a true 1:1 square centered champion portrait URL so champion faces/icons
     * fill hex/diamond cells and roster tiles completely without 1024x512 splash cropping.
     * Falls back seamlessly to [resolveChampionIconUrl].
     */
    fun resolveSquareChampionPortraitUrl(nameOrId: String, environment: TftEnvironment? = null): String? {
        val raw = nameOrId.trim()
        if (raw.isEmpty()) return null

        val norm = normalizeLookupKey(raw)
        val cleanName = formatChampionDisplayName(raw)
        val cleanNorm = normalizeLookupKey(cleanName)
        val baseCleanNorm = if (cleanName.contains("(")) {
            normalizeLookupKey(cleanName.substringBefore("(").trim())
        } else {
            cleanNorm
        }

        if (environment == TftEnvironment.PBE) {
            val pbeUrl = pbeChampionImages[norm]
                ?: pbeChampionImages[cleanNorm]
                ?: pbeChampionImages[baseCleanNorm]
            if (!pbeUrl.isNullOrBlank()) {
                return pbeUrl
            }
        }

        val set18Key = SET18_SQUARE_PORTRAIT_KEYS[cleanNorm]
            ?: SET18_SQUARE_PORTRAIT_KEYS[norm]
            ?: SET18_SQUARE_PORTRAIT_KEYS[baseCleanNorm]
        if (!set18Key.isNullOrBlank()) {
            return "https://sunderarmor.com/characters/Skin/18/$set18Key.png"
        }

        return resolveChampionIconUrl(nameOrId, environment)
    }

    /**
     * Ordered, deduplicated HTTPS portrait candidates for a champion so [TftChampionAvatar]
     * can seamlessly step through square portraits, Data Dragon, and preloaded fallbacks.
     */
    fun resolveChampionIconFallbackUrls(nameOrId: String, environment: TftEnvironment? = null): List<String> {
        val raw = nameOrId.trim()
        if (raw.isEmpty()) return emptyList()
        val norm = normalizeLookupKey(raw)
        val cleanName = formatChampionDisplayName(raw)
        val cleanNorm = normalizeLookupKey(cleanName)
        val baseCleanNorm = if (cleanName.contains("(")) {
            normalizeLookupKey(cleanName.substringBefore("(").trim())
        } else {
            cleanNorm
        }

        val urls = LinkedHashSet<String>(6)
        fun addUrl(url: String?) {
            if (!url.isNullOrBlank() && url.startsWith("https://", ignoreCase = true) && !isPlaceholderIconUrl(url)) {
                urls.add(url)
            }
        }

        addUrl(resolveSquareChampionPortraitUrl(raw, environment))
        addUrl(resolveChampionIconUrl(raw, environment))

        if (environment == TftEnvironment.PBE) {
            addUrl(pbeChampionImages[norm])
            addUrl(pbeChampionImages[cleanNorm])
            addUrl(pbeChampionImages[baseCleanNorm])
        }
        dynamicChampionImages[norm]?.let { addUrl(toDdragonImgUrl(it)) }
        dynamicChampionImages[cleanNorm]?.let { addUrl(toDdragonImgUrl(it)) }
        addUrl(preloadedChampionImages[norm])
        addUrl(preloadedChampionImages[cleanNorm])
        addUrl(preloadedChampionImages[baseCleanNorm])

        if (!NON_LOL_CHAMPION_KEYS.contains(cleanNorm) && !NON_LOL_CHAMPION_KEYS.contains(norm)) {
            val key = SPECIAL_CHAMPION_KEYS[cleanNorm]
                ?: SPECIAL_CHAMPION_KEYS[norm]
                ?: toChampionKey(cleanName)
            if (key.isNotBlank()) {
                addUrl("https://ddragon.leagueoflegends.com/cdn/$ddragonVersion/img/champion/$key.png")
            }
        }
        if (environment != TftEnvironment.LIVE) {
            addUrl(pbeChampionImages[norm])
            addUrl(pbeChampionImages[cleanNorm])
        }
        return urls.toList()
    }

    fun resolveItemIconUrl(nameOrId: String, environment: TftEnvironment? = null): String? =
        resolveItemIconFallbackUrls(nameOrId, environment).firstOrNull()

    fun resolveItemIconFallbackUrls(nameOrId: String, environment: TftEnvironment? = null): List<String> {
        val raw = nameOrId.trim()
        if (raw.isEmpty()) return emptyList()

        val norm = normalizeLookupKey(raw)
        val cleanName = formatRiotAssetLabel(raw)
        val cleanNorm = normalizeLookupKey(cleanName)

        val candidateKeys = LinkedHashSet<String>(16).apply {
            if (cleanNorm.isNotEmpty()) {
                add(cleanNorm)
                add("da$cleanNorm")
                add("da18$cleanNorm")
                add("daartifact$cleanNorm")
                add("tftitem$cleanNorm")
                add("tftitemartifact$cleanNorm")
                if (cleanNorm.startsWith("radiant") && cleanNorm.length > 7) {
                    val base = cleanNorm.removePrefix("radiant")
                    add("${base}radiant")
                    add("da${base}radiant")
                    add("tft5item${base}radiant")
                }
                if (cleanNorm.endsWith("emblem") && cleanNorm.length > 6) {
                    val trait = cleanNorm.removeSuffix("emblem")
                    add("da18emblem$trait")
                    add("da19emblem$trait")
                    add("tft18item${trait}emblemitem")
                    add("tft19item${trait}emblemitem")
                }
            }
            if (norm.isNotEmpty() && norm != cleanNorm) {
                add(norm)
            }
        }

        val urls = LinkedHashSet<String>(6)
        fun addUrl(url: String?) {
            if (!url.isNullOrBlank() && url.startsWith("https://", ignoreCase = true) && !isPlaceholderIconUrl(url)) {
                urls.add(url)
            }
        }

        // 0. For PBE items, prefer TFTactics Set 19 SunderArmor item icons
        if (environment == TftEnvironment.PBE) {
            for (k in candidateKeys) {
                addUrl(pbeItemImages[k])
            }
        }

        // 1. Dynamically fetched Riot tft-item.json map (prefer resolved official name first)
        for (k in candidateKeys) {
            dynamicItemImages[k]?.takeIf { it.isNotBlank() }?.let { addUrl(toDdragonImgUrl(it)) }
        }

        // 2. Preloaded Set 18 exact DDragon/TFTAcademy item icon catalog
        for (k in candidateKeys) {
            addUrl(preloadedItemImages[k])
        }

        // 3. Exact Data Dragon TFT item filename map
        for (k in candidateKeys) {
            ITEM_FILENAME_MAP[k]?.takeIf { it.isNotBlank() }?.let { staticFile ->
                addUrl("https://ddragon.leagueoflegends.com/cdn/$ddragonVersion/img/tft-item/$staticFile")
            }
        }

        // 4. PBE SunderArmor fallback when environment wasn't PBE
        if (environment != TftEnvironment.PBE) {
            for (k in candidateKeys) {
                addUrl(pbeItemImages[k])
            }
        }

        // 5. Real scraped item icon URLs from tft.ninja (handles brand-new set items/emblems)
        for (k in candidateKeys) {
            addUrl(scrapedItemImages[k])
        }

        // 6. Structured Data Dragon filename fallback
        if (cleanNorm.startsWith("radiant") && cleanNorm.length > 7) {
            val baseNorm = cleanNorm.removePrefix("radiant")
            val mappedBaseFile = ITEM_FILENAME_MAP[baseNorm]
            if (!mappedBaseFile.isNullOrBlank()) {
                val radiantFile = mappedBaseFile
                    .replace("TFT_Item_", "TFT5_Item_")
                    .replace(".png", "Radiant.png")
                addUrl("https://ddragon.leagueoflegends.com/cdn/$ddragonVersion/img/tft-item/$radiantFile")
            }
        }
        if (raw.startsWith("TFT", ignoreCase = true)) {
            val suffix = raw.substringAfterLast("_").replace(Regex("[^A-Za-z0-9]"), "")
            if (suffix.isNotBlank()) {
                addUrl("https://ddragon.leagueoflegends.com/cdn/$ddragonVersion/img/tft-item/TFT_Item_$suffix.png")
            }
        }
        val pascal = cleanName.split(Regex("[^A-Za-z0-9]+"))
            .filter { it.isNotBlank() }
            .joinToString("") { part -> part.replaceFirstChar { c -> c.uppercaseChar() } }
        if (pascal.isNotBlank()) {
            addUrl("https://ddragon.leagueoflegends.com/cdn/$ddragonVersion/img/tft-item/TFT_Item_$pascal.png")
            addUrl("https://sunderarmor.com/items/$pascal.png")
        }

        return urls.toList()
    }

    fun resolveAugmentIconUrl(nameOrId: String, environment: TftEnvironment? = null): String? =
        resolveAugmentIconFallbackUrls(nameOrId, environment).firstOrNull()

    fun resolveAugmentIconFallbackUrls(nameOrId: String, environment: TftEnvironment? = null): List<String> {
        val raw = nameOrId.trim()
        if (raw.isEmpty()) return emptyList()

        val norm = normalizeLookupKey(raw)
        val cleanName = formatRiotAssetLabel(raw)
        val cleanNorm = normalizeLookupKey(cleanName)
        val strippedTierNorm = normalizeLookupKey(
            cleanName
                .replace("+", "")
                .replace(REGEX_AUGMENT_TIER_SUFFIX, "")
        )
        val strippedPlusNorm = strippedTierNorm
            .removeSuffix("plusplus")
            .removeSuffix("plus")
        val candidateKeys = LinkedHashSet<String>(14).apply {
            if (norm.isNotEmpty()) add(norm)
            if (cleanNorm.isNotEmpty()) {
                add(cleanNorm)
                add("da$cleanNorm")
                add("da18$cleanNorm")
                add("da19$cleanNorm")
            }
            if (strippedTierNorm.isNotEmpty()) {
                add(strippedTierNorm)
                add("da$strippedTierNorm")
                add("da18$strippedTierNorm")
            }
            if (strippedPlusNorm.isNotEmpty()) {
                add(strippedPlusNorm)
                add("da$strippedPlusNorm")
                add("da18$strippedPlusNorm")
            }
        }

        val urls = LinkedHashSet<String>(8)
        fun addUrl(url: String?) {
            if (!url.isNullOrBlank() && url.startsWith("https://", ignoreCase = true) && !isPlaceholderIconUrl(url)) {
                urls.add(url)
            }
        }

        // 0. For PBE augments, check preloaded PBE Set 19 augment icon map first
        if (environment == TftEnvironment.PBE) {
            for (k in candidateKeys) {
                addUrl(pbeAugmentImages[k])
            }
        }

        // 1. Dynamically fetched Riot tft-augments.json map (real non-missing icons)
        for (k in candidateKeys) {
            dynamicAugmentImages[k]?.takeIf { it.isNotBlank() }?.let { addUrl(toDdragonImgUrl(it)) }
        }

        // 2. Preloaded Set 18 exact DDragon/TFTAcademy 200-OK augment icon catalog
        for (k in candidateKeys) {
            addUrl(preloadedAugmentImages[k])
        }

        // 3. Evergreen / well-known Data Dragon augment filenames
        for (k in candidateKeys) {
            AUGMENT_FILENAME_MAP[k]?.takeIf { it.isNotBlank() }?.let { staticFile ->
                addUrl("https://ddragon.leagueoflegends.com/cdn/$ddragonVersion/img/tft-augment/$staticFile")
            }
        }

        // 4. PBE fallback if environment wasn't PBE
        if (environment != TftEnvironment.PBE) {
            for (k in candidateKeys) {
                addUrl(pbeAugmentImages[k])
            }
        }

        // 5. Structured SunderArmor fallback for brand-new set augments
        val pascal = cleanName
            .replace("+", "")
            .replace(REGEX_AUGMENT_TIER_SUFFIX, "")
            .split(REGEX_NON_ALNUM)
            .filter { it.isNotBlank() }
            .joinToString("") { part -> part.replaceFirstChar { c -> c.uppercaseChar() } }
        if (pascal.isNotBlank()) {
            addUrl("https://sunderarmor.com/augments/$pascal.png")
            addUrl("https://sunderarmor.com/augments/${pascal}2.png")
            addUrl("https://sunderarmor.com/augments/${pascal}3.png")
        }

        return urls.toList()
    }

    internal fun parseDdragonChampionJson(jsonText: String): Map<String, String> =
        parseDdragonAssetJson(jsonText, defaultGroup = "tft-champion")

    internal fun parseDdragonItemJson(jsonText: String): Map<String, String> =
        parseDdragonAssetJson(jsonText, defaultGroup = "tft-item")

    internal fun parseDdragonAugmentJson(jsonText: String): Map<String, String> =
        parseDdragonAssetJson(jsonText, defaultGroup = "tft-augment", skipMissingPlaceholderImages = true)

    private fun parseDdragonAssetJson(
        jsonText: String,
        defaultGroup: String,
        skipMissingPlaceholderImages: Boolean = false,
        displayNamesOut: MutableMap<String, String>? = null
    ): Map<String, String> {
        val map = mutableMapOf<String, String>()
        runCatching {
            val root = JSONObject(jsonText)
            val data = root.optJSONObject("data") ?: return@runCatching
            val keys = data.keys()
            while (keys.hasNext()) {
                val mapKey = keys.next()
                val obj = data.optJSONObject(mapKey) ?: continue
                val apiId = obj.optString("id")
                val name = obj.optString("name").trim()
                if (displayNamesOut != null && isValidResolvedDisplayName(name)) {
                    val normMapKey = normalizeLookupKey(mapKey)
                    if (normMapKey.isNotEmpty()) displayNamesOut.putIfAbsent(normMapKey, name)
                    if (apiId.isNotBlank()) {
                        val normApiId = normalizeLookupKey(apiId)
                        if (normApiId.isNotEmpty()) displayNamesOut.putIfAbsent(normApiId, name)
                    }
                }
                val imageObj = obj.optJSONObject("image")
                val fullImg = imageObj?.optString("full").orEmpty()
                if (fullImg.isNotBlank()) {
                    if (skipMissingPlaceholderImages && fullImg.startsWith("Missing-", ignoreCase = true)) {
                        continue
                    }
                    val group = imageObj?.optString("group").orEmpty().ifBlank { defaultGroup }
                    val storedPath = if (fullImg.startsWith("http") || fullImg.contains("/")) {
                        fullImg
                    } else {
                        "$group/$fullImg"
                    }
                    map[normalizeLookupKey(mapKey)] = storedPath
                    if (apiId.isNotBlank()) {
                        map[normalizeLookupKey(apiId)] = storedPath
                    }
                    if (name.isNotBlank()) {
                        map[normalizeLookupKey(name)] = storedPath
                    }
                }
            }
        }
        return map
    }

    private val REGEX_LEADING_COUNT = Regex("^\\d+\\s+")
    private val REGEX_AUGMENT_TIER_SUFFIX = Regex("\\s+(III|II|I)\\s*$", RegexOption.IGNORE_CASE)
    private val REGEX_NON_ALNUM = Regex("[^A-Za-z0-9]+")
    private val REGEX_WHITESPACE = Regex("\\s+")

    private fun toDdragonImgUrl(relativeOrUrl: String): String =
        if (relativeOrUrl.startsWith("http")) relativeOrUrl
        else "https://ddragon.leagueoflegends.com/cdn/$ddragonVersion/img/${relativeOrUrl.trimStart('/')}"

    private fun normalizeLookupKey(raw: String): String {
        if (raw.isEmpty()) return ""
        val sb = StringBuilder(raw.length)
        for (i in 0 until raw.length) {
            val ch = raw[i]
            when (ch) {
                in 'a'..'z', in '0'..'9' -> sb.append(ch)
                in 'A'..'Z' -> sb.append((ch.code + 32).toChar())
            }
        }
        return sb.toString()
    }

    private fun toChampionKey(displayName: String): String {
        return displayName
            .replace("'", "")
            .replace(".", "")
            .replace("&", "")
            .split(REGEX_WHITESPACE)
            .filter { it.isNotBlank() }
            .joinToString("") { token ->
                token.lowercase().replaceFirstChar { it.uppercaseChar() }
            }
    }

    private val BUILTIN_DISPLAY_NAME_OVERRIDES: Map<String, String> = mapOf(
        // Set 18 / 19 champions & augments
        "da18sentry" to "Pebbles",
        "da18emblemslayer" to "Ravager Emblem",
        "da18florafatalisaugment" to "Consuming Flora",
        "da18rivalsaugment" to "Unrivaled",
        "da18riftbeasttraitaugment" to "Omega Riftbeast",
        "da18riftbeasttraitaugmentstampede" to "Jungle Pathing",
        "da18gnarsmall" to "Gnar",
        "da18luxelderwood" to "Lux (Elderwood)",
        "dabrambleback18" to "Brambleback",
        "dacinderling18" to "Cinderling",
        "dascuttlecrab18" to "Scuttlecrab",
        "dakarma18" to "Karma",
        "dalulu18" to "Lulu",
        "dazoe18" to "Zoe",
        "dagromp18ap" to "Gromp",
        "damurkwolf18" to "Murkwolf",
        "da18elderdragon" to "Elder Dragon",
        "dabaronnashor18" to "Baron Nashor",
        "daelderwood18lifeblossom" to "Lifeblossom",
        "daelderwood18stonebarktree" to "Stonebark Tree",
        "daelderwood18protector" to "Deepwood Protector",
        "danestingdolls" to "Nesting Dolls",
        "danestingdollsplus" to "Nesting Dolls+",
        "danestingdollsplusplus" to "Nesting Dolls++",
        // Official Riot internal TFT item codenames -> modern TFT in-game display names
        "tftitemredbuff" to "Sunfire Cape",
        "tftitemrapidfirecannon" to "Red Buff",
        "rapidfirecannon" to "Rapid Firecannon",
        "daartifactrapidfirecannon" to "Rapid Firecannon",
        "tftitemartifactrapidfirecannon" to "Rapid Firecannon",
        "tft9itemornnrapidfirecannon" to "Rapid Firecannon",
        "tftitemleviathan" to "Nashor's Tooth",
        "leviathan" to "Nashor's Tooth",
        "nashorstooth" to "Nashor's Tooth",
        "tftitemguardianangel" to "Edge of Night",
        "guardianangel" to "Edge of Night",
        "edgeofnight" to "Edge of Night",
        "tftitemunstableconcoction" to "Hand of Justice",
        "unstableconcoction" to "Hand of Justice",
        "handofjustice" to "Hand of Justice",
        "tftitemfrozenheart" to "Protector's Vow",
        "frozenheart" to "Protector's Vow",
        "protectorsvow" to "Protector's Vow",
        "tftitemnightharvester" to "Steadfast Heart",
        "nightharvester" to "Steadfast Heart",
        "steadfastheart" to "Steadfast Heart",
        "tftitemmadredsbloodrazor" to "Giant Slayer",
        "madredsbloodrazor" to "Giant Slayer",
        "giantslayer" to "Giant Slayer",
        "tftitemspectralgauntlet" to "Evenshroud",
        "spectralgauntlet" to "Evenshroud",
        "evenshroud" to "Evenshroud",
        "tftitempowergauntlet" to "Striker's Flail",
        "powergauntlet" to "Striker's Flail",
        "guardbreaker" to "Striker's Flail",
        "strikersflail" to "Striker's Flail",
        "dastrikersflail" to "Striker's Flail",
        "tftitemforceofnature" to "Tactician's Crown",
        "forceofnature" to "Tactician's Crown",
        "tacticianscrown" to "Tactician's Crown",
        "tftitemstatikkshiv" to "Void Staff",
        "statikkshiv" to "Void Staff",
        "voidstaff" to "Void Staff",
        "davoidstaff" to "Void Staff",
        "tftitemredemption" to "Spirit Visage",
        "redemption" to "Spirit Visage",
        "spiritvisage" to "Spirit Visage",
        "daspiritvisage" to "Spirit Visage",
        "tftitemrunaanshurricane" to "Kraken's Fury",
        "runaanshurricane" to "Kraken's Fury",
        "krakensfury" to "Kraken's Fury",
        "dakrakensfury" to "Kraken's Fury",
        "tftitembfsword" to "B.F. Sword",
        "bfsword" to "B.F. Sword",
        "tftitemtearofthegoddess" to "Tear of the Goddess",
        "tearofthegoddess" to "Tear of the Goddess",
        "tftitemspearofshojin" to "Spear of Shojin",
        "spearofshojin" to "Spear of Shojin",
        "tftitemtacticianscape" to "Tactician's Cape",
        "tacticianscape" to "Tactician's Cape",
        "tftitemtacticiansring" to "Tactician's Cape",
        "tacticiansring" to "Tactician's Cape",
        "tftitemtacticiansshield" to "Tactician's Shield",
        "tacticiansshield" to "Tactician's Shield",
        "tftitemtacticiansscepter" to "Tactician's Shield",
        "tacticiansscepter" to "Tactician's Shield",
        "tftitemjeweledgauntlet" to "Jeweled Gauntlet",
        "jeweledgauntlet" to "Jeweled Gauntlet",
        "tftitemwarmogsarmor" to "Warmog's Armor",
        "warmogsarmor" to "Warmog's Armor",
        "tftitemguinsoosrageblade" to "Guinsoo's Rageblade",
        "guinsoosrageblade" to "Guinsoo's Rageblade",
        "tftitemrabadonsdeathcap" to "Rabadon's Deathcap",
        "rabadonsdeathcap" to "Rabadon's Deathcap",
        "tftitemarchangelsstaff" to "Archangel's Staff",
        "archangelsstaff" to "Archangel's Staff",
        "tftitemdragonsclaw" to "Dragon's Claw",
        "dragonsclaw" to "Dragon's Claw",
        "tftitemtitansresolve" to "Titan's Resolve",
        "titansresolve" to "Titan's Resolve",
        "tftitemsteraksgage" to "Sterak's Gage",
        "steraksgage" to "Sterak's Gage",
        "tftitemthiefsgloves" to "Thief's Gloves",
        "thiefsgloves" to "Thief's Gloves",
        "tftitemgiantsbelt" to "Giant's Belt",
        "giantsbelt" to "Giant's Belt",
        "tftitemspatula" to "Spatula",
        "tftitemfryingpan" to "Frying Pan",
        "dacomponentfryingpan" to "Frying Pan",
        "aegisofdawn" to "Aegis of Dawn",
        "aegisofdusk" to "Aegis of Dusk",
        // Artifacts & Ornn Items (exact official Riot TFT in-game names)
        "tft4itemornnmuramana" to "Manazane",
        "ornnmuramana" to "Manazane",
        "manazane" to "Manazane",
        "daartifactmanazane" to "Manazane",
        "tft4itemornnthecollector" to "Gold Collector",
        "ornnthecollector" to "Gold Collector",
        "thecollector" to "Gold Collector",
        "goldcollector" to "Gold Collector",
        "daartifactgoldcollector" to "Gold Collector",
        "tft9itemornnhullbreaker" to "Hullcrusher",
        "ornnhullbreaker" to "Hullcrusher",
        "hullbreaker" to "Hullcrusher",
        "hullcrusher" to "Hullcrusher",
        "daartifacthullcrusher" to "Hullcrusher",
        "tft4itemornndeathsdefiance" to "Death's Defiance",
        "ornndeathsdefiance" to "Death's Defiance",
        "deathsdefiance" to "Death's Defiance",
        "daartifactdeathsdefiance" to "Death's Defiance",
        "tft4itemornninfinityforce" to "Infinity Force",
        "ornninfinityforce" to "Infinity Force",
        "infinityforce" to "Infinity Force",
        "daartifactinfinityforce" to "Infinity Force",
        "tft4itemornnzhonyasparadox" to "Zhonya's Paradox",
        "ornnzhonyasparadox" to "Zhonya's Paradox",
        "zhonyasparadox" to "Zhonya's Paradox",
        "daartifactzhonyasparadox" to "Zhonya's Paradox",
        "tft4itemornnanimavisage" to "Anima Visage",
        "ornnanimavisage" to "Anima Visage",
        "animavisage" to "Anima Visage",
        "daartifactanimavisage" to "Anima Visage",
        "tft4itemornneternalwinter" to "Eternal Winter",
        "ornneternalwinter" to "Eternal Winter",
        "eternalwinter" to "Eternal Winter",
        "daartifacteternalwinter" to "Eternal Winter",
        "tft4itemornnobsidiancleaver" to "Obsidian Cleaver",
        "obsidiancleaver" to "Obsidian Cleaver",
        "tft9itemornnhorizonfocus" to "Sniper's Focus",
        "ornnhorizonfocus" to "Sniper's Focus",
        "snipersfocus" to "Sniper's Focus",
        "tftitemartifactsnipersfocus" to "Sniper's Focus",
        "daartifactsnipersfocus" to "Sniper's Focus",
        "tft9itemornntrickstersglass" to "Trickster's Glass",
        "ornntrickstersglass" to "Trickster's Glass",
        "trickstersglass" to "Trickster's Glass",
        "daartifacttrickstersglass" to "Trickster's Glass",
        "tft9itemornndeathfiregrasp" to "Deathfire Grasp",
        "ornndeathfiregrasp" to "Deathfire Grasp",
        "deathfiregrasp" to "Deathfire Grasp",
        "daartifactdeathfiregrasp" to "Deathfire Grasp",
        "tft9itemornnblacksmithsgloves" to "Blacksmith's Gloves",
        "ornnblacksmithsgloves" to "Blacksmith's Gloves",
        "blacksmithsgloves" to "Blacksmith's Gloves",
        "daartifactblacksmithsgloves" to "Blacksmith's Gloves",
        "tftitemartifactnavoriflickerblades" to "Flickerblades",
        "daartifactnavoriflickerblade" to "Flickerblades",
        "daartifactnavoriflickerblades" to "Flickerblades",
        "navoriflickerblade" to "Flickerblades",
        "navoriflickerblades" to "Flickerblades",
        "flickerblade" to "Flickerblades",
        "flickerblades" to "Flickerblades",
        "tftitemartifactwitsend" to "Wit's End",
        "daartifactwitsend" to "Wit's End",
        "witsend" to "Wit's End",
        "tftitemartifactlichbane" to "Lich Bane",
        "daartifactlichbane" to "Lich Bane",
        "lichbane" to "Lich Bane",
        "tftitemartifactludenstempest" to "Luden's Tempest",
        "tftitemartifactludensecho" to "Luden's Tempest",
        "daartifactludenstempest" to "Luden's Tempest",
        "ludenstempest" to "Luden's Tempest",
        "tftitemartifacttalismanofascension" to "Talisman of Ascension",
        "daartifacttalismanofascension" to "Talisman of Ascension",
        "talismanofascension" to "Talisman of Ascension",
        "tftitemartifactfishbones" to "Fishbones",
        "daartifactfishbones" to "Fishbones",
        "fishbones" to "Fishbones",
        "tftitemartifactmittens" to "Mittens",
        "daartifactmittens" to "Mittens",
        "mittens" to "Mittens",
        "tftitemartifactprowlersclaw" to "Prowler's Claw",
        "daartifactprowlersclaw" to "Prowler's Claw",
        "prowlersclaw" to "Prowler's Claw",
        "tftitemartifactseekersarmguard" to "Seeker's Armguard",
        "daartifactseekersarmguard" to "Seeker's Armguard",
        "seekersarmguard" to "Seeker's Armguard",
        "tftitemartifactsilvermeredawn" to "Silvermere Dawn",
        "daartifactsilvermeredawn" to "Silvermere Dawn",
        "silvermeredawn" to "Silvermere Dawn",
        "tftitemartifactsuspicioustrenchcoat" to "Suspicious Trench Coat",
        "daartifactsuspicioustrenchcoat" to "Suspicious Trench Coat",
        "suspicioustrenchcoat" to "Suspicious Trench Coat",
        "tftitemartifactunendingdespair" to "Unending Despair",
        "daartifactunendingdespair" to "Unending Despair",
        "unendingdespair" to "Unending Despair",
        "tftitemartifactblightingjewel" to "Blighting Jewel",
        "daartifactblightingjewel" to "Blighting Jewel",
        "blightingjewel" to "Blighting Jewel",
        "tftitemartifactforbiddenidol" to "Forbidden Idol",
        "daartifactforbiddenidol" to "Forbidden Idol",
        "forbiddenidol" to "Forbidden Idol",
        "tftitemartifactinnervatinglocket" to "Innervating Locket",
        "daartifactinnervatinglocket" to "Innervating Locket",
        "innervatinglocket" to "Innervating Locket",
        "tftitemartifactlightshieldcrest" to "Lightshield Crest",
        "daartifactlightshieldcrest" to "Lightshield Crest",
        "lightshieldcrest" to "Lightshield Crest",
        "tftitemartifactmogulsmail" to "Mogul's Mail",
        "daartifactmogulsmail" to "Mogul's Mail",
        "mogulsmail" to "Mogul's Mail",
        "tftitemartifactgamblersblade" to "Gambler's Blade",
        "daartifactgamblersblade" to "Gambler's Blade",
        "gamblersblade" to "Gambler's Blade",
        "tftitemartifactdiamondhands" to "Diamond Hands",
        "daartifactdiamondhands" to "Diamond Hands",
        "diamondhands" to "Diamond Hands",
        "tftitemartifactdawncore" to "Dawncore",
        "daartifactdawncore" to "Dawncore",
        "dawncore" to "Dawncore",
        "tftitemartifactstatikkshiv" to "Statikk Shiv",
        "daartifactstatikkshiv" to "Statikk Shiv",
        "tftitemartifacttheindomitable" to "The Indomitable",
        "daartifacttheindomitable" to "The Indomitable",
        "theindomitable" to "The Indomitable",
        "tftitemartifacttitanichydra" to "Titanic Hydra",
        "daartifacttitanichydra" to "Titanic Hydra",
        "titanichydra" to "Titanic Hydra",
        "tftitemartifactspectralcutlass" to "Spectral Cutlass",
        "daartifactspectralcutlass" to "Spectral Cutlass",
        "spectralcutlass" to "Spectral Cutlass",
        "tftitemartifactcorruptvampiricscepter" to "Corrupt Vampiric Scepter",
        "corruptvampiricscepter" to "Corrupt Vampiric Scepter",
        // Support Items
        "tftitemshroud" to "Shroud of Stillness",
        "shroud" to "Shroud of Stillness",
        "shroudofstillness" to "Shroud of Stillness",
        "tftitemchalice" to "Chalice of Power",
        "chalice" to "Chalice of Power",
        "chaliceofpower" to "Chalice of Power",
        "tftitemlocketoftheironsolari" to "Locket of the Iron Solari",
        "locketoftheironsolari" to "Locket of the Iron Solari",
        "tftitemzekesherald" to "Zeke's Herald",
        "zekesherald" to "Zeke's Herald",
        "tftitemaegisofthelegion" to "Aegis of the Legion",
        "aegisofthelegion" to "Aegis of the Legion",
        "tftitemmoonstone" to "Moonstone Renewer",
        "moonstonerenewer" to "Moonstone Renewer",
        "tftitemvirtueofthemartyr" to "Virtue of the Martyr",
        "virtueofthemartyr" to "Virtue of the Martyr",
        "tftitemranduinssanctum" to "Randuin's Omen",
        "tft4itemornnranduinssanctum" to "Randuin's Omen",
        "randuinssanctum" to "Randuin's Omen",
        "randuinsomen" to "Randuin's Omen",
        "tftitemneedlesslybiggem" to "Needlessly Big Gem",
        "needlesslybiggem" to "Needlessly Big Gem",
        "tftitembansheesveil" to "Banshee's Veil",
        "bansheesveil" to "Banshee's Veil",
        "tftitemzephyr" to "Zephyr",
        "zephyr" to "Zephyr",
        "tftitemspite" to "Spite",
        "tftitemtheeternalflame" to "The Eternal Flame",
        "theeternalflame" to "The Eternal Flame",
        "tftitemknightsvow" to "Knight's Vow",
        "knightsvow" to "Knight's Vow",
        "tftitemunstabletreasurechest" to "Unstable Treasure Chest",
        "unstabletreasurechest" to "Unstable Treasure Chest"
    )

    private val NON_LOL_CHAMPION_KEYS: Set<String> = setOf(
        "gromp",
        "brambleback",
        "elderdragon",
        "krug",
        "pebbles",
        "sentry",
        "murkwolf",
        "cinderling",
        "scuttlecrab",
        "baronnashor",
        "alune",
        "lifeblossom",
        "stonebarktree",
        "deepwoodprotector",
        "smeech",
        "silco",
        "kobuko",
        "norra",
        "sevika",
        "powder",
        "violet",
        "vander",
        "loris",
        "renni",
        "maddie",
        "steb",
        "scar"
    )

    private val SPECIAL_CHAMPION_KEYS: Map<String, String> = mapOf(
        "tahmkench" to "TahmKench",
        "kogmaw" to "KogMaw",
        "kaisa" to "Kaisa",
        "khazix" to "Khazix",
        "velkoz" to "Velkoz",
        "chogath" to "Chogath",
        "reksai" to "RekSai",
        "belveth" to "Belveth",
        "leblanc" to "Leblanc",
        "wukong" to "MonkeyKing",
        "leesin" to "LeeSin",
        "masteryi" to "MasterYi",
        "missfortune" to "MissFortune",
        "twistedfate" to "TwistedFate",
        "aurelionsol" to "AurelionSol",
        "jarvaniv" to "JarvanIV",
        "xinzhao" to "XinZhao",
        "drmundo" to "DrMundo",
        "renataglasc" to "Renata",
        "nunuwillump" to "Nunu",
        "ksante" to "KSante"
    )

    private val SET18_SQUARE_PORTRAIT_KEYS: Map<String, String> = mapOf(
        "ahri" to "Ahri",
        "akali" to "Akali",
        "alistar" to "Alistar",
        "alune" to "Alune",
        "amumu" to "Amumu",
        "aphelios" to "Aphelios",
        "ashe" to "Ashe",
        "azir" to "Azir",
        "brambleback" to "Brambleback",
        "caitlyn" to "Caitlyn",
        "camille" to "Camille",
        "cassiopeia" to "Cassiopeia",
        "cinderling" to "Cinderling",
        "diana" to "Diana",
        "draven" to "Draven",
        "elderdragon" to "ElderDragon",
        "elise" to "Elise",
        "ezreal" to "Ezreal",
        "fiddlesticks" to "Fiddlesticks",
        "gnar" to "Gnar",
        "gromp" to "Gromp",
        "hecarim" to "Hecarim",
        "ivern" to "Ivern",
        "karma" to "Karma",
        "kayle" to "Kayle",
        "kennen" to "Kennen",
        "khazix" to "Khazix",
        "kobuko" to "Kobuko",
        "kogmaw" to "Kogmaw",
        "krug" to "Krug",
        "leblanc" to "Leblanc",
        "leona" to "Leona",
        "lillia" to "Lillia",
        "lux" to "Lux",
        "luxblackthorn" to "Lux",
        "luxblossom" to "Lux",
        "luxcoven" to "Lux",
        "luxelderwood" to "Lux",
        "luxfae" to "Lux",
        "luxinferno" to "Lux",
        "luxlunar" to "Lux",
        "luxprimal" to "Lux",
        "luxsolar" to "Lux",
        "malphite" to "Malphite",
        "mamabeak" to "MamaBeak",
        "crimsonraptor" to "MamaBeak",
        "maokai" to "Maokai",
        "masteryi" to "MasterYi",
        "morgana" to "Morgana",
        "murkwolf" to "Murkwolf",
        "nidalee" to "Nidalee",
        "ornn" to "Ornn",
        "pebbles" to "Pebbles",
        "sentry" to "Pebbles",
        "rakan" to "Rakan",
        "rammus" to "Rammus",
        "reksai" to "RekSai",
        "rengar" to "Rengar",
        "scuttlecrab" to "Scuttlecrab",
        "sejuani" to "Sejuani",
        "sentinel" to "Sentinel",
        "sett" to "Sett",
        "shen" to "Shen",
        "sivir" to "Sivir",
        "soraka" to "Soraka",
        "taric" to "Taric",
        "teemo" to "Teemo",
        "tristana" to "Tristana",
        "varus" to "Varus",
        "veigar" to "Veigar",
        "vi" to "Vi",
        "warwick" to "Warwick",
        "xayah" to "Xayah",
        "yorick" to "Yorick",
        "yunara" to "Yunara",
        "zyra" to "Zyra"
    )

    private val ITEM_FILENAME_MAP: Map<String, String> = mapOf(
        // Completed Items
        "bluebuff" to "TFT_Item_BlueBuff.png",
        "jeweledgauntlet" to "TFT_Item_JeweledGauntlet.png",
        "warmogsarmor" to "TFT_Item_WarmogsArmor.png",
        "infinityedge" to "TFT_Item_InfinityEdge.png",
        "guinsoosrageblade" to "TFT_Item_GuinsoosRageblade.png",
        "spearofshojin" to "TFT_Item_SpearOfShojin.png",
        "gargoylestoneplate" to "TFT_Item_GargoyleStoneplate.png",
        "nashorstooth" to "TFT_Item_Leviathan.png",
        "leviathan" to "TFT_Item_Leviathan.png",
        "lastwhisper" to "TFT_Item_LastWhisper.png",
        "bloodthirster" to "TFT_Item_Bloodthirster.png",
        "edgeofnight" to "TFT_Item_GuardianAngel.png",
        "guardianangel" to "TFT_Item_GuardianAngel.png",
        "handofjustice" to "TFT_Item_UnstableConcoction.png",
        "unstableconcoction" to "TFT_Item_UnstableConcoction.png",
        "protectorsvow" to "TFT_Item_FrozenHeart.png",
        "redbuff" to "TFT_Item_RapidFireCannon.png",
        "tftitemrapidfirecannon" to "TFT_Item_RapidFireCannon.png",
        "rapidfirecannon" to "TFT_Item_Artifact_RapidFirecannon.png",
        "sunfirecape" to "TFT_Item_RedBuff.png",
        "ionicspark" to "TFT_Item_IonicSpark.png",
        "morellonomicon" to "TFT_Item_Morellonomicon.png",
        "redemption" to "TFT_Item_Redemption.png",
        "bramblevest" to "TFT_Item_BrambleVest.png",
        "dragonsclaw" to "TFT_Item_DragonsClaw.png",
        "steadfastheart" to "TFT_Item_NightHarvester.png",
        "nightharvester" to "TFT_Item_NightHarvester.png",
        "runaanshurricane" to "TFT_Item_RunaansHurricane.png",
        "giantslayer" to "TFT_Item_MadredsBloodrazor.png",
        "madredsbloodrazor" to "TFT_Item_MadredsBloodrazor.png",
        "hextechgunblade" to "TFT_Item_HextechGunblade.png",
        "rabadonsdeathcap" to "TFT_Item_RabadonsDeathcap.png",
        "archangelsstaff" to "TFT_Item_ArchangelsStaff.png",
        "crownguard" to "TFT_Item_Crownguard.png",
        "evenshroud" to "TFT_Item_SpectralGauntlet.png",
        "spectralgauntlet" to "TFT_Item_SpectralGauntlet.png",
        "titansresolve" to "TFT_Item_TitansResolve.png",
        "steraksgage" to "TFT_Item_SteraksGage.png",
        "statikkshiv" to "TFT_Item_StatikkShiv.png",
        "voidstaff" to "TFT_Item_StatikkShiv.png",
        "adaptivehelm" to "TFT_Item_AdaptiveHelm.png",
        "quicksilver" to "TFT_Item_Quicksilver.png",
        "deathblade" to "TFT_Item_Deathblade.png",
        "thiefsgloves" to "TFT_Item_ThiefsGloves.png",
        "tacticianscrown" to "TFT_Item_ForceOfNature.png",
        "tacticianscape" to "TFT_Item_TacticiansRing.png",
        "tacticiansring" to "TFT_Item_TacticiansRing.png",
        "tacticiansshield" to "TFT_Item_TacticiansScepter.png",
        "tacticiansscepter" to "TFT_Item_TacticiansScepter.png",
        "spiritvisage" to "TFT_Item_Redemption.png",
        "krakensfury" to "TFT_Item_RunaansHurricane.png",
        "strikersflail" to "TFT_Item_PowerGauntlet.png",
        "guardbreaker" to "TFT_Item_PowerGauntlet.png",
        // Artifacts & Ornn Items
        "manazane" to "TFT4_Item_OrnnMuramana.png",
        "goldcollector" to "TFT4_Item_OrnnTheCollector.png",
        "hullcrusher" to "TFT9_Item_OrnnHullbreaker.png",
        "deathsdefiance" to "TFT4_Item_OrnnDeathsDefiance.png",
        "infinityforce" to "TFT4_Item_OrnnInfinityForce.png",
        "zhonyasparadox" to "TFT4_Item_OrnnZhonyasParadox.png",
        "animavisage" to "TFT4_Item_OrnnAnimaVisage.png",
        "eternalwinter" to "TFT4_Item_OrnnEternalWinter.png",
        "snipersfocus" to "TFT9_Item_OrnnHorizonFocus.png",
        "trickstersglass" to "TFT9_Item_OrnnTrickstersGlass.png",
        "deathfiregrasp" to "TFT9_Item_OrnnDeathfireGrasp.png",
        "blacksmithsgloves" to "TFT9_Item_OrnnBlacksmithsGloves.png",
        "flickerblades" to "TFT_Item_Artifact_NavoriFlickerblades.png",
        "witsend" to "TFT_Item_Artifact_WitsEnd.png",
        "lichbane" to "TFT_Item_Artifact_LichBane.png",
        "ludenstempest" to "TFT_Item_Artifact_LudensTempest.png",
        "talismanofascension" to "TFT_Item_Artifact_TalismanOfAscension.png",
        "fishbones" to "TFT_Item_Artifact_Fishbones.png",
        "mittens" to "TFT_Item_Artifact_Mittens.png",
        "prowlersclaw" to "TFT_Item_Artifact_ProwlersClaw.png",
        "seekersarmguard" to "TFT_Item_Artifact_SeekersArmguard.png",
        "silvermeredawn" to "TFT_Item_Artifact_SilvermereDawn.png",
        "suspicioustrenchcoat" to "TFT_Item_Artifact_SuspiciousTrenchCoat.png",
        "unendingdespair" to "TFT_Item_Artifact_UnendingDespair.png",
        "blightingjewel" to "TFT_Item_Artifact_BlightingJewel.png",
        "forbiddenidol" to "TFT_Item_Artifact_ForbiddenIdol.png",
        "innervatinglocket" to "TFT_Item_Artifact_InnervatingLocket.png",
        "lightshieldcrest" to "TFT_Item_Artifact_LightshieldCrest.png",
        "mogulsmail" to "TFT_Item_Artifact_MogulsMail.png",
        "gamblersblade" to "TFT_Item_Artifact_GamblersBlade.png",
        "diamondhands" to "TFT_Item_Artifact_DiamondHands.png",
        "dawncore" to "TFT_Item_Artifact_Dawncore.png",
        "titanichydra" to "TFT_Item_Artifact_TitanicHydra.png",
        "spectralcutlass" to "TFT_Item_Artifact_SpectralCutlass.png",
        // Support Items
        "shroudofstillness" to "TFT_Item_Shroud.png",
        "chaliceofpower" to "TFT_Item_Chalice.png",
        "locketoftheironsolari" to "TFT_Item_LocketOfTheIronSolari.png",
        "zekesherald" to "TFT_Item_ZekesHerald.png",
        "aegisofthelegion" to "TFT_Item_AegisOfTheLegion.png",
        "moonstonerenewer" to "TFT_Item_Moonstone.png",
        "virtueofthemartyr" to "TFT_Item_RadiantVirtue.png",
        "randuinsomen" to "TFT4_Item_OrnnRanduinsSanctum.png",
        "needlesslybiggem" to "TFT_Item_NeedlesslyBigGem.png",
        "bansheesveil" to "TFT_Item_BansheesVeil.png",
        "zephyr" to "TFT_Item_Zephyr.png",
        "spite" to "TFT_Item_Spite.png",
        "theeternalflame" to "TFT_Item_TheEternalFlame.png",
        "knightsvow" to "TFT_Item_KnightsVow.png",
        "unstabletreasurechest" to "TFT_Item_UnstableTreasureChest.png",
        // Base Components
        "bfsword" to "TFT_Item_BFSword.png",
        "recurvebow" to "TFT_Item_RecurveBow.png",
        "needlesslylargerod" to "TFT_Item_NeedlesslyLargeRod.png",
        "tearofthegoddess" to "TFT_Item_TearOfTheGoddess.png",
        "chainvest" to "TFT_Item_ChainVest.png",
        "negatroncloak" to "TFT_Item_NegatronCloak.png",
        "giantsbelt" to "TFT_Item_GiantsBelt.png",
        "sparringgloves" to "TFT_Item_SparringGloves.png",
        "spatula" to "TFT_Item_Spatula.png",
        "fryingpan" to "DA_Component_FryingPan.png"
    )

    private val AUGMENT_FILENAME_MAP: Map<String, String> = mapOf(
        "jeweledlotusii" to "TFT_Augment_JeweledLotus2.png",
        "jeweledlotus" to "TFT_Augment_JeweledLotus.png",
        "levelup" to "TFT_Augment_LevelUp.png",
        "tinytitans" to "TFT_Augment_TinyTitans.png",
        "tradesector" to "TFT_Augment_TradeSector.png",
        "twohealthy" to "TFT_Augment_TwoHealthy.png",
        "patientstudy" to "TFT_Augment_PatientStudy.png",
        "hedgefund" to "TFT_Augment_HedgeFund.png",
        "tacticianscrown" to "TFT_Augment_TacticiansCrown.png",
        "pumpingupii" to "TFT_Augment_PumpingUp2.png",
        "pumpingup" to "TFT_Augment_PumpingUp.png",
        "cyberneticuplink" to "TFT_Augment_CyberneticUplink2.png",
        "harmacist" to "TFT_Augment_Harmacist2.png",
        "harmacistii" to "TFT_Augment_Harmacist2.png",
        "vampirismii" to "TFT_Augment_Vampirism2.png",
        "unifiedresistance" to "TFT_Augment_UnifiedResistance2.png",
        "inspiringepitaph" to "TFT_Augment_InspiringEpitaph.png",
        "heavyhitters" to "TFT_Augment_HeavyHitters.png",
        "goldenticket" to "TFT_Augment_GoldenTicket.png",
        "prismaticticket" to "TFT_Augment_PrismaticTicket.png",
        "builtdifferent" to "TFT_Augment_BuiltDifferent2.png",
        "balancedbudget" to "TFT_Augment_BalancedBudget2.png",
        "starrynight" to "TFT_Augment_StarryNight.png",
        "pandorasitems" to "PandorasItems_I.png",
        "pandorasitemsii" to "PandorasItems_II.png",
        "pandorasitemsiii" to "PandorasItems_III.png",
        "portableforge" to "PortableForge2.png",
        "radiantrelics" to "RadiantRelic-III.png",
        "salvagebin" to "Salvage2.png",
        "itemgrabbag" to "ItemGrabBag_I.png",
        "smallgrabbag" to "SmallGrabBag_I.png",
        "rollingfordays" to "Rolling-For-Days-I.png",
        "silverspoon" to "Silver-Spoon-I.png",
        "branchingout" to "BranchingOut_I.png",
        "bandofthieves" to "BandOfThieves_I.png",
        "recombobulator" to "Recombobulator-I.png",
        "shoppingSpree" to "Silver-ticket-III.png",
        "shoppingspree" to "Silver-ticket-III.png",
        "silverdestiny" to "SilverDestiny_I.png",
        "prismaticdestiny" to "PrismaticDestiny_III.png",
        "tonsofstats" to "Tons-of-Stats-II.png",
        "timeskip" to "TimeSkip_II.png",
        "thegoldenegg" to "The-Golden-Egg-III.png",
        "thetraittree" to "TheTraitTree_III.png",
        "spreadingroots" to "SpreadingRoots_II.png",
        "swordoverflow" to "BladeOverflow_III.png",
        "wandoverflow" to "WandOverflow_III.png",
        "swordsmith" to "Swordsmith_II.png",
        "staffsmith" to "Rodsmith_II.png",
        "standunited" to "StandUnited1.png",
        "slammin" to "Slammin_II.png",
        "warpath" to "Warpath_II.png",
        "worththewait" to "WorthTheWait_II.png",
        "youngandwildandfree" to "Young-and-wild-and-free-I.png"
    )

    private val KNOWN_ITEM_NAME_KEYWORDS: List<String> = listOf(
        "emblem",
        "rageblade",
        "deathcap",
        "jeweled gauntlet",
        "stoneplate",
        "bloodthirster",
        "gunblade",
        "warmog",
        "spear of shojin",
        "blue buff",
        "red buff",
        "infinity edge",
        "last whisper",
        "giant slayer",
        "nashor's tooth",
        "sunfire cape",
        "ionic spark",
        "morellonomicon",
        "bramble vest",
        "dragon's claw",
        "titan's resolve",
        "sterak's gage",
        "statikk shiv",
        "void staff",
        "adaptive helm",
        "quicksilver",
        "deathblade",
        "thief's gloves",
        "crownguard",
        "evenshroud",
        "steadfast heart",
        "guardbreaker",
        "striker's flail",
        "kraken's fury",
        "spirit visage",
        "protector's vow",
        "hand of justice",
        "edge of night",
        "archangel's staff",
        "runaan's hurricane",
        "spatula",
        "frying pan",
        "b.f. sword",
        "bf sword",
        "recurve bow",
        "needlessly large rod",
        "tear of the goddess",
        "chain vest",
        "negatron cloak",
        "giant's belt",
        "sparring gloves",
        "tactician's",
        "radiant ",
        "zhonya",
        "manazane",
        "wits end",
        "wit's end",
        "lich bane",
        "snipers focus",
        "sniper's focus",
        "fishbones",
        "hullcrusher",
        "mogul's mail",
        "innervating locket",
        "death's defiance",
        "gold collector",
        "trickster's glass"
    )
}
