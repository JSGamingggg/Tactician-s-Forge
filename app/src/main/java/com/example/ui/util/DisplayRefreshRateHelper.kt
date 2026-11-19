package com.example.ui.util

import android.app.Activity
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.WindowManager

data class DisplayModeSpec(
    val modeId: Int,
    val physicalWidth: Int,
    val physicalHeight: Int,
    val refreshRateHz: Float
)

/**
 * Configures both Activity windows and floating overlay windows to run at the device's
 * native/peak display refresh rate (e.g., 90Hz, 120Hz, 144Hz, 165Hz) while preserving
 * the active display's native physical resolution.
 */
object DisplayRefreshRateHelper {

    fun selectOptimalDisplayMode(
        currentMode: DisplayModeSpec,
        supportedModes: List<DisplayModeSpec>
    ): DisplayModeSpec {
        val sameResolutionModes = supportedModes.filter {
            it.physicalWidth == currentMode.physicalWidth &&
                it.physicalHeight == currentMode.physicalHeight &&
                it.refreshRateHz > 0f
        }
        val candidates = if (sameResolutionModes.isNotEmpty()) sameResolutionModes else listOf(currentMode)
        return candidates.maxWithOrNull(
            compareBy<DisplayModeSpec> { it.refreshRateHz }
                .thenBy { if (it.modeId == currentMode.modeId) 1 else 0 }
        ) ?: currentMode
    }

    fun resolveOptimalDisplayMode(display: Display?): DisplayModeSpec? {
        if (display == null) return null
        return runCatching {
            val current = display.mode ?: return null
            val currentSpec = DisplayModeSpec(
                modeId = current.modeId,
                physicalWidth = current.physicalWidth,
                physicalHeight = current.physicalHeight,
                refreshRateHz = current.refreshRate
            )
            val supportedSpecs = display.supportedModes.orEmpty().map { mode ->
                DisplayModeSpec(
                    modeId = mode.modeId,
                    physicalWidth = mode.physicalWidth,
                    physicalHeight = mode.physicalHeight,
                    refreshRateHz = mode.refreshRate
                )
            }
            selectOptimalDisplayMode(currentSpec, supportedSpecs)
        }.getOrNull()
    }

    fun resolveActiveDisplay(context: Context): Display? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { context.display }.getOrNull()?.let { return it }
        }
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        displayManager?.getDisplay(Display.DEFAULT_DISPLAY)?.let { return it }
        @Suppress("DEPRECATION")
        return (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay
    }

    fun applyToLayoutParams(
        context: Context,
        params: WindowManager.LayoutParams,
        displayOverride: Display? = null
    ): Boolean {
        val display = displayOverride ?: resolveActiveDisplay(context) ?: return false
        val bestMode = resolveOptimalDisplayMode(display) ?: return false
        var changed = false
        if (params.preferredDisplayModeId != bestMode.modeId) {
            params.preferredDisplayModeId = bestMode.modeId
            changed = true
        }
        if (params.preferredRefreshRate != bestMode.refreshRateHz) {
            params.preferredRefreshRate = bestMode.refreshRateHz
            changed = true
        }
        return changed
    }

    fun applyToActivity(activity: Activity) {
        val window = activity.window ?: return
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { activity.display }.getOrNull() ?: resolveActiveDisplay(activity)
        } else {
            resolveActiveDisplay(activity)
        }
        val attrs = window.attributes ?: return
        val changed = applyToLayoutParams(activity, attrs, displayOverride = display)
        if (changed) {
            runCatching { window.attributes = attrs }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            runCatching { window.frameRateBoostOnTouchEnabled = true }
        }
    }
}
