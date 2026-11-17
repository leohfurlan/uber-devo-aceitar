package br.com.devoaceitar

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.Toast
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

private const val LAB_PACKAGE = "br.com.devoaceitar.lab"

private data class LabSnapshot(val identity: String, val bounds: Rect, val surface: Rect, val offer: Offer)
private data class PresentedOffer(val snapshot: LabSnapshot, val preferences: Preferences)
private data class PendingAnalysis(val key: String, val offer: Offer, val policy: AnalysisPolicy)

class LabOfferService : AccessibilityService() {
    private val windowManager by lazy { getSystemService(WindowManager::class.java) }
    private val saved by lazy { getSharedPreferences("driver_preferences", MODE_PRIVATE) }
    private val history by lazy { AnalysisHistory.get(this) }
    private val money = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
    private val clock = Handler(Looper.getMainLooper())
    private val expireOverlay = Runnable { removeOverlay() }
    private var overlay: LabOverlay? = null
    private var presented: PresentedOffer? = null
    private var offerIdentity: String? = null
    private var observedAtMs = 0L
    private var pending: PendingAnalysis? = null
    private var storedAssessment: Assessment? = null
    private var storageErrorShown = false
    private var destroyed = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        history.prepare()
        updateFromWindow()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val isContentEvent = event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        if (isContentEvent && event.packageName?.toString() != LAB_PACKAGE) return
        updateFromWindow()
    }

    private fun updateFromWindow() {
        val root = rootInActiveWindow
        val snapshot = if (root?.packageName?.toString() == LAB_PACKAGE) readSnapshot(root) else null
        if (snapshot == null) {
            removeOverlay()
            return
        }
        val next = PresentedOffer(snapshot, readPreferences(saved))
        val now = SystemClock.elapsedRealtime()
        if (snapshot.identity != offerIdentity) {
            offerIdentity = snapshot.identity
            observedAtMs = now
        }
        val analysis = PendingAnalysis(snapshot.identity, snapshot.offer, next.preferences.analysisPolicy())
        if (analysis != pending) {
            removeOverlay()
            pending = analysis
            storedAssessment = null
            history.evaluateLab(analysis.key, analysis.offer, analysis.policy) { result ->
                if (destroyed || pending !== analysis) return@evaluateLab
                result.onSuccess { entry ->
                    storageErrorShown = false
                    storedAssessment = entry.assessment
                    updateFromWindow()
                }.onFailure { error ->
                    Log.e("LabOfferService", "Falha ao gravar uma análise do laboratório", error)
                    pending = null
                    if (!storageErrorShown) {
                        storageErrorShown = true
                        Toast.makeText(this, "O histórico não pôde ser gravado.", Toast.LENGTH_SHORT).show()
                    }
                    clock.postDelayed({ if (pending == null) updateFromWindow() }, 1_000)
                }
            }
            return
        }
        val assessment = storedAssessment ?: return
        if (next == presented) return
        if (snapshot.bounds.isEmpty || !snapshot.surface.contains(snapshot.bounds)) {
            removeOverlay()
            return
        }
        val options = next.preferences.card
        val deadline = options.maxMillis?.let { observedAtMs + it }
        if (deadline != null && now >= deadline) {
            removeOverlay()
            return
        }
        val existing = overlay
        val host = existing ?: LabOverlay(this)
        val cardWidth = options.alignment.width(snapshot.bounds.width(), dp(240))
        host.replace(next.snapshot, assessmentView(assessment, money, options, cardWidth),
            options.alignment, cardWidth)
        if (existing == null) {
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.TOP or Gravity.LEFT }
            try {
                windowManager.addView(host, params)
                overlay = host
            } catch (_: WindowManager.BadTokenException) {
                return
            }
        }
        presented = next
        clock.removeCallbacks(expireOverlay)
        deadline?.let { clock.postDelayed(expireOverlay, it - now) }
    }

    private fun readSnapshot(root: AccessibilityNodeInfo): LabSnapshot? {
        val surfaceNode = root.visibleNode("lab_surface") ?: return null
        val card = surfaceNode.visibleNode("lab_offer") ?: return null
        val identity = card.contentDescription?.toString() ?: return null
        val bounds = Rect().also(card::getBoundsInScreen)
        val surface = Rect().also(surfaceNode::getBoundsInScreen)
        val kind = card.field("offer_kind")?.takeIf { it == "Corrida" || it == "Entrega" } ?: return null
        val offer = Offer(
            kind = kind,
            payCents = card.field("offer_amount")?.scaledInt("R$ ", multiplier = 100),
            distance = TripMeasure(
                total = card.field("offer_distance")?.scaledInt(suffix = " km", multiplier = 1000),
                pickup = card.field("offer_pickup_distance")?.scaledInt(suffix = " km", multiplier = 1000),
                trip = card.field("offer_trip_distance")?.scaledInt(suffix = " km", multiplier = 1000),
            ),
            duration = TripMeasure(
                total = card.field("offer_duration")?.scaledInt(suffix = " min", multiplier = 60),
                pickup = card.field("offer_pickup_duration")?.scaledInt(suffix = " min", multiplier = 60),
                trip = card.field("offer_trip_duration")?.scaledInt(suffix = " min", multiplier = 60),
            ),
            destination = card.field("offer_destination"),
            rating = card.field("offer_rating")?.replace(',', '.')?.toDoubleOrNull(),
            collectionWaitSeconds = card.field("offer_wait")?.scaledInt(suffix = " min", multiplier = 60),
        )
        return LabSnapshot(identity, bounds, surface, offer)
    }

    private fun removeOverlay() {
        clock.removeCallbacks(expireOverlay)
        overlay?.let { windowManager.removeViewImmediate(it) }
        overlay = null
        presented = null
    }

    override fun onInterrupt() = removeOverlay()

    override fun onDestroy() {
        destroyed = true
        removeOverlay()
        clock.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}

private class LabOverlay(context: Context) : FrameLayout(context) {
    private var snapshot: LabSnapshot? = null
    private var alignment = CardAlignment.FULL

    fun replace(offer: LabSnapshot, card: View, position: CardAlignment, cardWidth: Int) {
        snapshot = offer
        alignment = position
        removeAllViews()
        card.visibility = View.INVISIBLE
        addView(card, LayoutParams(cardWidth, LayoutParams.WRAP_CONTENT))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val offer = snapshot ?: return
        val card = getChildAt(0) ?: return
        val origin = IntArray(2).also(::getLocationOnScreen)
        val x = offer.bounds.left - origin[0] + alignment.offset(offer.bounds.width(), card.measuredWidth)
        val y = offer.bounds.top - origin[1] - context.dp(6) - card.measuredHeight
        val onScreen = Rect(
            x + origin[0], y + origin[1],
            x + origin[0] + card.measuredWidth, y + origin[1] + card.measuredHeight,
        )
        val fits = card.measuredHeight > 0 && x >= 0 && y >= 0 &&
            x + card.measuredWidth <= width && y + card.measuredHeight <= height &&
            offer.surface.contains(onScreen) && onScreen.bottom < offer.bounds.top
        if (fits) {
            card.layout(x, y, x + card.measuredWidth, y + card.measuredHeight)
        }
        card.visibility = if (fits) View.VISIBLE else View.INVISIBLE
    }
}

private fun AccessibilityNodeInfo.visibleNode(id: String): AccessibilityNodeInfo? =
    findAccessibilityNodeInfosByViewId("$LAB_PACKAGE:id/$id")
        .filter { it.isVisibleToUser }
        .singleOrNull()

private fun AccessibilityNodeInfo.field(id: String): String? =
    visibleNode(id)?.text?.toString()?.trim()?.ifEmpty { null }

private fun String.scaledInt(prefix: String = "", suffix: String = "", multiplier: Int): Int? {
    if (!startsWith(prefix) || !endsWith(suffix)) return null
    val raw = removePrefix(prefix).removeSuffix(suffix).trim()
    if (!raw.matches(Regex("[0-9]+([,.][0-9]+)?"))) return null
    return try {
        BigDecimal(raw.replace(',', '.')).multiply(BigDecimal.valueOf(multiplier.toLong())).intValueExact()
    } catch (_: ArithmeticException) {
        null
    }
}

private fun Context.dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
