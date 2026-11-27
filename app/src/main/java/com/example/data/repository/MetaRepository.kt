package com.example.data.repository

import android.content.Context
import com.example.data.model.AugmentTierEntry
import com.example.data.model.ChampionTierEntry
import com.example.data.model.ItemTierEntry
import com.example.data.model.LiveVersionInfo
import com.example.data.model.MetaComp
import com.example.data.model.MetaSyncResult
import com.example.data.model.PatchUpdateRecord
import com.example.data.model.TftEnvironment
import com.example.data.network.DDragonApi
import com.example.data.network.RiotApiClientFactory
import com.example.data.network.TftAcademyCompsScraper
import com.example.data.network.TftNinjaCompsScraper
import com.example.data.network.TftipsPatchScraper
import com.example.data.teamplanner.TftTeamPlannerCodeEncoder
import com.example.ui.components.TftAssetIconResolver
import okhttp3.OkHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request

object MetaCacheUpdates {
    private val _lastCacheWriteMillis = MutableStateFlow(0L)
    val lastCacheWriteMillis: StateFlow<Long> = _lastCacheWriteMillis.asStateFlow()

    fun notifyUpdated() {
        _lastCacheWriteMillis.update { prev -> maxOf(prev + 1L, System.currentTimeMillis()) }
    }
}

class MetaRepository(
    private val cacheStore: OfflineMetaCacheStore,
    private val tftipsScraper: TftipsPatchScraper = TftipsPatchScraper(),
    private val tftAcademyScraper: TftAcademyCompsScraper = TftAcademyCompsScraper(),
    private val tftNinjaScraper: TftNinjaCompsScraper = TftNinjaCompsScraper(),
    private val ddragonApi: DDragonApi = RiotApiClientFactory.ddragonApi,
    private val publicClient: OkHttpClient = RiotApiClientFactory.publicOkHttp,
    private val appContext: Context? = null
) {
    constructor(context: Context) : this(
        cacheStore = OfflineMetaCacheStore(context.applicationContext),
        appContext = context.applicationContext
    )

    fun peekOfflineBundle(): OfflineMetaBundle = cacheStore.peekInMemoryOrSeed()

    suspend fun loadOfflineBundleAsync(): OfflineMetaBundle = cacheStore.readOrSeedBundleAsync()

    fun loadOfflineBundle(): OfflineMetaBundle = cacheStore.readOrSeedBundle()

    fun getComps(environment: TftEnvironment, bundle: OfflineMetaBundle = loadOfflineBundle()): List<MetaComp> =
        TftNinjaCompsScraper.sortCompsTierSDown(
            when (environment) {
                TftEnvironment.LIVE -> bundle.liveComps
                TftEnvironment.PBE -> bundle.pbeComps
            }
        )

    fun getChampions(environment: TftEnvironment, bundle: OfflineMetaBundle = loadOfflineBundle()): List<ChampionTierEntry> =
        when (environment) {
            TftEnvironment.LIVE -> bundle.liveChampions
            TftEnvironment.PBE -> bundle.pbeChampions
        }

    fun getItems(environment: TftEnvironment, bundle: OfflineMetaBundle = loadOfflineBundle()): List<ItemTierEntry> =
        when (environment) {
            TftEnvironment.LIVE -> bundle.liveItems
            TftEnvironment.PBE -> bundle.pbeItems
        }

    fun getAugments(environment: TftEnvironment, bundle: OfflineMetaBundle = loadOfflineBundle()): List<AugmentTierEntry> =
        when (environment) {
            TftEnvironment.LIVE -> bundle.liveAugments
            TftEnvironment.PBE -> bundle.pbeAugments
        }

    fun getPatches(environment: TftEnvironment, bundle: OfflineMetaBundle = loadOfflineBundle()): List<PatchUpdateRecord> =
        when (environment) {
            TftEnvironment.LIVE -> bundle.livePatches
            TftEnvironment.PBE -> bundle.pbePatches
        }

    /**
     * Fetches LIVE & PBE patch updates from https://tftips.app/en/patches,
     * LIVE team compositions exclusively from https://tft.ninja/comps, and
     * PBE team compositions exclusively from https://tft.ninja/comps?e=pbe,
     * then persists updates offline.
     */
    suspend fun refreshAndPersistMeta(forceReplaceComps: Boolean = false): MetaSyncResult = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val currentBundle = cacheStore.readOrSeedBundle(skipResolverSync = true)
            val prevLiveVer = currentBundle.liveVersionInfo.livePatchVersion
            val prevPbeVer = currentBundle.liveVersionInfo.pbePatchVersion
            val existingCompIds = (currentBundle.liveComps + currentBundle.pbeComps).map { it.id }.toSet()

            val liveSetHint = Regex("""Set\s*(\d+)""", RegexOption.IGNORE_CASE)
                .find(currentBundle.liveVersionInfo.liveSetName)
                ?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: 18
            val pbeSetHint = Regex("""Set\s*(\d+)""", RegexOption.IGNORE_CASE)
                .find(currentBundle.liveVersionInfo.pbeSetName)
                ?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?.coerceAtLeast(19)
                ?: 19

            // Launch ALL independent scrapes concurrently immediately — never block tft.ninja on tftips/ddragon
            val tftNinjaLiveDeferred = async {
                runCatching {
                    tftNinjaScraper.fetchLiveTeamComps(livePatchVersion = prevLiveVer)
                }.getOrNull()
            }
            val tftNinjaPbeDeferred = async {
                runCatching {
                    tftNinjaScraper.fetchPbeTeamComps(
                        pbePatchVersion = prevPbeVer,
                        defaultPbeSetNumber = pbeSetHint
                    )
                }.getOrNull()
            }
            val tftipsDeferred = async { runCatching { tftipsScraper.fetchPatchesFromTftips() }.getOrNull() }
            val ddragonDeferred = async {
                runCatching { ddragonApi.getVersions() }.getOrNull()
            }
            val tftAcademyCatalogDeferred = async {
                runCatching {
                    tftAcademyScraper.fetchLiveItemsAndAugmentsTierlist(setNumber = liveSetHint)
                }.getOrNull()
            }
            val tftAcademyPbeCatalogDeferred = async {
                runCatching {
                    tftAcademyScraper.fetchLiveItemsAndAugmentsTierlist(setNumber = pbeSetHint)
                }.getOrNull()
            }
            val teamPlannerDeferred = appContext?.let { ctx ->
                async {
                    runCatching {
                        TftTeamPlannerCodeEncoder.refreshMappingsFromNetwork(ctx, publicClient)
                    }.getOrNull()
                }
            }

            // Phase 1: Immediately replace cached LIVE & PBE compositions from Tier S down as soon as tft.ninja responds
            val tftNinjaLiveResult = tftNinjaLiveDeferred.await()
            val tftNinjaPbeResult = tftNinjaPbeDeferred.await()

            val incomingLiveComps = TftNinjaCompsScraper.sortCompsTierSDown(
                tftNinjaLiveResult?.comps.orEmpty()
                    .filter { it.sourceUrl.contains("tft.ninja", ignoreCase = true) }
            )
            val incomingPbeComps = TftNinjaCompsScraper.sortCompsTierSDown(
                tftNinjaPbeResult?.comps.orEmpty()
                    .filter { it.sourceUrl.contains("tft.ninja", ignoreCase = true) }
            )

            val customLiveComps = currentBundle.liveComps.filter { it.id.startsWith("custom_") }
            val customPbeComps = currentBundle.pbeComps.filter { it.id.startsWith("custom_") }

            val liveCompsArrived = tftNinjaLiveResult?.fetchedFromNetwork == true && incomingLiveComps.isNotEmpty()
            val pbeCompsArrived = tftNinjaPbeResult?.fetchedFromNetwork == true && incomingPbeComps.isNotEmpty()

            val phase1LiveComps = TftNinjaCompsScraper.sortCompsTierSDown(
                if (liveCompsArrived) {
                    incomingLiveComps + customLiveComps
                } else {
                    currentBundle.liveComps.filter {
                        it.sourceUrl.contains("tft.ninja", ignoreCase = true) || it.id.startsWith("custom_")
                    }
                }
            )
            val phase1PbeComps = TftNinjaCompsScraper.sortCompsTierSDown(
                if (pbeCompsArrived) {
                    incomingPbeComps + customPbeComps
                } else {
                    currentBundle.pbeComps.filter {
                        it.sourceUrl.contains("tft.ninja", ignoreCase = true) || it.id.startsWith("custom_")
                    }
                }
            )

            val liveSetName = tftNinjaLiveResult?.detectedSet?.let { "Set $it" }
                ?: currentBundle.liveVersionInfo.liveSetName
            val pbeSetName = tftNinjaPbeResult?.detectedSet?.let { "Set $it (PBE)" }
                ?: currentBundle.liveVersionInfo.pbeSetName

            if (liveCompsArrived || pbeCompsArrived) {
                val instantNow = System.currentTimeMillis()
                val instantBundle = currentBundle.copy(
                    cachedAtMillis = instantNow,
                    liveVersionInfo = currentBundle.liveVersionInfo.copy(
                        liveSetName = liveSetName,
                        pbeSetName = pbeSetName,
                        checkedAtMillis = instantNow
                    ),
                    liveComps = phase1LiveComps,
                    pbeComps = phase1PbeComps,
                    compsSignature = TftNinjaCompsScraper.computeCompsSignature(phase1LiveComps, phase1PbeComps)
                )
                cacheStore.writeBundle(instantBundle)
                MetaCacheUpdates.notifyUpdated()
            }

            // Phase 2: Complete patch notes, Data Dragon icons, TFTAcademy catalog tiers, and Team Planner codes
            val tftipsResult = tftipsDeferred.await()
            val liveVersion = tftipsResult?.livePatchVersion ?: prevLiveVer
            val pbeVersion = tftipsResult?.pbePatchVersion ?: prevPbeVer
            var syncedFromNetwork = tftipsResult?.fetchedFromNetwork == true ||
                tftNinjaLiveResult?.fetchedFromNetwork == true ||
                tftNinjaPbeResult?.fetchedFromNetwork == true

            var ddragonVersion = currentBundle.ddragonVersion
            var ddragonChampJson = currentBundle.ddragonChampionJson
            var ddragonItemJson = currentBundle.ddragonItemJson
            var ddragonAugmentJson = currentBundle.ddragonAugmentJson
            var ddragonTraitJson = currentBundle.ddragonTraitJson

            try {
                val ddragonResp = ddragonDeferred.await()
                if (ddragonResp != null && ddragonResp.isSuccessful) {
                    val latestDdragon = ddragonResp.body()?.firstOrNull()
                    if (!latestDdragon.isNullOrBlank()) {
                        syncedFromNetwork = true
                        val versionChanged = latestDdragon != ddragonVersion
                        ddragonVersion = latestDdragon
                        val ddChampDeferred = if (versionChanged || ddragonChampJson.isNullOrBlank()) {
                            async {
                                runCatching {
                                    ddragonApi.getTftChampionsJson(latestDdragon).body()?.string()
                                }.getOrNull()
                            }
                        } else null
                        val ddItemDeferred = if (versionChanged || ddragonItemJson.isNullOrBlank()) {
                            async {
                                runCatching {
                                    ddragonApi.getTftItemsJson(latestDdragon).body()?.string()
                                }.getOrNull()
                            }
                        } else null
                        val ddAugDeferred = if (versionChanged || ddragonAugmentJson.isNullOrBlank()) {
                            async {
                                runCatching {
                                    ddragonApi.getTftAugmentsJson(latestDdragon).body()?.string()
                                }.getOrNull()
                            }
                        } else null
                        val ddTraitDeferred = if (versionChanged || ddragonTraitJson.isNullOrBlank()) {
                            async {
                                runCatching {
                                    ddragonApi.getTftTraitsJson(latestDdragon).body()?.string()
                                }.getOrNull()
                            }
                        } else null
                        ddChampDeferred?.await()?.let { ddragonChampJson = it }
                        ddItemDeferred?.await()?.let { ddragonItemJson = it }
                        ddAugDeferred?.await()?.let { ddragonAugmentJson = it }
                        ddTraitDeferred?.await()?.let { ddragonTraitJson = it }
                        val parsedCatalog = TftAssetIconResolver.parseDdragonCatalogInBackground(
                            version = ddragonVersion,
                            championJson = ddragonChampJson,
                            itemJson = ddragonItemJson,
                            augmentJson = ddragonAugmentJson,
                            traitJson = ddragonTraitJson
                        )
                        withContext(Dispatchers.Main.immediate) {
                            TftAssetIconResolver.applyParsedDdragonCatalog(parsedCatalog)
                        }
                    }
                }
            } catch (_: Exception) {
                // Keep offline cache silently
            }

            val tftAcademyCatalog = tftAcademyCatalogDeferred.await()
            val tftAcademyPbeCatalog = tftAcademyPbeCatalogDeferred.await()
            teamPlannerDeferred?.await()
            if (tftAcademyCatalog?.fetchedFromNetwork == true ||
                tftAcademyPbeCatalog?.fetchedFromNetwork == true
            ) {
                syncedFromNetwork = true
            }

            val sourceLabel = if (syncedFromNetwork) {
                "Patches: tftips.app/en/patches • LIVE: tft.ninja/comps • PBE: tft.ninja/comps?e=pbe"
            } else {
                "Offline Cache (tftips.app & tft.ninja)"
            }

            val now = System.currentTimeMillis()
            val mergedLivePatches = mergePatchLists(
                incoming = tftipsResult?.livePatchRecords.orEmpty(),
                existing = currentBundle.livePatches
            )
            val mergedPbePatches = mergePatchLists(
                incoming = tftipsResult?.pbePatchRecords.orEmpty(),
                existing = currentBundle.pbePatches
            )

            // Refresh patchTag on newly scraped comps if tftips reported a newer live/PBE patch version
            val rawLiveComps = phase1LiveComps.map { comp ->
                if (!comp.id.startsWith("custom_") && liveCompsArrived && comp.patchTag == prevLiveVer && liveVersion != prevLiveVer) {
                    comp.copy(patchTag = liveVersion)
                } else {
                    comp
                }
            }
            val rawPbeComps = phase1PbeComps.map { comp ->
                if (!comp.id.startsWith("custom_") && pbeCompsArrived && comp.patchTag == prevPbeVer && pbeVersion != prevPbeVer) {
                    comp.copy(patchTag = pbeVersion)
                } else {
                    comp
                }
            }
            // Automatically attach verified v02 Riot Team Planner copy codes to LIVE & PBE in parallel
            val resolvedLiveCompsDeferred = async {
                TftNinjaCompsScraper.sortCompsTierSDown(
                    appContext?.let { ctx ->
                        runCatching {
                            TftTeamPlannerCodeEncoder.enrichCompsWithTeamCodes(
                                context = ctx,
                                comps = rawLiveComps,
                                environment = TftEnvironment.LIVE,
                                client = publicClient
                            )
                        }.getOrNull()
                    } ?: rawLiveComps
                )
            }
            val resolvedPbeCompsDeferred = async {
                TftNinjaCompsScraper.sortCompsTierSDown(
                    appContext?.let { ctx ->
                        runCatching {
                            TftTeamPlannerCodeEncoder.enrichCompsWithTeamCodes(
                                context = ctx,
                                comps = rawPbeComps,
                                environment = TftEnvironment.PBE,
                                client = publicClient
                            )
                        }.getOrNull()
                    } ?: rawPbeComps
                )
            }
            val resolvedLiveComps = resolvedLiveCompsDeferred.await()
            val resolvedPbeComps = resolvedPbeCompsDeferred.await()

            val resolvedLiveCatalogDeferred = if (syncedFromNetwork) {
                async {
                    MetaCatalogSyncHelper.refreshLiveCatalog(
                        existingChampions = currentBundle.liveChampions,
                        existingItems = currentBundle.liveItems,
                        existingAugments = currentBundle.liveAugments,
                        liveComps = resolvedLiveComps,
                        liveSetNumber = tftNinjaLiveResult?.detectedSet ?: liveSetHint,
                        ddragonChampJson = ddragonChampJson,
                        ddragonItemJson = ddragonItemJson,
                        ddragonAugmentJson = ddragonAugmentJson,
                        academyTiers = tftAcademyCatalog,
                        ninjaChampRecommendations = tftNinjaLiveResult?.championRecommendations.orEmpty()
                    )
                }
            } else null
            val resolvedPbeCatalogDeferred = if (syncedFromNetwork) {
                async {
                    MetaCatalogSyncHelper.refreshPbeCatalog(
                        existingChampions = currentBundle.pbeChampions,
                        existingItems = currentBundle.pbeItems,
                        existingAugments = currentBundle.pbeAugments,
                        pbeComps = resolvedPbeComps,
                        academyTiers = tftAcademyPbeCatalog,
                        ninjaChampRecommendations = tftNinjaPbeResult?.championRecommendations.orEmpty()
                    )
                }
            } else null
            val resolvedLiveCatalog = resolvedLiveCatalogDeferred?.await()
            val resolvedPbeCatalog = resolvedPbeCatalogDeferred?.await()
            val resolvedPbeChampions = resolvedPbeCatalog?.champions ?: currentBundle.pbeChampions
            val resolvedPbeItems = resolvedPbeCatalog?.items ?: currentBundle.pbeItems
            val resolvedPbeAugments = resolvedPbeCatalog?.augments ?: currentBundle.pbeAugments
            val resolvedPbeChampIcons = currentBundle.pbeChampionIcons
            val resolvedPbeTraitIcons = currentBundle.pbeTraitIcons
            val resolvedPbeItemIcons = currentBundle.pbeItemIcons
            val resolvedPbeChampCosts = currentBundle.pbeChampionCosts
            val resolvedPbeChampTraits = currentBundle.pbeChampionTraits

            val newSignature = TftNinjaCompsScraper.computeCompsSignature(resolvedLiveComps, resolvedPbeComps)
            val compsUpdated = currentBundle.compsSignature.isNotBlank() &&
                newSignature != currentBundle.compsSignature &&
                (resolvedLiveComps.isNotEmpty() || resolvedPbeComps.isNotEmpty())
            val newlyAddedComps = (resolvedLiveComps + resolvedPbeComps).filter { it.id !in existingCompIds }

            val prevTopLive = currentBundle.livePatches.firstOrNull()
            val incomingTopLive = tftipsResult?.livePatchRecords?.firstOrNull()
            val newLivePatchDetected = tftipsResult?.fetchedFromNetwork == true && (
                !liveVersion.equals(prevLiveVer, ignoreCase = true) ||
                    (incomingTopLive != null &&
                        incomingTopLive.id != prevTopLive?.id &&
                        currentBundle.livePatches.none {
                            it.id == incomingTopLive.id || it.version.equals(incomingTopLive.version, ignoreCase = true)
                        })
                )

            val prevTopPbe = currentBundle.pbePatches.firstOrNull()
            val incomingTopPbe = tftipsResult?.pbePatchRecords?.firstOrNull()
            val newPbePatchDetected = tftipsResult?.fetchedFromNetwork == true && (
                !pbeVersion.equals(prevPbeVer, ignoreCase = true) ||
                    (incomingTopPbe != null &&
                        incomingTopPbe.id != prevTopPbe?.id &&
                        currentBundle.pbePatches.none {
                            it.id == incomingTopPbe.id || it.version.equals(incomingTopPbe.version, ignoreCase = true)
                        })
                )

            val updatedVersionInfo = LiveVersionInfo(
                livePatchVersion = liveVersion,
                pbePatchVersion = pbeVersion,
                source = sourceLabel,
                checkedAtMillis = if (syncedFromNetwork) now else currentBundle.liveVersionInfo.checkedAtMillis,
                liveSetName = liveSetName,
                pbeSetName = pbeSetName
            )

            val updatedBundle = currentBundle.copy(
                cachedAtMillis = if (syncedFromNetwork) now else currentBundle.cachedAtMillis,
                liveVersionInfo = updatedVersionInfo,
                liveComps = resolvedLiveComps,
                pbeComps = resolvedPbeComps,
                liveChampions = resolvedLiveCatalog?.champions ?: currentBundle.liveChampions,
                liveItems = resolvedLiveCatalog?.items ?: currentBundle.liveItems,
                liveAugments = resolvedLiveCatalog?.augments ?: currentBundle.liveAugments,
                pbeChampions = resolvedPbeChampions,
                pbeItems = resolvedPbeItems,
                pbeAugments = resolvedPbeAugments,
                compsSignature = newSignature,
                livePatches = mergedLivePatches,
                pbePatches = mergedPbePatches,
                ddragonVersion = ddragonVersion,
                ddragonChampionJson = ddragonChampJson,
                ddragonItemJson = ddragonItemJson,
                ddragonAugmentJson = ddragonAugmentJson,
                ddragonTraitJson = ddragonTraitJson,
                pbeChampionIcons = resolvedPbeChampIcons,
                pbeTraitIcons = resolvedPbeTraitIcons,
                pbeItemIcons = resolvedPbeItemIcons,
                pbeChampionCosts = resolvedPbeChampCosts,
                pbeChampionTraits = resolvedPbeChampTraits
            )

            val dataChanged = compsUpdated ||
                newLivePatchDetected ||
                newPbePatchDetected ||
                mergedLivePatches != currentBundle.livePatches ||
                mergedPbePatches != currentBundle.pbePatches ||
                ddragonVersion != currentBundle.ddragonVersion ||
                resolvedLiveComps != currentBundle.liveComps ||
                resolvedPbeComps != currentBundle.pbeComps ||
                (resolvedLiveCatalog != null && (
                    resolvedLiveCatalog.champions != currentBundle.liveChampions ||
                        resolvedLiveCatalog.items != currentBundle.liveItems ||
                        resolvedLiveCatalog.augments != currentBundle.liveAugments
                    )) ||
                resolvedPbeChampions != currentBundle.pbeChampions ||
                resolvedPbeItems != currentBundle.pbeItems ||
                resolvedPbeAugments != currentBundle.pbeAugments ||
                !cacheStore.hasPersistedCache() ||
                (now - currentBundle.cachedAtMillis) >= 300_000L

            if (syncedFromNetwork && (dataChanged || forceReplaceComps)) {
                cacheStore.writeBundle(updatedBundle)
                MetaCacheUpdates.notifyUpdated()
            }

            MetaSyncResult(
                versionInfo = updatedVersionInfo,
                cacheStatus = updatedBundle.toCacheStatus(isOfflineFallback = !syncedFromNetwork),
                newLivePatchDetected = newLivePatchDetected,
                newPbePatchDetected = newPbePatchDetected,
                newlyAddedComps = newlyAddedComps,
                compsUpdatedDetected = compsUpdated,
                syncedFromNetwork = syncedFromNetwork
            )
        }
    }

    private companion object {
        val syncMutex = Mutex()
    }

    fun persistIncomingUpdate(
        patchRecord: PatchUpdateRecord? = null,
        newComp: MetaComp? = null
    ) {
        val current = cacheStore.readOrSeedBundle()
        var updatedLivePatches = current.livePatches
        var updatedPbePatches = current.pbePatches
        var updatedLiveComps = current.liveComps
        var updatedPbeComps = current.pbeComps
        var liveVer = current.liveVersionInfo.livePatchVersion
        var pbeVer = current.liveVersionInfo.pbePatchVersion

        if (patchRecord != null) {
            if (patchRecord.environment == TftEnvironment.LIVE) {
                updatedLivePatches = mergePatchLists(listOf(patchRecord), updatedLivePatches)
                liveVer = patchRecord.version
            } else {
                updatedPbePatches = mergePatchLists(listOf(patchRecord), updatedPbePatches)
                pbeVer = patchRecord.version
            }
        }
        if (newComp != null) {
            if (newComp.environment == TftEnvironment.LIVE) {
                updatedLiveComps = TftNinjaCompsScraper.sortCompsTierSDown((listOf(newComp) + updatedLiveComps).distinctBy { it.id })
            } else {
                updatedPbeComps = TftNinjaCompsScraper.sortCompsTierSDown((listOf(newComp) + updatedPbeComps).distinctBy { it.id })
            }
        }

        val now = System.currentTimeMillis()
        val updated = current.copy(
            cachedAtMillis = now,
            liveVersionInfo = current.liveVersionInfo.copy(
                livePatchVersion = liveVer,
                pbePatchVersion = pbeVer,
                source = "Patches: tftips.app/en/patches • LIVE: tft.ninja/comps • PBE: tft.ninja/comps?e=pbe",
                checkedAtMillis = now
            ),
            liveComps = updatedLiveComps,
            pbeComps = updatedPbeComps,
            compsSignature = TftNinjaCompsScraper.computeCompsSignature(updatedLiveComps, updatedPbeComps),
            livePatches = updatedLivePatches,
            pbePatches = updatedPbePatches
        )
        cacheStore.writeBundle(updated)
        MetaCacheUpdates.notifyUpdated()
    }

    private fun mergePatchLists(
        incoming: List<PatchUpdateRecord>,
        existing: List<PatchUpdateRecord>
    ): List<PatchUpdateRecord> {
        fun normVersionKey(rec: PatchUpdateRecord): String =
            "${rec.environment.name}_${rec.version.lowercase().replace(Regex("[^a-z0-9]"), "")}"

        val byVersionKey = LinkedHashMap<String, PatchUpdateRecord>()
        val seenIds = HashSet<String>()
        if (incoming.isEmpty()) {
            existing.forEach { rec ->
                val verKey = normVersionKey(rec)
                val idKey = rec.id.trim().lowercase()
                if (!byVersionKey.containsKey(verKey) && (idKey.isEmpty() || seenIds.add(idKey))) {
                    byVersionKey[verKey] = rec
                }
            }
            return byVersionKey.values.toList()
        }
        incoming.forEach { rec ->
            val verKey = normVersionKey(rec)
            val idKey = rec.id.trim().lowercase()
            if (idKey.isEmpty() || seenIds.add(idKey)) {
                byVersionKey[verKey] = rec
            }
        }
        existing
            .filter { it.id.startsWith("tftips_") || it.sourceUrl.contains("tftips.app", ignoreCase = true) }
            .forEach { old ->
                val verKey = normVersionKey(old)
                val idKey = old.id.trim().lowercase()
                if (!byVersionKey.containsKey(verKey) && (idKey.isEmpty() || seenIds.add(idKey))) {
                    byVersionKey[verKey] = old
                }
            }
        return byVersionKey.values.toList()
    }
}
