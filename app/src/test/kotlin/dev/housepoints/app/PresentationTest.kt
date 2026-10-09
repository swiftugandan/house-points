package dev.housepoints.app

import dev.housepoints.app.sync.PairingCode
import dev.housepoints.app.ui.Relative
import dev.housepoints.app.ui.components.JarModel
import dev.housepoints.app.ui.format.Formats
import dev.housepoints.app.ui.record.CashOutSteps
import dev.housepoints.contracts.CurrencyCode
import dev.housepoints.contracts.ExchangeRate
import dev.housepoints.contracts.FamilyId
import dev.housepoints.contracts.InstantMs
import dev.housepoints.contracts.MinorUnits
import dev.housepoints.contracts.Points
import dev.housepoints.sync.FamilyKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.UUID

class PresentationTest {
    private val formats = Formats(Locale.UK, ZoneId.of("Europe/London"))

    @Test
    fun `signed amounts use a real minus sign and money uses the currency`() {
        assertEquals("+30", formats.signed(Points(30)))
        assertEquals("−150", formats.signed(Points(-150)))
        assertEquals("£2.52", formats.money(MinorUnits(252), CurrencyCode("GBP")))
        assertEquals("£2.52", formats.worth(Points(252), ExchangeRate(1, 1), CurrencyCode("GBP")))
        assertEquals("+2.02", formats.signedDecimal(2_020_000))
        assertEquals("+2", formats.signedDecimal(2_000_000))
        assertEquals("1%", formats.percent(100))
        assertEquals("1.25%", formats.percent(125))
    }

    @Test
    fun `week ranges read naturally across months`() {
        assertEquals("12 to 18 October", formats.weekRange(LocalDate.of(2026, 10, 12)))
        assertEquals("28 September to 4 October", formats.weekRange(LocalDate.of(2026, 9, 28)))
    }

    @Test
    fun `cash-outs step in whole units of money`() {
        assertEquals(100L, CashOutSteps.stepPoints(ExchangeRate(1, 1), CurrencyCode("GBP")))
        assertEquals(1000L, CashOutSteps.stepPoints(ExchangeRate(10, 1), CurrencyCode("GBP")))
        assertEquals(1L, CashOutSteps.stepPoints(ExchangeRate(1, 1), CurrencyCode("JPY")))
        assertEquals(Points(200), CashOutSteps.initial(Points(252), 100, Points(100)))
        assertEquals(Points(100), CashOutSteps.initial(Points(40), 100, Points(100)))
    }

    @Test
    fun `the pairing code round-trips and rejects anything else`() {
        val family = FamilyId(UUID.randomUUID())
        val key = FamilyKey.generate()
        val code = PairingCode.encode(family, key)
        val (decodedFamily, decodedKey) = PairingCode.decode(code)!!
        assertEquals(family, decodedFamily)
        assertArrayEquals(key.bytes(), decodedKey.bytes())
        assertNull(PairingCode.decode("hp1:not-base64!"))
        assertNull(PairingCode.decode("https://example.com"))
        assertNull(PairingCode.decode(code.dropLast(4)))
    }

    @Test
    fun `the jar shows ten-point coins, or hundred-point coins for big balances`() {
        assertEquals(JarModel(coins = 8, partial = true, coinValue = 10, goalCoins = 15), JarModel.of(Points(87), Points(150)))
        assertEquals(100L, JarModel.of(Points(2500), null).coinValue)
        assertEquals(0, JarModel.of(Points(-40), null).coins)
    }

    @Test
    fun `relative times are plain`() {
        val now = InstantMs(10 * 86_400_000L)
        assertEquals("just now", Relative.ago(InstantMs(now.value - 10_000), now))
        assertEquals("1 hour ago", Relative.ago(InstantMs(now.value - 3_700_000), now))
        assertEquals("2 days ago", Relative.ago(InstantMs(now.value - 2 * 86_400_000L - 5), now))
    }
}
