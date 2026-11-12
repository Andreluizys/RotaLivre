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
    private var previewScale: Float? = null
    private var shownPkg = ""
    private val tracker = OfferTracker()
    private val debuggable by lazy {
        applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
    }
    private val handler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable {
        hideOverlay()
        if (shownPkg.isNotEmpty()) tracker.onExpired(shownPkg)
    }

    // Leitura da tela fora da thread principal (a varredura da árvore é lenta).
    // Espera curta depois do último evento (debounce) para ler a oferta já completa.
    private val worker = android.os.HandlerThread("rotalivre-leitura").apply { start() }
    private val workHandler = Handler(worker.looper)
    @Volatile private var pendingPkg = ""
    @Volatile private var pendingEventTime = 0L
    private var firstPendingAt = 0L
    private val readRunnable = Runnable {
        firstPendingAt = 0L
        process(pendingPkg, pendingEventTime)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg !in Targets.ALL) return
        // Toque em "Aceitar": soma os km da oferta na hora, sem esperar a tela da corrida
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            onClick(pkg, event)
            return
        }
        // Rolagem e notificações nunca mudam a oferta
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED,
            AccessibilityEvent.TYPE_ANNOUNCEMENT -> return
        }
        pendingPkg = pkg
        pendingEventTime = event.eventTime

        // Debounce: lê DEBOUNCE_MS depois do último evento, mas no máximo MAX_WAIT_MS depois do
        // primeiro (telas que atualizam o tempo todo não podem adiar a leitura para sempre).
        val now = android.os.SystemClock.uptimeMillis()
        if (firstPendingAt == 0L) firstPendingAt = now
        val wait = minOf(DEBOUNCE_MS, maxOf(0L, firstPendingAt + MAX_WAIT_MS - now))
        workHandler.removeCallbacks(readRunnable)
        workHandler.postDelayed(readRunnable, wait)
    }

    /** Corrida aceita: soma os km do dia e guarda no histórico. */
    private fun registrarAceite(pkg: String, offer: Offer) {
        Stats.addAccepted(this, offer.totalKm, offer.price)
        RideHistory.add(this, pkg, offer)
    }

    /** O toque foi no botão de aceitar? Olha o próprio elemento, os filhos e os pais próximos. */
    private fun onClick(pkg: String, event: AccessibilityEvent) {
        val words = OfferKeywords.forPackage(pkg)
        val texts = ArrayList<String>(8)
        var node: AccessibilityNodeInfo? = event.source
        var up = 0
        while (node != null && up < 3) {   // o botão pode estar no elemento, ou o toque no pai dele
            collect(node, texts, depth = 3)
            node = node.parent
            up++
        }
        event.text?.forEach { it?.toString()?.takeIf(String::isNotBlank)?.let(texts::add) }
        val accepted = texts.any { OfferKeywords.isAccept(it, words) }
        if (debuggable) Log.d("RotaLivre", "clique pkg=$pkg aceitar=$accepted texts=$texts")
        if (!accepted) return
        val a = tracker.onAcceptClick(pkg, System.currentTimeMillis())
        if (a is Action.Accepted) {
            registrarAceite(pkg, a.offer)
            handler.post { hideOverlay() }
        }
    }

    private fun process(pkg: String, eventTime: Long) {
        if (pkg.isEmpty()) return
        val delivery = Targets.isDelivery(pkg)
        if (delivery && !Prefs.isDeliveryOn(this)) return

        // Janela ativa do app; se não servir, procura as outras janelas dele
        var roots = listOfNotNull(rootInActiveWindow).filter { it.packageName == pkg }
        if (roots.isEmpty())
            roots = windows.mapNotNull { w -> w.root?.takeIf { it.packageName == pkg } }
        // Sem janela legível agora (transição): não decide nada; o timer de 8 s cuida do quadro.
        if (roots.isEmpty()) return

        val texts = ArrayList<String>(64)
        roots.forEach { collect(it, texts) }

        val screen = OfferDetector.classify(pkg, texts)
        if (debuggable) logTexts(pkg, texts, screen)

        val action = tracker.onScreen(pkg, screen, System.currentTimeMillis())
        when (action) {
            is Action.None -> {}
            is Action.Hide -> handler.post { hideOverlay() }
            is Action.Accepted -> {
                registrarAceite(pkg, action.offer)
                if (action.hide) handler.post { hideOverlay() }
            }
            is Action.Show -> {
                if (action.count) Stats.add(this, action.offer.perKm)
                handler.post {
                    shownPkg = pkg
                    show(action.offer, delivery, renew = true)
                    if (debuggable)
                        Log.d("RotaLivre", "latência evento→quadro: ${android.os.SystemClock.uptimeMillis() - eventTime} ms")
                }
            }
        }
    }

    companion object {
        @Volatile var instance: RideService? = null
        /** Tempo que o quadro fica na tela depois de aparecer. */
        const val OVERLAY_MS = 8_000L
        /** Espera depois do último evento antes de ler a tela. */
        const val DEBOUNCE_MS = 300L
        /** Espera máxima desde o primeiro evento (telas que não param de atualizar). */
        const val MAX_WAIT_MS = 1_000L
    }

    private var lastLogSig = ""
    private fun logTexts(pkg: String, texts: List<String>, screen: Screen) {
        val sig = "$pkg|${texts.hashCode()}"
        if (sig == lastLogSig) return
        lastLogSig = sig
        Log.d("RotaLivre", "pkg=$pkg tela=${screen.kind} (${screen.reason}) estado=${tracker.state(pkg)} texts=$texts")
        if (texts.isEmpty()) {
            val w = windows.joinToString { "[type=${it.type} pkg=${it.root?.packageName} filhos=${it.root?.childCount}]" }
            Log.d("RotaLivre", "vazio: janelas=$w")
        }
    }

    private fun collect(node: AccessibilityNodeInfo?, out: MutableList<String>, depth: Int = Int.MAX_VALUE) {
        if (node == null) return
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let(out::add)
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(out::add)
        if (depth <= 0) return
        for (i in 0 until node.childCount) collect(node.getChild(i), out, depth - 1)
    }

    // ---------- Quadro da corrida ----------

    private enum class Verdict(val label: String, val accent: Int, val bg: Int) {
        GOOD("BOA CORRIDA", Color.rgb(79, 209, 139), Color.argb(250, 10, 40, 27)),
        LOW("ABAIXO DO MÍNIMO", Color.rgb(255, 122, 122), Color.argb(250, 52, 16, 16)),
        FAR("PASSAGEIRO LONGE", Color.rgb(255, 184, 77), Color.argb(250, 52, 36, 8))
    }

    private fun show(o: Offer, delivery: Boolean, renew: Boolean = true) {
        val picoAtivo = !delivery && (Prefs.isPicoOn(this) || demoFull) && o.surge
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

        // Na demonstração mostra todas as linhas e usa um custo de exemplo (R$ 0,55/km).
        val costKm = Prefs.costPerKm(this).let { if (demoFull && it <= 0.0) 0.55 else it }
        val custo = o.totalKm * costKm

        val details = mutableListOf<String>()
        if (Prefs.showPickup(this) || demoFull)
            details += "%s a %.1f km%s".format(
                if (delivery) "Coleta" else "Passageiro", o.pickupKm, if (!okPickup) "  ⚠" else ""
            )
        if (Prefs.showPerKm(this)) details += "Percurso total %.1f km".format(o.totalKm)
        if (Prefs.showPerHour(this) || demoFull) o.perHour?.let { details += "≈ R$ %.0f por hora".format(it) }
        if ((Prefs.showProfit(this) || demoFull) && costKm > 0.0)
            details += "Lucro R$ %.2f  ·  custo R$ %.2f".format(o.price - custo, custo)

        // Tamanho escolhido no app: se mudou, recria o quadro
        val wanted = previewScale ?: Prefs.getOverlayScale(this)
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

        // O quadro some 8 s depois de aparecer. Só reinicia a contagem para uma oferta
        // nova; atualizações da mesma oferta não prolongam.
        if (renew) {
            handler.removeCallbacks(hideRunnable)
            handler.postDelayed(hideRunnable, OVERLAY_MS)
        }
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
        if (debuggable) registrarGatilhoDemo()
    }

    // Só na versão de testes (debug): permite iniciar a demonstração por adb, sem botão no app:
    //   adb shell am broadcast -a br.com.andreluizs.rotalivre.DEMO
    private var gatilhoDemo: android.content.BroadcastReceiver? = null

    private fun registrarGatilhoDemo() {
        val receptor = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, i: android.content.Intent?) = demoSequence()
        }
        val filtro = android.content.IntentFilter("br.com.andreluizs.rotalivre.DEMO")
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receptor, filtro, android.content.Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(receptor, filtro)
        }
        gatilhoDemo = receptor
    }

    private fun soltarGatilhoDemo() {
        gatilhoDemo?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
        gatilhoDemo = null
    }

    /** Mostra uma oferta de exemplo, para o usuário conferir que o quadro aparece. */
    fun demo(delivery: Boolean = false, scale: Float? = null) {
        previewScale = scale
        try {
            show(Offer(price = 18.0, pickupKm = 1.2, tripKm = 7.3, totalMin = 24, surge = false), delivery)
        } finally {
            previewScale = null
        }
    }

    // ---------- Demonstração (para gravar vídeo / mostrar o app) ----------

    private var demoFull = false
    private val demoHandler = Handler(Looper.getMainLooper())

    private class DemoCase(val offer: Offer, val delivery: Boolean = false)

    /** Mostra, uma após a outra, corridas de exemplo: boa, abaixo do mínimo, passageiro longe, pico e entrega. */
    fun demoSequence(gapMs: Long = 6_500L) {
        demoHandler.removeCallbacksAndMessages(null)
        val cases = listOf(
            DemoCase(Offer(22.40, 1.1, 8.2, 26, false)),             // boa
            DemoCase(Offer(9.80, 2.0, 3.0, 14, false)),              // abaixo do mínimo
            DemoCase(Offer(30.00, 5.5, 8.0, 38, false)),             // passageiro longe
            DemoCase(Offer(26.00, 1.0, 6.0, 20, true)),              // pico
            DemoCase(Offer(11.50, 1.4, 3.2, 17, false), delivery = true) // entrega
        )
        demoFull = true
        cases.forEachIndexed { i, c ->
            demoHandler.postDelayed({
                show(c.offer, c.delivery)
            }, i * gapMs)
        }
        demoHandler.postDelayed({ demoFull = false }, cases.size * gapMs + OVERLAY_MS)
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        soltarGatilhoDemo()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        workHandler.removeCallbacksAndMessages(null)
        worker.quitSafely()
        handler.removeCallbacks(hideRunnable)
        hideOverlay()
        super.onDestroy()
    }
}
