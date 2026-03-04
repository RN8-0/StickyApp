package com.sticly

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.FrameLayout

/**
 * FrameLayout that intercepts touch gestures for zoom/pan.
 * Uses pivot at (0,0) and translation to avoid pivot-shift issues.
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

    var targetView: android.view.View? = null
    var zoomEnabled = true
    var interceptAll = false

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val target = targetView ?: return false
            val oldScale = currentScale
            currentScale = (currentScale * detector.scaleFactor).coerceIn(1f, 5f)
            val factor = currentScale / oldScale
            // Zoom around the focus point
            val fx = detector.focusX
            val fy = detector.focusY
            currentTransX = fx - factor * (fx - currentTransX)
            currentTransY = fy - factor * (fy - currentTransY)
            applyTransform(target)
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            if (currentScale < 1.05f) resetZoom()
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
        if (!zoomEnabled) return false
        if (interceptAll) return true
        if (ev.pointerCount >= 2) {
            parent?.requestDisallowInterceptTouchEvent(true)
            return true
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!zoomEnabled) return false
        scaleDetector.onTouchEvent(event)
        val target = targetView ?: return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastMidX = event.x
                lastMidY = event.y
                parent?.requestDisallowInterceptTouchEvent(true)
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
                if (event.pointerCount >= 2) {
                    val midX = (event.getX(0) + event.getX(1)) / 2f
                    val midY = (event.getY(0) + event.getY(1)) / 2f
                    currentTransX += midX - lastMidX
                    currentTransY += midY - lastMidY
                    applyTransform(target)
                    lastMidX = midX
                    lastMidY = midY
                } else if (interceptAll) {
                    currentTransX += event.x - lastMidX
                    currentTransY += event.y - lastMidY
                    applyTransform(target)
                    lastMidX = event.x
                    lastMidY = event.y
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
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

    /** Map a point from screen/overlay space to the target view's local (unzoomed) space */
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
