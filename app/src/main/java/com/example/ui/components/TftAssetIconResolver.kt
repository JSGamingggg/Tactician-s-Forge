package com.example.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/**
 * Resolves exact Riot Data Dragon icon URLs for TFT champions, items, and augments,
 * dynamically enriched whenever Data Dragon tft-champion.json, tft-item.json, and tft-augments.json sync.
 */
object TftAssetIconResolver {
    var ddragonVersion: String by mutableStateOf("15.22.1")
        private set

    /**
     * Incremented every time Data Dragon catalog maps refresh so icon composables recompose cleanly
     * even if the version string itself stayed constant.
     */
    var catalogRevision: Int by mutableIntStateOf(0)
        private set

    private var dynamicChampionImages: Map<String, String> by mutableStateOf(emptyMap())
    private var dynamicItemImages: Map<String, String> by mutableStateOf(emptyMap())
    private var dynamicAugmentImages: Map<String, String> by mutableStateOf(emptyMap())

    fun updateDdragonCatalog(
        version: String?,
        championJson: String? = null,
        itemJson: String? = null,
        augmentJson: String? = null
    ) {
        var changed = false
        if (!version.isNullOrBlank() && version != ddragonVersion) {
            ddragonVersion = version
            changed = true
        }
        if (!championJson.isNullOrBlank()) {
            parseDdragonAssetJson(championJson, defaultGroup = "tft-champion").takeIf { it.isNotEmpty() }?.let {
                dynamicChampionImages = it
                changed = true
            }
        }
        if (!itemJson.isNullOrBlank()) {
            parseDdragonAssetJson(itemJson, defaultGroup = "tft-item").takeIf { it.isNotEmpty() }?.let {
                dynamicItemImages = it
                changed = true
            }
        }
        if (!augmentJson.isNullOrBlank()) {
            parseDdragonAssetJson(augmentJson, defaultGroup = "tft-augment").takeIf { it.isNotEmpty() }?.let {
                dynamicAugmentImages = it
                changed = true
            }
        }
        if (changed) {
            catalogRevision += 1
        }
    }

    fun resolveChampionIconUrl(nameOrId: String): String? {
        val raw = nameOrId.trim()
        if (raw.isEmpty()) return null

        val norm = normalizeLookupKey(raw)
        val cleanName = formatRiotAssetLabel(raw)
        val cleanNorm = normalizeLookupKey(cleanName)

        // 1. Check dynamically fetched Riot tft-champion.json map
        val dynamicPath = dynamicChampionImages[norm] ?: dynamicChampionImages[cleanNorm]
        if (!dynamicPath.isNullOrBlank()) {
            return toDdragonImgUrl(dynamicPath)
        }

        // 2. Skip invalid League CDN URL guesses for TFT-exclusive creatures/NPCs until live tft-champion.json maps them
        if (NON_LOL_CHAMPION_KEYS.contains(cleanNorm) || NON_LOL_CHAMPION_KEYS.contains(norm)) {
            return null
        }

        // 3. Fall back to standard League Data Dragon champion portrait URL
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

        // 2. Check exact Data Dragon TFT item filename map
        val staticFile = ITEM_FILENAME_MAP[cleanNorm] ?: ITEM_FILENAME_MAP[norm]
        if (!staticFile.isNullOrBlank()) {
            return "https://ddragon.leagueoflegends.com/cdn/$ddragonVersion/img/tft-item/$staticFile"
        }

        // 3. Fallback if raw API ID is already like TFT_Item_Something
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

        // 1. Check dynamically fetched Riot tft-augments.json map
        val dynamicPath = dynamicAugmentImages[norm]
            ?: dynamicAugmentImages[cleanNorm]
            ?: dynamicAugmentImages[strippedTierNorm]
        if (!dynamicPath.isNullOrBlank()) {
            return toDdragonImgUrl(dynamicPath)
        }

        // 2. Check evergreen / well-known Data Dragon augment filenames
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
        parseDdragonAssetJson(jsonText, defaultGroup = "tft-augment")

    private fun parseDdragonAssetJson(jsonText: String, defaultGroup: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        runCatching {
            val root = JSONObject(jsonText)
            val data = root.optJSONObject("data") ?: return@runCatching
            val keys = data.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                val obj = data.optJSONObject(id) ?: continue
                val name = obj.optString("name")
                val imageObj = obj.optJSONObject("image")
                val fullImg = imageObj?.optString("full").orEmpty()
                if (fullImg.isNotBlank()) {
                    val group = imageObj?.optString("group").orEmpty().ifBlank { defaultGroup }
                    val storedPath = if (fullImg.startsWith("http") || fullImg.contains("/")) {
                        fullImg
                    } else {
                        "$group/$fullImg"
                    }
                    map[normalizeLookupKey(id)] = storedPath
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

    private val NON_LOL_CHAMPION_KEYS: Set<String> = setOf(
        "gromp",
        "brambleback",
        "elderdragon",
        "krug",
        "pebbles",
        "murkwolf",
        "baronnashor",
        "alune",
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
        "spatula" to "TFT_Item_Spatula.png"
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
