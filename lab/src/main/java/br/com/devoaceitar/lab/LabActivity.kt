package br.com.devoaceitar.lab

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

private data class Scenario(
    val name: String,
    val kind: String = "Corrida",
    val amount: String,
    val distance: String? = "9,3 km",
    val duration: String? = "22 min",
    val destination: String? = "Bairro Central",
    val rating: String? = "4,93",
    val wait: String? = null,
)

private val scenarios = mapOf(
    "good" to Scenario("Corrida favorável", amount = "R$ 26,40"),
    "zone" to Scenario("Zona evitada", amount = "R$ 31,50", destination = "Bairro Horizonte"),
    "wait" to Scenario("Espera na coleta", "Entrega", "R$ 19,40", "8 km", "20 min", rating = null, wait = "15 min"),
    "missing" to Scenario("Dados incompletos", amount = "R$ 22,00", duration = null, destination = null, rating = null),
    "rating" to Scenario("Nota baixa", amount = "R$ 28,00", rating = "4,51"),
)

class LabActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var surface: FrameLayout
    private lateinit var status: TextView
    private var offerCard: LinearLayout? = null
    private var serial = 0L
    private var deadline = 0L
    private val tick = object : Runnable {
        override fun run() {
            val left = deadline - SystemClock.elapsedRealtime()
            if (left <= 0) {
                end("Oferta expirada")
            } else {
                status.text = "Oferta sintética · ${(left + 999) / 1000} s"
                handler.postDelayed(this, minOf(left, 1000))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(18, 25, 34)) }
        surface = FrameLayout(this).apply {
            id = R.id.lab_surface
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        root.addView(surface, FrameLayout.LayoutParams(-1, -1))
        root.setOnApplyWindowInsetsListener { _, insets ->
            @Suppress("DEPRECATION")
            (surface.layoutParams as FrameLayout.LayoutParams).apply {
                setMargins(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }.also { surface.layoutParams = it }
            insets
        }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), 0)
        }
        toolbar.addView(label("Laboratório de ofertas", 22, bold = true))
        status = label("Escolha uma oferta sintética", 15)
        toolbar.addView(status)
        for (row in scenarios.entries.chunked(3)) {
            val buttons = LinearLayout(this)
            for ((key, scenario) in row) {
                buttons.addView(button(scenario.name) { showOffer(key, 15_000) },
                    LinearLayout.LayoutParams(0, -2, 1f))
            }
            toolbar.addView(buttons)
        }
        surface.addView(toolbar, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        setContentView(root)
        runScenarioFrom(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        runScenarioFrom(intent)
    }

    private fun runScenarioFrom(intent: Intent) {
        val key = intent.getStringExtra("scenario") ?: return
        showOffer(key, intent.getIntExtra("ttl_ms", 15_000).coerceIn(300, 120_000).toLong())
    }

    private fun showOffer(key: String, ttlMs: Long) {
        val scenario = scenarios[key] ?: return
        end("")
        val card = LinearLayout(this).apply {
            id = R.id.lab_offer
            contentDescription = "Oferta de laboratório ${++serial}"
            orientation = LinearLayout.VERTICAL
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            setPadding(dp(14), dp(10), dp(14), dp(6))
            setBackgroundColor(Color.rgb(39, 61, 94))
        }
        card.addView(label(scenario.kind, 14, R.id.offer_kind))
        card.addView(label(scenario.amount, 24, R.id.offer_amount, bold = true))
        val trip = LinearLayout(this)
        scenario.distance?.let { trip.addView(label(it, 16, R.id.offer_distance), LinearLayout.LayoutParams(0, -2, 1f)) }
        scenario.duration?.let { trip.addView(label(it, 16, R.id.offer_duration), LinearLayout.LayoutParams(0, -2, 1f)) }
        card.addView(trip)
        scenario.destination?.let { card.addView(label(it, 16, R.id.offer_destination)) }
        scenario.rating?.let { card.addView(label(it, 15, R.id.offer_rating)) }
        scenario.wait?.let { card.addView(label(it, 15, R.id.offer_wait)) }
        val actions = LinearLayout(this)
        actions.addView(button("Recusar", R.id.decline_offer) { end("Oferta recusada") },
            LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(button("Aceitar", R.id.accept_offer) { end("Oferta aceita") },
            LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(actions)
        val placement = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            setMargins(dp(16), 0, dp(16), dp(16))
        }
        offerCard = card
        surface.addView(card, placement)
        deadline = SystemClock.elapsedRealtime() + ttlMs
        tick.run()
    }

    private fun end(message: String) {
        handler.removeCallbacks(tick)
        offerCard?.let { surface.removeView(it) }
        offerCard = null
        status.text = message
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    private fun label(value: String, size: Int, viewId: Int = View.NO_ID, bold: Boolean = false) =
        TextView(this).apply {
            id = viewId
            text = value
            textSize = size.toFloat()
            setTextColor(Color.WHITE)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun button(title: String, viewId: Int = View.NO_ID, onClick: () -> Unit) = Button(this).apply {
        id = viewId
        text = title
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
}
