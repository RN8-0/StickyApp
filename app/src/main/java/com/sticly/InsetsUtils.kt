package com.sticly

import android.os.Build
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Window-inset handling for views that are NOT covered by a layout's `fitsSystemWindows`.
 *
 * Since targetSdk 35 the window is always edge-to-edge on API 35+ devices and
 * `android:windowOptOutEdgeToEdgeEnforcement` is ignored from API 36 on, so two things changed:
 *
 *  - a view added straight into `android.R.id.content` starts at y=0, i.e. underneath the status
 *    bar (the tap target ends up in the notification-shade pull-down zone), and
 *  - `SOFT_INPUT_ADJUST_RESIZE` no longer resizes the window, so the keyboard covers whatever sits
 *    at the bottom instead of pushing it up.
 *
 * Everything here is a no-op below API 35, where the decor still fits system windows and still
 * resizes for the keyboard — padding there would inset twice.
 */
object InsetsUtils {

    private val needsManualInsets: Boolean
        get() = Build.VERSION.SDK_INT >= 35

    /**
     * Keeps [overlay] clear of the status bar, the navigation bar and the keyboard.
     *
     * The listener is installed on the overlay's PARENT (the content root) because that view is
     * guaranteed to be reached by the inset dispatch; a listener on the overlay itself can be
     * skipped once a sibling with `fitsSystemWindows` consumes the insets. The default dispatch is
     * preserved, so the activity's own layout keeps getting its insets as before.
     *
     * The listener is removed automatically when the overlay is detached.
     */
    fun padForSystemBarsAndIme(overlay: View) {
        if (!needsManualInsets) return
        val parent = overlay.parent as? View ?: return

        ViewCompat.setOnApplyWindowInsetsListener(parent) { v, insets ->
            apply(overlay, insets)
            ViewCompat.onApplyWindowInsets(v, insets)
        }

        // The listener only fires on the next inset pass; seed the padding now so the first frame
        // is already correct.
        ViewCompat.getRootWindowInsets(parent)?.let { apply(overlay, it) }
        ViewCompat.requestApplyInsets(parent)

        overlay.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) {
                ViewCompat.setOnApplyWindowInsetsListener(parent, null)
                v.removeOnAttachStateChangeListener(this)
            }
        })
    }

    private fun apply(overlay: View, insets: WindowInsetsCompat) {
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
        overlay.setPadding(bars.left, bars.top, bars.right, maxOf(ime.bottom, bars.bottom))
    }

    /**
     * Adds the navigation-bar inset as bottom padding on a scrolling view.
     *
     * CoordinatorLayout roots hand their top inset to AppBarLayout but nothing consumes the bottom
     * one, so the last row of a list ends up under the gesture bar once the window is edge-to-edge.
     * [clipToPadding] is turned off so content still scrolls through the padded strip.
     */
    fun padBottomForNavBar(view: View?) {
        if (!needsManualInsets || view == null) return
        (view as? android.view.ViewGroup)?.clipToPadding = false
        val basePadding = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, basePadding + bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    /**
     * Pads a whole activity root that has no `fitsSystemWindows` of its own (e.g. a root built in
     * code). Safe to call once in onCreate.
     */
    fun padRootForSystemBars(root: View) {
        if (!needsManualInsets) return
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }
}
