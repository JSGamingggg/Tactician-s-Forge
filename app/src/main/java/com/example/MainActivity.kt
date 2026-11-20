package com.example

import android.Manifest
import android.content.Intent
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MilitaryTech
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.model.TftEnvironment
import com.example.data.model.ThemeMode
import com.example.data.repository.FirestoreUserRepository
import com.example.data.repository.LocalPreferencesStore
import com.example.data.repository.MetaRepository
import com.example.notifications.PatchMonitorWorker
import com.example.notifications.TftNotificationHelper
import com.example.overlay.OverlayController
import com.example.ui.auth.GoogleAuthHelper
import com.example.ui.components.AppUpdateInAppBanner
import com.example.ui.components.AppUpdatePromptDialog
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.AuthWelcomeScreen
import com.example.ui.screens.ChampionTierListScreen
import com.example.ui.screens.MetaSnapshotsScreen
import com.example.ui.screens.PatchesScreen
import com.example.ui.theme.TacticiansForgeTheme
import com.example.ui.util.DisplayRefreshRateHelper
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.UiState
import com.example.update.AppUpdateStatus
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

enum class AppTab(
    val key: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    META("meta", R.string.nav_meta, Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
    TIERS("tiers", R.string.nav_tiers, Icons.Filled.MilitaryTech, Icons.Outlined.MilitaryTech),
    PATCHES("patches", R.string.nav_patches, Icons.AutoMirrored.Filled.Article, Icons.AutoMirrored.Outlined.Article),
    ABOUT("about", R.string.nav_about, Icons.Filled.Info, Icons.Outlined.Info);

    companion object {
        fun fromKey(key: String?): AppTab = when (key?.lowercase()) {
            "tiers" -> TIERS
            "patches" -> PATCHES
            "about", "alerts" -> ABOUT
            else -> META
        }
    }
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
    private var openAppUpdateSignal by mutableStateOf(false)
    private var displayManager: DisplayManager? = null
    private val displayRefreshListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            DisplayRefreshRateHelper.applyToActivity(this@MainActivity)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        DisplayRefreshRateHelper.applyToActivity(this)
        displayManager = getSystemService(DISPLAY_SERVICE) as? DisplayManager
        runCatching {
            displayManager?.registerDisplayListener(
                displayRefreshListener,
                Handler(Looper.getMainLooper())
            )
        }
        val initialTab = intent?.getStringExtra(TftNotificationHelper.EXTRA_NAV_TAB)
        val initialEnv = intent?.getStringExtra(TftNotificationHelper.EXTRA_ENVIRONMENT)
        openAppUpdateSignal = intent?.getBooleanExtra(TftNotificationHelper.EXTRA_OPEN_APP_UPDATE, false) == true
        setContent {
            AppRoot(
                initialTabKey = initialTab,
                initialEnvironmentKey = initialEnv,
                openAppUpdateOnLaunch = openAppUpdateSignal,
                onConsumedAppUpdateSignal = { openAppUpdateSignal = false }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        DisplayRefreshRateHelper.applyToActivity(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            DisplayRefreshRateHelper.applyToActivity(this)
        }
    }

    override fun onDestroy() {
        runCatching { displayManager?.unregisterDisplayListener(displayRefreshListener) }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(TftNotificationHelper.EXTRA_OPEN_APP_UPDATE, false)) {
            openAppUpdateSignal = true
        }
    }
}

@Composable
fun AppRoot(
    initialTabKey: String?,
    initialEnvironmentKey: String?,
    openAppUpdateOnLaunch: Boolean = false,
    onConsumedAppUpdateSignal: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isFirebaseConfigured = remember(context) {
        GoogleAuthHelper.isFirebaseConfigured(context)
    }
    val auth = remember(context, isFirebaseConfigured) {
        runCatching {
            if (isFirebaseConfigured) Firebase.auth else null
        }.getOrNull()
    }
    val localStore = remember(context) { LocalPreferencesStore(context.applicationContext) }
    val currentUser by (auth?.authStateFlow() ?: flowOf(null))
        .collectAsStateWithLifecycle(initialValue = auth?.currentUser)
    val isGuestMode by localStore.isGuestModeFlow.collectAsStateWithLifecycle(initialValue = null)
    val prefs by localStore.preferencesFlow.collectAsStateWithLifecycle(initialValue = null)
    var attemptedAutoSignIn by rememberSaveable { mutableStateOf(false) }
    var requestedNotifPermissionOnLaunch by rememberSaveable { mutableStateOf(false) }
    val startupNotifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { _ ->
            PatchMonitorWorker.schedulePeriodicAndChainedBackgroundChecks(context)
        }
    )

    LaunchedEffect(Unit) {
        PatchMonitorWorker.schedulePeriodicAndChainedBackgroundChecks(context)
        if (!requestedNotifPermissionOnLaunch &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !TftNotificationHelper.hasNotificationPermission(context)
        ) {
            requestedNotifPermissionOnLaunch = true
            runCatching {
                startupNotifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(currentUser, isGuestMode, isFirebaseConfigured) {
        if (isFirebaseConfigured && currentUser == null && isGuestMode == false && !attemptedAutoSignIn) {
            attemptedAutoSignIn = true
            val cm = CredentialManager.create(context)
            GoogleAuthHelper.attemptAutoSignIn(
                context = context,
                credentialManager = cm,
                scope = scope,
                onAuthSuccess = {},
                onUnauthenticated = {}
            )
        }
    }

    when {
        isFirebaseConfigured && currentUser == null && isGuestMode == null -> {
            TacticiansForgeTheme(themeMode = ThemeMode.fromRaw(prefs?.themeMode)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                )
            }
        }
        isFirebaseConfigured && currentUser == null && isGuestMode == false -> {
            TacticiansForgeTheme(themeMode = ThemeMode.fromRaw(prefs?.themeMode)) {
                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing
                ) { innerPadding ->
                    AuthWelcomeScreen(
                        onExploreAsGuest = {
                            scope.launch { localStore.setGuestModeChoice(true) }
                        },
                        onAuthSuccess = {
                            scope.launch { localStore.setGuestModeChoice(false) }
                        },
                        onAuthError = {},
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
        else -> {
            val sessionKey = currentUser?.uid ?: "guest_local_session"
            key(sessionKey) {
                AuthenticatedOrGuestWorkspace(
                    currentUser = currentUser,
                    localStore = localStore,
                    initialTabKey = initialTabKey,
                    initialEnvironmentKey = initialEnvironmentKey,
                    openAppUpdateOnLaunch = openAppUpdateOnLaunch,
                    onConsumedAppUpdateSignal = onConsumedAppUpdateSignal
                )
            }
        }
    }
}

@Composable
private fun AuthenticatedOrGuestWorkspace(
    currentUser: FirebaseUser?,
    localStore: LocalPreferencesStore,
    initialTabKey: String?,
    initialEnvironmentKey: String?,
    openAppUpdateOnLaunch: Boolean,
    onConsumedAppUpdateSignal: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val uid = currentUser?.uid
    val sessionKey = uid ?: "guest_local_session"

    val mainViewModel: MainViewModel = viewModel(
        key = "main_$sessionKey",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                MainViewModel(
                    currentUserId = uid,
                    metaRepository = MetaRepository(app),
                    localStore = LocalPreferencesStore(app),
                    firestoreRepo = FirestoreUserRepository(app),
                    appContext = app.applicationContext
                )
            }
        }
    )


    val themeMode by mainViewModel.themeMode.collectAsStateWithLifecycle()
    val activeEnvSnapshot by mainViewModel.activeEnvSnapshot.collectAsStateWithLifecycle()
    val environment = activeEnvSnapshot.environment
    val metaComps = activeEnvSnapshot.comps
    val championTiers = activeEnvSnapshot.champions
    val itemTiers = activeEnvSnapshot.items
    val augmentTiers = activeEnvSnapshot.augments
    val patchFeed = activeEnvSnapshot.patches
    val offlineCacheStatus = activeEnvSnapshot.cacheStatus
    val favoritesState by mainViewModel.favoritesState.collectAsStateWithLifecycle()
    val preferences by mainViewModel.preferencesState.collectAsStateWithLifecycle()
    val liveVersionInfo by mainViewModel.liveVersionInfo.collectAsStateWithLifecycle()
    val isRefreshingVersions by mainViewModel.isRefreshingVersions.collectAsStateWithLifecycle()
    val snackbarMessage by mainViewModel.snackbarMessage.collectAsStateWithLifecycle()
    val copiedCompId by mainViewModel.copiedCompId.collectAsStateWithLifecycle()
    val appUpdateState by mainViewModel.appUpdateState.collectAsStateWithLifecycle()

    val hasPendingAppUpdate = appUpdateState.status is AppUpdateStatus.UpdateAvailable ||
        appUpdateState.status is AppUpdateStatus.Downloading ||
        appUpdateState.status is AppUpdateStatus.ReadyToInstall

    val isOverlayRunning by OverlayController.isRunning.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable {
        mutableStateOf(AppTab.fromKey(initialTabKey))
    }


    // Automatically check for newly released patches & team comps when returning to the app,
    // and ensure background monitoring is scheduled whenever the app moves to the background.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, mainViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mainViewModel.onAppResumed()
                Lifecycle.Event.ON_STOP -> PatchMonitorWorker.schedulePeriodicAndChainedBackgroundChecks(context)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(initialEnvironmentKey) {
        if (!initialEnvironmentKey.isNullOrBlank()) {
            mainViewModel.setEnvironment(TftEnvironment.fromRaw(initialEnvironmentKey))
        }
    }

    LaunchedEffect(openAppUpdateOnLaunch, appUpdateState.status) {
        if (openAppUpdateOnLaunch) {
            if (hasPendingAppUpdate) {
                mainViewModel.openAppUpdateDialog()
                onConsumedAppUpdateSignal()
            } else {
                mainViewModel.checkForAppUpdate(userInitiated = true)
                onConsumedAppUpdateSignal()
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            mainViewModel.consumeSnackbar()
        }
    }

    val triggerGoogleSignIn = {
        GoogleAuthHelper.onGoogleSignInClicked(
            context = context,
            credentialManager = credentialManager,
            scope = scope,
            onAuthSuccess = {
                scope.launch { localStore.setGuestModeChoice(false) }
                mainViewModel.showMessage("Signed in with Google! Syncing favorites to Firestore.")
            },
            onAuthError = { msg -> mainViewModel.showMessage(msg) },
            onAuthCancelled = {}
        )
    }

    val toggleOverlayAction = {
        if (isOverlayRunning) {
            OverlayController.stop(context)
            mainViewModel.showMessage("Live Match Overlay closed.")
        } else if (!OverlayController.canDrawOverlays(context)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            context.startActivity(intent)
            mainViewModel.showMessage("Grant 'Display over other apps' permission to launch the floating overlay.")
        } else {
            OverlayController.start(context)
            mainViewModel.showMessage("Floating Hextech Overlay active! Tap the bubble anytime.")
        }
    }

    TacticiansForgeTheme(themeMode = themeMode) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavRail = maxWidth >= 600.dp
            val compactRailLabels = maxHeight >= 360.dp

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (!useNavRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 6.dp
                        ) {
                            val navItemColors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            AppTab.entries.forEach { tab ->
                                val selected = currentTab == tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    colors = navItemColors,
                                    icon = {
                                        val navIcon = if (selected) tab.selectedIcon else tab.unselectedIcon
                                        if (tab == AppTab.ABOUT && hasPendingAppUpdate) {
                                            BadgedBox(badge = { Badge() }) {
                                                Icon(
                                                    imageVector = navIcon,
                                                    contentDescription = stringResource(tab.labelRes)
                                                )
                                            }
                                        } else {
                                            Icon(
                                                imageVector = navIcon,
                                                contentDescription = stringResource(tab.labelRes)
                                            )
                                        }
                                    },
                                    label = { Text(stringResource(tab.labelRes), maxLines = 1) },
                                    modifier = Modifier.testTag("nav_${tab.key}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useNavRail) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            val railColors = NavigationRailItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            AppTab.entries.forEach { tab ->
                                val selected = currentTab == tab
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    colors = railColors,
                                    alwaysShowLabel = compactRailLabels,
                                    icon = {
                                        val railIcon = if (selected) tab.selectedIcon else tab.unselectedIcon
                                        if (tab == AppTab.ABOUT && hasPendingAppUpdate) {
                                            BadgedBox(badge = { Badge() }) {
                                                Icon(
                                                    imageVector = railIcon,
                                                    contentDescription = stringResource(tab.labelRes)
                                                )
                                            }
                                        } else {
                                            Icon(
                                                imageVector = railIcon,
                                                contentDescription = stringResource(tab.labelRes)
                                            )
                                        }
                                    },
                                    label = { Text(stringResource(tab.labelRes), maxLines = 1) },
                                    modifier = Modifier.testTag("nav_${tab.key}")
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                    ) {
                        AppUpdateInAppBanner(
                            updateState = appUpdateState,
                            onOpenDetails = mainViewModel::openAppUpdateDialog,
                            onStartUpdate = mainViewModel::downloadAndInstallAppUpdate,
                            onInstallReadyApk = mainViewModel::installReadyAppUpdate,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            val contentModifier = Modifier.fillMaxSize()
                            val tabStateHolder = rememberSaveableStateHolder()
                        Crossfade(
                            targetState = currentTab,
                            animationSpec = tween(durationMillis = 135),
                            label = "mainTabCrossfade"
                        ) { tab ->
                            tabStateHolder.SaveableStateProvider(tab.key) {
                                when (tab) {
                                     AppTab.META -> MetaSnapshotsScreen(
                                        environment = environment,
                                        onSelectEnvironment = mainViewModel::setEnvironment,
                                        comps = metaComps,
                                        favoritesState = favoritesState,
                                        liveVersionInfo = liveVersionInfo,
                                        offlineCacheStatus = offlineCacheStatus,
                                        isRefreshing = isRefreshingVersions,
                                        isCheckingAppUpdate = appUpdateState.status is AppUpdateStatus.Checking,
                                        isGuestMode = currentUser == null,
                                        isOverlayRunning = isOverlayRunning,
                                        onRefreshSync = {
                                            mainViewModel.refreshPatchVersions(notifyIfUpdated = true)
                                        },
                                        onCheckAppUpdates = {
                                            mainViewModel.checkForAppUpdate(userInitiated = true)
                                        },
                                        onSignInRequested = triggerGoogleSignIn,
                                        onToggleOverlay = toggleOverlayAction,
                                        onToggleFavorite = mainViewModel::toggleFavorite,
                                        onTogglePin = mainViewModel::toggleOverlayPin,
                                        onCreateCustomComp = mainViewModel::saveCustomFavorite,
                                        copiedCompId = copiedCompId,
                                        onCopyCompCode = mainViewModel::copyCompTeamCode,
                                        patches = patchFeed,
                                        champions = championTiers,
                                        items = itemTiers,
                                        modifier = contentModifier
                                    )
                                    AppTab.TIERS -> {
                                        val currentFavorites = (favoritesState as? UiState.Success)?.data.orEmpty()
                                        val favIds = remember(currentFavorites) {
                                            currentFavorites.map { it.compId.ifBlank { it.id } }.toSet()
                                        }
                                        val pinIds = remember(currentFavorites) {
                                            currentFavorites.filter { it.pinnedToOverlay }.map { it.compId.ifBlank { it.id } }.toSet()
                                        }
                                        ChampionTierListScreen(
                                            environment = environment,
                                            onSelectEnvironment = mainViewModel::setEnvironment,
                                            champions = championTiers,
                                            items = itemTiers,
                                            augments = augmentTiers,
                                            liveVersionInfo = liveVersionInfo,
                                            offlineCacheStatus = offlineCacheStatus,
                                            isRefreshing = isRefreshingVersions,
                                            isGuestMode = currentUser == null,
                                            onRefreshSync = {
                                                mainViewModel.refreshPatchVersions(notifyIfUpdated = true)
                                            },
                                            onSignInRequested = triggerGoogleSignIn,
                                            patches = patchFeed,
                                            comps = metaComps,
                                            favoriteIds = favIds,
                                            pinnedIds = pinIds,
                                            onToggleFavorite = mainViewModel::toggleFavorite,
                                            onTogglePin = mainViewModel::toggleOverlayPin,
                                            copiedCompId = copiedCompId,
                                            onCopyCompCode = mainViewModel::copyCompTeamCode,
                                            modifier = contentModifier
                                        )
                                    }

                                    AppTab.PATCHES -> PatchesScreen(
                                        environment = environment,
                                        onSelectEnvironment = mainViewModel::setEnvironment,
                                        patches = patchFeed,
                                        liveVersionInfo = liveVersionInfo,
                                        isRefreshingVersions = isRefreshingVersions,
                                        onRefreshVersions = { mainViewModel.refreshPatchVersions(notifyIfUpdated = true) },
                                        modifier = contentModifier
                                    )

                                    AppTab.ABOUT -> AboutScreen(
                                        environment = environment,
                                        patches = patchFeed,
                                        preferences = preferences,
                                        themeMode = themeMode,
                                        isOverlayRunning = isOverlayRunning,
                                        currentUser = currentUser,
                                        appUpdateState = appUpdateState,
                                        onCheckAppUpdate = { mainViewModel.checkForAppUpdate(userInitiated = true) },
                                        onSaveGithubRepo = mainViewModel::saveGithubRepo,
                                        onToggleAppUpdateAlerts = mainViewModel::setAppUpdateAlertsEnabled,
                                        onStartAppUpdate = mainViewModel::downloadAndInstallAppUpdate,
                                        onOpenAppUpdateDialog = mainViewModel::openAppUpdateDialog,
                                        onUpdateAlertSettings = mainViewModel::updateAlertSettings,
                                        onSetThemeMode = mainViewModel::setThemeMode,
                                        onToggleOverlay = toggleOverlayAction,
                                        onShowMessage = mainViewModel::showMessage,
                                        onAuthSuccess = {
                                            scope.launch { localStore.setGuestModeChoice(false) }
                                            mainViewModel.showMessage("Signed in with Google! Syncing favorites to Firestore.")
                                        },
                                        onAuthError = { msg -> mainViewModel.showMessage(msg) },
                                        onSignedOut = {
                                            scope.launch { localStore.setGuestModeChoice(false) }
                                            mainViewModel.showMessage("Signed out — choose Explore as Guest or Sign in.")
                                        },
                                        modifier = contentModifier
                                    )
                                }
                            }
                        }
                        }
                    }
                }
            }
        }

        AppUpdatePromptDialog(
            updateState = appUpdateState,
            onDismissLater = { mainViewModel.dismissAppUpdateDialog(suppressThisReleaseOnLaunch = true) },
            onDownloadAndInstall = mainViewModel::downloadAndInstallAppUpdate,
            onInstallReadyApk = mainViewModel::installReadyAppUpdate
        )
    }
}
