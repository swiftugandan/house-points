package dev.housepoints.app

import dev.housepoints.app.family.Action
import dev.housepoints.app.family.FamilyActions
import dev.housepoints.app.family.Refusal
import dev.housepoints.contracts.ChildId
import dev.housepoints.contracts.CurrencyCode
import dev.housepoints.contracts.DeviceId
import dev.housepoints.contracts.DisplayStyle
import dev.housepoints.contracts.EntryIds
import dev.housepoints.contracts.EntryKind
import dev.housepoints.contracts.EntryRecorded
import dev.housepoints.contracts.FamilyId
import dev.housepoints.contracts.InstantMs
import dev.housepoints.contracts.Lamport
import dev.housepoints.contracts.MinorUnits
import dev.housepoints.contracts.Op
import dev.housepoints.contracts.OpCodec
import dev.housepoints.contracts.OpId
import dev.housepoints.contracts.Payload
import dev.housepoints.contracts.Points
import dev.housepoints.contracts.Seq
import dev.housepoints.contracts.Uuids
import dev.housepoints.ledger.Chores
import dev.housepoints.ledger.DenialReason
import dev.housepoints.ledger.FamilyState
import dev.housepoints.ledger.Projection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/** The parent-intent → payload layer, checked against the real ledger. */
class FamilyActionsTest {
    private val zone = ZoneId.of("Europe/London")
    private val family = FamilyId(UUID.fromString("aaaaaaaa-0000-4000-8000-000000000001"))
    private val phone = DeviceId(UUID.fromString("bbbbbbbb-0000-4000-8000-000000000001"))
    private val payloads = mutableListOf<Payload>()

    private fun at(local: String) = InstantMs(LocalDateTime.parse(local).atZone(zone).toInstant().toEpochMilli())

    private fun record(action: Action) {
        payloads += (action as Action.Record).payloads
    }

    private fun state(asOf: String): FamilyState {
        val ops = payloads.mapIndexed { i, payload ->
            val (type, body) = OpCodec.encodePayload(payload)
            Op(OpId(Uuids.v7(i.toLong() + 1)), family, phone, Seq(i + 1L), Lamport(i + 1L), Op.CURRENT_SCHEMA, type, body)
        }
        return Projection.project(ops, at(asOf))
    }

    private fun setUp(): ChildId {
        payloads += FamilyActions.createFamily("Test", CurrencyCode("GBP"), zone, DayOfWeek.MONDAY, phone, "Sam's phone")
        record(FamilyActions.addChild(state("2026-10-05T09:00"), "Ada", DisplayStyle.NUMBER))
        return state("2026-10-05T09:00").children.single().id
    }

    @Test
    fun `a new family has the default policies, values and this phone's name`() {
        setUp()
        val s = state("2026-10-05T09:00")
        assertEquals(5, s.values.size)
        assertEquals("Sam's phone", s.devices.single().name)
        assertEquals(dev.housepoints.contracts.PenaltyMode.CURRENT_WEEK, s.policies.penaltyAt(s.asOf))
        assertEquals(Points(100), s.policies.minCashOutAt(s.asOf))
    }

    @Test
    fun `awards need a note and cash-outs respect the minimum and the balance`() {
        val ada = setUp()
        assertEquals(Action.Refused(Refusal.NoteRequired), FamilyActions.award(setOf(ada), null, Points(10), " ", at("2026-10-05T10:00")))
        record(FamilyActions.award(setOf(ada), null, Points(250), "Kind to her brother", at("2026-10-05T10:00")))
        val s = state("2026-10-06T10:00")
        assertEquals(Action.Refused(Refusal.Rule(DenialReason.BELOW_MINIMUM)), FamilyActions.cashOut(s, ada, Points(50), at("2026-10-06T10:00")))
        assertEquals(Action.Refused(Refusal.Rule(DenialReason.MORE_THAN_BALANCE)), FamilyActions.cashOut(s, ada, Points(300), at("2026-10-06T10:00")))
        val cashOut = (FamilyActions.cashOut(s, ada, Points(200), at("2026-10-06T10:00")) as Action.Record).payloads.single() as EntryRecorded
        assertEquals(MinorUnits(200), cashOut.cashOut?.money)
        assertEquals(Points(-200), cashOut.points)
    }

    @Test
    fun `a due chore is recorded with the id both phones would use`() {
        val ada = setUp()
        record(FamilyActions.saveChore(null, "Bins", dev.housepoints.contracts.IconKey("bin"), dev.housepoints.contracts.ChoreKind.ASSIGNED, Points(20), setOf(ada), dev.housepoints.contracts.Recurrence.Daily))
        val s = state("2026-10-06T08:00")
        val due = Chores.dueFor(s, ada, s.family!!.let { java.time.LocalDate.of(2026, 10, 6) }).single()
        val entry = (FamilyActions.recordDueChore(ada, due, at("2026-10-06T08:00")).payloads.single()) as EntryRecorded
        assertEquals(EntryIds.recurringChore(due.chore.id, ada, java.time.LocalDate.of(2026, 10, 6)), entry.entryId)
    }

    @Test
    fun `reversing an entry cancels it at its own instant and cannot be repeated`() {
        val ada = setUp()
        record(FamilyActions.award(setOf(ada), null, Points(40), "Brave at the dentist", at("2026-10-05T10:00")))
        val line = state("2026-10-06T10:00").account(ada)!!.lines.single()
        record(FamilyActions.reverse(line, "Meant for Tom"))
        val after = state("2026-10-06T10:00").account(ada)!!
        assertEquals(Points(0), after.displayed)
        val reversed = after.lines.first { it.entry.kind == EntryKind.AWARD }
        assertEquals(Action.Refused(Refusal.AlreadyReversed), FamilyActions.reverse(reversed, "again"))
        assertTrue(after.lines.any { it.entry.kind == EntryKind.REVERSAL && it.entry.effectiveAt == line.entry.effectiveAt })
    }

    @Test
    fun `a new goal retires the old one`() {
        val ada = setUp()
        record(FamilyActions.setGoal(state("2026-10-05T09:00"), ada, "Kite", dev.housepoints.contracts.IconKey("kite"), Points(150)))
        record(FamilyActions.setGoal(state("2026-10-05T09:00"), ada, "Bike", dev.housepoints.contracts.IconKey("bike"), Points(5000)))
        val s = state("2026-10-05T09:00")
        assertEquals("Bike", s.activeGoal(ada)!!.title)
        assertEquals(1, s.goals.count { it.status == dev.housepoints.contracts.GoalStatus.ACTIVE })
    }
}
