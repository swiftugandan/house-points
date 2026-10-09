package dev.housepoints.app.ui.record

import dev.housepoints.contracts.InstantMs
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * SPEC FR-17: when something happened. Today means now; an earlier day means midday on that day, so the
 * entry counts towards that day's (and that week's) balance and interest.
 */
object RecordDate {
    const val DAYS_BACK: Int = 7
    private val MIDDAY: LocalTime = LocalTime.NOON

    fun effectiveAt(day: LocalDate, today: LocalDate, now: InstantMs, zone: ZoneId): InstantMs =
        if (!day.isBefore(today)) now else InstantMs(day.atTime(MIDDAY).atZone(zone).toInstant().toEpochMilli())

    /** Today first, then each earlier day up to [DAYS_BACK] days ago. */
    fun choices(today: LocalDate): List<LocalDate> = (0..DAYS_BACK).map { today.minusDays(it.toLong()) }
}
