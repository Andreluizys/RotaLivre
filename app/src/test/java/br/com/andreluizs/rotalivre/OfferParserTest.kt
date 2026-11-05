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
        val o = OfferParser.parse(listOf("R$ 15,00", "+R$ 3,00 extra", "1,0 km", "5,0 km"))!!
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

    // Textos reais capturados da Uber (nov/2026)
    @Test fun realUberOfferWithMapNoise() {
        val o = OfferParser.parse(listOf(
            "Página inicial", "R$ 43,93", "Radar de Viagens", "3", "+R$ 3,75", "1-7 min", "1-6 min",
            "UberX", "R$ 14,04", "Est. de R$1,46/km", "+10% de ganhos Uber Pro",
            "7 min • 1.8 km", "Rua Gandu, São José dos Campos", "4,64 (12)", "Verificado",
            "17 minutos • 7.8 km", "Avenida Nicanor Reis, 1601", "Tenho interesse"
        ))!!
        assertEquals(14.04, o.price, 0.001)
        assertEquals(1.8, o.pickupKm, 0.001)
        assertEquals(7.8, o.tripKm, 0.001)
        assertEquals(24, o.totalMin)
        assertFalse(o.surge)
    }

    @Test fun realUberOfferSimple() {
        val o = OfferParser.parse(listOf(
            "UberX", "Exclusivo", "R$ 7,03", "Est. de R$1,56/km", "4 min • 1.2 km",
            "R. Joao Adao", "7 minutos • 3.3 km", "Aceitar"
        ))!!
        assertEquals(7.03, o.price, 0.001)
        assertEquals(1.56, o.perKm, 0.01)
        assertEquals(11, o.totalMin)
    }

    @Test fun homeScreenEarningsIsNotAnOffer() {
        assertNull(OfferParser.parse(listOf("Página inicial", "R$ 43,93", "Você está online", "+R$ 8,50", "1-4 min")))
    }

    @Test fun noPriceOrNoDistanceIsNotAnOffer() {
        assertNull(OfferParser.parse(listOf("Você está online")))
        assertNull(OfferParser.parse(listOf("R$ 10,00")))
        assertNotNull(OfferParser.parse(listOf("R$ 10,00", "3 km")))
    }
}
