package br.com.devoaceitar

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PreferencesAndAssessmentTest {
    private val offer = Offer("Entrega", 2000, TripMeasure(pickup = 4000, trip = 6000),
        TripMeasure(pickup = 600, trip = 1200), "Bairro Central", rating = 4.85)
    private val preferences = Preferences(
        centsPerKm = 80, minNetPerHour = 24.0, rejectBelowNetPerHour = 16.0,
        grossPerKmBand = IncomeBand(1.60, 2.0), minimumFare = 20.0, ratingMode = RatingMode.BLOCK,
    )

    @Test fun `soma os dois trechos e aceita os valores exatos da meta`() {
        val result = assess(offer, preferences)
        assertEquals(2.0, result.grossPerKm!!, 0.000001)
        assertEquals(24.0, result.netPerHour!!, 0.000001)
        assertEquals(3.2, result.pickupCost!!, 0.000001)
        assertEquals(8.0, result.totalCost!!, 0.000001)
        assertEquals(Recommendation.ACCEPT, result.recommendation)
        val withTotals = offer.copy(distance = offer.distance.copy(total = 10000),
            duration = offer.duration.copy(total = 1800))
        assertEquals(result, assess(withTotals, preferences))
        assertEquals(Recommendation.REVIEW,
            assess(offer.copy(distance = offer.distance.copy(total = 9000)), preferences).recommendation)
        val zeroDistance = assess(offer.copy(distance = TripMeasure(total = 0)), preferences)
        assertNull(zeroDistance.netPerHour)
        assertEquals(Recommendation.REVIEW, zeroDistance.recommendation)
    }

    @Test fun `classifica pisos e metas sem arredondar a oferta exibida`() {
        val noFareMinimum = preferences.copy(minimumFare = null, rejectBelowNetPerHour = 0.0)
        assertEquals(Recommendation.REVIEW, assess(offer.copy(payCents = 1600), noFareMinimum).recommendation)
        assertEquals(Recommendation.DECLINE, assess(offer.copy(payCents = 1599), noFareMinimum).recommendation)
        val hourlyPiso = preferences.copy(grossPerKmBand = null, minimumFare = null)
        assertEquals(Recommendation.REVIEW, assess(offer.copy(payCents = 1600), hourlyPiso).recommendation)
        assertEquals(Recommendation.DECLINE, assess(offer.copy(payCents = 1599), hourlyPiso).recommendation)
        assertEquals(Recommendation.DECLINE, assess(offer.copy(payCents = 1999), preferences).recommendation)
    }

    @Test fun `preserva vetos de região e diferencia nota ausente de nota insuficiente`() {
        assertEquals("Bairro Horizonte", assess(offer.copy(destination = "Bairro Horizonte"), preferences)
            .avoidedDestination)
        assertEquals(Recommendation.DECLINE, assess(offer.copy(rating = 4.0), preferences).recommendation)
        assertEquals(Recommendation.REVIEW, assess(offer.copy(rating = null), preferences).recommendation)
        assertNull(assess(offer.copy(rating = null), preferences).rating)
        assertEquals(Recommendation.ACCEPT, assess(offer.copy(rating = null),
            preferences.copy(ratingMode = RatingMode.WHEN_AVAILABLE)).recommendation)
    }

    @Test fun `calcula por minuto e margem sem inventar dado indisponível`() {
        val result = assess(offer, preferences)
        assertEquals(20.0 / 30, CardMetric.GROSS_MINUTE.value(result)!!, 0.000001)
        assertEquals(60.0, CardMetric.NET_MARGIN.value(result)!!, 0.000001)
        val noFare = assess(offer.copy(payCents = null), preferences)
        assertNull(CardMetric.GROSS_MINUTE.value(noFare))
        assertNull(CardMetric.NET_MARGIN.value(noFare))
        assertNull(CardMetric.NET_MARGIN.value(assess(offer.copy(payCents = 0), preferences)))
    }

    @Test fun `migra os valores legados e persiste os opcionais sem reativar nota desabilitada`() {
        val storage = inMemoryPrefs(mutableMapOf("minimum" to 40, "floor" to 32, "rating" to 4.85f, "cost" to 73,
            "card_first" to CardMetric.NET_TOTAL.name, "card_second" to CardMetric.RATING.name))
        val legacy = readPreferences(storage)
        assertEquals(40.0, legacy.minNetPerHour, 0.0)
        assertEquals(32.0, legacy.rejectBelowNetPerHour, 0.0)
        assertEquals(4.85, legacy.minRating!!, 0.0)
        assertNull(legacy.minimumFare)
        assertNull(legacy.grossPerKmBand)
        assertFalse(legacy.includeFixedCosts)
        assertEquals(listOf(CardMetric.NET_TOTAL, CardMetric.RATING), legacy.card.visibleMetrics)
        val metrics = listOf(CardMetric.GROSS_KM, CardMetric.GROSS_MINUTE, CardMetric.RATING, CardMetric.NET_MARGIN)
        val next = legacy.copy(minNetPerHour = 42.25, minRating = null, minimumFare = 7.0,
            grossPerKmBand = IncomeBand(1.3, 1.6), card = CardOptions(metrics = metrics, maxSeconds = 8),
            workPlan = WorkPlan(5, 6.0, 150.0, 1200.0, 500.0, 1000.0), includeFixedCosts = true)
        savePreferences(storage, next)
        assertEquals(next, readPreferences(storage))
        savePreferences(storage, next.copy(card = next.card.copy(metrics = emptyList())))
        assertEquals(emptyList<CardMetric>(), readPreferences(storage).card.visibleMetrics)
    }

    private fun inMemoryPrefs(values: MutableMap<String, Any?>): SharedPreferences {
        val editorType = SharedPreferences.Editor::class.java
        val editor = Proxy.newProxyInstance(editorType.classLoader, arrayOf(editorType)) { proxy, method, args ->
            when (method.name) {
                "apply" -> null
                "putString", "putInt", "putBoolean" -> {
                    val key = args[0] as String
                    if (args[1] == null) values.remove(key) else values[key] = args[1]
                    proxy
                }
                else -> error(method.name)
            }
        }
        val prefsType = SharedPreferences::class.java
        return Proxy.newProxyInstance(prefsType.classLoader, arrayOf(prefsType)) { _, method, args ->
            when (method.name) {
                "getAll" -> values.toMap()
                "edit" -> editor
                "getString", "getInt", "getBoolean" -> values[args[0]] ?: args[1]
                else -> error(method.name)
            }
        } as SharedPreferences
    }
}
