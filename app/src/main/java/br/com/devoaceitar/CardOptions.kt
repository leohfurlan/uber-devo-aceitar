package br.com.devoaceitar

import java.text.NumberFormat
import java.util.Locale

internal const val MAX_CARD_METRICS = 4

enum class CardMetric(val label: String, val unit: String) {
    NONE("Nenhum", ""),
    GROSS_KM("Bruto por km", "/km bruto"),
    NET_HOUR("Líquido estimado por hora", "/h líq. est."),
    GROSS_HOUR("Bruto por hora", "/h bruto"),
    NET_TOTAL("Lucro estimado", "Líquido est."),
    RATING("Nota informada", "Nota"),
    GROSS_MINUTE("Bruto por minuto", "/min bruto"),
    NET_MARGIN("Margem de lucro", "Margem est."),
    ;

    fun value(result: Assessment): Double? = when (this) {
        NONE -> null
        GROSS_KM -> result.grossPerKm
        NET_HOUR -> result.netPerHour
        GROSS_HOUR -> result.grossPerHour
        NET_TOTAL -> result.netAmount
        RATING -> result.rating
        GROSS_MINUTE -> result.grossPerHour?.div(60)
        NET_MARGIN -> result.netMarginPercent
    }

    fun format(value: Double, currency: NumberFormat): String = when (this) {
        RATING -> String.format(Locale.forLanguageTag("pt-BR"), "%.2f", value)
        NET_MARGIN -> String.format(Locale.forLanguageTag("pt-BR"), "%.1f%%", value)
        else -> currency.format(value)
    }
}

enum class CardAlignment(val label: String) {
    FULL("Largura da oferta"), LEFT("Esquerda"), CENTER("Centro"), RIGHT("Direita");

    fun width(available: Int, minimum: Int): Int =
        if (this == FULL) available else maxOf(available * 84 / 100, minimum).coerceAtMost(available)

    fun offset(available: Int, width: Int): Int = when (this) {
        FULL, LEFT -> 0
        CENTER -> (available - width) / 2
        RIGHT -> available - width
    }
}

enum class CardFont(val label: String, val delta: Int) {
    SMALL("Compacta", -2), STANDARD("Padrão", 0), LARGE("Grande", 3),
}

data class CardOptions(
    val metrics: List<CardMetric> = listOf(CardMetric.GROSS_KM, CardMetric.NET_HOUR),
    val alignment: CardAlignment = CardAlignment.FULL,
    val font: CardFont = CardFont.STANDARD,
    val opacityPercent: Int = 100,
    val maxSeconds: Int = 0,
    val highContrast: Boolean = false,
) {
    val visibleMetrics: List<CardMetric>
        get() = metrics.filterNot { it == CardMetric.NONE }.distinct().take(MAX_CARD_METRICS)
    val maxMillis get() = if (maxSeconds > 0) maxSeconds * 1000L else null
}
