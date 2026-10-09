package dev.housepoints.app.ui.format

import dev.housepoints.contracts.CurrencyCode
import dev.housepoints.contracts.ExchangeRate
import dev.housepoints.contracts.InstantMs
import dev.housepoints.contracts.MinorUnits
import dev.housepoints.contracts.Points
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

/** Every number and date the UI shows goes through here, so the same value always reads the same way. */
class Formats(private val locale: Locale, private val zone: ZoneId) {
    private val integer = NumberFormat.getIntegerInstance(locale)
    private val shortDay = DateTimeFormatter.ofPattern("EEE d", locale)
    private val longDay = DateTimeFormatter.ofPattern("EEEE d MMMM", locale)
    private val dayMonth = DateTimeFormatter.ofPattern("d MMMM", locale)
    private val dayMonthYear = DateTimeFormatter.ofPattern("d MMMM yyyy", locale)
    private val shortDayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
    private val monthYear = DateTimeFormatter.ofPattern("MMM yyyy", locale)
    private val time = DateTimeFormatter.ofPattern("HH:mm", locale)
    private val dayShortMonth = DateTimeFormatter.ofPattern("EEE d MMM", locale)

    fun points(value: Points): String = integer.format(value.value)

    /** `+30`, `−150` (a real minus sign), `0`. */
    fun signed(value: Points): String = when {
        value.value > 0 -> "+" + integer.format(value.value)
        value.value < 0 -> "−" + integer.format(-value.value)
        else -> "0"
    }

    /** Points with two decimals for interest lines below one point, e.g. `+2.02`. */
    fun signedDecimal(micro: Long): String {
        val decimal = BigDecimal.valueOf(micro).movePointLeft(MICRO_DIGITS).setScale(2, RoundingMode.DOWN)
        val text = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = if (decimal.stripTrailingZeros().scale() > 0) 2 else 0
            maximumFractionDigits = 2
        }.format(decimal.abs())
        return if (micro < 0) "−$text" else "+$text"
    }

    fun money(amount: MinorUnits, currency: CurrencyCode): String {
        val javaCurrency = currency.toCurrency() ?: return "${amount.value} ${currency.code}"
        val major = BigDecimal.valueOf(amount.value).movePointLeft(javaCurrency.defaultFractionDigits.coerceAtLeast(0))
        return NumberFormat.getCurrencyInstance(locale).apply { this.currency = javaCurrency }.format(major)
    }

    /** What a number of points is worth, rounded down to the currency's minor unit. */
    fun worth(points: Points, rate: ExchangeRate?, currency: CurrencyCode): String? {
        if (rate == null || !rate.isValid) return null
        val minor = Math.floorDiv(Math.multiplyExact(points.value, rate.minorUnits), rate.points)
        return money(MinorUnits(minor), currency)
    }

    fun shortDay(at: InstantMs): String = shortDay.format(Instant.ofEpochMilli(at.value).atZone(zone))
    fun shortDay(day: LocalDate): String = shortDay.format(day)

    /** `Mon 16 Nov`. */
    fun dayShortMonth(at: InstantMs): String = dayShortMonth.format(Instant.ofEpochMilli(at.value).atZone(zone))
    fun longDay(day: LocalDate): String = longDay.format(day)
    fun dayMonth(day: LocalDate): String = dayMonth.format(day)
    fun dayMonthYear(day: LocalDate): String = dayMonthYear.format(day)
    fun shortDayMonthYear(day: LocalDate): String = shortDayMonthYear.format(day)
    fun monthYear(day: LocalDate): String = monthYear.format(day)
    fun time(at: InstantMs): String = time.format(Instant.ofEpochMilli(at.value).atZone(zone))
    fun localDate(at: InstantMs): LocalDate = Instant.ofEpochMilli(at.value).atZone(zone).toLocalDate()

    /** `12 to 18 October`, or `28 September to 4 October` across a month. */
    fun weekRange(start: LocalDate): String {
        val end = start.plusDays(6)
        return if (start.month == end.month) "${start.dayOfMonth} to ${dayMonth.format(end)}"
        else "${dayMonth.format(start)} to ${dayMonth.format(end)}"
    }

    /** "1%" or "1.5%" from basis points. */
    fun percent(basisPoints: Int): String =
        BigDecimal.valueOf(basisPoints.toLong()).movePointLeft(2).stripTrailingZeros().toPlainString() + "%"

    companion object {
        private const val MICRO_DIGITS = 6

        fun defaultCurrency(locale: Locale): CurrencyCode =
            CurrencyCode(runCatching { Currency.getInstance(locale).currencyCode }.getOrDefault("GBP"))
    }
}
