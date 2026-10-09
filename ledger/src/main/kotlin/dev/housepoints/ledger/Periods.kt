package dev.housepoints.ledger

import dev.housepoints.contracts.InstantMs
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** One week `[start, end)`, starting at local midnight on the family's week start day (SPEC FR-29). */
public data class Period(val startDate: LocalDate, val start: InstantMs, val end: InstantMs) {
    public val endDate: LocalDate get() = startDate.plusWeeks(1)
}

/** Week boundaries in the family's timezone; DST weeks come out 167 or 169 hours long, as they should. */
public class Periods(private val zone: ZoneId, private val weekStart: DayOfWeek) {
    public fun containing(instant: InstantMs): Period =
        startingOn(startDateOf(Instant.ofEpochMilli(instant.value).atZone(zone).toLocalDate()))

    public fun containingDay(day: LocalDate): Period = startingOn(startDateOf(day))

    public fun next(period: Period): Period = startingOn(period.endDate)

    public fun startDateOf(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(weekStart))

    public fun localDate(instant: InstantMs): LocalDate = Instant.ofEpochMilli(instant.value).atZone(zone).toLocalDate()

    private fun startingOn(startDate: LocalDate): Period =
        Period(startDate, midnight(startDate), midnight(startDate.plusWeeks(1)))

    private fun midnight(day: LocalDate): InstantMs = InstantMs(day.atStartOfDay(zone).toInstant().toEpochMilli())
}
