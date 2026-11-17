package br.com.devoaceitar

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import java.text.NumberFormat
import kotlin.math.round

internal fun showProfitCalculator(
    context: Context,
    currency: NumberFormat,
    initial: WorkPlan,
    variableReaisPerKm: Double,
    floorKm: Double,
    floorHour: Double,
    onApply: (WorkPlan, Pair<Double, Double>?) -> Unit,
) {
    val form = SettingsForm(context)
    form.body.setBackgroundColor(SettingsColors.canvas)
    val periods = WorkPeriod.entries
    var selectedPeriod = WorkPeriod.DAY
    val periodRow = LinearLayout(context)
    val periodButtons = periods.map { form.control(it.label, it == selectedPeriod) {} }
    for ((index, button) in periodButtons.withIndex()) {
        periodRow.addView(button, LinearLayout.LayoutParams(0, -2, 1f).apply {
            if (index > 0) marginStart = form.dp(6)
        })
    }
    form.body.addView(periodRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = form.dp(12) })
    val projectionCard = form.group("Lucro estimado")
    val profit = projectionCard.label("—", 30, SettingsColors.mint).apply { setTypeface(typeface, Typeface.BOLD) }
    val totals = projectionCard.label("", 15)
    val rates = projectionCard.label("").apply { setPadding(0, form.dp(10), 0, 0) }
    val simulation = form.group("Simular ganhos maiores")
    val extra = simulation.label("+0%", 22, SettingsColors.mint)
    val slider = SeekBar(context).apply {
        max = 100
        contentDescription = "Aumento do faturamento semanal"
        progressTintList = ColorStateList.valueOf(SettingsColors.mint)
        thumbTintList = ColorStateList.valueOf(SettingsColors.mint)
    }
    simulation.body.addView(slider)
    val editRoutine = simulation.button("Editar rotina e despesas") {}
    val message = simulation.label("", color = SettingsColors.red).apply { visibility = View.GONE }

    val routine = form.group("Rotina e despesas")
    val days = routine.field("Dias de trabalho por semana (1 a 7)", initial.daysPerWeek?.toString().orEmpty(),
        InputType.TYPE_CLASS_NUMBER)
    val hours = routine.field("Horas por dia", initial.hoursPerDay.editableNumber())
    val km = routine.field("Quilômetros por dia", initial.kmPerDay.editableNumber())
    val goal = routine.field("Meta semanal bruta (R$)", initial.weeklyGoal.editableNumber())
    val monthly = routine.field("Despesas fixas mensais (R$)", initial.monthlyCosts.editableNumber())
    val annual = routine.field("Impostos e taxas anuais (R$)", initial.annualCosts.editableNumber())
    routine.label("Custo variável atual · ${currency.format(variableReaisPerKm)}/km")
    routine.label("Mês médio de 52 ÷ 12 semanas. Dia calculado pelos dias trabalhados.")

    var invalid: EditText? = null
    fun parse(field: EditText, mark: Boolean, lower: Double = 0.0, upper: Double = Double.MAX_VALUE): Double? {
        val value = field.decimalValue()?.takeIf { it in lower..upper }
        if (field.text.isNotBlank() && value == null) {
            if (invalid == null) invalid = field
            if (mark) field.error = "Confira este valor."
        } else if (mark) field.error = null
        return value
    }
    fun read(mark: Boolean): WorkPlan {
        invalid = null
        return WorkPlan(parse(days, mark, 1.0, 7.0)?.toInt(), parse(hours, mark, upper = 24.0),
            parse(km, mark), parse(goal, mark), parse(monthly, mark) ?: 0.0, parse(annual, mark) ?: 0.0)
    }

    fun money(value: Double?) = value?.takeIf(Double::isFinite)?.let(currency::format) ?: "—"
    fun render() {
        val result = read(false).project(variableReaisPerKm, slider.progress)
            .forPeriod(selectedPeriod)
        extra.text = "+${slider.progress}%"
        val margin = result.margin?.let { NumberFormat.getNumberInstance(java.util.Locale.forLanguageTag("pt-BR"))
            .apply { maximumFractionDigits = 1 }.format(it) + "%" } ?: "—"
        profit.text = money(result.net)
        profit.setTextColor(if ((result.net ?: 0.0) < 0) SettingsColors.red else SettingsColors.mint)
        totals.text = "Faturamento ${money(result.gross)}\nCustos ${money(result.costs)} · Margem $margin"
        rates.text = "Meta bruta ${money(result.grossPerKm)}/km · ${money(result.grossPerHour)}/h\n" +
            "Meta líquida ${money(result.netPerHour)}/h\n" +
            "Custo ${money(result.costPerKm)}/km · ${money(result.costPerHour)}/h"
        message.visibility = View.GONE
    }

    val scroll = context.settingsScroll(form.body)
    val header = SettingsForm(context)
    val title = header.section("Calculadora de lucro").apply { textSize = 23f }
    header.body.removeView(title)
    val back = form.control("Voltar") {}
    header.body.addView(LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        addView(back, LinearLayout.LayoutParams(-2, -2).apply { marginStart = form.dp(6) })
    })
    val footer = SettingsForm(context)
    val useProfile = footer.control("Usar perfil") {}
    val applyTargets = footer.control("Aplicar metas", primary = true) {}
    footer.body.addView(LinearLayout(context).apply {
        addView(useProfile, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = form.dp(8) })
        addView(applyTargets, LinearLayout.LayoutParams(0, -2, 1f))
    })
    val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        isFocusableInTouchMode = true
        setBackgroundColor(SettingsColors.canvas)
        addView(header.body, LinearLayout.LayoutParams(-1, -2))
        addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        addView(footer.body, LinearLayout.LayoutParams(-1, -2))
    }
    val dialog = context.settingsDialog(content)
    content.requestFocus()
    back.setOnClickListener { dialog.dismiss() }
    editRoutine.setOnClickListener { scroll.smoothScrollTo(0, routine.body.top) }

    fun apply(useTargets: Boolean) {
        val plan = read(true)
        invalid?.let { it.requestFocus(); return }
        if (!useTargets) {
            onApply(plan, null)
            dialog.dismiss()
            return
        }
        val projection = plan.project(variableReaisPerKm, slider.progress)
        val byKm = projection.grossPerKm?.let { round(it * 100) / 100 }?.takeIf(Double::isFinite)
        val byHour = projection.netPerHour?.let { round(it * 100) / 100 }?.takeIf(Double::isFinite)
        if (byKm == null || byHour == null || byKm < floorKm || byHour < floorHour || byHour < 0) {
            message.text = "Confira a rotina e a meta. As metas sugeridas precisam alcançar os pisos atuais."
            message.visibility = View.VISIBLE
            scroll.smoothScrollTo(0, simulation.body.top)
            return
        }
        onApply(plan, byKm to byHour)
        dialog.dismiss()
    }

    useProfile.setOnClickListener { apply(false) }
    applyTargets.setOnClickListener { apply(true) }
    for (field in listOf(days, hours, km, goal, monthly, annual)) field.afterChange(::render)
    for ((index, button) in periodButtons.withIndex()) button.setOnClickListener {
        selectedPeriod = periods[index]
        for ((position, control) in periodButtons.withIndex()) {
            control.isSelected = position == index
            form.styleControl(control, control.isSelected)
        }
        render()
    }
    periodButtons[WorkPeriod.DAY.ordinal].isSelected = true
    slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = render()
        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
    })
    render()
}
