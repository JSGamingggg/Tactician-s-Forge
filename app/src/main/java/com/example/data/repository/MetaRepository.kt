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
import com.example.data.network.RiotApiClientFactory
import com.example.data.network.TftacticsCompsScraper
import com.example.data.network.TftipsPatchScraper
import com.example.ui.components.TftAssetIconResolver
import kotlinx.coroutines.Dispatchers
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
    private val tftacticsScraper: TftacticsCompsScraper = TftacticsCompsScraper()
) {
    constructor(context: Context) : this(
        cacheStore = OfflineMetaCacheStore(context.applicationContext)
    )

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
     * LIVE & PBE team compositions strictly from https://tftactics.gg/tierlist/team-comps/
     * (enforcing matching Set alignment for LIVE vs PBE), then persists updates offline.
     */
    suspend fun refreshAndPersistMeta(): MetaSyncResult = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val currentBundle = cacheStore.readOrSeedBundle()
            val prevLiveVer = currentBundle.liveVersionInfo.livePatchVersion
            val prevPbeVer = currentBundle.liveVersionInfo.pbePatchVersion
            val existingCompIds = (currentBundle.liveComps + currentBundle.pbeComps).map { it.id }.toSet()

        val tftipsResult = runCatching { tftipsScraper.fetchPatchesFromTftips() }.getOrNull()

        var liveVersion = tftipsResult?.livePatchVersion ?: prevLiveVer
        var pbeVersion = tftipsResult?.pbePatchVersion ?: prevPbeVer
        var syncedFromNetwork = tftipsResult?.fetchedFromNetwork == true

        var ddragonVersion = currentBundle.ddragonVersion
        var ddragonChampJson = currentBundle.ddragonChampionJson
        var ddragonItemJson = currentBundle.ddragonItemJson
        var ddragonAugmentJson = currentBundle.ddragonAugmentJson

        // Always keep Data Dragon asset version + TFT champion/item/augment icon maps up to date
        try {
            val ddragonResp = RiotApiClientFactory.ddragonApi.getVersions()
            if (ddragonResp.isSuccessful) {
                val latestDdragon = ddragonResp.body()?.firstOrNull()
                if (!latestDdragon.isNullOrBlank()) {
                    syncedFromNetwork = true
                    val versionChanged = latestDdragon != ddragonVersion
                    ddragonVersion = latestDdragon
                    if (versionChanged || ddragonChampJson.isNullOrBlank()) {
                        ddragonChampJson = runCatching {
                            RiotApiClientFactory.ddragonApi.getTftChampionsJson(latestDdragon).body()?.string()
                        }.getOrNull() ?: ddragonChampJson
                    }
                    if (versionChanged || ddragonItemJson.isNullOrBlank()) {
                        ddragonItemJson = runCatching {
                            RiotApiClientFactory.ddragonApi.getTftItemsJson(latestDdragon).body()?.string()
                        }.getOrNull() ?: ddragonItemJson
                    }
                    if (versionChanged || ddragonAugmentJson.isNullOrBlank()) {
                        ddragonAugmentJson = runCatching {
                            RiotApiClientFactory.ddragonApi.getTftAugmentsJson(latestDdragon).body()?.string()
                        }.getOrNull() ?: ddragonAugmentJson
                    }
                    TftAssetIconResolver.updateDdragonCatalog(
                        version = ddragonVersion,
                        championJson = ddragonChampJson,
                        itemJson = ddragonItemJson,
                        augmentJson = ddragonAugmentJson
                    )
                    if (liveVersion == prevLiveVer && tftipsResult?.fetchedFromNetwork != true) {
                        val shortTag = latestDdragon.split(".").take(2).joinToString(".")
                        liveVersion = "18.6b (DD $shortTag)"
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
                RiotApiClientFactory.publicOkHttp.newCall(req).execute().use { resp ->
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

        // Always fetch LIVE & PBE team compositions from https://tftactics.gg/tierlist/team-comps/
        val tftacticsResult = runCatching {
            tftacticsScraper.fetchTeamComps(
                livePatchVersion = liveVersion,
                pbePatchVersion = pbeVersion
            )
        }.getOrNull()

        if (tftacticsResult?.fetchedFromNetwork == true) {
            syncedFromNetwork = true
        }

        val liveSetName = tftacticsResult?.detectedLiveSet
            ?: currentBundle.liveVersionInfo.liveSetName
        val pbeSetName = tftacticsResult?.detectedPbeSet
            ?: currentBundle.liveVersionInfo.pbeSetName

        val sourceLabel = if (syncedFromNetwork) {
            "Patches: tftips.app/en/patches • Comps: tftactics.gg"
        } else {
            "Offline Cache (tftips.app & tftactics.gg)"
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

        val incomingLiveComps = tftacticsResult?.liveComps.orEmpty()
        val incomingPbeComps = tftacticsResult?.pbeComps.orEmpty()

        val mergedLiveComps = mergeCompLists(
            incoming = incomingLiveComps,
            existing = currentBundle.liveComps,
            latestPatchTag = liveVersion,
            expectedSetName = liveSetName
        )
        val mergedPbeComps = mergeCompLists(
            incoming = incomingPbeComps,
            existing = currentBundle.pbeComps,
            latestPatchTag = pbeVersion,
            expectedSetName = pbeSetName
        )

        val newlyAddedComps = (mergedLiveComps + mergedPbeComps).filter { it.id !in existingCompIds }
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
            liveComps = mergedLiveComps,
            pbeComps = mergedPbeComps,
            livePatches = mergedLivePatches,
            pbePatches = mergedPbePatches,
            ddragonVersion = ddragonVersion,
            ddragonChampionJson = ddragonChampJson,
            ddragonItemJson = ddragonItemJson,
            ddragonAugmentJson = ddragonAugmentJson
        )

        if (syncedFromNetwork) {
            cacheStore.writeBundle(updatedBundle)
            MetaCacheUpdates.notifyUpdated()
        }

        MetaSyncResult(
            versionInfo = updatedVersionInfo,
            cacheStatus = updatedBundle.toCacheStatus(isOfflineFallback = !syncedFromNetwork),
            newLivePatchDetected = newLivePatchDetected,
            newPbePatchDetected = newPbePatchDetected,
            newlyAddedComps = newlyAddedComps,
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
                source = "Patches: tftips.app/en/patches • Comps: tftactics.gg",
                checkedAtMillis = now
            ),
            liveComps = updatedLiveComps,
            pbeComps = updatedPbeComps,
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
        val byId = LinkedHashMap<String, PatchUpdateRecord>()
        incoming.forEach { byId[it.id] = it }
        existing.forEach { old -> if (!byId.containsKey(old.id)) byId[old.id] = old }
        return byId.values.toList()
    }

    private fun mergeCompLists(
        incoming: List<MetaComp>,
        existing: List<MetaComp>,
        latestPatchTag: String,
        expectedSetName: String
    ): List<MetaComp> {
        val expectedSetNum = Regex("""Set\s*(\d+)""", RegexOption.IGNORE_CASE)
            .find(expectedSetName)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val byId = LinkedHashMap<String, MetaComp>()
        incoming.forEach { byId[it.id] = it }
        existing.forEach { comp ->
            if (!byId.containsKey(comp.id)) {
                val compSetNum = Regex("""Set\s*(\d+)""", RegexOption.IGNORE_CASE)
                    .find(comp.setName)?.groupValues?.getOrNull(1)?.toIntOrNull()
                // Only keep fallback comps if they match the active set (or no set conflict)
                if (expectedSetNum == null || compSetNum == null || compSetNum == expectedSetNum || incoming.isEmpty()) {
                    byId[comp.id] = if (incoming.isNotEmpty()) {
                        comp.copy(patchTag = latestPatchTag, setName = expectedSetName)
                    } else {
                        comp
                    }
                }
            }
        }
        return byId.values.toList()
    }
}
