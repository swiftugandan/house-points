package dev.housepoints.app

import dev.housepoints.app.child.ChildViews
import dev.housepoints.app.family.Action
import dev.housepoints.app.family.FamilyActions
import dev.housepoints.app.ui.account.AccountModels
import dev.housepoints.app.ui.format.Formats
import dev.housepoints.app.ui.home.HomeModels
import dev.housepoints.app.ui.payday.Statements
import dev.housepoints.contracts.ChildId
import dev.housepoints.contracts.CurrencyCode
import dev.housepoints.contracts.DeviceId
import dev.housepoints.contracts.DisplayStyle
import dev.housepoints.contracts.FamilyId
import dev.housepoints.contracts.IconKey
import dev.housepoints.contracts.InstantMs
import dev.housepoints.contracts.Lamport
import dev.housepoints.contracts.Op
import dev.housepoints.contracts.OpCodec
import dev.housepoints.contracts.OpId
import dev.housepoints.contracts.Payload
import dev.housepoints.contracts.Points
import dev.housepoints.contracts.Seq
import dev.housepoints.contracts.Uuids
import dev.housepoints.ledger.FamilyState
import dev.housepoints.ledger.Locks
import dev.housepoints.ledger.Projection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import java.util.UUID

/** SPEC FR-53: wherever a balance is shown, what is locked away is shown with it. */
class SavingsPresentationTest {
    private val zone = ZoneId.of("Europe/London")
    private val formats = Formats(Locale.UK, zone)
    private val payloads = mutableListOf<Payload>()
    private fun at(local: String) = InstantMs(LocalDateTime.parse(local).atZone(zone).toInstant().toEpochMilli())

    private fun state(asOf: String): FamilyState = Projection.project(
        payloads.mapIndexed { i, p ->
            val (type, body) = OpCodec.encodePayload(p)
            Op(OpId(Uuids.v7(i + 1L)), FAMILY, PHONE, Seq(i + 1L), Lamport(i + 1L), Op.CURRENT_SCHEMA, type, body)
        },
        at(asOf),
    )

    private fun record(action: Action) {
        payloads += (action as Action.Record).payloads
    }

    private fun adaWithLock(): ChildId {
        payloads += FamilyActions.createFamily("T", CurrencyCode("GBP"), zone, DayOfWeek.MONDAY, PHONE, "Phone")
        record(FamilyActions.addChild(state("2026-10-01T09:00"), "Ada", DisplayStyle.NUMBER))
        val ada = state("2026-10-01T09:00").children.single().id
        record(FamilyActions.award(setOf(ada), null, Points(1000), "Birthday money", at("2026-10-01T09:00")))
        record(FamilyActions.lock(state("2026-10-14T12:00"), ada, Points(500), 4, at("2026-10-14T12:00")))
        return ada
    }

    @Test
    fun `the account shows each lock, when it comes back and roughly how much more`() {
        val ada = adaWithLock()
        val s = state("2026-10-20T09:00")
        val payout = Locks.forChild(s, ada).single().payout
        val model = AccountModels.from(s, ada, LocalDate.of(2026, 10, 20), formats, Locale.UK)!!
        val lock = model.locks.single()
        assertEquals("Locked away: 500", lock.title)
        assertEquals("Back Mon 16 Nov with about ${payout.value - 500} more", lock.detail)
    }

    @Test
    fun `history names locks, returns and rewards in plain words`() {
        val ada = adaWithLock()
        record(FamilyActions.saveReward(null, "Cinema trip", IconKey("gift"), Points(100)))
        val s = state("2026-10-20T09:00")
        record(FamilyActions.redeem(s, ada, s.rewards.single(), at("2026-10-20T10:00")))
        payloads += Locks.duePayouts(state("2026-11-16T09:00"))
        val after = state("2026-11-16T10:00")
        val titles = after.account(ada)!!.lines.map { AccountModels.lineModel(after, it, formats).title }
        assertTrue(titles.toString(), "Locked away · 4 weeks" in titles)
        assertTrue(titles.toString(), "Locked savings back" in titles)
        assertTrue(titles.toString(), "Reward · Cinema trip" in titles)
    }

    @Test
    fun `home mentions what is locked beside the week's earnings`() {
        val ada = adaWithLock()
        val home = HomeModels.from(state("2026-10-20T09:00"), LocalDate.of(2026, 10, 20), formats, Locale.UK)
        assertTrue(home.children.single { it.id == ada }.weekLine.endsWith("· 500 locked"))
    }

    @Test
    fun `the child sees the lock in their own words`() {
        val ada = adaWithLock()
        val view = ChildViews.from(state("2026-10-20T09:00"), ada, LocalDate.of(2026, 10, 20), formats)!!
        assertEquals("500 locked away until Mon 16 Nov, growing to about ${Locks.forChild(state("2026-10-20T09:00"), ada).single().payout.value}", view.lockedLine)
    }

    @Test
    fun `a broken lock coming back is not counted as earned this week`() {
        val ada = adaWithLock()
        record(FamilyActions.breakLock(Locks.forChild(state("2026-10-21T18:00"), ada).single(), at("2026-10-21T18:00")))
        val home = HomeModels.from(state("2026-10-22T09:00"), LocalDate.of(2026, 10, 22), formats, Locale.UK)
        assertEquals("Nothing yet this week", home.children.single { it.id == ada }.weekLine)
    }

    @Test
    fun `the payday statement shows locking as its own line and still adds up`() {
        val ada = adaWithLock()
        val statement = Statements.from(state("2026-10-20T09:00"), ada, LocalDate.of(2026, 10, 12), formats, Locale.UK)!!
        assertEquals(formats.signed(Points.ZERO), statement.spent)
        assertEquals(formats.signed(Points(-500)), statement.locked)
        assertEquals(null, statement.earnedDetail)
    }

    private companion object {
        val FAMILY = FamilyId(UUID.fromString("aaaaaaaa-0000-4000-8000-000000000004"))
        val PHONE = DeviceId(UUID.fromString("bbbbbbbb-0000-4000-8000-000000000004"))
    }
}
