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
import com.example.data.network.TftacticsCompsScraper
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
        source = "Patches: tftips.app/en/patches • LIVE: tftacademy.com • PBE: tftactics.gg",
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
        const val CURRENT_CACHE_VERSION = 14
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
                        pbe.sourceUrl.contains("tftactics.gg", ignoreCase = true) || pbe.id.startsWith("custom_")
                    }.ifEmpty {
                        loadPreloadedTftacticsPbeData(parsed.liveVersionInfo.pbePatchVersion)?.pbeComps.orEmpty()
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
        val preloadedPbeData = loadPreloadedTftacticsPbeData(baseSeed.liveVersionInfo.pbePatchVersion)
        val preloadedPbeComps = preloadedPbeData?.pbeComps.orEmpty()
        val preloadedPbeChamps = preloadedPbeData?.pbeChampionTiers.orEmpty()
        val preloadedPbeItems = preloadedPbeData?.pbeItemTiers.orEmpty()
        val preloadedPbeAugments = preloadedPbeData?.pbeAugmentTiers.orEmpty()

        val seeded = baseSeed.copy(
            liveVersionInfo = baseSeed.liveVersionInfo.copy(
                pbeSetName = preloadedPbeData?.detectedPbeSet ?: baseSeed.liveVersionInfo.pbeSetName
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
            pbeChampionIcons = preloadedPbeData?.pbeChampionIcons.orEmpty(),
            pbeTraitIcons = preloadedPbeData?.pbeTraitIcons.orEmpty(),
            pbeItemIcons = preloadedPbeData?.pbeItemIcons.orEmpty(),
            pbeChampionCosts = preloadedPbeData?.pbeChampionCosts.orEmpty(),
            pbeChampionTraits = preloadedPbeData?.pbeChampionTraits.orEmpty()
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
            runCatching {
                val rawPbeJson = appContext.assets.open(PBE_PRELOAD_ASSET_FILE)
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }
                val parsedPbe = TftacticsCompsScraper().parsePreloadJson(rawPbeJson, "19.1-PBE")
                TftAssetIconResolver.updatePbeSetCatalog(
                    championIcons = parsedPbe.pbeChampionIcons,
                    championCosts = parsedPbe.pbeChampionCosts,
                    championTraits = parsedPbe.pbeChampionTraits,
                    traitIcons = parsedPbe.pbeTraitIcons,
                    itemIcons = parsedPbe.pbeItemIcons
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

    private fun loadPreloadedTftacticsPbeData(
        pbePatchVersion: String
    ): com.example.data.network.TftacticsPbeFetchResult? {
        return runCatching {
            val rawJson = appContext.assets.open(PBE_PRELOAD_ASSET_FILE).bufferedReader(Charsets.UTF_8).use {
                it.readText()
            }
            val parsed = TftacticsCompsScraper().parsePreloadJson(rawJson, pbePatchVersion)
            TftAssetIconResolver.updatePbeSetCatalog(
                championIcons = parsed.pbeChampionIcons,
                championCosts = parsed.pbeChampionCosts,
                championTraits = parsed.pbeChampionTraits,
                traitIcons = parsed.pbeTraitIcons,
                itemIcons = parsed.pbeItemIcons
            )
            parsed
        }.getOrNull()
    }

    fun writeBundle(bundle: OfflineMetaBundle): Boolean = synchronized(cacheIoLock) {
        val cleanLive = bundle.liveComps.filter {
            it.sourceUrl.contains("tftacademy.com", ignoreCase = true) || it.id.startsWith("custom_")
        }
        val cleanPbe = bundle.pbeComps.filter {
            it.sourceUrl.contains("tftactics.gg", ignoreCase = true) || it.id.startsWith("custom_")
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
        private const val PBE_PRELOAD_ASSET_FILE = "tftactics_pbe_preload.json"
        private const val CATALOG_PRELOAD_ASSET_FILE = "set18_catalog_preload.json"
    }
}
