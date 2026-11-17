package com.example.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.data.model.TftEnvironment
import org.json.JSONObject

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
    private var pbeChampionCosts: Map<String, Int> = emptyMap()
    private var pbeChampionTraits: Map<String, List<String>> = emptyMap()

    @Volatile
    private var preloadedCatalogLoaded: Boolean = false

    @Volatile
    private var pbeCatalogLoaded: Boolean = false

    data class ParsedDdragonCatalog(
        val version: String?,
        val championImages: Map<String, String>?,
        val itemImages: Map<String, String>?,
        val augmentImages: Map<String, String>?,
        val traitImages: Map<String, String>? = null
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
        val traitDescriptions: Map<String, String>
    )

    fun isPreloadedCatalogApplied(): Boolean = preloadedCatalogLoaded

    fun isPbeCatalogApplied(): Boolean = pbeCatalogLoaded

    fun updatePbeSetCatalog(
        championIcons: Map<String, String>,
        championCosts: Map<String, Int>,
        championTraits: Map<String, List<String>>,
        traitIcons: Map<String, String>
    ) {
        if (championIcons.isEmpty() && traitIcons.isEmpty()) return
        val normChamps = HashMap<String, String>(championIcons.size * 2)
        championIcons.forEach { (k, v) ->
            if (v.isNotBlank()) normChamps[normalizeLookupKey(k)] = v
        }
        val normTraits = HashMap<String, String>(traitIcons.size * 2)
        traitIcons.forEach { (k, v) ->
            if (v.isNotBlank()) normTraits[normalizeLookupKey(k)] = v
        }
        val normCosts = HashMap<String, Int>(championCosts.size * 2)
        championCosts.forEach { (k, v) ->
            if (v in 1..9) normCosts[normalizeLookupKey(k)] = v
        }
        val normChampTraits = HashMap<String, List<String>>(championTraits.size * 2)
        championTraits.forEach { (k, v) ->
            if (v.isNotEmpty()) normChampTraits[normalizeLookupKey(k)] = v
        }
        pbeChampionImages = normChamps
        pbeTraitImages = normTraits
        pbeChampionCosts = normCosts
        pbeChampionTraits = normChampTraits
        pbeCatalogLoaded = true
        catalogRevision += 1
    }

    fun parsePreloadedSetCatalogInBackground(rawJson: String): ParsedPreloadedSetCatalog? {
        return runCatching {
            val root = JSONObject(rawJson)
            val version = root.optString("ddragonVersion").takeIf { it.isNotBlank() }

            fun readStringMap(obj: JSONObject?): Map<String, String> {
                if (obj == null) return emptyMap()
                val out = HashMap<String, String>(obj.length() * 2)
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = obj.optString(k)
                    if (v.isNotBlank()) {
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

            ParsedPreloadedSetCatalog(
                version = version,
                championImages = readStringMap(root.optJSONObject("championIcons")),
                itemImages = readStringMap(root.optJSONObject("itemIcons")),
                augmentImages = readStringMap(root.optJSONObject("augmentIcons")),
                traitImages = readStringMap(root.optJSONObject("traitIcons")),
                apiDisplayNames = readStringMap(root.optJSONObject("apiIdToName")),
                championTraits = traitsMap,
                championCosts = costsMap,
                traitDescriptions = traitDescMap
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
        preloadedCatalogLoaded = true
        catalogRevision += 1
    }

    fun parseDdragonCatalogInBackground(
        version: String?,
        championJson: String? = null,
        itemJson: String? = null,
        augmentJson: String? = null,
        traitJson: String? = null
    ): ParsedDdragonCatalog {
        val champs = championJson?.takeIf { it.isNotBlank() }
            ?.let { parseDdragonAssetJson(it, defaultGroup = "tft-champion").takeIf { m -> m.isNotEmpty() } }
        val items = itemJson?.takeIf { it.isNotBlank() }
            ?.let { parseDdragonAssetJson(it, defaultGroup = "tft-item").takeIf { m -> m.isNotEmpty() } }
        val augments = augmentJson?.takeIf { it.isNotBlank() }
            ?.let {
                parseDdragonAssetJson(
                    jsonText = it,
                    defaultGroup = "tft-augment",
                    skipMissingPlaceholderImages = true
                ).takeIf { m -> m.isNotEmpty() }
            }
        val traits = traitJson?.takeIf { it.isNotBlank() }
            ?.let { parseDdragonAssetJson(it, defaultGroup = "tft-trait").takeIf { m -> m.isNotEmpty() } }
        return ParsedDdragonCatalog(
            version = version?.takeIf { it.isNotBlank() },
            championImages = champs,
            itemImages = items,
            augmentImages = augments,
            traitImages = traits
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

    fun resolveDisplayName(rawId: String): String? {
        val raw = rawId.trim()
        if (raw.isEmpty()) return null
        val norm = normalizeLookupKey(raw)
        apiDisplayNameMap[norm]?.let { return it }
        BUILTIN_DISPLAY_NAME_OVERRIDES[norm]?.let { return it }
        return null
    }

    fun getChampionTraits(nameOrId: String, environment: TftEnvironment? = null): List<String> {
        val norm = normalizeLookupKey(nameOrId)
        val cleanNorm = normalizeLookupKey(formatRiotAssetLabel(nameOrId))
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
        val cleanNorm = normalizeLookupKey(formatRiotAssetLabel(nameOrId))
        if (environment == TftEnvironment.PBE) {
            (pbeChampionCosts[norm] ?: pbeChampionCosts[cleanNorm])?.let { return it }
        }
        return championCostsCatalog[norm]
            ?: championCostsCatalog[cleanNorm]
            ?: pbeChampionCosts[norm]
            ?: pbeChampionCosts[cleanNorm]
    }

    fun resolveTraitDescription(traitNameOrId: String): String? {
        val clean = traitNameOrId.replace(Regex("^\\d+\\s+"), "").trim()
        val norm = normalizeLookupKey(clean)
        return traitDescriptionCatalog[norm] ?: traitDescriptionCatalog[normalizeLookupKey(traitNameOrId)]
    }

    fun resolveTraitIconUrl(traitNameOrId: String, environment: TftEnvironment? = null): String? {
        val clean = traitNameOrId.replace(Regex("^\\d+\\s+"), "").trim()
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
        return pbeTraitImages[norm] ?: pbeTraitImages[rawNorm]
    }

    fun resolveChampionIconUrl(nameOrId: String, environment: TftEnvironment? = null): String? {
        val raw = nameOrId.trim()
        if (raw.isEmpty()) return null

        val norm = normalizeLookupKey(raw)
        val cleanName = formatRiotAssetLabel(raw)
        val cleanNorm = normalizeLookupKey(cleanName)

        // 0. For PBE comps/champions, prefer verified TFTactics Set 19 portraits first
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

        // 3. Fall back to PBE Set 19 catalog if caller did not pass environment (e.g. Set 19-exclusive units)
        val pbeFallback = pbeChampionImages[norm] ?: pbeChampionImages[cleanNorm]
        if (!pbeFallback.isNullOrBlank()) {
            return pbeFallback
        }

        // 4. Skip invalid League CDN URL guesses for TFT-exclusive creatures/NPCs
        if (NON_LOL_CHAMPION_KEYS.contains(cleanNorm) || NON_LOL_CHAMPION_KEYS.contains(norm)) {
            return null
        }

        // 5. Fall back to standard League Data Dragon champion portrait URL
        val key = SPECIAL_CHAMPION_KEYS[cleanNorm]
            ?: SPECIAL_CHAMPION_KEYS[norm]
            ?: toChampionKey(cleanName)
        if (key.isBlank()) return null
        return "https://ddragon.leagueoflegends.com/cdn/$ddragonVersion/img/champion/$key.png"
    }

    fun resolveItemIconUrl(nameOrId: String): String? {
        val raw = nameOrId.trim()
        if (raw.isEmpty()) return null

        val norm = normalizeLookupKey(raw)
        val cleanName = formatRiotAssetLabel(raw)
        val cleanNorm = normalizeLookupKey(cleanName)

        // 1. Check dynamically fetched Riot tft-item.json map
        val dynamicPath = dynamicItemImages[norm] ?: dynamicItemImages[cleanNorm]
        if (!dynamicPath.isNullOrBlank()) {
            return toDdragonImgUrl(dynamicPath)
        }

        // 2. Check preloaded Set 18 exact DDragon/TFTAcademy item icon catalog
        val preloaded = preloadedItemImages[norm] ?: preloadedItemImages[cleanNorm]
        if (!preloaded.isNullOrBlank()) {
            return preloaded
        }

        // 3. Check exact Data Dragon TFT item filename map
        val staticFile = ITEM_FILENAME_MAP[cleanNorm] ?: ITEM_FILENAME_MAP[norm]
        if (!staticFile.isNullOrBlank()) {
            return "https://ddragon.leagueoflegends.com/cdn/$ddragonVersion/img/tft-item/$staticFile"
        }

        // 4. Fallback if raw API ID is already like TFT_Item_Something
        if (raw.startsWith("TFT", ignoreCase = true)) {
            val suffix = raw.substringAfterLast("_").replace(Regex("[^A-Za-z0-9]"), "")
            if (suffix.isNotBlank()) {
                return "https://ddragon.leagueoflegends.com/cdn/$ddragonVersion/img/tft-item/TFT_Item_$suffix.png"
            }
        }

        val pascal = cleanName.split(Regex("[^A-Za-z0-9]+"))
            .filter { it.isNotBlank() }
            .joinToString("") { part -> part.replaceFirstChar { c -> c.uppercaseChar() } }
        return if (pascal.isNotBlank()) {
            "https://ddragon.leagueoflegends.com/cdn/$ddragonVersion/img/tft-item/TFT_Item_$pascal.png"
        } else null
    }

    fun resolveAugmentIconUrl(nameOrId: String): String? {
        val raw = nameOrId.trim()
        if (raw.isEmpty()) return null

        val norm = normalizeLookupKey(raw)
        val cleanName = formatRiotAssetLabel(raw)
        val cleanNorm = normalizeLookupKey(cleanName)
        val strippedTierNorm = normalizeLookupKey(
            cleanName
                .replace("+", "")
                .replace(Regex("\\s+(III|II|I)\\s*$", RegexOption.IGNORE_CASE), "")
        )

        // 1. Check dynamically fetched Riot tft-augments.json map (real non-missing icons)
        val dynamicPath = dynamicAugmentImages[norm]
            ?: dynamicAugmentImages[cleanNorm]
            ?: dynamicAugmentImages[strippedTierNorm]
        if (!dynamicPath.isNullOrBlank()) {
            return toDdragonImgUrl(dynamicPath)
        }

        // 2. Check preloaded Set 18 exact DDragon/TFTAcademy 200-OK augment icon catalog
        val preloaded = preloadedAugmentImages[norm]
            ?: preloadedAugmentImages[cleanNorm]
            ?: preloadedAugmentImages[strippedTierNorm]
        if (!preloaded.isNullOrBlank()) {
            return preloaded
        }

        // 3. Check evergreen / well-known Data Dragon augment filenames
        val staticFile = AUGMENT_FILENAME_MAP[norm]
            ?: AUGMENT_FILENAME_MAP[cleanNorm]
            ?: AUGMENT_FILENAME_MAP[strippedTierNorm]
        if (!staticFile.isNullOrBlank()) {
            return "https://ddragon.leagueoflegends.com/cdn/$ddragonVersion/img/tft-augment/$staticFile"
        }

        return null
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
        skipMissingPlaceholderImages: Boolean = false
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
                val name = obj.optString("name")
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

    private fun toDdragonImgUrl(relativeOrUrl: String): String =
        if (relativeOrUrl.startsWith("http")) relativeOrUrl
        else "https://ddragon.leagueoflegends.com/cdn/$ddragonVersion/img/${relativeOrUrl.trimStart('/')}"

    private fun normalizeLookupKey(raw: String): String =
        raw.lowercase().replace(Regex("[^a-z0-9]"), "")

    private fun toChampionKey(displayName: String): String {
        return displayName
            .replace("'", "")
            .replace(".", "")
            .replace("&", "")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString("") { token ->
                token.lowercase().replaceFirstChar { it.uppercaseChar() }
            }
    }

    private val BUILTIN_DISPLAY_NAME_OVERRIDES: Map<String, String> = mapOf(
        "da18sentry" to "Pebbles",
        "da18emblemslayer" to "Ravager Emblem",
        "da18florafatalisaugment" to "Consuming Flora",
        "da18rivalsaugment" to "Unrivaled",
        "da18riftbeasttraitaugment" to "Omega Riftbeast",
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
        "danestingdolls" to "Nesting Dolls"
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
        "rapidfirecannon" to "TFT_Item_RapidFireCannon.png",
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
        "tacticianscape" to "TFT_Item_ForceOfNature.png",
        "tacticiansshield" to "TFT_Item_ForceOfNature.png",
        "spiritvisage" to "TFT_Item_Redemption.png",
        "krakensfury" to "TFT_Item_RunaansHurricane.png",
        "strikersflail" to "TFT_Item_PowerGauntlet.png",
        "guardbreaker" to "TFT_Item_PowerGauntlet.png",
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
        "starrynight" to "TFT_Augment_StarryNight.png"
    )
}
