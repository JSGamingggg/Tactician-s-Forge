package com.example.data.repository

import android.content.Context
import com.example.data.model.AugmentTier
import com.example.data.model.AugmentTierEntry
import com.example.data.model.ChampionTierEntry
import com.example.data.model.ItemCategory
import com.example.data.model.ItemTierEntry
import com.example.data.model.LiveVersionInfo
import com.example.data.model.MetaComp
import com.example.data.model.OfflineCacheStatus
import com.example.data.model.PatchUpdateRecord
import com.example.data.model.TftEnvironment
import com.example.data.model.TierRank
import com.example.data.network.TftAcademyCompsScraper
import com.example.ui.components.TftAssetIconResolver
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class OfflineMetaBundle(
    val cacheVersion: Int = CURRENT_CACHE_VERSION,
    val cachedAtMillis: Long = System.currentTimeMillis(),
    val liveVersionInfo: LiveVersionInfo = LiveVersionInfo(
        livePatchVersion = "18.7",
        pbePatchVersion = "19.1-PBE",
        source = "Tactician's Forge Live Meta Sync",
        checkedAtMillis = System.currentTimeMillis()
    ),
    val liveComps: List<MetaComp> = MetaCatalogData.liveComps,
    val pbeComps: List<MetaComp> = MetaCatalogData.pbeComps,
    val compsSignature: String = "",
    val liveChampions: List<ChampionTierEntry> = MetaCatalogData.liveChampions,
    val pbeChampions: List<ChampionTierEntry> = MetaCatalogData.pbeChampions,
    val liveItems: List<ItemTierEntry> = MetaCatalogData.liveItems,
    val pbeItems: List<ItemTierEntry> = MetaCatalogData.pbeItems,
    val liveAugments: List<AugmentTierEntry> = MetaCatalogData.liveAugments,
    val pbeAugments: List<AugmentTierEntry> = MetaCatalogData.pbeAugments,
    val livePatches: List<PatchUpdateRecord> = MetaCatalogData.livePatches,
    val pbePatches: List<PatchUpdateRecord> = MetaCatalogData.pbePatches,
    val ddragonVersion: String = TftAssetIconResolver.DEFAULT_DDRAGON_VERSION,
    val ddragonChampionJson: String? = null,
    val ddragonItemJson: String? = null,
    val ddragonAugmentJson: String? = null,
    val ddragonTraitJson: String? = null,
    val pbeChampionIcons: Map<String, String> = emptyMap(),
    val pbeTraitIcons: Map<String, String> = emptyMap(),
    val pbeItemIcons: Map<String, String> = emptyMap(),
    val pbeChampionCosts: Map<String, Int> = emptyMap(),
    val pbeChampionTraits: Map<String, List<String>> = emptyMap()
) {
    fun toCacheStatus(
        isOfflineFallback: Boolean = false,
        environment: TftEnvironment? = null
    ): OfflineCacheStatus {
        return when (environment) {
            TftEnvironment.LIVE -> OfflineCacheStatus(
                isReady = true,
                isOfflineFallback = isOfflineFallback,
                cachedAtMillis = cachedAtMillis,
                compsCount = liveComps.size,
                championsCount = liveChampions.size,
                itemsCount = liveItems.size,
                augmentsCount = liveAugments.size
            )
            TftEnvironment.PBE -> OfflineCacheStatus(
                isReady = true,
                isOfflineFallback = isOfflineFallback,
                cachedAtMillis = cachedAtMillis,
                compsCount = pbeComps.size,
                championsCount = pbeChampions.size,
                itemsCount = pbeItems.size,
                augmentsCount = pbeAugments.size
            )
            null -> OfflineCacheStatus(
                isReady = true,
                isOfflineFallback = isOfflineFallback,
                cachedAtMillis = cachedAtMillis,
                compsCount = liveComps.size + pbeComps.size,
                championsCount = liveChampions.size,
                itemsCount = liveItems.size,
                augmentsCount = liveAugments.size
            )
        }
    }

    companion object {
        const val CURRENT_CACHE_VERSION = 16
    }
}

class OfflineMetaCacheStore(context: Context) {
    private val appContext = context.applicationContext
    private val cacheFile = File(appContext.filesDir, CACHE_FILE_NAME)
    private val cacheKey: String = cacheFile.absolutePath

    private data class PreloadedSet18CatalogData(
        val champions: List<ChampionTierEntry>,
        val items: List<ItemTierEntry>,
        val augments: List<AugmentTierEntry>,
        val ddragonVersion: String
    )

    private data class PreloadedSet19PbeCatalogData(
        val detectedPbeSet: String,
        val champions: List<ChampionTierEntry>,
        val items: List<ItemTierEntry>,
        val augments: List<AugmentTierEntry>,
        val championIcons: Map<String, String>,
        val traitIcons: Map<String, String>,
        val itemIcons: Map<String, String>,
        val championCosts: Map<String, Int>,
        val championTraits: Map<String, List<String>>
    )

    fun peekInMemoryOrSeed(): OfflineMetaBundle = sharedBundlesByPath[cacheKey] ?: OfflineMetaBundle()

    suspend fun readOrSeedBundleAsync(): OfflineMetaBundle = withContext(Dispatchers.IO) {
        val bundle = readOrSeedBundle(skipResolverSync = true)
        val shouldApplyDdragon = !TftAssetIconResolver.hasSameDynamicVersion(
            version = bundle.ddragonVersion,
            hasChampJson = !bundle.ddragonChampionJson.isNullOrBlank(),
            hasItemJson = !bundle.ddragonItemJson.isNullOrBlank(),
            hasAugmentJson = !bundle.ddragonAugmentJson.isNullOrBlank()
        )
        val parsedAssets = if (shouldApplyDdragon) {
            TftAssetIconResolver.parseDdragonCatalogInBackground(
                version = bundle.ddragonVersion,
                championJson = bundle.ddragonChampionJson,
                itemJson = bundle.ddragonItemJson,
                augmentJson = bundle.ddragonAugmentJson,
                traitJson = bundle.ddragonTraitJson
            )
        } else {
            null
        }
        val shouldApplyPbe = !TftAssetIconResolver.isPbeCatalogApplied() &&
            (bundle.pbeChampionIcons.isNotEmpty() || bundle.pbeTraitIcons.isNotEmpty() || bundle.pbeItemIcons.isNotEmpty())
        if (parsedAssets != null || shouldApplyPbe) {
            withContext(Dispatchers.Main.immediate) {
                parsedAssets?.let { TftAssetIconResolver.applyParsedDdragonCatalog(it) }
                if (shouldApplyPbe) {
                    applyPersistedPbeCatalogIfPresent(bundle)
                }
            }
        }
        bundle
    }

    fun readOrSeedBundle(skipResolverSync: Boolean = false): OfflineMetaBundle = synchronized(cacheIoLock) {
        ensurePreloadedIconCatalogInitialized()

        sharedBundlesByPath[cacheKey]?.let { cached ->
            if (!skipResolverSync) {
                TftAssetIconResolver.updateDdragonCatalog(
                    version = cached.ddragonVersion,
                    championJson = cached.ddragonChampionJson,
                    itemJson = cached.ddragonItemJson,
                    augmentJson = cached.ddragonAugmentJson,
                    traitJson = cached.ddragonTraitJson
                )
                applyPersistedPbeCatalogIfPresent(cached)
            }
            return cached
        }

        if (cacheFile.exists()) {
            val raw = runCatching { cacheFile.readText(Charsets.UTF_8) }.getOrNull()
            if (!raw.isNullOrBlank()) {
                val parsed = runCatching { sharedAdapter.fromJson(raw) }.getOrNull()
                if (parsed != null &&
                    parsed.cacheVersion >= OfflineMetaBundle.CURRENT_CACHE_VERSION &&
                    parsed.liveItems.isNotEmpty() &&
                    parsed.liveAugments.isNotEmpty()
                ) {
                    val cleanLive = parsed.liveComps.filter {
                        it.sourceUrl.contains("tftacademy.com", ignoreCase = true) || it.id.startsWith("custom_")
                    }.ifEmpty {
                        loadPreloadedTftAcademyComps(parsed.liveVersionInfo.livePatchVersion)
                    }
                    val cleanPbe = parsed.pbeComps.filter { pbe ->
                        pbe.sourceUrl.contains("tftacademy.com", ignoreCase = true) || pbe.id.startsWith("custom_")
                    }
                    val cleanLivePatches = parsed.livePatches.filter {
                        it.id.startsWith("tftips_") || it.sourceUrl.contains("tftips.app", ignoreCase = true)
                    }.ifEmpty { MetaCatalogData.livePatches }
                    val cleanPbePatches = parsed.pbePatches.filter {
                        it.id.startsWith("tftips_") || it.sourceUrl.contains("tftips.app", ignoreCase = true)
                    }.ifEmpty { MetaCatalogData.pbePatches }
                    val sanitized = if (cleanLive.size != parsed.liveComps.size ||
                        cleanPbe.size != parsed.pbeComps.size ||
                        cleanLivePatches.size != parsed.livePatches.size ||
                        cleanPbePatches.size != parsed.pbePatches.size
                    ) {
                        parsed.copy(
                            liveComps = cleanLive,
                            pbeComps = cleanPbe,
                            livePatches = cleanLivePatches,
                            pbePatches = cleanPbePatches,
                            compsSignature = TftAcademyCompsScraper.computeCompsSignature(cleanLive, cleanPbe)
                        )
                    } else {
                        parsed
                    }
                    sharedBundlesByPath[cacheKey] = sanitized
                    if (!skipResolverSync) {
                        TftAssetIconResolver.updateDdragonCatalog(
                            version = sanitized.ddragonVersion,
                            championJson = sanitized.ddragonChampionJson,
                            itemJson = sanitized.ddragonItemJson,
                            augmentJson = sanitized.ddragonAugmentJson,
                            traitJson = sanitized.ddragonTraitJson
                        )
                        applyPersistedPbeCatalogIfPresent(sanitized)
                    }
                    return sanitized
                }
            }
        }

        val baseSeed = OfflineMetaBundle()
        val preloadedCatalog = loadPreloadedSet18CatalogData()
        val preloadedLive = loadPreloadedTftAcademyComps(baseSeed.liveVersionInfo.livePatchVersion)
        val preloadedPbeCatalog = loadPreloadedSet19PbeCatalogData()
        val preloadedPbeComps = emptyList<MetaComp>()
        val preloadedPbeChamps = preloadedPbeCatalog?.champions.orEmpty()
        val preloadedPbeItems = preloadedPbeCatalog?.items.orEmpty()
        val preloadedPbeAugments = preloadedPbeCatalog?.augments.orEmpty()

        val seeded = baseSeed.copy(
            liveVersionInfo = baseSeed.liveVersionInfo.copy(
                pbeSetName = preloadedPbeCatalog?.detectedPbeSet ?: baseSeed.liveVersionInfo.pbeSetName
            ),
            ddragonVersion = preloadedCatalog?.ddragonVersion ?: baseSeed.ddragonVersion,
            liveComps = preloadedLive.ifEmpty { baseSeed.liveComps },
            pbeComps = preloadedPbeComps.ifEmpty { baseSeed.pbeComps },
            compsSignature = TftAcademyCompsScraper.computeCompsSignature(
                preloadedLive.ifEmpty { baseSeed.liveComps },
                preloadedPbeComps.ifEmpty { baseSeed.pbeComps }
            ),
            liveChampions = preloadedCatalog?.champions?.takeIf { it.isNotEmpty() } ?: baseSeed.liveChampions,
            pbeChampions = preloadedPbeChamps.ifEmpty { baseSeed.pbeChampions },
            liveItems = preloadedCatalog?.items?.takeIf { it.isNotEmpty() } ?: baseSeed.liveItems,
            pbeItems = preloadedPbeItems.ifEmpty { baseSeed.pbeItems },
            liveAugments = preloadedCatalog?.augments?.takeIf { it.isNotEmpty() } ?: baseSeed.liveAugments,
            pbeAugments = preloadedPbeAugments.ifEmpty { baseSeed.pbeAugments },
            pbeChampionIcons = preloadedPbeCatalog?.championIcons.orEmpty(),
            pbeTraitIcons = preloadedPbeCatalog?.traitIcons.orEmpty(),
            pbeItemIcons = preloadedPbeCatalog?.itemIcons.orEmpty(),
            pbeChampionCosts = preloadedPbeCatalog?.championCosts.orEmpty(),
            pbeChampionTraits = preloadedPbeCatalog?.championTraits.orEmpty()
        )
        writeBundle(seeded)
        if (!skipResolverSync) {
            TftAssetIconResolver.updateDdragonCatalog(
                version = seeded.ddragonVersion,
                championJson = seeded.ddragonChampionJson,
                itemJson = seeded.ddragonItemJson,
                augmentJson = seeded.ddragonAugmentJson,
                traitJson = seeded.ddragonTraitJson
            )
            applyPersistedPbeCatalogIfPresent(seeded)
        }
        return@synchronized seeded
    }

    private fun applyPersistedPbeCatalogIfPresent(bundle: OfflineMetaBundle) {
        if (bundle.pbeChampionIcons.isNotEmpty() || bundle.pbeTraitIcons.isNotEmpty() || bundle.pbeItemIcons.isNotEmpty()) {
            TftAssetIconResolver.updatePbeSetCatalog(
                championIcons = bundle.pbeChampionIcons,
                championCosts = bundle.pbeChampionCosts,
                championTraits = bundle.pbeChampionTraits,
                traitIcons = bundle.pbeTraitIcons,
                itemIcons = bundle.pbeItemIcons
            )
        }
    }

    private fun ensurePreloadedIconCatalogInitialized() {
        if (!TftAssetIconResolver.isPreloadedCatalogApplied()) {
            runCatching {
                val rawJson = appContext.assets.open(CATALOG_PRELOAD_ASSET_FILE)
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }
                TftAssetIconResolver.parsePreloadedSetCatalogInBackground(rawJson)?.let { parsed ->
                    TftAssetIconResolver.applyPreloadedSetCatalog(parsed)
                }
            }
        }
        if (!TftAssetIconResolver.isPbeCatalogApplied()) {
            loadPreloadedSet19PbeCatalogData()?.let { parsedPbe ->
                TftAssetIconResolver.updatePbeSetCatalog(
                    championIcons = parsedPbe.championIcons,
                    championCosts = parsedPbe.championCosts,
                    championTraits = parsedPbe.championTraits,
                    traitIcons = parsedPbe.traitIcons,
                    itemIcons = parsedPbe.itemIcons
                )
            }
        }
    }

    private fun loadPreloadedSet18CatalogData(): PreloadedSet18CatalogData? {
        return runCatching {
            val rawJson = appContext.assets.open(CATALOG_PRELOAD_ASSET_FILE)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            val root = JSONObject(rawJson)
            val ddVer = root.optString("ddragonVersion").ifBlank { TftAssetIconResolver.DEFAULT_DDRAGON_VERSION }

            fun parseStrings(arr: org.json.JSONArray?): List<String> {
                if (arr == null) return emptyList()
                return (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { it.isNotBlank() } }
            }

            val champsArr = root.optJSONArray("champions")
            val champs = if (champsArr != null) {
                (0 until champsArr.length()).mapNotNull { i ->
                    val o = champsArr.optJSONObject(i) ?: return@mapNotNull null
                    ChampionTierEntry(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        cost = o.optInt("cost", 1).coerceIn(1, 5),
                        tier = TierRank.fromRaw(o.optString("tier")),
                        environment = TftEnvironment.LIVE,
                        traits = parseStrings(o.optJSONArray("traits")),
                        role = o.optString("role"),
                        avgPlacement = o.optDouble("avgPlacement", 4.0),
                        top4Rate = o.optDouble("top4Rate", 52.0),
                        bestItems = parseStrings(o.optJSONArray("bestItems")),
                        synergyComps = parseStrings(o.optJSONArray("synergyComps")),
                        abilityName = o.optString("abilityName"),
                        abilityDescription = o.optString("abilityDescription")
                    )
                }
            } else emptyList()

            val itemsArr = root.optJSONArray("items")
            val items = if (itemsArr != null) {
                (0 until itemsArr.length()).mapNotNull { i ->
                    val o = itemsArr.optJSONObject(i) ?: return@mapNotNull null
                    val cat = runCatching { ItemCategory.valueOf(o.optString("category")) }
                        .getOrDefault(ItemCategory.FIGHTER)
                    ItemTierEntry(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        tier = TierRank.fromRaw(o.optString("tier")),
                        environment = TftEnvironment.LIVE,
                        category = cat,
                        components = parseStrings(o.optJSONArray("components")),
                        statsSummary = o.optString("statsSummary"),
                        avgPlacement = o.optDouble("avgPlacement", 4.0),
                        top4Rate = o.optDouble("top4Rate", 54.0),
                        bestHolders = parseStrings(o.optJSONArray("bestHolders"))
                    )
                }
            } else emptyList()

            val augsArr = root.optJSONArray("augments")
            val augs = if (augsArr != null) {
                (0 until augsArr.length()).mapNotNull { i ->
                    val o = augsArr.optJSONObject(i) ?: return@mapNotNull null
                    val augTier = runCatching { AugmentTier.valueOf(o.optString("augmentTier")) }
                        .getOrDefault(AugmentTier.GOLD)
                    AugmentTierEntry(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        tier = TierRank.fromRaw(o.optString("tier")),
                        augmentTier = augTier,
                        environment = TftEnvironment.LIVE,
                        category = o.optString("category"),
                        avgPlacement = o.optDouble("avgPlacement", 4.0),
                        top4Rate = o.optDouble("top4Rate", 54.0),
                        description = o.optString("description"),
                        synergyComps = parseStrings(o.optJSONArray("synergyComps"))
                    )
                }
            } else emptyList()

            PreloadedSet18CatalogData(
                champions = champs,
                items = items,
                augments = augs,
                ddragonVersion = ddVer
            )
        }.getOrNull()
    }

    private fun loadPreloadedTftAcademyComps(livePatchVersion: String): List<MetaComp> {
        return runCatching {
            val rawJson = appContext.assets.open(PRELOAD_ASSET_FILE).bufferedReader(Charsets.UTF_8).use {
                it.readText()
            }
            val parsed = TftAcademyCompsScraper().parseCompsDocument(
                html = rawJson,
                targetEnvironment = TftEnvironment.LIVE,
                patchTag = livePatchVersion,
                isDedicatedEnvPage = true,
                includeRemoteBundles = false
            )
            parsed.comps.map { comp ->
                comp.copy(sourceUrl = TftAcademyCompsScraper.TFTACADEMY_COMPS_URL)
            }
        }.getOrDefault(emptyList())
    }

    private fun loadPreloadedSet19PbeCatalogData(): PreloadedSet19PbeCatalogData? {
        return runCatching {
            val rawJson = appContext.assets.open(PBE_PRELOAD_ASSET_FILE).bufferedReader(Charsets.UTF_8).use {
                it.readText()
            }
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

            fun extractStringValues(raw: Any?): List<String> = when (raw) {
                is org.json.JSONArray -> (0 until raw.length()).mapNotNull { i ->
                    raw.optString(i).trim().takeIf { it.isNotBlank() }
                }
                is String -> listOfNotNull(raw.trim().takeIf { it.isNotBlank() })
                else -> emptyList()
            }

            val champIcons = readStringMap(root.optJSONObject("championIcons"))
            val traitIcons = readStringMap(root.optJSONObject("traitIcons"))
            val itemIcons = readStringMap(root.optJSONObject("itemIcons"))
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
                        val list = extractStringValues(obj.optJSONArray(k))
                        if (list.isNotEmpty()) put(k, list)
                    }
                }
            }

            val detailsObj = root.optJSONObject("championDetails")
            val champEntries = LinkedHashSet<String>().apply {
                addAll(champCosts.keys)
                addAll(champIcons.keys)
            }.mapNotNull { name ->
                val cleanName = name.trim()
                if (cleanName.isBlank()) return@mapNotNull null
                val cost = (champCosts[cleanName] ?: 3).coerceIn(1, 5)
                val traits = champTraits[cleanName].orEmpty()
                val detail = detailsObj?.optJSONObject(cleanName)
                val tierNum = detail?.optInt("tier", 3) ?: 3
                val bestItems = extractStringValues(detail?.opt("items")).take(3)
                val abilityName = detail?.optString("abilityName").orEmpty().trim()
                val abilityDesc = detail?.optString("abilityDescription").orEmpty().trim()
                val tier = when {
                    tierNum <= 1 -> TierRank.S
                    tierNum == 2 || cost >= 5 -> TierRank.A
                    cost == 4 -> TierRank.B
                    else -> TierRank.C
                }
                val avg = when {
                    cost >= 5 -> 3.95
                    cost == 4 -> 4.18
                    else -> 4.42
                }
                val top4 = kotlin.math.round((100.0 - (avg - 1.5) * 18.0).coerceIn(44.0, 74.0) * 10.0) / 10.0
                val slug = cleanName.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_").trim('_')
                ChampionTierEntry(
                    id = "pbe_champ_$slug",
                    name = cleanName,
                    cost = cost,
                    tier = tier,
                    environment = TftEnvironment.PBE,
                    traits = traits,
                    role = when {
                        cost >= 5 -> "5-Cost Flex"
                        traits.isNotEmpty() -> "${traits.first()} Unit"
                        else -> "Synergy Unit"
                    },
                    avgPlacement = avg,
                    top4Rate = top4,
                    bestItems = bestItems,
                    synergyComps = emptyList(),
                    abilityName = abilityName,
                    abilityDescription = abilityDesc
                )
            }.sortedWith(
                compareBy<ChampionTierEntry> { it.tier.sortOrder }
                    .thenBy { it.avgPlacement }
                    .thenByDescending { it.cost }
                    .thenBy { it.name }
            )

            val itemEntries = mutableListOf<ItemTierEntry>()
            val seenItemNames = LinkedHashSet<String>()
            val itemsArr = root.optJSONArray("items")
            if (itemsArr != null) {
                for (i in 0 until itemsArr.length()) {
                    val obj = itemsArr.optJSONObject(i) ?: continue
                    val setArr = obj.optJSONArray("set")
                    if (setArr != null && setArr.length() > 0) {
                        var hasSet = false
                        for (sIdx in 0 until setArr.length()) {
                            if (setArr.optInt(sIdx, -1) == setNum) {
                                hasSet = true
                                break
                            }
                        }
                        if (!hasSet) continue
                    }
                    val name = obj.optString("name").trim()
                    if (name.isBlank() || !seenItemNames.add(name.lowercase(Locale.US))) continue
                    val combine = extractStringValues(obj.opt("combine"))
                    val bonusLines = extractStringValues(obj.opt("bonus"))
                    val statsArr = obj.optJSONArray("stats")
                    val statTokens = if (statsArr != null) {
                        (0 until statsArr.length()).mapNotNull { sIdx ->
                            val st = statsArr.optJSONObject(sIdx) ?: return@mapNotNull null
                            val sName = st.optString("name").trim().uppercase(Locale.US)
                            val sVal = st.optString("value").trim()
                            if (sName.isNotBlank() && sVal.isNotBlank()) "+$sVal $sName" else null
                        }
                    } else emptyList()
                    val statsSummary = buildList {
                        if (statTokens.isNotEmpty()) add(statTokens.joinToString(", "))
                        bonusLines.forEach { add(it) }
                    }.joinToString(" • ").ifBlank { "$name — Set $setNum PBE item." }
                    val tierNum = obj.optInt("tier", 3)
                    val tier = when {
                        tierNum <= 1 -> TierRank.S
                        tierNum == 2 -> TierRank.A
                        tierNum == 3 -> TierRank.B
                        else -> TierRank.C
                    }
                    val avg = when (tier) {
                        TierRank.S -> 3.52
                        TierRank.A -> 3.84
                        TierRank.B -> 4.18
                        TierRank.C -> 4.46
                        TierRank.X -> 4.30
                    }
                    val top4 = kotlin.math.round((100.0 - (avg - 1.5) * 18.0) * 10.0) / 10.0
                    val slug = name.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_").trim('_')
                    val cat = MetaCatalogSyncHelper.classifyItemCategory(name, statsSummary)
                    itemEntries.add(
                        ItemTierEntry(
                            id = "pbe_item_$slug",
                            name = name,
                            tier = tier,
                            environment = TftEnvironment.PBE,
                            category = cat,
                            components = combine,
                            statsSummary = statsSummary,
                            avgPlacement = avg,
                            top4Rate = top4,
                            bestHolders = emptyList()
                        )
                    )
                }
            }

            val augEntries = mutableListOf<AugmentTierEntry>()
            val seenAugNames = LinkedHashSet<String>()
            val augsArr = root.optJSONArray("augments")
            if (augsArr != null) {
                for (i in 0 until augsArr.length()) {
                    val obj = augsArr.optJSONObject(i) ?: continue
                    val name = obj.optString("name").trim()
                    if (name.isBlank() || !seenAugNames.add(name.lowercase(Locale.US))) continue
                    val augTier = AugmentTier.fromRaw(obj.optString("tier"))
                    val desc = obj.optString("bonus").trim().ifBlank { "$name — PBE augment." }
                    val tierListNum = obj.optInt("tierlist", 3)
                    val tier = when {
                        tierListNum <= 1 -> TierRank.S
                        tierListNum == 2 -> TierRank.A
                        tierListNum == 3 -> TierRank.B
                        else -> TierRank.C
                    }
                    val avg = when (tier) {
                        TierRank.S -> 3.48
                        TierRank.A -> 3.84
                        TierRank.B -> 4.18
                        TierRank.C -> 4.48
                        TierRank.X -> 4.32
                    }
                    val top4 = kotlin.math.round((100.0 - (avg - 1.5) * 18.0) * 10.0) / 10.0
                    val cat = when {
                        name.contains("Crown", true) || name.contains("Crest", true) || name.contains("Emblem", true) ||
                            desc.contains("Emblem", true) -> "Trait / Emblem"
                        desc.contains("gold", true) || desc.contains("XP", false) || desc.contains("reroll", true) -> "Economy / XP"
                        else -> "Combat / Items"
                    }
                    val slug = (obj.optString("id").ifBlank { name })
                        .lowercase(Locale.US)
                        .replace(Regex("[^a-z0-9]+"), "_")
                        .trim('_')
                    augEntries.add(
                        AugmentTierEntry(
                            id = "pbe_aug_$slug",
                            name = name,
                            tier = tier,
                            augmentTier = augTier,
                            environment = TftEnvironment.PBE,
                            category = cat,
                            avgPlacement = avg,
                            top4Rate = top4,
                            description = desc,
                            synergyComps = emptyList()
                        )
                    )
                }
            }

            TftAssetIconResolver.updatePbeSetCatalog(
                championIcons = champIcons,
                championCosts = champCosts,
                championTraits = champTraits,
                traitIcons = traitIcons,
                itemIcons = itemIcons
            )

            PreloadedSet19PbeCatalogData(
                detectedPbeSet = setLabel,
                champions = champEntries,
                items = itemEntries.sortedWith(compareBy<ItemTierEntry> { it.tier.sortOrder }.thenBy { it.avgPlacement }.thenBy { it.name }),
                augments = augEntries.sortedWith(compareBy<AugmentTierEntry> { it.tier.sortOrder }.thenBy { it.avgPlacement }.thenBy { it.name }),
                championIcons = champIcons,
                traitIcons = traitIcons,
                itemIcons = itemIcons,
                championCosts = champCosts,
                championTraits = champTraits
            )
        }.getOrNull()
    }

    fun writeBundle(bundle: OfflineMetaBundle): Boolean = synchronized(cacheIoLock) {
        val cleanLive = bundle.liveComps.filter {
            it.sourceUrl.contains("tftacademy.com", ignoreCase = true) || it.id.startsWith("custom_")
        }
        val cleanPbe = bundle.pbeComps.filter {
            it.sourceUrl.contains("tftacademy.com", ignoreCase = true) || it.id.startsWith("custom_")
        }
        val normalized = bundle.copy(
            cacheVersion = OfflineMetaBundle.CURRENT_CACHE_VERSION,
            liveComps = cleanLive,
            pbeComps = cleanPbe,
            compsSignature = bundle.compsSignature.ifBlank {
                TftAcademyCompsScraper.computeCompsSignature(cleanLive, cleanPbe)
            }
        )
        sharedBundlesByPath[cacheKey] = normalized
        var tmpFile: File? = null
        return@synchronized runCatching {
            val parent = cacheFile.parentFile ?: appContext.filesDir
            if (!parent.exists()) {
                parent.mkdirs()
            }
            tmpFile = File.createTempFile("tft_meta_cache_", ".tmp", parent)
            tmpFile!!.writeText(sharedAdapter.toJson(normalized), Charsets.UTF_8)
            if (cacheFile.exists()) {
                cacheFile.delete()
            }
            val renamed = tmpFile!!.renameTo(cacheFile)
            if (!renamed) {
                tmpFile!!.copyTo(cacheFile, overwrite = true)
                tmpFile!!.delete()
            }
            cacheFile.exists() && cacheFile.length() > 0L
        }.onFailure {
            runCatching { tmpFile?.delete() }
        }.getOrDefault(false)
    }

    fun hasPersistedCache(): Boolean = synchronized(cacheIoLock) {
        cacheFile.exists() && cacheFile.length() > 0L
    }

    companion object {
        private val cacheIoLock = Any()
        private val sharedBundlesByPath = ConcurrentHashMap<String, OfflineMetaBundle>()
        private val sharedMoshi: Moshi by lazy {
            Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()
        }
        private val sharedAdapter by lazy {
            sharedMoshi.adapter(OfflineMetaBundle::class.java)
        }
        private const val CACHE_FILE_NAME = "tft_offline_meta_cache_v2.json"
        private const val PRELOAD_ASSET_FILE = "tftacademy_preload.json"
        private const val PBE_PRELOAD_ASSET_FILE = "set19_pbe_catalog_preload.json"
        private const val CATALOG_PRELOAD_ASSET_FILE = "set18_catalog_preload.json"
    }
}
