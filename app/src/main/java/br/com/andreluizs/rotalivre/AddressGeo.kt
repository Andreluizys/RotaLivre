package br.com.andreluizs.rotalivre

import java.text.Normalizer
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Regras puras (sem Android) para ler endereços da tela da oferta:
 * reconhecer o texto de um endereço, achar a cidade escrita nele e comparar cidades.
 */
object AddressGeo {

    private val accentRx = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val spaceRx = Regex("\\s+")

    /** Sem acento, minúsculo e com espaços normalizados. */
    fun norm(s: String): String =
        accentRx.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "")
            .lowercase().replace(spaceRx, " ").trim()

    private val streetStart = Regex(
        """^(r|rua|av|avenida|al|alameda|tv|travessa|estrada|estr|rod|rodovia|pca|praca|largo|beco|via|vila|vl|jd|jardim|sitio|chacara|fazenda|viela|marginal)\b[. ]"""
    )
    private val notAddress = Regex("""r\$|\bkm\b|\bmin\b|\d+\s?m\b|nota|avalia|corrida|viagem|aceitar|selecionar""")

    /** O texto parece um endereço (começa com rua, avenida, estrada... e não é preço, distância etc.)? */
    fun looksLikeAddress(text: String): Boolean {
        val t = norm(text)
        if (t.length < 8 || t.length > 160) return false
        if (notAddress.containsMatchIn(t)) return false
        return streetStart.containsMatchIn("$t ")
    }

    private const val UF = "ac|al|ap|am|ba|ce|df|es|go|ma|mt|ms|mg|pa|pb|pr|pe|pi|rj|rn|rs|ro|rr|sc|sp|se|to"
    private val cityUfRx = Regex("""([^,\-–/]{3,45}?)\s*[-–/,]\s*($UF)\b(?!\w)""")

    /**
     * Cidade escrita no endereço, se houver: "Rua A, 10 - Centro, Jacareí - SP" -> "Jacareí".
     * Sem a cidade (só rua e bairro) devolve nulo.
     */
    fun cityIn(address: String): String? {
        val t = norm(address)
        val m = cityUfRx.findAll(t).lastOrNull() ?: return null
        val city = m.groupValues[1].substringAfterLast(',').substringAfterLast(" - ").trim()
        if (city.length < 3 || city.any { it.isDigit() }) return null
        return city
    }

    /** As duas cidades são a mesma (ignora acento, maiúscula e o "de/da/do")? */
    fun sameCity(a: String, b: String): Boolean = simple(a) == simple(b)

    private fun simple(s: String) = norm(s).replace(Regex("""\b(de|da|do|das|dos)\b"""), "").replace(spaceRx, " ").trim()

    /** O endereço cita a cidade escolhida pelo nome? */
    fun mentions(address: String, city: String): Boolean = simple(address).contains(simple(city))

    /** Nome da rua sem número ("Rua das Flores, 123 - Centro" -> "rua das flores"). */
    fun streetName(address: String): String =
        norm(address).substringBefore(',').substringBefore(" - ").replace(Regex("""\s\d+.*$"""), "").trim()

    /** Distância em linha reta, em km. */
    fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    /**
     * Estimativa sem internet: de onde o motorista está até o centro da cidade, somando a distância até o
     * passageiro, passou do raio da cidade? É só um indício ("possivelmente fora").
     */
    fun possiblyOutside(driverToCenterKm: Double, pickupKm: Double, radiusKm: Double): Boolean =
        driverToCenterKm + pickupKm > radiusKm
}
