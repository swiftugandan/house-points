package dev.housepoints.ledger

import dev.housepoints.contracts.ChoreKind
import dev.housepoints.contracts.ChoreRef
import dev.housepoints.contracts.EntryIds
import dev.housepoints.contracts.EntryKind
import dev.housepoints.contracts.Recurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class FlagsAndChoresTest {
    @Test
    fun `the same award recorded on both phones on the same day is a possible duplicate`() {
        val log = LogBuilder().apply { createFamily() }
        val bea = log.child("Bea")
        val first = log.entry(bea, EntryKind.AWARD, 10, london("2026-10-14T09:00"), log.phoneA, note = "shoes")
        val second = log.entry(bea, EntryKind.AWARD, 10, london("2026-10-14T18:00"), log.phoneB, note = "shoes")
        val flags = Projection.project(log.ops(), london("2026-10-15T09:00")).flags
        val expected = listOf(first, second).sorted()
        assertTrue(flags.contains(Flag.PossibleDuplicate(bea, expected[0], expected[1])))
    }

    @Test
    fun `same-phone repeats and reversed entries are not duplicates`() {
        val log = LogBuilder().apply { createFamily() }
        val bea = log.child("Bea")
        log.entry(bea, EntryKind.AWARD, 10, london("2026-10-14T09:00"), log.phoneA, note = "a")
        log.entry(bea, EntryKind.AWARD, 10, london("2026-10-14T18:00"), log.phoneA, note = "b")
        val onB = log.entry(bea, EntryKind.AWARD, 10, london("2026-10-14T19:00"), log.phoneB, note = "c")
        log.reverse(onB, bea, 10, london("2026-10-14T19:00"), log.phoneB)
        val flags = Projection.project(log.ops(), london("2026-10-15T09:00")).flags
        assertTrue(flags.none { it is Flag.PossibleDuplicate })
    }

    @Test
    fun `a recurring chore recorded on both phones lands once because the id is shared`() {
        val log = LogBuilder().apply { createFamily() }
        val tom = log.child("Tom")
        val bins = log.chore("Bins", 20, assignees = setOf(tom), recurrence = Recurrence.Weekdays(setOf(DayOfWeek.TUESDAY)))
        val day = LocalDate.of(2026, 10, 13)
        val id = EntryIds.recurringChore(bins, tom, day)
        log.entry(tom, EntryKind.CHORE, 20, london("2026-10-13T17:00"), log.phoneA, id = id, chore = ChoreRef(bins, day))
        log.entry(tom, EntryKind.CHORE, 20, london("2026-10-13T18:30"), log.phoneB, id = id, chore = ChoreRef(bins, day))
        val account = Projection.project(log.ops(), london("2026-10-14T09:00")).account(tom)!!
        assertEquals(1, account.lines.size)
        assertEquals(dev.housepoints.contracts.Points(20), account.displayed)
    }

    @Test
    fun `due chores follow each recurrence and disappear once recorded`() {
        val log = LogBuilder().apply { createFamily() }
        val ada = log.child("Ada")
        val bins = log.chore("Bins", 20, assignees = setOf(ada), recurrence = Recurrence.Weekdays(setOf(DayOfWeek.TUESDAY)))
        val dishes = log.chore("Dishwasher", 15, assignees = setOf(ada), recurrence = Recurrence.Daily)
        val hoover = log.chore("Hoover", 30, assignees = setOf(ada), recurrence = Recurrence.Weekly)
        val garage = log.chore("Garage", 200, assignees = setOf(ada), recurrence = Recurrence.Once)
        log.chore("Car", 150, kind = ChoreKind.BOUNTY, recurrence = Recurrence.Daily)

        val tuesday = LocalDate.of(2026, 10, 13)
        log.entry(ada, EntryKind.CHORE, 15, london("2026-10-13T08:00"), id = EntryIds.recurringChore(dishes, ada, tuesday), chore = ChoreRef(dishes, tuesday))
        log.entry(ada, EntryKind.CHORE, 200, london("2026-10-10T08:00"), id = EntryIds.onceChore(garage, ada), chore = ChoreRef(garage))

        val state = Projection.project(log.ops(), london("2026-10-13T12:00"))
        val due = Chores.dueFor(state, ada, tuesday).map { it.chore.id to it.occurrence }
        assertEquals(listOf(bins to tuesday, hoover to LocalDate.of(2026, 10, 12)), due)
        assertEquals(listOf("Car"), Chores.bounties(state).map { it.title })
    }
}
