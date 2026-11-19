package com.example.data.model

data class HexUnitPlacement(
    val unitName: String,
    val row: Int, // 0 (frontline) .. 3 (backline)
    val col: Int, // 0 .. 6
    val cost: Int,
    val items: List<String> = emptyList(),
    val isCarry: Boolean = false,
    val stars: Int = 2
) {
    fun encode(): String = "$unitName@$row,$col@${items.joinToString(",")}"

    companion object {
        fun decode(raw: String): HexUnitPlacement? {
            val parts = raw.split("@")
            if (parts.size < 2) return null
            val coords = parts[1].split(",")
            if (coords.size != 2) return null
            val r = coords[0].toIntOrNull() ?: return null
            val c = coords[1].toIntOrNull() ?: return null
            val items = if (parts.size >= 3 && parts[2].isNotBlank()) {
                parts[2].split(",").map { it.trim() }.filter { it.isNotEmpty() }
            } else emptyList()
            return HexUnitPlacement(
                unitName = parts[0],
                row = r.coerceIn(0, 3),
                col = c.coerceIn(0, 6),
                cost = 4,
                items = items,
                isCarry = items.isNotEmpty()
            )
        }
    }
}

data class MetaComp(
    val id: String,
    val environment: TftEnvironment,
    val title: String,
    val tier: TierRank,
    val playstyle: String,
    val avgPlacement: Double,
    val top4Rate: Double,
    val winRate: Double,
    val pickRate: Double,
    val trendDelta: String,
    val isTrending: Boolean,
    val patchTag: String,
    val carryName: String,
    val traits: List<String>,
    val coreUnits: List<String>,
    val hexPlacements: List<HexUnitPlacement>,
    val augmentPriorities: List<String>,
    val levelingGuide: String,
    val notes: String,
    val setName: String = environment.setName,
    val sourceUrl: String = "https://tftacademy.com/tierlist/comps",
    val teamPlannerCode: String = ""
)

data class ChampionTierEntry(
    val id: String,
    val name: String,
    val cost: Int,
    val tier: TierRank,
    val environment: TftEnvironment,
    val traits: List<String>,
    val role: String,
    val avgPlacement: Double,
    val top4Rate: Double,
    val bestItems: List<String>,
    val synergyComps: List<String>,
    val patchDelta: DeltaType? = null,
    val patchNote: String = "",
    val abilityName: String = "",
    val abilityDescription: String = ""
)

data class BalanceChangeEntry(
    val id: String,
    val targetName: String,
    val category: String, // Champion, Trait, Augment, System
    val deltaType: DeltaType,
    val summary: String,
    val previousValue: String,
    val newValue: String
)

data class PatchUpdateRecord(
    val id: String,
    val environment: TftEnvironment,
    val version: String,
    val title: String,
    val releaseDate: String,
    val isMajorPatch: Boolean,
    val headline: String,
    val highlights: List<String>,
    val balanceChanges: List<BalanceChangeEntry>,
    val trendingCompIds: List<String>,
    val sourceUrl: String = "https://tftips.app/en/patches"
)

data class LiveVersionInfo(
    val livePatchVersion: String,
    val pbePatchVersion: String,
    val source: String,
    val checkedAtMillis: Long,
    val liveSetName: String = TftEnvironment.LIVE.setName,
    val pbeSetName: String = TftEnvironment.PBE.setName
)

data class ItemTierEntry(
    val id: String,
    val name: String,
    val tier: TierRank,
    val environment: TftEnvironment,
    val category: ItemCategory,
    val components: List<String>,
    val statsSummary: String,
    val avgPlacement: Double,
    val top4Rate: Double,
    val bestHolders: List<String>,
    val patchDelta: DeltaType? = null,
    val patchNote: String = ""
)

data class AugmentTierEntry(
    val id: String,
    val name: String,
    val tier: TierRank,
    val augmentTier: AugmentTier,
    val environment: TftEnvironment,
    val category: String,
    val avgPlacement: Double,
    val top4Rate: Double,
    val description: String,
    val synergyComps: List<String>,
    val patchDelta: DeltaType? = null,
    val patchNote: String = ""
)

data class OfflineCacheStatus(
    val isReady: Boolean = false,
    val isOfflineFallback: Boolean = false,
    val cachedAtMillis: Long = 0L,
    val compsCount: Int = 0,
    val championsCount: Int = 0,
    val itemsCount: Int = 0,
    val augmentsCount: Int = 0,
    val prefetchedAssetsCount: Int = 0,
    val totalPrefetchAssets: Int = 0,
    val isPrefetchingAssets: Boolean = false
)

data class MetaSyncResult(
    val versionInfo: LiveVersionInfo,
    val cacheStatus: OfflineCacheStatus,
    val newLivePatchDetected: Boolean = false,
    val newPbePatchDetected: Boolean = false,
    val newlyAddedComps: List<MetaComp> = emptyList(),
    val compsUpdatedDetected: Boolean = false,
    val syncedFromNetwork: Boolean = false
)

