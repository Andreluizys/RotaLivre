package br.com.andreluizs.rotalivre

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView

class RideService : AccessibilityService() {

    private val targets = setOf("com.ubercab.driver", "com.app99.driver")
    private var overlay: TextView? = null
    private var lastSig = ""
    private var lastTime = 0L
    private val debuggable by lazy {
        applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
    }
    private val handler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideOverlay() }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg !in targets) return
        val root = rootInActiveWindow ?: return

        val texts = mutableListOf<String>()
        collect(root, texts)
        val offer = OfferParser.parse(texts) ?: return

        if (debuggable) Log.d("RotaLivre", "pkg=$pkg texts=$texts")

        // Mesma oferta ainda na tela: só atualiza o quadro, não conta de novo.
        val now = System.currentTimeMillis()
        val sameOffer = offer.signature == lastSig && now - lastTime < 30_000
        lastSig = offer.signature
        lastTime = now

        if (!sameOffer) Stats.add(this, offer.perKm)
        show(offer)
    }

    private fun collect(node: AccessibilityNodeInfo?, out: MutableList<String>) {
        if (node == null) return
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let(out::add)
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(out::add)
        for (i in 0 until node.childCount) collect(node.getChild(i), out)
    }

    private fun show(o: Offer) {
        val picoAtivo = Prefs.isPicoOn(this) && o.surge
        val limite = if (picoAtivo) Prefs.getPicoMin(this) else Prefs.currentMin(this)
        val maxPickup = Prefs.getMaxPickup(this)

        // Verde/vermelho continua pelo valor por km (+ distância máx. até o passageiro)
        val okKm = o.perKm >= limite
        val okPickup = o.pickupKm <= maxPickup
        val boa = okKm && okPickup

        val costKm = Prefs.costPerKm(this)
        val custo = o.totalKm * costKm
        val lucro = o.price - custo

        val linhas = mutableListOf<String>()
        if (Prefs.showPickup(this))
            linhas += "Até passageiro: %.1f km%s".format(o.pickupKm, if (!okPickup) " ⚠ LONGE" else "")
        if (Prefs.showPerKm(this)) {
            linhas += "Total (busca + viagem): %.1f km".format(o.totalKm)
            linhas += "R$ %.2f/km  (mín %.2f)".format(o.perKm, limite)
        }
        if (Prefs.showPerHour(this))
            o.perHour?.let { linhas += "≈ R$ %.0f/hora".format(it) }
        if (Prefs.showProfit(this) && costKm > 0.0)
            linhas += "Lucro: R$ %.2f  (custo R$ %.2f)".format(lucro, custo)
        if (picoAtivo) linhas += "🔥 PICO"
        if (Prefs.showSummary(this)) linhas += Stats.summary(this)
        if (linhas.isEmpty()) linhas += "R$ %.2f".format(o.price)

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val view = overlay ?: TextView(this).also {
            it.textSize = 18f
            it.setTextColor(Color.WHITE)
            it.setPadding(32, 24, 32, 24)
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = 120 }
            wm.addView(it, lp)
            overlay = it
        }
        view.text = linhas.joinToString("\n")
        view.setBackgroundColor(
            if (boa) Color.argb(220, 0, 140, 60) else Color.argb(220, 190, 30, 30)
        )

        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, 12000)
    }

    private fun hideOverlay() {
        overlay?.let {
            try { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) } catch (_: Exception) {}
        }
        overlay = null
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        hideOverlay()
        super.onDestroy()
    }
}
