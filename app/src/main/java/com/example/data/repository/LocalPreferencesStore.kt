package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.FavoriteCompDoc
import com.example.data.model.TftEnvironment
import com.example.data.model.ThemeMode
import com.example.data.model.UserPreferencesDoc
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.tftDataStore: DataStore<Preferences> by preferencesDataStore(name = "tacticians_forge_prefs")

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
        val LAST_NOTIFIED_COMPS_SIGNATURE = stringPreferencesKey("last_notified_comps_signature")
        val LOCAL_FAVORITES_JSON = stringPreferencesKey("local_favorites_json")
        val LOCAL_PLAYERS_JSON = stringPreferencesKey("local_players_json")
        val IS_GUEST_MODE = booleanPreferencesKey("is_guest_mode")
        val GITHUB_REPO_OVERRIDE = stringPreferencesKey("github_repo_override")
        val GITHUB_TOKEN_OVERRIDE = stringPreferencesKey("github_token_override")
        val APP_UPDATE_ALERTS_ENABLED = booleanPreferencesKey("app_update_alerts_enabled")
        val LAST_NOTIFIED_APP_RELEASE_KEY = stringPreferencesKey("last_notified_app_release_key")
        val LAST_PROMPTED_APP_RELEASE_KEY = stringPreferencesKey("last_prompted_app_release_key")
        val LAST_INSTALLED_APK_ASSET_ID = longPreferencesKey("last_installed_apk_asset_id")
        val DISMISSED_APP_RELEASE_KEY = stringPreferencesKey("dismissed_app_release_key")
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

    suspend fun getLastNotifiedCompsSignature(): String? =
        appContext.tftDataStore.data.first()[Keys.LAST_NOTIFIED_COMPS_SIGNATURE]?.takeIf { it.isNotBlank() }

    suspend fun setLastNotifiedCompsSignature(signature: String) {
        if (signature.isBlank()) return
        appContext.tftDataStore.edit { prefs ->
            prefs[Keys.LAST_NOTIFIED_COMPS_SIGNATURE] = signature
        }
    }

    suspend fun getLastPromptedAppReleaseKey(): String? =
        appContext.tftDataStore.data.first()[Keys.LAST_PROMPTED_APP_RELEASE_KEY]?.takeIf { it.isNotBlank() }

    suspend fun setLastPromptedAppReleaseKey(releaseKey: String) {
        if (releaseKey.isBlank()) return
        appContext.tftDataStore.edit { prefs ->
            prefs[Keys.LAST_PROMPTED_APP_RELEASE_KEY] = releaseKey
        }
    }

    internal suspend fun clearAllForTests() {
        appContext.tftDataStore.edit { it.clear() }
        sharedPrefs.edit().clear().commit()
    }

    val githubRepoOverrideFlow: Flow<String?> = appContext.tftDataStore.data.map { prefs ->
        prefs[Keys.GITHUB_REPO_OVERRIDE]?.takeIf { it.isNotBlank() }
    }

    val appUpdateAlertsEnabledFlow: Flow<Boolean> = appContext.tftDataStore.data.map { prefs ->
        prefs[Keys.APP_UPDATE_ALERTS_ENABLED] ?: true
    }

    suspend fun getGithubRepoOverride(): String? =
        appContext.tftDataStore.data.first()[Keys.GITHUB_REPO_OVERRIDE]?.takeIf { it.isNotBlank() }

    suspend fun setGithubRepoOverride(slugOrEmpty: String) {
        appContext.tftDataStore.edit { prefs ->
            val trimmed = slugOrEmpty.trim()
            if (trimmed.isBlank()) {
                prefs.remove(Keys.GITHUB_REPO_OVERRIDE)
            } else {
                prefs[Keys.GITHUB_REPO_OVERRIDE] = trimmed
            }
        }
    }

    suspend fun getGithubTokenOverride(): String? =
        appContext.tftDataStore.data.first()[Keys.GITHUB_TOKEN_OVERRIDE]?.takeIf { it.isNotBlank() }

    suspend fun setGithubTokenOverride(tokenOrEmpty: String) {
        appContext.tftDataStore.edit { prefs ->
            val trimmed = tokenOrEmpty.trim()
            if (trimmed.isBlank()) {
                prefs.remove(Keys.GITHUB_TOKEN_OVERRIDE)
            } else {
                prefs[Keys.GITHUB_TOKEN_OVERRIDE] = trimmed
            }
        }
    }

    suspend fun isAppUpdateAlertsEnabled(): Boolean =
        appContext.tftDataStore.data.first()[Keys.APP_UPDATE_ALERTS_ENABLED] ?: true

    suspend fun setAppUpdateAlertsEnabled(enabled: Boolean) {
        appContext.tftDataStore.edit { prefs ->
            prefs[Keys.APP_UPDATE_ALERTS_ENABLED] = enabled
        }
    }

    suspend fun getLastNotifiedAppReleaseKey(): String? =
        appContext.tftDataStore.data.first()[Keys.LAST_NOTIFIED_APP_RELEASE_KEY]

    suspend fun setLastNotifiedAppReleaseKey(releaseKey: String) {
        appContext.tftDataStore.edit { prefs ->
            prefs[Keys.LAST_NOTIFIED_APP_RELEASE_KEY] = releaseKey
        }
    }

    suspend fun getLastInstalledApkAssetId(): Long? =
        appContext.tftDataStore.data.first()[Keys.LAST_INSTALLED_APK_ASSET_ID]

    suspend fun setLastInstalledApkAssetId(assetId: Long) {
        appContext.tftDataStore.edit { prefs ->
            prefs[Keys.LAST_INSTALLED_APK_ASSET_ID] = assetId
        }
    }

    suspend fun getDismissedAppReleaseKey(): String? =
        appContext.tftDataStore.data.first()[Keys.DISMISSED_APP_RELEASE_KEY]

    suspend fun setDismissedAppReleaseKey(releaseKey: String?) {
        appContext.tftDataStore.edit { prefs ->
            if (releaseKey.isNullOrBlank()) {
                prefs.remove(Keys.DISMISSED_APP_RELEASE_KEY)
            } else {
                prefs[Keys.DISMISSED_APP_RELEASE_KEY] = releaseKey
            }
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

    fun readOverlayOpacity(): Float {
        val stored = sharedPrefs.getFloat(KEY_OVERLAY_OPACITY, DEFAULT_OVERLAY_OPACITY)
        return if (stored.isNaN()) DEFAULT_OVERLAY_OPACITY else stored.coerceIn(MIN_OVERLAY_OPACITY, MAX_OVERLAY_OPACITY)
    }

    fun saveOverlayOpacity(opacity: Float) {
        val clamped = if (opacity.isNaN()) DEFAULT_OVERLAY_OPACITY else opacity.coerceIn(MIN_OVERLAY_OPACITY, MAX_OVERLAY_OPACITY)
        sharedPrefs.edit().putFloat(KEY_OVERLAY_OPACITY, clamped).apply()
    }

    companion object {
        const val KEY_OVERLAY_OPACITY = "overlay_opacity_float"
        const val MIN_OVERLAY_OPACITY = 0.25f
        const val MAX_OVERLAY_OPACITY = 1.0f
        const val DEFAULT_OVERLAY_OPACITY = 0.96f

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
    }
}
