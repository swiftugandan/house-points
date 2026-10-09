package dev.housepoints.ledger

import dev.housepoints.contracts.ChildUpsert
import dev.housepoints.contracts.CurrencyCode
import dev.housepoints.contracts.DeviceRemoved
import dev.housepoints.contracts.DeviceUpsert
import dev.housepoints.contracts.DisplayStyle
import dev.housepoints.contracts.EntryKind
import dev.housepoints.contracts.FamilyCreated
import dev.housepoints.contracts.GoalId
import dev.housepoints.contracts.GoalStatus
import dev.housepoints.contracts.GoalUpsert
import dev.housepoints.contracts.IconKey
import dev.housepoints.contracts.Points
import dev.housepoints.contracts.TickSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class RecordsTest {
    @Test
    fun `no family until family created arrives`() {
        val state = Projection.project(emptyList(), london("2026-10-09T12:00"))
        assertTrue(state.isEmpty)
        assertNull(state.family)
    }

    @Test
    fun `the first family created wins and later ones are ignored`() {
        val log = LogBuilder()
        log.add(FamilyCreated("First", CurrencyCode("GBP"), LONDON, DayOfWeek.MONDAY), lamport = 1)
        log.add(FamilyCreated("Second", CurrencyCode("EUR"), ZoneId.of("Europe/Paris"), DayOfWeek.SUNDAY), log.phoneB, lamport = 5)
        val family = Projection.project(log.ops(), london("2026-10-09T12:00")).family!!
        assertEquals("First", family.name)
        assertEquals(CurrencyCode("GBP"), family.currency)
        assertEquals(log.family, family.id)
    }

    @Test
    fun `fields are last-writer-wins independently`() {
        val log = LogBuilder().apply { createFamily() }
        val ada = log.child("Ada")
        log.add(ChildUpsert(ada, name = "Adaeze"), log.phoneA, lamport = 20)
        log.add(ChildUpsert(ada, displayStyle = DisplayStyle.PICTURE), log.phoneB, lamport = 19)
        log.add(ChildUpsert(ada, name = "Ady"), log.phoneB, lamport = 18)
        val child = Projection.project(log.ops(), london("2026-10-09T12:00")).child(ada)!!
        assertEquals("Adaeze", child.name)
        assertEquals(DisplayStyle.PICTURE, child.displayStyle)
    }

    @Test
    fun `equal lamports are decided by device id, unsigned`() {
        val log = LogBuilder().apply { createFamily() }
        val ada = log.child("Ada")
        log.add(ChildUpsert(ada, name = "From A"), log.phoneA, lamport = 50)
        log.add(ChildUpsert(ada, name = "From B"), log.phoneB, lamport = 50)
        assertEquals("From B", Projection.project(log.ops(), london("2026-10-09T12:00")).child(ada)!!.name)
    }

    @Test
    fun `devices carry their names and removal`() {
        val log = LogBuilder().apply { createFamily() }
        log.add(DeviceUpsert(log.phoneB, name = "Sam's phone"), log.phoneB)
        log.add(DeviceRemoved(log.phoneB))
        val devices = Projection.project(log.ops(), london("2026-10-09T12:00")).devices.associateBy { it.id }
        assertEquals("Sam's phone", devices.getValue(log.phoneB).name)
        assertTrue(devices.getValue(log.phoneB).removed)
    }

    @Test
    fun `the active goal is the latest active one for the child`() {
        val log = LogBuilder().apply { createFamily() }
        val ada = log.child("Ada")
        val kite = GoalId(log.uuid())
        val phones = GoalId(log.uuid())
        log.add(GoalUpsert(kite, ada, "Kite", IconKey("kite"), Points(150), GoalStatus.ACTIVE))
        log.add(GoalUpsert(kite, status = GoalStatus.REACHED))
        log.add(GoalUpsert(phones, ada, "Headphones", IconKey("headphones"), Points(1200), GoalStatus.ACTIVE))
        assertEquals(phones, Projection.project(log.ops(), london("2026-10-09T12:00")).activeGoal(ada)!!.id)
    }

    @Test
    fun `ticks are last-writer-wins per chore, child and day`() {
        val log = LogBuilder().apply { createFamily() }
        val bea = log.child("Bea")
        val bed = log.chore("Make your bed", 0, kind = dev.housepoints.contracts.ChoreKind.EXPECTED, assignees = setOf(bea))
        val day = LocalDate.of(2026, 10, 9)
        log.add(TickSet(bed, bea, day, done = true), lamport = 30)
        log.add(TickSet(bed, bea, day, done = false), log.phoneB, lamport = 29)
        assertEquals(true, Projection.project(log.ops(), london("2026-10-09T12:00")).ticks[TickKey(bed, bea, day)])
    }

    @Test
    fun `entries with the wrong sign are flagged and have no effect`() {
        val log = LogBuilder().apply { createFamily() }
        val ada = log.child("Ada")
        log.entry(ada, EntryKind.CASH_OUT, 50, london("2026-10-06T10:00"))
        val state = Projection.project(log.ops(), london("2026-10-09T12:00"))
        assertEquals(Points(0), state.account(ada)!!.displayed)
        assertTrue(state.flags.any { it is Flag.MalformedEntry })
    }

    @Test
    fun `unknown op types are counted for the newer-version notice`() {
        val log = LogBuilder().apply { createFamily() }
        val unknown = log.ops().first().copy(type = "locked.savings", body = "{}", opId = dev.housepoints.contracts.OpId(log.uuid()))
        val state = Projection.project(log.ops() + unknown, london("2026-10-09T12:00"))
        assertTrue(state.flags.contains(Flag.NewerVersionSeen(1)))
    }
}
