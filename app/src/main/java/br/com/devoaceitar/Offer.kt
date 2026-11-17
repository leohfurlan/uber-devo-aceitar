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
    val electricRangeKm: Double? = null,
    val electricBatteryKwh: Double? = null,
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
    val minNetPerHour: Double = 26.0,
    val rejectBelowNetPerHour: Double = 16.0,
    val minRating: Double? = 4.7,
    val avoidDestination: String = "Bairro Horizonte",
    val energyCost: EnergyCost? = null,
    val grossPerKmBand: IncomeBand? = null,
    val minimumFare: Double? = null,
    val ratingMode: RatingMode = RatingMode.WHEN_AVAILABLE,
    val workPlan: WorkPlan = WorkPlan(),
    val includeFixedCosts: Boolean = false,
    val card: CardOptions = CardOptions(),
) {
    val variableReaisPerKm: Double get() = energyCost?.reaisPerKm ?: centsPerKm / 100.0
    val reaisPerKm: Double
        get() = variableReaisPerKm + if (includeFixedCosts) workPlan.fixedReaisPerKm ?: 0.0 else 0.0
}

data class IncomeBand(val floor: Double, val target: Double) {
    fun isValid() = floor.isFinite() && target.isFinite() && floor >= 0 && target >= floor
}

enum class RatingMode(val label: String) {
    WHEN_AVAILABLE("Alertar quando houver nota"),
    REQUIRE("Exigir nota e alertar abaixo do mínimo"),
    BLOCK("Exigir nota e recusar abaixo do mínimo"),
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
    val rating: Double? = null,
    val netMarginPercent: Double? = null,
    val totalCost: Double? = null,
    val pickupCost: Double? = null,
)

internal data class AnalysisPolicy(
    val reaisPerKm: Double,
    val minNetPerHour: Double,
    val rejectBelowNetPerHour: Double,
    val minRating: Double?,
    val avoidDestination: String,
    val grossPerKmBand: IncomeBand?,
    val minimumFare: Double?,
    val ratingMode: RatingMode,
)

internal fun Preferences.analysisPolicy() = AnalysisPolicy(reaisPerKm, minNetPerHour, rejectBelowNetPerHour,
    minRating, avoidDestination, grossPerKmBand, minimumFare, ratingMode)

fun assess(offer: Offer, preferences: Preferences): Assessment = assess(offer, preferences.analysisPolicy())

internal fun assess(offer: Offer, preferences: AnalysisPolicy): Assessment {
    val pay = offer.payCents?.takeIf { it >= 0 }?.div(100.0)
    val km = offer.distance.resolved()?.takeIf { it > 0 }?.div(1000.0)
    val seconds = offer.duration.resolved()?.takeIf { it > 0 }?.toLong()
    val wait = offer.collectionWaitSeconds?.takeIf { it >= 0 } ?: 0
    val hours = seconds?.plus(wait)?.div(3600.0)
    val grossPerKm = km?.let { pay?.div(it) }
    val extraCost = offer.extraCostCents?.takeIf { it >= 0 }?.div(100.0) ?: 0.0
    val totalCost = km?.let { preferences.reaisPerKm * it + extraCost }
    val pickupCost = offer.distance.pickup?.takeIf { it >= 0 }?.let { preferences.reaisPerKm * it / 1000 }
    val net = totalCost?.let { cost -> pay?.minus(cost) }
    val netPerHour = hours?.let { net?.div(it) }
    val zone = preferences.avoidDestination.normalized()
    val destination = offer.destination?.normalized()?.ifBlank { null }
    val avoidedDestination = offer.destination?.trim()?.takeIf { zone.isNotEmpty() && destination == zone }
    val rating = offer.rating?.takeIf { it.isFinite() && it in 1.0..5.0 }
    val lowRating = preferences.minRating?.let { minimum -> rating?.below(minimum) } == true
    val kmBand = preferences.grossPerKmBand
    val belowKmFloor = kmBand != null && grossPerKm?.below(kmBand.floor) == true
    val belowKmTarget = kmBand != null && grossPerKm?.below(kmBand.target) == true

    val (recommendation, reason) = when {
        avoidedDestination != null ->
            Recommendation.DECLINE to "Destino em zona marcada para evitar."
        lowRating && preferences.ratingMode == RatingMode.BLOCK ->
            Recommendation.DECLINE to "Nota informada abaixo do mínimo obrigatório."
        preferences.minimumFare?.let { minimum -> pay?.below(minimum) } == true ->
            Recommendation.DECLINE to "Valor da oferta abaixo do mínimo."
        belowKmFloor ->
            Recommendation.DECLINE to "Valor bruto por km abaixo do piso."
        netPerHour?.below(preferences.rejectBelowNetPerHour) == true ->
            Recommendation.DECLINE to "Ganho líquido por hora abaixo do piso."
        zone.isNotEmpty() && destination == null ->
            Recommendation.REVIEW to "Região de destino não verificada."
        netPerHour == null ->
            Recommendation.REVIEW to "Oferta sem dados suficientes para o cálculo."
        rating == null && preferences.minRating != null && preferences.ratingMode != RatingMode.WHEN_AVAILABLE ->
            Recommendation.REVIEW to "Nota obrigatória indisponível."
        lowRating ->
            Recommendation.REVIEW to "Nota informada abaixo do seu mínimo."
        belowKmTarget ->
            Recommendation.REVIEW to "Valor bruto por km abaixo da meta."
        netPerHour.below(preferences.minNetPerHour) && wait > 0 ->
            Recommendation.REVIEW to "Com a espera na coleta, o ganho por hora fica abaixo da meta."
        netPerHour.below(preferences.minNetPerHour) ->
            Recommendation.REVIEW to "Ganho líquido por hora abaixo da meta."
        else ->
            Recommendation.ACCEPT to "Atinge sua meta de ganho por hora."
    }
    val grossPerHour = hours?.let { pay?.div(it) }
    val margin = pay?.takeIf { it > 0 }?.let { amount -> net?.div(amount)?.times(100) }
    return Assessment(recommendation, reason, grossPerKm, grossPerHour, netPerHour, net, avoidedDestination, rating,
        margin, totalCost, pickupCost)
}

// Preserve an inclusive boundary when division lands a few floating-point steps below it.
private fun Double.below(limit: Double) = this < limit - Math.ulp(limit) * 4

private fun String.normalized(): String =
    Normalizer.normalize(trim(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ")
