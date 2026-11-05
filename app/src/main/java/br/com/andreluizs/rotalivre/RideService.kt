package br.com.andreluizs.rotalivre

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.TextView

class RideService : AccessibilityService() {

    private var card: Card? = null
    private var scale = 1f
    private var lastSig = ""
    private var lastTime = 0L
    private val debuggable by lazy {
        applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
    }
    private val handler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideOverlay() }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg !in Targets.ALL) return
        val delivery = Targets.isDelivery(pkg)
        if (delivery && !Prefs.isDeliveryOn(this)) return

        // A oferta pode vir numa janela própria (não a ativa): lê todas as janelas do app.
        val texts = mutableListOf<String>()
        val roots = windows.mapNotNull { w -> w.root?.takeIf { it.packageName == pkg } }
            .ifEmpty { listOfNotNull(rootInActiveWindow, event.source) }
        roots.forEach { collect(it, texts) }

        val offer = OfferParser.parse(texts)
        if (debuggable) logTexts(pkg, texts, offer != null)
        if (offer == null) return

        // Mesma oferta ainda na tela: só atualiza o quadro, não conta de novo.
        val now = System.currentTimeMillis()
        val sameOffer = offer.signature == lastSig && now - lastTime < 30_000
        lastSig = offer.signature
        lastTime = now

        if (!sameOffer) Stats.add(this, offer.perKm)
        show(offer, delivery)
    }

    companion object {
        @Volatile var instance: RideService? = null
    }

    private var lastLogSig = ""
    private fun logTexts(pkg: String, texts: List<String>, parsed: Boolean) {
        val sig = "$pkg|${texts.hashCode()}"
        if (sig == lastLogSig) return
        lastLogSig = sig
        Log.d("RotaLivre", "pkg=$pkg parsed=$parsed texts=$texts")
        if (texts.isEmpty()) {
            val w = windows.joinToString { "[type=${it.type} pkg=${it.root?.packageName} filhos=${it.root?.childCount}]" }
            Log.d("RotaLivre", "vazio: janelas=$w")
        }
    }

    private fun collect(node: AccessibilityNodeInfo?, out: MutableList<String>) {
        if (node == null) return
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let(out::add)
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(out::add)
        for (i in 0 until node.childCount) collect(node.getChild(i), out)
    }

    // ---------- Quadro da corrida ----------

    private enum class Verdict(val label: String, val accent: Int, val bg: Int) {
        GOOD("BOA CORRIDA", Color.rgb(79, 209, 139), Color.argb(250, 10, 40, 27)),
        LOW("ABAIXO DO MÍNIMO", Color.rgb(255, 122, 122), Color.argb(250, 52, 16, 16)),
        FAR("PASSAGEIRO LONGE", Color.rgb(255, 184, 77), Color.argb(250, 52, 36, 8))
    }

    private fun show(o: Offer, delivery: Boolean) {
        val picoAtivo = !delivery && Prefs.isPicoOn(this) && o.surge
        val limite = when {
            delivery -> Prefs.getDeliveryMin(this)
            picoAtivo -> Prefs.getPicoMin(this)
            else -> Prefs.currentMin(this)
        }
        val okKm = o.perKm >= limite
        val okPickup = o.pickupKm <= if (delivery) Prefs.getDeliveryMaxPickup(this) else Prefs.getMaxPickup(this)
        val verdict = when {
            !okKm -> Verdict.LOW
            !okPickup -> Verdict.FAR
            else -> Verdict.GOOD
        }

        val costKm = Prefs.costPerKm(this)
        val custo = o.totalKm * costKm

        val details = mutableListOf<String>()
        if (Prefs.showPickup(this))
            details += "%s a %.1f km%s".format(
                if (delivery) "Coleta" else "Passageiro", o.pickupKm, if (!okPickup) "  ⚠" else ""
            )
        if (Prefs.showPerKm(this)) details += "Percurso total %.1f km".format(o.totalKm)
        if (Prefs.showPerHour(this)) o.perHour?.let { details += "≈ R$ %.0f por hora".format(it) }
        if (Prefs.showProfit(this) && costKm > 0.0)
            details += "Lucro R$ %.2f  ·  custo R$ %.2f".format(o.price - custo, custo)

        // Tamanho escolhido no app: se mudou, recria o quadro
        val wanted = floatArrayOf(1f, 1.2f, 1.4f)[Prefs.getOverlaySize(this).coerceIn(0, 2)]
        if (card != null && wanted != scale) hideOverlay()
        scale = wanted
        val c = card ?: buildCard().also { card = it }
        c.badge.text = when {
            delivery && verdict == Verdict.FAR -> "COLETA LONGE"
            delivery && verdict == Verdict.GOOD -> "BOA ENTREGA"
            else -> verdict.label
        }
        c.badge.setTextColor(verdict.accent)
        c.badge.background = pill(Color.argb(46, Color.red(verdict.accent), Color.green(verdict.accent), Color.blue(verdict.accent)))
        c.pico.visibility = if (picoAtivo) View.VISIBLE else View.GONE

        c.main.setTextColor(verdict.accent)
        c.main.text = if (Prefs.showPerKm(this)) "R$ %.2f/km".format(o.perKm) else "R$ %.2f".format(o.price)
        c.sub.text = if (Prefs.showPerKm(this)) "mínimo R$ %.2f/km  ·  corrida R$ %.2f".format(limite, o.price) else ""
        c.sub.visibility = if (c.sub.text.isEmpty()) View.GONE else View.VISIBLE

        c.details.text = details.joinToString("\n")
        c.details.visibility = if (details.isEmpty()) View.GONE else View.VISIBLE
        c.summary.text = Stats.summary(this)
        c.summary.visibility = if (Prefs.showSummary(this)) View.VISIBLE else View.GONE

        (c.root.background as GradientDrawable).apply {
            setColor(verdict.bg)
            setStroke(dp(2), verdict.accent)
        }

        if (c.root.parent == null) {
            val maxW = resources.displayMetrics.widthPixels - dp(16)
            val lp = WindowManager.LayoutParams(
                minOf(dp((320 * scale).toInt()), maxW),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = dp(48) }
            (getSystemService(WINDOW_SERVICE) as WindowManager).addView(c.root, lp)
        }

        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, 12000)
    }

    private class Card(
        val root: LinearLayout, val badge: TextView, val pico: TextView,
        val main: TextView, val sub: TextView, val details: TextView, val summary: TextView
    )

    private fun buildCard(): Card {
        fun tv(sizeSp: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

        val badge = tv(14f * scale, Color.WHITE, true).apply {
            letterSpacing = 0.06f
            setPadding(dp(12), dp(5), dp(12), dp(5))
        }
        val pico = tv(14f * scale, Color.rgb(255, 200, 90), true).apply {
            text = "🔥 PICO"
            setPadding(dp(12), dp(5), dp(12), dp(5))
            background = pill(Color.argb(60, 255, 184, 77))
            visibility = View.GONE
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(badge)
            addView(pico, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
        }
        val main = tv(46f * scale, Color.WHITE, true).apply { setPadding(0, dp(8), 0, 0) }
        val sub = tv(16f * scale, Color.argb(235, 255, 255, 255))
        val details = tv(20f * scale, Color.WHITE, true).apply { setLineSpacing(dp(4).toFloat(), 1f); setPadding(0, dp(12), 0, 0) }
        val summary = tv(13f * scale, Color.argb(200, 255, 255, 255)).apply { setPadding(0, dp(12), 0, 0) }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = GradientDrawable().apply { cornerRadius = dp(20).toFloat() }
            addView(top)
            addView(main)
            addView(sub)
            addView(details)
            addView(summary)
        }
        return Card(root, badge, pico, main, sub, details, summary)
    }

    private fun pill(color: Int) = GradientDrawable().apply {
        cornerRadius = dp(20).toFloat()
        setColor(color)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun hideOverlay() {
        card?.root?.let {
            try { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) } catch (_: Exception) {}
        }
        card = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    /** Mostra uma oferta de exemplo, para o usuário conferir que o quadro aparece. */
    fun demo(delivery: Boolean = false) {
        show(Offer(price = 18.0, pickupKm = 1.2, tripKm = 7.3, totalMin = 24, surge = false), delivery)
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        handler.removeCallbacks(hideRunnable)
        hideOverlay()
        super.onDestroy()
    }
}
