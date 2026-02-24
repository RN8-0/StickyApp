package com.sticly

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat

class RangeSelectionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val HANDLE_WIDTH_DP = 14f
        private const val BORDER_HEIGHT_DP = 3f
        private const val TOUCH_SLOP_DP = 24f
    }

    private val density = resources.displayMetrics.density
    private val handleWidthPx = HANDLE_WIDTH_DP * density
    private val borderHeightPx = BORDER_HEIGHT_DP * density
    private val touchSlopPx = TOUCH_SLOP_DP * density

    private val primaryColor = ContextCompat.getColor(context, R.color.primary)
    private val overlayColor = 0xCC000000.toInt()
    private val handleGripColor = 0xFFFFFFFF.toInt()

    private val paintFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val paintGrip = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = handleGripColor
        strokeWidth = 2f * density
        strokeCap = Paint.Cap.ROUND
    }

    // Normalized positions 0..1
    private var leftPos = 0f
    private var rightPos = 1f

    private var dragging = DragTarget.NONE
    private var lastTouchX = 0f

    var onRangeChanged: ((leftNorm: Float, rightNorm: Float) -> Unit)? = null

    private enum class DragTarget { NONE, LEFT, RIGHT, CENTER }

    // The usable width between the two handle gutters
    private fun usableWidth(): Float = width.toFloat() - 2 * handleWidthPx

    // Convert normalized 0..1 to pixel X (left edge of content area)
    private fun normToPixel(norm: Float): Float = handleWidthPx + norm * usableWidth()

    // Convert pixel X to normalized 0..1
    private fun pixelToNorm(px: Float): Float = ((px - handleWidthPx) / usableWidth()).coerceIn(0f, 1f)

    fun setRange(left: Float, right: Float) {
        leftPos = left.coerceIn(0f, 1f)
        rightPos = right.coerceIn(0f, 1f)
        invalidate()
    }

    fun getLeftPos() = leftPos
    fun getRightPos() = rightPos

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        val leftX = normToPixel(leftPos)
        val rightX = normToPixel(rightPos)

        // Draw dark overlay on unselected left region
        paintFill.color = overlayColor
        canvas.drawRect(0f, 0f, leftX, h, paintFill)

        // Draw dark overlay on unselected right region
        canvas.drawRect(rightX, 0f, w, h, paintFill)

        // Draw top and bottom borders of selection
        paintFill.color = primaryColor
        canvas.drawRect(leftX, 0f, rightX, borderHeightPx, paintFill)
        canvas.drawRect(leftX, h - borderHeightPx, rightX, h, paintFill)

        // Draw left handle
        val leftHandleRect = RectF(leftX - handleWidthPx, 0f, leftX, h)
        paintFill.color = primaryColor
        canvas.drawRoundRect(leftHandleRect, 6f * density, 6f * density, paintFill)
        drawGripLines(canvas, leftHandleRect)

        // Draw right handle
        val rightHandleRect = RectF(rightX, 0f, rightX + handleWidthPx, h)
        paintFill.color = primaryColor
        canvas.drawRoundRect(rightHandleRect, 6f * density, 6f * density, paintFill)
        drawGripLines(canvas, rightHandleRect)
    }

    private fun drawGripLines(canvas: Canvas, rect: RectF) {
        val cx = rect.centerX()
        val lineH = rect.height() * 0.3f
        val top = rect.centerY() - lineH / 2
        val bottom = rect.centerY() + lineH / 2
        val gap = 3f * density
        canvas.drawLine(cx - gap, top, cx - gap, bottom, paintGrip)
        canvas.drawLine(cx + gap, top, cx + gap, bottom, paintGrip)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val leftX = normToPixel(leftPos)
                val rightX = normToPixel(rightPos)

                val distLeft = kotlin.math.abs(x - leftX)
                val distRight = kotlin.math.abs(x - rightX)

                // Determine which handle is closer if both are within slop
                dragging = when {
                    distLeft <= touchSlopPx && distRight <= touchSlopPx -> {
                        if (distLeft < distRight) DragTarget.LEFT else DragTarget.RIGHT
                    }
                    distLeft <= touchSlopPx -> DragTarget.LEFT
                    distRight <= touchSlopPx -> DragTarget.RIGHT
                    x > leftX && x < rightX -> DragTarget.CENTER
                    else -> DragTarget.NONE
                }

                if (dragging != DragTarget.NONE) {
                    lastTouchX = x
                    parent.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (dragging != DragTarget.NONE) {
                    val newNorm = pixelToNorm(x)

                    when (dragging) {
                        DragTarget.LEFT -> {
                            leftPos = newNorm.coerceAtMost(rightPos - 0.01f)
                        }
                        DragTarget.RIGHT -> {
                            rightPos = newNorm.coerceAtLeast(leftPos + 0.01f)
                        }
                        DragTarget.CENTER -> {
                            val dxNorm = (x - lastTouchX) / usableWidth()
                            val range = rightPos - leftPos
                            var newLeft = leftPos + dxNorm
                            var newRight = rightPos + dxNorm

                            if (newLeft < 0f) {
                                newLeft = 0f
                                newRight = range
                            } else if (newRight > 1f) {
                                newRight = 1f
                                newLeft = 1f - range
                            }

                            leftPos = newLeft
                            rightPos = newRight
                        }
                        else -> {}
                    }

                    lastTouchX = x
                    invalidate()
                    onRangeChanged?.invoke(leftPos, rightPos)
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging != DragTarget.NONE) {
                    dragging = DragTarget.NONE
                    parent.requestDisallowInterceptTouchEvent(false)
                    return true
                }
            }
        }

        return super.onTouchEvent(event)
    }
}
