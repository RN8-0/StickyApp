package com.sticly

import android.app.Activity
import android.os.Build

/**
 * Drives the app's window at the display's maximum refresh rate.
 *
 * Android only guarantees the *default* refresh rate unless an app explicitly opts in, so on
 * 90/120Hz phones the UI could run at 60Hz and feel less smooth. [applyMaxRefreshRate] picks the
 * highest-Hz display mode at the current resolution and requests it for the window. It is a safe
 * no-op on single-mode displays and older APIs.
 */
object DisplayUtils {

    fun applyMaxRefreshRate(activity: Activity) {
        try {
            // Display.Mode (supportedModes / modeId) is API 23+; minSdk is 24 so always available,
            // but keep the guard defensive.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.display
            } else {
                @Suppress("DEPRECATION")
                activity.windowManager.defaultDisplay
            } ?: return

            val current = display.mode ?: return
            val modes = display.supportedModes ?: return
            if (modes.size <= 1) return

            // Highest refresh rate at the native resolution (avoid switching resolution).
            val best = modes
                .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
                .maxByOrNull { it.refreshRate }
                ?: modes.maxByOrNull { it.refreshRate }
                ?: return

            if (best.modeId == current.modeId || best.refreshRate <= current.refreshRate) return

            val params = activity.window.attributes
            params.preferredDisplayModeId = best.modeId
            activity.window.attributes = params
        } catch (_: Exception) {
            // Refresh-rate tuning is best-effort; never let it crash an activity.
        }
    }
}
