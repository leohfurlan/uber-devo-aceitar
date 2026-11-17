package br.com.devoaceitar

private const val WEEKS_PER_YEAR = 52.0

data class WorkPlan(
    val daysPerWeek: Int? = null,
    val hoursPerDay: Double? = null,
    val kmPerDay: Double? = null,
    val weeklyGoal: Double? = null,
    val monthlyCosts: Double = 0.0,
    val annualCosts: Double = 0.0,
) {
    private val days get() = daysPerWeek?.takeIf { it in 1..7 }?.toDouble()
    val weeklyHours get() = days?.let { hoursPerDay?.takeIf { hours -> hours in 0.0..24.0 }?.times(it) }
    val weeklyKm get() = days?.let { kmPerDay?.takeIf { km -> km.isFinite() && km >= 0 }?.times(it) }
    val weeklyFixedCosts get() = (monthlyCosts * 12 + annualCosts) / WEEKS_PER_YEAR
    val fixedReaisPerKm get() = weeklyFixedCosts.per(weeklyKm)

    fun project(variableReaisPerKm: Double, increasePercent: Int = 0): ProfitProjection {
        val gross = weeklyGoal?.takeIf { it.isFinite() && it >= 0 }?.scale(1 + increasePercent / 100.0)
        val costs = weeklyKm?.let { (variableReaisPerKm * it + weeklyFixedCosts).takeIf(Double::isFinite) }
        return ProfitProjection(gross, costs, weeklyKm, weeklyHours, days)
    }
}

enum class WorkPeriod(val label: String) { DAY("Dia"), WEEK("Semana"), MONTH("Mês") }

data class ProfitProjection(
    val gross: Double?,
    val costs: Double?,
    val km: Double?,
    val hours: Double?,
    val days: Double?,
) {
    val net get() = gross?.let { revenue -> costs?.let(revenue::minus) }
    val margin get() = net?.per(gross)?.times(100)
    val grossPerKm get() = gross?.per(km)
    val grossPerHour get() = gross?.per(hours)
    val netPerHour get() = net?.per(hours)
    val costPerKm get() = costs?.per(km)
    val costPerHour get() = costs?.per(hours)

    fun forPeriod(period: WorkPeriod): ProfitProjection {
        val factor = when (period) {
            WorkPeriod.DAY -> 1.0.per(days)
            WorkPeriod.WEEK -> 1.0
            WorkPeriod.MONTH -> WEEKS_PER_YEAR / 12
        }
        return copy(gross = gross.scale(factor), costs = costs.scale(factor),
            km = km.scale(factor), hours = hours.scale(factor))
    }
}

internal fun electricRendimento(rangeKm: Double?, batteryKwh: Double?): Double? =
    rangeKm?.takeIf { it.isFinite() && it > 0 }?.per(batteryKwh)

private fun Double.per(divisor: Double?): Double? =
    divisor?.takeIf { it.isFinite() && it > 0 }?.let { (this / it).takeIf(Double::isFinite) }

private fun Double?.scale(factor: Double?): Double? =
    if (this != null && factor != null) (this * factor).takeIf(Double::isFinite) else null
