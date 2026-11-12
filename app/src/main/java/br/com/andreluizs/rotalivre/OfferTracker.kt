package br.com.andreluizs.rotalivre

/** O que fazer com o quadro depois de ler uma tela. */
sealed class Action {
    object None : Action()
    object Hide : Action()
    /** [count]: é uma oferta nova (entra no resumo do dia). */
    class Show(val offer: Offer, val count: Boolean) : Action()
    /** A oferta foi aceita (virou corrida em andamento): esconde o quadro e soma os km. */
    class Accepted(val offer: Offer, val hide: Boolean) : Action()
}

/**
 * Máquina de estados por app: SEM_OFERTA → OFERTA_VISIVEL → EM_CORRIDA.
 * Usa a assinatura da oferta para não recalcular, redesenhar nem contar duas vezes a mesma oferta.
 */
class OfferTracker(
    private val sameOfferWindowMs: Long = 30_000L,
    /** Quanto tempo depois de ver a oferta a corrida em andamento ainda conta como "aceitou". */
    private val acceptWindowMs: Long = 15_000L
) {

    enum class State { SEM_OFERTA, OFERTA_VISIVEL, EM_CORRIDA }

    private class App {
        var state = State.SEM_OFERTA
        var lastSig = ""
        var lastSeen = 0L
        var expiredSig = ""   // oferta cujo quadro já passou dos 12 s: não mostra de novo
        var pending: Offer? = null   // última oferta vista que ainda não foi aceita nem contada
        var acceptedSig = ""  // oferta já aceita e contada: não pode ser contada de novo
        var acceptedAt = 0L
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
                // Aceitou: a oferta foi vista há pouco e agora há corrida em andamento.
                // Só conta uma vez (pending é zerado) e nunca por leituras durante a corrida.
                val accepted = a.pending?.takeIf { now - a.lastSeen < acceptWindowMs }
                a.pending = null
                if (accepted != null) {
                    markAccepted(a, pkg, accepted, now)
                    return Action.Accepted(accepted, hide = was == State.OFERTA_VISIVEL)
                }
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
                // A tela da oferta ainda aparece por instantes depois do toque em "Aceitar":
                // não é oferta nova e não pode voltar a ser contada.
                if (sig == a.acceptedSig && now - a.acceptedAt < sameOfferWindowMs) return Action.None
                val sameAsLast = sig == a.lastSig && now - a.lastSeen < sameOfferWindowMs
                val wasVisible = a.state == State.OFERTA_VISIVEL
                a.state = State.OFERTA_VISIVEL
                a.lastSeen = now
                a.pending = offer
                val count = !sameAsLast
                a.lastSig = sig
                if (sig == a.expiredSig && sameAsLast) return Action.None
                if (wasVisible && sameAsLast) return Action.None   // mesma oferta: nada a fazer
                a.expiredSig = ""
                return Action.Show(offer, count)
            }
        }
    }

    private fun markAccepted(a: App, pkg: String, offer: Offer, now: Long) {
        a.acceptedSig = pkg + "|" + offer.signature
        a.acceptedAt = now
    }

    /**
     * O motorista tocou em "Aceitar" (evento de clique da acessibilidade). É o sinal mais
     * confiável de aceite: não depende de reconhecer a tela da corrida em andamento.
     * Conta uma vez só (a leitura da tela da corrida depois não conta de novo).
     */
    fun onAcceptClick(pkg: String, now: Long): Action {
        val a = app(pkg)
        val offer = a.pending?.takeIf { now - a.lastSeen < acceptWindowMs } ?: return Action.None
        val wasVisible = a.state == State.OFERTA_VISIVEL
        a.pending = null
        a.state = State.EM_CORRIDA
        markAccepted(a, pkg, offer, now)
        return Action.Accepted(offer, hide = wasVisible)
    }

    /** O quadro passou dos 12 s: a mesma oferta não deve reaparecer. */
    fun onExpired(pkg: String) {
        val a = app(pkg)
        a.expiredSig = a.lastSig
    }
}
