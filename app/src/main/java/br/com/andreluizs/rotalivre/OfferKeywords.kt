package br.com.andreluizs.rotalivre

import java.text.Normalizer

/**
 * Palavras-chave que separam "oferta esperando resposta" de "corrida em andamento".
 *
 * É AQUI que se ajusta quando um app muda um texto. Escreva tudo em minúsculas e sem acento
 * (a comparação ignora maiúsculas e acentos).
 *
 *  - [accept]: texto do botão de aceitar a oferta. Só existe enquanto a oferta espera resposta.
 *  - [inRide]: textos que só existem com a corrida/entrega em andamento.
 *    Frases longas (8+ letras) valem se aparecerem dentro de um texto; palavras curtas
 *    ("cheguei", "rota") só valem se forem o texto inteiro do elemento.
 *  - [minDistances]: quantas distâncias a oferta completa traz (até o passageiro/coleta + viagem).
 */
class AppWords(
    val accept: Set<String>,
    val inRide: Set<String>,
    val minDistances: Int = 2
)

object OfferKeywords {

    private val COMMON_ACCEPT = setOf(
        "aceitar", "aceitar corrida", "aceitar pedido", "aceitar entrega", "aceitar viagem",
        "selecionar", "tenho interesse"
    )

    private val COMMON_IN_RIDE = setOf(
        "iniciar viagem", "finalizar viagem", "encerrar viagem", "cancelar viagem",
        "chegou ao destino", "passageiro a bordo", "cheguei", "cheguei ao local", "rota",
        "navegar", "confirmar entrega", "iniciar entrega", "finalizar entrega",
        "cheguei na coleta", "cheguei ao destino", "retirei o pedido", "confirmar retirada",
        "ir para o destino", "buscando passageiro", "a caminho do passageiro"
    )

    private val DEFAULT = AppWords(COMMON_ACCEPT, COMMON_IN_RIDE)

    private val BY_PACKAGE: Map<String, AppWords> = mapOf(
        // Uber Driver
        "com.ubercab.driver" to AppWords(
            accept = COMMON_ACCEPT,
            inRide = COMMON_IN_RIDE + setOf("deslize para iniciar", "deslize para finalizar", "pickup")
        ),
        // 99 Motorista
        "com.app99.driver" to AppWords(
            accept = COMMON_ACCEPT + setOf("toque para aceitar", "aceitar e ir"),
            inRide = COMMON_IN_RIDE + setOf("iniciar corrida", "finalizar corrida", "passageiro embarcou")
        ),
        // iFood Entregador
        "br.com.ifood.driver.app" to AppWords(
            accept = COMMON_ACCEPT + setOf("aceitar rota", "quero fazer"),
            inRide = COMMON_IN_RIDE + setOf("entregar pedido", "pedido coletado")
        ),
        // Loggi
        "com.loggi.driverapp" to AppWords(accept = COMMON_ACCEPT, inRide = COMMON_IN_RIDE),
        // Shopee Entregador
        "com.shopee.spx.driver.brazil" to AppWords(accept = COMMON_ACCEPT, inRide = COMMON_IN_RIDE)
    )

    fun forPackage(pkg: String): AppWords = BY_PACKAGE[pkg] ?: DEFAULT

    // ---- comparação sem maiúsculas, sem acentos e com espaços normalizados ----

    private val accentRx = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val spaceRx = Regex("\\s+")

    fun norm(s: String): String =
        accentRx.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "")
            .lowercase().replace(spaceRx, " ").trim()

    /** O elemento é o botão de aceitar (texto igual, ou "aceitar ..." curto). */
    fun isAccept(element: String, words: AppWords): Boolean {
        val t = norm(element)
        if (t.isEmpty() || t.length > 40) return false
        return words.accept.any { t == it || t.startsWith("$it ") }
    }

    /** O elemento é um marcador de corrida em andamento. */
    fun isInRide(element: String, words: AppWords): Boolean {
        val t = norm(element)
        if (t.isEmpty() || t.length > 80) return false
        return words.inRide.any { w -> t == w || (w.length >= 8 && t.contains(w)) }
    }
}
