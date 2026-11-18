package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.FavoriteCompDoc
import com.example.data.model.RiotPlatform
import com.example.data.model.RiotRegion
import com.example.data.model.TftEnvironment
import com.example.data.model.ThemeMode
import com.example.data.model.TrackedPlayerDoc
import com.example.data.model.UserPreferencesDoc
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.tftDataStore: DataStore<Preferences> by preferencesDataStore(name = "tacticians_forge_prefs")

@JsonClass(generateAdapter = true)
data class OverlayPinSnapshot(
    val compId: String,
    val environment: String,
    val title: String,
    val tier: String,
    val playstyle: String,
    val carryName: String,
    val coreUnits: List<String>,
    val hexPositions: List<String>,
    val notes: String = ""
)

@JsonClass(generateAdapter = true)
data class LocalFavoriteRecord(
    val compId: String,
    val environment: String,
    val title: String,
    val tier: String,
    val playstyle: String,
    val carryName: String,
    val coreUnits: List<String>,
    val hexPositions: List<String>,
    val pinnedToOverlay: Boolean,
    val notes: String? = null
)

@JsonClass(generateAdapter = true)
data class LocalTrackedPlayerRecord(
    val playerId: String,
    val puuid: String,
    val gameName: String,
    val tagLine: String,
    val region: String,
    val platform: String,
    val lastTier: String,
    val lastLp: Long
)

class LocalPreferencesStore(context: Context) {
    private val appContext = context.applicationContext
    private val sharedPrefs = appContext.getSharedPreferences("overlay_bridge_prefs", Context.MODE_PRIVATE)

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ENVIRONMENT = stringPreferencesKey("selected_environment")
        val PATCH_ALERTS = booleanPreferencesKey("patch_alerts_enabled")
        val PBE_ALERTS = booleanPreferencesKey("pbe_alerts_enabled")
        val BALANCE_ALERTS = booleanPreferencesKey("balance_alerts_enabled")
        val TRENDING_ALERTS = booleanPreferencesKey("trending_alerts_enabled")
        val LAST_LIVE_PATCH = stringPreferencesKey("last_seen_live_patch")
        val LAST_PBE_PATCH = stringPreferencesKey("last_seen_pbe_patch")
        val LAST_NOTIFIED_LIVE_DD = stringPreferencesKey("last_notified_live_dd")
        val LAST_NOTIFIED_PBE_TAG = stringPreferencesKey("last_notified_pbe_tag")
        val LAST_NOTIFIED_COMP_IDS = stringPreferencesKey("last_notified_comp_ids")
        val LOCAL_FAVORITES_JSON = stringPreferencesKey("local_favorites_json")
        val LOCAL_PLAYERS_JSON = stringPreferencesKey("local_players_json")
        val IS_GUEST_MODE = booleanPreferencesKey("is_guest_mode")
    }

    val preferencesFlow: Flow<UserPreferencesDoc> = appContext.tftDataStore.data.map { prefs ->
        UserPreferencesDoc(
            id = "settings",
            userId = "local",
            themeMode = prefs[Keys.THEME_MODE] ?: ThemeMode.DARK.name,
            selectedEnvironment = prefs[Keys.ENVIRONMENT] ?: TftEnvironment.LIVE.name,
            patchAlertsEnabled = prefs[Keys.PATCH_ALERTS] ?: true,
            pbeAlertsEnabled = prefs[Keys.PBE_ALERTS] ?: true,
            balanceAlertsEnabled = prefs[Keys.BALANCE_ALERTS] ?: true,
            trendingAlertsEnabled = prefs[Keys.TRENDING_ALERTS] ?: true,
            lastSeenLivePatch = prefs[Keys.LAST_LIVE_PATCH] ?: "18.6",
            lastSeenPbePatch = prefs[Keys.LAST_PBE_PATCH] ?: "18.7-PBE"
        )
    }

    val isGuestModeFlow: Flow<Boolean> = appContext.tftDataStore.data.map { prefs ->
        prefs[Keys.IS_GUEST_MODE] ?: false
    }

    suspend fun setGuestModeChoice(enabled: Boolean) {
        appContext.tftDataStore.edit { prefs ->
            prefs[Keys.IS_GUEST_MODE] = enabled
        }
    }

    val localFavoritesFlow: Flow<List<FavoriteCompDoc>> = appContext.tftDataStore.data.map { prefs ->
        val raw = prefs[Keys.LOCAL_FAVORITES_JSON] ?: return@map emptyList()
        val parsed = runCatching { localFavAdapter.fromJson(raw) }.getOrNull().orEmpty()
        parsed.map {
            FavoriteCompDoc(
                id = it.compId,
                userId = "local",
                compId = it.compId,
                environment = it.environment,
                title = it.title,
                tier = it.tier,
                playstyle = it.playstyle,
                carryName = it.carryName,
                coreUnits = it.coreUnits,
                hexPositions = it.hexPositions,
                pinnedToOverlay = it.pinnedToOverlay,
                notes = it.notes
            )
        }
    }

    val localTrackedPlayersFlow: Flow<List<TrackedPlayerDoc>> = appContext.tftDataStore.data.map { prefs ->
        val raw = prefs[Keys.LOCAL_PLAYERS_JSON] ?: return@map emptyList()
        val parsed = runCatching { localPlayerAdapter.fromJson(raw) }.getOrNull().orEmpty()
        parsed.map {
            TrackedPlayerDoc(
                id = it.playerId,
                userId = "local",
                puuid = it.puuid,
                gameName = it.gameName,
                tagLine = it.tagLine,
                region = it.region,
                platform = it.platform,
                lastTier = it.lastTier,
                lastLp = it.lastLp
            )
        }
    }

    suspend fun updatePreferences(
        themeMode: ThemeMode? = null,
        environment: TftEnvironment? = null,
        patchAlertsEnabled: Boolean? = null,
        pbeAlertsEnabled: Boolean? = null,
        balanceAlertsEnabled: Boolean? = null,
        trendingAlertsEnabled: Boolean? = null,
        lastSeenLivePatch: String? = null,
        lastSeenPbePatch: String? = null
    ) {
        appContext.tftDataStore.edit { prefs ->
            themeMode?.let { prefs[Keys.THEME_MODE] = it.name }
            environment?.let { prefs[Keys.ENVIRONMENT] = it.name }
            patchAlertsEnabled?.let { prefs[Keys.PATCH_ALERTS] = it }
            pbeAlertsEnabled?.let { prefs[Keys.PBE_ALERTS] = it }
            balanceAlertsEnabled?.let { prefs[Keys.BALANCE_ALERTS] = it }
            trendingAlertsEnabled?.let { prefs[Keys.TRENDING_ALERTS] = it }
            lastSeenLivePatch?.let { prefs[Keys.LAST_LIVE_PATCH] = it }
            lastSeenPbePatch?.let { prefs[Keys.LAST_PBE_PATCH] = it }
        }
    }

    suspend fun saveLocalFavorite(doc: FavoriteCompDoc) {
        appContext.tftDataStore.edit { prefs ->
            val current = prefs[Keys.LOCAL_FAVORITES_JSON]?.let {
                runCatching { localFavAdapter.fromJson(it) }.getOrNull()
            }.orEmpty().toMutableList()
            current.removeAll { it.compId == doc.compId }
            current.add(
                0,
                LocalFavoriteRecord(
                    compId = doc.compId,
                    environment = doc.environment,
                    title = doc.title,
                    tier = doc.tier,
                    playstyle = doc.playstyle,
                    carryName = doc.carryName,
                    coreUnits = doc.coreUnits,
                    hexPositions = doc.hexPositions,
                    pinnedToOverlay = doc.pinnedToOverlay,
                    notes = doc.notes
                )
            )
            prefs[Keys.LOCAL_FAVORITES_JSON] = localFavAdapter.toJson(current)
        }
    }

    suspend fun removeLocalFavorite(compId: String) {
        appContext.tftDataStore.edit { prefs ->
            val current = prefs[Keys.LOCAL_FAVORITES_JSON]?.let {
                runCatching { localFavAdapter.fromJson(it) }.getOrNull()
            }.orEmpty().filterNot { it.compId == compId }
            prefs[Keys.LOCAL_FAVORITES_JSON] = localFavAdapter.toJson(current)
        }
    }

    suspend fun setLocalFavoritePinned(compId: String, pinned: Boolean) {
        appContext.tftDataStore.edit { prefs ->
            val current = prefs[Keys.LOCAL_FAVORITES_JSON]?.let {
                runCatching { localFavAdapter.fromJson(it) }.getOrNull()
            }.orEmpty().map {
                if (it.compId == compId) it.copy(pinnedToOverlay = pinned) else it
            }
            prefs[Keys.LOCAL_FAVORITES_JSON] = localFavAdapter.toJson(current)
        }
    }

    suspend fun saveLocalTrackedPlayer(
        playerId: String,
        puuid: String,
        gameName: String,
        tagLine: String,
        region: RiotRegion,
        platform: RiotPlatform,
        lastTier: String,
        lastLp: Long
    ) {
        appContext.tftDataStore.edit { prefs ->
            val current = prefs[Keys.LOCAL_PLAYERS_JSON]?.let {
                runCatching { localPlayerAdapter.fromJson(it) }.getOrNull()
            }.orEmpty().toMutableList()
            current.removeAll { it.playerId == playerId }
            current.add(
                0,
                LocalTrackedPlayerRecord(
                    playerId = playerId,
                    puuid = puuid,
                    gameName = gameName,
                    tagLine = tagLine,
                    region = region.name,
                    platform = platform.code,
                    lastTier = lastTier,
                    lastLp = lastLp
                )
            )
            prefs[Keys.LOCAL_PLAYERS_JSON] = localPlayerAdapter.toJson(current)
        }
    }

    suspend fun removeLocalTrackedPlayer(playerId: String) {
        appContext.tftDataStore.edit { prefs ->
            val current = prefs[Keys.LOCAL_PLAYERS_JSON]?.let {
                runCatching { localPlayerAdapter.fromJson(it) }.getOrNull()
            }.orEmpty().filterNot { it.playerId == playerId }
            prefs[Keys.LOCAL_PLAYERS_JSON] = localPlayerAdapter.toJson(current)
        }
    }

    suspend fun getNotificationSnapshot(): UserPreferencesDoc = preferencesFlow.first()

    suspend fun getLastNotifiedVersions(): Pair<String?, String?> {
        val prefs = appContext.tftDataStore.data.first()
        return prefs[Keys.LAST_NOTIFIED_LIVE_DD] to prefs[Keys.LAST_NOTIFIED_PBE_TAG]
    }

    suspend fun setLastNotifiedVersions(liveVer: String?, pbeVer: String?) {
        appContext.tftDataStore.edit { prefs ->
            liveVer?.let { prefs[Keys.LAST_NOTIFIED_LIVE_DD] = it }
            pbeVer?.let { prefs[Keys.LAST_NOTIFIED_PBE_TAG] = it }
        }
    }

    suspend fun getNotifiedCompIds(): Set<String>? {
        val prefs = appContext.tftDataStore.data.first()
        val raw = prefs[Keys.LAST_NOTIFIED_COMP_IDS] ?: return null
        return raw.split('\n').filter { it.isNotBlank() }.toSet()
    }

    suspend fun saveNotifiedCompIds(compIds: Set<String>) {
        appContext.tftDataStore.edit { prefs ->
            prefs[Keys.LAST_NOTIFIED_COMP_IDS] = compIds.sorted().joinToString("\n")
        }
    }

    // Synchronous bridge for OverlayService (runs in same process, zero Firestore race)
    fun publishOverlayPinnedComps(favorites: List<FavoriteCompDoc>) {
        val pinned = favorites
            .filter { it.pinnedToOverlay }
            .ifEmpty { favorites.take(3) }
            .map {
                OverlayPinSnapshot(
                    compId = it.compId.ifBlank { it.id },
                    environment = it.environment,
                    title = it.title,
                    tier = it.tier,
                    playstyle = it.playstyle,
                    carryName = it.carryName,
                    coreUnits = it.coreUnits,
                    hexPositions = it.hexPositions,
                    notes = it.notes.orEmpty()
                )
            }
        sharedPrefs.edit().putString("pinned_comps_json", overlayAdapter.toJson(pinned)).apply()
    }

    fun readOverlayPinnedComps(): List<OverlayPinSnapshot> {
        val raw = sharedPrefs.getString("pinned_comps_json", null) ?: return emptyList()
        return runCatching { overlayAdapter.fromJson(raw) }.getOrNull().orEmpty()
    }

    companion object {
        private val sharedMoshi: Moshi by lazy {
            Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        }
        private val overlayAdapter by lazy {
            val type = Types.newParameterizedType(List::class.java, OverlayPinSnapshot::class.java)
            sharedMoshi.adapter<List<OverlayPinSnapshot>>(type)
        }
        private val localFavAdapter by lazy {
            val type = Types.newParameterizedType(List::class.java, LocalFavoriteRecord::class.java)
            sharedMoshi.adapter<List<LocalFavoriteRecord>>(type)
        }
        private val localPlayerAdapter by lazy {
            val type = Types.newParameterizedType(List::class.java, LocalTrackedPlayerRecord::class.java)
            sharedMoshi.adapter<List<LocalTrackedPlayerRecord>>(type)
        }
    }
}
