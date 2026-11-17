package br.com.devoaceitar

import android.content.SharedPreferences

private val CARD_METRIC_KEYS = listOf("card_first", "card_second", "card_third", "card_fourth")

private fun Map<String, *>.decimal(key: String): Double? = when (val value = this[key]) {
    is String -> value.toDoubleOrNull()
    is Float -> value.toString().toDoubleOrNull()
    is Number -> value.toDouble()
    else -> null
}?.takeIf(Double::isFinite)

private inline fun <reified T : Enum<T>> SharedPreferences.choice(key: String, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == getString(key, null) } ?: fallback

internal fun readPreferences(saved: SharedPreferences): Preferences {
    val defaults = Preferences()
    val values = saved.all
    val source = EnergySource.entries.firstOrNull { it.name == values["energy_source"] }
    val price = values.decimal("energy_price")
    val consumption = values.decimal("energy_consumption")
    val other = values.decimal("other_cost_per_km") ?: 0.0
    val energyCost = if (source != null && price != null && consumption != null) {
        EnergyCost(source, price, consumption, other,
            values.decimal("electric_range"), values.decimal("electric_battery")).takeIf(EnergyCost::isValid)
    } else null
    val floor = values.decimal("gross_km_floor")
    val target = values.decimal("gross_km_target")
    val hourly = IncomeBand(values.decimal("floor") ?: defaults.rejectBelowNetPerHour,
        values.decimal("minimum") ?: defaults.minNetPerHour).takeIf(IncomeBand::isValid)
    val card = defaults.card
    val metrics = CARD_METRIC_KEYS.mapIndexed { index, key ->
        saved.choice(key, card.metrics.getOrNull(index) ?: CardMetric.NONE)
    }.filterNot { it == CardMetric.NONE }.distinct()
    return Preferences(
        centsPerKm = saved.getInt("cost", defaults.centsPerKm),
        minNetPerHour = hourly?.target ?: defaults.minNetPerHour,
        rejectBelowNetPerHour = hourly?.floor ?: defaults.rejectBelowNetPerHour,
        minRating = if (values.containsKey("rating")) values.decimal("rating")?.takeIf { it in 1.0..5.0 }
            else defaults.minRating,
        avoidDestination = saved.getString("zone", defaults.avoidDestination).orEmpty(),
        energyCost = energyCost,
        grossPerKmBand = if (floor != null && target != null) IncomeBand(floor, target).takeIf(IncomeBand::isValid)
            else null,
        minimumFare = values.decimal("minimum_fare")?.takeIf { it >= 0 },
        ratingMode = saved.choice("rating_mode", defaults.ratingMode),
        workPlan = WorkPlan(
            (values["work_days"] as? Int)?.takeIf { it in 1..7 },
            values.decimal("work_hours"),
            values.decimal("work_km"),
            values.decimal("work_goal"),
            values.decimal("work_monthly_costs") ?: 0.0,
            values.decimal("work_annual_costs") ?: 0.0,
        ),
        includeFixedCosts = saved.getBoolean("use_work_costs", false),
        card = CardOptions(
            metrics,
            saved.choice("card_alignment", card.alignment), saved.choice("card_font", card.font),
            saved.getInt("card_opacity", card.opacityPercent).coerceIn(75, 100),
            saved.getInt("card_duration", card.maxSeconds).coerceIn(0, 120),
            saved.getBoolean("card_contrast", card.highContrast),
        ),
    )
}

internal fun savePreferences(saved: SharedPreferences, preferences: Preferences) {
    val energy = preferences.energyCost
    val work = preferences.workPlan
    val card = preferences.card
    saved.edit()
        .putInt("cost", preferences.centsPerKm)
        .putString("minimum", preferences.minNetPerHour.toString())
        .putString("floor", preferences.rejectBelowNetPerHour.toString())
        .putString("rating", preferences.minRating?.toString().orEmpty())
        .putString("zone", preferences.avoidDestination)
        .putString("energy_source", energy?.source?.name)
        .putString("energy_price", energy?.pricePerUnit?.toString())
        .putString("energy_consumption", energy?.kmPerUnit?.toString())
        .putString("other_cost_per_km", energy?.otherCostPerKm?.toString())
        .putString("electric_range", energy?.electricRangeKm?.toString())
        .putString("electric_battery", energy?.electricBatteryKwh?.toString())
        .putString("gross_km_floor", preferences.grossPerKmBand?.floor?.toString())
        .putString("gross_km_target", preferences.grossPerKmBand?.target?.toString())
        .putString("minimum_fare", preferences.minimumFare?.toString())
        .putString("rating_mode", preferences.ratingMode.name)
        .putInt("work_days", work.daysPerWeek ?: 0)
        .putString("work_hours", work.hoursPerDay?.toString())
        .putString("work_km", work.kmPerDay?.toString())
        .putString("work_goal", work.weeklyGoal?.toString())
        .putString("work_monthly_costs", work.monthlyCosts.toString())
        .putString("work_annual_costs", work.annualCosts.toString())
        .putBoolean("use_work_costs", preferences.includeFixedCosts)
        .also { edit ->
            val metrics = card.visibleMetrics
            for ((index, key) in CARD_METRIC_KEYS.withIndex()) {
                edit.putString(key, (metrics.getOrNull(index) ?: CardMetric.NONE).name)
            }
        }
        .putString("card_alignment", card.alignment.name)
        .putString("card_font", card.font.name)
        .putInt("card_opacity", card.opacityPercent)
        .putInt("card_duration", card.maxSeconds)
        .putBoolean("card_contrast", card.highContrast)
        .apply()
}
