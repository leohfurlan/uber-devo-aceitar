package br.com.devoaceitar.lab

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

internal class LabMapView(context: Context) : View(context) {
    private val brush = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    private val scale = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoom = (zoom * detector.scaleFactor).coerceIn(1f, 2.5f)
            constrainPan()
            invalidate()
            return true
        }
    })
    private val gesture = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent) = true

        override fun onScroll(first: MotionEvent?, last: MotionEvent, dx: Float, dy: Float): Boolean {
            if (!scale.isInProgress) {
                panX -= dx
                panY -= dy
                constrainPan()
                invalidate()
            }
            return true
        }

        override fun onDoubleTap(event: MotionEvent): Boolean {
            zoom = if (zoom > 1f) 1f else 1.5f
            constrainPan()
            invalidate()
            return true
        }
    })

    var routeVisible = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    var collectionTarget: Int? = null
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    init {
        contentDescription = "Mapa esquemático de Uberlândia"
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scale.onTouchEvent(event)
        gesture.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun constrainPan() {
        val horizontal = width * (zoom - 1f) / 2
        val vertical = height * (zoom - 1f) / 2
        panX = panX.coerceIn(-horizontal, horizontal)
        panY = panY.coerceIn(-vertical, vertical)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(223, 232, 231))
        if (width == 0 || height == 0) return
        val save = canvas.save()
        canvas.translate(panX, panY)
        canvas.scale(zoom, zoom, width / 2f, height / 2f)
        canvas.scale(width / 400f, height / 700f)

        brush.style = Paint.Style.FILL
        brush.color = Color.rgb(204, 222, 207)
        canvas.drawOval(-65f, 165f, 75f, 285f, brush)
        canvas.drawOval(304f, 37f, 484f, 176f, brush)
        canvas.drawOval(263f, 530f, 455f, 708f, brush)
        brush.style = Paint.Style.STROKE
        brush.strokeWidth = 1.7f
        brush.color = Color.rgb(246, 249, 247)
        for (x in -140..460 step 23) {
            canvas.drawLine(x.toFloat(), 0f, x + 140f, 700f, brush)
        }
        for (y in 18..750 step 19) {
            canvas.drawLine(0f, y.toFloat(), 400f, y - 95f, brush)
        }
        brush.color = Color.rgb(172, 197, 210)
        brush.strokeWidth = 4f
        canvas.drawPath(path(341f, 0f, 306f, 80f, 326f, 165f, 292f, 213f, 302f, 312f,
            238f, 415f, 254f, 514f, 203f, 700f), brush)

        brush.style = Paint.Style.FILL
        brush.color = Color.rgb(74, 94, 101)
        brush.textSize = 19f
        brush.textAlign = Paint.Align.CENTER
        canvas.drawText("Uberlândia", 208f, 227f, brush)

        if (routeVisible) {
            val firstLeg = floatArrayOf(29f, 239f, 46f, 210f, 83f, 221f, 108f, 189f, 177f, 175f,
                188f, 134f, 207f, 102f)
            val secondLeg = floatArrayOf(323f, 185f, 333f, 151f, 357f, 167f, 367f, 110f)
            val route = when (collectionTarget) {
                0 -> path(*firstLeg)
                1 -> path(29f, 239f, 65f, 230f, 83f, 250f, 219f, 251f, 259f, 209f,
                    306f, 219f, 333f, 151f, 357f, 167f, 367f, 110f)
                else -> path(*(firstLeg + secondLeg))
            }
            brush.style = Paint.Style.STROKE
            brush.color = ink
            brush.strokeWidth = 11f
            canvas.drawPath(route, brush)
            brush.color = Color.rgb(207, 227, 216)
            brush.strokeWidth = 5f
            canvas.drawPath(route, brush)
            marker(canvas, 29f, 239f, ink)
            if (collectionTarget != 1) {
                marker(canvas, 207f, 102f, if (collectionTarget == 0) green else Color.rgb(28, 80, 117))
            }
            if (collectionTarget != 0) marker(canvas, 367f, 110f, green)
        }
        canvas.restoreToCount(save)
    }

    private fun path(vararg points: Float) = Path().apply {
        moveTo(points[0], points[1])
        for (index in 2 until points.size step 2) lineTo(points[index], points[index + 1])
    }

    private fun marker(canvas: Canvas, x: Float, y: Float, color: Int) {
        brush.style = Paint.Style.FILL
        brush.color = color
        canvas.drawCircle(x, y, 15f, brush)
        brush.color = Color.WHITE
        canvas.drawCircle(x, y, 5f, brush)
    }
}
