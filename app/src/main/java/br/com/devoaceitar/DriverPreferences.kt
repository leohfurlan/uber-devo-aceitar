package br.com.devoaceitar

import android.content.SharedPreferences

internal fun readPreferences(saved: SharedPreferences): Preferences {
    val defaults = Preferences()
    val source = EnergySource.entries.firstOrNull { it.name == saved.getString("energy_source", null) }
    val price = saved.getString("energy_price", null)?.toDoubleOrNull()
    val consumption = saved.getString("energy_consumption", null)?.toDoubleOrNull()
    val other = saved.getString("other_cost_per_km", null)?.toDoubleOrNull() ?: 0.0
    val energyCost = if (source != null && price != null && consumption != null) {
        EnergyCost(source, price, consumption, other).takeIf(EnergyCost::isValid)
    } else null
    return Preferences(
        centsPerKm = saved.getInt("cost", defaults.centsPerKm),
        minNetPerHour = saved.getInt("minimum", defaults.minNetPerHour),
        rejectBelowNetPerHour = saved.getInt("floor", defaults.rejectBelowNetPerHour),
        minRating = saved.getFloat("rating", defaults.minRating.toFloat()).toString().toDouble(),
        avoidDestination = saved.getString("zone", defaults.avoidDestination).orEmpty(),
        energyCost = energyCost,
    )
}

internal fun savePreferences(saved: SharedPreferences, preferences: Preferences) {
    val energy = preferences.energyCost
    saved.edit()
        .putInt("cost", preferences.centsPerKm)
        .putInt("minimum", preferences.minNetPerHour)
        .putInt("floor", preferences.rejectBelowNetPerHour)
        .putFloat("rating", preferences.minRating.toFloat())
        .putString("zone", preferences.avoidDestination)
        .putString("energy_source", energy?.source?.name)
        .putString("energy_price", energy?.pricePerUnit?.toString())
        .putString("energy_consumption", energy?.kmPerUnit?.toString())
        .putString("other_cost_per_km", energy?.otherCostPerKm?.toString())
        .apply()
}
