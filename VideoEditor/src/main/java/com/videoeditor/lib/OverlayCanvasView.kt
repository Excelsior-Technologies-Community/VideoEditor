package com.videoeditor.lib

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.videoeditor.lib.overlay.OverlayItem
import kotlin.math.*

class OverlayCanvasView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val overlays = mutableListOf<OverlayItem>()
    private var selectedId: String? = null
    var onOverlaySelected: ((OverlayItem?) -> Unit)? = null

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var initialSpan = 0f
    private var initialScale = 1f
    private var initialAngle = 0f
    private var initialRotation = 0f
    private var isScaling = false

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
        pathEffect = DashPathEffect(floatArrayOf(15f, 10f), 0f)
    }

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                isScaling = true
                initialScale = selectedOverlay()?.scale ?: 1f
                initialSpan  = detector.currentSpan
                return true
            }
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                selectedOverlay()?.let {
                    it.scale = (initialScale * (detector.currentSpan / initialSpan)).coerceIn(0.3f, 5f)
                    invalidate()
                }
                return true
            }
            override fun onScaleEnd(detector: ScaleGestureDetector) { isScaling = false }
        })

    fun addOverlay(item: OverlayItem) {
        overlays.add(item)
        selectedId = item.id
        invalidate()
        onOverlaySelected?.invoke(item)
    }

    fun removeOverlay(id: String) {
        overlays.removeAll { it.id == id }
        if (selectedId == id) { selectedId = null; onOverlaySelected?.invoke(null) }
        invalidate()
    }

    fun getOverlays(): List<OverlayItem> = overlays.toList()
    fun clearSelection() { selectedId = null; invalidate() }
    fun selectedOverlay(): OverlayItem? = overlays.firstOrNull { it.id == selectedId }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (item in overlays) {
            canvas.save()
            val cx = item.normX * width
            val cy = item.normY * height
            canvas.translate(cx, cy)
            canvas.rotate(item.rotation)
            canvas.scale(item.scale, item.scale)

            when (item) {
                is OverlayItem.TextOverlay  -> drawText(canvas, item)
                is OverlayItem.StickerOverlay -> drawSticker(canvas, item)
            }

            if (item.id == selectedId) {
                val bounds = getLocalBounds(item)
                canvas.drawRect(bounds, selectionPaint)
            }
            canvas.restore()
        }
    }

    private fun drawText(canvas: Canvas, item: OverlayItem.TextOverlay) {
        textPaint.apply {
            color = item.textColor
            textSize = item.fontSize
            typeface = item.typeface
            if (item.bold && typeface != Typeface.DEFAULT_BOLD) {
                isFakeBoldText = true
            } else {
                isFakeBoldText = false
            }
            textAlign = Paint.Align.LEFT
        }

        val bounds = Rect()
        textPaint.getTextBounds(item.text, 0, item.text.length, bounds)
        val fm = textPaint.fontMetrics
        val advanceWidth = textPaint.measureText(item.text)

        val contentLeft = min(0f, bounds.left.toFloat())
        val contentRight = max(advanceWidth, bounds.right.toFloat())
        val totalWidth = contentRight - contentLeft
        val totalHeight = fm.descent - fm.ascent

        val contentCenterX = (contentLeft + contentRight) / 2f
        val contentCenterY = (fm.ascent + fm.descent) / 2f

        val drawX = -contentCenterX
        val drawY = -contentCenterY

        val hPad = 100f
        val topPad = 50f
        val bottomPad = 100f

        if (item.bgColor != Color.TRANSPARENT) {
            bgPaint.color = item.bgColor
            val bgRect = RectF(
                -totalWidth / 2f - hPad,
                -totalHeight / 2f - topPad,
                totalWidth / 2f + hPad,
                totalHeight / 2f + bottomPad
            )
            canvas.drawRoundRect(bgRect, 40f, 40f, bgPaint)
        }

        if (item.hasShadow) textPaint.setShadowLayer(10f, 5f, 5f, Color.BLACK)
        else textPaint.clearShadowLayer()

        canvas.drawText(item.text, drawX, drawY, textPaint)
    }

    private fun drawSticker(canvas: Canvas, item: OverlayItem.StickerOverlay) {
        val hw = item.bitmap.width / 2f
        val hh = item.bitmap.height / 2f
        canvas.drawBitmap(item.bitmap, -hw, -hh, null)
    }

    private fun getLocalBounds(item: OverlayItem): RectF {
        return when (item) {
            is OverlayItem.TextOverlay -> {
                textPaint.apply {
                    textSize = item.fontSize
                    typeface = item.typeface
                }
                val bounds = Rect()
                textPaint.getTextBounds(item.text, 0, item.text.length, bounds)
                val fm = textPaint.fontMetrics
                val advanceWidth = textPaint.measureText(item.text)

                val contentLeft = min(0f, bounds.left.toFloat())
                val contentRight = max(advanceWidth, bounds.right.toFloat())
                val totalWidth = contentRight - contentLeft
                val totalHeight = fm.descent - fm.ascent

                val hPad = 100f
                val topPad = 50f
                val bottomPad = 100f

                RectF(
                    -totalWidth / 2f - hPad,
                    -totalHeight / 2f - topPad,
                    totalWidth / 2f + hPad,
                    totalHeight / 2f + bottomPad
                )
            }

            is OverlayItem.StickerOverlay -> {
                RectF(
                    -item.bitmap.width / 2f,
                    -item.bitmap.height / 2f,
                    item.bitmap.width / 2f,
                    item.bitmap.height / 2f
                )
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                val hit = findOverlayAt(event.x, event.y)
                selectedId = hit?.id
                onOverlaySelected?.invoke(hit)
                invalidate()
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    initialAngle  = angleBetween(event)
                    initialRotation = selectedOverlay()?.rotation ?: 0f
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isScaling) {
                    if (event.pointerCount == 2) {
                        val angle = angleBetween(event)
                        selectedOverlay()?.let {
                            it.rotation = initialRotation + (angle - initialAngle)
                            invalidate()
                        }
                    } else {
                        val dx = event.x - lastTouchX
                        val dy = event.y - lastTouchY
                        selectedOverlay()?.let {
                            it.normX = (it.normX + dx / width).coerceIn(0f, 1f)
                            it.normY = (it.normY + dy / height).coerceIn(0f, 1f)
                            invalidate()
                        }
                        lastTouchX = event.x
                        lastTouchY = event.y
                    }
                }
            }
        }
        return true
    }

    private fun findOverlayAt(x: Float, y: Float): OverlayItem? {
        return overlays.asReversed().firstOrNull { item ->
            val cx = item.normX * width
            val cy = item.normY * height
            val matrix = Matrix()
            matrix.setTranslate(cx, cy)
            matrix.preRotate(item.rotation)
            matrix.preScale(item.scale, item.scale)
            val invMatrix = Matrix()
            matrix.invert(invMatrix)
            val pts = floatArrayOf(x, y)
            invMatrix.mapPoints(pts)
            getLocalBounds(item).contains(pts[0], pts[1])
        }
    }

    private fun angleBetween(event: MotionEvent): Float {
        val dx = event.getX(1) - event.getX(0)
        val dy = event.getY(1) - event.getY(0)
        return Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    }
}
