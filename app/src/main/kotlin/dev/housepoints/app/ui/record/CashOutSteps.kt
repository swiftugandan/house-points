package dev.housepoints.app.ui.record

import dev.housepoints.contracts.CurrencyCode
import dev.housepoints.contracts.ExchangeRate
import dev.housepoints.contracts.Points

/** Cash-outs move in whole units of money (£1, $1, ¥100) so every amount converts exactly (SPEC FR-14). */
object CashOutSteps {
    private const val DECIMAL = 10L

    /** Points worth one major unit of the currency, or the smallest exact step when that is not whole. */
    fun stepPoints(rate: ExchangeRate?, currency: CurrencyCode): Long {
        if (rate == null || !rate.isValid) return 1L
        val digits = currency.toCurrency()?.defaultFractionDigits?.coerceAtLeast(0) ?: 2
        val minorPerMajor = (0 until digits).fold(1L) { acc, _ -> acc * DECIMAL }
        val numerator = Math.multiplyExact(minorPerMajor, rate.points)
        if (numerator % rate.minorUnits == 0L) return numerator / rate.minorUnits
        // One minor unit's worth is always exact when rate.points divides evenly; otherwise fall back to rate.points.
        return rate.points
    }

    /** The largest whole step not above [available], at least the [minimum] rounded up to a step. */
    fun initial(available: Points, step: Long, minimum: Points): Points {
        val floorToStep = (available.value / step) * step
        val minimumStep = ((minimum.value + step - 1) / step).coerceAtLeast(1) * step
        return Points(maxOf(floorToStep, minimumStep))
    }
}
