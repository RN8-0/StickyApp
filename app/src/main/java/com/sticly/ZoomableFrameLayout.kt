package com.sticly

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.FrameLayout

/**
 * FrameLayout that intercepts 2-finger pinch/pan for canvas zoom ONLY
 * when no child (sticker/text/emoji) is actively handling the touch.
 *
 * Logic:
 *  - ACTION_DOWN is never intercepted → children get first shot
 *  - If a child handles ACTION_DOWN → selfHandledDown = false
 *    → ACTION_POINTER_DOWN is NOT intercepted → child handles 2-finger resize
 *  - If no child handles ACTION_DOWN → we get onTouchEvent(ACTION_DOWN), selfHandledDown = true
 *    → subsequent events (including ACTION_POINTER_DOWN) come to our onTouchEvent
 *    → ScaleGestureDetector handles canvas zoom
 *  - Double-tap on empty area → reset zoom
 */
class ZoomableFrameLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle) {

    var currentScale = 1f
        private set
    var currentTransX = 0f
        private set
    var currentTransY = 0f
        private set

    private var lastMidX = 0f
    private var lastMidY = 0f

    // True only if WE handled ACTION_DOWN (no child consumed it → empty area touch)
    private var selfHandledDown = false

    var targetView: android.view.View? = null
    var zoomEnabled = true

    /**
     * Invoked on a double-tap. The activity returns true if the tap landed on an editable overlay
     * (e.g. a text sticker) and it handled it (opened the editor); false means "empty area" and we
     * reset the zoom instead.
     */
    var onOverlayDoubleTap: ((MotionEvent) -> Boolean)? = null

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val target = targetView ?: return false
            val oldScale = currentScale
            currentScale = (currentScale * detector.scaleFactor).coerceIn(0.3f, 6f)
            val factor = currentScale / oldScale
            val fx = detector.focusX
            val fy = detector.focusY
            currentTransX = fx - factor * (fx - currentTransX)
            currentTransY = fy - factor * (fy - currentTransY)
            applyTransform(target)
            return true
        }
    })

    private val doubleTapDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            // Give the activity first chance (double-tap on a text sticker → edit). If it didn't
            // handle it, the double-tap was on empty canvas → reset zoom.
            if (onOverlayDoubleTap?.invoke(e) == true) return true
            resetZoom()
            return true
        }
    })

    private fun applyTransform(target: android.view.View) {
        target.pivotX = 0f
        target.pivotY = 0f
        target.scaleX = currentScale
        target.scaleY = currentScale
        target.translationX = currentTransX
        target.translationY = currentTransY
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // Observe EVERY touch for double-tap detection — even when a child (sticker/text) consumes
        // it — without intercepting, so double-tapping a text sticker is detected. Returning false
        // below keeps the library's drag/scale/select untouched.
        doubleTapDetector.onTouchEvent(ev)
        if (!zoomEnabled) return false
        return when (ev.actionMasked) {
            // Never intercept first touch — let children (stickers/text) handle it first
            MotionEvent.ACTION_DOWN -> false
            // Intercept second finger ONLY if we own the touch sequence (empty area)
            MotionEvent.ACTION_POINTER_DOWN -> selfHandledDown
            else -> false
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!zoomEnabled) return false

        scaleDetector.onTouchEvent(event)
        // (double-tap is fed from onInterceptTouchEvent so it also sees taps on child overlays)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // We only get here if no child handled ACTION_DOWN → empty area
                selfHandledDown = true
                lastMidX = event.x
                lastMidY = event.y
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    lastMidX = (event.getX(0) + event.getX(1)) / 2f
                    lastMidY = (event.getY(0) + event.getY(1)) / 2f
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val target = targetView ?: return true
                if (event.pointerCount >= 2 && !scaleDetector.isInProgress) {
                    val midX = (event.getX(0) + event.getX(1)) / 2f
                    val midY = (event.getY(0) + event.getY(1)) / 2f
                    currentTransX += midX - lastMidX
                    currentTransY += midY - lastMidY
                    applyTransform(target)
                    lastMidX = midX
                    lastMidY = midY
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                selfHandledDown = false
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount == 2) {
                    val remainIdx = if (event.actionIndex == 0) 1 else 0
                    lastMidX = event.getX(remainIdx)
                    lastMidY = event.getY(remainIdx)
                }
                return true
            }
        }
        return false
    }

    fun screenToLocal(x: Float, y: Float): FloatArray {
        val lx = (x - currentTransX) / currentScale
        val ly = (y - currentTransY) / currentScale
        return floatArrayOf(lx, ly)
    }

    fun resetZoom() {
        currentScale = 1f
        currentTransX = 0f
        currentTransY = 0f
        val target = targetView ?: return
        target.animate()
            .scaleX(1f).scaleY(1f)
            .translationX(0f).translationY(0f)
            .setDuration(200).start()
        target.pivotX = 0f
        target.pivotY = 0f
    }

    fun isZoomed(): Boolean = currentScale > 1.05f
}
