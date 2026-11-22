package com.example.ui.viewmodel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AugmentTierEntry
import com.example.data.model.ChampionTierEntry
import com.example.data.model.FavoriteCompDoc
import com.example.data.model.HexUnitPlacement
import com.example.data.model.ItemTierEntry
import com.example.data.model.LiveVersionInfo
import com.example.data.model.MetaComp
import com.example.data.model.OfflineCacheStatus
import com.example.data.model.PatchUpdateRecord
import com.example.data.model.TftEnvironment
import com.example.data.model.ThemeMode
import com.example.data.model.TierRank
import com.example.data.model.UserPreferencesDoc
import com.example.data.repository.FirestoreUserRepository
import com.example.data.repository.LocalPreferencesStore
import com.example.data.repository.MetaCacheUpdates
import com.example.data.repository.MetaRepository
import com.example.data.repository.OfflineMetaBundle
import com.example.notifications.MetaUpdateNotifier
import com.example.prefetch.MetaPrefetchScheduler
import com.example.prefetch.PrefetchScope
import com.example.prefetch.PrefetchTelemetryStore
import com.example.data.teamplanner.TftTeamPlannerCodeEncoder
import com.example.update.AppUpdateCoordinator
import com.example.update.AppUpdateUiState
import com.example.update.GitHubReleaseInfo
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class EnvironmentSnapshot(
    val environment: TftEnvironment,
    val comps: List<MetaComp>,
    val champions: List<ChampionTierEntry>,
    val items: List<ItemTierEntry>,
    val augments: List<AugmentTierEntry>,
    val patches: List<PatchUpdateRecord>,
    val cacheStatus: OfflineCacheStatus
)

class MainViewModel(
    private val currentUserId: String?, // null = Guest Mode (local-only, 0 Firestore queries)
    private val metaRepository: MetaRepository,
    private val localStore: LocalPreferencesStore,
    private val firestoreRepo: FirestoreUserRepository,
    autoSyncOnInit: Boolean = false,
    private val autoCheckAppUpdateOnInit: Boolean = true,
    private val appContext: Context? = null
) : ViewModel() {

    internal val autoSyncCompsOnInitEnabled: Boolean = autoSyncOnInit
    internal val autoCheckAppUpdateOnInitEnabled: Boolean = autoCheckAppUpdateOnInit

    private var activeSyncJob: Job? = null
    private var envPersistJob: Job? = null
    private var hasUserExplicitlySelectedEnv = false
    private val prefetchedEnvsInSession = mutableSetOf<TftEnvironment>()

    private val initialBundle = metaRepository.peekOfflineBundle()

    private val _selectedEnvironment = MutableStateFlow(TftEnvironment.LIVE)
    val selectedEnvironment: StateFlow<TftEnvironment> = _selectedEnvironment.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.DARK)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _metaBundle = MutableStateFlow(initialBundle)

    private val _liveVersionInfo = MutableStateFlow<LiveVersionInfo?>(initialBundle.liveVersionInfo)
    val liveVersionInfo: StateFlow<LiveVersionInfo?> = _liveVersionInfo.asStateFlow()

    private val _isOfflineFallback = MutableStateFlow(false)
    val offlineCacheStatus: StateFlow<OfflineCacheStatus> =
        combine(
            _selectedEnvironment,
            _metaBundle,
            _isOfflineFallback,
            PrefetchTelemetryStore.status
        ) { env, bundle, offlineFallback, prefetchStatus ->
            bundle.toCacheStatus(isOfflineFallback = offlineFallback, environment = env).copy(
                prefetchedAssetsCount = prefetchStatus.totalReadyCount,
                totalPrefetchAssets = prefetchStatus.totalPlannedCount,
                isPrefetchingAssets = prefetchStatus.isRunning
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = initialBundle.toCacheStatus(environment = TftEnvironment.LIVE)
        )

    private val _isRefreshingVersions = MutableStateFlow(false)
    val isRefreshingVersions: StateFlow<Boolean> = _isRefreshingVersions.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _copiedCompId = MutableStateFlow<String?>(null)
    val copiedCompId: StateFlow<String?> = _copiedCompId.asStateFlow()
    private var copiedCompClearJob: Job? = null

    val appUpdateState: StateFlow<AppUpdateUiState> = AppUpdateCoordinator.uiState

    val preferencesState: StateFlow<UserPreferencesDoc> =
        if (currentUserId.isNullOrBlank()) {
            localStore.preferencesFlow
                .onEach { prefs ->
                    _themeMode.value = ThemeMode.fromRaw(prefs.themeMode)
                    if (!hasUserExplicitlySelectedEnv) {
                        val persistedEnv = TftEnvironment.fromRaw(prefs.selectedEnvironment)
                        if (_selectedEnvironment.value != persistedEnv) {
                            _selectedEnvironment.value = persistedEnv
                            refreshActiveEnvSnapshot()
                        }
                    }
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = UserPreferencesDoc()
                )
        } else {
            combine(
                localStore.preferencesFlow,
                firestoreRepo.observePreferences(currentUserId)
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        Log.w(TAG, "Error observing preferences", firestoreError ?: error)
                        emit(null)
                    }
            ) { local, cloud ->
                cloud ?: local
            }
                .onEach { prefs ->
                    _themeMode.value = ThemeMode.fromRaw(prefs.themeMode)
                    if (!hasUserExplicitlySelectedEnv) {
                        val persistedEnv = TftEnvironment.fromRaw(prefs.selectedEnvironment)
                        if (_selectedEnvironment.value != persistedEnv) {
                            _selectedEnvironment.value = persistedEnv
                            refreshActiveEnvSnapshot()
                        }
                    }
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = UserPreferencesDoc()
                )
        }

    val favoritesState: StateFlow<UiState<List<FavoriteCompDoc>>> =
        if (currentUserId.isNullOrBlank()) {
            localStore.localFavoritesFlow
                .onEach { list -> localStore.publishOverlayPinnedComps(list) }
                .map<List<FavoriteCompDoc>, UiState<List<FavoriteCompDoc>>> { UiState.Success(it) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = UiState.Loading
                )
        } else {
            firestoreRepo.observeFavorites(currentUserId)
                .onEach { list -> localStore.publishOverlayPinnedComps(list) }
                .map<List<FavoriteCompDoc>, UiState<List<FavoriteCompDoc>>> { UiState.Success(it) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val rootError = firestoreError ?: error
                    Log.w(TAG, "Error observing favorites", rootError)
                    emit(UiState.Error(rootError.message ?: "Failed to load cloud favorites"))
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                    initialValue = UiState.Loading
                )
        }

    val metaComps: StateFlow<List<MetaComp>> = combine(_selectedEnvironment, _metaBundle) { env, bundle ->
        metaRepository.getComps(env, bundle)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = metaRepository.getComps(TftEnvironment.LIVE, initialBundle)
    )

    val championTiers: StateFlow<List<ChampionTierEntry>> = combine(_selectedEnvironment, _metaBundle) { env, bundle ->
        metaRepository.getChampions(env, bundle)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = metaRepository.getChampions(TftEnvironment.LIVE, initialBundle)
    )

    val itemTiers: StateFlow<List<ItemTierEntry>> = combine(_selectedEnvironment, _metaBundle) { env, bundle ->
        metaRepository.getItems(env, bundle)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = metaRepository.getItems(TftEnvironment.LIVE, initialBundle)
    )

    val augmentTiers: StateFlow<List<AugmentTierEntry>> = combine(_selectedEnvironment, _metaBundle) { env, bundle ->
        metaRepository.getAugments(env, bundle)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = metaRepository.getAugments(TftEnvironment.LIVE, initialBundle)
    )

    val patchFeed: StateFlow<List<PatchUpdateRecord>> = combine(_selectedEnvironment, _metaBundle) { env, bundle ->
        metaRepository.getPatches(env, bundle)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = metaRepository.getPatches(TftEnvironment.LIVE, initialBundle)
    )

    private fun buildEnvironmentSnapshot(
        env: TftEnvironment = _selectedEnvironment.value,
        bundle: OfflineMetaBundle = _metaBundle.value,
        offlineFallback: Boolean = _isOfflineFallback.value
    ): EnvironmentSnapshot {
        val prefetchStatus = PrefetchTelemetryStore.status.value
        return EnvironmentSnapshot(
            environment = env,
            comps = metaRepository.getComps(env, bundle),
            champions = metaRepository.getChampions(env, bundle),
            items = metaRepository.getItems(env, bundle),
            augments = metaRepository.getAugments(env, bundle),
            patches = metaRepository.getPatches(env, bundle),
            cacheStatus = bundle.toCacheStatus(isOfflineFallback = offlineFallback, environment = env).copy(
                prefetchedAssetsCount = prefetchStatus.totalReadyCount,
                totalPrefetchAssets = prefetchStatus.totalPlannedCount,
                isPrefetchingAssets = prefetchStatus.isRunning
            )
        )
    }

    private fun refreshActiveEnvSnapshot() {
        _activeEnvSnapshot.value = buildEnvironmentSnapshot()
    }

    private val _activeEnvSnapshot = MutableStateFlow(
        buildEnvironmentSnapshot(
            env = TftEnvironment.LIVE,
            bundle = initialBundle,
            offlineFallback = false
        )
    )
    val activeEnvSnapshot: StateFlow<EnvironmentSnapshot> = _activeEnvSnapshot.asStateFlow()

    init {
        viewModelScope.launch {
            combine(_metaBundle, _isOfflineFallback, PrefetchTelemetryStore.status) { bundle, offlineFallback, _ ->
                bundle to offlineFallback
            }.collect { (bundle, offlineFallback) ->
                _activeEnvSnapshot.value = buildEnvironmentSnapshot(
                    env = _selectedEnvironment.value,
                    bundle = bundle,
                    offlineFallback = offlineFallback
                )
            }
        }
        viewModelScope.launch {
            val loaded = metaRepository.loadOfflineBundleAsync()
            _metaBundle.value = loaded
            _liveVersionInfo.value = loaded.liveVersionInfo
            refreshActiveEnvSnapshot()
            appContext?.let { AppUpdateCoordinator.refreshSettingsSnapshot(it) }
        }
        observeBackgroundCacheUpdates()
        if (autoSyncOnInit) {
            refreshPatchVersions(
                notifyIfUpdated = true,
                onlyNotifyOnChanges = true,
                triggerPushAlerts = false
            )
        }
        if (autoCheckAppUpdateOnInit && appContext != null) {
            checkForAppUpdate(userInitiated = false, dispatchOsNotification = false)
        }
        if (!currentUserId.isNullOrBlank()) {
            migrateLocalFavoritesToCloudIfPresent()
        }
    }

    fun onAppResumed() {
        val ctx = appContext
        if (ctx != null) {
            viewModelScope.launch {
                AppUpdateCoordinator.onResumeHandlePendingInstall(ctx)
            }
        }
        // Auto-check for App Updates when opening/returning to the app (throttled in coordinator),
        // while keeping Comp Updates strictly manual via user button press.
        if (autoCheckAppUpdateOnInit && ctx != null) {
            checkForAppUpdate(userInitiated = false, dispatchOsNotification = false)
        }
    }

    fun checkForAppUpdate(
        userInitiated: Boolean = true,
        dispatchOsNotification: Boolean = false
    ) {
        val ctx = appContext ?: return
        viewModelScope.launch {
            val outcome = AppUpdateCoordinator.checkForUpdates(
                context = ctx,
                userInitiated = userInitiated,
                showInAppModalOnLaunch = true,
                dispatchOsNotification = dispatchOsNotification
            )
            if (outcome.updateAvailable && outcome.release != null && (userInitiated || outcome.inAppNotificationTriggered)) {
                _snackbarMessage.value = "App update available: ${outcome.release.displayVersionLabel} — tap Update to install in-app."
            } else if (!outcome.updateAvailable && userInitiated) {
                val repo = AppUpdateCoordinator.uiState.value.configuredRepo
                if (repo != null && AppUpdateCoordinator.uiState.value.status !is com.example.update.AppUpdateStatus.Error) {
                    _snackbarMessage.value = "TFT's Helper is up to date."
                }
            }
        }
    }

    fun saveGithubRepo(rawInput: String) {
        val ctx = appContext ?: return
        viewModelScope.launch {
            val saved = AppUpdateCoordinator.saveGithubRepoSlug(ctx, rawInput)
            if (saved == null) {
                _snackbarMessage.value = "Enter a valid GitHub repo like owner/repo or https://github.com/owner/repo"
            } else {
                _snackbarMessage.value = "Saved update source: github.com/$saved"
                checkForAppUpdate(userInitiated = true)
            }
        }
    }

    fun setAppUpdateAlertsEnabled(enabled: Boolean) {
        val ctx = appContext ?: return
        viewModelScope.launch {
            AppUpdateCoordinator.setAppUpdateAlertsEnabled(ctx, enabled)
        }
    }

    fun downloadAndInstallAppUpdate(release: GitHubReleaseInfo) {
        val ctx = appContext ?: return
        viewModelScope.launch {
            val started = AppUpdateCoordinator.downloadAndInstallRelease(ctx, release)
            val status = AppUpdateCoordinator.uiState.value.status
            if (!started && status is com.example.update.AppUpdateStatus.Error) {
                _snackbarMessage.value = status.message
            } else if (status is com.example.update.AppUpdateStatus.ReadyToInstall && status.needsInstallPermission) {
                _snackbarMessage.value = "Allow 'Install unknown apps' for TFT's Helper, then return to finish installing."
            }
        }
    }

    fun installReadyAppUpdate() {
        val ctx = appContext ?: return
        val ready = AppUpdateCoordinator.uiState.value.status as? com.example.update.AppUpdateStatus.ReadyToInstall
            ?: return
        viewModelScope.launch {
            val ok = AppUpdateCoordinator.launchApkInstaller(ctx, ready.apkFilePath, ready.release)
            if (!ok) {
                val err = (AppUpdateCoordinator.uiState.value.status as? com.example.update.AppUpdateStatus.Error)?.message
                if (!err.isNullOrBlank()) {
                    _snackbarMessage.value = err
                }
            }
        }
    }

    fun openInstallPermissionSettings() {
        val ctx = appContext ?: return
        val opened = AppUpdateCoordinator.openInstallPermissionSettings(ctx)
        if (!opened) {
            _snackbarMessage.value = "Unable to open Android install permission settings."
        }
    }

    fun openAppUpdateDestination(release: GitHubReleaseInfo? = null) {
        val ctx = appContext ?: return
        val opened = AppUpdateCoordinator.openStoreOrReleaseUpdate(ctx, release)
        if (!opened) {
            _snackbarMessage.value = "Unable to open Google Play or browser for this update."
        }
    }

    fun openGitHubReleasePage(release: GitHubReleaseInfo? = null) {
        val ctx = appContext ?: return
        val opened = AppUpdateCoordinator.openGitHubReleaseUpdate(ctx, release)
        if (!opened) {
            _snackbarMessage.value = "Unable to open GitHub Releases in browser."
        }
    }

    fun openPlayStorePage() {
        val ctx = appContext ?: return
        val opened = AppUpdateCoordinator.openPlayStoreUpdate(ctx)
        if (!opened) {
            _snackbarMessage.value = "Unable to open Google Play Store."
        }
    }

    fun clearLocalAppData() {
        viewModelScope.launch {
            localStore.clearAllLocalData()
            _themeMode.value = ThemeMode.DARK
            _snackbarMessage.value = "Cleared local preferences, favorites, and overlay settings."
        }
    }

    fun deleteCloudAccountAndData(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val result = firestoreRepo.deleteAllUserDataAndAccount()
            if (result.isSuccess) {
                localStore.clearAllLocalData()
                _themeMode.value = ThemeMode.DARK
                _snackbarMessage.value = "Account and cloud favorites permanently deleted."
                onDeleted()
            } else {
                _snackbarMessage.value = result.exceptionOrNull()?.localizedMessage
                    ?: "Could not delete account. Please sign in again and retry."
            }
        }
    }

    fun openAppUpdateDialog() {
        AppUpdateCoordinator.openPromptModalIfAvailable()
    }

    fun dismissAppUpdateDialog(suppressThisReleaseOnLaunch: Boolean = true) {
        val ctx = appContext ?: return
        viewModelScope.launch {
            AppUpdateCoordinator.dismissPromptModal(ctx, suppressThisReleaseOnLaunch)
        }
    }

    private fun observeBackgroundCacheUpdates() {
        viewModelScope.launch {
            MetaCacheUpdates.lastCacheWriteMillis.collect { ts ->
                if (ts > 0L) {
                    val fresh = metaRepository.peekOfflineBundle()
                    if (fresh !== _metaBundle.value && fresh.liveComps.isNotEmpty()) {
                        _metaBundle.value = fresh
                        _liveVersionInfo.value = fresh.liveVersionInfo
                        _isOfflineFallback.value = false
                    } else if (fresh.liveComps.isEmpty()) {
                        val loaded = metaRepository.loadOfflineBundleAsync()
                        _metaBundle.value = loaded
                        _liveVersionInfo.value = loaded.liveVersionInfo
                        _isOfflineFallback.value = false
                    }
                }
            }
        }
    }

    private fun migrateLocalFavoritesToCloudIfPresent() {
        viewModelScope.launch {
            val localFavs = runCatching { localStore.localFavoritesFlow.first() }.getOrNull().orEmpty()
            if (localFavs.isEmpty()) return@launch
            localFavs.forEach { doc ->
                val result = firestoreRepo.saveFavoriteComp(doc.toMetaComp(), doc.pinnedToOverlay)
                if (result.isSuccess) {
                    localStore.removeLocalFavorite(doc.compId)
                }
            }
        }
    }

    fun refreshPatchVersions(
        notifyIfUpdated: Boolean = false,
        onlyNotifyOnChanges: Boolean = false,
        triggerPushAlerts: Boolean = false
    ) {
        if (activeSyncJob?.isActive == true) return
        activeSyncJob = viewModelScope.launch {
            try {
                _isRefreshingVersions.value = true
                val sync = metaRepository.refreshAndPersistMeta()
                val freshBundle = metaRepository.loadOfflineBundleAsync()
                _metaBundle.value = freshBundle
                _liveVersionInfo.value = sync.versionInfo
                _isOfflineFallback.value = sync.cacheStatus.isOfflineFallback

                if (triggerPushAlerts && appContext != null) {
                    MetaUpdateNotifier.dispatchNotifications(
                        context = appContext,
                        syncResult = sync,
                        bundle = freshBundle
                    )
                } else if (appContext != null && sync.syncedFromNetwork) {
                    MetaUpdateNotifier.markCurrentSnapshotAsSeen(
                        bundle = freshBundle,
                        prefsStore = localStore
                    )
                }

                if (appContext != null && sync.syncedFromNetwork) {
                    val hasDelta = sync.newLivePatchDetected ||
                        sync.newPbePatchDetected ||
                        sync.compsUpdatedDetected ||
                        sync.newlyAddedComps.isNotEmpty()
                    viewModelScope.launch(Dispatchers.IO) {
                        MetaPrefetchScheduler.enqueuePrefetch(
                            context = appContext,
                            scope = if (hasDelta) PrefetchScope.DELTA_SYNC else PrefetchScope.ALL,
                            refreshSnapshot = false,
                            replaceExisting = hasDelta
                        )
                    }
                }

                if (notifyIfUpdated) {
                    val updates = buildList {
                        if (sync.newLivePatchDetected) add("LIVE ${sync.versionInfo.livePatchVersion}")
                        if (sync.newPbePatchDetected) add("PBE ${sync.versionInfo.pbePatchVersion}")
                        if (sync.newlyAddedComps.isNotEmpty()) {
                            add("${sync.newlyAddedComps.size} new comp(s)")
                        } else if (sync.compsUpdatedDetected) {
                            add("Comps updated")
                        }
                    }
                    if (updates.isNotEmpty()) {
                        _snackbarMessage.value = "Updated LIVE & PBE meta: ${updates.joinToString(", ")}"
                    } else if (!onlyNotifyOnChanges) {
                        _snackbarMessage.value = if (sync.syncedFromNetwork) {
                            "Synced latest LIVE & PBE patches and compositions."
                        } else {
                            "Offline mode — using persistent device cache."
                        }
                    }
                }
            } finally {
                _isRefreshingVersions.value = false
            }
        }
    }

    fun setEnvironment(env: TftEnvironment) {
        hasUserExplicitlySelectedEnv = true
        if (_selectedEnvironment.value == env) return
        _selectedEnvironment.value = env
        refreshActiveEnvSnapshot()
        val shouldPrefetch = appContext != null && prefetchedEnvsInSession.add(env)
        envPersistJob?.cancel()
        envPersistJob = viewModelScope.launch(Dispatchers.IO) {
            if (shouldPrefetch && appContext != null) {
                MetaPrefetchScheduler.enqueuePrefetch(
                    context = appContext,
                    scope = if (env == TftEnvironment.PBE) PrefetchScope.PBE else PrefetchScope.LIVE,
                    refreshSnapshot = false,
                    warmMemoryCount = 24,
                    replaceExisting = false
                )
            }
            localStore.updatePreferences(environment = env)
            if (!currentUserId.isNullOrBlank()) {
                val p = preferencesState.value
                firestoreRepo.upsertPreferences(
                    themeMode = ThemeMode.fromRaw(p.themeMode),
                    selectedEnvironment = env,
                    patchAlertsEnabled = p.patchAlertsEnabled,
                    pbeAlertsEnabled = p.pbeAlertsEnabled,
                    balanceAlertsEnabled = p.balanceAlertsEnabled,
                    trendingAlertsEnabled = p.trendingAlertsEnabled,
                    lastSeenLivePatch = p.lastSeenLivePatch,
                    lastSeenPbePatch = p.lastSeenPbePatch
                )
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        viewModelScope.launch {
            localStore.updatePreferences(themeMode = mode)
            if (!currentUserId.isNullOrBlank()) {
                val p = preferencesState.value
                firestoreRepo.upsertPreferences(
                    themeMode = mode,
                    selectedEnvironment = TftEnvironment.fromRaw(p.selectedEnvironment),
                    patchAlertsEnabled = p.patchAlertsEnabled,
                    pbeAlertsEnabled = p.pbeAlertsEnabled,
                    balanceAlertsEnabled = p.balanceAlertsEnabled,
                    trendingAlertsEnabled = p.trendingAlertsEnabled,
                    lastSeenLivePatch = p.lastSeenLivePatch,
                    lastSeenPbePatch = p.lastSeenPbePatch
                )
            }
        }
    }

    fun updateAlertSettings(
        patchAlertsEnabled: Boolean? = null,
        pbeAlertsEnabled: Boolean? = null,
        balanceAlertsEnabled: Boolean? = null,
        trendingAlertsEnabled: Boolean? = null
    ) {
        viewModelScope.launch {
            localStore.updatePreferences(
                patchAlertsEnabled = patchAlertsEnabled,
                pbeAlertsEnabled = pbeAlertsEnabled,
                balanceAlertsEnabled = balanceAlertsEnabled,
                trendingAlertsEnabled = trendingAlertsEnabled
            )
            if (!currentUserId.isNullOrBlank()) {
                val p = preferencesState.value
                val result = firestoreRepo.upsertPreferences(
                    themeMode = ThemeMode.fromRaw(p.themeMode),
                    selectedEnvironment = TftEnvironment.fromRaw(p.selectedEnvironment),
                    patchAlertsEnabled = patchAlertsEnabled ?: p.patchAlertsEnabled,
                    pbeAlertsEnabled = pbeAlertsEnabled ?: p.pbeAlertsEnabled,
                    balanceAlertsEnabled = balanceAlertsEnabled ?: p.balanceAlertsEnabled,
                    trendingAlertsEnabled = trendingAlertsEnabled ?: p.trendingAlertsEnabled,
                    lastSeenLivePatch = p.lastSeenLivePatch,
                    lastSeenPbePatch = p.lastSeenPbePatch
                )
                if (result.isFailure) {
                    _snackbarMessage.value = "Saved locally; cloud sync error: ${result.exceptionOrNull()?.localizedMessage}"
                }
            }
        }
    }

    fun toggleFavorite(comp: MetaComp) {
        val currentFavs = (favoritesState.value as? UiState.Success)?.data.orEmpty()
        val isAlreadyFav = currentFavs.any { it.compId == comp.id || it.id == comp.id }

        viewModelScope.launch {
            if (currentUserId.isNullOrBlank()) {
                if (isAlreadyFav) {
                    localStore.removeLocalFavorite(comp.id)
                    _snackbarMessage.value = "Removed '${comp.title}' from favorites"
                } else {
                    localStore.saveLocalFavorite(
                        FavoriteCompDoc(
                            id = comp.id,
                            userId = "local",
                            compId = comp.id,
                            environment = comp.environment.name,
                            title = comp.title,
                            tier = comp.tier.name,
                            playstyle = comp.playstyle,
                            carryName = comp.carryName,
                            coreUnits = comp.coreUnits,
                            hexPositions = comp.hexPlacements.map { it.encode() },
                            pinnedToOverlay = true,
                            notes = comp.notes.takeIf { it.isNotBlank() }
                        )
                    )
                    _snackbarMessage.value = "Saved '${comp.title}' & pinned to Live Overlay"
                }
            } else {
                val result = if (isAlreadyFav) {
                    firestoreRepo.removeFavorite(comp.id)
                } else {
                    firestoreRepo.saveFavoriteComp(comp, pinnedToOverlay = true)
                }
                result.onSuccess {
                    _snackbarMessage.value = if (isAlreadyFav) {
                        "Removed '${comp.title}' from cloud favorites"
                    } else {
                        "Synced '${comp.title}' & pinned to Live Overlay"
                    }
                }.onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Failed to update favorites"
                }
            }
        }
    }

    fun toggleOverlayPin(compId: String, pinned: Boolean) {
        viewModelScope.launch {
            if (currentUserId.isNullOrBlank()) {
                localStore.setLocalFavoritePinned(compId, pinned)
            } else {
                firestoreRepo.toggleOverlayPin(compId, pinned).onFailure { err ->
                    _snackbarMessage.value = err.localizedMessage ?: "Failed to update overlay pin"
                }
            }
        }
    }

    fun saveCustomFavorite(
        title: String,
        environment: TftEnvironment,
        tier: TierRank,
        playstyle: String,
        carryName: String,
        unitsCsv: String,
        notes: String
    ) {
        val cleanTitle = title.trim()
        if (cleanTitle.isBlank()) {
            _snackbarMessage.value = "Composition name cannot be empty."
            return
        }
        val unitList = unitsCsv.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(10)
            .ifEmpty { listOf(carryName.trim().ifBlank { "Carry" }) }

        val placements = unitList.mapIndexed { idx, name ->
            val row = if (idx < unitList.size / 2) 0 else 3
            val col = (idx * 2) % 7
            HexUnitPlacement(
                unitName = name,
                row = row,
                col = col,
                cost = 4,
                isCarry = name.equals(carryName.trim(), ignoreCase = true)
            )
        }

        val customComp = MetaComp(
            id = "custom_${environment.name.lowercase()}_${System.currentTimeMillis()}",
            environment = environment,
            title = cleanTitle,
            tier = tier,
            playstyle = playstyle.trim().ifBlank { "Flex" },
            avgPlacement = 3.9,
            top4Rate = 58.0,
            winRate = 14.0,
            pickRate = 1.0,
            trendDelta = "CUSTOM",
            isTrending = false,
            patchTag = if (environment == TftEnvironment.PBE) {
                _liveVersionInfo.value?.pbePatchVersion ?: "18.7-PBE"
            } else {
                _liveVersionInfo.value?.livePatchVersion ?: "18.6b"
            },
            carryName = carryName.trim().ifBlank { unitList.first() },
            traits = emptyList(),
            coreUnits = unitList,
            hexPlacements = placements,
            augmentPriorities = emptyList(),
            levelingGuide = notes.trim(),
            notes = notes.trim()
        )

        viewModelScope.launch {
            val resolvedCode = appContext?.let { ctx ->
                runCatching {
                    TftTeamPlannerCodeEncoder.resolveTeamCode(ctx, customComp)
                }.getOrNull()?.takeIf { it.isSuccess }?.code.orEmpty()
            }.orEmpty()
            val enrichedCustomComp = if (resolvedCode.isNotBlank()) {
                customComp.copy(teamPlannerCode = resolvedCode)
            } else {
                customComp
            }
            if (currentUserId.isNullOrBlank()) {
                localStore.saveLocalFavorite(
                    FavoriteCompDoc(
                        id = enrichedCustomComp.id,
                        userId = "local",
                        compId = enrichedCustomComp.id,
                        environment = enrichedCustomComp.environment.name,
                        title = enrichedCustomComp.title,
                        tier = enrichedCustomComp.tier.name,
                        playstyle = enrichedCustomComp.playstyle,
                        carryName = enrichedCustomComp.carryName,
                        coreUnits = enrichedCustomComp.coreUnits,
                        hexPositions = enrichedCustomComp.hexPlacements.map { it.encode() },
                        pinnedToOverlay = true,
                        notes = enrichedCustomComp.notes.takeIf { it.isNotBlank() }
                    )
                )
                _snackbarMessage.value = "Created & pinned '${enrichedCustomComp.title}'"
            } else {
                firestoreRepo.saveFavoriteComp(enrichedCustomComp, pinnedToOverlay = true)
                    .onSuccess { _snackbarMessage.value = "Synced & pinned '${enrichedCustomComp.title}'" }
                    .onFailure { _snackbarMessage.value = it.localizedMessage ?: "Failed to save composition" }
            }
        }
    }

    fun copyCompTeamCode(comp: MetaComp) {
        val ctx = appContext ?: run {
            _snackbarMessage.value = "Clipboard unavailable"
            return
        }
        copiedCompClearJob?.cancel()
        if (comp.teamPlannerCode.isBlank()) {
            _snackbarMessage.value = "Preparing ${comp.environment.label} Team Planner code for ${comp.title}…"
        }
        viewModelScope.launch {
            val result = TftTeamPlannerCodeEncoder.resolveTeamCode(ctx, comp)
            if (result.isSuccess) {
                val copiedOk = withContext(Dispatchers.Main.immediate) {
                    runCatching {
                        val clipboard = ctx.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            ?: return@runCatching false
                        clipboard.setPrimaryClip(ClipData.newPlainText("TFT Team Planner Code", result.code))
                        true
                    }.getOrDefault(false)
                }
                if (copiedOk) {
                    _copiedCompId.value = comp.id
                    _snackbarMessage.value =
                        "Copied ${comp.environment.label} (${result.setKey}) code • ${result.matchedChampions.size} units • Paste in TFT"
                    copiedCompClearJob?.cancel()
                    copiedCompClearJob = viewModelScope.launch {
                        delay(3000L)
                        if (_copiedCompId.value == comp.id) {
                            _copiedCompId.value = null
                        }
                    }
                } else {
                    _copiedCompId.value = null
                    _snackbarMessage.value = "Could not write ${comp.environment.label} code to clipboard"
                }
            } else {
                _copiedCompId.value = null
                _snackbarMessage.value =
                    result.errorMessage ?: "Could not load Riot ${result.setKey} IDs — check connection"
            }
        }
    }

    fun showMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    fun consumeSnackbar() {
        _snackbarMessage.value = null
    }

    private companion object {
        const val TAG = "MainViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
