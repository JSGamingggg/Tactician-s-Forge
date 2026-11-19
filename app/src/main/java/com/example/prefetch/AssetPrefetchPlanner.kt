package com.example.prefetch

import android.content.Context
import com.example.data.model.MetaComp
import com.example.data.model.TftEnvironment
import com.example.data.repository.OfflineMetaBundle
import com.example.ui.components.TftAssetIconResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PrefetchScope {
    ALL,
    LIVE,
    PBE,
    WARM_LAUNCH,
    DELTA_SYNC;

    companion object {
        fun fromRaw(raw: String?): PrefetchScope =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: ALL
    }
}

enum class PrefetchAssetCategory {
    CHAMPION_PORTRAIT,
    TRAIT_ICON,
    ITEM_ICON,
    AUGMENT_ICON
}

data class PrefetchAssetTarget(
    val url: String,
    val sizePx: Int,
    val category: PrefetchAssetCategory
)

data class AssetPrefetchStatus(
    val isRunning: Boolean = false,
    val cachedCount: Int = 0,
    val skippedAlreadyCachedCount: Int = 0,
    val failedCount: Int = 0,
    val totalPlannedCount: Int = 0,
    val lastCompletedAtMillis: Long = 0L,
    val lastScope: String = ""
) {
    val totalReadyCount: Int
        get() = (cachedCount + skippedAlreadyCachedCount).coerceAtMost(
            if (totalPlannedCount > 0) totalPlannedCount else Int.MAX_VALUE
        )
}

/**
 * Lightweight persisted telemetry store exposing WorkManager asset prefetch progress
 * to the UI without blocking startup or requiring a database migration.
 */
object PrefetchTelemetryStore {
    private const val PREFS_NAME = "tft_prefetch_telemetry_prefs"
    private const val KEY_CACHED_COUNT = "cached_count"
    private const val KEY_SKIPPED_COUNT = "skipped_count"
    private const val KEY_FAILED_COUNT = "failed_count"
    private const val KEY_TOTAL_PLANNED = "total_planned"
    private const val KEY_LAST_COMPLETED_MS = "last_completed_ms"
    private const val KEY_LAST_SCOPE = "last_scope"

    private val _status = MutableStateFlow(AssetPrefetchStatus())
    val status: StateFlow<AssetPrefetchStatus> = _status.asStateFlow()

    fun hydrate(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastCompleted = prefs.getLong(KEY_LAST_COMPLETED_MS, 0L)
        if (lastCompleted > 0L) {
            _status.value = AssetPrefetchStatus(
                isRunning = false,
                cachedCount = prefs.getInt(KEY_CACHED_COUNT, 0),
                skippedAlreadyCachedCount = prefs.getInt(KEY_SKIPPED_COUNT, 0),
                failedCount = prefs.getInt(KEY_FAILED_COUNT, 0),
                totalPlannedCount = prefs.getInt(KEY_TOTAL_PLANNED, 0),
                lastCompletedAtMillis = lastCompleted,
                lastScope = prefs.getString(KEY_LAST_SCOPE, "").orEmpty()
            )
        }
    }

    fun markStarted(scope: PrefetchScope, totalPlanned: Int) {
        val prev = _status.value
        _status.value = prev.copy(
            isRunning = true,
            totalPlannedCount = totalPlanned.coerceAtLeast(prev.totalPlannedCount),
            lastScope = scope.name
        )
    }

    fun updateProgress(
        cached: Int,
        skipped: Int,
        failed: Int,
        totalPlanned: Int
    ) {
        val prev = _status.value
        _status.value = prev.copy(
            isRunning = true,
            cachedCount = cached,
            skippedAlreadyCachedCount = skipped,
            failedCount = failed,
            totalPlannedCount = totalPlanned
        )
    }

    fun markCompleted(
        context: Context,
        scope: PrefetchScope,
        cached: Int,
        skipped: Int,
        failed: Int,
        totalPlanned: Int,
        completedAtMillis: Long = System.currentTimeMillis()
    ) {
        val next = AssetPrefetchStatus(
            isRunning = false,
            cachedCount = cached,
            skippedAlreadyCachedCount = skipped,
            failedCount = failed,
            totalPlannedCount = totalPlanned,
            lastCompletedAtMillis = completedAtMillis,
            lastScope = scope.name
        )
        _status.value = next
        runCatching {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_CACHED_COUNT, cached)
                .putInt(KEY_SKIPPED_COUNT, skipped)
                .putInt(KEY_FAILED_COUNT, failed)
                .putInt(KEY_TOTAL_PLANNED, totalPlanned)
                .putLong(KEY_LAST_COMPLETED_MS, completedAtMillis)
                .putString(KEY_LAST_SCOPE, scope.name)
                .apply()
        }
    }

    fun markIdleOnError() {
        _status.value = _status.value.copy(isRunning = false)
    }

    fun resetForTests(context: Context? = null) {
        _status.value = AssetPrefetchStatus()
        context?.applicationContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.clear()
            ?.commit()
    }
}

/**
 * Pure, deterministic planner that transforms an [OfflineMetaBundle] into a prioritized,
 * deduplicated list of image assets to warm into Coil's persistent DiskCache before users
 * navigate to Meta Snapshots, Tier Lists (Champions / Items / Augments), Champion Inspector,
 * or the Live Overlay.
 */
object AssetPrefetchPlanner {
    const val DEFAULT_MAX_ASSETS = 650
    const val WARM_LAUNCH_MAX_ASSETS = 220

    private const val CHAMPION_TARGET_PX = 128
    private const val TRAIT_TARGET_PX = 48
    private const val ITEM_TARGET_PX = 64
    private const val AUGMENT_TARGET_PX = 64

    private val LEADING_TRAIT_COUNT_REGEX = Regex("""^\d+\s+""")

    fun buildPrefetchQueue(
        bundle: OfflineMetaBundle,
        scope: PrefetchScope = PrefetchScope.ALL,
        maxAssets: Int = DEFAULT_MAX_ASSETS
    ): List<PrefetchAssetTarget> {
        if (maxAssets <= 0) return emptyList()
        val planned = LinkedHashMap<String, PrefetchAssetTarget>(minOf(maxAssets, 512))

        fun enqueueUrl(url: String?, sizePx: Int, category: PrefetchAssetCategory): Boolean {
            if (planned.size >= maxAssets) return false
            val clean = url?.trim().orEmpty()
            if (clean.isEmpty() || !clean.startsWith("http", ignoreCase = true)) return true
            if (!planned.containsKey(clean)) {
                planned[clean] = PrefetchAssetTarget(
                    url = clean,
                    sizePx = sizePx,
                    category = category
                )
            }
            return planned.size < maxAssets
        }

        fun enqueueChampion(name: String, env: TftEnvironment, includeFallback: Boolean): Boolean {
            if (name.isBlank()) return planned.size < maxAssets
            val primary = TftAssetIconResolver.resolveSquareChampionPortraitUrl(name, env)
            if (!enqueueUrl(primary, CHAMPION_TARGET_PX, PrefetchAssetCategory.CHAMPION_PORTRAIT)) {
                return false
            }
            if (includeFallback) {
                val fallback = TftAssetIconResolver.resolveChampionIconUrl(name, env)
                if (fallback != null && fallback != primary) {
                    if (!enqueueUrl(fallback, CHAMPION_TARGET_PX, PrefetchAssetCategory.CHAMPION_PORTRAIT)) {
                        return false
                    }
                }
            }
            return planned.size < maxAssets
        }

        fun enqueueTrait(rawTrait: String, env: TftEnvironment): Boolean {
            val cleanTrait = rawTrait.trim().replace(LEADING_TRAIT_COUNT_REGEX, "").trim()
            if (cleanTrait.isEmpty()) return planned.size < maxAssets
            return enqueueUrl(
                TftAssetIconResolver.resolveTraitIconUrl(cleanTrait, env),
                TRAIT_TARGET_PX,
                PrefetchAssetCategory.TRAIT_ICON
            )
        }

        fun enqueueItem(itemName: String, env: TftEnvironment): Boolean {
            if (itemName.isBlank()) return planned.size < maxAssets
            return enqueueUrl(
                TftAssetIconResolver.resolveItemIconUrl(itemName, env),
                ITEM_TARGET_PX,
                PrefetchAssetCategory.ITEM_ICON
            )
        }

        fun enqueueAugment(augmentName: String): Boolean {
            if (augmentName.isBlank()) return planned.size < maxAssets
            return enqueueUrl(
                TftAssetIconResolver.resolveAugmentIconUrl(augmentName),
                AUGMENT_TARGET_PX,
                PrefetchAssetCategory.AUGMENT_ICON
            )
        }

        fun enqueueCompAssets(comps: List<MetaComp>, env: TftEnvironment): Boolean {
            val sorted = comps.sortedWith(compareBy<MetaComp> { it.tier.sortOrder }.thenBy { it.avgPlacement })
            for (comp in sorted) {
                if (!enqueueChampion(comp.carryName, env, includeFallback = true)) return false
                for (unit in comp.coreUnits) {
                    if (!enqueueChampion(unit, env, includeFallback = false)) return false
                }
                for (placement in comp.hexPlacements) {
                    if (!enqueueChampion(placement.unitName, env, includeFallback = placement.isCarry)) return false
                    for (item in placement.items) {
                        if (!enqueueItem(item, env)) return false
                    }
                }
                for (trait in comp.traits) {
                    if (!enqueueTrait(trait, env)) return false
                }
                for (augment in comp.augmentPriorities) {
                    if (!enqueueAugment(augment)) return false
                }
            }
            return planned.size < maxAssets
        }

        val environments = when (scope) {
            PrefetchScope.LIVE -> listOf(TftEnvironment.LIVE)
            PrefetchScope.PBE -> listOf(TftEnvironment.PBE)
            PrefetchScope.ALL,
            PrefetchScope.WARM_LAUNCH,
            PrefetchScope.DELTA_SYNC -> listOf(TftEnvironment.LIVE, TftEnvironment.PBE)
        }

        // Phase 1: Highest impact — Meta Snapshots & Overlay comp carries, units, traits, BIS items, and augments
        for (env in environments) {
            val comps = if (env == TftEnvironment.PBE) bundle.pbeComps else bundle.liveComps
            if (!enqueueCompAssets(comps, env)) return planned.values.toList()
        }

        // Phase 2: Champion Tier List & Champion Inspector portraits, traits, and BIS items
        for (env in environments) {
            val champs = (if (env == TftEnvironment.PBE) bundle.pbeChampions else bundle.liveChampions)
                .sortedWith(compareBy({ it.tier.sortOrder }, { it.avgPlacement }))
            for (champ in champs) {
                if (!enqueueChampion(champ.name, env, includeFallback = true)) return planned.values.toList()
                for (trait in champ.traits) {
                    if (!enqueueTrait(trait, env)) return planned.values.toList()
                }
                for (item in champ.bestItems) {
                    if (!enqueueItem(item, env)) return planned.values.toList()
                }
            }
        }

        // Phase 3: Item Tier List icons, recipe components, and best holder portraits
        for (env in environments) {
            val items = (if (env == TftEnvironment.PBE) bundle.pbeItems else bundle.liveItems)
                .sortedWith(compareBy({ it.tier.sortOrder }, { it.avgPlacement }))
            for (item in items) {
                if (!enqueueItem(item.name, env)) return planned.values.toList()
                for (component in item.components) {
                    if (!enqueueItem(component, env)) return planned.values.toList()
                }
                for (holder in item.bestHolders) {
                    if (!enqueueChampion(holder, env, includeFallback = false)) return planned.values.toList()
                }
            }
        }

        // Phase 4: Augment Tier List icons
        for (env in environments) {
            val augments = (if (env == TftEnvironment.PBE) bundle.pbeAugments else bundle.liveAugments)
                .sortedWith(compareBy({ it.tier.sortOrder }, { it.avgPlacement }))
            for (augment in augments) {
                if (!enqueueAugment(augment.name)) return planned.values.toList()
            }
        }

        return planned.values.toList()
    }
}
