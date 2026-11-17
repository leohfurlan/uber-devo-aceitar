package br.com.devoaceitar

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.TextView

internal fun Context.avoidedDestinationView(neighborhood: String): TextView {
    val density = resources.displayMetrics.density
    val horizontal = (8 * density + 0.5f).toInt()
    val vertical = (4 * density + 0.5f).toInt()
    return TextView(this).apply {
        text = "Bairro a evitar · $neighborhood"
        textSize = 15f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Color.rgb(255, 239, 237))
        setPadding(horizontal, vertical, horizontal, vertical)
        background = GradientDrawable().apply {
            setColor(Color.rgb(132, 31, 28))
            cornerRadius = 6 * density
        }
    }
}
