package br.com.devoaceitar

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

internal class AnalysisHistoryView(
    private val context: Context,
    private val history: AnalysisHistory,
    private val currency: NumberFormat,
    private val currentPolicy: () -> AnalysisPolicy,
) {
    private val dates = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.forLanguageTag("pt-BR"))
    private val numbers = NumberFormat.getNumberInstance(Locale.forLanguageTag("pt-BR")).apply {
        maximumFractionDigits = 2
    }

    fun show() {
        val list = SettingsForm(context)
        val header = SettingsForm(context)
        header.section("Histórico de análises").textSize = 24f
        val status = list.label("Carregando…")
        val more = list.control("Carregar mais") {}
        val footer = SettingsForm(context)
        val back = footer.button("Voltar") {}
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(SettingsColors.canvas)
            addView(header.body)
            addView(context.settingsScroll(list.body), LinearLayout.LayoutParams(-1, 0, 1f))
            addView(footer.body)
        }
        val dialog = context.settingsDialog(content)
        back.setOnClickListener { dialog.dismiss() }

        fun renderItem(entry: AnalysisEntry) {
            val item = list.button(
                "${entry.assessment.recommendation.label} · ${money(entry.offer.payCents?.div(100.0))}\n" +
                    "${entry.offer.kind} · ${entry.source.label} · ${dates.format(Date(entry.timeMs))}",
            ) { detail(entry) { reviewed ->
                if (dialog.isShowing) {
                    renderItem(reviewed)
                    val latest = list.body.getChildAt(list.body.childCount - 1)
                    list.body.removeView(latest)
                    list.body.addView(latest, 1)
                }
            } }
            item.gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
            item.setPadding(list.dp(14), list.dp(10), list.dp(14), list.dp(10))
        }

        var cursor: Long? = null
        fun load() {
            more.isEnabled = false
            list.body.removeView(more)
            history.page(cursor) { result ->
                if (!dialog.isShowing) return@page
                result.onSuccess { entries ->
                    status.text = "As análises do laboratório e as reavaliações ficam salvas neste aparelho."
                    if (entries.isEmpty() && cursor == null) {
                        status.text = "Nenhuma análise registrada. Gere uma oferta no Laboratório."
                    }
                    entries.forEach(::renderItem)
                    cursor = entries.lastOrNull()?.id ?: cursor
                    if (entries.size == AnalysisHistory.PAGE_SIZE) {
                        list.body.addView(more)
                        more.isEnabled = true
                    }
                }.onFailure {
                    status.text = "Não foi possível abrir o histórico."
                    list.body.addView(more)
                    more.isEnabled = true
                }
            }
        }
        more.setOnClickListener { load() }
        load()
    }

    private fun detail(entry: AnalysisEntry, onReview: (AnalysisEntry) -> Unit) {
        val form = SettingsForm(context)
        val details = form.group(entry.assessment.recommendation.label)
        details.label("${entry.offer.kind} · ${entry.source.label} · ${dates.format(Date(entry.timeMs))}")
        if (entry.originalId != null) details.label("Reavaliação da análise ${entry.originalId}")
        details.label(entry.assessment.reason)
        val offer = form.group("Oferta")
        val pickup = if (entry.offer.kind == "Entrega") "Até a coleta" else "Até o embarque"
        val trip = "Até o destino"
        offer.label("Valor da oferta · ${money(entry.offer.payCents?.div(100.0))}")
        offer.label("$pickup · ${distance(entry.offer.distance.pickup)} · ${time(entry.offer.duration.pickup)}")
        offer.label("$trip · ${distance(entry.offer.distance.trip)} · ${time(entry.offer.duration.trip)}")
        offer.label("Total do trajeto · ${distance(entry.offer.distance.resolved())} · ${time(entry.offer.duration.resolved())}")
        entry.offer.collectionWaitSeconds?.let { offer.label("Espera adicional · ${time(it)}") }
        entry.offer.destination?.let { offer.label("Destino · $it") }
        entry.assessment.rating?.let { offer.label("Nota · ${numbers.format(it)}") }
        val costs = form.group("Custos e ganhos")
        costs.label("Custo até coleta ou embarque · ${money(entry.assessment.pickupCost)}")
        costs.label("Custo total · ${money(entry.assessment.totalCost)}")
        costs.label("Lucro estimado · ${money(entry.assessment.netAmount)}")
        costs.label("Bruto por km · ${money(entry.assessment.grossPerKm)}")
        costs.label("Líquido por hora · ${money(entry.assessment.netPerHour)}")
        val rules = form.group("Parâmetros usados")
        rules.label("Custo por km · ${money(entry.policy.reaisPerKm)}")
        rules.label("Piso e meta líquidos por hora · ${money(entry.policy.rejectBelowNetPerHour)} / ${money(entry.policy.minNetPerHour)}")
        entry.policy.grossPerKmBand?.let { band ->
            rules.label("Piso e meta brutos por km · ${money(band.floor)} / ${money(band.target)}")
        }
        entry.policy.minimumFare?.let { rules.label("Valor mínimo · ${money(it)}") }
        entry.policy.minRating?.let { rules.label("Nota mínima · ${numbers.format(it)} · ${entry.policy.ratingMode.label}") }
        entry.policy.avoidDestination.takeIf(String::isNotBlank)?.let { rules.label("Bairro a evitar · $it") }

        val reviewResult = form.label("").apply { visibility = View.GONE }
        val review = form.button("Reavaliar com as preferências atuais", primary = true) {}
        val back = form.button("Voltar") {}
        val scroll = context.settingsScroll(form.body).apply { setBackgroundColor(SettingsColors.canvas) }
        val dialog = context.settingsDialog(scroll)
        back.setOnClickListener { dialog.dismiss() }
        review.setOnClickListener {
            review.isEnabled = false
            history.review(entry, currentPolicy()) { result ->
                result.onSuccess(onReview)
                if (!dialog.isShowing) return@review
                review.isEnabled = true
                reviewResult.text = result.fold({ updated ->
                    "Original · ${entry.assessment.recommendation.label}\n" +
                        "Atual · ${updated.assessment.recommendation.label} · ${money(updated.assessment.netPerHour)}/h líquido\n" +
                        "Reavaliação salva no histórico."
                }, { "Não foi possível salvar a reavaliação." })
                reviewResult.visibility = View.VISIBLE
            }
        }
    }

    private fun distance(metres: Int?) = metres?.let { "${numbers.format(it / 1000.0)} km" } ?: "—"
    private fun time(seconds: Int?) = seconds?.let { "${numbers.format(it / 60.0)} min" } ?: "—"
    private fun money(value: Double?) = value?.let(currency::format) ?: "—"
}
