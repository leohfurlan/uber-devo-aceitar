package br.com.devoaceitar

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.text.NumberFormat
import java.util.Locale

class MainActivity : Activity() {
    private val session = OfferSession()
    private val clock = Handler(Looper.getMainLooper())
    private val currency = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
    private val saved by lazy { getSharedPreferences("driver_preferences", MODE_PRIVATE) }
    private lateinit var stage: LinearLayout
    private lateinit var scroll: ScrollView
    private var preferences = Preferences()
    private var expiry: Runnable? = null

    private val background = Color.rgb(21, 26, 24)
    private val surface = Color.rgb(34, 42, 38)
    private val ink = Color.rgb(243, 247, 244)
    private val muted = Color.rgb(187, 203, 193)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = readPreferences(saved)
        val root = column()
        root.setPadding(dp(20), dp(16), dp(20), dp(16))
        root.setBackgroundColor(background)
        root.addView(label("Devo aceitar?", 30, bold = true), rowParams())
        root.addView(label("Simulador de ofertas", 15, muted), rowParams(bottom = 12))
        root.addView(action("Preferências") { editPreferences() }, rowParams(bottom = 12))
        stage = column()
        root.addView(stage, rowParams(bottom = 16))
        root.addView(label("Reproduzir oferta", 17, bold = true), rowParams(bottom = 4))
        for ((index, scenario) in sampleOffers.withIndex()) {
            root.addView(action(scenario.label) { startScenario(index) }, rowParams())
        }
        scroll = ScrollView(this)
        scroll.setBackgroundColor(background)
        scroll.isFillViewport = true
        scroll.addView(root)
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            @Suppress("DEPRECATION")
            view.setPadding(
                insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                insets.systemWindowInsetRight, insets.systemWindowInsetBottom,
            )
            insets
        }
        setContentView(scroll)
        stage.addView(label("Escolha um cenário para ver a análise.", 15, muted), rowParams())
    }

    private fun startScenario(index: Int) {
        expiry?.let { clock.removeCallbacks(it) }
        val active = session.start(sampleOffers[index].offer, SystemClock.elapsedRealtime(), 15_000)
        expiry = Runnable { finishOffer(active.id, "Oferta simulada expirada.") }
            .also { clock.postDelayed(it, 15_000) }
        render(active)
        scroll.scrollTo(0, 0)
    }

    private fun render(active: ActiveOffer) {
        if (session.current(SystemClock.elapsedRealtime())?.id != active.id) return
        val offer = active.offer
        stage.removeAllViews()
        if (session.showAssessment(active.id)) {
            val result = assess(offer, preferences)
            val accent = when (result.recommendation) {
                Recommendation.ACCEPT -> Color.rgb(155, 230, 173)
                Recommendation.REVIEW -> Color.rgb(255, 215, 145)
                Recommendation.DECLINE -> Color.rgb(255, 174, 164)
            }
            val analysis = card(surface, accent)
            analysis.addView(label(result.recommendation.label, 23, accent, bold = true), rowParams())
            result.avoidedDestination?.let {
                analysis.addView(avoidedDestinationView(it), rowParams(top = 4))
            }
            val metrics = listOfNotNull(
                result.grossPerKm?.let { "${currency.format(it)}/km bruto" },
                result.netPerHour?.let { "${currency.format(it)}/h líquido estimado" },
            )
            if (metrics.isNotEmpty()) {
                analysis.addView(label(metrics.joinToString("  ·  "), 15, bold = true), rowParams(top = 4))
            }
            val earnings = listOfNotNull(
                result.grossPerHour?.let { "${currency.format(it)}/h bruto" },
                result.netAmount?.let { "${currency.format(it)} de lucro estimado" },
            )
            if (earnings.isNotEmpty()) {
                analysis.addView(label(earnings.joinToString("  ·  "), 13, muted), rowParams(top = 4))
            }
            preferences.energyCost?.let { energy ->
                val cost = currency.format(preferences.reaisPerKm)
                analysis.addView(label("${energy.source.label} · custo de ${cost}/km", 13, muted), rowParams(top = 4))
            }
            if (result.avoidedDestination == null) {
                analysis.addView(label(result.reason, 15, muted), rowParams(top = 4))
            }
            analysis.addView(action("Dispensar análise") {
                session.dismiss(active.id)
                render(active)
            }, rowParams(top = 4))
            stage.addView(analysis, rowParams(bottom = 6))
        }
        val offerCard = card(Color.rgb(45, 49, 47))
        offerCard.addView(label("${offer.kind} simulada", 14, muted), rowParams())
        val pay = offer.payCents?.let { currency.format(it / 100.0) } ?: "Valor indisponível"
        offerCard.addView(label(pay, 25, bold = true), rowParams())
        offerCard.addView(label(offer.destination ?: "Destino indisponível", 15), rowParams())
        offer.rating?.let {
            offerCard.addView(label("Nota informada: ${it.toString().replace('.', ',')}", 14, muted), rowParams())
        }
        val controls = LinearLayout(this)
        controls.addView(action("Recusar") { finishOffer(active.id, "Oferta simulada recusada.") }, buttonColumn())
        controls.addView(action("Aceitar") { finishOffer(active.id, "Oferta simulada aceita.") }, buttonColumn())
        offerCard.addView(controls, rowParams(top = 6))
        stage.addView(offerCard, rowParams())
    }

    private fun finishOffer(id: Long, message: String) {
        val current = session.current(SystemClock.elapsedRealtime())
        if (current != null && current.id != id) return
        session.end(id)
        clearStage(message)
    }

    private fun clearStage(message: String) {
        expiry?.let { clock.removeCallbacks(it) }
        expiry = null
        stage.removeAllViews()
        stage.addView(label(message, 15, muted), rowParams())
    }

    override fun onStop() {
        session.current(SystemClock.elapsedRealtime())?.let { session.end(it.id) }
        if (expiry != null) clearStage("Escolha um cenário para ver a análise.")
        super.onStop()
    }

    private fun editPreferences() {
        val fields = column()
        fields.setPadding(dp(20), 0, dp(20), dp(8))
        fun field(
            title: String, value: String, type: Int, parent: LinearLayout = fields,
            caption: TextView = label(title, 14, muted),
        ): EditText {
            caption.id = View.generateViewId()
            parent.addView(caption, rowParams(top = 6))
            return EditText(this).also {
                it.id = View.generateViewId()
                caption.labelFor = it.id
                it.setSingleLine()
                it.inputType = type
                it.setText(value)
                parent.addView(it, rowParams())
            }
        }
        val sources = listOf<EnergySource?>(null) + EnergySource.entries
        val selectedEnergy = preferences.energyCost
        val sourcePicker = Spinner(this).apply {
            id = View.generateViewId()
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_item,
                sources.map { it?.label ?: "Informar custo por km" },
            ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            setSelection(sources.indexOf(selectedEnergy?.source))
        }
        fields.addView(label("Combustível ou fonte de energia", 14, muted).apply {
            labelFor = sourcePicker.id
        }, rowParams(top = 6))
        fields.addView(sourcePicker, rowParams())
        val manualFields = column()
        val energyFields = column()
        fields.addView(manualFields, rowParams())
        fields.addView(energyFields, rowParams())
        val cost = field(
            "Custo do carro (centavos por km)", preferences.centsPerKm.toString(),
            InputType.TYPE_CLASS_NUMBER, manualFields,
        )
        val decimal = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        val priceCaption = label("Preço da energia", 14, muted)
        val price = field("Preço da energia", selectedEnergy?.pricePerUnit?.ptDecimal().orEmpty(), decimal,
            energyFields, priceCaption)
        val consumptionCaption = label("Consumo médio", 14, muted)
        val consumption = field("Consumo médio", selectedEnergy?.kmPerUnit?.ptDecimal().orEmpty(), decimal,
            energyFields, consumptionCaption)
        val extra = field(
            "Outros custos (R$/km, opcional)", selectedEnergy?.otherCostPerKm?.ptDecimal().orEmpty(),
            decimal, energyFields,
        )
        var currentSource = selectedEnergy?.source
        fun updateEnergyFields(source: EnergySource?) {
            if (source != currentSource && source != null) {
                val matching = selectedEnergy?.takeIf { it.source == source }
                price.setText(matching?.pricePerUnit?.ptDecimal().orEmpty())
                consumption.setText(matching?.kmPerUnit?.ptDecimal().orEmpty())
                price.error = null
                consumption.error = null
            }
            currentSource = source
            manualFields.visibility = if (source == null) View.VISIBLE else View.GONE
            energyFields.visibility = if (source != null) View.VISIBLE else View.GONE
            source?.let {
                priceCaption.text = "Preço (R$/${it.unit})"
                consumptionCaption.text = "Consumo médio (km/${it.unit})"
            }
        }
        updateEnergyFields(currentSource)
        sourcePicker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateEnergyFields(sources[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        val minimum = field("Meta líquida (R$/h)", preferences.minNetPerHour.toString(), InputType.TYPE_CLASS_NUMBER)
        val floor = field("Recusar abaixo de (R$/h)", preferences.rejectBelowNetPerHour.toString(), InputType.TYPE_CLASS_NUMBER)
        val rating = field("Alertar abaixo da nota", preferences.minRating.toString(),
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val zone = field("Bairro de destino a evitar", preferences.avoidDestination, InputType.TYPE_CLASS_TEXT)
        val form = ScrollView(this).apply { addView(fields) }
        val dialog = AlertDialog.Builder(this).setTitle("Preferências").setView(form)
            .setNegativeButton("Cancelar", null).setPositiveButton("Salvar", null).show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val source = sources[sourcePicker.selectedItemPosition]
            val newCost = if (source == null) cost.text.toString().toIntOrNull() else preferences.centsPerKm
            val newMin = minimum.text.toString().toIntOrNull()
            val newFloor = floor.text.toString().toIntOrNull()
            val newRating = rating.readDecimal()
            val energy = if (source != null) {
                val unitPrice = price.readDecimal()
                val kmPerUnit = consumption.readDecimal()
                val other = if (extra.text.isBlank()) 0.0 else extra.readDecimal()
                when {
                    unitPrice == null || unitPrice < 0 -> {
                        price.error = "Informe um preço válido."
                        return@setOnClickListener
                    }
                    kmPerUnit == null || kmPerUnit <= 0 -> {
                        consumption.error = "Informe um consumo maior que zero."
                        return@setOnClickListener
                    }
                    other == null || other < 0 -> {
                        extra.error = "Informe um custo válido."
                        return@setOnClickListener
                    }
                }
                EnergyCost(source, unitPrice, kmPerUnit, other).takeIf(EnergyCost::isValid) ?: run {
                    consumption.error = "Não foi possível calcular o custo com esses valores."
                    return@setOnClickListener
                }
            } else null
            when {
                newCost == null || newCost < 0 -> cost.error = "Informe um valor válido."
                newMin == null || newMin < 0 -> minimum.error = "Informe um valor válido."
                newFloor == null || newFloor !in 0..newMin -> floor.error = "Informe um valor até a meta."
                newRating == null || newRating !in 1.0..5.0 -> rating.error = "Informe uma nota de 1 a 5."
                else -> {
                    preferences = Preferences(newCost, newMin, newFloor, newRating, zone.text.toString().trim(), energy)
                    savePreferences(saved, preferences)
                    session.current(SystemClock.elapsedRealtime())?.let { render(it) }
                    dialog.dismiss()
                }
            }
        }
    }

    private fun EditText.readDecimal(): Double? =
        text.toString().trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

    private fun Double.ptDecimal(): String = toString().replace('.', ',')

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    private fun card(fill: Int, border: Int? = null) = column().apply {
        setPadding(dp(14), dp(10), dp(14), dp(8))
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(fill)
            border?.let { setStroke(dp(1), it) }
        }
    }

    private fun label(value: String, size: Int, color: Int = ink, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun action(title: String, onClick: () -> Unit) = Button(this).apply {
        text = title
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun rowParams(top: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(top); bottomMargin = dp(bottom) }

    private fun buttonColumn() = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

    private fun dp(value: Int) = (resources.displayMetrics.density * value + 0.5f).toInt()
}
