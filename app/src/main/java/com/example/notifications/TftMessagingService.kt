package com.example.notifications

import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.model.BalanceChangeEntry
import com.example.data.model.DeltaType
import com.example.data.model.HexUnitPlacement
import com.example.data.model.MetaComp
import com.example.data.model.PatchUpdateRecord
import com.example.data.model.TftEnvironment
import com.example.data.model.TierRank
import com.example.data.repository.MetaRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class TftMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM registration token refreshed")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        val env = TftEnvironment.fromRaw(data["environment"])
        val type = data["type"] ?: "patch_update"
        val title = data["title"] ?: message.notification?.title ?: "TFT Meta & Patch Update"
        val body = data["body"] ?: message.notification?.body ?: "Tap to view the latest balance and composition updates."
        val version = data["version"] ?: if (env == TftEnvironment.PBE) "19.1-PBE" else "18.7"

        val metaRepo = MetaRepository(applicationContext)

        when (type) {
            "app_update" -> {
                PatchMonitorWorker.schedulePeriodicAndChainedBackgroundChecks(
                    context = applicationContext,
                    triggerImmediateCheck = true
                )
            }
            "trending_comp", "new_comp" -> {
                val carry = data["carry"] ?: "Flex Carry"
                val units = data["units"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
                if (units.isNotEmpty()) {
                    val newComp = MetaComp(
                        id = data["comp_id"] ?: "push_${env.name.lowercase()}_${System.currentTimeMillis()}",
                        environment = env,
                        title = title,
                        tier = TierRank.fromRaw(data["tier"] ?: "S"),
                        playstyle = data["playstyle"] ?: "Fast 8",
                        avgPlacement = 3.65,
                        top4Rate = 61.5,
                        winRate = 17.0,
                        pickRate = 8.0,
                        trendDelta = "New Release",
                        isTrending = true,
                        patchTag = version,
                        carryName = carry,
                        traits = emptyList(),
                        coreUnits = units,
                        hexPlacements = units.mapIndexed { idx, name ->
                            HexUnitPlacement(
                                unitName = name,
                                row = if (idx < units.size / 2) 0 else 3,
                                col = (idx * 2) % 7,
                                cost = 4,
                                isCarry = name.equals(carry, ignoreCase = true)
                            )
                        },
                        augmentPriorities = emptyList(),
                        levelingGuide = body,
                        notes = "Added via live update for patch $version."
                    )
                    metaRepo.persistIncomingUpdate(newComp = newComp)
                }

                TftNotificationHelper.postTrendingCompNotification(
                    context = applicationContext,
                    compTitle = title,
                    environment = env,
                    summary = body
                )
            }
            else -> {
                val patchRecord = PatchUpdateRecord(
                    id = "push_${env.name.lowercase()}_${version.replace(Regex("[^a-zA-Z0-9]"), "_")}",
                    environment = env,
                    version = version,
                    title = title,
                    releaseDate = "Just Released",
                    isMajorPatch = true,
                    headline = body,
                    highlights = listOf(data["expanded_body"] ?: body),
                    balanceChanges = listOf(
                        BalanceChangeEntry(
                            id = "push_bc_${System.currentTimeMillis()}",
                            targetName = title,
                            category = "Patch Update",
                            deltaType = DeltaType.NEW,
                            summary = body,
                            previousValue = "Prev",
                            newValue = version
                        )
                    ),
                    trendingCompIds = emptyList(),
                    sourceUrl = data["source_url"] ?: "https://tftips.app/en/patches"
                )
                metaRepo.persistIncomingUpdate(patchRecord = patchRecord)

                TftNotificationHelper.postPatchOrBalanceNotification(
                    context = applicationContext,
                    title = title,
                    shortBody = body,
                    expandedBody = data["expanded_body"] ?: body,
                    environment = env
                )
            }
        }

        runCatching {
            PatchMonitorWorker.schedulePeriodicAndChainedBackgroundChecks(
                context = applicationContext,
                triggerImmediateCheck = true
            )
        }
    }

    companion object {
        private const val TAG = "TftMessagingService"
    }
}
