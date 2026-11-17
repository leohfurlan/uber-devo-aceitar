package br.com.devoaceitar

import java.text.Normalizer
import java.util.Locale

data class TripMeasure(val total: Int? = null, val pickup: Int? = null, val trip: Int? = null) {
    fun resolved(): Int? {
        val parts = if (pickup != null && trip != null && pickup >= 0 && trip >= 0) {
            (pickup.toLong() + trip).takeIf { it <= Int.MAX_VALUE }?.toInt()
        } else null
        if (total != null && parts != null && total != parts) return null
        return (total ?: parts)?.takeIf { it >= 0 }
    }
}

data class Offer(
    val kind: String,
    val payCents: Int?,
    val distance: TripMeasure,
    val duration: TripMeasure,
    val destination: String?,
    val rating: Double? = null,
    val collectionWaitSeconds: Int? = null,
    val extraCostCents: Int? = null,
)

enum class EnergySource(val label: String, val unit: String) {
    GASOLINE("Gasolina", "L"),
    ETHANOL("Etanol", "L"),
    DIESEL("Diesel", "L"),
    NATURAL_GAS("GNV", "m³"),
    ELECTRIC("Elétrico", "kWh"),
}

data class EnergyCost(
    val source: EnergySource,
    val pricePerUnit: Double,
    val kmPerUnit: Double,
    val otherCostPerKm: Double = 0.0,
) {
    val reaisPerKm: Double get() = pricePerUnit / kmPerUnit + otherCostPerKm

    fun isValid(): Boolean =
        pricePerUnit.isFinite() && pricePerUnit >= 0 &&
            kmPerUnit.isFinite() && kmPerUnit > 0 &&
            otherCostPerKm.isFinite() && otherCostPerKm >= 0 &&
            reaisPerKm.isFinite()
}

data class Preferences(
    val centsPerKm: Int = 90,
    val minNetPerHour: Int = 26,
    val rejectBelowNetPerHour: Int = 16,
    val minRating: Double = 4.7,
    val avoidDestination: String = "Bairro Horizonte",
    val energyCost: EnergyCost? = null,
) {
    val reaisPerKm: Double get() = energyCost?.reaisPerKm ?: centsPerKm / 100.0
}

enum class Recommendation(val label: String) {
    ACCEPT("Aceitar"),
    REVIEW("Avaliar"),
    DECLINE("Recusar"),
}

data class Assessment(
    val recommendation: Recommendation,
    val reason: String,
    val grossPerKm: Double?,
    val grossPerHour: Double?,
    val netPerHour: Double?,
    val netAmount: Double?,
    val avoidedDestination: String? = null,
)

fun assess(offer: Offer, preferences: Preferences): Assessment {
    val pay = offer.payCents?.takeIf { it >= 0 }?.div(100.0)
    val km = offer.distance.resolved()?.div(1000.0)
    val seconds = offer.duration.resolved()?.takeIf { it > 0 }?.toLong()
    val wait = offer.collectionWaitSeconds?.takeIf { it >= 0 } ?: 0
    val hours = seconds?.plus(wait)?.div(3600.0)
    val grossPerKm = km?.takeIf { it > 0 }?.let { safeKm -> pay?.div(safeKm) }
    val extraCost = offer.extraCostCents?.takeIf { it >= 0 }?.div(100.0) ?: 0.0
    val net = if (km != null && pay != null) {
        pay - preferences.reaisPerKm * km - extraCost
    } else null
    val netPerHour = hours?.let { net?.div(it) }
    val zone = preferences.avoidDestination.normalized()
    val destination = offer.destination?.normalized()?.ifBlank { null }
    val avoidedDestination = offer.destination?.trim()?.takeIf { zone.isNotEmpty() && destination == zone }
    val rating = offer.rating?.takeIf { it.isFinite() && it in 1.0..5.0 }

    val (recommendation, reason) = when {
        avoidedDestination != null ->
            Recommendation.DECLINE to "Destino em zona marcada para evitar."
        netPerHour != null && netPerHour < preferences.rejectBelowNetPerHour ->
            Recommendation.DECLINE to "Ganho líquido por hora abaixo do piso."
        zone.isNotEmpty() && destination == null ->
            Recommendation.REVIEW to "Região de destino não verificada."
        netPerHour == null || km == 0.0 ->
            Recommendation.REVIEW to "Oferta sem dados suficientes para o cálculo."
        rating != null && rating < preferences.minRating ->
            Recommendation.REVIEW to "Nota informada abaixo do seu mínimo."
        netPerHour < preferences.minNetPerHour && wait > 0 ->
            Recommendation.REVIEW to "Com a espera na coleta, o ganho por hora fica abaixo da meta."
        netPerHour < preferences.minNetPerHour ->
            Recommendation.REVIEW to "Ganho líquido por hora abaixo da meta."
        else ->
            Recommendation.ACCEPT to "Atinge sua meta de ganho por hora."
    }
    val grossPerHour = hours?.let { pay?.div(it) }
    return Assessment(recommendation, reason, grossPerKm, grossPerHour, netPerHour, net, avoidedDestination)
}

private fun String.normalized(): String =
    Normalizer.normalize(trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ")
