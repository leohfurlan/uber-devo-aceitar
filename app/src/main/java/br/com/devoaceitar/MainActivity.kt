package br.com.devoaceitar

import android.app.Activity
import android.os.Bundle
import java.text.NumberFormat
import java.util.Locale

class MainActivity : Activity() {
    private val currency = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
    private val saved by lazy { getSharedPreferences("driver_preferences", MODE_PRIVATE) }
    private val history by lazy { AnalysisHistory.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = SettingsForm(this, inset = 20)
        page.body.setBackgroundColor(SettingsColors.canvas)
        page.section("Devo aceitar?").textSize = 30f
        page.button("Preferências", primary = true) {
            PreferencesEditor(this, readPreferences(saved), currency) { result ->
                savePreferences(saved, result)
            }.show()
        }
        page.button("Histórico de análises") {
            AnalysisHistoryView(this, history, currency) { readPreferences(saved).analysisPolicy() }.show()
        }
        val root = settingsScroll(page.body).apply {
            setBackgroundColor(SettingsColors.canvas)
            isFillViewport = true
            setOnApplyWindowInsetsListener { view, insets ->
                @Suppress("DEPRECATION")
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                insets
            }
        }
        setContentView(root)
        history.prepare()
    }
}
