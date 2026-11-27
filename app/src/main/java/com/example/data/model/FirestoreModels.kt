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
        title = com.example.ui.components.decodeHtmlEntities(title),
        tier = TierRank.fromRaw(tier),
        playstyle = playstyle,
        avgPlacement = 3.85,
        top4Rate = 61.5,
        winRate = 15.2,
        pickRate = 4.5,
        trendDelta = if (pinnedToOverlay) "PINNED" else "SAVED",
        isTrending = false,
        patchTag = if (environment.equals("PBE", ignoreCase = true)) "19.1-PBE" else "18.7",
        carryName = com.example.ui.components.formatChampionDisplayName(carryName).ifBlank { carryName },
        traits = emptyList(),
        coreUnits = coreUnits.map { com.example.ui.components.formatChampionDisplayName(it).ifBlank { it } },
        hexPlacements = hexPositions.mapNotNull { HexUnitPlacement.decode(it) }.map { p ->
            p.copy(
                unitName = com.example.ui.components.formatChampionDisplayName(p.unitName).ifBlank { p.unitName },
                items = p.items.map { com.example.ui.components.formatRiotAssetLabel(it).ifBlank { it } },
                otherItems = p.otherItems.map { com.example.ui.components.formatRiotAssetLabel(it).ifBlank { it } }
            )
        },
        augmentPriorities = emptyList(),
        levelingGuide = notes?.takeIf { it.isNotBlank() }?.let { com.example.ui.components.decodeHtmlEntities(it) }
            ?: "Custom saved favorite composition",
        notes = com.example.ui.components.decodeHtmlEntities(notes.orEmpty())
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
    val lastSeenLivePatch: String = "18.7",
    val lastSeenPbePatch: String = "19.1-PBE",
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
