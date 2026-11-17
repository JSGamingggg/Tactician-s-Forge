package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.MilitaryTech
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
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
import com.example.data.repository.RiotTftRepository
import com.example.notifications.TftNotificationHelper
import com.example.overlay.OverlayController
import com.example.ui.auth.GoogleAuthHelper
import com.example.ui.screens.AuthWelcomeScreen
import com.example.ui.screens.ChampionTierListScreen
import com.example.ui.screens.MetaSnapshotsScreen
import com.example.ui.screens.PatchAlertsScreen
import com.example.ui.screens.PlayerStatsScreen
import com.example.ui.theme.TacticiansForgeTheme
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.PlayerViewModel
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
    STATS("stats", R.string.nav_stats, Icons.Filled.PersonSearch, Icons.Outlined.PersonSearch),
    ALERTS("alerts", R.string.nav_alerts, Icons.Filled.Notifications, Icons.Outlined.Notifications)
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initialTab = intent?.getStringExtra(TftNotificationHelper.EXTRA_NAV_TAB)
        val initialEnv = intent?.getStringExtra(TftNotificationHelper.EXTRA_ENVIRONMENT)
        setContent {
            AppRoot(
                initialTabKey = initialTab,
                initialEnvironmentKey = initialEnv
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

@Composable
fun AppRoot(
    initialTabKey: String?,
    initialEnvironmentKey: String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = remember(context) {
        runCatching {
            if (FirebaseApp.getApps(context).isNotEmpty()) Firebase.auth else null
        }.getOrNull()
    }
    val localStore = remember(context) { LocalPreferencesStore(context.applicationContext) }
    val currentUser by (auth?.authStateFlow() ?: flowOf(null))
        .collectAsStateWithLifecycle(initialValue = auth?.currentUser)
    val isGuestMode by localStore.isGuestModeFlow.collectAsStateWithLifecycle(initialValue = null)
    val prefs by localStore.preferencesFlow.collectAsStateWithLifecycle(initialValue = null)
    var attemptedAutoSignIn by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(currentUser, isGuestMode) {
        if (currentUser == null && isGuestMode == false && !attemptedAutoSignIn) {
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
        currentUser == null && isGuestMode == null -> {
            TacticiansForgeTheme(themeMode = ThemeMode.fromRaw(prefs?.themeMode)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                )
            }
        }
        currentUser == null && isGuestMode == false -> {
            val authSnackbarHostState = remember { SnackbarHostState() }
            TacticiansForgeTheme(themeMode = ThemeMode.fromRaw(prefs?.themeMode)) {
                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(hostState = authSnackbarHostState) }
                ) { innerPadding ->
                    AuthWelcomeScreen(
                        onExploreAsGuest = {
                            scope.launch { localStore.setGuestModeChoice(true) }
                        },
                        onAuthSuccess = {
                            scope.launch { localStore.setGuestModeChoice(false) }
                        },
                        onAuthError = { msg ->
                            scope.launch { authSnackbarHostState.showSnackbar(msg) }
                        },
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
                    initialEnvironmentKey = initialEnvironmentKey
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
    initialEnvironmentKey: String?
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

    val playerViewModel: PlayerViewModel = viewModel(
        key = "player_$sessionKey",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                PlayerViewModel(
                    currentUserId = uid,
                    riotRepo = RiotTftRepository(),
                    localStore = LocalPreferencesStore(app),
                    firestoreRepo = FirestoreUserRepository(app)
                )
            }
        }
    )

    val themeMode by mainViewModel.themeMode.collectAsStateWithLifecycle()
    val environment by mainViewModel.selectedEnvironment.collectAsStateWithLifecycle()
    val metaComps by mainViewModel.metaComps.collectAsStateWithLifecycle()
    val championTiers by mainViewModel.championTiers.collectAsStateWithLifecycle()
    val itemTiers by mainViewModel.itemTiers.collectAsStateWithLifecycle()
    val augmentTiers by mainViewModel.augmentTiers.collectAsStateWithLifecycle()
    val patchFeed by mainViewModel.patchFeed.collectAsStateWithLifecycle()
    val favoritesState by mainViewModel.favoritesState.collectAsStateWithLifecycle()
    val preferences by mainViewModel.preferencesState.collectAsStateWithLifecycle()
    val liveVersionInfo by mainViewModel.liveVersionInfo.collectAsStateWithLifecycle()
    val offlineCacheStatus by mainViewModel.offlineCacheStatus.collectAsStateWithLifecycle()
    val isRefreshingVersions by mainViewModel.isRefreshingVersions.collectAsStateWithLifecycle()
    val snackbarMessage by mainViewModel.snackbarMessage.collectAsStateWithLifecycle()

    val lookupState by playerViewModel.lookupState.collectAsStateWithLifecycle()
    val selectedPlatform by playerViewModel.selectedPlatform.collectAsStateWithLifecycle()
    val trackedPlayers by playerViewModel.trackedPlayers.collectAsStateWithLifecycle()
    val selectedMatch by playerViewModel.selectedMatch.collectAsStateWithLifecycle()
    val isOverlayRunning by OverlayController.isRunning.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable {
        mutableStateOf(
            AppTab.entries.firstOrNull { it.key == initialTabKey } ?: AppTab.META
        )
    }

    // Automatically check for newly released patches & team comps when returning to the app
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, mainViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                mainViewModel.onAppResumed()
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
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            AppTab.entries.forEach { tab ->
                                val selected = currentTab == tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(tab.labelRes)
                                        )
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
                            AppTab.entries.forEach { tab ->
                                val selected = currentTab == tab
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    alwaysShowLabel = compactRailLabels,
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(tab.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(tab.labelRes), maxLines = 1) },
                                    modifier = Modifier.testTag("nav_${tab.key}")
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        val contentModifier = Modifier
                            .widthIn(max = 880.dp)
                            .fillMaxSize()
                        when (currentTab) {
                            AppTab.META -> MetaSnapshotsScreen(
                                environment = environment,
                                onSelectEnvironment = mainViewModel::setEnvironment,
                                comps = metaComps,
                                favoritesState = favoritesState,
                                liveVersionInfo = liveVersionInfo,
                                offlineCacheStatus = offlineCacheStatus,
                                isRefreshing = isRefreshingVersions,
                                isGuestMode = currentUser == null,
                                isOverlayRunning = isOverlayRunning,
                                onRefreshSync = { mainViewModel.refreshPatchVersions(notifyIfUpdated = true) },
                                onSignInRequested = triggerGoogleSignIn,
                                onToggleOverlay = toggleOverlayAction,
                                onToggleFavorite = mainViewModel::toggleFavorite,
                                onTogglePin = mainViewModel::toggleOverlayPin,
                                onCreateCustomComp = mainViewModel::saveCustomFavorite,
                                modifier = contentModifier
                            )
                            AppTab.TIERS -> ChampionTierListScreen(
                                environment = environment,
                                onSelectEnvironment = mainViewModel::setEnvironment,
                                champions = championTiers,
                                items = itemTiers,
                                augments = augmentTiers,
                                liveVersionInfo = liveVersionInfo,
                                offlineCacheStatus = offlineCacheStatus,
                                isRefreshing = isRefreshingVersions,
                                isGuestMode = currentUser == null,
                                onRefreshSync = { mainViewModel.refreshPatchVersions(notifyIfUpdated = true) },
                                onSignInRequested = triggerGoogleSignIn,
                                modifier = contentModifier
                            )
                            AppTab.STATS -> PlayerStatsScreen(
                                lookupState = lookupState,
                                selectedPlatform = selectedPlatform,
                                isApiKeyConfigured = playerViewModel.isApiKeyConfigured,
                                trackedPlayers = trackedPlayers,
                                selectedMatch = selectedMatch,
                                onSelectPlatform = playerViewModel::selectPlatform,
                                onSearchPlayer = playerViewModel::searchPlayer,
                                onBookmarkPlayer = playerViewModel::bookmarkCurrentPlayer,
                                onRemoveTrackedPlayer = playerViewModel::removeTrackedPlayer,
                                onOpenMatchAnalysis = playerViewModel::openMatchAnalysis,
                                onDismissMatchAnalysis = playerViewModel::closeMatchAnalysis,
                                modifier = contentModifier
                            )
                            AppTab.ALERTS -> PatchAlertsScreen(
                                environment = environment,
                                onSelectEnvironment = mainViewModel::setEnvironment,
                                patches = patchFeed,
                                preferences = preferences,
                                liveVersionInfo = liveVersionInfo,
                                isRefreshingVersions = isRefreshingVersions,
                                themeMode = themeMode,
                                isOverlayRunning = isOverlayRunning,
                                currentUser = currentUser,
                                onRefreshVersions = { mainViewModel.refreshPatchVersions(notifyIfUpdated = true) },
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
