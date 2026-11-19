package com.example.notifications

import android.content.Context
import com.example.data.model.MetaComp
import com.example.data.model.MetaSyncResult
import com.example.data.model.TftEnvironment
import com.example.data.network.TftAcademyCompsScraper
import com.example.data.repository.LocalPreferencesStore
import com.example.data.repository.OfflineMetaBundle
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class MetaNotificationDispatchResult(
    val livePatchNotified: Boolean,
    val pbePatchNotified: Boolean,
    val newCompsDetectedCount: Int,
    val newCompsNotified: Boolean = false
)

object MetaUpdateNotifier {
    private val dispatchMutex = Mutex()

    /**
     * Records the current bundle versions and compositions as already seen (e.g. when the user
     * actively opens the UI) so subsequent background syncs will not send a notification unless a
     * newer update actually occurs later.
     */
    suspend fun markCurrentSnapshotAsSeen(
        bundle: OfflineMetaBundle,
        prefsStore: LocalPreferencesStore
    ) = dispatchMutex.withLock {
        val allCompIds = (bundle.liveComps + bundle.pbeComps).map { it.id }.toSet()
        val currentSig = TftAcademyCompsScraper.computeCompsSignature(bundle.liveComps, bundle.pbeComps)
        prefsStore.setLastNotifiedVersions(
            liveVer = bundle.liveVersionInfo.livePatchVersion,
            pbeVer = bundle.liveVersionInfo.pbePatchVersion
        )
        prefsStore.saveNotifiedCompIds(allCompIds)
        prefsStore.setLastNotifiedCompsSignature(currentSig)
    }

    /**
     * Evaluates newly synced LIVE/PBE patch versions and newly added/updated compositions strictly
     * against persisted notification watermarks. Initial baseline runs seed silently; subsequent
     * runs post OS notifications ONLY when an actual update occurred since the last notified state.
     */
    suspend fun dispatchNotifications(
        context: Context,
        syncResult: MetaSyncResult,
        bundle: OfflineMetaBundle,
        prefsStore: LocalPreferencesStore = LocalPreferencesStore(context.applicationContext),
        seedBaselineOnFirstRun: Boolean = true
    ): MetaNotificationDispatchResult = dispatchMutex.withLock {
        val prefs = prefsStore.getNotificationSnapshot()
        val versionInfo = syncResult.versionInfo
        val (lastNotifiedLive, lastNotifiedPbe) = prefsStore.getLastNotifiedVersions()
        val existingKnownCompIds = prefsStore.getNotifiedCompIds()
        val lastNotifiedCompsSig = prefsStore.getLastNotifiedCompsSignature()

        var newLastLive = lastNotifiedLive
        var newLastPbe = lastNotifiedPbe
        var livePatchNotified = false
        var pbePatchNotified = false

        val hasLiveUpdate = lastNotifiedLive != null &&
            !versionInfo.livePatchVersion.equals(lastNotifiedLive, ignoreCase = true)

        if (lastNotifiedLive == null && seedBaselineOnFirstRun) {
            newLastLive = versionInfo.livePatchVersion
        } else if (hasLiveUpdate) {
            if (prefs.patchAlertsEnabled) {
                val latestLivePatch = bundle.livePatches.firstOrNull()
                val cleanHeadline = latestLivePatch?.headline?.takeIf { it.isNotBlank() }
                    ?: "New LIVE patch ${versionInfo.livePatchVersion} balance updates are now available."
                val highlightsText = latestLivePatch?.highlights?.joinToString("\n• ", prefix = "• ").orEmpty()
                val posted = TftNotificationHelper.postPatchOrBalanceNotification(
                    context = context,
                    title = "TFT LIVE Patch ${versionInfo.livePatchVersion} Released",
                    shortBody = cleanHeadline,
                    expandedBody = buildString {
                        append(cleanHeadline)
                        if (highlightsText.isNotBlank()) {
                            append("\n")
                            append(highlightsText)
                        }
                    },
                    environment = TftEnvironment.LIVE,
                    notificationId = 10_001
                )
                if (posted) {
                    newLastLive = versionInfo.livePatchVersion
                    livePatchNotified = true
                }
            } else {
                newLastLive = versionInfo.livePatchVersion
            }
        }

        val hasPbeUpdate = lastNotifiedPbe != null &&
            !versionInfo.pbePatchVersion.equals(lastNotifiedPbe, ignoreCase = true)

        if (lastNotifiedPbe == null && seedBaselineOnFirstRun) {
            newLastPbe = versionInfo.pbePatchVersion
        } else if (hasPbeUpdate) {
            if (prefs.pbeAlertsEnabled) {
                val latestPbePatch = bundle.pbePatches.firstOrNull()
                val cleanHeadline = latestPbePatch?.headline?.takeIf { it.isNotBlank() }
                    ?: "New PBE patch ${versionInfo.pbePatchVersion} balance updates are now available."
                val highlightsText = latestPbePatch?.highlights?.joinToString("\n• ", prefix = "• ").orEmpty()
                val posted = TftNotificationHelper.postPatchOrBalanceNotification(
                    context = context,
                    title = "TFT PBE Patch ${versionInfo.pbePatchVersion} Released",
                    shortBody = cleanHeadline,
                    expandedBody = buildString {
                        append(cleanHeadline)
                        if (highlightsText.isNotBlank()) {
                            append("\n")
                            append(highlightsText)
                        }
                    },
                    environment = TftEnvironment.PBE,
                    notificationId = 10_002
                )
                if (posted) {
                    newLastPbe = versionInfo.pbePatchVersion
                    pbePatchNotified = true
                }
            } else {
                newLastPbe = versionInfo.pbePatchVersion
            }
        }

        val allComps = (bundle.liveComps + bundle.pbeComps).distinctBy { it.id }
        val allCompIds = allComps.map { it.id }.toSet()
        val currentCompsSig = TftAcademyCompsScraper.computeCompsSignature(bundle.liveComps, bundle.pbeComps)

        val isInitialCompSeed = existingKnownCompIds == null && seedBaselineOnFirstRun
        val newlyAddedPool: List<MetaComp> = if (isInitialCompSeed) {
            // Always seed initial baseline silently without firing notifications
            emptyList()
        } else {
            val known = existingKnownCompIds.orEmpty()
            val diffByStore = allComps.filter { it.id !in known }
            (diffByStore + syncResult.newlyAddedComps.filter { it.id !in known }).distinctBy { it.id }
        }

        var compsNotified = false
        var deferredUnnotifiedCompIds: Set<String> = emptySet()
        var nextCompsSig = lastNotifiedCompsSig

        if (lastNotifiedCompsSig == null && seedBaselineOnFirstRun) {
            nextCompsSig = currentCompsSig
        }

        if (!isInitialCompSeed && newlyAddedPool.isNotEmpty()) {
            if (prefs.trendingAlertsEnabled) {
                val prioritized = newlyAddedPool.sortedWith(
                    compareBy<MetaComp> { it.tier.sortOrder }.thenBy { it.avgPlacement }
                )
                compsNotified = if (prioritized.size == 1) {
                    val comp = prioritized.first()
                    TftNotificationHelper.postTrendingCompNotification(
                        context = context,
                        compTitle = comp.title,
                        environment = comp.environment,
                        summary = "${comp.tier.label}-Tier • ${comp.setName} • Carry: ${comp.carryName} (${comp.top4Rate}% Top 4)"
                    )
                } else {
                    val lead = prioritized.first()
                    val summaryLines = prioritized.take(4).joinToString("\n") { comp ->
                        "• [${comp.environment.label} ${comp.tier.label}] ${comp.title} (Carry: ${comp.carryName})"
                    }
                    TftNotificationHelper.postNewCompsBatchNotification(
                        context = context,
                        title = "${prioritized.size} New TFT Compositions Released",
                        shortBody = "${lead.title} (${lead.tier.label}-Tier) & ${prioritized.size - 1} more compositions added",
                        expandedBody = summaryLines,
                        environment = lead.environment
                    )
                }
                if (compsNotified) {
                    nextCompsSig = currentCompsSig
                } else {
                    // Keep newly detected comps unmarked until OS notification permission is granted
                    deferredUnnotifiedCompIds = newlyAddedPool.map { it.id }.toSet()
                }
            } else {
                nextCompsSig = currentCompsSig
            }
        } else if (!isInitialCompSeed &&
            lastNotifiedCompsSig != null &&
            currentCompsSig.isNotBlank() &&
            currentCompsSig != lastNotifiedCompsSig &&
            syncResult.compsUpdatedDetected
        ) {
            if (prefs.trendingAlertsEnabled || prefs.balanceAlertsEnabled) {
                val topSComps = allComps
                    .sortedWith(compareBy<MetaComp> { it.tier.sortOrder }.thenBy { it.avgPlacement })
                    .take(3)
                if (topSComps.isNotEmpty()) {
                    val lead = topSComps.first()
                    val summaryLines = topSComps.joinToString("\n") { comp ->
                        "• [${comp.environment.label} ${comp.tier.label}] ${comp.title} (Carry: ${comp.carryName})"
                    }
                    compsNotified = TftNotificationHelper.postNewCompsBatchNotification(
                        context = context,
                        title = "TFT Meta Compositions Updated",
                        shortBody = "Tier list & builds updated — Lead: ${lead.title} (${lead.tier.label}-Tier)",
                        expandedBody = summaryLines,
                        environment = lead.environment
                    )
                    if (compsNotified) {
                        nextCompsSig = currentCompsSig
                    }
                } else {
                    nextCompsSig = currentCompsSig
                }
            } else {
                nextCompsSig = currentCompsSig
            }
        }

        prefsStore.setLastNotifiedVersions(newLastLive, newLastPbe)
        val updatedKnownIds = (existingKnownCompIds.orEmpty() + allCompIds) - deferredUnnotifiedCompIds
        prefsStore.saveNotifiedCompIds(updatedKnownIds)
        nextCompsSig?.let { prefsStore.setLastNotifiedCompsSignature(it) }

        MetaNotificationDispatchResult(
            livePatchNotified = livePatchNotified,
            pbePatchNotified = pbePatchNotified,
            newCompsDetectedCount = newlyAddedPool.size,
            newCompsNotified = compsNotified
        )
    }
}
