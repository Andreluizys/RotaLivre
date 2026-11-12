package br.com.andreluizs.rotalivre

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddressGeoTest {

    @Test fun recognizesStreetAddresses() {
        assertTrue(AddressGeo.looksLikeAddress("Rua das Flores, 123 - Centro"))
        assertTrue(AddressGeo.looksLikeAddress("Av. Cassiano Ricardo, 500 - Jardim Aquarius, São José dos Campos - SP"))
        assertTrue(AddressGeo.looksLikeAddress("Estrada do Cajuru, 1200"))
    }

    @Test fun ignoresPricesDistancesAndButtons() {
        assertFalse(AddressGeo.looksLikeAddress("R$ 12,50"))
        assertFalse(AddressGeo.looksLikeAddress("Rua 2,1 km de distância"))
        assertFalse(AddressGeo.looksLikeAddress("Aceitar corrida"))
        assertFalse(AddressGeo.looksLikeAddress("5 min (2,1 km) de viagem"))
        assertFalse(AddressGeo.looksLikeAddress("Centro"))
    }

    @Test fun findsTheCityWrittenInTheAddress() {
        assertEquals("jacarei", AddressGeo.cityIn("Rua A, 10 - Centro, Jacareí - SP"))
        assertEquals("sao jose dos campos", AddressGeo.cityIn("Av. B, 5 - Jardim X, São José dos Campos - SP, 12245-000"))
        assertEquals("taubate", AddressGeo.cityIn("Rua C, 99, Taubaté, SP"))
    }

    @Test fun noCityWhenOnlyStreetAndNeighborhood() {
        assertNull(AddressGeo.cityIn("Rua das Flores, 123 - Centro"))
        assertNull(AddressGeo.cityIn("Av. Brasil, 400"))
    }

    @Test fun comparesCitiesIgnoringAccentsAndSmallWords() {
        assertTrue(AddressGeo.sameCity("São José dos Campos", "sao jose dos campos"))
        assertTrue(AddressGeo.sameCity("Sao Jose Campos", "São José dos Campos"))
        assertFalse(AddressGeo.sameCity("Jacareí", "São José dos Campos"))
    }

    @Test fun mentionsHomeCityInTheText() {
        assertTrue(AddressGeo.mentions("Rua A, 10 - São José dos Campos", "São José dos Campos"))
        assertFalse(AddressGeo.mentions("Rua A, 10 - Jacareí", "São José dos Campos"))
    }

    @Test fun streetNameWithoutNumber() {
        assertEquals("rua das flores", AddressGeo.streetName("Rua das Flores, 123 - Centro"))
        assertEquals("estrada do cajuru", AddressGeo.streetName("Estrada do Cajuru 1200"))
    }

    @Test fun distanceBetweenCitiesIsReasonable() {
        // São José dos Campos -> Jacareí: ~ 17 km em linha reta
        val d = AddressGeo.haversineKm(-23.1896, -45.8841, -23.3053, -45.9658)
        assertTrue(d in 14.0..20.0)
    }

    @Test fun possibleOutsideUsesRadius() {
        assertFalse(AddressGeo.possiblyOutside(driverToCenterKm = 5.0, pickupKm = 3.0, radiusKm = 20.0))
        assertTrue(AddressGeo.possiblyOutside(driverToCenterKm = 18.0, pickupKm = 4.0, radiusKm = 20.0))
    }

    @Test fun parserKeepsTheTwoAddresses() {
        val o = OfferParser.parse(
            listOf(
                "R$ 12,50", "5 min (2,1 km) de distância", "Rua das Flores, 123 - Centro",
                "12 min (6,3 km) de viagem", "Av. Brasil, 400 - Jacareí - SP", "Aceitar",
            )
        )!!
        assertEquals(listOf("Rua das Flores, 123 - Centro", "Av. Brasil, 400 - Jacareí - SP"), o.addresses)
    }
}
