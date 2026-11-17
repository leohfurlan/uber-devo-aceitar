package br.com.devoaceitar

import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import java.text.NumberFormat

internal class PreferencesEditor(
    private val context: Context,
    private val saved: Preferences,
    private val currency: NumberFormat,
    private val onSave: (Preferences) -> Unit,
) {
    private val chrome = SettingsForm(context, inset = 0)
    private val watched = mutableListOf<EditText>()
    private val sources = listOf(null) + EnergySource.entries
    private var workPlan = saved.workPlan
    private var firstError: EditText? = null

    private fun SettingsForm.watch(title: String, value: String, type: Int = DECIMAL,
                                   caption: String = title, color: Int = SettingsColors.muted) =
        field(title, value, type, caption, color).also(watched::add)

    fun show() {
        val previewForm = SettingsForm(context, inset = 0)
        val previewGroup = previewForm.group("Teste do cartão")
        val scenario = previewGroup.picker("Oferta de exemplo", sampleOffers.map(Scenario::label), 0)
        val caption = previewGroup.label("")
        val preview = FrameLayout(context).apply { minimumHeight = chrome.dp(62) }
        previewGroup.body.addView(preview, LinearLayout.LayoutParams(-1, -2).apply { topMargin = chrome.dp(8) })
        val message = previewGroup.label("").apply { visibility = View.GONE }
        previewForm.body.visibility = View.GONE

        val goals = SettingsForm(context)
        val hourly = goals.group("Ganho líquido por hora")
        val hourlyFloor = SettingsForm(context, inset = 0)
        val hourlyTarget = SettingsForm(context, inset = 0)
        val floorHour = hourlyFloor.watch("Piso líquido (R$/h)", saved.rejectBelowNetPerHour.editableNumber(),
            caption = "Recusar abaixo de\nR$/h", color = SettingsColors.red)
        val goalHour = hourlyTarget.watch("Meta líquida (R$/h)", saved.minNetPerHour.editableNumber(),
            caption = "Aceitar a partir de\nR$/h", color = SettingsColors.mint)
        hourly.columns(hourlyFloor, hourlyTarget)
        val byKm = goals.group("Valor bruto por km")
        byKm.label("Opcional. Deixe os dois campos em branco para desativar.")
        val kmFloor = SettingsForm(context, inset = 0)
        val kmTarget = SettingsForm(context, inset = 0)
        val floorKm = kmFloor.watch("Piso bruto (R$/km) · opcional", saved.grossPerKmBand?.floor.editableNumber(),
            caption = "Recusar abaixo de\nR$/km", color = SettingsColors.red)
        val goalKm = kmTarget.watch("Meta bruta (R$/km) · opcional", saved.grossPerKmBand?.target.editableNumber(),
            caption = "Aceitar a partir de\nR$/km", color = SettingsColors.mint)
        byKm.columns(kmFloor, kmTarget)
        val filters = goals.group("Filtros da oferta")
        val fare = filters.watch("Valor mínimo da oferta (R$) · opcional", saved.minimumFare.editableNumber())
        val rating = filters.watch("Nota mínima (1 a 5) · opcional", saved.minRating.editableNumber())
        val ratingModes = RatingMode.entries
        val ratingMode = filters.picker("Regra da nota", ratingModes.map(RatingMode::label), saved.ratingMode.ordinal)
        val zone = filters.watch("Bairro de destino a evitar", saved.avoidDestination, InputType.TYPE_CLASS_TEXT)

        val vehicle = SettingsForm(context)
        val costSummary = vehicle.group("Custo estimado")
        val perKm = costSummary.label("", 25, SettingsColors.mint)
        val openCalculator = costSummary.button("Calculadora de lucro", primary = true) {}
        val vehicleCost = vehicle.group("Custo do veículo")
        val sourcePicker = vehicleCost.picker("Fonte do custo",
            sources.map { it?.label ?: "Informar custo por km" }, sources.indexOf(saved.energyCost?.source))
        val manualForm = SettingsForm(context, inset = 0)
        val manual = manualForm.watch("Custo variável direto (R$/km)", (saved.centsPerKm / 100.0).editableNumber())
        vehicleCost.body.addView(manualForm.body)
        val energyForms = EnergySource.entries.associateWith { EnergyFields(it, vehicleCost) }
        val includeFixed = vehicleCost.checkbox("Incluir despesas do perfil", saved.includeFixedCosts)

        val card = SettingsForm(context)
        val indicators = card.group("Indicadores")
        indicators.label("Escolha até quatro na ordem em que aparecem no cartão.")
        val metricLabels = CardMetric.entries.map(CardMetric::label)
        val initialMetrics = saved.card.visibleMetrics
        val selectedMetrics = MutableList(MAX_CARD_METRICS) { initialMetrics.getOrNull(it) ?: CardMetric.NONE }
        val metricPickers = selectedMetrics.mapIndexed { index, metric ->
            indicators.picker("${index + 1}º indicador", metricLabels, metric.ordinal)
        }
        indicators.button("Inverter indicadores") {
            val reversed = selectedMetrics.filterNot { it == CardMetric.NONE }.asReversed()
            for (index in selectedMetrics.indices) {
                selectedMetrics[index] = reversed.getOrNull(index) ?: CardMetric.NONE
            }
            for ((index, picker) in metricPickers.withIndex()) picker.setSelection(selectedMetrics[index].ordinal)
        }
        val appearance = card.group("Aparência e duração")
        val alignment = appearance.picker("Alinhamento", CardAlignment.entries.map(CardAlignment::label),
            saved.card.alignment.ordinal)
        val font = appearance.picker("Tamanho da fonte", CardFont.entries.map(CardFont::label), saved.card.font.ordinal)
        val opacity = appearance.watch("Opacidade do fundo (75 a 100%)", saved.card.opacityPercent.toString(),
            InputType.TYPE_CLASS_NUMBER)
        val duration = appearance.watch("Duração máxima (segundos; 0 acompanha a oferta)", saved.card.maxSeconds.toString(),
            InputType.TYPE_CLASS_NUMBER, caption = "Duração máxima (s)")
        appearance.label("Use 0 para acompanhar a duração da oferta.")
        val contrast = appearance.checkbox("Alto contraste", saved.card.highContrast)

        fun cost(mark: Boolean): Pair<Int, EnergyCost?>? {
            val source = sources[sourcePicker.selectedItemPosition]
            if (source != null) return (energyForms.getValue(source).read(mark) ?: return null)
                .let { saved.centsPerKm to it }
            val reais = get(manual, mark, 0.0, Int.MAX_VALUE / 100.0) ?: return null
            return kotlin.math.round(reais * 100).toInt() to null
        }

        fun draft(mark: Boolean): Preferences? {
            firstError = null
            val hourlyFloor = get(floorHour, mark) ?: return null
            val hourlyTarget = get(goalHour, mark, min = hourlyFloor) ?: return null
            val kmActive = floorKm.text.isNotBlank() || goalKm.text.isNotBlank()
            val kmBand = if (kmActive) {
                val floor = get(floorKm, mark) ?: return null
                IncomeBand(floor, get(goalKm, mark, min = floor) ?: return null)
            } else null
            val minFare = if (fare.text.isBlank()) null else get(fare, mark) ?: return null
            val minRating = if (rating.text.isBlank()) null else get(rating, mark, 1.0, 5.0) ?: return null
            val costs = cost(mark) ?: return null
            val opacityPercent = get(opacity, mark, 75.0, 100.0)?.toInt() ?: return null
            val maxSeconds = get(duration, mark, 0.0, 120.0)?.toInt() ?: return null
            if (includeFixed.isChecked && workPlan.fixedReaisPerKm == null) return null
            return saved.copy(
                centsPerKm = costs.first, energyCost = costs.second, minNetPerHour = hourlyTarget,
                rejectBelowNetPerHour = hourlyFloor, grossPerKmBand = kmBand, minimumFare = minFare,
                minRating = minRating, ratingMode = ratingModes[ratingMode.selectedItemPosition],
                avoidDestination = zone.text.toString().trim(), workPlan = workPlan,
                includeFixedCosts = includeFixed.isChecked,
                card = CardOptions(selectedMetrics.filterNot { it == CardMetric.NONE },
                    CardAlignment.entries[alignment.selectedItemPosition], CardFont.entries[font.selectedItemPosition],
                    opacityPercent, maxSeconds, contrast.isChecked),
            )
        }

        fun renderPreview() {
            val currentCost = cost(false)
            val extra = if (includeFixed.isChecked) workPlan.fixedReaisPerKm else 0.0
            val estimate = currentCost?.let { it.second?.reaisPerKm ?: it.first / 100.0 }
            perKm.text = if (estimate != null && extra != null) "${currency.format(estimate + extra)}/km" else "—"
            if (previewForm.body.visibility != View.VISIBLE) return
            val sample = sampleOffers[scenario.selectedItemPosition]
            val offer = sample.offer
            val details = listOfNotNull(offer.payCents?.let { currency.format(it / 100.0) },
                offer.distance.resolved()?.let { "${(it / 1000.0).editableNumber()} km" },
                offer.duration.resolved()?.let { "${(it / 60.0).editableNumber()} min" })
            caption.text = details.joinToString(" · ")
            preview.removeAllViews()
            val preferences = draft(false)
            message.visibility = if (preferences == null) View.VISIBLE else View.GONE
            if (preferences == null) {
                message.text = if (includeFixed.isChecked && workPlan.fixedReaisPerKm == null) {
                    "Informe a quilometragem na calculadora para incluir as despesas."
                } else "Confira os valores em edição para ver a prévia."
                return
            }
            val options = preferences.card
            if (options.maxSeconds > 0) caption.append(" · cartão até ${options.maxSeconds} s")
            val width = if (preview.width > 0) options.alignment.width(preview.width, chrome.dp(240)) else -1
            preview.addView(context.assessmentView(assess(offer, preferences), currency, options,
                width.takeIf { it > 0 }),
                FrameLayout.LayoutParams(width, -2, options.alignment.viewGravity()))
        }

        openCalculator.setOnClickListener {
                firstError = null
                val currentCost = cost(true)
                if (currentCost == null) {
                    firstError?.requestFocus()
                    return@setOnClickListener
                }
                val variable = currentCost.second?.reaisPerKm ?: currentCost.first / 100.0
                showProfitCalculator(context, currency, workPlan, variable, floorKm.decimalValue() ?: 0.0,
                    floorHour.decimalValue() ?: 0.0) { plan, targets ->
                    workPlan = plan
                    if (targets != null) {
                        if (floorKm.text.isBlank()) floorKm.setText("0")
                        goalKm.setText(targets.first.editableNumber())
                        goalHour.setText(targets.second.editableNumber())
                        includeFixed.isChecked = true
                    }
                    renderPreview()
                }
        }

        fun showSelectedCost() {
            val source = sources[sourcePicker.selectedItemPosition]
            manualForm.body.visibility = if (source == null) View.VISIBLE else View.GONE
            for ((type, fields) in energyForms) fields.form.body.visibility =
                if (source == type) View.VISIBLE else View.GONE
            renderPreview()
        }

        val pages = listOf(goals, vehicle, card).map { context.settingsScroll(it.body) }
        var currentTab = 0
        val header = SettingsForm(context).apply {
            val title = section("Preferências")
            body.removeView(title)
            title.textSize = 26f
            val test = control("Teste") {}
            test.setOnClickListener {
                val page = pages[currentTab]
                val wasOpen = previewForm.body.visibility == View.VISIBLE
                if (wasOpen && page.scrollY > previewForm.body.height / 2) {
                    page.smoothScrollTo(0, 0)
                    return@setOnClickListener
                }
                previewForm.body.visibility = if (wasOpen) View.GONE else View.VISIBLE
                val open = previewForm.body.visibility == View.VISIBLE
                test.isSelected = open
                styleControl(test, open)
                if (open) page.smoothScrollTo(0, 0)
                renderPreview()
            }
            body.addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(title, LinearLayout.LayoutParams(0, -2, 1f))
                addView(test, LinearLayout.LayoutParams(-2, -2))
            })
        }
        val panes = FrameLayout(context)
        val tabs = LinearLayout(context).apply { setPadding(chrome.dp(16), 0, chrome.dp(16), chrome.dp(10)) }
        val tabButtons = listOf("Metas", "Veículo", "Cartão").map { chrome.control(it) {} }
        for ((index, page) in pages.withIndex()) {
            panes.addView(page, FrameLayout.LayoutParams(-1, -1))
            tabs.addView(tabButtons[index], LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (index > 0) marginStart = chrome.dp(6)
            })
        }
        fun selectTab(index: Int) {
            currentTab = index
            for ((position, page) in pages.withIndex()) {
                val selected = position == index
                page.visibility = if (selected) View.VISIBLE else View.GONE
                tabButtons[position].isSelected = selected
                chrome.styleControl(tabButtons[position], selected)
            }
        }
        val footer = SettingsForm(context)
        val error = footer.label("", color = SettingsColors.red).apply { visibility = View.GONE }
        val actions = LinearLayout(context)
        val cancel = chrome.control("Cancelar") {}
        val save = chrome.control("Salvar", primary = true) {}
        actions.addView(cancel, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = chrome.dp(8) })
        actions.addView(save, LinearLayout.LayoutParams(0, -2, 1f))
        footer.body.addView(actions)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isFocusableInTouchMode = true
            setBackgroundColor(SettingsColors.canvas)
            addView(header.body, LinearLayout.LayoutParams(-1, -2))
            addView(tabs, LinearLayout.LayoutParams(-1, -2))
            addView(panes, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(footer.body, LinearLayout.LayoutParams(-1, -2))
        }
        // Keep the preview in the scrollable content so the keyboard and small displays cannot hide the active fields.
        val pageContents = listOf(goals, vehicle, card)
        fun movePreview(index: Int) {
            (previewForm.body.parent as? LinearLayout)?.removeView(previewForm.body)
            pageContents[index].body.addView(previewForm.body, 0, LinearLayout.LayoutParams(-1, -2))
        }
        movePreview(0)
        selectTab(0)
        val dialog = context.settingsDialog(container)
        container.requestFocus()
        for ((index, button) in tabButtons.withIndex()) button.setOnClickListener {
            container.requestFocus()
            context.getSystemService(InputMethodManager::class.java)
                ?.hideSoftInputFromWindow(container.windowToken, 0)
            movePreview(index)
            selectTab(index)
        }
        cancel.setOnClickListener { dialog.dismiss() }
        save.setOnClickListener {
            val result = draft(true)
            if (result == null) {
                val field = firstError
                val index = if (field == null) 1 else pages.indexOfFirst { page ->
                    generateSequence(field.parent) { it.parent }.any { it === page }
                }
                if (index >= 0) {
                    movePreview(index)
                    selectTab(index)
                }
                error.text = if (field == null) "Informe a quilometragem na calculadora ou desmarque as despesas."
                    else "Confira o campo destacado."
                error.visibility = View.VISIBLE
                field?.requestFocus()
                renderPreview()
            } else {
                onSave(result)
                dialog.dismiss()
            }
        }
        for (input in watched) input.afterChange {
            input.error = null
            error.visibility = View.GONE
            renderPreview()
        }
        for (picker in listOf(scenario, ratingMode, alignment, font)) {
            picker.onSelectionChanged { renderPreview() }
        }
        for ((index, picker) in metricPickers.withIndex()) {
            picker.onSelectionChanged {
                val selection = CardMetric.entries[picker.selectedItemPosition]
                val previous = selectedMetrics[index]
                if (selection != previous) {
                    val duplicate = if (selection == CardMetric.NONE) -1 else selectedMetrics.indexOf(selection)
                    selectedMetrics[index] = selection
                    if (duplicate >= 0) {
                        selectedMetrics[duplicate] = previous
                        metricPickers[duplicate].setSelection(previous.ordinal)
                    }
                }
                renderPreview()
            }
        }
        sourcePicker.onSelectionChanged { showSelectedCost() }
        for (energy in energyForms.values) energy.onModeChanged(::renderPreview)
        includeFixed.setOnCheckedChangeListener { _, _ -> renderPreview() }
        contrast.setOnCheckedChangeListener { _, _ -> renderPreview() }
        preview.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) renderPreview()
        }
        showSelectedCost()
    }

    private fun get(field: EditText, mark: Boolean, min: Double = 0.0, max: Double = Double.MAX_VALUE): Double? {
        val value = field.decimalValue()?.takeIf { it in min..max }
        if (mark) field.error = if (value == null) "Confira este valor." else null
        if (mark && value == null && firstError == null) firstError = field
        return value
    }

    private inner class EnergyFields(private val source: EnergySource, parent: SettingsForm) {
        val form = SettingsForm(context, inset = 0).also { parent.body.addView(it.body) }
        private val original = saved.energyCost?.takeIf { it.source == source }
        private val price = form.watch("Preço (R$/${source.unit})", original?.pricePerUnit.editableNumber())
        private val mode = if (source == EnergySource.ELECTRIC) form.picker(
            "Como você mede o consumo?", listOf("Rendimento em km/kWh", "Autonomia e capacidade da bateria"),
            if (original?.electricRangeKm != null) 1 else 0,
        ) else null
        private val directFields = SettingsForm(context, inset = 0).also { form.body.addView(it.body) }
        private val direct = directFields.watch("Rendimento (km/${source.unit})",
            original?.takeIf { it.electricRangeKm == null }?.kmPerUnit.editableNumber())
        private val batteryFields = mode?.let { SettingsForm(context, inset = 0).also { form.body.addView(it.body) } }
        private val range = batteryFields?.watch("Autonomia por carga (km)", original?.electricRangeKm.editableNumber())
        private val battery = batteryFields?.watch("Capacidade útil da bateria (kWh)",
            original?.electricBatteryKwh.editableNumber())
        private val extra = form.watch("Outros custos variáveis (R$/km) · opcional",
            original?.otherCostPerKm.editableNumber())

        fun onModeChanged(change: () -> Unit) {
            val picker = mode ?: return
            fun update() {
                val useBattery = picker.selectedItemPosition == 1
                directFields.body.visibility = if (useBattery) View.GONE else View.VISIBLE
                batteryFields?.body?.visibility = if (useBattery) View.VISIBLE else View.GONE
            }
            picker.onSelectionChanged {
                update()
                change()
            }
            update()
        }

        fun read(mark: Boolean): EnergyCost? {
            val energyPrice = get(price, mark) ?: return null
            val useBattery = mode?.selectedItemPosition == 1
            val rangeKm = if (useBattery) get(checkNotNull(range), mark, min = 0.000001) ?: return null else null
            val batteryKwh = if (useBattery) get(checkNotNull(battery), mark, min = 0.000001) ?: return null else null
            val efficiency = if (useBattery) electricRendimento(rangeKm, batteryKwh)
                else get(direct, mark, min = 0.000001)
            val variable = if (extra.text.isBlank()) 0.0 else get(extra, mark) ?: return null
            return efficiency?.let {
                EnergyCost(source, energyPrice, it, variable, rangeKm, batteryKwh).takeIf(EnergyCost::isValid)
            }
        }
    }
}
