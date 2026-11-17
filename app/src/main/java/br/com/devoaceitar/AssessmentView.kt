package br.com.devoaceitar

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import java.text.NumberFormat

internal fun CardAlignment.viewGravity(): Int = when (this) {
    CardAlignment.FULL, CardAlignment.LEFT -> Gravity.LEFT
    CardAlignment.CENTER -> Gravity.CENTER_HORIZONTAL
    CardAlignment.RIGHT -> Gravity.RIGHT
}

internal fun Context.assessmentView(
    result: Assessment,
    currency: NumberFormat,
    options: CardOptions = CardOptions(),
    widthPx: Int? = null,
    onDismiss: (() -> Unit)? = null,
): LinearLayout {
    val (fill, ink) = if (options.highContrast) Color.BLACK to Color.WHITE else when (result.recommendation) {
        Recommendation.ACCEPT -> Color.rgb(8, 76, 48) to Color.WHITE
        Recommendation.REVIEW -> Color.rgb(255, 212, 118) to Color.rgb(44, 29, 8)
        Recommendation.DECLINE -> Color.rgb(139, 30, 43) to Color.WHITE
    }
    val density = resources.displayMetrics.density
    fun dp(value: Int) = (value * density + 0.5f).toInt()
    fun text(value: String, size: Int, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = (size + options.font.delta).toFloat()
        setTextColor(ink)
        includeFontPadding = false
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(13), dp(8), dp(13), dp(9))
        background = GradientDrawable().apply {
            setColor(Color.argb((options.opacityPercent * 255 + 50) / 100,
                Color.red(fill), Color.green(fill), Color.blue(fill)))
            cornerRadius = dp(10).toFloat()
        }
        val heading = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(text(result.recommendation.label, 24, bold = true).apply {
                if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
            }, LinearLayout.LayoutParams(0, -2, 1f))
            if (onDismiss != null) {
                addView(Button(context).apply {
                    text = "×"
                    textSize = 24f
                    contentDescription = "Dispensar análise"
                    setTextColor(ink)
                    setPadding(0, 0, 0, 0)
                    setBackgroundColor(Color.TRANSPARENT)
                    stateListAnimator = null
                    setOnClickListener { onDismiss() }
                }, LinearLayout.LayoutParams(dp(48), dp(48)))
            }
        }
        addView(heading)
        val metrics = options.visibleMetrics.mapNotNull { metric ->
            metric.value(result)?.let { amount -> metric to amount }
        }
        if (metrics.isNotEmpty()) {
            val dense = metrics.size > 2
            val numberSize = if (dense) 18 else 20
            val unitSize = if (dense) 12 else 13
            val cells = metrics.map { (metricType, amount) ->
                val number = text(metricType.format(amount, currency), numberSize, bold = true)
                val unit = text(metricType.unit, unitSize)
                val preferredWidth = maxOf(
                    number.paint.measureText(number.text.toString()),
                    unit.paint.measureText(unit.text.toString()),
                )
                number.setSingleLine()
                number.setAutoSizeTextTypeUniformWithConfiguration(
                    12, numberSize + options.font.delta, 1, TypedValue.COMPLEX_UNIT_SP,
                )
                val cell = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(number, LinearLayout.LayoutParams(-1, -2))
                    addView(unit)
                }
                cell to preferredWidth
            }
            val contentWidth = (widthPx ?: resources.displayMetrics.widthPixels - dp(40)) - paddingLeft - paddingRight
            val gap = dp(8)
            val columns = if (dense && cells.all { it.second <= (contentWidth - gap * (cells.size - 1)) / cells.size }) {
                cells.size
            } else minOf(2, cells.size)
            for (line in cells.chunked(columns)) {
                val row = LinearLayout(context)
                for (index in 0 until columns) {
                    val cell = line.getOrNull(index)?.first ?: Space(context)
                    row.addView(cell, LinearLayout.LayoutParams(0, -2, 1f).apply {
                        if (index > 0) marginStart = gap
                    })
                }
                addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
            }
        }
        result.avoidedDestination?.let { neighborhood ->
            addView(avoidedDestinationView(neighborhood).apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(5) })
        }
    }
}
