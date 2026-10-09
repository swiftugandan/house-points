package dev.housepoints.app

import dev.housepoints.app.family.Action
import dev.housepoints.app.family.FamilyActions
import dev.housepoints.app.ui.record.RecordDate
import dev.housepoints.contracts.ChoreKind
import dev.housepoints.contracts.CurrencyCode
import dev.housepoints.contracts.DeviceId
import dev.housepoints.contracts.DisplayStyle
import dev.housepoints.contracts.EntryIds
import dev.housepoints.contracts.EntryRecorded
import dev.housepoints.contracts.FamilyId
import dev.housepoints.contracts.IconKey
import dev.housepoints.contracts.InstantMs
import dev.housepoints.contracts.Lamport
import dev.housepoints.contracts.Op
import dev.housepoints.contracts.OpCodec
import dev.housepoints.contracts.OpId
import dev.housepoints.contracts.Payload
import dev.housepoints.contracts.Points
import dev.housepoints.contracts.Recurrence
import dev.housepoints.contracts.Seq
import dev.housepoints.contracts.Uuids
import dev.housepoints.ledger.Chores
import dev.housepoints.ledger.FamilyState
import dev.housepoints.ledger.Projection
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/** SPEC FR-17: a parent may backdate chores and awards; the entry then counts from that day. */
class RecordDateTest {
    private val zone = ZoneId.of("Europe/London")
    private val today = LocalDate.of(2026, 10, 14)
    private val now = at("2026-10-14T19:30")

    private fun at(local: String) = InstantMs(LocalDateTime.parse(local).atZone(zone).toInstant().toEpochMilli())

    @Test
    fun `today means now and an earlier day means midday on that day`() {
        assertEquals(now, RecordDate.effectiveAt(today, today, now, zone))
        assertEquals(at("2026-10-13T12:00"), RecordDate.effectiveAt(today.minusDays(1), today, now, zone))
    }

    @Test
    fun `the choices go back one week, newest first`() {
        val days = RecordDate.choices(today)
        assertEquals(today, days.first())
        assertEquals(today.minusDays(RecordDate.DAYS_BACK.toLong()), days.last())
        assertEquals(RecordDate.DAYS_BACK + 1, days.size)
    }

    @Test
    fun `a chore done yesterday gets yesterday's shared id and earns from yesterday`() {
        val family = FamilyId(UUID.fromString("aaaaaaaa-0000-4000-8000-000000000002"))
        val phone = DeviceId(UUID.fromString("bbbbbbbb-0000-4000-8000-000000000002"))
        val payloads = mutableListOf<Payload>()
        payloads += FamilyActions.createFamily("T", CurrencyCode("GBP"), zone, DayOfWeek.MONDAY, phone, "Phone")
        fun state(): FamilyState = Projection.project(
            payloads.mapIndexed { i, p ->
                val (type, body) = OpCodec.encodePayload(p)
                Op(OpId(Uuids.v7(i + 1L)), family, phone, Seq(i + 1L), Lamport(i + 1L), Op.CURRENT_SCHEMA, type, body)
            },
            now,
        )
        payloads += (FamilyActions.addChild(state(), "Tom", DisplayStyle.NUMBER)).payloads
        val tom = state().children.single().id
        payloads += FamilyActions.saveChore(null, "Bins", IconKey("bin"), ChoreKind.ASSIGNED, Points(20), setOf(tom), Recurrence.Daily).payloads

        val yesterday = today.minusDays(1)
        val due = Chores.dueFor(state(), tom, yesterday).single()
        val entry = FamilyActions.recordDueChore(tom, due, RecordDate.effectiveAt(yesterday, today, now, zone)).payloads.single() as EntryRecorded
        assertEquals(EntryIds.recurringChore(due.chore.id, tom, yesterday), entry.entryId)
        payloads += entry

        val line = state().account(tom)!!.lines.single()
        assertEquals(at("2026-10-13T12:00"), line.entry.effectiveAt)
        assertEquals(emptyList<Any>(), Chores.dueFor(state(), tom, yesterday))
        assertEquals(1, (Chores.dueFor(state(), tom, today)).size)
    }
}
