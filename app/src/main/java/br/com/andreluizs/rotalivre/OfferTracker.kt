package br.com.andreluizs.rotalivre

/** O que fazer com o quadro depois de ler uma tela. */
sealed class Action {
    object None : Action()
    object Hide : Action()
    /** [count]: é uma oferta nova (entra no resumo do dia). */
    class Show(val offer: Offer, val count: Boolean) : Action()
}

/**
 * Máquina de estados por app: SEM_OFERTA → OFERTA_VISIVEL → EM_CORRIDA.
 * Usa a assinatura da oferta para não recalcular, redesenhar nem contar duas vezes a mesma oferta.
 */
class OfferTracker(private val sameOfferWindowMs: Long = 30_000L) {

    enum class State { SEM_OFERTA, OFERTA_VISIVEL, EM_CORRIDA }

    private class App {
        var state = State.SEM_OFERTA
        var lastSig = ""
        var lastSeen = 0L
        var expiredSig = ""   // oferta cujo quadro já passou dos 8 s: não mostra de novo
    }

    private val apps = HashMap<String, App>()
    private fun app(pkg: String) = apps.getOrPut(pkg) { App() }

    fun state(pkg: String) = app(pkg).state

    fun onScreen(pkg: String, screen: Screen, now: Long): Action {
        val a = app(pkg)
        when (screen.kind) {
            ScreenKind.IN_RIDE -> {
                val was = a.state
                a.state = State.EM_CORRIDA
                return if (was == State.OFERTA_VISIVEL) Action.Hide else Action.None
            }
            ScreenKind.OTHER -> {
                val was = a.state
                // Saiu da corrida (home, ganhos...) ou a oferta sumiu (recusou/expirou).
                a.state = State.SEM_OFERTA
                return if (was == State.OFERTA_VISIVEL) Action.Hide else Action.None
            }
            ScreenKind.OFFER -> {
                val offer = screen.offer ?: return Action.None
                val sig = pkg + "|" + offer.signature
                val sameAsLast = sig == a.lastSig && now - a.lastSeen < sameOfferWindowMs
                val wasVisible = a.state == State.OFERTA_VISIVEL
                a.state = State.OFERTA_VISIVEL
                a.lastSeen = now
                val count = !sameAsLast
                a.lastSig = sig
                if (sig == a.expiredSig && sameAsLast) return Action.None
                if (wasVisible && sameAsLast) return Action.None   // mesma oferta: nada a fazer
                a.expiredSig = ""
                return Action.Show(offer, count)
            }
        }
    }

    /** O quadro passou dos 8 s: a mesma oferta não deve reaparecer. */
    fun onExpired(pkg: String) {
        val a = app(pkg)
        a.expiredSig = a.lastSig
    }
}
