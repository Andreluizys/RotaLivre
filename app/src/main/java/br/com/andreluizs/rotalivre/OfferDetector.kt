package br.com.andreluizs.rotalivre

/** O que a tela atual de um app é. */
enum class ScreenKind { OFFER, IN_RIDE, OTHER }

class Screen(val kind: ScreenKind, val offer: Offer?, val reason: String)

/**
 * ehTelaDeOferta: só devolve OFFER quando há uma oferta real esperando resposta:
 *  1) nenhum marcador de corrida em andamento;
 *  2) existe o botão de aceitar;
 *  3) os dados completos estão juntos: preço (R$) + distância até o passageiro/coleta + distância da viagem.
 */
object OfferDetector {

    fun classify(pkg: String, texts: List<String>): Screen {
        val words = OfferKeywords.forPackage(pkg)

        texts.firstOrNull { OfferKeywords.isInRide(it, words) }
            ?.let { return Screen(ScreenKind.IN_RIDE, null, "corrida em andamento (\"$it\")") }

        if (texts.none { OfferKeywords.isAccept(it, words) })
            return Screen(ScreenKind.OTHER, null, "ignorada: sem botão de aceitar")

        val offer = OfferParser.parse(texts)
            ?: return Screen(ScreenKind.OTHER, null, "ignorada: sem preço ou distância")
        if (offer.distCount < words.minDistances)
            return Screen(ScreenKind.OTHER, null, "ignorada: dados incompletos (${offer.distCount} distância)")

        return Screen(ScreenKind.OFFER, offer, "oferta válida R$ %.2f".format(offer.price))
    }
}
