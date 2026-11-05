package br.com.andreluizs.rotalivre

import org.junit.Assert.assertEquals
import org.junit.Test

class OfferDetectorTest {
    private val uber = "com.ubercab.driver"

    private val offer = listOf(
        "UberX", "Exclusivo", "R$ 7,03", "Est. de R$1,56/km", "4 min • 1.2 km",
        "R. Joao Adao", "7 minutos • 3.3 km", "Aceitar"
    )

    @Test fun completeOfferWithAcceptButtonIsOffer() {
        val s = OfferDetector.classify(uber, offer)
        assertEquals(ScreenKind.OFFER, s.kind)
        assertEquals(7.03, s.offer!!.price, 0.001)
    }

    @Test fun acceptWordsIgnoreCaseAndAccents() {
        assertEquals(ScreenKind.OFFER, OfferDetector.classify(uber, offer.dropLast(1) + "ACEITAR").kind)
        assertEquals(ScreenKind.OFFER, OfferDetector.classify(uber, offer.dropLast(1) + "Tenho interesse").kind)
    }

    @Test fun rideInProgressWithNumbersIsNotAnOffer() {
        // Corrida em andamento: distância/tempo restantes mudando, sem botão de aceitar
        val s = OfferDetector.classify(uber, listOf("R$ 14,04", "12 min • 5.4 km", "Rua A", "Navegar", "Finalizar viagem"))
        assertEquals(ScreenKind.IN_RIDE, s.kind)
    }

    @Test fun rideMarkerWinsEvenIfAcceptWordIsPresent() {
        val s = OfferDetector.classify(uber, offer + "Iniciar viagem")
        assertEquals(ScreenKind.IN_RIDE, s.kind)
    }

    @Test fun noAcceptButtonIsIgnored() {
        assertEquals(ScreenKind.OTHER, OfferDetector.classify(uber, offer.dropLast(1)).kind)
    }

    @Test fun incompleteDataIsIgnored() {
        val s = OfferDetector.classify(uber, listOf("R$ 7,03", "4 min • 1.2 km", "Aceitar"))
        assertEquals(ScreenKind.OTHER, s.kind)
        assertEquals(ScreenKind.OTHER, OfferDetector.classify(uber, listOf("4 min • 1.2 km", "7 min • 3.3 km", "Aceitar")).kind)
    }

    @Test fun shortMarkerOnlyMatchesWholeText() {
        // "rota" dentro de um endereço não é marcador
        val s = OfferDetector.classify(uber, offer.map { if (it == "R. Joao Adao") "Rua da Rota Nova" else it })
        assertEquals(ScreenKind.OFFER, s.kind)
    }

    @Test fun deliveryPackageUsesItsOwnWords() {
        val s = OfferDetector.classify("br.com.ifood.driver.app",
            listOf("R$ 11,50", "3 min • 1.4 km", "9 min • 3.2 km", "Quero fazer"))
        assertEquals(ScreenKind.OFFER, s.kind)
    }
}
