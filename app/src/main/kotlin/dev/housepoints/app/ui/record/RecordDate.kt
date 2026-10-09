package dev.housepoints.app.ui.record

import dev.housepoints.contracts.InstantMs
import java.time.LocalDate
import java.time.ZoneId

object RecordDate {
    const val DAYS_BACK: Int = 7
    fun effectiveAt(day: LocalDate, today: LocalDate, now: InstantMs, zone: ZoneId): InstantMs = TODO("green")
    fun choices(today: LocalDate): List<LocalDate> = TODO("green")
}
