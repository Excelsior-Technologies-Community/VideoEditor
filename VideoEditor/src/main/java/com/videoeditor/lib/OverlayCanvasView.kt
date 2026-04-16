package com.videoeditor.lib

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.videoeditor.lib.overlay.OverlayItem
import kotlin.math.*

class OverlayCanvasView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val overlays = mutableListOf<OverlayItem>()
    private val gifDrawables = mutableMapOf<String, GifDrawable>()
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
        if (item is OverlayItem.GifOverlay) {
            loadGifDrawable(item)
        }
        invalidate()
        onOverlaySelected?.invoke(item)
    }

    private fun loadGifDrawable(item: OverlayItem.GifOverlay) {
        Glide.with(context)
            .asGif()
            .load(item.gifBytes)
            .into(object : CustomTarget<GifDrawable>() {
                override fun onResourceReady(resource: GifDrawable, transition: Transition<in GifDrawable>?) {
                    resource.callback = object : Drawable.Callback {
                        override fun invalidateDrawable(who: Drawable) { invalidate() }
                        override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) { postDelayed(what, `when`) }
                        override fun unscheduleDrawable(who: Drawable, what: Runnable) { removeCallbacks(what) }
                    }
                    resource.start()
                    gifDrawables[item.id] = resource
                    invalidate()
                }
                override fun onLoadCleared(placeholder: Drawable?) {
                    gifDrawables.remove(item.id)
                }
            })
    }

    fun removeOverlay(id: String) {
        overlays.removeAll { it.id == id }
        gifDrawables.remove(id)?.stop()
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
                is OverlayItem.GifOverlay -> drawGif(canvas, item)
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
            isFakeBoldText = item.bold && typeface != Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.LEFT
        }

        val bounds = Rect()
        textPaint.getTextBounds(item.text, 0, item.text.length, bounds)
        val fm = textPaint.fontMetrics
        val advanceWidth = textPaint.measureText(item.text)

        val contentCenterX = (min(0f, bounds.left.toFloat()) + max(advanceWidth, bounds.right.toFloat())) / 2f
        val contentCenterY = (fm.ascent + fm.descent) / 2f

        if (item.bgColor != Color.TRANSPARENT) {
            bgPaint.color = item.bgColor
            val totalWidth = max(advanceWidth, bounds.right.toFloat()) - min(0f, bounds.left.toFloat())
            val totalHeight = fm.descent - fm.ascent
            val bgRect = RectF(-totalWidth/2f - 40f, -totalHeight/2f - 20f, totalWidth/2f + 40f, totalHeight/2f + 20f)
            canvas.drawRoundRect(bgRect, 20f, 20f, bgPaint)
        }

        if (item.hasShadow) textPaint.setShadowLayer(10f, 5f, 5f, Color.BLACK)
        else textPaint.clearShadowLayer()

        canvas.drawText(item.text, -contentCenterX, -contentCenterY, textPaint)
    }

    private fun drawGif(canvas: Canvas, item: OverlayItem.GifOverlay) {
        val drawable = gifDrawables[item.id]
        if (drawable != null) {
            val hw = drawable.intrinsicWidth / 2f
            val hh = drawable.intrinsicHeight / 2f
            drawable.setBounds((-hw).toInt(), (-hh).toInt(), hw.toInt(), hh.toInt())
            drawable.draw(canvas)
        }
    }

    private fun getLocalBounds(item: OverlayItem): RectF {
        return when (item) {
            is OverlayItem.TextOverlay -> {
                textPaint.apply { textSize = item.fontSize; typeface = item.typeface }
                val bounds = Rect()
                textPaint.getTextBounds(item.text, 0, item.text.length, bounds)
                val fm = textPaint.fontMetrics
                val totalWidth = max(textPaint.measureText(item.text), bounds.right.toFloat()) - min(0f, bounds.left.toFloat())
                val totalHeight = fm.descent - fm.ascent
                RectF(-totalWidth/2f - 40f, -totalHeight/2f - 20f, totalWidth/2f + 40f, totalHeight/2f + 20f)
            }
            is OverlayItem.GifOverlay -> {
                val drawable = gifDrawables[item.id]
                val w = drawable?.intrinsicWidth ?: 100
                val h = drawable?.intrinsicHeight ?: 100
                RectF(-w/2f, -h/2f, w/2f, h/2f)
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
            val matrix = Matrix().apply {
                setTranslate(cx, cy)
                preRotate(item.rotation)
                preScale(item.scale, item.scale)
            }
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
