package br.com.devoaceitar.lab

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

internal data class CollectionStop(val store: String)
internal data class OfferLeg(val duration: String, val distance: String)

internal data class Scenario(
    val name: String,
    val payCents: Int,
    val kind: String = "Corrida",
    val distance: String? = "9,3 km",
    val duration: String? = "22 min",
    val destination: String? = "Bairro Central",
    val rating: String? = "4,93",
    val wait: String? = null,
    val pickup: String? = null,
    val exclusive: Boolean = false,
    val userConfirmation: Boolean = false,
    val collections: List<CollectionStop> = emptyList(),
    val pickupLeg: OfferLeg? = null,
    val tripLeg: OfferLeg? = null,
) {
    val amount: String get() = formatMoney(payCents)
}

private val scenarios = linkedMapOf(
    "delivery" to Scenario(
        "Entrega exclusiva", 3934, "Entrega", "27,4 km", "53 min",
        destination = "R. Joaquim Leal de Camargos, 460\nChácaras Tubalina e Quartel\nUberlândia",
        rating = null, pickup = "LATAM (UDI) - Aeroporto", exclusive = true, userConfirmation = true,
    ),
    "batch" to Scenario(
        "Entrega em lote", 3240, "Entrega", "12,6 km", "38 min",
        destination = "Tibery · Uberlândia", rating = null, exclusive = true,
        collections = listOf(CollectionStop("Confeitaria Central"), CollectionStop("Mercado do Bairro")),
    ),
    "split" to Scenario(
        "Envios Carro · dois trechos", 1201, "Entrega", "12,4 km", "33 min",
        destination = "Bairro Planalto", rating = "4,95", pickup = "Bairro Nossa Senhora Aparecida",
        pickupLeg = OfferLeg("12 min", "4.8 km"), tripLeg = OfferLeg("21 min", "7.6 km"),
    ),
    "good" to Scenario("Corrida favorável", 2640, pickup = "Praça Central",
        pickupLeg = OfferLeg("4 min", "1,2 km"), tripLeg = OfferLeg("18 min", "8,1 km")),
    "zone" to Scenario("Zona evitada", 3150, destination = "Bairro Horizonte", pickup = "Praça Central"),
    "wait" to Scenario("Espera na coleta", 1940, "Entrega", "8 km", "20 min",
        rating = null, wait = "15 min", pickup = "Loja simulada"),
    "missing" to Scenario("Dados incompletos", 2200, duration = null, destination = null, rating = null),
    "rating" to Scenario("Nota baixa", 2800, rating = "4,51", pickup = "Praça Central"),
)

private const val NEXT_OFFER_MS = 10_000L
private const val AUTO_OFFER_TTL_MS = 8_000L
private const val MANUAL_OFFER_TTL_MS = 15_000L

class LabActivity : Activity() {
    private enum class Screen { MAP, EARNINGS, ACCOUNT }
    private enum class CollectionState(val label: String) {
        READY("Pendente"), PAUSED("Pausada"), COLLECTED("Coletada"),
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var surface: FrameLayout
    private lateinit var map: LabMapView
    private lateinit var status: TextView
    private lateinit var onlineButton: Button
    private lateinit var scenariosButton: Button
    private lateinit var scroll: ScrollView
    private lateinit var panel: LinearLayout
    private lateinit var navigation: LinearLayout
    private var offerCard: LabOfferView? = null
    private var offerKey: String? = null
    private var activeKey: String? = null
    private var collectionStates = mutableListOf<CollectionState>()
    private var offerIdentity: String? = null
    private var deadline = 0L
    private var offerTtl = 0L
    private var nextScenario = 0
    private var online = false
    private var resumed = false
    private var choosingScenario = false
    private var screen = Screen.MAP
    private var notice = ""
    private val completed = ArrayList<String>()

    private val showNextOffer = Runnable {
        if (canScheduleOffer()) {
            val key = scenarios.keys.elementAt(nextScenario)
            nextScenario = (nextScenario + 1) % scenarios.size
            showOffer(key, AUTO_OFFER_TTL_MS)
        }
    }
    private val tick = object : Runnable {
        override fun run() {
            val card = offerCard ?: return
            val left = deadline - SystemClock.elapsedRealtime()
            if (left <= 0) {
                endOffer("Oferta expirada")
                return
            }
            card.showRemaining(left.toFloat() / offerTtl)
            val countdown = "Oferta sintética · ${(left + 999) / 1000} s"
            if (status.text != countdown) status.text = countdown
            if (resumed) handler.postDelayed(this, minOf(left, 80))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        online = savedInstanceState?.getBoolean("online") ?: false
        nextScenario = savedInstanceState?.getInt("next") ?: 0
        activeKey = savedInstanceState?.getString("active")
        val savedCollections = savedInstanceState?.getIntArray("collections")
        collectionStates = activeKey?.let(scenarios::get)?.collections.orEmpty().mapIndexed { index, _ ->
            val persisted = savedCollections?.getOrNull(index)
            persisted?.let(CollectionState.entries::getOrNull) ?: CollectionState.READY
        }.toMutableList()
        savedInstanceState?.getStringArrayList("completed")?.let(completed::addAll)
        notice = savedInstanceState?.getString("notice").orEmpty()
        screen = Screen.entries[savedInstanceState?.getInt("screen") ?: 0]

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(223, 232, 231)) }
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
        map = LabMapView(this)
        surface.addView(map, FrameLayout.LayoutParams(-1, -1))
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        scroll = ScrollView(this).apply {
            addView(panel)
            isFillViewport = true
        }
        surface.addView(scroll, FrameLayout.LayoutParams(-1, -1).apply {
            topMargin = dp(86)
            bottomMargin = dp(60)
        })
        surface.addView(toolbar(), FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply {
            setMargins(dp(12), dp(8), dp(12), 0)
        })
        navigation = LinearLayout(this).apply {
            background = rounded(Color.WHITE, 13)
            for ((title, destination) in listOf(
                "Mapa" to Screen.MAP, "Ganhos" to Screen.EARNINGS, "Conta" to Screen.ACCOUNT,
            )) {
                addView(action(title) { screen = destination; render() }, LinearLayout.LayoutParams(0, dp(48), 1f))
            }
        }
        surface.addView(navigation, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            setMargins(dp(12), 0, dp(12), dp(8))
        })

        setContentView(root)
        render()
        if (savedInstanceState == null) {
            runScenarioFrom(intent)
        } else {
            val key = savedInstanceState.getString("offer")
            val remaining = savedInstanceState.getLong("offerEnd") - SystemClock.elapsedRealtime()
            if (key != null && remaining > 0) showOffer(key, remaining, savedInstanceState.getString("offerIdentity"))
            if (key != null && remaining <= 0) {
                notice = "Oferta expirada"
                render()
            }
        }
    }

    private fun toolbar(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(10), dp(6), dp(10), dp(6))
        background = rounded(Color.WHITE, 13)
        val row = LinearLayout(this@LabActivity).apply { gravity = Gravity.CENTER_VERTICAL }
        val title = label("Laboratório de ofertas", 15, bold = true)
        if (resources.configuration.screenWidthDp < 360) {
            addView(title)
            row.gravity = Gravity.CENTER
        } else {
            row.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        }
        scenariosButton = action("Cenários") { chooseScenario() }
        row.addView(scenariosButton, LinearLayout.LayoutParams(-2, dp(48)))
        onlineButton = action("Ficar online") { toggleOnline() }
        row.addView(onlineButton, LinearLayout.LayoutParams(-2, dp(48)))
        addView(row)
        status = label("", 14)
        status.setPadding(0, 0, 0, dp(3))
        addView(status)
    }

    private fun toggleOnline() {
        online = !online
        notice = ""
        if (!online && offerCard != null) endOffer("")
        render()
        scheduleOffer()
    }

    private fun chooseScenario() {
        choosingScenario = true
        handler.removeCallbacks(showNextOffer)
        val keys = scenarios.keys.toList()
        AlertDialog.Builder(this)
            .setTitle("Oferta sintética")
            .setItems(scenarios.values.map(Scenario::name).toTypedArray()) { _, index ->
                showOffer(keys[index], MANUAL_OFFER_TTL_MS)
            }
            .setNegativeButton("Fechar", null)
            .setOnDismissListener {
                choosingScenario = false
                scheduleOffer()
            }
            .show()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        runScenarioFrom(intent)
    }

    private fun runScenarioFrom(intent: Intent) {
        val key = intent.getStringExtra("scenario") ?: return
        val duration = intent.getIntExtra("ttl_ms", MANUAL_OFFER_TTL_MS.toInt()).coerceIn(300, 120_000)
        showOffer(key, duration.toLong())
    }

    private fun showOffer(key: String, ttlMs: Long, restoredIdentity: String? = null) {
        val scenario = scenarios[key] ?: return
        if (activeKey != null) return
        removeOffer()
        handler.removeCallbacks(showNextOffer)
        screen = Screen.MAP
        notice = ""
        val identity = restoredIdentity ?: UUID.randomUUID().toString()
        val card = LabOfferView(this, scenario, identity, { endOffer("Oferta recusada") }, {
            activeKey = key
            collectionStates = scenario.collections.map { CollectionState.READY }.toMutableList()
            endOffer("Oferta aceita")
        })
        offerCard = card
        offerKey = key
        offerIdentity = identity
        val placement = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            setMargins(dp(16), 0, dp(16), dp(16))
        }
        surface.addView(card, placement)
        offerTtl = ttlMs
        deadline = SystemClock.elapsedRealtime() + ttlMs
        render()
        tick.run()
    }

    private fun endOffer(message: String) {
        removeOffer()
        notice = message
        render()
        scheduleOffer()
    }

    private fun removeOffer() {
        handler.removeCallbacks(tick)
        offerCard?.let(surface::removeView)
        offerCard = null
        offerKey = null
        offerIdentity = null
    }

    private fun finishTrip(complete: Boolean) {
        val key = activeKey ?: return
        if (complete && collectionStates.any { it != CollectionState.COLLECTED }) return
        if (complete) completed.add(key)
        activeKey = null
        collectionStates.clear()
        notice = if (complete) "Viagem simulada concluída" else "Viagem simulada cancelada"
        render()
        scheduleOffer()
    }

    private fun canScheduleOffer() =
        resumed && online && offerCard == null && activeKey == null && !choosingScenario

    private fun scheduleOffer() {
        handler.removeCallbacks(showNextOffer)
        if (canScheduleOffer()) handler.postDelayed(showNextOffer, NEXT_OFFER_MS)
    }

    private fun render() {
        onlineButton.text = if (online) "Ficar offline" else "Ficar online"
        onlineButton.setTextColor(if (online) green else ink)
        onlineButton.isEnabled = activeKey == null
        scenariosButton.isEnabled = activeKey == null
        val hasOffer = offerCard != null
        navigation.visibility = if (hasOffer) View.GONE else View.VISIBLE
        scroll.visibility = if (screen != Screen.MAP || activeKey != null) View.VISIBLE else View.GONE
        val nextCollection = collectionStates.indexOf(CollectionState.READY).takeIf { it >= 0 }
        map.routeVisible = hasOffer || (activeKey != null &&
            (nextCollection != null || collectionStates.none { it == CollectionState.PAUSED }))
        map.collectionTarget = nextCollection
        status.text = when {
            hasOffer -> "Oferta sintética"
            notice.isNotEmpty() -> notice
            online -> "Online · aguardando ofertas sintéticas"
            else -> "Offline · selecione um cenário para testar"
        }
        panel.removeAllViews()
        when (screen) {
            Screen.MAP -> activeKey?.let { key -> scenarios[key]?.let(::renderTrip) }
            Screen.EARNINGS -> renderEarnings()
            Screen.ACCOUNT -> renderAccount()
        }
    }

    private fun newPanel(title: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(12))
        background = rounded(Color.WHITE, 15)
        addView(label(title, 21, bold = true))
        panel.addView(this)
    }

    private fun LinearLayout.detail(text: String, size: Int = 16) {
        addView(label(text, size).apply { setPadding(0, dp(8), 0, 0) })
    }

    private fun renderTrip(scenario: Scenario) {
        newPanel("${scenario.kind} em andamento").apply {
            detail(scenario.amount, 25)
            listOfNotNull(scenario.pickup, scenario.destination).forEach { detail(it) }
            listOfNotNull(scenario.duration, scenario.distance).takeIf { it.isNotEmpty() }
                ?.let { detail(it.joinToString(" · ")) }
            if (scenario.collections.isEmpty()) {
                addView(action("Concluir simulação") { finishTrip(true) })
            } else {
                renderCollections(scenario)
            }
            addView(action("Cancelar simulação") { finishTrip(false) })
        }
    }

    private fun LinearLayout.renderCollections(scenario: Scenario) {
        val next = collectionStates.indexOf(CollectionState.READY)
        if (next >= 0) {
            detail("Próxima coleta · ${scenario.collections[next].store}")
            addView(action("Marcar como coletada") { setCollectionState(next, CollectionState.COLLECTED) })
            addView(action("Pausar coleta") { setCollectionState(next, CollectionState.PAUSED) })
        }
        for ((index, stop) in scenario.collections.withIndex()) {
            val state = collectionStates[index]
            if (index != next) detail("${stop.store} · ${state.label}", 14)
            if (state == CollectionState.PAUSED) {
                addView(action("Retomar ${stop.store}") { setCollectionState(index, CollectionState.READY) })
            }
        }
        if (collectionStates.all { it == CollectionState.COLLECTED }) {
            addView(action("Concluir simulação") { finishTrip(true) })
        }
    }

    private fun setCollectionState(index: Int, state: CollectionState) {
        collectionStates[index] = state
        render()
    }

    private fun renderEarnings() {
        val trips = completed.mapNotNull(scenarios::get)
        newPanel("Ganhos da sessão").apply {
            detail(formatMoney(trips.sumOf(Scenario::payCents)), 30)
            detail(if (trips.size == 1) "1 viagem concluída" else "${trips.size} viagens concluídas")
            if (trips.isEmpty()) detail("Conclua uma corrida ou entrega no mapa para registrar o valor.")
            for (trip in trips.asReversed()) detail("${trip.name} · ${trip.amount}")
        }
    }

    private fun renderAccount() {
        newPanel("Conta de laboratório").apply {
            detail("Motorista de teste")
            detail(if (online) "Status online" else "Status offline")
            detail("Mapa e ofertas sintéticos. O aceite acontece só no laboratório.", 14)
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        handler.removeCallbacks(tick)
        if (offerCard != null) tick.run() else scheduleOffer()
    }

    override fun onPause() {
        resumed = false
        handler.removeCallbacks(showNextOffer)
        handler.removeCallbacks(tick)
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("online", online)
        outState.putInt("next", nextScenario)
        outState.putString("active", activeKey)
        outState.putIntArray("collections", collectionStates.map { it.ordinal }.toIntArray())
        outState.putStringArrayList("completed", completed)
        outState.putString("notice", notice)
        outState.putInt("screen", screen.ordinal)
        outState.putString("offer", offerKey)
        outState.putString("offerIdentity", offerIdentity)
        outState.putLong("offerEnd", deadline)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}

private fun formatMoney(cents: Int): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
        .format(cents / 100.0)
        .replace('\u00A0', ' ')
