package br.com.andreluizs.rotalivre

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferParserTest {

    @Test fun parsesPriceAndBothDistances() {
        val o = OfferParser.parse(listOf("R$ 12,50", "5 min (2,1 km) de distância", "12 min (6,3 km) de viagem"))!!
        assertEquals(12.5, o.price, 0.001)
        assertEquals(2.1, o.pickupKm, 0.001)
        assertEquals(6.3, o.tripKm, 0.001)
        assertEquals(17, o.totalMin)
        assertEquals(12.5 / 8.4, o.perKm, 0.001)
    }

    @Test fun singleDistanceIsTrip() {
        val o = OfferParser.parse(listOf("R$ 10,00", "6,0 km"))!!
        assertEquals(0.0, o.pickupKm, 0.0)
        assertEquals(6.0, o.tripKm, 0.001)
    }

    @Test fun metersAreConvertedToKm() {
        val o = OfferParser.parse(listOf("R$ 8,00", "800 m", "3,2 km"))!!
        assertEquals(0.8, o.pickupKm, 0.001)
    }

    @Test fun minutesAreNotMistakenForMeters() {
        val o = OfferParser.parse(listOf("R$ 9,00", "4 min", "2,0 km", "9 min", "3,0 km"))!!
        assertEquals(2.0, o.pickupKm, 0.001)
        assertEquals(3.0, o.tripKm, 0.001)
    }

    @Test fun extraSurgeValueIsNotThePrice() {
        val o = OfferParser.parse(listOf("+R$ 3,00", "R$ 15,00", "1,0 km", "5,0 km"))!!
        assertEquals(15.0, o.price, 0.001)
        assertTrue(o.surge)
    }

    @Test fun perKmLabelIsNotThePrice() {
        val o = OfferParser.parse(listOf("R$ 1,80/km", "R$ 14,00", "1,0 km", "6,0 km"))!!
        assertEquals(14.0, o.price, 0.001)
    }

    @Test fun detectsSurgeWordsAndMultiplier() {
        assertTrue(OfferParser.parse(listOf("R$ 10,00", "Tarifa dinâmica", "1,0 km", "4,0 km"))!!.surge)
        assertTrue(OfferParser.parse(listOf("R$ 10,00", "1,5x", "1,0 km", "4,0 km"))!!.surge)
        assertFalse(OfferParser.parse(listOf("R$ 10,00", "1,0 km", "4,0 km"))!!.surge)
    }

    @Test fun noPriceOrNoDistanceIsNotAnOffer() {
        assertNull(OfferParser.parse(listOf("Você está online")))
        assertNull(OfferParser.parse(listOf("R$ 10,00")))
        assertNotNull(OfferParser.parse(listOf("R$ 10,00", "3 km")))
    }
}
