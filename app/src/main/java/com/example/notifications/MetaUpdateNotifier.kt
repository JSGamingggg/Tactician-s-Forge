package com.example.notifications

import android.content.Context
import com.example.data.model.MetaComp
import com.example.data.model.MetaSyncResult
import com.example.data.model.TftEnvironment
import com.example.data.repository.LocalPreferencesStore
import com.example.data.repository.OfflineMetaBundle
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class MetaNotificationDispatchResult(
    val livePatchNotified: Boolean,
    val pbePatchNotified: Boolean,
    val newCompsDetectedCount: Int
)

object MetaUpdateNotifier {
    private val dispatchMutex = Mutex()

    /**
     * Evaluates newly synced LIVE/PBE patch versions and newly added compositions against
     * persisted notification watermarks, posting OS push alerts without duplicate spam.
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

        var newLastLive = lastNotifiedLive
        var newLastPbe = lastNotifiedPbe
        var livePatchNotified = false
        var pbePatchNotified = false

        val shouldNotifyLive = (lastNotifiedLive != null && versionInfo.livePatchVersion != lastNotifiedLive) ||
            syncResult.newLivePatchDetected

        if (prefs.patchAlertsEnabled && shouldNotifyLive) {
            val latestLivePatch = bundle.livePatches.firstOrNull()
            val highlightsText = latestLivePatch?.highlights?.joinToString("\n• ", prefix = "• ").orEmpty()
            TftNotificationHelper.postPatchOrBalanceNotification(
                context = context,
                title = "TFT LIVE Patch ${versionInfo.livePatchVersion} Update",
                shortBody = latestLivePatch?.headline ?: "New LIVE patch synced from tftips.app/en/patches.",
                expandedBody = buildString {
                    append(latestLivePatch?.headline ?: "New LIVE patch detected on tftips.app/en/patches.")
                    if (highlightsText.isNotBlank()) {
                        append("\n")
                        append(highlightsText)
                    }
                },
                environment = TftEnvironment.LIVE,
                notificationId = 10_001
            )
            newLastLive = versionInfo.livePatchVersion
            livePatchNotified = true
        } else if (newLastLive == null) {
            newLastLive = versionInfo.livePatchVersion
        }

        val shouldNotifyPbe = (lastNotifiedPbe != null && versionInfo.pbePatchVersion != lastNotifiedPbe) ||
            syncResult.newPbePatchDetected

        if (prefs.pbeAlertsEnabled && shouldNotifyPbe) {
            val latestPbePatch = bundle.pbePatches.firstOrNull()
            val highlightsText = latestPbePatch?.highlights?.joinToString("\n• ", prefix = "• ").orEmpty()
            TftNotificationHelper.postPatchOrBalanceNotification(
                context = context,
                title = "TFT PBE Patch ${versionInfo.pbePatchVersion} Update",
                shortBody = latestPbePatch?.headline ?: "New PBE update synced from tftips.app/en/patches.",
                expandedBody = buildString {
                    append(latestPbePatch?.headline ?: "New PBE build detected on tftips.app/en/patches.")
                    if (highlightsText.isNotBlank()) {
                        append("\n")
                        append(highlightsText)
                    }
                },
                environment = TftEnvironment.PBE,
                notificationId = 10_002
            )
            newLastPbe = versionInfo.pbePatchVersion
            pbePatchNotified = true
        } else if (newLastPbe == null) {
            newLastPbe = versionInfo.pbePatchVersion
        }

        val allComps = (bundle.liveComps + bundle.pbeComps).distinctBy { it.id }
        val allCompIds = allComps.map { it.id }.toSet()

        val newlyAddedPool: List<MetaComp> = when {
            existingKnownCompIds == null && seedBaselineOnFirstRun -> {
                // First run seeds current catalog silently unless explicit newlyAddedComps arrived this cycle
                syncResult.newlyAddedComps
            }
            else -> {
                val known = existingKnownCompIds.orEmpty()
                val diffByStore = allComps.filter { it.id !in known }
                (diffByStore + syncResult.newlyAddedComps.filter { it.id !in known }).distinctBy { it.id }
            }
        }

        if (prefs.trendingAlertsEnabled && newlyAddedPool.isNotEmpty()) {
            val prioritized = newlyAddedPool.sortedWith(
                compareBy<MetaComp> { it.tier.sortOrder }.thenBy { it.avgPlacement }
            )
            if (prioritized.size == 1) {
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
                    title = "${prioritized.size} New TFT Comps Synced",
                    shortBody = "${lead.title} (${lead.tier.label}-Tier) & ${prioritized.size - 1} more from tftactics.gg",
                    expandedBody = summaryLines,
                    environment = lead.environment
                )
            }
        }

        prefsStore.setLastNotifiedVersions(newLastLive, newLastPbe)
        prefsStore.saveNotifiedCompIds(existingKnownCompIds.orEmpty() + allCompIds)

        MetaNotificationDispatchResult(
            livePatchNotified = livePatchNotified,
            pbePatchNotified = pbePatchNotified,
            newCompsDetectedCount = newlyAddedPool.size
        )
    }
}
