package dev.housepoints.contracts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ValuesTest {
    @Test
    fun `displayed points round down, including below zero`() {
        assertEquals(Points(204), Micropoints(204_020_000).floorPoints())
        assertEquals(Points(-1), Micropoints(-1).floorPoints())
    }

    @Test(expected = ArithmeticException::class)
    fun `overflow is an error, never a wrapped value`() {
        Points(Long.MAX_VALUE).toMicropoints()
    }

    @Test
    fun `cash-out amounts convert only when exact`() {
        val penny = ExchangeRate(points = 1, minorUnits = 1)
        assertEquals(MinorUnits(150), penny.moneyFor(Points(150)))
        val tenPerPenny = ExchangeRate(points = 10, minorUnits = 1)
        assertEquals(MinorUnits(15), tenPerPenny.moneyFor(Points(150)))
        assertNull(tenPerPenny.moneyFor(Points(155)))
    }
}
