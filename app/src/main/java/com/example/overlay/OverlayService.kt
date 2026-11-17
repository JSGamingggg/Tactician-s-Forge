package com.example.overlay

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.ui.components.TftItemChip
import com.example.ui.components.TftItemIcon
import com.example.ui.components.TftTraitChip
import com.example.ui.theme.ArcaneCyan
import com.example.ui.theme.BrightGold
import com.example.ui.theme.BrushedGold
import com.example.ui.theme.DeepSlate
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.PbePurple
import com.example.ui.theme.SlateElevated
import com.example.ui.theme.SlateOutline
import com.example.ui.theme.TacticiansForgeTheme
import com.google.firebase.Firebase
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

class OverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
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
    private var savedBubbleAnchorY = 220
    private val allCompsState = mutableStateOf<List<MetaComp>>(emptyList())
    private val favoriteDocsState = mutableStateOf<List<FavoriteCompDoc>>(emptyList())
    private var favoritesObserverJob: Job? = null

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
        val bundle = metaRepository.loadOfflineBundle()
        allCompsState.value = (bundle.liveComps + bundle.pbeComps)
            .sortedWith(compareBy<MetaComp> { it.environment.ordinal }.thenBy { it.tier.sortOrder }.thenBy { it.avgPlacement })
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
        serviceScope.launch {
            val sync = metaRepository.refreshAndPersistMeta()
            val bundle = metaRepository.loadOfflineBundle()
            MetaUpdateNotifier.dispatchNotifications(
                context = applicationContext,
                syncResult = sync,
                bundle = bundle
            )
            reloadCompsFromCache()
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
            val uid = runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
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
            val uid = runCatching { Firebase.auth.currentUser?.uid }.getOrNull()
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

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).roundToInt()

    private fun screenWidthPx(): Int = resources.displayMetrics.widthPixels.coerceAtLeast(1)

    private fun screenHeightPx(): Int = resources.displayMetrics.heightPixels.coerceAtLeast(1)

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

        val edgeGravity = if (dockEdge == OverlayDockEdge.LEFT) Gravity.START else Gravity.END
        val marginPx = dpToPx(12)
        val minY = dpToPx(24)
        val maxCompactY = (screenHeightPx() - dpToPx(110)).coerceAtLeast(minY)
        val maxExpandedY = (screenHeightPx() - dpToPx(480)).coerceAtLeast(minY)

        when (targetState) {
            OverlayDisplayState.EDGE_COLLAPSED -> {
                overlayLayoutParams.gravity = Gravity.TOP or edgeGravity
                overlayLayoutParams.x = 0
                overlayLayoutParams.y = savedBubbleAnchorY.coerceIn(minY, maxCompactY)
            }
            OverlayDisplayState.BUBBLE -> {
                overlayLayoutParams.gravity = Gravity.TOP or edgeGravity
                overlayLayoutParams.x = marginPx
                overlayLayoutParams.y = savedBubbleAnchorY.coerceIn(minY, maxCompactY)
            }
            OverlayDisplayState.EXPANDED -> {
                overlayLayoutParams.gravity = Gravity.TOP or edgeGravity
                overlayLayoutParams.x = marginPx
                overlayLayoutParams.y = savedBubbleAnchorY.coerceIn(minY, maxExpandedY)
            }
        }

        overlayView?.let { view ->
            runCatching { windowManager.updateViewLayout(view, overlayLayoutParams) }
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

        val initialMarginPx = dpToPx(12)
        savedBubbleAnchorY = dpToPx(88)
        overlayLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialMarginPx
            y = savedBubbleAnchorY
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
            val edgeSnapThresholdPx = dpToPx(24)
            val uncollapseDragThresholdPx = dpToPx(32)

            setOnTouchListener { view, event ->
                val activeState = displayState.value
                if (activeState == OverlayDisplayState.EXPANDED) return@setOnTouchListener false

                val viewW = view.width.takeIf { it > 0 } ?: dpToPx(60)
                val screenW = screenWidthPx()
                val minY = dpToPx(24)
                val maxY = (screenHeightPx() - dpToPx(110)).coerceAtLeast(minY)

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        val isEndGravity = (overlayLayoutParams.gravity and Gravity.END) == Gravity.END
                        startLeftPx = if (isEndGravity) {
                            (screenW - viewW - overlayLayoutParams.x).coerceAtLeast(0)
                        } else {
                            overlayLayoutParams.x
                        }
                        initialY = overlayLayoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        moved = false
                        false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (abs(dx) > 10 || abs(dy) > 10) {
                            moved = true
                            val clampedY = (initialY + dy).coerceIn(minY, maxY)
                            savedBubbleAnchorY = clampedY

                            if (displayState.value == OverlayDisplayState.EDGE_COLLAPSED) {
                                val pulledAway = (dockEdgeState.value == OverlayDockEdge.LEFT && dx > uncollapseDragThresholdPx) ||
                                    (dockEdgeState.value == OverlayDockEdge.RIGHT && dx < -uncollapseDragThresholdPx)
                                if (pulledAway) {
                                    displayState.value = OverlayDisplayState.BUBBLE
                                    overlayLayoutParams.gravity = Gravity.TOP or Gravity.START
                                    overlayLayoutParams.x = (startLeftPx + dx).coerceIn(0, (screenW - viewW).coerceAtLeast(0))
                                    overlayLayoutParams.y = clampedY
                                } else {
                                    // Stay flush to the docked screen edge while sliding vertically
                                    overlayLayoutParams.x = 0
                                    overlayLayoutParams.y = clampedY
                                }
                            } else {
                                overlayLayoutParams.gravity = Gravity.TOP or Gravity.START
                                overlayLayoutParams.x = (startLeftPx + dx).coerceIn(0, (screenW - viewW).coerceAtLeast(0))
                                overlayLayoutParams.y = clampedY
                            }
                            runCatching { windowManager.updateViewLayout(this, overlayLayoutParams) }
                        }
                        moved
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (moved && displayState.value == OverlayDisplayState.BUBBLE) {
                            val decision = computeEdgeSnapDecision(
                                leftEdgePx = overlayLayoutParams.x,
                                viewWidthPx = viewW,
                                screenWidthPx = screenW,
                                snapThresholdPx = edgeSnapThresholdPx
                            )
                            dockEdgeState.value = decision.dockEdge
                            if (decision.shouldCollapseToEdge) {
                                transitionDisplayState(
                                    targetState = OverlayDisplayState.EDGE_COLLAPSED,
                                    dockEdge = decision.dockEdge
                                )
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
                        allComps = allCompsState.value,
                        favoriteDocs = favoriteDocsState.value,
                        onExpandDrawer = {
                            reloadCompsFromCache()
                            transitionDisplayState(OverlayDisplayState.EXPANDED)
                        },
                        onMinimizeToBubble = {
                            transitionDisplayState(OverlayDisplayState.BUBBLE)
                        },
                        onCollapseToEdge = {
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
        serviceScope.cancel()
        OverlayController.setRunning(false)
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        overlayView = null
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }

    companion object {
        private const val NOTIFICATION_ID = 7701
        private const val ACTIVE_OVERLAY_SYNC_INTERVAL_MS = 5 * 60_000L
        const val ACTION_STOP_OVERLAY = "com.example.overlay.ACTION_STOP"
    }
}

private enum class OverlayBrowseMode {
    ALL_COMPS,
    FAVORITES
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverlayRootContent(
    displayState: OverlayDisplayState,
    dockEdge: OverlayDockEdge,
    allComps: List<MetaComp>,
    favoriteDocs: List<FavoriteCompDoc>,
    onExpandDrawer: () -> Unit,
    onMinimizeToBubble: () -> Unit,
    onCollapseToEdge: () -> Unit,
    onRefresh: () -> Unit,
    onToggleFavorite: (MetaComp) -> Unit,
    onCloseOverlay: () -> Unit
) {
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

    AnimatedContent(
        targetState = displayState,
        transitionSpec = {
            fadeIn(tween(170)) togetherWith fadeOut(tween(120))
        },
        label = "overlayDisplayStateTransition"
    ) { state ->
        when (state) {
            OverlayDisplayState.EDGE_COLLAPSED -> {
                val badgeCount = if (favoriteDocs.isNotEmpty()) favoriteDocs.size else mergedAllComps.size
                val isLeft = dockEdge == OverlayDockEdge.LEFT
                val tabShape = if (isLeft) {
                    RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 14.dp, bottomEnd = 14.dp)
                } else {
                    RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp, topEnd = 0.dp, bottomEnd = 0.dp)
                }
                val pullIcon = if (isLeft) {
                    Icons.AutoMirrored.Filled.KeyboardArrowRight
                } else {
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft
                }

                // 48dp accessible hit box while visible Hextech tab stays slim (28dp) flush against screen edge
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(72.dp)
                        .clip(tabShape)
                        .clickable(onClick = onExpandDrawer)
                        .testTag("overlay_edge_tab"),
                    contentAlignment = if (isLeft) Alignment.CenterStart else Alignment.CenterEnd
                ) {
                    Surface(
                        shape = tabShape,
                        color = ObsidianVoid.copy(alpha = 0.84f),
                        border = BorderStroke(1.4.dp, BrushedGold.copy(alpha = 0.85f)),
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .width(28.dp)
                            .height(66.dp)
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
                                    .background(BrushedGold.copy(alpha = 0.65f), RoundedCornerShape(2.dp))
                            )
                            if (badgeCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
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
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        onClick = onExpandDrawer,
                        shape = CircleShape,
                        color = ObsidianVoid.copy(alpha = 0.94f),
                        border = BorderStroke(2.dp, BrushedGold),
                        shadowElevation = 10.dp,
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
                        color = ObsidianVoid.copy(alpha = 0.88f),
                        border = BorderStroke(1.dp, BrushedGold.copy(alpha = 0.7f)),
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
                                color = Color(0xFFE6ECF5)
                            )
                        }
                    }
                }
            }

            OverlayDisplayState.EXPANDED -> {
                var browseMode by remember { mutableStateOf(OverlayBrowseMode.ALL_COMPS) }
                var envFilter by remember { mutableStateOf<TftEnvironment?>(null) }
                var tierFilter by remember { mutableStateOf<TierRank?>(null) }
                var selectedHexCompId by remember { mutableStateOf<String?>(null) }
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

                val displayed = remember(mergedAllComps, favoriteComps, browseMode, envFilter, tierFilter) {
                    val source = if (browseMode == OverlayBrowseMode.FAVORITES) favoriteComps else mergedAllComps
                    source.filter { comp ->
                        val matchesEnv = envFilter == null || comp.environment == envFilter
                        val matchesTier = tierFilter == null || comp.tier == tierFilter
                        matchesEnv && matchesTier
                    }
                }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = ObsidianVoid.copy(alpha = 0.95f),
                    border = BorderStroke(1.5.dp, BrushedGold.copy(alpha = 0.7f)),
                    shadowElevation = 14.dp,
                    modifier = Modifier.width(332.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = BrightGold,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "LIVE MATCH DRAWER",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = BrightGold,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = onRefresh,
                                    modifier = Modifier
                                        .size(34.dp)
                                        .testTag("overlay_refresh_button")
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Refresh comps & favorites", tint = ArcaneCyan, modifier = Modifier.size(17.dp))
                                }
                                IconButton(
                                    onClick = onCollapseToEdge,
                                    modifier = Modifier
                                        .size(34.dp)
                                        .testTag("overlay_collapse_edge_button")
                                ) {
                                    Icon(collapseArrow, contentDescription = "Collapse to screen edge", tint = BrightGold, modifier = Modifier.size(19.dp))
                                }
                                IconButton(
                                    onClick = onMinimizeToBubble,
                                    modifier = Modifier
                                        .size(34.dp)
                                        .testTag("overlay_minimize_bubble_button")
                                ) {
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minimize to floating bubble", tint = Color.White, modifier = Modifier.size(19.dp))
                                }
                                IconButton(
                                    onClick = onCloseOverlay,
                                    modifier = Modifier
                                        .size(34.dp)
                                        .testTag("overlay_close_button")
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Stop overlay", tint = Color(0xFFE74C3C), modifier = Modifier.size(17.dp))
                                }
                            }
                        }

                        // All Comps vs Favorites Switcher
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OverlayEnvChip(
                                label = "All Comps (${mergedAllComps.size})",
                                selected = browseMode == OverlayBrowseMode.ALL_COMPS,
                                icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                                onClick = { browseMode = OverlayBrowseMode.ALL_COMPS }
                            )
                            OverlayEnvChip(
                                label = "Favorites (${favoriteComps.size})",
                                selected = browseMode == OverlayBrowseMode.FAVORITES,
                                icon = Icons.Filled.Star,
                                onClick = { browseMode = OverlayBrowseMode.FAVORITES }
                            )
                        }

                        // Environment & S-Tier quick filter
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OverlayEnvChip("ALL", envFilter == null, Icons.Default.Public) { envFilter = null }
                            OverlayEnvChip("LIVE", envFilter == TftEnvironment.LIVE, Icons.Default.Bolt) { envFilter = TftEnvironment.LIVE }
                            OverlayEnvChip("PBE", envFilter == TftEnvironment.PBE, Icons.Default.Science) { envFilter = TftEnvironment.PBE }
                            OverlayEnvChip("S-Tier", tierFilter == TierRank.S, Icons.Default.WorkspacePremium) {
                                tierFilter = if (tierFilter == TierRank.S) null else TierRank.S
                            }
                        }

                        AnimatedContent(
                            targetState = Triple(browseMode, envFilter, tierFilter),
                            transitionSpec = {
                                fadeIn(tween(180)) togetherWith fadeOut(tween(130))
                            },
                            label = "overlayCompsFilterTransition"
                        ) { (activeMode, _, _) ->
                            if (displayed.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(DeepSlate, RoundedCornerShape(12.dp))
                                        .padding(14.dp)
                                ) {
                                    Text(
                                        text = if (activeMode == OverlayBrowseMode.FAVORITES) {
                                            "No favorites yet. Tap the Star icon on any composition in All Comps to add it to your favorites."
                                        } else {
                                            "No compositions match the active LIVE/PBE or tier filter."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF93A1B8)
                                    )
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 390.dp)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    displayed.forEach { comp ->
                                        val showHex = selectedHexCompId == comp.id
                                        val isFav = favoriteIds.contains(comp.id)
                                        OverlayCompCard(
                                            comp = comp,
                                            isFavorite = isFav,
                                            expanded = showHex,
                                            onCardClick = {
                                                selectedHexCompId = if (showHex) null else comp.id
                                            },
                                            onToggleFavorite = { onToggleFavorite(comp) }
                                        )
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
                                text = "Auto-sync 5m • Live + PBE",
                                fontSize = 9.sp,
                                color = Color(0xFF93A1B8)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverlayCompCard(
    comp: MetaComp,
    isFavorite: Boolean,
    expanded: Boolean,
    onCardClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val unitCosts = remember(comp.hexPlacements) {
        comp.hexPlacements.associate { it.unitName to it.cost }
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = DeepSlate,
        border = BorderStroke(
            width = if (isFavorite) 1.3.dp else 1.dp,
            color = if (isFavorite) BrushedGold.copy(alpha = 0.8f) else SlateOutline
        ),
        modifier = Modifier
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
                    text = "Carry: ${comp.carryName} • ${comp.playstyle} • Avg ${String.format("%.2f", comp.avgPlacement)}",
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
                        TftTraitChip(traitLabel = trait)
                    }
                }
            }

            Spacer(Modifier.height(5.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                comp.coreUnits.take(8).forEach { unit ->
                    val isCarryUnit = unit.equals(comp.carryName, ignoreCase = true)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier
                            .background(
                                if (isCarryUnit) BrushedGold.copy(alpha = 0.25f) else SlateElevated,
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        TftChampionAvatar(
                            name = unit,
                            cost = unitCosts[unit] ?: if (isCarryUnit) 4 else 3,
                            isCarry = isCarryUnit,
                            size = 16.dp
                        )
                        Text(
                            text = unit,
                            fontSize = 10.sp,
                            color = if (isCarryUnit) BrightGold else Color(0xFFE6ECF5)
                        )
                    }
                }
            }

            if (expanded) {
                val itemizedUnits = remember(comp.hexPlacements) {
                    comp.hexPlacements.filter { it.items.isNotEmpty() }
                }
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
                val secondaryItemized = itemizedUnits.filterNot { it.isCarry }
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
                                    size = 18.dp
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
                    MiniOverlayHexGrid(comp.hexPlacements)
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
                if (comp.levelingGuide.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = comp.levelingGuide,
                        fontSize = 10.sp,
                        color = Color(0xFFB7C4D8)
                    )
                }
            } else {
                Text(
                    text = "Tap card for BIS items, 4×7 hex board & augments",
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
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) BrushedGold.copy(alpha = 0.22f) else SlateElevated,
        border = BorderStroke(1.dp, if (selected) BrightGold else SlateOutline)
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
                    tint = if (selected) BrightGold else Color(0xFF93A1B8),
                    modifier = Modifier.size(12.dp)
                )
            }
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) BrightGold else Color(0xFF93A1B8),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MiniOverlayHexGrid(placements: List<HexUnitPlacement>) {
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
                .height(106.dp)
        ) {
            val cols = 7
            val rows = 4
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            val radius = (widthPx / (cols * 2.2f)).coerceAtMost(heightPx / (rows * 2.1f))
            val dx = radius * 1.95f
            val dy = radius * 1.65f
            val occupied = placements.associateBy { it.row to it.col }

            Canvas(modifier = Modifier.matchParentSize()) {
                for (r in 0 until rows) {
                    for (c in 0 until cols) {
                        val xOffset = if (r % 2 == 1) dx * 0.5f else 0f
                        val cx = radius * 1.2f + c * dx + xOffset
                        val cy = radius * 1.1f + r * dy
                        val unit = occupied[r to c]
                        val path = Path().apply {
                            for (i in 0 until 6) {
                                val angle = Math.toRadians((60 * i - 30).toDouble())
                                val px = cx + radius * 0.88f * cos(angle).toFloat()
                                val py = cy + radius * 0.88f * sin(angle).toFloat()
                                if (i == 0) moveTo(px, py) else lineTo(px, py)
                            }
                            close()
                        }
                        when {
                            unit?.isCarry == true -> {
                                drawPath(path, color = BrightGold.copy(alpha = 0.35f))
                                drawPath(path, color = BrightGold, style = Stroke(width = 2f))
                            }
                            unit != null -> {
                                drawPath(path, color = ArcaneCyan.copy(alpha = 0.25f))
                                drawPath(path, color = ArcaneCyan, style = Stroke(width = 1.6f))
                            }
                            else -> {
                                drawPath(path, color = SlateOutline.copy(alpha = 0.7f), style = Stroke(width = 1f))
                            }
                        }
                    }
                }
            }

            val boxPx = (radius * 1.7f).roundToInt().coerceAtLeast(18)
            val boxDp = with(density) { boxPx.toDp() }
            placements.forEach { unit ->
                val xOffset = if (unit.row % 2 == 1) dx * 0.5f else 0f
                val cx = radius * 1.2f + unit.col * dx + xOffset
                val cy = radius * 1.1f + unit.row * dy
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (cx - boxPx / 2f).roundToInt(),
                                (cy - boxPx / 2f).roundToInt()
                            )
                        }
                        .size(boxDp),
                    contentAlignment = Alignment.Center
                ) {
                    TftChampionAvatar(
                        name = unit.unitName,
                        cost = unit.cost,
                        isCarry = unit.isCarry,
                        stars = unit.stars,
                        size = (boxDp - 4.dp).coerceIn(13.dp, 20.dp)
                    )
                }
            }
        }
    }
}
