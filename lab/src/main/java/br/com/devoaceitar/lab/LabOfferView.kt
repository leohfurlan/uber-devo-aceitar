package br.com.devoaceitar.lab

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

internal val ink = Color.rgb(21, 29, 25)
internal val green = Color.rgb(0, 108, 57)
private val muted = Color.rgb(72, 83, 77)

internal class LabOfferView(
    context: Context,
    private val scenario: Scenario,
    identity: String,
    decline: () -> Unit,
    accept: () -> Unit,
) : LinearLayout(context) {
    private val remainingFill = CountdownBackground(context.dp(10).toFloat())

    init {
        id = R.id.lab_offer
        contentDescription = "Oferta de laboratório $identity"
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        orientation = VERTICAL
        background = context.rounded(Color.WHITE, 20, green, 2)
        setPadding(context.dp(16), context.dp(10), context.dp(16), context.dp(12))
        elevation = context.dp(4).toFloat()

        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        val kind = context.pill(scenario.kind, green, Color.WHITE, R.id.offer_kind)
        header.addView(kind)
        if (scenario.exclusive) {
            header.addView(context.pill("Exclusivo", Color.rgb(225, 248, 226), green),
                LayoutParams(-2, -2).apply { marginStart = context.dp(6) })
        }
        header.addView(View(context), LayoutParams(0, 1, 1f))
        header.addView(context.action("×", R.id.decline_offer, decline).apply {
            contentDescription = "Recusar oferta"
            setTextColor(ink)
            textSize = 26f
            background = context.rounded(Color.rgb(244, 247, 245), 10)
        }, LayoutParams(context.dp(48), context.dp(48)))
        addView(header)
        addView(context.label(scenario.amount, 38, R.id.offer_amount, bold = true))
        if (scenario.userConfirmation) {
            addView(context.pill("Confirmação do usuário", Color.rgb(237, 241, 238), ink),
                LayoutParams(-2, -2))
        } else {
            scenario.rating?.let { rating ->
                val line = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
                line.addView(context.label("★ ", 15, color = green))
                line.addView(context.label(rating, 15, R.id.offer_rating))
                addView(line)
            }
        }
        val pickupLeg = scenario.pickupLeg
        val tripLeg = scenario.tripLeg
        if (pickupLeg != null && tripLeg != null) {
            divider()
            val pickupLabel = if (scenario.kind == "Entrega") "Até a coleta" else "Até o embarque"
            addLeg(pickupLabel, pickupLeg, R.id.offer_pickup_duration, R.id.offer_pickup_distance)
            addLeg("Até o destino", tripLeg, R.id.offer_trip_duration, R.id.offer_trip_distance)
        } else if (scenario.duration != null || scenario.distance != null) {
            divider()
            val trip = LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, context.dp(8), 0, context.dp(8))
            }
            trip.addView(context.label("◷", 23), LayoutParams(context.dp(30), -2))
            scenario.duration?.let { trip.addView(context.label(it, 17, R.id.offer_duration)) }
            if (scenario.duration != null && scenario.distance != null) trip.addView(context.label(" (", 17))
            scenario.distance?.let { trip.addView(context.label(it, 17, R.id.offer_distance)) }
            if (scenario.duration != null && scenario.distance != null) trip.addView(context.label(")", 17))
            addView(trip)
        }
        if (scenario.pickup != null || scenario.destination != null || scenario.collections.isNotEmpty()) {
            divider()
            val route = LinearLayout(context).apply {
                gravity = Gravity.TOP
                setPadding(0, context.dp(9), 0, context.dp(7))
            }
            route.addView(context.label("⋮", 24), LayoutParams(context.dp(30), -2))
            val stops = LinearLayout(context).apply { orientation = VERTICAL }
            scenario.pickup?.let { stops.addView(context.label(it, 16, bold = true).apply { maxLines = 2 }) }
            if (scenario.collections.isNotEmpty()) {
                stops.addView(context.label("${scenario.collections.size} coletas", 14, color = muted))
            }
            for (stop in scenario.collections) {
                stops.addView(context.label(stop.store, 16, bold = true))
            }
            scenario.destination?.let {
                stops.addView(context.label(it, 15, R.id.offer_destination, color = muted).apply {
                    maxLines = 3
                    ellipsize = TextUtils.TruncateAt.END
                })
            }
            route.addView(stops, LayoutParams(0, -2, 1f))
            addView(route)
        }
        scenario.wait?.let { wait ->
            val row = LinearLayout(context)
            row.addView(context.label("Espera na coleta · ", 14))
            row.addView(context.label(wait, 14, R.id.offer_wait))
            addView(row)
        }
        if (scenario.userConfirmation) {
            divider()
            addView(context.pill("Confirmação do usuário", Color.rgb(244, 247, 245), ink),
                LayoutParams(-2, -2).apply { bottomMargin = context.dp(7) })
        }
        addView(context.action("Aceitar", R.id.accept_offer, accept).apply {
            setTextColor(Color.WHITE)
            textSize = 19f
            background = remainingFill
        }, LayoutParams(-1, context.dp(50)).apply { topMargin = context.dp(7) })
    }

    fun showRemaining(fraction: Float) {
        remainingFill.level = (fraction.coerceIn(0f, 1f) * 10_000).toInt()
    }

    private fun addLeg(title: String, leg: OfferLeg, durationId: Int, distanceId: Int) {
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, context.dp(5), 0, context.dp(5))
            addView(context.label(title, 14, color = muted), LayoutParams(0, -2, 1f))
            addView(context.label(leg.duration, 16, durationId))
            addView(context.label(" (", 16))
            addView(context.label(leg.distance, 16, distanceId))
            addView(context.label(")", 16))
        })
    }

    private fun divider() {
        addView(View(context).apply { setBackgroundColor(Color.rgb(224, 230, 226)) },
            LayoutParams(-1, context.dp(1)).apply { topMargin = context.dp(5) })
    }
}

private class CountdownBackground(private val radius: Float) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var opacity = 255

    override fun draw(canvas: Canvas) {
        val left = bounds.left.toFloat()
        val right = bounds.right.toFloat()
        val top = bounds.top.toFloat()
        val bottom = bounds.bottom.toFloat()
        paint.color = Color.rgb(46, 137, 99)
        paint.alpha = opacity
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint)
        val save = canvas.save()
        canvas.clipRect(left, top, left + bounds.width() * level / 10_000f, bottom)
        paint.color = green
        paint.alpha = opacity
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, paint)
        canvas.restoreToCount(save)
    }

    override fun onLevelChange(level: Int): Boolean {
        invalidateSelf()
        return true
    }

    override fun setAlpha(alpha: Int) {
        opacity = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Android keeps this method for Drawable compatibility")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

internal fun Context.dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

internal fun Context.label(value: String, size: Int, viewId: Int = View.NO_ID,
                           bold: Boolean = false, color: Int = ink) = TextView(this).apply {
    id = viewId
    text = value
    textSize = size.toFloat()
    setTextColor(color)
    includeFontPadding = false
    if (bold) setTypeface(typeface, Typeface.BOLD)
}

internal fun Context.rounded(fill: Int, radius: Int, stroke: Int = fill, strokeWidth: Int = 0) =
    GradientDrawable().apply {
        cornerRadius = dp(radius).toFloat()
        setColor(fill)
        if (strokeWidth > 0) setStroke(dp(strokeWidth), stroke)
    }

internal fun Context.pill(title: String, fill: Int, color: Int, viewId: Int = View.NO_ID) =
    label(title, 14, viewId, bold = true, color = color).apply {
        setPadding(dp(8), dp(5), dp(8), dp(5))
        background = rounded(fill, 9)
    }

internal fun Context.action(title: String, viewId: Int = View.NO_ID, onClick: () -> Unit) =
    Button(this).apply {
        id = viewId
        text = title
        isAllCaps = false
        textSize = 14f
        minimumWidth = 0
        minWidth = 0
        setPadding(dp(8), 0, dp(8), 0)
        setTextColor(ink)
        stateListAnimator = null
        backgroundTintList = null
        rippleColorIfSupported()
        setOnClickListener { onClick() }
    }

private fun Button.rippleColorIfSupported() {
    (background as? android.graphics.drawable.RippleDrawable)?.setColor(ColorStateList.valueOf(0x23006C39))
}
