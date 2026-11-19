package com.example.overlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.R
import com.example.data.model.FavoriteCompDoc
import com.example.data.model.HexUnitPlacement
import com.example.data.model.MetaComp
import com.example.data.model.TftEnvironment
import com.example.data.model.TierRank
import com.example.data.repository.FirestoreUserRepository
import com.example.data.repository.LocalPreferencesStore
import com.example.data.repository.MetaCacheUpdates
import com.example.data.repository.MetaRepository
import com.example.notifications.MetaUpdateNotifier
import com.example.notifications.TftNotificationHelper
import com.example.ui.components.TftAugmentChip
import com.example.ui.components.TftChampionAvatar
import com.example.ui.components.TftChampionTile
import com.example.ui.components.TftItemChip
import com.example.ui.components.TftItemIcon
import com.example.ui.components.TftTraitChip
import com.example.ui.components.TierBadge
import com.example.ui.components.championCostColor
import com.example.ui.components.formatRiotAssetLabel
import com.example.ui.components.sanitizeExternalSourceMentions
import com.example.ui.screens.groupCompsByTier
import com.example.ui.theme.ArcaneCyan
import com.example.ui.theme.BrightGold
import com.example.ui.theme.BrushedGold
import com.example.ui.theme.DeepSlate
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.PbePurple
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.SlateOutline
import com.example.ui.theme.TacticiansForgeTheme
import com.example.ui.theme.TierAColor
import com.example.ui.theme.TierBColor
import com.example.ui.theme.TierCColor
import com.example.ui.theme.TierSColor
import com.example.ui.theme.TierXColor
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

object OverlayController {
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    internal fun setRunning(running: Boolean) {
        _isRunning.value = running
    }

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun start(context: Context) {
        if (!canDrawOverlays(context)) return
        val intent = Intent(context, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, OverlayService::class.java))
    }
}

enum class OverlayDisplayState {
    EDGE_COLLAPSED,
    BUBBLE,
    EXPANDED
}

enum class OverlayDockEdge {
    LEFT,
    RIGHT
}

data class OverlayEdgeSnapResult(
    val dockEdge: OverlayDockEdge,
    val shouldCollapseToEdge: Boolean
)

internal fun computeEdgeSnapDecision(
    leftEdgePx: Int,
    viewWidthPx: Int,
    screenWidthPx: Int,
    snapThresholdPx: Int
): OverlayEdgeSnapResult {
    val safeScreenWidth = screenWidthPx.coerceAtLeast(1)
    val safeViewWidth = viewWidthPx.coerceAtLeast(1)
    val leftGapPx = leftEdgePx.coerceAtLeast(0)
    val rightGapPx = (safeScreenWidth - (leftEdgePx + safeViewWidth)).coerceAtLeast(0)
    val centerX = leftEdgePx + safeViewWidth / 2
    val dockEdge = if (centerX < safeScreenWidth / 2) OverlayDockEdge.LEFT else OverlayDockEdge.RIGHT
    return OverlayEdgeSnapResult(
        dockEdge = dockEdge,
        shouldCollapseToEdge = minOf(leftGapPx, rightGapPx) <= snapThresholdPx
    )
}

data class OverlayWindowBounds(
    val widthPx: Int,
    val heightPx: Int,
    val safeLeftPx: Int = 0,
    val safeTopPx: Int = 0,
    val safeRightPx: Int = 0,
    val safeBottomPx: Int = 0,
    val density: Float = 1f
) {
    val usableWidthPx: Int get() = (widthPx - safeLeftPx - safeRightPx).coerceAtLeast(1)
    val usableHeightPx: Int get() = (heightPx - safeTopPx - safeBottomPx).coerceAtLeast(1)
}

internal fun resolveOverlayWindowBounds(
    windowManager: WindowManager?,
    resources: Resources
): OverlayWindowBounds {
    val density = resources.displayMetrics.density.coerceAtLeast(1f)
    if (windowManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val metrics = runCatching { windowManager.currentWindowMetrics }.getOrNull()
        if (metrics != null) {
            val bounds = metrics.bounds
            val visibleInsets = metrics.windowInsets.getInsets(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            val cutoutInsets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.displayCutout()
            )
            return OverlayWindowBounds(
                widthPx = bounds.width().coerceAtLeast(1),
                heightPx = bounds.height().coerceAtLeast(1),
                safeLeftPx = maxOf(visibleInsets.left, cutoutInsets.left).coerceAtLeast(0),
                safeTopPx = maxOf(visibleInsets.top, cutoutInsets.top).coerceAtLeast(0),
                safeRightPx = maxOf(visibleInsets.right, cutoutInsets.right).coerceAtLeast(0),
                safeBottomPx = maxOf(visibleInsets.bottom, cutoutInsets.bottom).coerceAtLeast(0),
                density = density
            )
        }
    }

    if (windowManager != null) {
        val realMetrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        val resolved = runCatching { windowManager.defaultDisplay?.getRealMetrics(realMetrics) }.isSuccess
        if (resolved && realMetrics.widthPixels > 0 && realMetrics.heightPixels > 0) {
            return OverlayWindowBounds(
                widthPx = realMetrics.widthPixels.coerceAtLeast(1),
                heightPx = realMetrics.heightPixels.coerceAtLeast(1),
                density = realMetrics.density.coerceAtLeast(1f)
            )
        }
    }

    val dm = resources.displayMetrics
    return OverlayWindowBounds(
        widthPx = dm.widthPixels.coerceAtLeast(1),
        heightPx = dm.heightPixels.coerceAtLeast(1),
        density = density
    )
}

internal fun resolveOverlayWindowScreenOrientation(
    @Suppress("UNUSED_PARAMETER") state: OverlayDisplayState
): Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

data class OverlayDrawerDimensions(
    val widthDp: Dp,
    val maxHeightDp: Dp,
    val isLandscape: Boolean,
    val useSideDetailPane: Boolean = widthDp >= 480.dp,
    val useTwoColumnCards: Boolean = widthDp >= 430.dp
)

internal fun computeOverlayDrawerDimensions(
    screenWidthDp: Int,
    screenHeightDp: Int
): OverlayDrawerDimensions {
    val safeW = screenWidthDp.coerceAtLeast(240)
    val safeH = screenHeightDp.coerceAtLeast(240)
    val isLandscape = safeW > safeH
    val widthDp = if (isLandscape) {
        // Adapt dynamically to take advantage of the full usable device width
        (safeW - 16).coerceAtLeast(320).dp
    } else {
        // Keep landscape card proportions (width > height) even if opened before display finishes rotating
        (safeW - 16).coerceAtLeast(260).dp
    }
    val maxHeightDp = if (isLandscape) {
        (safeH - 20).coerceIn(220, 480).dp
    } else {
        (widthDp.value * 0.78f)
            .roundToInt()
            .coerceIn(220, 320)
            .coerceAtMost((safeH - 48).coerceAtLeast(200))
            .dp
    }
    return OverlayDrawerDimensions(
        widthDp = widthDp,
        maxHeightDp = maxHeightDp,
        isLandscape = isLandscape,
        useSideDetailPane = widthDp >= 480.dp,
        useTwoColumnCards = widthDp >= 430.dp
    )
}

data class OverlaySizeBounds(
    val minWidthDp: Float,
    val minHeightDp: Float,
    val maxWidthDp: Float,
    val maxHeightDp: Float
)

internal fun resolveOverlaySizeBounds(
    screenWidthDp: Int,
    screenHeightDp: Int
): OverlaySizeBounds {
    val safeW = screenWidthDp.coerceAtLeast(240)
    val safeH = screenHeightDp.coerceAtLeast(240)
    val maxW = (safeW - 16).coerceAtLeast(240).toFloat()
    val maxH = (safeH - 16).coerceAtLeast(190).toFloat()
    val minW = 280f.coerceAtMost(maxW)
    val minH = 190f.coerceAtMost(maxH)
    return OverlaySizeBounds(
        minWidthDp = minW,
        minHeightDp = minH,
        maxWidthDp = maxW,
        maxHeightDp = maxH
    )
}

internal fun clampOverlayCustomSize(
    customWidthDp: Float?,
    customHeightDp: Float?,
    defaultDims: OverlayDrawerDimensions,
    bounds: OverlaySizeBounds
): Pair<Dp, Dp?> {
    val activeWidth = (customWidthDp ?: defaultDims.widthDp.value)
        .coerceIn(bounds.minWidthDp, bounds.maxWidthDp)
        .dp
    val activeHeight = customHeightDp?.coerceIn(bounds.minHeightDp, bounds.maxHeightDp)?.dp
    return activeWidth to activeHeight
}

internal fun computeLeftEdgeResizeResult(
    startOriginX: Int,
    startWidthDp: Float,
    deltaRawX: Float,
    density: Float,
    minLeftPx: Int,
    maxRightPx: Int,
    bounds: OverlaySizeBounds
): Pair<Float, Int> {
    val safeDensity = density.coerceAtLeast(1f)
    val fixedRightPx = (startOriginX + (startWidthDp * safeDensity).roundToInt()).coerceAtMost(maxRightPx)
    val desiredWidthDp = startWidthDp - (deltaRawX / safeDensity)
    val maxWidthFromRightPx = ((fixedRightPx - minLeftPx) / safeDensity).coerceAtLeast(bounds.minWidthDp)
    val clampedWidthDp = desiredWidthDp.coerceIn(bounds.minWidthDp, minOf(bounds.maxWidthDp, maxWidthFromRightPx))
    val newOriginX = (fixedRightPx - (clampedWidthDp * safeDensity).roundToInt()).coerceAtLeast(minLeftPx)
    return clampedWidthDp to newOriginX
}

class OverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private var displayManager: DisplayManager? = null
    private lateinit var prefsStore: LocalPreferencesStore
    private lateinit var metaRepository: MetaRepository
    private lateinit var firestoreRepo: FirestoreUserRepository
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var overlayView: ComposeView? = null
    private lateinit var overlayLayoutParams: WindowManager.LayoutParams

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    private val displayState = mutableStateOf(OverlayDisplayState.BUBBLE)
    private val dockEdgeState = mutableStateOf(OverlayDockEdge.LEFT)
    private val isDraggingState = mutableStateOf(false)
    private val customWidthDpState = mutableStateOf<Float?>(null)
    private val customHeightDpState = mutableStateOf<Float?>(null)
    private var savedExpandedOriginPx: Pair<Int, Int>? = null
    private val configurationVersion = mutableIntStateOf(0)
    private var savedBubbleAnchorY = 220
    private val allCompsState = mutableStateOf<List<MetaComp>>(emptyList())
    private val favoriteDocsState = mutableStateOf<List<FavoriteCompDoc>>(emptyList())
    private var favoritesObserverJob: Job? = null
    private var activeOverlaySyncJob: Job? = null
    private var windowSnapAnimator: ValueAnimator? = null

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            configurationVersion.intValue += 1
            clampOverlayPositionForCurrentBounds()
        }
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        prefsStore = LocalPreferencesStore(this)
        metaRepository = MetaRepository(this)
        firestoreRepo = FirestoreUserRepository(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        displayManager = (getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)?.also { dm ->
            dm.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
        }

        reloadCompsFromCache()
        observeCacheAndFavorites()
        startOverlayAutoSyncLoop()

        TftNotificationHelper.ensureChannels(this)
        startCompliantForeground()
        attachOverlayView()
        OverlayController.setRunning(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_OVERLAY) {
            stopSelf()
            return START_NOT_STICKY
        }
        reloadCompsFromCache()
        return START_STICKY
    }

    private fun reloadCompsFromCache() {
        serviceScope.launch {
            val bundle = metaRepository.loadOfflineBundleAsync()
            allCompsState.value = (bundle.liveComps + bundle.pbeComps)
                .sortedWith(compareBy<MetaComp> { it.environment.ordinal }.thenBy { it.tier.sortOrder }.thenBy { it.avgPlacement })
        }
    }

    private fun startOverlayAutoSyncLoop() {
        serviceScope.launch {
            while (isActive) {
                delay(ACTIVE_OVERLAY_SYNC_INTERVAL_MS)
                syncOverlayMetaFromNetwork()
            }
        }
    }

    private fun syncOverlayMetaFromNetwork() {
        if (activeOverlaySyncJob?.isActive == true) return
        activeOverlaySyncJob = serviceScope.launch {
            val sync = metaRepository.refreshAndPersistMeta()
            val bundle = metaRepository.loadOfflineBundleAsync()
            allCompsState.value = (bundle.liveComps + bundle.pbeComps)
                .sortedWith(compareBy<MetaComp> { it.environment.ordinal }.thenBy { it.tier.sortOrder }.thenBy { it.avgPlacement })
            MetaUpdateNotifier.dispatchNotifications(
                context = applicationContext,
                syncResult = sync,
                bundle = bundle
            )
        }
    }

    private fun observeCacheAndFavorites() {
        serviceScope.launch {
            MetaCacheUpdates.lastCacheWriteMillis.collectLatest {
                reloadCompsFromCache()
            }
        }

        favoritesObserverJob?.cancel()
        favoritesObserverJob = serviceScope.launch {
            val uid = runCatching {
                if (FirebaseApp.getApps(this@OverlayService).isNotEmpty()) Firebase.auth.currentUser?.uid else null
            }.getOrNull()
            if (uid.isNullOrBlank()) {
                prefsStore.localFavoritesFlow.collectLatest { docs ->
                    favoriteDocsState.value = docs
                    prefsStore.publishOverlayPinnedComps(docs)
                }
            } else {
                firestoreRepo.observeFavorites(uid)
                    .catch {
                        // Fall back to local pinned snapshots on transient error
                        val pinnedFallback = prefsStore.readOverlayPinnedComps().map { snap ->
                            FavoriteCompDoc(
                                id = snap.compId,
                                userId = uid,
                                compId = snap.compId,
                                environment = snap.environment,
                                title = snap.title,
                                tier = snap.tier,
                                playstyle = snap.playstyle,
                                carryName = snap.carryName,
                                coreUnits = snap.coreUnits,
                                hexPositions = snap.hexPositions,
                                pinnedToOverlay = true,
                                notes = snap.notes
                            )
                        }
                        emit(pinnedFallback)
                    }
                    .collectLatest { docs ->
                        favoriteDocsState.value = docs
                        prefsStore.publishOverlayPinnedComps(docs)
                    }
            }
        }
    }

    private fun toggleFavoriteInOverlay(comp: MetaComp) {
        val currentFavs = favoriteDocsState.value
        val isFav = currentFavs.any { it.compId == comp.id || it.id == comp.id }
        serviceScope.launch {
            val uid = runCatching {
                if (FirebaseApp.getApps(this@OverlayService).isNotEmpty()) Firebase.auth.currentUser?.uid else null
            }.getOrNull()
            if (uid.isNullOrBlank()) {
                if (isFav) {
                    prefsStore.removeLocalFavorite(comp.id)
                } else {
                    prefsStore.saveLocalFavorite(
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
                }
            } else {
                if (isFav) {
                    firestoreRepo.removeFavorite(comp.id)
                } else {
                    firestoreRepo.saveFavoriteComp(comp, pinnedToOverlay = true)
                }
            }
        }
    }

    private fun startCompliantForeground() {
        val openAppIntent = PendingIntent.getActivity(
            this,
            3001,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            3002,
            Intent(this, OverlayService::class.java).apply { action = ACTION_STOP_OVERLAY },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, TftNotificationHelper.CHANNEL_OVERLAY)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Live Match Overlay Active")
            .setContentText("Tap bubble to expand comps or drag/dock to screen edge while playing.")
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .addAction(0, getString(R.string.overlay_action_stop), stopIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun currentWindowBounds(): OverlayWindowBounds =
        resolveOverlayWindowBounds(windowManager = windowManager, resources = resources)

    private fun dpToPx(dp: Int): Int = (dp * currentWindowBounds().density).roundToInt()

    private fun currentSizeBounds(bounds: OverlayWindowBounds = currentWindowBounds()): OverlaySizeBounds {
        val widthDp = (bounds.usableWidthPx / bounds.density).roundToInt()
        val heightDp = (bounds.usableHeightPx / bounds.density).roundToInt()
        return resolveOverlaySizeBounds(screenWidthDp = widthDp, screenHeightDp = heightDp)
    }

    private fun currentDrawerDimensions(bounds: OverlayWindowBounds = currentWindowBounds()): OverlayDrawerDimensions {
        val widthDp = (bounds.usableWidthPx / bounds.density).roundToInt()
        val heightDp = (bounds.usableHeightPx / bounds.density).roundToInt()
        return computeOverlayDrawerDimensions(screenWidthDp = widthDp, screenHeightDp = heightDp)
    }

    private fun activeExpandedHeightDpValue(bounds: OverlayWindowBounds = currentWindowBounds()): Float {
        customHeightDpState.value?.let { return it }
        val measuredPx = overlayView?.height?.takeIf { it > 0 }
        if (measuredPx != null) {
            val sizeBounds = currentSizeBounds(bounds)
            return (measuredPx / bounds.density).coerceIn(sizeBounds.minHeightDp, sizeBounds.maxHeightDp)
        }
        return currentDrawerDimensions(bounds).maxHeightDp.value
    }

    private fun applyExpandedBoundsToWindow(
        widthDp: Float?,
        heightDp: Float?,
        targetX: Int? = null,
        targetY: Int? = null
    ) {
        if (!::overlayLayoutParams.isInitialized) return
        val bounds = currentWindowBounds()
        val sizeBounds = currentSizeBounds(bounds)
        val defaultDims = currentDrawerDimensions(bounds)
        val (clampedW, clampedH) = clampOverlayCustomSize(widthDp, heightDp, defaultDims, sizeBounds)

        customWidthDpState.value = widthDp?.let { clampedW.value }
        customHeightDpState.value = clampedH?.value

        val effectiveWpx = (clampedW.value * bounds.density).roundToInt()
        val effectiveHpx = ((clampedH?.value ?: defaultDims.maxHeightDp.value) * bounds.density).roundToInt()
        val minLeftX = bounds.safeLeftPx + dpToPx(8)
        val maxLeftX = (bounds.widthPx - bounds.safeRightPx - effectiveWpx - dpToPx(8)).coerceAtLeast(minLeftX)
        val minTopY = bounds.safeTopPx + dpToPx(8)
        val maxTopY = (bounds.heightPx - bounds.safeBottomPx - effectiveHpx - dpToPx(8)).coerceAtLeast(minTopY)

        overlayLayoutParams.gravity = Gravity.TOP or Gravity.START
        overlayLayoutParams.width = effectiveWpx
        overlayLayoutParams.height = if (clampedH != null) effectiveHpx else WindowManager.LayoutParams.WRAP_CONTENT
        val candidateX = targetX ?: savedExpandedOriginPx?.first ?: overlayLayoutParams.x
        val candidateY = targetY ?: savedExpandedOriginPx?.second ?: overlayLayoutParams.y
        val clampedX = candidateX.coerceIn(minLeftX, maxLeftX)
        val clampedY = candidateY.coerceIn(minTopY, maxTopY)
        overlayLayoutParams.x = clampedX
        overlayLayoutParams.y = clampedY
        if (savedExpandedOriginPx != null || targetX != null || targetY != null) {
            savedExpandedOriginPx = clampedX to clampedY
        }

        overlayView?.let { view ->
            if (view.isAttachedToWindow) {
                runCatching {
                    windowManager.updateViewLayout(view, overlayLayoutParams)
                    view.requestLayout()
                }
            }
        }
    }

    private fun extendExpandedToFullWidth() {
        if (!::overlayLayoutParams.isInitialized) return
        val bounds = currentWindowBounds()
        val sizeBounds = currentSizeBounds(bounds)
        val minLeftX = bounds.safeLeftPx + dpToPx(8)
        applyExpandedBoundsToWindow(
            widthDp = sizeBounds.maxWidthDp,
            heightDp = customHeightDpState.value,
            targetX = minLeftX,
            targetY = savedExpandedOriginPx?.second ?: overlayLayoutParams.y
        )
    }

    private fun resetExpandedSizeAndPosition() {
        customWidthDpState.value = null
        customHeightDpState.value = null
        savedExpandedOriginPx = null
        clampOverlayPositionForCurrentBounds()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        configurationVersion.intValue += 1
        clampOverlayPositionForCurrentBounds()
    }

    private fun clampOverlayPositionForCurrentBounds() {
        if (!::overlayLayoutParams.isInitialized) return
        val bounds = currentWindowBounds()
        val expandedMarginPx = dpToPx(8)
        val minY = bounds.safeTopPx + dpToPx(8)
        val maxCompactY = (bounds.heightPx - bounds.safeBottomPx - dpToPx(88)).coerceAtLeast(minY)
        val defaultDims = currentDrawerDimensions(bounds)
        val sizeBounds = currentSizeBounds(bounds)
        val (activeW, activeH) = clampOverlayCustomSize(
            customWidthDpState.value,
            customHeightDpState.value,
            defaultDims,
            sizeBounds
        )
        if (customWidthDpState.value != null) customWidthDpState.value = activeW.value
        if (customHeightDpState.value != null) customHeightDpState.value = activeH?.value

        val effectiveWpx = (activeW.value * bounds.density).roundToInt()
        val effectiveHpx = ((activeH?.value ?: defaultDims.maxHeightDp.value) * bounds.density).roundToInt()
        val minExpandedX = bounds.safeLeftPx + expandedMarginPx
        val maxExpandedX = (bounds.widthPx - bounds.safeRightPx - effectiveWpx - expandedMarginPx).coerceAtLeast(minExpandedX)
        val maxExpandedY = (bounds.heightPx - bounds.safeBottomPx - effectiveHpx - expandedMarginPx).coerceAtLeast(minY)

        savedBubbleAnchorY = savedBubbleAnchorY.coerceIn(minY, maxCompactY)
        overlayLayoutParams.screenOrientation = resolveOverlayWindowScreenOrientation(displayState.value)

        when (displayState.value) {
            OverlayDisplayState.EDGE_COLLAPSED -> {
                val edgeGravity = if (dockEdgeState.value == OverlayDockEdge.LEFT) Gravity.START else Gravity.END
                overlayLayoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT
                overlayLayoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT
                overlayLayoutParams.gravity = Gravity.TOP or edgeGravity
                overlayLayoutParams.x = if (dockEdgeState.value == OverlayDockEdge.LEFT) bounds.safeLeftPx else bounds.safeRightPx
                overlayLayoutParams.y = savedBubbleAnchorY
            }
            OverlayDisplayState.BUBBLE -> {
                val minX = bounds.safeLeftPx
                val maxX = (bounds.widthPx - bounds.safeRightPx - dpToPx(72)).coerceAtLeast(minX)
                overlayLayoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT
                overlayLayoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT
                overlayLayoutParams.x = overlayLayoutParams.x.coerceIn(minX, maxX)
                overlayLayoutParams.y = savedBubbleAnchorY
            }
            OverlayDisplayState.EXPANDED -> {
                overlayLayoutParams.gravity = Gravity.TOP or Gravity.START
                overlayLayoutParams.width = effectiveWpx
                overlayLayoutParams.height = if (activeH != null) effectiveHpx else WindowManager.LayoutParams.WRAP_CONTENT
                val preferredX = savedExpandedOriginPx?.first ?: minExpandedX
                val preferredY = savedExpandedOriginPx?.second ?: savedBubbleAnchorY
                overlayLayoutParams.x = preferredX.coerceIn(minExpandedX, maxExpandedX)
                overlayLayoutParams.y = preferredY.coerceIn(minY, maxExpandedY)
            }
        }
        overlayView?.let { view ->
            if (view.isAttachedToWindow) {
                runCatching {
                    windowManager.updateViewLayout(view, overlayLayoutParams)
                    view.requestLayout()
                }
            }
        }
    }

    private fun transitionDisplayState(
        targetState: OverlayDisplayState,
        dockEdge: OverlayDockEdge = dockEdgeState.value
    ) {
        val prevState = displayState.value
        if (prevState != OverlayDisplayState.EXPANDED) {
            savedBubbleAnchorY = overlayLayoutParams.y
        }

        dockEdgeState.value = dockEdge
        displayState.value = targetState
        overlayLayoutParams.screenOrientation = resolveOverlayWindowScreenOrientation(targetState)

        val bounds = currentWindowBounds()
        val edgeGravity = if (dockEdge == OverlayDockEdge.LEFT) Gravity.START else Gravity.END
        val edgeSafeMargin = if (dockEdge == OverlayDockEdge.LEFT) bounds.safeLeftPx else bounds.safeRightPx
        val marginPx = dpToPx(12)
        val expandedMarginPx = dpToPx(8)
        val minY = bounds.safeTopPx + dpToPx(8)
        val maxCompactY = (bounds.heightPx - bounds.safeBottomPx - dpToPx(88)).coerceAtLeast(minY)
        val defaultDims = currentDrawerDimensions(bounds)
        val sizeBounds = currentSizeBounds(bounds)
        val (activeW, activeH) = clampOverlayCustomSize(
            customWidthDpState.value,
            customHeightDpState.value,
            defaultDims,
            sizeBounds
        )
        val effectiveWpx = (activeW.value * bounds.density).roundToInt()
        val effectiveHpx = ((activeH?.value ?: defaultDims.maxHeightDp.value) * bounds.density).roundToInt()
        val minExpandedX = bounds.safeLeftPx + expandedMarginPx
        val maxExpandedX = (bounds.widthPx - bounds.safeRightPx - effectiveWpx - expandedMarginPx).coerceAtLeast(minExpandedX)
        val maxExpandedY = (bounds.heightPx - bounds.safeBottomPx - effectiveHpx - expandedMarginPx).coerceAtLeast(minY)

        when (targetState) {
            OverlayDisplayState.EDGE_COLLAPSED -> {
                overlayLayoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT
                overlayLayoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT
                overlayLayoutParams.gravity = Gravity.TOP or edgeGravity
                overlayLayoutParams.x = edgeSafeMargin
                overlayLayoutParams.y = savedBubbleAnchorY.coerceIn(minY, maxCompactY)
            }
            OverlayDisplayState.BUBBLE -> {
                overlayLayoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT
                overlayLayoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT
                overlayLayoutParams.gravity = Gravity.TOP or edgeGravity
                overlayLayoutParams.x = edgeSafeMargin + marginPx
                overlayLayoutParams.y = savedBubbleAnchorY.coerceIn(minY, maxCompactY)
            }
            OverlayDisplayState.EXPANDED -> {
                overlayLayoutParams.gravity = Gravity.TOP or Gravity.START
                overlayLayoutParams.width = effectiveWpx
                overlayLayoutParams.height = if (activeH != null) effectiveHpx else WindowManager.LayoutParams.WRAP_CONTENT
                val preferredX = savedExpandedOriginPx?.first ?: minExpandedX
                val preferredY = savedExpandedOriginPx?.second ?: savedBubbleAnchorY
                overlayLayoutParams.x = preferredX.coerceIn(minExpandedX, maxExpandedX)
                overlayLayoutParams.y = preferredY.coerceIn(minY, maxExpandedY)
            }
        }

        overlayView?.let { view ->
            if (view.isAttachedToWindow) {
                runCatching {
                    windowManager.updateViewLayout(view, overlayLayoutParams)
                    view.requestLayout()
                }
            }
        }
    }

    private fun animateOverlayXTo(
        view: ComposeView,
        fromX: Int,
        toX: Int,
        onComplete: () -> Unit = {}
    ) {
        windowSnapAnimator?.cancel()
        if (fromX == toX || !view.isAttachedToWindow) {
            onComplete()
            return
        }
        windowSnapAnimator = ValueAnimator.ofInt(fromX, toX).apply {
            duration = 210L
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { anim ->
                overlayLayoutParams.x = anim.animatedValue as Int
                if (view.isAttachedToWindow) {
                    runCatching { windowManager.updateViewLayout(view, overlayLayoutParams) }
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }
                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelled) onComplete()
                }
            })
            start()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachOverlayView() {
        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val initialBounds = currentWindowBounds()
        val initialMarginPx = dpToPx(12)
        savedBubbleAnchorY = (initialBounds.safeTopPx + dpToPx(28))
            .coerceIn(initialBounds.safeTopPx + dpToPx(8), (initialBounds.heightPx - initialBounds.safeBottomPx - dpToPx(88)).coerceAtLeast(initialBounds.safeTopPx + dpToPx(8)))
        overlayLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialBounds.safeLeftPx + initialMarginPx
            y = savedBubbleAnchorY
            screenOrientation = resolveOverlayWindowScreenOrientation(displayState.value)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)

            var startLeftPx = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f
            var moved = false
            var velocityTracker: VelocityTracker? = null
            val touchSlopPx = ViewConfiguration.get(this@OverlayService).scaledTouchSlop.coerceAtLeast(8)
            val edgeSnapThresholdPx = dpToPx(28)
            val uncollapseDragThresholdPx = dpToPx(30)

            setOnTouchListener { view, event ->
                val activeState = displayState.value
                if (activeState == OverlayDisplayState.EXPANDED) return@setOnTouchListener false

                val viewW = view.width.takeIf { it > 0 } ?: dpToPx(60)
                val bounds = currentWindowBounds()
                val minLeftX = bounds.safeLeftPx
                val maxLeftX = (bounds.widthPx - bounds.safeRightPx - viewW).coerceAtLeast(minLeftX)
                val minY = bounds.safeTopPx + dpToPx(8)
                val maxY = (bounds.heightPx - bounds.safeBottomPx - dpToPx(88)).coerceAtLeast(minY)

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        windowSnapAnimator?.cancel()
                        velocityTracker?.recycle()
                        velocityTracker = VelocityTracker.obtain().apply { addMovement(event) }
                        val isEndGravity = (overlayLayoutParams.gravity and Gravity.END) == Gravity.END
                        startLeftPx = if (isEndGravity) {
                            (bounds.widthPx - viewW - overlayLayoutParams.x).coerceIn(minLeftX, maxLeftX)
                        } else {
                            overlayLayoutParams.x.coerceIn(minLeftX, maxLeftX)
                        }
                        initialY = overlayLayoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        moved = false
                        isDraggingState.value = false
                        false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        velocityTracker?.addMovement(event)
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (abs(dx) > touchSlopPx || abs(dy) > touchSlopPx) {
                            moved = true
                            isDraggingState.value = true
                            val clampedY = (initialY + dy).coerceIn(minY, maxY)
                            savedBubbleAnchorY = clampedY

                            if (displayState.value == OverlayDisplayState.EDGE_COLLAPSED) {
                                val pulledAway = (dockEdgeState.value == OverlayDockEdge.LEFT && dx > uncollapseDragThresholdPx) ||
                                    (dockEdgeState.value == OverlayDockEdge.RIGHT && dx < -uncollapseDragThresholdPx)
                                if (pulledAway) {
                                    displayState.value = OverlayDisplayState.BUBBLE
                                    overlayLayoutParams.gravity = Gravity.TOP or Gravity.START
                                    overlayLayoutParams.x = (startLeftPx + dx).coerceIn(minLeftX, maxLeftX)
                                    overlayLayoutParams.y = clampedY
                                } else {
                                    // Stay flush to the docked safe edge while sliding vertically
                                    overlayLayoutParams.x = if (dockEdgeState.value == OverlayDockEdge.LEFT) bounds.safeLeftPx else bounds.safeRightPx
                                    overlayLayoutParams.y = clampedY
                                }
                            } else {
                                overlayLayoutParams.gravity = Gravity.TOP or Gravity.START
                                overlayLayoutParams.x = (startLeftPx + dx).coerceIn(minLeftX, maxLeftX)
                                overlayLayoutParams.y = clampedY
                            }
                            if (this.isAttachedToWindow) {
                                runCatching { windowManager.updateViewLayout(this, overlayLayoutParams) }
                            }
                        }
                        moved
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isDraggingState.value = false
                        velocityTracker?.addMovement(event)
                        velocityTracker?.computeCurrentVelocity(1000)
                        val vx = velocityTracker?.xVelocity ?: 0f
                        velocityTracker?.recycle()
                        velocityTracker = null

                        if (moved && displayState.value == OverlayDisplayState.BUBBLE) {
                            val currentLeft = overlayLayoutParams.x
                            val safeMarginLeft = minLeftX
                            val safeMarginRight = maxLeftX
                            val clampedTarget = currentLeft.coerceIn(safeMarginLeft, safeMarginRight)
                            if (clampedTarget != currentLeft) {
                                overlayLayoutParams.x = clampedTarget
                                if (this.isAttachedToWindow) {
                                    runCatching { windowManager.updateViewLayout(this, overlayLayoutParams) }
                                }
                            }
                        }
                        moved
                    }
                    else -> false
                }
            }

            setContent {
                TacticiansForgeTheme {
                    OverlayRootContent(
                        displayState = displayState.value,
                        dockEdge = dockEdgeState.value,
                        isDragging = isDraggingState.value,
                        configVersion = configurationVersion.intValue,
                        customWidthDp = customWidthDpState.value,
                        customHeightDp = customHeightDpState.value,
                        allComps = allCompsState.value,
                        favoriteDocs = favoriteDocsState.value,
                        windowOriginProvider = { overlayLayoutParams.x to overlayLayoutParams.y },
                        windowSizeProvider = {
                            val b = currentWindowBounds()
                            val activeW = customWidthDpState.value ?: currentDrawerDimensions(b).widthDp.value
                            val activeH = activeExpandedHeightDpValue(b)
                            activeW to activeH
                        },
                        densityProvider = { currentWindowBounds().density },
                        onMoveExpandedWindow = { nextX, nextY ->
                            applyExpandedBoundsToWindow(
                                widthDp = customWidthDpState.value,
                                heightDp = customHeightDpState.value,
                                targetX = nextX,
                                targetY = nextY
                            )
                        },
                        onResizeExpandedWindow = { nextW, nextH, nextX ->
                            applyExpandedBoundsToWindow(
                                widthDp = nextW,
                                heightDp = nextH,
                                targetX = nextX
                            )
                        },
                        onExtendFullWidth = ::extendExpandedToFullWidth,
                        onResetExpandedSize = ::resetExpandedSizeAndPosition,
                        onExpandDrawer = {
                            windowSnapAnimator?.cancel()
                            transitionDisplayState(OverlayDisplayState.EXPANDED)
                        },
                        onMinimizeToBubble = {
                            windowSnapAnimator?.cancel()
                            transitionDisplayState(OverlayDisplayState.BUBBLE)
                        },
                        onCollapseToEdge = {
                            windowSnapAnimator?.cancel()
                            transitionDisplayState(OverlayDisplayState.EDGE_COLLAPSED)
                        },
                        onRefresh = {
                            reloadCompsFromCache()
                            observeCacheAndFavorites()
                            syncOverlayMetaFromNetwork()
                        },
                        onToggleFavorite = ::toggleFavoriteInOverlay,
                        onCloseOverlay = { stopSelf() }
                    )
                }
            }
        }

        overlayView = composeView
        windowManager.addView(composeView, overlayLayoutParams)
    }

    override fun onDestroy() {
        super.onDestroy()
        displayManager?.unregisterDisplayListener(displayListener)
        displayManager = null
        windowSnapAnimator?.cancel()
        serviceScope.cancel()
        OverlayController.setRunning(false)
        overlayView?.let { view ->
            runCatching { view.disposeComposition() }
            if (view.isAttachedToWindow) {
                runCatching { windowManager.removeView(view) }
            }
        }
        overlayView = null
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }

    companion object {
        private const val NOTIFICATION_ID = 7701
        private const val ACTIVE_OVERLAY_SYNC_INTERVAL_MS = 60_000L
        const val ACTION_STOP_OVERLAY = "com.example.overlay.ACTION_STOP"
    }
}

private enum class OverlayBrowseMode {
    ALL_COMPS,
    FAVORITES
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun OverlayRootContent(
    displayState: OverlayDisplayState,
    dockEdge: OverlayDockEdge,
    isDragging: Boolean,
    configVersion: Int,
    customWidthDp: Float?,
    customHeightDp: Float?,
    allComps: List<MetaComp>,
    favoriteDocs: List<FavoriteCompDoc>,
    windowOriginProvider: () -> Pair<Int, Int>,
    windowSizeProvider: () -> Pair<Float, Float>,
    densityProvider: () -> Float,
    onMoveExpandedWindow: (Int, Int) -> Unit,
    onResizeExpandedWindow: (Float?, Float?, Int?) -> Unit,
    onExtendFullWidth: () -> Unit,
    onResetExpandedSize: () -> Unit,
    onExpandDrawer: () -> Unit,
    onMinimizeToBubble: () -> Unit,
    onCollapseToEdge: () -> Unit,
    onRefresh: () -> Unit,
    onToggleFavorite: (MetaComp) -> Unit,
    onCloseOverlay: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val (defaultDrawerDims, sizeBounds) = remember(
        configVersion,
        configuration.screenWidthDp,
        configuration.screenHeightDp,
        configuration.orientation
    ) {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val bounds = resolveOverlayWindowBounds(windowManager = wm, resources = context.resources)
        val wDp = (bounds.usableWidthPx / bounds.density).roundToInt().takeIf { it > 0 } ?: configuration.screenWidthDp
        val hDp = (bounds.usableHeightPx / bounds.density).roundToInt().takeIf { it > 0 } ?: configuration.screenHeightDp
        computeOverlayDrawerDimensions(screenWidthDp = wDp, screenHeightDp = hDp) to
            resolveOverlaySizeBounds(screenWidthDp = wDp, screenHeightDp = hDp)
    }
    val (activeWidthDp, activeHeightDp) = clampOverlayCustomSize(
        customWidthDp = customWidthDp,
        customHeightDp = customHeightDp,
        defaultDims = defaultDrawerDims,
        bounds = sizeBounds
    )
    val drawerDims = OverlayDrawerDimensions(
        widthDp = activeWidthDp,
        maxHeightDp = activeHeightDp ?: defaultDrawerDims.maxHeightDp,
        isLandscape = defaultDrawerDims.isLandscape,
        useSideDetailPane = activeWidthDp >= 480.dp,
        useTwoColumnCards = activeWidthDp >= 430.dp
    )
    val isCustomResized = customWidthDp != null || customHeightDp != null
    val favoriteIds = remember(favoriteDocs) {
        favoriteDocs.map { it.compId.ifBlank { it.id } }.toSet()
    }
    val mergedAllComps = remember(allComps, favoriteDocs) {
        val byId = LinkedHashMap<String, MetaComp>()
        allComps.forEach { byId[it.id] = it }
        favoriteDocs.forEach { fav ->
            val fid = fav.compId.ifBlank { fav.id }
            if (!byId.containsKey(fid)) {
                byId[fid] = fav.toMetaComp()
            }
        }
        byId.values.toList()
    }
    val dragScale by animateFloatAsState(
        targetValue = if (isDragging) 1.09f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "overlayDragScale"
    )

    AnimatedContent(
        targetState = displayState,
        transitionSpec = {
            (fadeIn(tween(180)) + scaleIn(initialScale = 0.94f, animationSpec = tween(180))) togetherWith
                (fadeOut(tween(120)) + scaleOut(targetScale = 0.94f, animationSpec = tween(120)))
        },
        label = "overlayDisplayStateTransition"
    ) { state ->
        when (state) {
            OverlayDisplayState.EDGE_COLLAPSED -> {
                val badgeCount = if (favoriteDocs.isNotEmpty()) favoriteDocs.size else mergedAllComps.size
                val isLeft = dockEdge == OverlayDockEdge.LEFT
                val tabShape = if (isLeft) {
                    RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 16.dp, bottomEnd = 16.dp)
                } else {
                    RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp, topEnd = 0.dp, bottomEnd = 0.dp)
                }
                val pullIcon = if (isLeft) {
                    Icons.AutoMirrored.Filled.KeyboardArrowRight
                } else {
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft
                }

                // 48dp accessible hit box while visible Hextech tab stays slim flush against screen edge
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(74.dp)
                        .graphicsLayer {
                            scaleX = dragScale
                            scaleY = dragScale
                        }
                        .clip(tabShape)
                        .clickable(onClick = onExpandDrawer)
                        .testTag("overlay_edge_tab"),
                    contentAlignment = if (isLeft) Alignment.CenterStart else Alignment.CenterEnd
                ) {
                    Surface(
                        shape = tabShape,
                        color = ObsidianVoid.copy(alpha = 0.90f),
                        border = BorderStroke(
                            width = 1.5.dp,
                            brush = Brush.verticalGradient(listOf(BrightGold, ArcaneCyan))
                        ),
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .width(30.dp)
                            .height(68.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Icon(
                                imageVector = pullIcon,
                                contentDescription = "Open edge-docked Live Match Overlay",
                                tint = BrightGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(14.dp)
                                    .background(
                                        Brush.verticalGradient(listOf(BrightGold, ArcaneCyan)),
                                        RoundedCornerShape(2.dp)
                                    )
                            )
                            if (badgeCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .size(17.dp)
                                        .background(ArcaneCyan, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = badgeCount.coerceAtMost(99).toString(),
                                        color = ObsidianVoid,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = ArcaneCyan,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            OverlayDisplayState.BUBBLE -> {
                val badgeCount = if (favoriteDocs.isNotEmpty()) favoriteDocs.size else mergedAllComps.size
                val collapseArrow = if (dockEdge == OverlayDockEdge.LEFT) {
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft
                } else {
                    Icons.AutoMirrored.Filled.KeyboardArrowRight
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.graphicsLayer {
                        scaleX = dragScale
                        scaleY = dragScale
                    }
                ) {
                    Surface(
                        onClick = onExpandDrawer,
                        shape = CircleShape,
                        color = ObsidianVoid.copy(alpha = 0.96f),
                        border = BorderStroke(
                            width = if (isDragging) 2.5.dp else 2.dp,
                            brush = Brush.linearGradient(
                                if (isDragging) listOf(ArcaneCyan, BrightGold) else listOf(BrightGold, ArcaneCyan)
                            )
                        ),
                        shadowElevation = if (isDragging) 16.dp else 10.dp,
                        modifier = Modifier
                            .size(58.dp)
                            .testTag("overlay_bubble_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "Expand Live Match Comps & Favorites Drawer",
                                tint = BrightGold,
                                modifier = Modifier.size(25.dp)
                            )
                            if (badgeCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(5.dp)
                                        .size(18.dp)
                                        .background(ArcaneCyan, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = badgeCount.coerceAtMost(99).toString(),
                                        color = ObsidianVoid,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Dedicated 1-tap Collapse-to-Edge handle underneath the bubble
                    Surface(
                        onClick = onCollapseToEdge,
                        shape = RoundedCornerShape(10.dp),
                        color = ObsidianVoid.copy(alpha = 0.90f),
                        border = BorderStroke(1.dp, BrushedGold.copy(alpha = 0.78f)),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .height(24.dp)
                            .testTag("overlay_bubble_collapse_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = collapseArrow,
                                contentDescription = "Collapse overlay to screen edge",
                                tint = BrightGold,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Dock",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF3F7FF)
                            )
                        }
                    }
                }
            }

            OverlayDisplayState.EXPANDED -> {
                var browseMode by remember { mutableStateOf(OverlayBrowseMode.ALL_COMPS) }
                var envFilter by remember { mutableStateOf(TftEnvironment.LIVE) }
                var tierFilter by remember { mutableStateOf<TierRank?>(null) }
                var selectedCompId by remember { mutableStateOf<String?>(null) }
                val collapseArrow = if (dockEdge == OverlayDockEdge.LEFT) {
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft
                } else {
                    Icons.AutoMirrored.Filled.KeyboardArrowRight
                }

                val favoriteComps = remember(mergedAllComps, favoriteDocs, favoriteIds) {
                    val matched = mergedAllComps.filter { favoriteIds.contains(it.id) }
                    val extra = favoriteDocs
                        .filter { doc -> matched.none { it.id == doc.compId || it.id == doc.id } }
                        .map { it.toMetaComp() }
                    matched + extra
                }

                val envScopedCompsCount = remember(mergedAllComps, envFilter) {
                    mergedAllComps.count { it.environment == envFilter }
                }
                val envScopedFavoritesCount = remember(favoriteComps, envFilter) {
                    favoriteComps.count { it.environment == envFilter }
                }

                val displayed = remember(mergedAllComps, favoriteComps, browseMode, envFilter, tierFilter) {
                    val source = if (browseMode == OverlayBrowseMode.FAVORITES) favoriteComps else mergedAllComps
                    source.filter { comp ->
                        val matchesEnv = comp.environment == envFilter
                        val matchesTier = tierFilter == null || comp.tier == tierFilter
                        matchesEnv && matchesTier
                    }
                }
                val displayedByTier = remember(displayed) {
                    groupCompsByTier(displayed)
                }
                val selectedComp = remember(displayed, selectedCompId) {
                    displayed.firstOrNull { it.id == selectedCompId }
                }
                // All tier sub-headers in the Live Match Drawer start closed until opened
                var expandedOverlayTiers by remember(browseMode, envFilter) {
                    mutableStateOf(emptySet<TierRank>())
                }

                var moveStartOrigin by remember { mutableStateOf(0 to 0) }
                var moveStartRaw by remember { mutableStateOf(0f to 0f) }
                var resizeStartSize by remember { mutableStateOf(drawerDims.widthDp.value to drawerDims.maxHeightDp.value) }
                var resizeStartRaw by remember { mutableStateOf(0f to 0f) }

                val surfaceSizeModifier = if (activeHeightDp != null) {
                    Modifier
                        .width(activeWidthDp)
                        .height(activeHeightDp)
                } else {
                    Modifier
                        .width(activeWidthDp)
                        .heightIn(max = drawerDims.maxHeightDp)
                }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = ObsidianVoid.copy(alpha = 0.96f),
                    border = BorderStroke(
                        width = 1.6.dp,
                        brush = Brush.linearGradient(listOf(BrightGold, ArcaneCyan))
                    ),
                    shadowElevation = 16.dp,
                    modifier = surfaceSizeModifier.testTag("overlay_expanded_drawer")
                ) {
                    Box(
                        modifier = if (activeHeightDp != null) {
                            Modifier.fillMaxWidth().fillMaxHeight()
                        } else {
                            Modifier.fillMaxWidth()
                        }
                    ) {
                        Column(
                            modifier = (if (activeHeightDp != null) {
                                Modifier.fillMaxWidth().fillMaxHeight()
                            } else {
                                Modifier.fillMaxWidth()
                            })
                                .padding(
                                    start = 14.dp,
                                    end = 14.dp,
                                    top = 8.dp,
                                    bottom = 12.dp
                                )
                        ) {
                            val inlineBrowseChips = drawerDims.widthDp >= 430.dp
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .pointerInteropFilter { event ->
                                                when (event.actionMasked) {
                                                    MotionEvent.ACTION_DOWN -> {
                                                        moveStartOrigin = windowOriginProvider()
                                                        moveStartRaw = event.rawX to event.rawY
                                                        true
                                                    }
                                                    MotionEvent.ACTION_MOVE -> {
                                                        val dx = (event.rawX - moveStartRaw.first).roundToInt()
                                                        val dy = (event.rawY - moveStartRaw.second).roundToInt()
                                                        onMoveExpandedWindow(
                                                            moveStartOrigin.first + dx,
                                                            moveStartOrigin.second + dy
                                                        )
                                                        true
                                                    }
                                                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
                                                    else -> false
                                                }
                                            }
                                            .testTag("overlay_drag_move_header")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DragIndicator,
                                            contentDescription = "Drag to move overlay",
                                            tint = Color(0xFF93A1B8),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            tint = BrightGold,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "LIVE MATCH",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = BrightGold,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                    if (inlineBrowseChips) {
                                        OverlayEnvChip(
                                            label = "${envFilter.label} ($envScopedCompsCount)",
                                            selected = browseMode == OverlayBrowseMode.ALL_COMPS,
                                            icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                                            onClick = {
                                                browseMode = OverlayBrowseMode.ALL_COMPS
                                                selectedCompId = null
                                            }
                                        )
                                        OverlayEnvChip(
                                            label = "Favs ($envScopedFavoritesCount)",
                                            selected = browseMode == OverlayBrowseMode.FAVORITES,
                                            icon = Icons.Filled.Star,
                                            onClick = {
                                                browseMode = OverlayBrowseMode.FAVORITES
                                                selectedCompId = null
                                            }
                                        )
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val isAtFullWidth = activeWidthDp.value >= sizeBounds.maxWidthDp - 6f
                                    IconButton(
                                        onClick = {
                                            if (isAtFullWidth) {
                                                val compactW = (sizeBounds.maxWidthDp * 0.62f)
                                                    .coerceAtLeast(sizeBounds.minWidthDp)
                                                onResizeExpandedWindow(compactW, customHeightDp, null)
                                            } else {
                                                onExtendFullWidth()
                                            }
                                        },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .testTag("overlay_extend_full_width_button")
                                    ) {
                                        Icon(
                                            if (isAtFullWidth) Icons.Default.CloseFullscreen else Icons.Default.OpenInFull,
                                            contentDescription = if (isAtFullWidth) {
                                                "Compact overlay width"
                                            } else {
                                                "Extend overlay across full device width"
                                            },
                                            tint = ArcaneCyan,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                    if (isCustomResized) {
                                        IconButton(
                                            onClick = onResetExpandedSize,
                                            modifier = Modifier
                                                .size(32.dp)
                                                .testTag("overlay_reset_size_button")
                                        ) {
                                            Icon(
                                                Icons.Default.RestartAlt,
                                                contentDescription = "Reset overlay to default landscape size",
                                                tint = BrightGold,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = onRefresh,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .testTag("overlay_refresh_button")
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Refresh comps & favorites", tint = ArcaneCyan, modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(
                                        onClick = onCollapseToEdge,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .testTag("overlay_collapse_edge_button")
                                    ) {
                                        Icon(collapseArrow, contentDescription = "Collapse to screen edge", tint = BrightGold, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = onMinimizeToBubble,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .testTag("overlay_minimize_bubble_button")
                                    ) {
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minimize to floating bubble", tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = onCloseOverlay,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .testTag("overlay_close_button")
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Stop overlay", tint = Color(0xFFE74C3C), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }

                        // Compact single filter strip while browsing comps list
                        if (selectedComp == null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(top = 4.dp, bottom = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!inlineBrowseChips) {
                                    OverlayEnvChip(
                                        label = "${envFilter.label} Comps ($envScopedCompsCount)",
                                        selected = browseMode == OverlayBrowseMode.ALL_COMPS,
                                        icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                                        onClick = {
                                            browseMode = OverlayBrowseMode.ALL_COMPS
                                            selectedCompId = null
                                        }
                                    )
                                    OverlayEnvChip(
                                        label = "Favorites ($envScopedFavoritesCount)",
                                        selected = browseMode == OverlayBrowseMode.FAVORITES,
                                        icon = Icons.Filled.Star,
                                        onClick = {
                                            browseMode = OverlayBrowseMode.FAVORITES
                                            selectedCompId = null
                                        }
                                    )
                                }
                                OverlayEnvChip("LIVE", envFilter == TftEnvironment.LIVE, Icons.Default.Bolt) {
                                    envFilter = TftEnvironment.LIVE
                                    selectedCompId = null
                                }
                                OverlayEnvChip("PBE", envFilter == TftEnvironment.PBE, Icons.Default.Science) {
                                    envFilter = TftEnvironment.PBE
                                    selectedCompId = null
                                }
                                TierRank.entries.forEach { t ->
                                    val tColor = when (t) {
                                        TierRank.S -> TierSColor
                                        TierRank.A -> TierAColor
                                        TierRank.B -> TierBColor
                                        TierRank.C -> TierCColor
                                        TierRank.X -> TierXColor
                                    }
                                    OverlayEnvChip(
                                        label = "${t.label}-Tier",
                                        selected = tierFilter == t,
                                        accentColor = tColor,
                                        icon = if (t == TierRank.S) Icons.Default.WorkspacePremium else null
                                    ) {
                                        tierFilter = if (tierFilter == t) null else t
                                    }
                                }
                                if (displayedByTier.isNotEmpty()) {
                                    val anyOpen = displayedByTier.any { expandedOverlayTiers.contains(it.first) }
                                    OverlayEnvChip(
                                        label = if (anyOpen) "Close All" else "Open All",
                                        selected = anyOpen,
                                        accentColor = ArcaneCyan
                                    ) {
                                        expandedOverlayTiers = if (anyOpen) {
                                            emptySet()
                                        } else {
                                            displayedByTier.map { it.first }.toSet()
                                        }
                                    }
                                }
                            }
                        } else {
                            Spacer(Modifier.height(6.dp))
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = activeHeightDp != null)
                        ) {
                            AnimatedContent(
                                targetState = Triple(browseMode, envFilter, tierFilter) to selectedComp?.id,
                                transitionSpec = {
                                    fadeIn(tween(150)) togetherWith fadeOut(tween(110))
                                },
                                label = "overlayCompsFilterTransition"
                            ) { (filterKey, _) ->
                                val (activeMode, activeEnv, _) = filterKey
                                if (selectedComp != null) {
                                    val leftWeight = if (drawerDims.widthDp >= 600.dp) 0.49f else 0.46f
                                    val rightWeight = 1f - leftWeight
                                    val paneHeightModifier = if (activeHeightDp != null) {
                                        Modifier.fillMaxHeight()
                                    } else {
                                        Modifier.heightIn(max = (drawerDims.maxHeightDp - 84.dp).coerceAtLeast(180.dp))
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(if (activeHeightDp != null) Modifier.fillMaxHeight() else Modifier),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        OverlayCompLeftDetailPane(
                                            comp = selectedComp,
                                            isFavorite = favoriteIds.contains(selectedComp.id),
                                            onToggleFavorite = { onToggleFavorite(selectedComp) },
                                            onBackToComps = { selectedCompId = null },
                                            modifier = Modifier
                                                .weight(leftWeight)
                                                .then(paneHeightModifier)
                                        )
                                        OverlayHexBoardRightPane(
                                            comp = selectedComp,
                                            fillAvailableHeight = activeHeightDp != null,
                                            maxPaneHeightDp = (drawerDims.maxHeightDp - 84.dp).coerceAtLeast(180.dp),
                                            modifier = Modifier
                                                .weight(rightWeight)
                                                .then(paneHeightModifier)
                                        )
                                    }
                                } else if (displayed.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(DeepSlate, RoundedCornerShape(12.dp))
                                            .padding(14.dp)
                                    ) {
                                        Text(
                                            text = if (activeMode == OverlayBrowseMode.FAVORITES) {
                                                "No ${activeEnv.label} favorites yet. Tap the Star icon on any composition to add it."
                                            } else {
                                                "No compositions match the active ${activeEnv.label} or tier filter."
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF93A1B8)
                                        )
                                    }
                                } else {
                                    val listState = rememberLazyListState()
                                    val useFullWidthTwoColGrid = drawerDims.widthDp >= 640.dp

                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        displayedByTier.forEach { (tier, tierComps) ->
                                            val isTierOpen = expandedOverlayTiers.contains(tier)
                                            item(
                                                key = "overlay_tier_header_${activeMode}_${activeEnv}_${tier.name}",
                                                contentType = "overlay_tier_header"
                                            ) {
                                                OverlayTierSubHeader(
                                                    tier = tier,
                                                    comps = tierComps,
                                                    expanded = isTierOpen,
                                                    onClick = {
                                                        expandedOverlayTiers = if (isTierOpen) {
                                                            expandedOverlayTiers - tier
                                                        } else {
                                                            expandedOverlayTiers + tier
                                                        }
                                                    },
                                                    modifier = Modifier.animateItem(
                                                        fadeInSpec = tween(180),
                                                        fadeOutSpec = tween(120),
                                                        placementSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                                    )
                                                )
                                            }
                                            if (isTierOpen) {
                                                if (useFullWidthTwoColGrid) {
                                                    val rows = tierComps.chunked(2)
                                                    items(
                                                        items = rows,
                                                        key = { pair -> "${tier.name}_row_${pair.joinToString("_") { it.id }}" },
                                                        contentType = { "overlay_comp_row" }
                                                    ) { rowComps ->
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .animateItem(
                                                                    fadeInSpec = tween(180),
                                                                    fadeOutSpec = tween(130),
                                                                    placementSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                                                ),
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            rowComps.forEach { comp ->
                                                                val isFav = favoriteIds.contains(comp.id)
                                                                OverlayCompCard(
                                                                    comp = comp,
                                                                    isFavorite = isFav,
                                                                    selected = false,
                                                                    expanded = false,
                                                                    opensInSidePane = true,
                                                                    useTwoColumnLayout = false,
                                                                    onCardClick = { selectedCompId = comp.id },
                                                                    onToggleFavorite = { onToggleFavorite(comp) },
                                                                    modifier = Modifier.weight(1f)
                                                                )
                                                            }
                                                            if (rowComps.size == 1) {
                                                                Spacer(Modifier.weight(1f))
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    items(
                                                        items = tierComps,
                                                        key = { "${tier.name}_${it.id}" },
                                                        contentType = { "overlay_comp" }
                                                    ) { comp ->
                                                        val isFav = favoriteIds.contains(comp.id)
                                                        OverlayCompCard(
                                                            comp = comp,
                                                            isFavorite = isFav,
                                                            selected = false,
                                                            expanded = false,
                                                            opensInSidePane = true,
                                                            useTwoColumnLayout = false,
                                                            onCardClick = { selectedCompId = comp.id },
                                                            onToggleFavorite = { onToggleFavorite(comp) },
                                                            modifier = Modifier.animateItem(
                                                                fadeInSpec = tween(180),
                                                                fadeOutSpec = tween(130),
                                                                placementSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                                            )
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SlateElevated.copy(alpha = 0.72f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                                .testTag("overlay_made_by_credits"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.made_by_credits),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrightGold
                            )
                            Text(
                                text = "Drag edge/corner to resize • ${drawerDims.widthDp.value.roundToInt()}×${drawerDims.maxHeightDp.value.roundToInt()}dp",
                                fontSize = 9.sp,
                                color = Color(0xFF93A1B8),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        }

                        // Left-edge horizontal resize grip (extends leftward across device width)
                        var leftResizeStartOriginX by remember { mutableIntStateOf(0) }
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .width(14.dp)
                                .height(76.dp)
                                .pointerInteropFilter { event ->
                                    when (event.actionMasked) {
                                        MotionEvent.ACTION_DOWN -> {
                                            leftResizeStartOriginX = windowOriginProvider().first
                                            resizeStartSize = windowSizeProvider()
                                            resizeStartRaw = event.rawX to event.rawY
                                            true
                                        }
                                        MotionEvent.ACTION_MOVE -> {
                                            val d = densityProvider().coerceAtLeast(1f)
                                            val deltaRawX = event.rawX - resizeStartRaw.first
                                            val (nextW, nextX) = computeLeftEdgeResizeResult(
                                                startOriginX = leftResizeStartOriginX,
                                                startWidthDp = resizeStartSize.first,
                                                deltaRawX = deltaRawX,
                                                density = d,
                                                minLeftPx = 0,
                                                maxRightPx = Int.MAX_VALUE,
                                                bounds = sizeBounds
                                            )
                                            onResizeExpandedWindow(nextW, customHeightDp, nextX)
                                            true
                                        }
                                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
                                        else -> false
                                    }
                                }
                                .testTag("overlay_resize_left_handle"),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(start = 3.dp)
                                    .width(3.dp)
                                    .height(34.dp)
                                    .background(BrushedGold.copy(alpha = 0.72f), RoundedCornerShape(2.dp))
                            )
                        }

                        // Right-edge horizontal resize grip (extends rightward across device width)
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .width(14.dp)
                                .height(76.dp)
                                .pointerInteropFilter { event ->
                                    when (event.actionMasked) {
                                        MotionEvent.ACTION_DOWN -> {
                                            resizeStartSize = windowSizeProvider()
                                            resizeStartRaw = event.rawX to event.rawY
                                            true
                                        }
                                        MotionEvent.ACTION_MOVE -> {
                                            val d = densityProvider().coerceAtLeast(1f)
                                            val nextW = resizeStartSize.first + (event.rawX - resizeStartRaw.first) / d
                                            onResizeExpandedWindow(nextW, customHeightDp, null)
                                            true
                                        }
                                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
                                        else -> false
                                    }
                                }
                                .testTag("overlay_resize_right_handle"),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(end = 3.dp)
                                    .width(3.dp)
                                    .height(34.dp)
                                    .background(BrushedGold.copy(alpha = 0.72f), RoundedCornerShape(2.dp))
                            )
                        }

                        // Bottom-edge vertical resize grip
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(12.dp)
                                .padding(horizontal = 44.dp)
                                .pointerInteropFilter { event ->
                                    when (event.actionMasked) {
                                        MotionEvent.ACTION_DOWN -> {
                                            resizeStartSize = windowSizeProvider()
                                            resizeStartRaw = event.rawX to event.rawY
                                            true
                                        }
                                        MotionEvent.ACTION_MOVE -> {
                                            val d = densityProvider().coerceAtLeast(1f)
                                            val nextH = resizeStartSize.second + (event.rawY - resizeStartRaw.second) / d
                                            onResizeExpandedWindow(customWidthDp, nextH, null)
                                            true
                                        }
                                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
                                        else -> false
                                    }
                                }
                                .testTag("overlay_resize_bottom_handle"),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(bottom = 3.dp)
                                    .width(42.dp)
                                    .height(3.dp)
                                    .background(BrushedGold.copy(alpha = 0.72f), RoundedCornerShape(2.dp))
                            )
                        }

                        // Bottom-right corner diagonal resize grip (width + height freely)
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(30.dp)
                                .pointerInteropFilter { event ->
                                    when (event.actionMasked) {
                                        MotionEvent.ACTION_DOWN -> {
                                            resizeStartSize = windowSizeProvider()
                                            resizeStartRaw = event.rawX to event.rawY
                                            true
                                        }
                                        MotionEvent.ACTION_MOVE -> {
                                            val d = densityProvider().coerceAtLeast(1f)
                                            val nextW = resizeStartSize.first + (event.rawX - resizeStartRaw.first) / d
                                            val nextH = resizeStartSize.second + (event.rawY - resizeStartRaw.second) / d
                                            onResizeExpandedWindow(nextW, nextH, null)
                                            true
                                        }
                                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
                                        else -> false
                                    }
                                }
                                .testTag("overlay_resize_corner_handle"),
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            Canvas(
                                modifier = Modifier
                                    .size(20.dp)
                                    .padding(end = 4.dp, bottom = 4.dp)
                            ) {
                                val w = size.width
                                val h = size.height
                                drawLine(
                                    color = ArcaneCyan,
                                    start = Offset(w * 0.25f, h),
                                    end = Offset(w, h * 0.25f),
                                    strokeWidth = 2.4f,
                                    cap = StrokeCap.Round
                                )
                                drawLine(
                                    color = BrightGold,
                                    start = Offset(w * 0.62f, h),
                                    end = Offset(w, h * 0.62f),
                                    strokeWidth = 2.4f,
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayTierSubHeader(
    tier: TierRank,
    comps: List<MetaComp>,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tierColor = when (tier) {
        TierRank.S -> TierSColor
        TierRank.A -> TierAColor
        TierRank.B -> TierBColor
        TierRank.C -> TierCColor
        TierRank.X -> TierXColor
    }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "overlayTierChevron_${tier.name}"
    )
    val previewCarries = remember(comps) {
        comps.map { it.carryName }.filter { it.isNotBlank() }.distinct().take(3)
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (expanded) SlateElevated else DeepSlate,
        border = BorderStroke(
            width = if (expanded) 1.3.dp else 1.dp,
            color = if (expanded) tierColor else tierColor.copy(alpha = 0.42f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("overlay_tier_header_${tier.label}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            tierColor.copy(alpha = if (expanded) 0.16f else 0.08f),
                            Color.Transparent
                        )
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(22.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(tierColor)
                    )
                    TierBadge(tier = tier)
                    Text(
                        text = "${comps.size} ${if (comps.size == 1) "Comp" else "Comps"}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (expanded) Color.White else Color(0xFFE3EBFA)
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!expanded && previewCarries.isNotEmpty()) {
                        val stackW = (20 + (previewCarries.size - 1) * 12).dp
                        Box(
                            modifier = Modifier
                                .width(stackW)
                                .height(20.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            previewCarries.forEachIndexed { idx, carry ->
                                TftChampionAvatar(
                                    name = carry,
                                    cost = 4,
                                    isCarry = false,
                                    size = 18.dp,
                                    modifier = Modifier.offset(x = (idx * 12).dp)
                                )
                            }
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse ${tier.label}-Tier" else "Expand ${tier.label}-Tier",
                        tint = if (expanded) tierColor else Color(0xFF9FB0CC),
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(rotation)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverlayCompLeftDetailPane(
    comp: MetaComp,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onBackToComps: () -> Unit,
    modifier: Modifier = Modifier
) {
    val unitCosts = remember(comp.hexPlacements) {
        comp.hexPlacements.associate { it.unitName to it.cost }
    }
    val carryBis = remember(comp.hexPlacements) {
        comp.hexPlacements.firstOrNull { it.isCarry }?.items.orEmpty()
    }
    val secondaryItemized = remember(comp.hexPlacements) {
        comp.hexPlacements.filter { it.items.isNotEmpty() && !it.isCarry }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = DeepSlate,
        border = BorderStroke(
            width = 1.4.dp,
            brush = Brush.linearGradient(listOf(BrightGold, ArcaneCyan))
        ),
        modifier = modifier.testTag("overlay_left_comp_pane")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onBackToComps,
                    shape = RoundedCornerShape(8.dp),
                    color = SlateElevated,
                    border = BorderStroke(1.dp, ArcaneCyan.copy(alpha = 0.65f)),
                    modifier = Modifier.testTag("overlay_detail_back_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to compositions list",
                            tint = ArcaneCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Comps",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ArcaneCyan
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("overlay_detail_fav_${comp.id}")
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                            tint = if (isFavorite) BrightGold else Color(0xFF93A1B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onBackToComps,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("overlay_detail_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close composition details",
                            tint = Color(0xFFB7C4D8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TierBadge(tier = comp.tier)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = comp.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${comp.environment.label} • ${comp.playstyle} • Avg ${String.format(Locale.US, "%.2f", comp.avgPlacement)} • ${comp.setName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (comp.environment == TftEnvironment.PBE) PbePurple else ArcaneCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (comp.traits.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    comp.traits.take(6).forEach { trait ->
                        TftTraitChip(traitLabel = trait, environment = comp.environment)
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                comp.coreUnits.take(10).forEach { unit ->
                    val isCarryUnit = unit.equals(comp.carryName, ignoreCase = true)
                    TftChampionTile(
                        name = unit,
                        cost = unitCosts[unit] ?: if (isCarryUnit) 4 else 3,
                        isCarry = isCarryUnit,
                        size = 34.dp,
                        labelFontSize = 10.sp,
                        environment = comp.environment
                    )
                }
            }

            OverlayCompSideBuildDetails(
                comp = comp,
                carryBis = carryBis,
                secondaryItemized = secondaryItemized
            )
            if (comp.notes.isNotBlank()) {
                Text(
                    text = comp.notes,
                    fontSize = 10.sp,
                    color = Color(0xFF9FB0CC)
                )
            }
        }
    }
}

@Composable
private fun OverlayHexBoardRightPane(
    comp: MetaComp,
    fillAvailableHeight: Boolean,
    maxPaneHeightDp: Dp,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = DeepSlate,
        border = BorderStroke(
            width = 1.4.dp,
            brush = Brush.linearGradient(listOf(ArcaneCyan, BrightGold))
        ),
        modifier = modifier.testTag("overlay_right_hex_board_pane")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (fillAvailableHeight) Modifier.fillMaxHeight() else Modifier)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "4×7 Hex Board",
                    style = MaterialTheme.typography.titleSmall,
                    color = ArcaneCyan,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Carry: ${comp.carryName}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BrightGold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (comp.hexPlacements.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(ObsidianVoid.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No hex placements listed for this composition.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF93A1B8)
                    )
                }
            } else {
                val boardModifier = if (fillAvailableHeight) {
                    Modifier.fillMaxWidth().weight(1f)
                } else {
                    Modifier.fillMaxWidth()
                }
                BoxWithConstraints(modifier = boardModifier) {
                    val computedGridHeight = if (constraints.hasBoundedHeight) {
                        (maxHeight - 28.dp).coerceIn(168.dp, 260.dp)
                    } else {
                        (maxPaneHeightDp - 72.dp).coerceIn(168.dp, 230.dp)
                    }
                    MiniOverlayHexGrid(
                        placements = comp.hexPlacements,
                        environment = comp.environment,
                        gridHeight = computedGridHeight
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverlayCompSideBuildDetails(
    comp: MetaComp,
    carryBis: List<String>,
    secondaryItemized: List<HexUnitPlacement>
) {
    if (carryBis.isNotEmpty()) {
        Text(
            text = "Carry BIS (${comp.carryName})",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = BrightGold
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            carryBis.forEach { itemName ->
                TftItemChip(itemName = itemName, compact = true)
            }
        }
    }
    if (secondaryItemized.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            secondaryItemized.take(4).forEach { unit ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    TftChampionAvatar(
                        name = unit.unitName,
                        cost = unit.cost,
                        isCarry = false,
                        stars = unit.stars,
                        size = 18.dp,
                        environment = comp.environment
                    )
                    Text(
                        text = "${unit.unitName}:",
                        fontSize = 10.sp,
                        color = Color(0xFFB7C4D8),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    unit.items.take(3).forEach { item ->
                        TftItemIcon(itemName = item, size = 16.dp)
                    }
                }
            }
        }
    }
    if (comp.augmentPriorities.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            comp.augmentPriorities.take(3).forEach { aug ->
                TftAugmentChip(augmentName = aug, compact = true)
            }
        }
    }
    val cleanLeveling = remember(comp.levelingGuide) {
        sanitizeExternalSourceMentions(comp.levelingGuide)
    }
    if (cleanLeveling.isNotBlank()) {
        Text(
            text = cleanLeveling,
            fontSize = 10.sp,
            color = Color(0xFFB7C4D8)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverlayCompCard(
    comp: MetaComp,
    isFavorite: Boolean,
    selected: Boolean = false,
    expanded: Boolean,
    opensInSidePane: Boolean = false,
    useTwoColumnLayout: Boolean = false,
    onCardClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val unitCosts = remember(comp.hexPlacements) {
        comp.hexPlacements.associate { it.unitName to it.cost }
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) SlateElevated else DeepSlate,
        border = BorderStroke(
            width = if (selected) 1.6.dp else if (isFavorite) 1.3.dp else 1.dp,
            color = when {
                selected -> ArcaneCyan
                isFavorite -> BrushedGold.copy(alpha = 0.85f)
                else -> SlateOutline
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
            .clickable(onClick = onCardClick)
            .testTag("overlay_comp_${comp.id}")
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = comp.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${comp.environment.label} • ${comp.tier.label}-Tier • ${comp.setName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (comp.environment == TftEnvironment.PBE) PbePurple else ArcaneCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("overlay_fav_${comp.id}")
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (isFavorite) BrightGold else Color(0xFF93A1B8),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            val carryBis = remember(comp.hexPlacements) {
                comp.hexPlacements.firstOrNull { it.isCarry }?.items.orEmpty()
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Carry: ${comp.carryName} • ${comp.playstyle} • Avg ${String.format(Locale.US, "%.2f", comp.avgPlacement)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = BrightGold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (carryBis.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        carryBis.take(3).forEach { itemName ->
                            TftItemIcon(
                                itemName = itemName,
                                size = 18.dp
                            )
                        }
                    }
                }
            }

            if (comp.traits.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    comp.traits.take(4).forEach { trait ->
                        TftTraitChip(traitLabel = trait, environment = comp.environment)
                    }
                }
            }

            Spacer(Modifier.height(5.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                comp.coreUnits.take(10).forEach { unit ->
                    val isCarryUnit = unit.equals(comp.carryName, ignoreCase = true)
                    TftChampionTile(
                        name = unit,
                        cost = unitCosts[unit] ?: if (isCarryUnit) 4 else 3,
                        isCarry = isCarryUnit,
                        size = 34.dp,
                        labelFontSize = 10.sp,
                        environment = comp.environment
                    )
                }
            }

            if (expanded) {
                val itemizedUnits = remember(comp.hexPlacements) {
                    comp.hexPlacements.filter { it.items.isNotEmpty() }
                }
                val secondaryItemized = remember(itemizedUnits) {
                    itemizedUnits.filterNot { it.isCarry }
                }
                val hasLeftDetails = carryBis.isNotEmpty() ||
                    secondaryItemized.isNotEmpty() ||
                    comp.augmentPriorities.isNotEmpty() ||
                    comp.levelingGuide.isNotBlank()

                if (useTwoColumnLayout && comp.hexPlacements.isNotEmpty() && hasLeftDetails) {
                    Spacer(Modifier.height(7.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier.weight(1.05f),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            if (carryBis.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    carryBis.forEach { itemName ->
                                        TftItemChip(itemName = itemName, compact = true)
                                    }
                                }
                            }
                            if (secondaryItemized.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    secondaryItemized.take(3).forEach { unit ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            TftChampionAvatar(
                                                name = unit.unitName,
                                                cost = unit.cost,
                                                isCarry = false,
                                                stars = unit.stars,
                                                size = 18.dp,
                                                environment = comp.environment
                                            )
                                            Text(
                                                text = "${unit.unitName}:",
                                                fontSize = 10.sp,
                                                color = Color(0xFFB7C4D8),
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            unit.items.take(3).forEach { item ->
                                                TftItemIcon(itemName = item, size = 16.dp)
                                            }
                                        }
                                    }
                                }
                            }
                            if (comp.augmentPriorities.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    comp.augmentPriorities.take(3).forEach { aug ->
                                        TftAugmentChip(augmentName = aug, compact = true)
                                    }
                                }
                            }
                            val cleanLeveling = remember(comp.levelingGuide) {
                                sanitizeExternalSourceMentions(comp.levelingGuide)
                            }
                            if (cleanLeveling.isNotBlank()) {
                                Text(
                                    text = cleanLeveling,
                                    fontSize = 10.sp,
                                    color = Color(0xFFB7C4D8)
                                )
                            }
                        }
                        Box(modifier = Modifier.weight(0.95f)) {
                            MiniOverlayHexGrid(
                                placements = comp.hexPlacements,
                                environment = comp.environment,
                                gridHeight = 168.dp
                            )
                        }
                    }
                } else {
                    if (carryBis.isNotEmpty()) {
                        Spacer(Modifier.height(7.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            carryBis.forEach { itemName ->
                                TftItemChip(itemName = itemName, compact = true)
                            }
                        }
                    }
                    if (secondaryItemized.isNotEmpty()) {
                        Spacer(Modifier.height(5.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            secondaryItemized.take(3).forEach { unit ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    TftChampionAvatar(
                                        name = unit.unitName,
                                        cost = unit.cost,
                                        isCarry = false,
                                        stars = unit.stars,
                                        size = 18.dp,
                                        environment = comp.environment
                                    )
                                    Text(
                                        text = "${unit.unitName}:",
                                        fontSize = 10.sp,
                                        color = Color(0xFFB7C4D8),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    unit.items.take(3).forEach { item ->
                                        TftItemIcon(itemName = item, size = 16.dp)
                                    }
                                }
                            }
                        }
                    }
                    if (comp.hexPlacements.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        MiniOverlayHexGrid(comp.hexPlacements, environment = comp.environment)
                    }
                    if (comp.augmentPriorities.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            comp.augmentPriorities.take(3).forEach { aug ->
                                TftAugmentChip(augmentName = aug, compact = true)
                            }
                        }
                    }
                    val cleanLeveling = remember(comp.levelingGuide) {
                        sanitizeExternalSourceMentions(comp.levelingGuide)
                    }
                    if (cleanLeveling.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = cleanLeveling,
                            fontSize = 10.sp,
                            color = Color(0xFFB7C4D8)
                        )
                    }
                }
            } else {
                Text(
                    text = if (opensInSidePane) {
                        "Tap card for Comp (Left) & Hex Board (Right) →"
                    } else {
                        "Tap card for BIS items, 4×7 hex board & augments"
                    },
                    fontSize = 9.sp,
                    color = Color(0xFF7D8DA6),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun OverlayEnvChip(
    label: String,
    selected: Boolean,
    icon: ImageVector? = null,
    accentColor: Color = BrightGold,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(9.dp),
        color = if (selected) accentColor.copy(alpha = 0.24f) else SlateElevated,
        border = BorderStroke(1.dp, if (selected) accentColor else SlateOutline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) accentColor else Color(0xFFA6B6D4),
                    modifier = Modifier.size(12.dp)
                )
            }
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) accentColor else Color(0xFFA6B6D4),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MiniOverlayHexGrid(
    placements: List<HexUnitPlacement>,
    environment: TftEnvironment? = null,
    gridHeight: Dp = 184.dp
) {
    val density = LocalDensity.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(ObsidianVoid, DeepSlate)),
                RoundedCornerShape(8.dp)
            )
            .padding(6.dp)
    ) {
        Text(
            text = "Board (R1 Front -> R4 Back) • Gold = Carry",
            fontSize = 9.sp,
            color = Color(0xFF93A1B8)
        )
        Spacer(Modifier.height(3.dp))
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(gridHeight)
        ) {
            val cols = 7
            val rows = 4
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            val bottomLabelReservePx = with(density) { 14.dp.toPx() }
            val rByWidth = widthPx / ((cols - 1) * 1.88f + 0.94f + 2.25f)
            val rByHeight = (heightPx - bottomLabelReservePx) / ((rows - 1) * 2.48f + 2.0f)
            val radius = minOf(rByWidth, rByHeight).coerceAtLeast(8f)
            val dx = radius * 1.88f
            val dy = radius * 2.48f
            val totalGridWidth = (cols - 1) * dx + dx * 0.5f + radius * 2f
            val totalGridHeight = (rows - 1) * dy + radius * 2f + bottomLabelReservePx
            val startX = (widthPx - totalGridWidth) / 2f + radius
            val startY = (heightPx - totalGridHeight) / 2f + radius
            val hexR = radius * 0.94f
            val occupied = placements.associateBy { it.row to it.col }

            Canvas(modifier = Modifier.matchParentSize()) {
                for (r in 0 until rows) {
                    for (c in 0 until cols) {
                        val xOffset = if (r % 2 == 1) dx * 0.5f else 0f
                        val cx = startX + c * dx + xOffset
                        val cy = startY + r * dy
                        val unit = occupied[r to c]
                        val path = Path().apply {
                            for (i in 0 until 6) {
                                val angle = Math.toRadians((60 * i - 30).toDouble())
                                val px = cx + hexR * cos(angle).toFloat()
                                val py = cy + hexR * sin(angle).toFloat()
                                if (i == 0) moveTo(px, py) else lineTo(px, py)
                            }
                            close()
                        }
                        when {
                            unit?.isCarry == true -> {
                                drawPath(path, color = BrightGold.copy(alpha = 0.28f))
                                drawPath(path, color = BrightGold, style = Stroke(width = 2.4f))
                            }
                            unit != null -> {
                                val costTint = championCostColor(unit.cost)
                                drawPath(path, color = costTint.copy(alpha = 0.22f))
                                drawPath(path, color = costTint, style = Stroke(width = 1.9f))
                            }
                            else -> {
                                drawPath(path, color = SlateOutline.copy(alpha = 0.75f), style = Stroke(width = 1.1f))
                            }
                        }
                    }
                }
            }

            val hexWpx = (1.73205f * hexR).roundToInt().coerceIn(18, 60)
            val hexHpx = (2.0f * hexR).roundToInt().coerceIn(20, 70)
            val hexWDp = with(density) { hexWpx.toDp() }
            val hexHDp = with(density) { hexHpx.toDp() }
            val labelMaxWpx = (dx * 1.32f).roundToInt().coerceAtLeast(hexWpx + 10)
            val labelMaxWDp = with(density) { labelMaxWpx.toDp() }
            val labelTopGapPx = with(density) { 1.dp.toPx() }

            placements.forEach { unit ->
                val xOffset = if (unit.row % 2 == 1) dx * 0.5f else 0f
                val cx = startX + unit.col * dx + xOffset
                val cy = startY + unit.row * dy
                val leftPx = (cx - hexWpx / 2f).roundToInt()
                val topPx = (cy - hexHpx / 2f).roundToInt()
                val cleanLabel = formatRiotAssetLabel(unit.unitName)

                Box(
                    modifier = Modifier
                        .offset { IntOffset(leftPx, topPx) }
                        .size(width = hexWDp, height = hexHDp),
                    contentAlignment = Alignment.Center
                ) {
                    TftChampionAvatar(
                        name = unit.unitName,
                        cost = unit.cost,
                        isCarry = unit.isCarry,
                        stars = unit.stars,
                        size = hexWDp,
                        height = hexHDp,
                        environment = environment,
                        exactSize = true,
                        useHexShape = true,
                        showBadge = false
                    )
                }

                val labelLeftPx = (cx - labelMaxWpx / 2f).roundToInt()
                val labelTopPx = (cy + hexHpx / 2f + labelTopGapPx).roundToInt()
                Box(
                    modifier = Modifier
                        .offset { IntOffset(labelLeftPx, labelTopPx) }
                        .width(labelMaxWDp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = cleanLabel,
                        fontSize = 8.5.sp,
                        lineHeight = 9.sp,
                        fontWeight = if (unit.isCarry) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (unit.isCarry) BrightGold else Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .background(Color(0xEB070B12), RoundedCornerShape(3.dp))
                            .padding(horizontal = 3.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}
