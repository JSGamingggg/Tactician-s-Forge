package com.example.data.repository

import android.content.Context
import com.example.data.model.AugmentTierEntry
import com.example.data.model.ChampionTierEntry
import com.example.data.model.ItemTierEntry
import com.example.data.model.LiveVersionInfo
import com.example.data.model.MetaComp
import com.example.data.model.OfflineCacheStatus
import com.example.data.model.PatchUpdateRecord
import com.example.ui.components.TftAssetIconResolver
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File

data class OfflineMetaBundle(
    val cacheVersion: Int = CURRENT_CACHE_VERSION,
    val cachedAtMillis: Long = System.currentTimeMillis(),
    val liveVersionInfo: LiveVersionInfo = LiveVersionInfo(
        livePatchVersion = "18.6b",
        pbePatchVersion = "19.1-PBE",
        source = "Patches: tftips.app/en/patches • Comps: tftactics.gg",
        checkedAtMillis = System.currentTimeMillis()
    ),
    val liveComps: List<MetaComp> = MetaCatalogData.liveComps,
    val pbeComps: List<MetaComp> = MetaCatalogData.pbeComps,
    val liveChampions: List<ChampionTierEntry> = MetaCatalogData.liveChampions,
    val pbeChampions: List<ChampionTierEntry> = MetaCatalogData.pbeChampions,
    val liveItems: List<ItemTierEntry> = MetaCatalogData.liveItems,
    val pbeItems: List<ItemTierEntry> = MetaCatalogData.pbeItems,
    val liveAugments: List<AugmentTierEntry> = MetaCatalogData.liveAugments,
    val pbeAugments: List<AugmentTierEntry> = MetaCatalogData.pbeAugments,
    val livePatches: List<PatchUpdateRecord> = MetaCatalogData.livePatches,
    val pbePatches: List<PatchUpdateRecord> = MetaCatalogData.pbePatches,
    val ddragonVersion: String = "15.22.1",
    val ddragonChampionJson: String? = null,
    val ddragonItemJson: String? = null,
    val ddragonAugmentJson: String? = null
) {
    fun toCacheStatus(isOfflineFallback: Boolean = false): OfflineCacheStatus =
        OfflineCacheStatus(
            isReady = true,
            isOfflineFallback = isOfflineFallback,
            cachedAtMillis = cachedAtMillis,
            compsCount = liveComps.size + pbeComps.size,
            championsCount = liveChampions.size + pbeChampions.size,
            itemsCount = liveItems.size + pbeItems.size,
            augmentsCount = liveAugments.size + pbeAugments.size
        )

    companion object {
        const val CURRENT_CACHE_VERSION = 5
    }
}

class OfflineMetaCacheStore(context: Context) {
    private val appContext = context.applicationContext
    private val cacheFile = File(appContext.filesDir, CACHE_FILE_NAME)
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val adapter = moshi.adapter(OfflineMetaBundle::class.java)

    @Synchronized
    fun readOrSeedBundle(): OfflineMetaBundle {
        if (cacheFile.exists()) {
            val raw = runCatching { cacheFile.readText(Charsets.UTF_8) }.getOrNull()
            if (!raw.isNullOrBlank()) {
                val parsed = runCatching { adapter.fromJson(raw) }.getOrNull()
                if (parsed != null &&
                    parsed.cacheVersion >= OfflineMetaBundle.CURRENT_CACHE_VERSION &&
                    parsed.liveComps.isNotEmpty() &&
                    parsed.liveItems.isNotEmpty() &&
                    parsed.liveAugments.isNotEmpty()
                ) {
                    TftAssetIconResolver.updateDdragonCatalog(
                        version = parsed.ddragonVersion,
                        championJson = parsed.ddragonChampionJson,
                        itemJson = parsed.ddragonItemJson,
                        augmentJson = parsed.ddragonAugmentJson
                    )
                    return parsed
                }
            }
        }
        val seeded = OfflineMetaBundle()
        writeBundle(seeded)
        TftAssetIconResolver.updateDdragonCatalog(
            version = seeded.ddragonVersion,
            championJson = seeded.ddragonChampionJson,
            itemJson = seeded.ddragonItemJson,
            augmentJson = seeded.ddragonAugmentJson
        )
        return seeded
    }

    @Synchronized
    fun writeBundle(bundle: OfflineMetaBundle): Boolean {
        return runCatching {
            val parent = cacheFile.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }
            val tmpFile = File(appContext.filesDir, "$CACHE_FILE_NAME.tmp")
            tmpFile.writeText(adapter.toJson(bundle), Charsets.UTF_8)
            if (cacheFile.exists()) {
                cacheFile.delete()
            }
            tmpFile.renameTo(cacheFile)
        }.getOrDefault(false)
    }

    fun hasPersistedCache(): Boolean = cacheFile.exists() && cacheFile.length() > 0L

    companion object {
        private const val CACHE_FILE_NAME = "tft_offline_meta_cache_v2.json"
    }
}
