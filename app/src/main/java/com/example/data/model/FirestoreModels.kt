package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class FavoriteCompDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val compId: String = "",
    val environment: String = "LIVE",
    val title: String = "",
    val tier: String = "S",
    val playstyle: String = "",
    val carryName: String = "",
    val coreUnits: List<String> = emptyList(),
    val hexPositions: List<String> = emptyList(),
    val pinnedToOverlay: Boolean = false,
    val notes: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> {
        val base = mutableMapOf<String, Any>(
            "userId" to uid,
            "compId" to compId.take(128),
            "environment" to TftEnvironment.fromRaw(environment).name,
            "title" to title.trim().take(80),
            "tier" to TierRank.fromRaw(tier).name,
            "playstyle" to playstyle.trim().ifBlank { "Flex" }.take(40),
            "carryName" to carryName.trim().ifBlank { "Carry" }.take(50),
            "coreUnits" to coreUnits.take(10).map { it.take(160) },
            "hexPositions" to hexPositions.take(10).map { it.take(160) },
            "pinnedToOverlay" to pinnedToOverlay,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        notes?.trim()?.takeIf { it.isNotEmpty() }?.let {
            base["notes"] = it.take(300)
        }
        return base
    }

    fun toMetaComp(): MetaComp = MetaComp(
        id = compId.ifBlank { id },
        environment = TftEnvironment.fromRaw(environment),
        title = title,
        tier = TierRank.fromRaw(tier),
        playstyle = playstyle,
        avgPlacement = 3.8,
        top4Rate = 61.5,
        winRate = 15.2,
        pickRate = 4.5,
        trendDelta = if (pinnedToOverlay) "PINNED" else "SAVED",
        isTrending = false,
        patchTag = if (environment == "PBE") "18.7 PBE" else "18.6",
        carryName = carryName,
        traits = emptyList(),
        coreUnits = coreUnits,
        hexPlacements = hexPositions.mapNotNull { HexUnitPlacement.decode(it) },
        augmentPriorities = emptyList(),
        levelingGuide = notes?.takeIf { it.isNotBlank() } ?: "Custom saved favorite composition",
        notes = notes.orEmpty()
    )
}

@Keep
@IgnoreExtraProperties
data class TrackedPlayerDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val puuid: String = "",
    val gameName: String = "",
    val tagLine: String = "",
    val region: String = "AMERICAS",
    val platform: String = "NA1",
    val lastTier: String = "UNRANKED",
    val lastLp: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "puuid" to puuid.take(128),
        "gameName" to gameName.trim().take(32),
        "tagLine" to tagLine.trim().take(16),
        "region" to RiotRegion.fromRaw(region).name,
        "platform" to RiotPlatform.fromRaw(platform).code,
        "lastTier" to lastTier.take(32),
        "lastLp" to lastLp.coerceIn(0L, 5000L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class UserPreferencesDoc(
    @DocumentId val id: String = "",
    val userId: String = "",
    val themeMode: String = ThemeMode.DARK.name,
    val selectedEnvironment: String = TftEnvironment.LIVE.name,
    val patchAlertsEnabled: Boolean = true,
    val pbeAlertsEnabled: Boolean = true,
    val balanceAlertsEnabled: Boolean = true,
    val trendingAlertsEnabled: Boolean = true,
    val lastSeenLivePatch: String = "18.6",
    val lastSeenPbePatch: String = "18.7-PBE",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "themeMode" to ThemeMode.fromRaw(themeMode).name,
        "selectedEnvironment" to TftEnvironment.fromRaw(selectedEnvironment).name,
        "patchAlertsEnabled" to patchAlertsEnabled,
        "pbeAlertsEnabled" to pbeAlertsEnabled,
        "balanceAlertsEnabled" to balanceAlertsEnabled,
        "trendingAlertsEnabled" to trendingAlertsEnabled,
        "lastSeenLivePatch" to lastSeenLivePatch.take(32),
        "lastSeenPbePatch" to lastSeenPbePatch.take(32),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}
