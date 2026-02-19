package com.sticly

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class CropOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Shape { NONE, CIRCLE, SQUARE, RECTANGLE, VERTICAL_RECT }

    var shape: Shape = Shape.NONE
        set(value) {
            field = value
            offsetX = 0f; offsetY = 0f
            invalidate()
        }

    var videoWidth: Int = 0
    var videoHeight: Int = 0
    fun setVideoDimensions(vw: Int, vh: Int) {
        videoWidth = vw; videoHeight = vh; invalidate()
    }

    /** Normalized offset (0..1 range relative to video rect) for FFmpeg crop */
    var offsetX = 0f; private set
    var offsetY = 0f; private set

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    private val overlayPaint = Paint().apply {
        color = Color.parseColor("#99000000")
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f * resources.displayMetrics.density
    }

    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3.5f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
    }

    private val cropRect = RectF()
    private val videoRect = RectF()
    private val cornerLen = 22f * resources.displayMetrics.density

    private fun calcVideoRect(): RectF {
        val viewW = width.toFloat()
        val viewH = height.toFloat()
        if (videoWidth <= 0 || videoHeight <= 0) return RectF(0f, 0f, viewW, viewH)
        val videoAspect = videoWidth.toFloat() / videoHeight.toFloat()
        val viewAspect = viewW / viewH
        val dw: Float; val dh: Float
        if (videoAspect > viewAspect) { dw = viewW; dh = viewW / videoAspect }
        else { dh = viewH; dw = viewH * videoAspect }
        val left = (viewW - dw) / 2f
        val top = (viewH - dh) / 2f
        return RectF(left, top, left + dw, top + dh)
    }

    /** Get crop offset in actual video pixels for FFmpeg */
    fun getCropOffsetPixels(): Pair<Int, Int> {
        if (videoWidth <= 0 || videoHeight <= 0) return Pair(0, 0)
        videoRect.set(calcVideoRect())
        val scaleX = videoWidth.toFloat() / videoRect.width()
        val scaleY = videoHeight.toFloat() / videoRect.height()
        val pixOffX = ((cropRect.left - videoRect.left) * scaleX).toInt().coerceAtLeast(0)
        val pixOffY = ((cropRect.top - videoRect.top) * scaleY).toInt().coerceAtLeast(0)
        return Pair(pixOffX, pixOffY)
    }

    fun getCropSizePixels(): Pair<Int, Int> {
        if (videoWidth <= 0 || videoHeight <= 0) return Pair(videoWidth, videoHeight)
        videoRect.set(calcVideoRect())
        val scaleX = videoWidth.toFloat() / videoRect.width()
        val scaleY = videoHeight.toFloat() / videoRect.height()
        val w = (cropRect.width() * scaleX).toInt().coerceAtMost(videoWidth)
        val h = (cropRect.height() * scaleY).toInt().coerceAtMost(videoHeight)
        return Pair(w, h)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (shape == Shape.NONE) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (cropRect.contains(event.x, event.y)) {
                    isDragging = true
                    lastTouchX = event.x; lastTouchY = event.y
                    parent.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    offsetX += dx; offsetY += dy
                    clampOffset()
                    lastTouchX = event.x; lastTouchY = event.y
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                parent.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return false
    }

    private fun clampOffset() {
        videoRect.set(calcVideoRect())
        val cw = cropRect.width(); val ch = cropRect.height()
        val baseCx = videoRect.centerX(); val baseCy = videoRect.centerY()
        val newCx = baseCx + offsetX; val newCy = baseCy + offsetY
        val minX = videoRect.left + cw / 2; val maxX = videoRect.right - cw / 2
        val minY = videoRect.top + ch / 2; val maxY = videoRect.bottom - ch / 2
        if (minX < maxX) offsetX = (newCx.coerceIn(minX, maxX)) - baseCx
        if (minY < maxY) offsetY = (newCy.coerceIn(minY, maxY)) - baseCy
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (shape == Shape.NONE) return

        val w = width.toFloat(); val h = height.toFloat()
        videoRect.set(calcVideoRect())
        val vw = videoRect.width(); val vh = videoRect.height()
        val vcx = videoRect.centerX() + offsetX
        val vcy = videoRect.centerY() + offsetY
        val padding = 8f * resources.displayMetrics.density

        when (shape) {
            Shape.SQUARE -> {
                val size = minOf(vw, vh) - padding * 2
                cropRect.set(vcx - size / 2, vcy - size / 2, vcx + size / 2, vcy + size / 2)
                drawOverlayWithRect(canvas, w, h)
                drawCornerBrackets(canvas)
            }
            Shape.RECTANGLE -> {
                val rectW = vw - padding * 2
                val rectH = minOf(vh - padding * 2, rectW * 3f / 4f)
                cropRect.set(vcx - rectW / 2, vcy - rectH / 2, vcx + rectW / 2, vcy + rectH / 2)
                drawOverlayWithRect(canvas, w, h)
                drawCornerBrackets(canvas)
            }
            Shape.VERTICAL_RECT -> {
                val rectH = vh - padding * 2
                val rectW = minOf(vw - padding * 2, rectH * 9f / 16f)
                cropRect.set(vcx - rectW / 2, vcy - rectH / 2, vcx + rectW / 2, vcy + rectH / 2)
                drawOverlayWithRect(canvas, w, h)
                drawCornerBrackets(canvas)
            }
            Shape.CIRCLE -> {
                val size = minOf(vw, vh) - padding * 2
                val radius = size / 2
                cropRect.set(vcx - radius, vcy - radius, vcx + radius, vcy + radius)
                val saved = canvas.saveLayer(0f, 0f, w, h, null)
                canvas.drawRect(0f, 0f, w, h, overlayPaint)
                val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                }
                canvas.drawCircle(vcx, vcy, radius, clearPaint)
                canvas.restoreToCount(saved)
                canvas.drawCircle(vcx, vcy, radius, borderPaint)
                drawCornerBrackets(canvas)
            }
            else -> {}
        }
    }

    private fun drawOverlayWithRect(canvas: Canvas, w: Float, h: Float) {
        val saved = canvas.saveLayer(0f, 0f, w, h, null)
        canvas.drawRect(0f, 0f, w, h, overlayPaint)
        val clearPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
        canvas.drawRect(cropRect, clearPaint)
        canvas.restoreToCount(saved)
        canvas.drawRect(cropRect, borderPaint)
    }

    private fun drawCornerBrackets(canvas: Canvas) {
        val l = cropRect.left; val t = cropRect.top
        val r = cropRect.right; val b = cropRect.bottom
        val cl = cornerLen
        canvas.drawLine(l, t, l + cl, t, cornerPaint)
        canvas.drawLine(l, t, l, t + cl, cornerPaint)
        canvas.drawLine(r, t, r - cl, t, cornerPaint)
        canvas.drawLine(r, t, r, t + cl, cornerPaint)
        canvas.drawLine(l, b, l + cl, b, cornerPaint)
        canvas.drawLine(l, b, l, b - cl, cornerPaint)
        canvas.drawLine(r, b, r - cl, b, cornerPaint)
        canvas.drawLine(r, b, r, b - cl, cornerPaint)
    }
}
