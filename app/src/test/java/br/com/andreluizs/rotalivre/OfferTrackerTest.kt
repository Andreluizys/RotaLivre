package br.com.andreluizs.rotalivre

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferTrackerTest {
    private val pkg = "com.ubercab.driver"
    private fun offer(price: Double = 10.0, pickup: Double = 1.0, trip: Double = 5.0) =
        Screen(ScreenKind.OFFER, Offer(price, pickup, trip, 20, false), "ok")
    private val ride = Screen(ScreenKind.IN_RIDE, null, "")
    private val other = Screen(ScreenKind.OTHER, null, "")

    @Test fun sameOfferShowsOnceAndCountsOnce() {
        val t = OfferTracker()
        val a = t.onScreen(pkg, offer(), 0)
        assertTrue(a is Action.Show && a.count)
        repeat(20) { assertTrue(t.onScreen(pkg, offer(), 100L * (it + 1)) is Action.None) }
    }

    @Test fun changedOfferShowsAgain() {
        val t = OfferTracker()
        t.onScreen(pkg, offer(), 0)
        val a = t.onScreen(pkg, offer(price = 12.0), 500)
        assertTrue(a is Action.Show && a.count)
    }

    @Test fun declinedOfferHidesCard() {
        val t = OfferTracker()
        t.onScreen(pkg, offer(), 0)
        assertTrue(t.onScreen(pkg, other, 1000) is Action.Hide)
        assertEquals(OfferTracker.State.SEM_OFERTA, t.state(pkg))
    }

    @Test fun acceptedRideHidesAndNeverCalculatesDuringRide() {
        val t = OfferTracker()
        t.onScreen(pkg, offer(), 0)
        val a = t.onScreen(pkg, ride, 1000)
        assertTrue(a is Action.Accepted && a.hide)
        assertEquals(OfferTracker.State.EM_CORRIDA, t.state(pkg))
        repeat(10) { assertTrue(t.onScreen(pkg, ride, 2000L + it) is Action.None) }
    }

    @Test fun acceptedRideCountsKmOnlyOnce() {
        val t = OfferTracker()
        t.onScreen(pkg, offer(pickup = 1.0, trip = 5.0), 0)
        val a = t.onScreen(pkg, ride, 1000) as Action.Accepted
        assertEquals(6.0, a.offer.totalKm, 0.001)
        repeat(10) { assertTrue(t.onScreen(pkg, ride, 2000L + it) !is Action.Accepted) }
    }

    @Test fun acceptThroughFlickerStillCounts() {
        val t = OfferTracker()
        t.onScreen(pkg, offer(), 0)
        t.onScreen(pkg, other, 500)
        assertTrue(t.onScreen(pkg, ride, 1500) is Action.Accepted)
    }

    @Test fun declinedOfferNeverCountsAsAccepted() {
        val t = OfferTracker()
        t.onScreen(pkg, offer(), 0)
        t.onScreen(pkg, other, 1000)
        assertTrue(t.onScreen(pkg, ride, 60_000) !is Action.Accepted)
    }

    @Test fun newOfferAfterRideShowsAgain() {
        val t = OfferTracker()
        t.onScreen(pkg, offer(), 0)
        t.onScreen(pkg, ride, 1000)
        t.onScreen(pkg, other, 60_000)
        val a = t.onScreen(pkg, offer(price = 9.0), 120_000)
        assertTrue(a is Action.Show && a.count)
    }

    @Test fun flickerReShowsButDoesNotCountTwice() {
        val t = OfferTracker()
        t.onScreen(pkg, offer(), 0)
        t.onScreen(pkg, other, 500)            // árvore incompleta por instantes
        val a = t.onScreen(pkg, offer(), 800)
        assertTrue(a is Action.Show && !a.count)
    }

    @Test fun expiredCardDoesNotComeBack() {
        val t = OfferTracker()
        t.onScreen(pkg, offer(), 0)
        t.onExpired(pkg)
        assertTrue(t.onScreen(pkg, offer(), 9_000) is Action.None)
        t.onScreen(pkg, other, 10_000)
        assertTrue(t.onScreen(pkg, offer(), 11_000) is Action.None)
    }

    @Test fun appsAreIndependent() {
        val t = OfferTracker()
        t.onScreen(pkg, offer(), 0)
        val a = t.onScreen("com.app99.driver", offer(), 100)
        assertTrue(a is Action.Show && a.count)
    }

    @Test fun acceptClickCountsKmWithoutRideScreen() {
        val t = OfferTracker()
        t.onScreen(pkg, offer(pickup = 1.0, trip = 5.0), 0)
        val a = t.onAcceptClick(pkg, 2000) as Action.Accepted
        assertEquals(6.0, a.offer.totalKm, 0.001)
        assertEquals(OfferTracker.State.EM_CORRIDA, t.state(pkg))
    }

    @Test fun acceptClickAndRideScreenCountOnlyOnce() {
        val t = OfferTracker()
        t.onScreen(pkg, offer(), 0)
        assertTrue(t.onAcceptClick(pkg, 2000) is Action.Accepted)
        // a tela da oferta ainda aparece por instantes e depois vem a corrida em andamento
        assertTrue(t.onScreen(pkg, offer(), 2300) is Action.None)
        assertTrue(t.onScreen(pkg, ride, 3000) !is Action.Accepted)
    }

    @Test fun clickWithoutOfferDoesNothing() {
        val t = OfferTracker()
        assertTrue(t.onAcceptClick(pkg, 1000) is Action.None)
    }

    @Test fun clickAfterOfferExpiredDoesNotCount() {
        val t = OfferTracker()
        t.onScreen(pkg, offer(), 0)
        assertTrue(t.onAcceptClick(pkg, 60_000) is Action.None)
    }
}
