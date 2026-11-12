package br.com.andreluizs.rotalivre

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RideHistoryTest {
    @Test fun encodeAndDecodeRoundTrip() {
        val r = RideRecord(1_700_000_000_000L, "com.ubercab.driver", 1.2, 7.3)
        val back = RideHistory.decode(RideHistory.encode(r))
        assertEquals(r, back)
        assertEquals(8.5, back!!.totalKm, 0.001)
        assertEquals("Uber", back.appName)
    }

    @Test fun badLinesAreIgnored() {
        assertNull(RideHistory.decode("lixo"))
        assertNull(RideHistory.decode("a|b|c|d"))
    }

    @Test fun appNames() {
        assertEquals("99", RideRecord(0, "com.app99.driver", 0.0, 0.0).appName)
    }
}
