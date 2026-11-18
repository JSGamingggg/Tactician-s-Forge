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
import com.example.data.network.TftacticsCompsScraper
import com.example.data.network.TftipsPatchScraper
import com.example.ui.components.TftAssetIconResolver
import okhttp3.OkHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request

object MetaCacheUpdates {
    private val _lastCacheWriteMillis = MutableStateFlow(0L)
    val lastCacheWriteMillis: StateFlow<Long> = _lastCacheWriteMillis.asStateFlow()

    fun notifyUpdated() {
        _lastCacheWriteMillis.value = System.currentTimeMillis()
    }
}

class MetaRepository(
    private val cacheStore: OfflineMetaCacheStore,
    private val tftipsScraper: TftipsPatchScraper = TftipsPatchScraper(),
    private val tftAcademyScraper: TftAcademyCompsScraper = TftAcademyCompsScraper(),
    private val tftacticsScraper: TftacticsCompsScraper = TftacticsCompsScraper(),
    private val ddragonApi: DDragonApi = RiotApiClientFactory.ddragonApi,
    private val publicClient: OkHttpClient = RiotApiClientFactory.publicOkHttp
) {
    constructor(context: Context) : this(
        cacheStore = OfflineMetaCacheStore(context.applicationContext)
    )

    fun peekOfflineBundle(): OfflineMetaBundle = cacheStore.peekInMemoryOrSeed()

    suspend fun loadOfflineBundleAsync(): OfflineMetaBundle = cacheStore.readOrSeedBundleAsync()

    fun loadOfflineBundle(): OfflineMetaBundle = cacheStore.readOrSeedBundle()

    fun getComps(environment: TftEnvironment, bundle: OfflineMetaBundle = loadOfflineBundle()): List<MetaComp> =
        when (environment) {
            TftEnvironment.LIVE -> bundle.liveComps
            TftEnvironment.PBE -> bundle.pbeComps
        }

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
     * Fetches LIVE & PBE patch updates from https://tftips.app/en/patches and
     * ALL team compositions exclusively from https://tftacademy.com/tierlist/comps
     * (enforcing matching Set alignment and mirroring additions/edits/removals cleanly),
     * then persists updates offline.
     */
    suspend fun refreshAndPersistMeta(): MetaSyncResult = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val currentBundle = cacheStore.readOrSeedBundle(skipResolverSync = true)
            val prevLiveVer = currentBundle.liveVersionInfo.livePatchVersion
            val prevPbeVer = currentBundle.liveVersionInfo.pbePatchVersion
            val existingCompIds = (currentBundle.liveComps + currentBundle.pbeComps).map { it.id }.toSet()

            // Run independent network scrapes concurrently so startup & sync are fast and snappy
            val tftipsDeferred = async { runCatching { tftipsScraper.fetchPatchesFromTftips() }.getOrNull() }
            val ddragonDeferred = async {
                runCatching { ddragonApi.getVersions() }.getOrNull()
            }

            val tftipsResult = tftipsDeferred.await()
            var liveVersion = tftipsResult?.livePatchVersion ?: prevLiveVer
            var pbeVersion = tftipsResult?.pbePatchVersion ?: prevPbeVer
            var syncedFromNetwork = tftipsResult?.fetchedFromNetwork == true

            val tftAcademyDeferred = async {
                runCatching {
                    tftAcademyScraper.fetchLiveTeamComps(livePatchVersion = liveVersion)
                }.getOrNull()
            }
            val liveSetHint = Regex("""Set\s*(\d+)""", RegexOption.IGNORE_CASE)
                .find(currentBundle.liveVersionInfo.liveSetName)
                ?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: 18
            val tftAcademyCatalogDeferred = async {
                runCatching {
                    tftAcademyScraper.fetchLiveItemsAndAugmentsTierlist(setNumber = liveSetHint)
                }.getOrNull()
            }
            val tftacticsDeferred = async {
                runCatching {
                    tftacticsScraper.fetchPbeTeamComps(
                        pbePatchVersion = pbeVersion,
                        liveSetNumber = liveSetHint
                    )
                }.getOrNull()
            }

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
                        if (versionChanged || ddragonChampJson.isNullOrBlank()) {
                            ddragonChampJson = runCatching {
                                ddragonApi.getTftChampionsJson(latestDdragon).body()?.string()
                            }.getOrNull() ?: ddragonChampJson
                        }
                        if (versionChanged || ddragonItemJson.isNullOrBlank()) {
                            ddragonItemJson = runCatching {
                                ddragonApi.getTftItemsJson(latestDdragon).body()?.string()
                            }.getOrNull() ?: ddragonItemJson
                        }
                        if (versionChanged || ddragonAugmentJson.isNullOrBlank()) {
                            ddragonAugmentJson = runCatching {
                                ddragonApi.getTftAugmentsJson(latestDdragon).body()?.string()
                            }.getOrNull() ?: ddragonAugmentJson
                        }
                        if (versionChanged || ddragonTraitJson.isNullOrBlank()) {
                            ddragonTraitJson = runCatching {
                                ddragonApi.getTftTraitsJson(latestDdragon).body()?.string()
                            }.getOrNull() ?: ddragonTraitJson
                        }
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
                        if (liveVersion == prevLiveVer && tftipsResult?.fetchedFromNetwork != true) {
                            val shortTag = latestDdragon.split(".").take(2).joinToString(".")
                            liveVersion = "18.7 (DD $shortTag)"
                        }
                    }
                }
            } catch (_: Exception) {
                // Keep offline cache silently
            }

            if (pbeVersion == prevPbeVer && tftipsResult?.pbePatchVersion == null) {
                try {
                    val req = Request.Builder()
                        .url("https://raw.communitydragon.org/pbe/content-metadata.json")
                        .head()
                        .build()
                    publicClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            syncedFromNetwork = true
                            val etag = resp.header("etag")?.replace("\"", "")?.take(6)
                            if (!etag.isNullOrBlank()) {
                                pbeVersion = "19.1-PBE ($etag)"
                            }
                        }
                    }
                } catch (_: Exception) {
                    // Keep cached PBE tag silently
                }
            }

            val tftAcademyResult = tftAcademyDeferred.await()
            val tftAcademyCatalog = tftAcademyCatalogDeferred.await()
            val tftacticsResult = tftacticsDeferred.await()
            if (tftAcademyResult?.fetchedFromNetwork == true ||
                tftAcademyCatalog?.fetchedFromNetwork == true ||
                tftacticsResult?.fetchedFromNetwork == true
            ) {
                syncedFromNetwork = true
            }
            if (tftacticsResult != null &&
                (tftacticsResult.pbeChampionIcons.isNotEmpty() ||
                    tftacticsResult.pbeTraitIcons.isNotEmpty() ||
                    tftacticsResult.pbeItemIcons.isNotEmpty())
            ) {
                withContext(Dispatchers.Main.immediate) {
                    TftAssetIconResolver.updatePbeSetCatalog(
                        championIcons = tftacticsResult.pbeChampionIcons,
                        championCosts = tftacticsResult.pbeChampionCosts,
                        championTraits = tftacticsResult.pbeChampionTraits,
                        traitIcons = tftacticsResult.pbeTraitIcons,
                        itemIcons = tftacticsResult.pbeItemIcons
                    )
                }
            }

            val liveSetName = tftAcademyResult?.detectedLiveSet
                ?: currentBundle.liveVersionInfo.liveSetName
            val pbeSetName = tftacticsResult?.detectedPbeSet
                ?: currentBundle.liveVersionInfo.pbeSetName

            val sourceLabel = if (syncedFromNetwork) {
                "Patches: tftips.app/en/patches • LIVE: tftacademy.com • PBE: tftactics.gg"
            } else {
                "Offline Cache (tftips.app, tftacademy.com & tftactics.gg)"
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

            val incomingLiveComps = tftAcademyResult?.liveComps.orEmpty()
            val incomingPbeComps = tftacticsResult?.pbeComps.orEmpty()

            // Mirror TFTAcademy (LIVE) and TFTactics (PBE) authoritatively whenever network comps arrive
            val resolvedLiveComps = if (tftAcademyResult?.fetchedFromNetwork == true && incomingLiveComps.isNotEmpty()) {
                incomingLiveComps
            } else {
                currentBundle.liveComps
            }
            val resolvedPbeComps = if (tftacticsResult?.fetchedFromNetwork == true && incomingPbeComps.isNotEmpty()) {
                incomingPbeComps
            } else {
                currentBundle.pbeComps
            }
            val resolvedLiveCatalog = if (syncedFromNetwork) {
                MetaCatalogSyncHelper.refreshLiveCatalog(
                    existingChampions = currentBundle.liveChampions,
                    existingItems = currentBundle.liveItems,
                    existingAugments = currentBundle.liveAugments,
                    liveComps = resolvedLiveComps,
                    liveSetNumber = liveSetHint,
                    ddragonChampJson = ddragonChampJson,
                    ddragonItemJson = ddragonItemJson,
                    ddragonAugmentJson = ddragonAugmentJson,
                    academyTiers = tftAcademyCatalog
                )
            } else {
                null
            }
            val resolvedPbeChampions = if (tftacticsResult?.fetchedFromNetwork == true &&
                tftacticsResult.pbeChampionTiers.isNotEmpty()
            ) {
                tftacticsResult.pbeChampionTiers
            } else {
                currentBundle.pbeChampions
            }
            val resolvedPbeItems = if (tftacticsResult?.fetchedFromNetwork == true &&
                tftacticsResult.pbeItemTiers.isNotEmpty()
            ) {
                tftacticsResult.pbeItemTiers
            } else {
                currentBundle.pbeItems
            }
            val resolvedPbeAugments = if (tftacticsResult?.fetchedFromNetwork == true &&
                tftacticsResult.pbeAugmentTiers.isNotEmpty()
            ) {
                tftacticsResult.pbeAugmentTiers
            } else {
                currentBundle.pbeAugments
            }
            val resolvedPbeChampIcons = tftacticsResult?.pbeChampionIcons?.takeIf { it.isNotEmpty() }
                ?: currentBundle.pbeChampionIcons
            val resolvedPbeTraitIcons = tftacticsResult?.pbeTraitIcons?.takeIf { it.isNotEmpty() }
                ?: currentBundle.pbeTraitIcons
            val resolvedPbeItemIcons = tftacticsResult?.pbeItemIcons?.takeIf { it.isNotEmpty() }
                ?: currentBundle.pbeItemIcons
            val resolvedPbeChampCosts = tftacticsResult?.pbeChampionCosts?.takeIf { it.isNotEmpty() }
                ?: currentBundle.pbeChampionCosts
            val resolvedPbeChampTraits = tftacticsResult?.pbeChampionTraits?.takeIf { it.isNotEmpty() }
                ?: currentBundle.pbeChampionTraits

            val newSignature = TftAcademyCompsScraper.computeCompsSignature(resolvedLiveComps, resolvedPbeComps)
            val compsUpdated = newSignature != currentBundle.compsSignature &&
                (resolvedLiveComps.isNotEmpty() || resolvedPbeComps.isNotEmpty())
            val newlyAddedComps = (resolvedLiveComps + resolvedPbeComps).filter { it.id !in existingCompIds }
            val newLivePatchDetected = liveVersion != prevLiveVer ||
                (tftipsResult?.livePatchRecords?.any { inc -> currentBundle.livePatches.none { it.id == inc.id } } == true)
            val newPbePatchDetected = pbeVersion != prevPbeVer ||
                (tftipsResult?.pbePatchRecords?.any { inc -> currentBundle.pbePatches.none { it.id == inc.id } } == true)

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
                    resolvedLiveCatalog.champions.size != currentBundle.liveChampions.size ||
                        resolvedLiveCatalog.items.size != currentBundle.liveItems.size ||
                        resolvedLiveCatalog.augments.size != currentBundle.liveAugments.size
                    )) ||
                resolvedPbeChampions != currentBundle.pbeChampions ||
                resolvedPbeItems != currentBundle.pbeItems ||
                resolvedPbeAugments != currentBundle.pbeAugments ||
                !cacheStore.hasPersistedCache() ||
                (now - currentBundle.cachedAtMillis) >= 300_000L

            if (syncedFromNetwork && dataChanged) {
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
                updatedLiveComps = (listOf(newComp) + updatedLiveComps).distinctBy { it.id }
            } else {
                updatedPbeComps = (listOf(newComp) + updatedPbeComps).distinctBy { it.id }
            }
        }

        val now = System.currentTimeMillis()
        val updated = current.copy(
            cachedAtMillis = now,
            liveVersionInfo = current.liveVersionInfo.copy(
                livePatchVersion = liveVer,
                pbePatchVersion = pbeVer,
                source = "Patches: tftips.app/en/patches • LIVE: tftacademy.com • PBE: tftactics.gg",
                checkedAtMillis = now
            ),
            liveComps = updatedLiveComps,
            pbeComps = updatedPbeComps,
            compsSignature = TftAcademyCompsScraper.computeCompsSignature(updatedLiveComps, updatedPbeComps),
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
        if (incoming.isEmpty()) return existing
        val byVersionKey = LinkedHashMap<String, PatchUpdateRecord>()
        incoming.forEach { rec ->
            val key = "${rec.environment.name}_${rec.version.uppercase()}"
            byVersionKey[key] = rec
        }
        existing
            .filter { it.id.startsWith("tftips_") || it.sourceUrl.contains("tftips.app", ignoreCase = true) }
            .forEach { old ->
                val key = "${old.environment.name}_${old.version.uppercase()}"
                if (!byVersionKey.containsKey(key)) {
                    byVersionKey[key] = old
                }
            }
        return byVersionKey.values.toList()
    }
}
