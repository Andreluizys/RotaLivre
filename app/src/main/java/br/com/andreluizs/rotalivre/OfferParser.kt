package br.com.andreluizs.rotalivre

data class Offer(
    val price: Double,
    val pickupKm: Double,
    val tripKm: Double,
    val totalMin: Int?,
    val surge: Boolean
) {
    /** Distância total: ir buscar o passageiro + levar até o destino. */
    val totalKm get() = pickupKm + tripKm
    val perKm get() = if (totalKm > 0) price / totalKm else 0.0
    val perHour get() = totalMin?.takeIf { it > 0 }?.let { price / (it / 60.0) }
    val signature get() = "$price|$pickupKm|$tripKm"
}

object OfferParser {
    private val priceRx = Regex("""R\$\s?(\d{1,4}(?:[.,]\d{2})?)""")
    private val perKmSuffixRx = Regex("""^\s?/\s?km""", RegexOption.IGNORE_CASE)

    /** Primeiro R$ que é o preço da corrida: ignora "+R$ x" (extra de pico) e "R$ x/km". */
    private fun findPrice(all: String): Double? {
        for (m in priceRx.findAll(all)) {
            val before = all.substring(0, m.range.first).trimEnd()
            if (before.endsWith("+")) continue
            if (perKmSuffixRx.containsMatchIn(all.substring(m.range.last + 1))) continue
            return num(m.groupValues[1])
        }
        return null
    }
    private val distRx = Regex("""(\d{1,3}(?:[.,]\d{1,2})?)\s?(km|m)\b""", RegexOption.IGNORE_CASE)
    private val minRx = Regex("""(\d{1,3})\s?min""", RegexOption.IGNORE_CASE)
    private val multRx = Regex("""\d[.,]\d{1,2}\s?x""", RegexOption.IGNORE_CASE)
    private val extraRx = Regex("""\+\s?R\$""")
    private val surgeWords = listOf("dinâmic", "dinamic", "pico", "tarifa alta", "alta demanda", "surge")

    private fun num(s: String) = s.replace(",", ".").toDouble()

    /** Elemento que é só o preço da corrida (ex.: "R$ 14,04"), no formato real da Uber. */
    private val priceOnlyRx = Regex("""^R\$\s?\d{1,4}(?:[.,]\d{2})?$""")

    fun parse(texts: List<String>): Offer? {
        // Cartão da oferta: começa no preço. Antes dele ficam avisos do mapa
        // ("+R$ 3,75", "1-7 min") que não fazem parte da corrida.
        // O preço da corrida é o "R$ x" mais próximo antes da 1ª distância
        // (o primeiro da tela pode ser o ganho do dia, ex.: "R$ 43,93").
        val firstDist = texts.indexOfFirst { distRx.containsMatchIn(it) }
        val start = if (firstDist < 0) -1
            else texts.subList(0, firstDist).indexOfLast { priceOnlyRx.matches(it.trim()) }
        val card = if (start >= 0) texts.subList(start, texts.size) else texts
        val all = card.joinToString(" | ")
        val price = findPrice(all) ?: return null

        val dists = distRx.findAll(all).map {
            val v = num(it.groupValues[1])
            if (it.groupValues[2].equals("m", true)) v / 1000.0 else v
        }.toList()
        if (dists.isEmpty()) return null

        // Normalmente a 1ª distância é até o passageiro e a 2ª é a viagem
        val (pickup, trip) = if (dists.size >= 2) dists[0] to dists[1] else 0.0 to dists[0]

        val mins = minRx.findAll(all).map { it.groupValues[1].toInt() }.toList()
        val totalMin = if (mins.isNotEmpty()) mins.take(2).sum() else null

        val low = texts.joinToString(" | ").lowercase()
        val surge = surgeWords.any { low.contains(it) } ||
                multRx.containsMatchIn(all) ||
                extraRx.containsMatchIn(all)

        return Offer(price, pickup, trip, totalMin, surge)
    }
}
