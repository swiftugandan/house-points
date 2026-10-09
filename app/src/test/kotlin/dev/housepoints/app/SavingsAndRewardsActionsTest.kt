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
import dev.housepoints.ledger.DenialReason
import dev.housepoints.ledger.FamilyState
import dev.housepoints.ledger.LockStatus
import dev.housepoints.ledger.Locks
import dev.housepoints.ledger.Projection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/** SPEC Amendment 1: the parent-intent layer for locks and rewards. */
class SavingsAndRewardsActionsTest {
    private val zone = ZoneId.of("Europe/London")
    private val family = FamilyId(UUID.fromString("aaaaaaaa-0000-4000-8000-000000000003"))
    private val phone = DeviceId(UUID.fromString("bbbbbbbb-0000-4000-8000-000000000003"))
    private val payloads = mutableListOf<Payload>()

    private fun at(local: String) = InstantMs(LocalDateTime.parse(local).atZone(zone).toInstant().toEpochMilli())

    private fun state(asOf: String): FamilyState = Projection.project(
        payloads.mapIndexed { i, p ->
            val (type, body) = OpCodec.encodePayload(p)
            Op(OpId(Uuids.v7(i + 1L)), family, phone, Seq(i + 1L), Lamport(i + 1L), Op.CURRENT_SCHEMA, type, body)
        },
        at(asOf),
    )

    private fun record(action: Action) {
        payloads += (action as Action.Record).payloads
    }

    private fun ada(): ChildId {
        payloads += FamilyActions.createFamily("T", CurrencyCode("GBP"), zone, DayOfWeek.MONDAY, phone, "Phone")
        record(FamilyActions.addChild(state("2026-10-01T09:00"), "Ada", DisplayStyle.NUMBER))
        val ada = state("2026-10-01T09:00").children.single().id
        record(FamilyActions.award(setOf(ada), null, Points(1000), "Saved birthday money", at("2026-10-01T09:00")))
        return ada
    }

    @Test
    fun `a lock records frozen terms and a clear note`() {
        val ada = ada()
        val s = state("2026-10-14T12:00")
        val entry = (FamilyActions.lock(s, ada, Points(500), weeks = 4, now = at("2026-10-14T12:00")) as Action.Record).payloads.single() as EntryRecorded
        assertEquals(EntryKind.ADJUSTMENT, entry.kind)
        assertEquals(Points(-500), entry.points)
        assertEquals(Locks.termsNow(s, 4), entry.lock)
        assertEquals("Locked away for 4 weeks", entry.note)
    }

    @Test
    fun `locks below the minimum or above the balance are refused`() {
        val ada = ada()
        val s = state("2026-10-14T12:00")
        assertEquals(Action.Refused(Refusal.Rule(DenialReason.BELOW_MINIMUM)), FamilyActions.lock(s, ada, Points(50), 4, at("2026-10-14T12:00")))
        assertEquals(Action.Refused(Refusal.Rule(DenialReason.MORE_THAN_BALANCE)), FamilyActions.lock(s, ada, Points(5000), 4, at("2026-10-14T12:00")))
    }

    @Test
    fun `breaking a lock returns the principal now and stops the payout`() {
        val ada = ada()
        record(FamilyActions.lock(state("2026-10-14T12:00"), ada, Points(500), 4, at("2026-10-14T12:00")))
        val lock = Locks.forChild(state("2026-10-28T18:00"), ada).single()
        val back = FamilyActions.breakLock(lock, at("2026-10-28T18:00")).payloads.single() as EntryRecorded
        assertEquals(Points(500), back.points)
        assertEquals(lock.lockId, back.lockPayout)
        payloads += back
        assertEquals(LockStatus.BROKEN, Locks.forChild(state("2026-11-20T09:00"), ada).single().status)
    }

    @Test
    fun `a lock with a payout can only be reversed together with it`() {
        val ada = ada()
        record(FamilyActions.lock(state("2026-10-14T12:00"), ada, Points(500), 4, at("2026-10-14T12:00")))
        payloads += Locks.duePayouts(state("2026-11-16T09:00"))
        val s = state("2026-11-17T09:00")
        val lockLine = s.account(ada)!!.lines.single { it.entry.lock != null }
        assertEquals(Action.Refused(Refusal.LockHasReturns), FamilyActions.reverse(s, lockLine, "mistake"))
        val payoutLine = s.account(ada)!!.lines.single { it.entry.lockPayout != null }
        assertEquals(Action.Refused(Refusal.LockHasReturns), FamilyActions.reverse(s, payoutLine, "mistake"))
        val both = (FamilyActions.reverseLock(s, lockLine, "mistake") as Action.Record).payloads.map { it as EntryRecorded }
        assertEquals(setOf(EntryIds.reversal(lockLine.entry.entryId), EntryIds.reversal(EntryIds.lockPayout(lockLine.entry.entryId))), both.map { it.entryId }.toSet())
        payloads += both
        val after = state("2026-11-17T09:00")
        assertTrue(after.flags.isEmpty())
        assertTrue(Locks.forChild(after, ada).isEmpty())
    }

    @Test
    fun `redeeming a reward spends its price with a readable note`() {
        val ada = ada()
        record(FamilyActions.saveReward(null, "Screen time, 30 minutes", IconKey("music"), Points(50)))
        val s = state("2026-10-02T09:00")
        val reward = s.rewards.single()
        val entry = (FamilyActions.redeem(s, ada, reward, at("2026-10-02T09:00")) as Action.Record).payloads.single() as EntryRecorded
        assertEquals(Points(-50), entry.points)
        assertEquals("Reward: Screen time, 30 minutes", entry.note)
        assertEquals(reward.id, entry.rewardId)
        assertEquals(Action.Refused(Refusal.Rule(DenialReason.MORE_THAN_BALANCE)), FamilyActions.redeem(s, ada, reward.copy(price = Points(5000)), at("2026-10-02T09:00")))
    }
}
