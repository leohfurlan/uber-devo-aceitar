package br.com.devoaceitar

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

private const val LAB_PACKAGE = "br.com.devoaceitar.lab"

private data class LabSnapshot(val identity: String, val bounds: Rect, val surface: Rect, val offer: Offer)
private data class PresentedOffer(val snapshot: LabSnapshot, val preferences: Preferences)

class LabOfferService : AccessibilityService() {
    private val windowManager by lazy { getSystemService(WindowManager::class.java) }
    private val saved by lazy { getSharedPreferences("driver_preferences", MODE_PRIVATE) }
    private val money = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"))
    private var overlay: LabOverlay? = null
    private var presented: PresentedOffer? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
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
        if (next == presented) return
        val existing = overlay
        val host = existing ?: LabOverlay(this)
        host.replace(next.snapshot, analysisCard(assess(snapshot.offer, next.preferences)))
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
    }

    private fun readSnapshot(root: AccessibilityNodeInfo): LabSnapshot? {
        val surfaceNode = root.visibleNode("lab_surface") ?: return null
        val card = surfaceNode.visibleNode("lab_offer") ?: return null
        val identity = card.contentDescription?.toString() ?: return null
        val bounds = Rect().also(card::getBoundsInScreen)
        val surface = Rect().also(surfaceNode::getBoundsInScreen)
        if (bounds.isEmpty || !surface.contains(bounds)) return null
        val kind = card.field("offer_kind")?.takeIf { it == "Corrida" || it == "Entrega" } ?: return null
        val offer = Offer(
            kind = kind,
            payCents = card.field("offer_amount")?.scaledInt("R$ ", multiplier = 100),
            distance = TripMeasure(total = card.field("offer_distance")?.scaledInt(suffix = " km", multiplier = 1000)),
            duration = TripMeasure(total = card.field("offer_duration")?.scaledInt(suffix = " min", multiplier = 60)),
            destination = card.field("offer_destination"),
            rating = card.field("offer_rating")?.replace(',', '.')?.toDoubleOrNull(),
            collectionWaitSeconds = card.field("offer_wait")?.scaledInt(suffix = " min", multiplier = 60),
        )
        return LabSnapshot(identity, bounds, surface, offer)
    }

    private fun analysisCard(result: Assessment): View {
        val accent = when (result.recommendation) {
            Recommendation.ACCEPT -> Color.rgb(155, 230, 173)
            Recommendation.REVIEW -> Color.rgb(255, 215, 145)
            Recommendation.DECLINE -> Color.rgb(255, 174, 164)
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(Color.rgb(22, 68, 55))
            addView(line("Recomendação: ${result.recommendation.label}", 19, accent, true))
            result.avoidedDestination?.let { addView(avoidedDestinationView(it)) }
            val metrics = listOfNotNull(
                result.grossPerKm?.let { "${money.format(it)}/km bruto" },
                result.netPerHour?.let { "${money.format(it)}/h líquido est." },
            )
            if (metrics.isNotEmpty()) addView(line(metrics.joinToString(" · "), 14))
            if (result.avoidedDestination == null) addView(line(result.reason, 14))
        }
    }

    private fun line(value: String, sp: Int, color: Int = Color.WHITE, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = sp.toFloat()
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun removeOverlay() {
        overlay?.let { windowManager.removeViewImmediate(it) }
        overlay = null
        presented = null
    }

    override fun onInterrupt() = removeOverlay()

    override fun onDestroy() {
        removeOverlay()
        super.onDestroy()
    }
}

private class LabOverlay(context: Context) : FrameLayout(context) {
    private var snapshot: LabSnapshot? = null

    fun replace(offer: LabSnapshot, card: View) {
        snapshot = offer
        removeAllViews()
        card.visibility = View.INVISIBLE
        addView(card, LayoutParams(offer.bounds.width(), LayoutParams.WRAP_CONTENT))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val offer = snapshot ?: return
        val card = getChildAt(0) ?: return
        val origin = IntArray(2).also(::getLocationOnScreen)
        val x = offer.bounds.left - origin[0]
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
    if (!raw.matches(Regex("[0-9]+(,[0-9]+)?"))) return null
    return try {
        BigDecimal(raw.replace(',', '.')).multiply(BigDecimal.valueOf(multiplier.toLong())).intValueExact()
    } catch (_: ArithmeticException) {
        null
    }
}

private fun Context.dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
