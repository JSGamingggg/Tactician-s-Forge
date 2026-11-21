package com.example.overlay

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
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
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
import com.example.data.model.ChampionTierEntry
import com.example.data.model.FavoriteCompDoc
import com.example.data.model.HexUnitPlacement
import com.example.data.model.ItemTierEntry
import com.example.data.model.MetaComp
import com.example.data.model.TftEnvironment
import com.example.data.model.TierRank
import com.example.data.repository.FirestoreUserRepository
import com.example.data.repository.LocalPreferencesStore
import com.example.data.repository.MetaCacheUpdates
import com.example.data.repository.MetaRepository
import com.example.data.teamplanner.TftTeamPlannerCodeEncoder
import com.example.notifications.MetaUpdateNotifier
import com.example.notifications.TftNotificationHelper
import com.example.ui.components.ChampionDetailSheetContent
import com.example.ui.components.CompFullBoardRosterSection
import com.example.ui.components.CompLevelTransitionGuideSection
import com.example.ui.components.TftAugmentChip
import com.example.ui.components.TftChampionAvatar
import com.example.ui.components.TftChampionTile
import com.example.ui.components.TftItemChip
import com.example.ui.components.TftItemIcon
import com.example.ui.components.TftTraitChip
import com.example.ui.components.TierBadge
import com.example.ui.components.championCostColor
import com.example.ui.components.formatRiotAssetLabel
import com.example.ui.components.resolveChampionDetailProfile
import com.example.ui.components.resolveCompUnitCost
import com.example.ui.components.sanitizeExternalSourceMentions
import com.example.ui.components.sortCompUnitsByCostAscending
import com.example.ui.util.DisplayRefreshRateHelper
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
import kotlinx.coroutines.withContext
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

internal const val DEFAULT_LANDSCAPE_OVERLAY_WIDTH_DP = 650
internal const val DEFAULT_LANDSCAPE_OVERLAY_HEIGHT_DP = 391
internal const val OVERLAY_BAUBLE_TOUCH_TARGET_DP = 58
internal const val OVERLAY_BAUBLE_VISUAL_SIZE_DP = 48
internal const val MIN_OVERLAY_OPACITY = LocalPreferencesStore.MIN_OVERLAY_OPACITY
internal const val MAX_OVERLAY_OPACITY = LocalPreferencesStore.MAX_OVERLAY_OPACITY
internal const val DEFAULT_OVERLAY_OPACITY = LocalPreferencesStore.DEFAULT_OVERLAY_OPACITY
internal const val MIN_BAUBLE_IDLE_ALPHA = 0.60f

internal fun clampOverlayOpacity(raw: Float): Float =
    if (raw.isNaN()) DEFAULT_OVERLAY_OPACITY else raw.coerceIn(MIN_OVERLAY_OPACITY, MAX_OVERLAY_OPACITY)

/**
 * Computes a visibility-safe alpha for the floating bauble based on the user's overlay opacity.
 * While pressed, dragging, or open, the bauble snaps to full opacity so it never gets lost;
 * while idle, it dims gently down to [MIN_BAUBLE_IDLE_ALPHA] so it remains unobtrusive yet clearly visible.
 */
internal fun resolveBaubleAlpha(overlayOpacity: Float, isInteractive: Boolean): Float {
    if (isInteractive) return 1f
    val clamped = clampOverlayOpacity(overlayOpacity)
    val fraction = ((clamped - MIN_OVERLAY_OPACITY) / (MAX_OVERLAY_OPACITY - MIN_OVERLAY_OPACITY)).coerceIn(0f, 1f)
    return (MIN_BAUBLE_IDLE_ALPHA + fraction * (1f - MIN_BAUBLE_IDLE_ALPHA)).coerceIn(MIN_BAUBLE_IDLE_ALPHA, 1f)
}

data class OverlayDrawerDimensions(
    val widthDp: Dp,
    val maxHeightDp: Dp,
    val isLandscape: Boolean,
    val useSideDetailPane: Boolean = widthDp >= 480.dp,
    val useTwoColumnCards: Boolean = widthDp >= 430.dp,
    val useFixedDefaultHeight: Boolean = isLandscape
)

internal fun computeOverlayDrawerDimensions(
    screenWidthDp: Int,
    screenHeightDp: Int
): OverlayDrawerDimensions {
    val safeW = screenWidthDp.coerceAtLeast(240)
    val safeH = screenHeightDp.coerceAtLeast(240)
    val isLandscape = safeW > safeH
    val widthDp = if (isLandscape) {
        // Default to 650dp in landscape, clamped safely inside usable screen width on compact displays
        DEFAULT_LANDSCAPE_OVERLAY_WIDTH_DP
            .coerceAtMost((safeW - 16).coerceAtLeast(280))
            .dp
    } else {
        // Keep landscape card proportions (width > height) even if opened before display finishes rotating
        (safeW - 16).coerceAtLeast(260).dp
    }
    val maxHeightDp = if (isLandscape) {
        // Default to 391dp in landscape, clamped safely inside usable screen height on compact displays
        DEFAULT_LANDSCAPE_OVERLAY_HEIGHT_DP
            .coerceAtMost((safeH - 16).coerceAtLeast(190))
            .dp
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
        useTwoColumnCards = widthDp >= 430.dp,
        useFixedDefaultHeight = isLandscape
    )
}

internal fun shouldResetOverlaySizeForOrientationChange(
    previousIsLandscape: Boolean?,
    currentIsLandscape: Boolean
): Boolean = previousIsLandscape != null && previousIsLandscape != currentIsLandscape

internal fun resolveEffectiveOverlayHeightDp(
    customHeightDp: Dp?,
    defaultDims: OverlayDrawerDimensions
): Dp? = customHeightDp ?: defaultDims.maxHeightDp.takeIf { defaultDims.useFixedDefaultHeight }

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

internal fun toggleOverlayDisplayState(current: OverlayDisplayState): OverlayDisplayState =
    if (current == OverlayDisplayState.EXPANDED) {
        OverlayDisplayState.BUBBLE
    } else {
        OverlayDisplayState.EXPANDED
    }

internal fun clampBubbleWindowPosition(
    desiredX: Int,
    desiredY: Int,
    viewWidthPx: Int,
    viewHeightPx: Int,
    bounds: OverlayWindowBounds,
    marginPx: Int = 0
): Pair<Int, Int> {
    val safeW = viewWidthPx.coerceAtLeast(1)
    val safeH = viewHeightPx.coerceAtLeast(1)
    val safeMargin = marginPx.coerceAtLeast(0)
    val minX = bounds.safeLeftPx + safeMargin
    val maxX = (bounds.widthPx - bounds.safeRightPx - safeW - safeMargin).coerceAtLeast(minX)
    val minY = bounds.safeTopPx + safeMargin
    val maxY = (bounds.heightPx - bounds.safeBottomPx - safeH - safeMargin).coerceAtLeast(minY)
    return desiredX.coerceIn(minX, maxX) to desiredY.coerceIn(minY, maxY)
}

/**
 * Unified root touch state machine for the floating overlay bauble.
 * Handles touches starting from ANY point on the bauble (center, icon, badge, or edge),
 * cleanly separating taps (open/close drawer) from drags (reposition bauble anywhere).
 */
internal class OverlayBaubleGestureController(
    private val touchSlopPx: Int,
    private val marginPx: Int,
    private val currentOriginProvider: () -> Pair<Int, Int>,
    private val viewSizeProvider: () -> Pair<Int, Int>,
    private val boundsProvider: () -> OverlayWindowBounds,
    private val onPressedChanged: (Boolean) -> Unit,
    private val onDraggingChanged: (Boolean) -> Unit,
    private val onPositionUpdated: (Int, Int) -> Unit,
    private val onTap: () -> Unit
) {
    private var startX = 0
    private var startY = 0
    private var initialRawX = 0f
    private var initialRawY = 0f
    private var moved = false
    private var tracking = false

    fun onTouchEvent(actionMasked: Int, rawX: Float, rawY: Float): Boolean {
        val (viewW, viewH) = viewSizeProvider()
        val bounds = boundsProvider()

        return when (actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val (originX, originY) = currentOriginProvider()
                startX = originX
                startY = originY
                initialRawX = rawX
                initialRawY = rawY
                moved = false
                tracking = true
                onPressedChanged(true)
                onDraggingChanged(false)
                true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!tracking) return false
                val dx = (rawX - initialRawX).roundToInt()
                val dy = (rawY - initialRawY).roundToInt()
                if (moved || abs(dx) > touchSlopPx || abs(dy) > touchSlopPx) {
                    moved = true
                    onDraggingChanged(true)
                    val (clampedX, clampedY) = clampBubbleWindowPosition(
                        desiredX = startX + dx,
                        desiredY = startY + dy,
                        viewWidthPx = viewW,
                        viewHeightPx = viewH,
                        bounds = bounds,
                        marginPx = marginPx
                    )
                    onPositionUpdated(clampedX, clampedY)
                }
                true
            }
            MotionEvent.ACTION_UP -> {
                if (!tracking) return false
                tracking = false
                onPressedChanged(false)
                onDraggingChanged(false)
                if (moved) {
                    val (curX, curY) = currentOriginProvider()
                    val (clampedX, clampedY) = clampBubbleWindowPosition(
                        desiredX = curX,
                        desiredY = curY,
                        viewWidthPx = viewW,
                        viewHeightPx = viewH,
                        bounds = bounds,
                        marginPx = marginPx
                    )
                    onPositionUpdated(clampedX, clampedY)
                } else {
                    onTap()
                }
                moved = false
                true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (!tracking) return false
                tracking = false
                onPressedChanged(false)
                onDraggingChanged(false)
                if (moved) {
                    val (curX, curY) = currentOriginProvider()
                    val (clampedX, clampedY) = clampBubbleWindowPosition(
                        desiredX = curX,
                        desiredY = curY,
                        viewWidthPx = viewW,
                        viewHeightPx = viewH,
                        bounds = bounds,
                        marginPx = marginPx
                    )
                    onPositionUpdated(clampedX, clampedY)
                }
                moved = false
                true
            }
            else -> false
        }
    }
}

class OverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private var displayManager: DisplayManager? = null
    private lateinit var prefsStore: LocalPreferencesStore
    private lateinit var metaRepository: MetaRepository
    private lateinit var firestoreRepo: FirestoreUserRepository
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var drawerView: ComposeView? = null
    private lateinit var drawerLayoutParams: WindowManager.LayoutParams
    private var bubbleView: View? = null
    private var bubbleComposeContentView: ComposeView? = null
    private lateinit var bubbleLayoutParams: WindowManager.LayoutParams

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    private val displayState = mutableStateOf(OverlayDisplayState.BUBBLE)
    private val dockEdgeState = mutableStateOf(OverlayDockEdge.LEFT)
    private val isDraggingState = mutableStateOf(false)
    private val isBaublePressedState = mutableStateOf(false)
    private val overlayOpacityState = mutableFloatStateOf(DEFAULT_OVERLAY_OPACITY)
    private val customWidthDpState = mutableStateOf<Float?>(null)
    private val customHeightDpState = mutableStateOf<Float?>(null)
    private var savedExpandedOriginPx: Pair<Int, Int>? = null
    private var savedBubblePosPx: Pair<Int, Int>? = null
    private var lastKnownIsLandscape: Boolean? = null
    private val configurationVersion = mutableIntStateOf(0)
    private val allCompsState = mutableStateOf<List<MetaComp>>(emptyList())
    private val allChampionsState = mutableStateOf<List<ChampionTierEntry>>(emptyList())
    private val allItemsState = mutableStateOf<List<ItemTierEntry>>(emptyList())
    private val favoriteDocsState = mutableStateOf<List<FavoriteCompDoc>>(emptyList())
    private val sessionState = OverlaySessionState()
    private val copiedCompIdState = mutableStateOf<String?>(null)
    private val copyBannerMessageState = mutableStateOf<String?>(null)
    private val copyBannerIsErrorState = mutableStateOf(false)
    private var isSearchInputFocused = false
    private var copyFeedbackClearJob: Job? = null
    private var favoritesObserverJob: Job? = null
    private var activeOverlaySyncJob: Job? = null

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
        overlayOpacityState.floatValue = prefsStore.readOverlayOpacity()
        metaRepository = MetaRepository(this)
        firestoreRepo = FirestoreUserRepository(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        displayManager = (getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)?.also { dm ->
            dm.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
        }

        reloadCompsFromCache()
        observeCacheAndFavorites()
        startOverlayAutoSyncLoop()
        warmTeamPlannerMappings()

        TftNotificationHelper.ensureChannels(this)
        startCompliantForeground()
        attachOverlayView()
        OverlayController.setRunning(true)
    }

    private fun warmTeamPlannerMappings() {
        serviceScope.launch {
            runCatching {
                val liveSet = allCompsState.value
                    .firstOrNull { it.environment == TftEnvironment.LIVE }
                    ?.let { TftTeamPlannerCodeEncoder.extractTargetSetNumber(it) }
                    ?: 18
                val pbeSet = allCompsState.value
                    .firstOrNull { it.environment == TftEnvironment.PBE }
                    ?.let { TftTeamPlannerCodeEncoder.extractTargetSetNumber(it) }
                    ?: 19
                TftTeamPlannerCodeEncoder.ensureMappingLoaded(
                    context = applicationContext,
                    environment = TftEnvironment.LIVE,
                    requiredSetNumber = liveSet
                )
                TftTeamPlannerCodeEncoder.ensureMappingLoaded(
                    context = applicationContext,
                    environment = TftEnvironment.PBE,
                    requiredSetNumber = pbeSet
                )
            }
        }
    }

    private fun copyCompTeamCode(comp: MetaComp) {
        copyFeedbackClearJob?.cancel()
        copyBannerIsErrorState.value = false
        copyBannerMessageState.value = "Syncing ${comp.environment.label} Team Planner code for ${comp.title}…"
        serviceScope.launch {
            val result = TftTeamPlannerCodeEncoder.resolveTeamCode(applicationContext, comp)
            if (result.isSuccess) {
                val copiedOk = withContext(Dispatchers.Main.immediate) {
                    runCatching {
                        val clipboard = applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            ?: return@runCatching false
                        clipboard.setPrimaryClip(ClipData.newPlainText("TFT Team Planner Code", result.code))
                        true
                    }.getOrDefault(false)
                }
                if (copiedOk) {
                    copiedCompIdState.value = comp.id
                    copyBannerIsErrorState.value = false
                    copyBannerMessageState.value =
                        "Copied ${comp.environment.label} (${result.setKey}) code (${result.matchedChampions.size} units) • Paste in TFT"
                } else {
                    copiedCompIdState.value = null
                    copyBannerIsErrorState.value = true
                    copyBannerMessageState.value =
                        "Could not access clipboard for ${comp.environment.label} code"
                }
            } else {
                copiedCompIdState.value = null
                copyBannerIsErrorState.value = true
                copyBannerMessageState.value =
                    result.errorMessage ?: "Could not load Riot ${result.setKey} IDs — check connection"
            }
            copyFeedbackClearJob?.cancel()
            copyFeedbackClearJob = serviceScope.launch {
                delay(if (copyBannerIsErrorState.value) 6000L else 3000L)
                copiedCompIdState.value = null
                copyBannerMessageState.value = null
                copyBannerIsErrorState.value = false
            }
        }
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
            allChampionsState.value = bundle.liveChampions + bundle.pbeChampions
            allItemsState.value = bundle.liveItems + bundle.pbeItems
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
            allChampionsState.value = bundle.liveChampions + bundle.pbeChampions
            allItemsState.value = bundle.liveItems + bundle.pbeItems
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
            .setContentText("Tap floating bauble to open or close overlay; drag to move it anywhere.")
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

    private fun syncOrientationTransition(bounds: OverlayWindowBounds) {
        val currentIsLandscape = bounds.usableWidthPx > bounds.usableHeightPx
        if (shouldResetOverlaySizeForOrientationChange(lastKnownIsLandscape, currentIsLandscape)) {
            customWidthDpState.value = null
            customHeightDpState.value = null
            savedExpandedOriginPx = null
        }
        lastKnownIsLandscape = currentIsLandscape
    }

    private fun activeExpandedHeightDpValue(bounds: OverlayWindowBounds = currentWindowBounds()): Float {
        customHeightDpState.value?.let { return it }
        val defaultDims = currentDrawerDimensions(bounds)
        if (defaultDims.useFixedDefaultHeight) {
            return defaultDims.maxHeightDp.value
        }
        val measuredPx = drawerView?.height?.takeIf { it > 0 }
        if (measuredPx != null) {
            val sizeBounds = currentSizeBounds(bounds)
            return (measuredPx / bounds.density).coerceIn(sizeBounds.minHeightDp, sizeBounds.maxHeightDp)
        }
        return defaultDims.maxHeightDp.value
    }

    private fun applyExpandedBoundsToWindow(
        widthDp: Float?,
        heightDp: Float?,
        targetX: Int? = null,
        targetY: Int? = null
    ) {
        if (!::drawerLayoutParams.isInitialized) return
        val bounds = currentWindowBounds()
        val sizeBounds = currentSizeBounds(bounds)
        val defaultDims = currentDrawerDimensions(bounds)
        val (clampedW, clampedH) = clampOverlayCustomSize(widthDp, heightDp, defaultDims, sizeBounds)
        val effectiveFixedH = resolveEffectiveOverlayHeightDp(clampedH, defaultDims)

        customWidthDpState.value = widthDp?.let { clampedW.value }
        customHeightDpState.value = clampedH?.value

        val effectiveWpx = (clampedW.value * bounds.density).roundToInt()
        val effectiveHpx = ((effectiveFixedH?.value ?: defaultDims.maxHeightDp.value) * bounds.density).roundToInt()
        val minLeftX = bounds.safeLeftPx + dpToPx(8)
        val maxLeftX = (bounds.widthPx - bounds.safeRightPx - effectiveWpx - dpToPx(8)).coerceAtLeast(minLeftX)
        val minTopY = bounds.safeTopPx + dpToPx(8)
        val maxTopY = (bounds.heightPx - bounds.safeBottomPx - effectiveHpx - dpToPx(8)).coerceAtLeast(minTopY)

        drawerLayoutParams.gravity = Gravity.TOP or Gravity.START
        drawerLayoutParams.width = effectiveWpx
        drawerLayoutParams.height = if (effectiveFixedH != null) effectiveHpx else WindowManager.LayoutParams.WRAP_CONTENT
        val candidateX = targetX ?: savedExpandedOriginPx?.first ?: drawerLayoutParams.x
        val candidateY = targetY ?: savedExpandedOriginPx?.second ?: drawerLayoutParams.y
        val clampedX = candidateX.coerceIn(minLeftX, maxLeftX)
        val clampedY = candidateY.coerceIn(minTopY, maxTopY)
        drawerLayoutParams.x = clampedX
        drawerLayoutParams.y = clampedY
        if (savedExpandedOriginPx != null || targetX != null || targetY != null) {
            savedExpandedOriginPx = clampedX to clampedY
        }

        drawerView?.let { view ->
            if (view.isAttachedToWindow) {
                runCatching {
                    windowManager.updateViewLayout(view, drawerLayoutParams)
                    view.requestLayout()
                }
            }
        }
    }

    private fun extendExpandedToFullWidth() {
        if (!::drawerLayoutParams.isInitialized) return
        val bounds = currentWindowBounds()
        val sizeBounds = currentSizeBounds(bounds)
        val minLeftX = bounds.safeLeftPx + dpToPx(8)
        applyExpandedBoundsToWindow(
            widthDp = sizeBounds.maxWidthDp,
            heightDp = customHeightDpState.value,
            targetX = minLeftX,
            targetY = savedExpandedOriginPx?.second ?: drawerLayoutParams.y
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
        val bounds = currentWindowBounds()
        syncOrientationTransition(bounds)
        val expandedMarginPx = dpToPx(8)
        val minY = bounds.safeTopPx + expandedMarginPx

        if (::drawerLayoutParams.isInitialized) {
            val defaultDims = currentDrawerDimensions(bounds)
            val sizeBounds = currentSizeBounds(bounds)
            val (activeW, activeH) = clampOverlayCustomSize(
                customWidthDpState.value,
                customHeightDpState.value,
                defaultDims,
                sizeBounds
            )
            val effectiveFixedH = resolveEffectiveOverlayHeightDp(activeH, defaultDims)
            if (customWidthDpState.value != null) customWidthDpState.value = activeW.value
            if (customHeightDpState.value != null) customHeightDpState.value = activeH?.value

            val effectiveWpx = (activeW.value * bounds.density).roundToInt()
            val effectiveHpx = ((effectiveFixedH?.value ?: defaultDims.maxHeightDp.value) * bounds.density).roundToInt()
            val minExpandedX = bounds.safeLeftPx + expandedMarginPx
            val maxExpandedX = (bounds.widthPx - bounds.safeRightPx - effectiveWpx - expandedMarginPx).coerceAtLeast(minExpandedX)
            val maxExpandedY = (bounds.heightPx - bounds.safeBottomPx - effectiveHpx - expandedMarginPx).coerceAtLeast(minY)
            val defaultExpandedX = if (defaultDims.isLandscape) (minExpandedX + maxExpandedX) / 2 else minExpandedX
            val defaultExpandedY = if (defaultDims.isLandscape) (minY + maxExpandedY) / 2 else (bounds.safeTopPx + dpToPx(72))

            drawerLayoutParams.screenOrientation = resolveOverlayWindowScreenOrientation(OverlayDisplayState.EXPANDED)
            drawerLayoutParams.gravity = Gravity.TOP or Gravity.START
            drawerLayoutParams.width = effectiveWpx
            drawerLayoutParams.height = if (effectiveFixedH != null) effectiveHpx else WindowManager.LayoutParams.WRAP_CONTENT
            val preferredX = savedExpandedOriginPx?.first ?: defaultExpandedX
            val preferredY = savedExpandedOriginPx?.second ?: defaultExpandedY
            drawerLayoutParams.x = preferredX.coerceIn(minExpandedX, maxExpandedX)
            drawerLayoutParams.y = preferredY.coerceIn(minY, maxExpandedY)
            DisplayRefreshRateHelper.applyToLayoutParams(this, drawerLayoutParams)

            drawerView?.let { view ->
                if (view.isAttachedToWindow) {
                    runCatching {
                        windowManager.updateViewLayout(view, drawerLayoutParams)
                        view.requestLayout()
                    }
                }
            }
        }

        if (::bubbleLayoutParams.isInitialized) {
            val viewW = bubbleView?.width?.takeIf { it > 0 } ?: dpToPx(OVERLAY_BAUBLE_TOUCH_TARGET_DP)
            val viewH = bubbleView?.height?.takeIf { it > 0 } ?: dpToPx(OVERLAY_BAUBLE_TOUCH_TARGET_DP)
            val targetX = savedBubblePosPx?.first ?: bubbleLayoutParams.x
            val targetY = savedBubblePosPx?.second ?: bubbleLayoutParams.y
            val (clampedX, clampedY) = clampBubbleWindowPosition(
                desiredX = targetX,
                desiredY = targetY,
                viewWidthPx = viewW,
                viewHeightPx = viewH,
                bounds = bounds,
                marginPx = dpToPx(6)
            )
            bubbleLayoutParams.screenOrientation = resolveOverlayWindowScreenOrientation(OverlayDisplayState.BUBBLE)
            bubbleLayoutParams.x = clampedX
            bubbleLayoutParams.y = clampedY
            DisplayRefreshRateHelper.applyToLayoutParams(this, bubbleLayoutParams)
            savedBubblePosPx = clampedX to clampedY

            bubbleView?.let { view ->
                if (view.isAttachedToWindow) {
                    runCatching {
                        windowManager.updateViewLayout(view, bubbleLayoutParams)
                        view.requestLayout()
                    }
                }
            }
        }
    }

    private fun syncDrawerWindowState(isOpen: Boolean) {
        if (!::drawerLayoutParams.isInitialized) return
        val bounds = currentWindowBounds()
        syncOrientationTransition(bounds)
        val expandedMarginPx = dpToPx(8)
        val minY = bounds.safeTopPx + expandedMarginPx
        val defaultDims = currentDrawerDimensions(bounds)
        val sizeBounds = currentSizeBounds(bounds)
        val (activeW, activeH) = clampOverlayCustomSize(
            customWidthDpState.value,
            customHeightDpState.value,
            defaultDims,
            sizeBounds
        )
        val effectiveFixedH = resolveEffectiveOverlayHeightDp(activeH, defaultDims)
        val effectiveWpx = (activeW.value * bounds.density).roundToInt()
        val effectiveHpx = ((effectiveFixedH?.value ?: defaultDims.maxHeightDp.value) * bounds.density).roundToInt()
        val minExpandedX = bounds.safeLeftPx + expandedMarginPx
        val maxExpandedX = (bounds.widthPx - bounds.safeRightPx - effectiveWpx - expandedMarginPx).coerceAtLeast(minExpandedX)
        val maxExpandedY = (bounds.heightPx - bounds.safeBottomPx - effectiveHpx - expandedMarginPx).coerceAtLeast(minY)
        val defaultExpandedX = if (defaultDims.isLandscape) (minExpandedX + maxExpandedX) / 2 else minExpandedX
        val defaultExpandedY = if (defaultDims.isLandscape) (minY + maxExpandedY) / 2 else (bounds.safeTopPx + dpToPx(72))

        drawerLayoutParams.screenOrientation = resolveOverlayWindowScreenOrientation(displayState.value)
        drawerLayoutParams.gravity = Gravity.TOP or Gravity.START
        drawerLayoutParams.width = effectiveWpx
        drawerLayoutParams.height = if (effectiveFixedH != null) effectiveHpx else WindowManager.LayoutParams.WRAP_CONTENT
        if (!isOpen) {
            isSearchInputFocused = false
        }
        drawerLayoutParams.flags = when {
            !isOpen -> WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            isSearchInputFocused -> WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            else -> WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        drawerLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN

        if (isOpen) {
            val preferredX = savedExpandedOriginPx?.first ?: defaultExpandedX
            val preferredY = savedExpandedOriginPx?.second ?: defaultExpandedY
            drawerLayoutParams.x = preferredX.coerceIn(minExpandedX, maxExpandedX)
            drawerLayoutParams.y = preferredY.coerceIn(minY, maxExpandedY)
        }

        drawerView?.let { view ->
            view.visibility = if (isOpen) View.VISIBLE else View.GONE
            if (view.isAttachedToWindow) {
                runCatching {
                    windowManager.updateViewLayout(view, drawerLayoutParams)
                    view.requestLayout()
                }
            }
        }
    }

    private fun setDrawerSearchFocusable(focusable: Boolean) {
        if (!::drawerLayoutParams.isInitialized) return
        val isOpen = displayState.value == OverlayDisplayState.EXPANDED
        val nextFocused = focusable && isOpen
        if (isSearchInputFocused == nextFocused) return
        isSearchInputFocused = nextFocused
        drawerLayoutParams.flags = when {
            !isOpen -> WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            nextFocused -> WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            else -> WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        drawerLayoutParams.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        drawerView?.let { view ->
            if (view.isAttachedToWindow) {
                runCatching { windowManager.updateViewLayout(view, drawerLayoutParams) }
            }
        }
    }

    private fun transitionDisplayState(
        targetState: OverlayDisplayState,
        dockEdge: OverlayDockEdge = dockEdgeState.value
    ) {
        dockEdgeState.value = dockEdge
        displayState.value = targetState
        val isOpen = targetState == OverlayDisplayState.EXPANDED
        syncDrawerWindowState(isOpen)
    }

    private fun toggleDrawerExpanded() {
        val nextState = toggleOverlayDisplayState(displayState.value)
        transitionDisplayState(nextState)
    }

    private fun updateOverlayOpacity(newOpacity: Float) {
        val clamped = clampOverlayOpacity(newOpacity)
        overlayOpacityState.floatValue = clamped
        prefsStore.saveOverlayOpacity(clamped)
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
        lastKnownIsLandscape = initialBounds.usableWidthPx > initialBounds.usableHeightPx
        val initialDrawerDims = currentDrawerDimensions(initialBounds)
        val initialFixedHeightDp = resolveEffectiveOverlayHeightDp(null, initialDrawerDims)
        val initialDrawerWpx = (initialDrawerDims.widthDp.value * initialBounds.density).roundToInt()
        val initialDrawerHpx = ((initialFixedHeightDp?.value ?: initialDrawerDims.maxHeightDp.value) * initialBounds.density).roundToInt()
        val initialMarginPx = dpToPx(8)
        val initialMinX = initialBounds.safeLeftPx + initialMarginPx
        val initialMaxX = (initialBounds.widthPx - initialBounds.safeRightPx - initialDrawerWpx - initialMarginPx).coerceAtLeast(initialMinX)
        val initialMinY = initialBounds.safeTopPx + initialMarginPx
        val initialMaxY = (initialBounds.heightPx - initialBounds.safeBottomPx - initialDrawerHpx - initialMarginPx).coerceAtLeast(initialMinY)
        val initialDrawerX = if (initialDrawerDims.isLandscape) (initialMinX + initialMaxX) / 2 else initialMinX
        val initialDrawerY = if (initialDrawerDims.isLandscape) {
            (initialMinY + initialMaxY) / 2
        } else {
            (initialBounds.safeTopPx + dpToPx(72)).coerceIn(initialMinY, initialMaxY)
        }

        drawerLayoutParams = WindowManager.LayoutParams(
            initialDrawerWpx,
            if (initialFixedHeightDp != null) initialDrawerHpx else WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialDrawerX
            y = initialDrawerY
            screenOrientation = resolveOverlayWindowScreenOrientation(OverlayDisplayState.EXPANDED)
            DisplayRefreshRateHelper.applyToLayoutParams(this@OverlayService, this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        val drawerComposeView = ComposeView(this).apply {
            visibility = View.GONE
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setContent {
                TacticiansForgeTheme {
                    OverlayDrawerContent(
                        isOpen = displayState.value == OverlayDisplayState.EXPANDED,
                        configVersion = configurationVersion.intValue,
                        customWidthDp = customWidthDpState.value,
                        customHeightDp = customHeightDpState.value,
                        overlayOpacity = overlayOpacityState.floatValue,
                        onOpacityChanged = ::updateOverlayOpacity,
                        allComps = allCompsState.value,
                        allChampions = allChampionsState.value,
                        allItems = allItemsState.value,
                        favoriteDocs = favoriteDocsState.value,
                        sessionState = sessionState,
                        copiedCompId = copiedCompIdState.value,
                        copyBannerMessage = copyBannerMessageState.value,
                        copyBannerIsError = copyBannerIsErrorState.value,
                        onSearchFocusChanged = ::setDrawerSearchFocusable,
                        windowOriginProvider = { drawerLayoutParams.x to drawerLayoutParams.y },
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
                        onCloseDrawer = {
                            setDrawerSearchFocusable(false)
                            transitionDisplayState(OverlayDisplayState.BUBBLE)
                        },
                        onRefresh = {
                            reloadCompsFromCache()
                            observeCacheAndFavorites()
                            syncOverlayMetaFromNetwork()
                        },
                        onToggleFavorite = ::toggleFavoriteInOverlay,
                        onCopyCompCode = ::copyCompTeamCode,
                        onStopOverlay = { stopSelf() }
                    )
                }
            }
        }

        val defaultBubbleSizePx = dpToPx(OVERLAY_BAUBLE_TOUCH_TARGET_DP)
        val (initialBubbleX, initialBubbleY) = clampBubbleWindowPosition(
            desiredX = initialBounds.safeLeftPx + dpToPx(12),
            desiredY = initialBounds.safeTopPx + dpToPx(18),
            viewWidthPx = defaultBubbleSizePx,
            viewHeightPx = defaultBubbleSizePx,
            bounds = initialBounds,
            marginPx = dpToPx(6)
        )
        savedBubblePosPx = initialBubbleX to initialBubbleY

        bubbleLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialBubbleX
            y = initialBubbleY
            screenOrientation = resolveOverlayWindowScreenOrientation(OverlayDisplayState.BUBBLE)
            DisplayRefreshRateHelper.applyToLayoutParams(this@OverlayService, this)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        val bubbleComposeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setContent {
                TacticiansForgeTheme {
                    OverlayFloatingBauble(
                        isDrawerOpen = displayState.value == OverlayDisplayState.EXPANDED,
                        isDragging = isDraggingState.value,
                        isPressed = isBaublePressedState.value,
                        overlayOpacity = overlayOpacityState.floatValue,
                        onToggleDrawer = ::toggleDrawerExpanded
                    )
                }
            }
        }

        val touchSlopPx = ViewConfiguration.get(this@OverlayService).scaledTouchSlop.coerceAtLeast(8)
        val bubbleContainer = object : FrameLayout(this) {
            private val gestureController = OverlayBaubleGestureController(
                touchSlopPx = touchSlopPx,
                marginPx = dpToPx(6),
                currentOriginProvider = { bubbleLayoutParams.x to bubbleLayoutParams.y },
                viewSizeProvider = {
                    (width.takeIf { it > 0 } ?: defaultBubbleSizePx) to
                        (height.takeIf { it > 0 } ?: defaultBubbleSizePx)
                },
                boundsProvider = ::currentWindowBounds,
                onPressedChanged = { pressed -> isBaublePressedState.value = pressed },
                onDraggingChanged = { dragging -> isDraggingState.value = dragging },
                onPositionUpdated = { clampedX, clampedY ->
                    bubbleLayoutParams.gravity = Gravity.TOP or Gravity.START
                    bubbleLayoutParams.x = clampedX
                    bubbleLayoutParams.y = clampedY
                    savedBubblePosPx = clampedX to clampedY
                    if (isAttachedToWindow) {
                        runCatching { windowManager.updateViewLayout(this, bubbleLayoutParams) }
                    }
                },
                onTap = { performClick() }
            )

            override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                // Intercept at the root container BEFORE child Compose views so dragging works
                // seamlessly from ANY point on the bauble (center, icon, badge, or edge).
                return gestureController.onTouchEvent(ev.actionMasked, ev.rawX, ev.rawY) ||
                    super.dispatchTouchEvent(ev)
            }

            override fun performClick(): Boolean {
                super.performClick()
                toggleDrawerExpanded()
                return true
            }
        }.apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            addView(
                bubbleComposeView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        // Attach drawer first, then bubble second so the bauble always remains visible above the drawer
        drawerView = drawerComposeView
        windowManager.addView(drawerComposeView, drawerLayoutParams)
        bubbleComposeContentView = bubbleComposeView
        bubbleView = bubbleContainer
        windowManager.addView(bubbleContainer, bubbleLayoutParams)
    }

    override fun onDestroy() {
        super.onDestroy()
        displayManager?.unregisterDisplayListener(displayListener)
        displayManager = null
        copyFeedbackClearJob?.cancel()
        copiedCompIdState.value = null
        copyBannerMessageState.value = null
        // Clear remembered filters and composition only when overlay is fully closed off
        sessionState.reset()
        serviceScope.cancel()
        OverlayController.setRunning(false)
        bubbleComposeContentView?.let { composeView ->
            runCatching { composeView.disposeComposition() }
        }
        bubbleComposeContentView = null
        bubbleView?.let { view ->
            if (view.isAttachedToWindow) {
                runCatching { windowManager.removeView(view) }
            }
        }
        bubbleView = null
        drawerView?.let { view ->
            runCatching { view.disposeComposition() }
            if (view.isAttachedToWindow) {
                runCatching { windowManager.removeView(view) }
            }
        }
        drawerView = null
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

@Composable
private fun OverlayFloatingBauble(
    isDrawerOpen: Boolean,
    isDragging: Boolean,
    isPressed: Boolean,
    overlayOpacity: Float,
    onToggleDrawer: () -> Unit
) {
    val dragScale by animateFloatAsState(
        targetValue = when {
            isDragging -> 1.12f
            isPressed -> 0.94f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "overlayBubbleDragScale"
    )
    val baubleAlpha by animateFloatAsState(
        targetValue = resolveBaubleAlpha(
            overlayOpacity = overlayOpacity,
            isInteractive = isDrawerOpen || isDragging || isPressed
        ),
        animationSpec = tween(durationMillis = 160),
        label = "overlayBubbleAlpha"
    )
    val borderBrush = Brush.linearGradient(
        when {
            isDragging -> listOf(ArcaneCyan, BrightGold)
            isDrawerOpen -> listOf(ArcaneCyan, BrightGold, ArcaneCyan)
            else -> listOf(BrightGold, ArcaneCyan)
        }
    )
    val actionLabel = if (isDrawerOpen) {
        "Close TFT's Helper Live Match Overlay or drag anywhere to move"
    } else {
        "Open TFT's Helper Live Match Overlay or drag anywhere to move"
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(OVERLAY_BAUBLE_TOUCH_TARGET_DP.dp)
            .graphicsLayer {
                scaleX = dragScale
                scaleY = dragScale
                alpha = baubleAlpha
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = actionLabel
                onClick(label = actionLabel) {
                    onToggleDrawer()
                    true
                }
            }
            .testTag("overlay_bubble_button")
    ) {
        // Non-clickable Surface so root touch handler receives 100% of drag & tap events
        // across the entire 58dp hit zone while the visual bauble is 48dp (with headroom for drag scale & shadow).
        Surface(
            shape = CircleShape,
            color = if (isDrawerOpen) DeepSlate.copy(alpha = 0.98f) else ObsidianVoid.copy(alpha = 0.96f),
            border = BorderStroke(
                width = if (isDragging || isDrawerOpen) 2.dp else 1.5.dp,
                brush = borderBrush
            ),
            shadowElevation = if (isDragging || isDrawerOpen) 8.dp else 5.dp,
            modifier = Modifier
                .size(OVERLAY_BAUBLE_VISUAL_SIZE_DP.dp)
                .testTag("overlay_bubble_visual")
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isDrawerOpen) {
                    Icon(
                        imageVector = Icons.Default.CloseFullscreen,
                        contentDescription = null,
                        tint = ArcaneCyan,
                        modifier = Modifier.size(21.dp)
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.ic_tfts_helper_emblem),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun OverlayDrawerContent(
    isOpen: Boolean,
    configVersion: Int,
    customWidthDp: Float?,
    customHeightDp: Float?,
    overlayOpacity: Float,
    onOpacityChanged: (Float) -> Unit,
    allComps: List<MetaComp>,
    allChampions: List<ChampionTierEntry> = emptyList(),
    allItems: List<ItemTierEntry> = emptyList(),
    favoriteDocs: List<FavoriteCompDoc>,
    sessionState: OverlaySessionState,
    copiedCompId: String?,
    copyBannerMessage: String?,
    copyBannerIsError: Boolean,
    onSearchFocusChanged: (Boolean) -> Unit,
    windowOriginProvider: () -> Pair<Int, Int>,
    windowSizeProvider: () -> Pair<Float, Float>,
    densityProvider: () -> Float,
    onMoveExpandedWindow: (Int, Int) -> Unit,
    onResizeExpandedWindow: (Float?, Float?, Int?) -> Unit,
    onExtendFullWidth: () -> Unit,
    onResetExpandedSize: () -> Unit,
    onCloseDrawer: () -> Unit,
    onRefresh: () -> Unit,
    onToggleFavorite: (MetaComp) -> Unit,
    onCopyCompCode: (MetaComp) -> Unit,
    onStopOverlay: () -> Unit
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
    val effectiveFixedHeightDp = resolveEffectiveOverlayHeightDp(activeHeightDp, defaultDrawerDims)
    val drawerDims = OverlayDrawerDimensions(
        widthDp = activeWidthDp,
        maxHeightDp = effectiveFixedHeightDp ?: defaultDrawerDims.maxHeightDp,
        isLandscape = defaultDrawerDims.isLandscape,
        useSideDetailPane = activeWidthDp >= 480.dp,
        useTwoColumnCards = activeWidthDp >= 430.dp,
        useFixedDefaultHeight = defaultDrawerDims.useFixedDefaultHeight
    )
    val isCustomResized = customWidthDp != null || customHeightDp != null
    if (!isOpen) return
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

    // All user inputs and selected composition live in OverlaySessionState until full close
    val browseMode = sessionState.browseMode
    val envFilter = sessionState.envFilter
    val tierFilter = sessionState.tierFilter
    val selectedCompId = sessionState.selectedCompId
    val inspectedOverlayChampion = sessionState.inspectedChampion
    val expandedOverlayTiers = sessionState.expandedTiers

    val favoriteComps = remember(mergedAllComps, favoriteDocs, favoriteIds) {
        val matched = mergedAllComps.filter { favoriteIds.contains(it.id) }
        val extra = favoriteDocs
            .filter { doc -> matched.none { it.id == doc.compId || it.id == doc.id } }
            .map { it.toMetaComp() }
        matched + extra
    }

    val liveAllComps = remember(mergedAllComps) {
        mergedAllComps.filter { it.environment == TftEnvironment.LIVE }
    }
    val pbeAllComps = remember(mergedAllComps) {
        mergedAllComps.filter { it.environment == TftEnvironment.PBE }
    }
    val liveAllCompsByTier = remember(liveAllComps) {
        groupCompsByTier(liveAllComps)
    }
    val pbeAllCompsByTier = remember(pbeAllComps) {
        groupCompsByTier(pbeAllComps)
    }
    val liveFavoriteComps = remember(favoriteComps) {
        favoriteComps.filter { it.environment == TftEnvironment.LIVE }
    }
    val pbeFavoriteComps = remember(favoriteComps) {
        favoriteComps.filter { it.environment == TftEnvironment.PBE }
    }

    val envAllComps = if (envFilter == TftEnvironment.LIVE) liveAllComps else pbeAllComps
    val envFavoriteComps = if (envFilter == TftEnvironment.LIVE) liveFavoriteComps else pbeFavoriteComps
    val envCatalogItems = remember(allItems, envFilter) {
        allItems.filter { it.environment == envFilter }
    }
    val championChoices = remember(sessionState.isArmyFinderOpen, envAllComps, allChampions, envFilter) {
        if (!sessionState.isArmyFinderOpen) emptyList()
        else OverlayArmySearchEngine.extractChampionChoices(envAllComps, allChampions, envFilter)
    }
    val itemChoices = remember(sessionState.isArmyFinderOpen, envAllComps, allItems, envFilter) {
        if (!sessionState.isArmyFinderOpen) emptyList()
        else OverlayArmySearchEngine.extractItemChoices(envAllComps, allItems, envFilter)
    }

    val envScopedCompsCount = envAllComps.size
    val envScopedFavoritesCount = envFavoriteComps.size

    val selectedChampions = sessionState.selectedChampions
    val selectedItems = sessionState.selectedItems
    val finderQuery = sessionState.finderQuery
    val hasActiveArmyFilters = sessionState.hasActiveArmyFilters

    val isDefaultAllCompsView = browseMode == OverlayBrowseMode.ALL_COMPS &&
        tierFilter == null &&
        !hasActiveArmyFilters

    val displayed = remember(
        isDefaultAllCompsView,
        envAllComps,
        envFavoriteComps,
        browseMode,
        tierFilter,
        selectedChampions,
        selectedItems,
        finderQuery,
        envCatalogItems
    ) {
        if (isDefaultAllCompsView) {
            envAllComps
        } else {
            val source = if (browseMode == OverlayBrowseMode.FAVORITES) envFavoriteComps else envAllComps
            val tierFiltered = if (tierFilter == null) source else source.filter { it.tier == tierFilter }
            OverlayArmySearchEngine.filterArmies(
                comps = tierFiltered,
                selectedChampions = selectedChampions,
                selectedItems = selectedItems,
                query = finderQuery,
                catalogItems = envCatalogItems
            )
        }
    }
    val displayedByTier = remember(isDefaultAllCompsView, envFilter, liveAllCompsByTier, pbeAllCompsByTier, displayed) {
        if (isDefaultAllCompsView) {
            if (envFilter == TftEnvironment.LIVE) liveAllCompsByTier else pbeAllCompsByTier
        } else {
            groupCompsByTier(displayed)
        }
    }
    val selectedComp = remember(displayed, mergedAllComps, selectedCompId) {
        if (selectedCompId == null) null
        else displayed.firstOrNull { it.id == selectedCompId }
            ?: mergedAllComps.firstOrNull { it.id == selectedCompId }
    }

    var moveStartOrigin by remember { mutableStateOf(0 to 0) }
    var moveStartRaw by remember { mutableStateOf(0f to 0f) }
    var resizeStartSize by remember { mutableStateOf(drawerDims.widthDp.value to drawerDims.maxHeightDp.value) }
    var resizeStartRaw by remember { mutableStateOf(0f to 0f) }

    val surfaceSizeModifier = if (effectiveFixedHeightDp != null) {
        Modifier
            .width(activeWidthDp)
            .height(effectiveFixedHeightDp)
    } else {
        Modifier
            .width(activeWidthDp)
            .heightIn(max = drawerDims.maxHeightDp)
    }

    val clampedOverlayOpacity = clampOverlayOpacity(overlayOpacity)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = ObsidianVoid.copy(alpha = 0.96f),
        border = BorderStroke(
            width = 1.6.dp,
            brush = Brush.linearGradient(listOf(BrightGold, ArcaneCyan))
        ),
        shadowElevation = 16.dp,
        modifier = surfaceSizeModifier
            .graphicsLayer {
                alpha = clampedOverlayOpacity
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .testTag("overlay_expanded_drawer")
    ) {
        Box(
            modifier = if (effectiveFixedHeightDp != null) {
                Modifier.fillMaxWidth().fillMaxHeight()
            } else {
                Modifier.fillMaxWidth()
            }
        ) {
            Column(
                modifier = (if (effectiveFixedHeightDp != null) {
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
                                    sessionState.selectBrowseMode(OverlayBrowseMode.ALL_COMPS)
                                }
                            )
                            OverlayEnvChip(
                                label = "Favs ($envScopedFavoritesCount)",
                                selected = browseMode == OverlayBrowseMode.FAVORITES,
                                icon = Icons.Filled.Star,
                                onClick = {
                                    sessionState.selectBrowseMode(OverlayBrowseMode.FAVORITES)
                                }
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val isAtFullWidth = activeWidthDp.value >= sizeBounds.maxWidthDp - 6f
                        IconButton(
                            onClick = {
                                if (isAtFullWidth) {
                                    val compactW = if (defaultDrawerDims.isLandscape) {
                                        defaultDrawerDims.widthDp.value
                                    } else {
                                        (sizeBounds.maxWidthDp * 0.62f)
                                            .coerceAtLeast(sizeBounds.minWidthDp)
                                    }
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
                        val isOpacityCustom = abs(clampedOverlayOpacity - DEFAULT_OVERLAY_OPACITY) > 0.02f
                        IconButton(
                            onClick = { sessionState.toggleOpacityControl() },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("overlay_opacity_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Opacity,
                                contentDescription = stringResource(R.string.overlay_opacity_toggle_desc),
                                tint = when {
                                    sessionState.isOpacityControlOpen -> BrightGold
                                    isOpacityCustom -> ArcaneCyan
                                    else -> Color(0xFFB7C4D8)
                                },
                                modifier = Modifier.size(16.dp)
                            )
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
                            onClick = onCloseDrawer,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("overlay_minimize_bubble_button")
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close overlay drawer (keep bauble visible)", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = onStopOverlay,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("overlay_close_button")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Stop overlay", tint = Color(0xFFE74C3C), modifier = Modifier.size(16.dp))
                        }
                    }
                }

                AnimatedVisibility(visible = sessionState.isOpacityControlOpen) {
                    OverlayOpacityControlBar(
                        opacity = clampedOverlayOpacity,
                        showPresets = drawerDims.widthDp >= 430.dp,
                        onOpacityChanged = onOpacityChanged,
                        onResetOpacity = { onOpacityChanged(DEFAULT_OVERLAY_OPACITY) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 4.dp)
                    )
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
                                        label = "Comps ($envScopedCompsCount)",
                                        selected = browseMode == OverlayBrowseMode.ALL_COMPS,
                                        icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                                        onClick = {
                                            sessionState.selectBrowseMode(OverlayBrowseMode.ALL_COMPS)
                                        }
                                    )
                                    OverlayEnvChip(
                                        label = "Favs ($envScopedFavoritesCount)",
                                        selected = browseMode == OverlayBrowseMode.FAVORITES,
                                        icon = Icons.Filled.Star,
                                        onClick = {
                                            sessionState.selectBrowseMode(OverlayBrowseMode.FAVORITES)
                                        }
                                    )
                                }
                                OverlayEnvChip("LIVE", envFilter == TftEnvironment.LIVE, Icons.Default.Bolt) {
                                    sessionState.selectEnvironment(TftEnvironment.LIVE)
                                }
                                OverlayEnvChip("PBE", envFilter == TftEnvironment.PBE, Icons.Default.Science) {
                                    sessionState.selectEnvironment(TftEnvironment.PBE)
                                }
                                val activeFinderCount = sessionState.activeArmyFilterCount
                                OverlayEnvChip(
                                    label = if (activeFinderCount > 0) "Find ($activeFinderCount)" else "Find",
                                    selected = sessionState.isArmyFinderOpen || activeFinderCount > 0,
                                    icon = Icons.Default.Search,
                                    accentColor = if (activeFinderCount > 0) BrightGold else ArcaneCyan
                                ) {
                                    if (sessionState.isArmyFinderOpen) {
                                        onSearchFocusChanged(false)
                                    }
                                    sessionState.toggleArmyFinder()
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
                                        sessionState.toggleTierFilter(t)
                                    }
                                }
                                if (displayedByTier.isNotEmpty() && !sessionState.hasActiveArmyFilters) {
                                    val anyOpen = displayedByTier.any { expandedOverlayTiers.contains(it.first) }
                                    OverlayEnvChip(
                                        label = if (anyOpen) "Close All" else "Open All",
                                        selected = anyOpen,
                                        accentColor = ArcaneCyan
                                    ) {
                                        sessionState.setExpandedTiers(
                                            if (anyOpen) emptySet()
                                            else displayedByTier.map { it.first }.toSet()
                                        )
                                    }
                                }
                            }
                            if (sessionState.hasActiveArmyFilters) {
                                OverlayActiveArmyFiltersBar(
                                    selectedChampions = selectedChampions,
                                    selectedItems = selectedItems,
                                    finderQuery = finderQuery,
                                    matchedCount = displayed.size,
                                    environment = envFilter,
                                    onRemoveChampion = { sessionState.toggleSelectedChampion(it) },
                                    onRemoveItem = { sessionState.toggleSelectedItem(it) },
                                    onClearQuery = { sessionState.updateFinderQuery("") },
                                    onClearAll = {
                                        onSearchFocusChanged(false)
                                        sessionState.clearArmyFilters()
                                    },
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                            AnimatedVisibility(visible = sessionState.isArmyFinderOpen) {
                                OverlayArmyFinderPanel(
                                    environment = envFilter,
                                    sessionState = sessionState,
                                    championChoices = championChoices,
                                    itemChoices = itemChoices,
                                    matchedArmiesCount = displayed.size,
                                    onSearchFocusChanged = onSearchFocusChanged,
                                    onClosePanel = {
                                        onSearchFocusChanged(false)
                                        sessionState.updateArmyFinderOpen(false)
                                    },
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                        } else {
                            Spacer(Modifier.height(6.dp))
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = effectiveFixedHeightDp != null)
                        ) {
                            val activeMode = browseMode
                            val activeEnv = envFilter
                            val activeOverlayChamp = inspectedOverlayChampion
                            if (activeOverlayChamp != null) {
                                val profile = remember(activeOverlayChamp, envFilter, allChampions, envAllComps, allItems) {
                                    resolveChampionDetailProfile(
                                        championName = activeOverlayChamp.first,
                                        environment = envFilter,
                                        champions = allChampions,
                                        comps = envAllComps,
                                        items = allItems,
                                        fallbackCost = activeOverlayChamp.second
                                    )
                                }
                                    val paneHeightModifier = if (effectiveFixedHeightDp != null) {
                                        Modifier.fillMaxHeight()
                                    } else {
                                        Modifier.heightIn(max = (drawerDims.maxHeightDp - 84.dp).coerceAtLeast(180.dp))
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = DeepSlate,
                                        border = BorderStroke(
                                            width = 1.4.dp,
                                            brush = Brush.linearGradient(listOf(BrightGold, ArcaneCyan))
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(paneHeightModifier)
                                            .testTag("overlay_champion_detail_pane")
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Surface(
                                                    onClick = { sessionState.closeChampionInspector() },
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = SlateElevated,
                                                    border = BorderStroke(1.dp, ArcaneCyan.copy(alpha = 0.65f)),
                                                    modifier = Modifier.testTag("overlay_champion_back_button")
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                            contentDescription = "Back",
                                                            tint = ArcaneCyan,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Text(
                                                            text = "Back",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = ArcaneCyan
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = "${profile.name} Inspector",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = BrightGold,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                IconButton(
                                                    onClick = { sessionState.closeChampionInspector() },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Close champion inspector",
                                                        tint = Color(0xFFB7C4D8),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                            ChampionDetailSheetContent(
                                                profile = profile,
                                                onClose = null,
                                                onSelectChampion = { nextName, nextCost ->
                                                    sessionState.inspectChampion(nextName, nextCost)
                                                },
                                                onOpenComp = { targetComp ->
                                                    sessionState.openCompInEnvironment(targetComp.id, targetComp.environment)
                                                }
                                            )
                                        }
                                    }
                                } else if (selectedComp != null) {
                                    val leftWeight = if (drawerDims.widthDp >= 600.dp) 0.49f else 0.46f
                                    val rightWeight = 1f - leftWeight
                                    val paneHeightModifier = if (effectiveFixedHeightDp != null) {
                                        Modifier.fillMaxHeight()
                                    } else {
                                        Modifier.heightIn(max = (drawerDims.maxHeightDp - 84.dp).coerceAtLeast(180.dp))
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(if (effectiveFixedHeightDp != null) Modifier.fillMaxHeight() else Modifier),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        OverlayCompLeftDetailPane(
                                            comp = selectedComp,
                                            isFavorite = favoriteIds.contains(selectedComp.id),
                                            isCodeCopied = copiedCompId == selectedComp.id,
                                            champions = allChampions,
                                            allComps = envAllComps,
                                            initialScrollOffset = sessionState.detailScrollOffset,
                                            onScrollChanged = { sessionState.updateDetailScroll(it) },
                                            onCopyCode = { onCopyCompCode(selectedComp) },
                                            onToggleFavorite = { onToggleFavorite(selectedComp) },
                                            onBackToComps = { sessionState.selectComp(null) },
                                            onChampionClick = { unitName, cost ->
                                                sessionState.inspectChampion(unitName, cost)
                                            },
                                            modifier = Modifier
                                                .weight(leftWeight)
                                                .then(paneHeightModifier)
                                        )
                                        OverlayLevelGuideRightPane(
                                            comp = selectedComp,
                                            fillAvailableHeight = effectiveFixedHeightDp != null,
                                            onChampionClick = { unitName, cost ->
                                                sessionState.inspectChampion(unitName, cost)
                                            },
                                            modifier = Modifier
                                                .weight(rightWeight)
                                                .then(paneHeightModifier)
                                        )
                                    }
                                } else if (displayed.isEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(DeepSlate, RoundedCornerShape(12.dp))
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = when {
                                                sessionState.hasActiveArmyFilters ->
                                                    "No ${activeEnv.label} armies include all selected champions/items."
                                                activeMode == OverlayBrowseMode.FAVORITES ->
                                                    "No ${activeEnv.label} favorites yet. Tap the Star icon on any composition to add it."
                                                else ->
                                                    "No compositions match the active ${activeEnv.label} or tier filter."
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF93A1B8)
                                        )
                                        if (sessionState.hasActiveArmyFilters) {
                                            Surface(
                                                onClick = {
                                                    onSearchFocusChanged(false)
                                                    sessionState.clearArmyFilters()
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                color = ArcaneCyan.copy(alpha = 0.16f),
                                                border = BorderStroke(1.dp, ArcaneCyan.copy(alpha = 0.6f))
                                            ) {
                                                Text(
                                                    text = "Clear Champion & Item Filters",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = ArcaneCyan,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    val listState = rememberLazyListState(
                                        initialFirstVisibleItemIndex = sessionState.listScrollIndex,
                                        initialFirstVisibleItemScrollOffset = sessionState.listScrollOffset
                                    )
                                    LaunchedEffect(listState) {
                                        snapshotFlow {
                                            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                                        }.collect { (idx, off) ->
                                            sessionState.updateListScroll(idx, off)
                                        }
                                    }
                                    val useFullWidthTwoColGrid = drawerDims.widthDp >= 640.dp

                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        displayedByTier.forEach { (tier, tierComps) ->
                                            val isTierOpen = sessionState.hasActiveArmyFilters || expandedOverlayTiers.contains(tier)
                                            item(
                                                key = "overlay_tier_header_${tier.name}",
                                                contentType = "overlay_tier_header"
                                            ) {
                                                OverlayTierSubHeader(
                                                    tier = tier,
                                                    comps = tierComps,
                                                    expanded = isTierOpen,
                                                    onClick = {
                                                        sessionState.toggleTierExpanded(tier)
                                                    }
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
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            rowComps.forEach { comp ->
                                                                val isFav = favoriteIds.contains(comp.id)
                                                                OverlayCompCard(
                                                                    comp = comp,
                                                                    isFavorite = isFav,
                                                                    isCodeCopied = copiedCompId == comp.id,
                                                                    selected = false,
                                                                    expanded = false,
                                                                    opensInSidePane = true,
                                                                    useTwoColumnLayout = false,
                                                                    champions = allChampions,
                                                                    allComps = envAllComps,
                                                                    onCardClick = { sessionState.selectComp(comp.id) },
                                                                    onChampionClick = { unitName, cost ->
                                                                        sessionState.inspectChampion(unitName, cost)
                                                                    },
                                                                    onCopyCode = { onCopyCompCode(comp) },
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
                                                            isCodeCopied = copiedCompId == comp.id,
                                                            selected = false,
                                                            expanded = false,
                                                            opensInSidePane = true,
                                                            useTwoColumnLayout = false,
                                                            champions = allChampions,
                                                            allComps = envAllComps,
                                                            onCardClick = { sessionState.selectComp(comp.id) },
                                                            onChampionClick = { unitName, cost ->
                                                                sessionState.inspectChampion(unitName, cost)
                                                            },
                                                            onCopyCode = { onCopyCompCode(comp) },
                                                            onToggleFavorite = { onToggleFavorite(comp) }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                        }

                        AnimatedVisibility(visible = !copyBannerMessage.isNullOrBlank()) {
                            val bannerBg = if (copyBannerIsError) Color(0xFF3A2312) else Color(0xFF103128)
                            val bannerBorder = if (copyBannerIsError) Color(0xFFF39C12) else Color(0xFF2ECC71)
                            val bannerTextColor = if (copyBannerIsError) Color(0xFFFFE5B8) else Color(0xFFD6F9E5)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = bannerBg,
                                border = BorderStroke(1.dp, bannerBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp)
                                    .testTag("overlay_copy_feedback_banner")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (copyBannerIsError) Icons.Default.Warning else Icons.Default.Check,
                                        contentDescription = null,
                                        tint = bannerBorder,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = copyBannerMessage.orEmpty(),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = bannerTextColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
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
    isCodeCopied: Boolean = false,
    champions: List<ChampionTierEntry> = emptyList(),
    allComps: List<MetaComp> = emptyList(),
    initialScrollOffset: Int = 0,
    onScrollChanged: (Int) -> Unit = {},
    onCopyCode: () -> Unit = {},
    onToggleFavorite: () -> Unit,
    onBackToComps: () -> Unit,
    onChampionClick: ((String, Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState(initial = initialScrollOffset)
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }.collect { offset ->
            onScrollChanged(offset)
        }
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
                .verticalScroll(scrollState),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OverlayCopyCodeButton(
                        compTitle = comp.title,
                        copied = isCodeCopied,
                        onClick = onCopyCode,
                        testTag = "overlay_detail_copy_code_${comp.id}"
                    )
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

            CompFullBoardRosterSection(
                comp = comp,
                champions = champions,
                allComps = allComps,
                onChampionClick = onChampionClick,
                compact = true
            )

            if (comp.augmentPriorities.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Recommended Augments",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrightGold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        comp.augmentPriorities.forEach { aug ->
                            TftAugmentChip(augmentName = aug, compact = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayLevelGuideRightPane(
    comp: MetaComp,
    fillAvailableHeight: Boolean,
    onChampionClick: ((String, Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = DeepSlate,
        border = BorderStroke(
            width = 1.4.dp,
            brush = Brush.linearGradient(listOf(ArcaneCyan, BrightGold))
        ),
        modifier = modifier.testTag("overlay_right_level_guide_pane")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (fillAvailableHeight) Modifier.fillMaxHeight() else Modifier)
                .verticalScroll(scrollState)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CompLevelTransitionGuideSection(
                comp = comp,
                onChampionClick = onChampionClick,
                compact = true
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverlayCompCard(
    comp: MetaComp,
    isFavorite: Boolean,
    isCodeCopied: Boolean = false,
    selected: Boolean = false,
    expanded: Boolean,
    opensInSidePane: Boolean = false,
    useTwoColumnLayout: Boolean = false,
    champions: List<ChampionTierEntry> = emptyList(),
    allComps: List<MetaComp> = emptyList(),
    onCardClick: () -> Unit,
    onChampionClick: ((String, Int) -> Unit)? = null,
    onCopyCode: () -> Unit = {},
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    OverlayCopyCodeButton(
                        compTitle = comp.title,
                        copied = isCodeCopied,
                        onClick = onCopyCode,
                        testTag = "overlay_copy_code_${comp.id}"
                    )
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
            }

            Text(
                text = "Carry: ${comp.carryName} • ${comp.playstyle} • Avg ${String.format(Locale.US, "%.2f", comp.avgPlacement)}",
                style = MaterialTheme.typography.bodySmall,
                color = BrightGold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

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

            if (!expanded) {
                Spacer(Modifier.height(5.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val sortedUnits = remember(comp.coreUnits, comp.hexPlacements, comp.environment) {
                        sortCompUnitsByCostAscending(comp)
                    }
                    sortedUnits.forEach { unit ->
                        val isCarryUnit = unit.equals(comp.carryName, ignoreCase = true)
                        val unitCost = resolveCompUnitCost(unit, comp)
                        TftChampionTile(
                            name = unit,
                            cost = unitCost,
                            isCarry = isCarryUnit,
                            size = 34.dp,
                            labelFontSize = 10.sp,
                            environment = comp.environment,
                            onClick = onChampionClick?.let { cb -> { cb(unit, unitCost) } }
                        )
                    }
                }
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                if (useTwoColumnLayout) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier.weight(1.05f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CompFullBoardRosterSection(
                                comp = comp,
                                champions = champions,
                                allComps = allComps,
                                onChampionClick = onChampionClick,
                                compact = true
                            )
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
                        }
                        CompLevelTransitionGuideSection(
                            comp = comp,
                            onChampionClick = onChampionClick,
                            compact = true,
                            modifier = Modifier.weight(0.95f)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CompFullBoardRosterSection(
                            comp = comp,
                            champions = champions,
                            allComps = allComps,
                            onChampionClick = onChampionClick,
                            compact = true
                        )
                        CompLevelTransitionGuideSection(
                            comp = comp,
                            onChampionClick = onChampionClick,
                            compact = true
                        )
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
                    }
                }
            } else {
                Text(
                    text = if (opensInSidePane) {
                        "Tap card for Unit Items (Left) & Level Guide (Right) →"
                    } else {
                        "Tap card for Full Board Roster, Unit Items & Level Guide"
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
private fun OverlayCopyCodeButton(
    compTitle: String,
    copied: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val accent = if (copied) Color(0xFF2ECC71) else ArcaneCyan
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = accent.copy(alpha = if (copied) 0.24f else 0.14f),
        border = BorderStroke(1.dp, accent.copy(alpha = if (copied) 0.95f else 0.65f)),
        modifier = Modifier.testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                contentDescription = if (copied) {
                    "Copied TFT Team Planner code for $compTitle"
                } else {
                    "Copy TFT Team Planner code for $compTitle"
                },
                tint = accent,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = if (copied) "Copied" else "Code",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = accent,
                maxLines = 1
            )
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
private fun OverlayOpacityControlBar(
    opacity: Float,
    showPresets: Boolean,
    onOpacityChanged: (Float) -> Unit,
    onResetOpacity: () -> Unit,
    modifier: Modifier = Modifier
) {
    val percent = (clampOverlayOpacity(opacity) * 100f).roundToInt()
    val sliderDesc = stringResource(R.string.overlay_opacity_slider_desc)

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = DeepSlate.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, ArcaneCyan.copy(alpha = 0.45f)),
        modifier = modifier.testTag("overlay_opacity_panel")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Opacity,
                contentDescription = null,
                tint = BrightGold,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = stringResource(R.string.overlay_opacity_label),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFD5E1F2),
                maxLines = 1
            )
            Text(
                text = "$percent%",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = ArcaneCyan,
                maxLines = 1,
                modifier = Modifier
                    .width(34.dp)
                    .testTag("overlay_opacity_value")
            )
            Slider(
                value = clampOverlayOpacity(opacity),
                onValueChange = { onOpacityChanged(it) },
                valueRange = MIN_OVERLAY_OPACITY..MAX_OVERLAY_OPACITY,
                colors = SliderDefaults.colors(
                    thumbColor = BrightGold,
                    activeTrackColor = ArcaneCyan,
                    inactiveTrackColor = SlateOutline
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .semantics { contentDescription = "$sliderDesc: $percent%" }
                    .testTag("overlay_opacity_slider")
            )
            if (showPresets) {
                OverlayOpacityPresetChip(
                    label = "50%",
                    selected = abs(opacity - 0.50f) < 0.03f,
                    onClick = { onOpacityChanged(0.50f) },
                    testTag = "overlay_opacity_preset_50"
                )
                OverlayOpacityPresetChip(
                    label = "100%",
                    selected = abs(opacity - 1.0f) < 0.03f,
                    onClick = { onOpacityChanged(1.0f) },
                    testTag = "overlay_opacity_preset_100"
                )
            }
            if (abs(opacity - DEFAULT_OVERLAY_OPACITY) > 0.02f) {
                IconButton(
                    onClick = onResetOpacity,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("overlay_opacity_reset_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = stringResource(R.string.overlay_opacity_reset_desc),
                        tint = BrightGold,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun OverlayOpacityPresetChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(7.dp),
        color = if (selected) ArcaneCyan.copy(alpha = 0.22f) else SlateElevated,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) ArcaneCyan else SlateOutline.copy(alpha = 0.7f)
        ),
        modifier = Modifier.testTag(testTag)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) ArcaneCyan else Color(0xFFB7C4D8),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}

