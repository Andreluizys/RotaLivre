package br.com.andreluizs.rotalivre

import android.content.Context

/**
 * Decide quais tarjas brancas aparecem no quadro: endereço fora da cidade escolhida e rua de terra.
 * Cada opção só roda se o motorista marcou na tela de configurações.
 */
object AddressChecker {

    /** Raio usado só quando não há internet nem cidade escrita: ~20 km a partir do centro da cidade. */
    const val FALLBACK_RADIUS_KM = 20.0

    private val cache = object : LinkedHashMap<String, List<String>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?) = size > 30
    }

    /** Tarjas para a oferta (lista vazia = nada a avisar). Pode demorar alguns segundos. */
    fun tagsFor(c: Context, offer: Offer): List<String> {
        val outOn = Prefs.warnOutOfCity(c)
        val dirtOn = Prefs.warnDirtRoad(c)
        if ((!outOn && !dirtOn) || offer.addresses.isEmpty()) return emptyList()

        val home = Prefs.getHomeCity(c)
        val key = "$home|$outOn|$dirtOn|${offer.addresses.joinToString("|")}"
        synchronized(cache) { cache[key]?.let { return it } }

        val here = GeoLookup.lastLocation(c)
        val roles = if (offer.addresses.size >= 2) listOf("EMBARQUE", "DESTINO") else listOf("ENDEREÇO")
        val tags = mutableListOf<String>()

        offer.addresses.take(roles.size).forEachIndexed { i, address ->
            val role = roles[i]
            var geo: Geo? = null
            var geoTried = false
            fun lookup(): Geo? {
                if (!geoTried) { geoTried = true; geo = GeoLookup.geocode(c, address, here) }
                return geo
            }

            if (outOn) {
                // 1) a cidade veio escrita no endereço; 2) o Android diz a cidade do endereço; 3) estimativa pela distância
                val written = AddressGeo.cityIn(address)
                val outside: Boolean? = when {
                    written != null -> !AddressGeo.sameCity(written, home)
                    AddressGeo.mentions(address, home) -> false
                    else -> lookup()?.city?.let { !AddressGeo.sameCity(it, home) }
                }
                when (outside) {
                    true -> tags += "$role FORA DOS LIMITES"
                    false -> {}
                    null -> if (i == 0 && here != null) {
                        val center = GeoLookup.cityCenter(c, home)
                        if (center != null) {
                            val d = AddressGeo.haversineKm(here.latitude, here.longitude, center.lat, center.lon)
                            if (AddressGeo.possiblyOutside(d, offer.pickupKm, FALLBACK_RADIUS_KM))
                                tags += "$role POSSIVELMENTE FORA DOS LIMITES"
                        }
                    }
                }
            }

            if (dirtOn) {
                val g = lookup()
                if (g != null && GeoLookup.isDirtRoad(g.lat, g.lon, AddressGeo.streetName(address)) == true)
                    tags += "$role RUA DE TERRA (mapa)"
            }
        }

        synchronized(cache) { cache[key] = tags }
        return tags
    }
}
