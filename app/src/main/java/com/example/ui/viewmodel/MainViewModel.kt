package com.example.ui.viewmodel

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
import com.example.update.AppUpdateCoordinator
import com.example.update.AppUpdateUiState
import com.example.update.GitHubReleaseInfo
import com.google.firebase.firestore.FirebaseFirestoreException
import java.io.File
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

class MainViewModel(
    private val currentUserId: String?, // null = Guest Mode (local-only, 0 Firestore queries)
    private val metaRepository: MetaRepository,
    private val localStore: LocalPreferencesStore,
    private val firestoreRepo: FirestoreUserRepository,
    autoSyncOnInit: Boolean = true,
    private val appContext: Context? = null
) : ViewModel() {

    private var lastAutoSyncAttemptMillis = 0L
    private var activeSyncJob: Job? = null

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
        combine(_selectedEnvironment, _metaBundle, _isOfflineFallback) { env, bundle, offlineFallback ->
            bundle.toCacheStatus(isOfflineFallback = offlineFallback, environment = env)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = initialBundle.toCacheStatus(environment = TftEnvironment.LIVE)
        )

    private val _isRefreshingVersions = MutableStateFlow(false)
    val isRefreshingVersions: StateFlow<Boolean> = _isRefreshingVersions.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    val appUpdateState: StateFlow<AppUpdateUiState> = AppUpdateCoordinator.uiState

    val preferencesState: StateFlow<UserPreferencesDoc> =
        if (currentUserId.isNullOrBlank()) {
            localStore.preferencesFlow
                .onEach { prefs ->
                    _themeMode.value = ThemeMode.fromRaw(prefs.themeMode)
                    _selectedEnvironment.value = TftEnvironment.fromRaw(prefs.selectedEnvironment)
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
                    _selectedEnvironment.value = TftEnvironment.fromRaw(prefs.selectedEnvironment)
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

    init {
        viewModelScope.launch {
            val loaded = metaRepository.loadOfflineBundleAsync()
            _metaBundle.value = loaded
            _liveVersionInfo.value = loaded.liveVersionInfo
            appContext?.let { AppUpdateCoordinator.refreshSettingsSnapshot(it) }
        }
        observeBackgroundCacheUpdates()
        if (autoSyncOnInit) {
            refreshPatchVersions(
                notifyIfUpdated = true,
                onlyNotifyOnChanges = true,
                triggerPushAlerts = true
            )
            checkForAppUpdate(userInitiated = false)
            startActivePollingLoop()
        }
        if (!currentUserId.isNullOrBlank()) {
            migrateLocalFavoritesToCloudIfPresent()
        }
    }

    private fun startActivePollingLoop() {
        viewModelScope.launch {
            while (isActive) {
                delay(ACTIVE_SYNC_INTERVAL_MS)
                refreshPatchVersions(
                    notifyIfUpdated = true,
                    onlyNotifyOnChanges = true,
                    triggerPushAlerts = true
                )
                checkForAppUpdate(userInitiated = false)
            }
        }
    }

    fun onAppResumed() {
        val now = System.currentTimeMillis()
        appContext?.let { ctx ->
            viewModelScope.launch {
                AppUpdateCoordinator.resumePendingInstallIfPermitted(ctx)
            }
        }
        if (now - lastAutoSyncAttemptMillis < RESUME_DEBOUNCE_MS) return
        refreshPatchVersions(
            notifyIfUpdated = true,
            onlyNotifyOnChanges = true,
            triggerPushAlerts = true
        )
        checkForAppUpdate(userInitiated = false)
    }

    fun checkForAppUpdate(userInitiated: Boolean = true) {
        val ctx = appContext ?: return
        viewModelScope.launch {
            val outcome = AppUpdateCoordinator.checkForUpdates(
                context = ctx,
                userInitiated = userInitiated,
                showInAppModalOnLaunch = true,
                dispatchOsNotification = true
            )
            if (outcome.updateAvailable && outcome.release != null && (userInitiated || outcome.inAppNotificationTriggered)) {
                _snackbarMessage.value = "App update available: ${outcome.release.displayVersionLabel} — tap Update to install in-app."
            } else if (!outcome.updateAvailable && userInitiated) {
                val repo = AppUpdateCoordinator.uiState.value.configuredRepo
                if (repo != null && AppUpdateCoordinator.uiState.value.status !is com.example.update.AppUpdateStatus.Error) {
                    _snackbarMessage.value = "Tactician's Forge is up to date with github.com/$repo."
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
            AppUpdateCoordinator.downloadAndInstallUpdate(ctx, release)
        }
    }

    fun installReadyAppUpdate(apkFile: File, release: GitHubReleaseInfo) {
        val ctx = appContext ?: return
        viewModelScope.launch {
            AppUpdateCoordinator.launchPackageInstaller(ctx, apkFile, release)
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
                lastAutoSyncAttemptMillis = System.currentTimeMillis()
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
        _selectedEnvironment.value = env
        viewModelScope.launch {
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
            if (currentUserId.isNullOrBlank()) {
                localStore.saveLocalFavorite(
                    FavoriteCompDoc(
                        id = customComp.id,
                        userId = "local",
                        compId = customComp.id,
                        environment = customComp.environment.name,
                        title = customComp.title,
                        tier = customComp.tier.name,
                        playstyle = customComp.playstyle,
                        carryName = customComp.carryName,
                        coreUnits = customComp.coreUnits,
                        hexPositions = customComp.hexPlacements.map { it.encode() },
                        pinnedToOverlay = true,
                        notes = customComp.notes.takeIf { it.isNotBlank() }
                    )
                )
                _snackbarMessage.value = "Created & pinned '${customComp.title}'"
            } else {
                firestoreRepo.saveFavoriteComp(customComp, pinnedToOverlay = true)
                    .onSuccess { _snackbarMessage.value = "Synced & pinned '${customComp.title}'" }
                    .onFailure { _snackbarMessage.value = it.localizedMessage ?: "Failed to save composition" }
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
        const val ACTIVE_SYNC_INTERVAL_MS = 60_000L
        const val RESUME_DEBOUNCE_MS = 5_000L
    }
}
