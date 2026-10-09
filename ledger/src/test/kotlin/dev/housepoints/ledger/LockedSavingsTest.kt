package dev.housepoints.ledger

import dev.housepoints.contracts.ChildId
import dev.housepoints.contracts.EntryIds
import dev.housepoints.contracts.EntryRecorded
import dev.housepoints.contracts.IconKey
import dev.housepoints.contracts.Points
import dev.housepoints.contracts.RewardId
import dev.housepoints.contracts.RewardUpsert
import dev.housepoints.contracts.Uuids
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** SPEC Amendment 1, examples L1 to L3 and R1. */
class LockedSavingsTest {
    private class L1 {
        val log = LogBuilder().apply {
            createFamily()
            interestRate(0, from = "2026-08-01T00:00")
            interestRate(100, from = "2026-10-12T00:00")
            lockBonus(100)
        }
        val ada: ChildId = log.child("Ada")
        val lockId = run {
            log.award(ada, 1000, "2026-10-01T09:00")
            log.lock(ada, 500, weeks = 4, rateBp = 200, cap = 2000, at = "2026-10-14T12:00")
        }

        fun state(asOf: String) = Projection.project(log.ops(), london(asOf))
    }

    @Test
    fun `L1 - the lock leaves the main account and its week earns on what is left`() {
        val ex = L1()
        val week = ex.state("2026-10-19T09:00").account(ex.ada)!!.periods.single { it.startDate == LocalDate.of(2026, 10, 12) }
        assertEquals(micro("500"), week.lowest)
        assertEquals(micro("5"), week.interest)
        assertEquals(micro("505"), week.closing)
    }

    @Test
    fun `L1 - the pot earns four whole weeks at its frozen rate and pays out the floor`() {
        val ex = L1()
        val lock = Locks.forChild(ex.state("2026-10-30T12:00"), ex.ada).single()
        assertEquals(Points(500), lock.principal)
        assertEquals(london("2026-10-19T00:00"), lock.earnsFrom)
        assertEquals(london("2026-11-16T00:00"), lock.maturity)
        assertEquals(Points(541), lock.payout)
        assertEquals(micro("510"), lock.potAt(london("2026-10-30T12:00")))
        assertEquals(micro("520.2"), lock.potAt(london("2026-11-02T12:00")))
        assertEquals(micro("541.21608"), lock.potAt(london("2026-11-16T00:00")))
        assertEquals(LockStatus.LOCKED, lock.status)
    }

    @Test
    fun `L1 - at maturity one payout is due, and recording it on both phones lands once`() {
        val ex = L1()
        val due = Locks.duePayouts(ex.state("2026-11-16T09:00")).single()
        assertEquals(EntryIds.lockPayout(ex.lockId), due.entryId)
        assertEquals(Points(541), due.points)
        assertEquals(london("2026-11-16T00:00"), due.effectiveAt)
        assertEquals(ex.lockId, due.lockPayout)

        ex.log.add(due, ex.log.phoneA)
        ex.log.add(due, ex.log.phoneB)
        val after = ex.state("2026-11-16T09:00")
        assertEquals(emptyList<EntryRecorded>(), Locks.duePayouts(after))
        assertEquals(LockStatus.PAID, Locks.forChild(after, ex.ada).single().status)
        assertEquals(1, after.account(ex.ada)!!.lines.count { it.entry.lockPayout == ex.lockId })
    }

    @Test
    fun `L1 - a payout at the start of a week earns main interest only from the next week`() {
        val ex = L1()
        ex.log.add(Locks.duePayouts(ex.state("2026-11-16T09:00")).single())
        val week = ex.state("2026-11-23T09:00").account(ex.ada)!!.periods.single { it.startDate == LocalDate.of(2026, 11, 16) }
        assertEquals(week.opening, week.lowest)
        assertEquals(week.opening + micro("541") + week.interest, week.closing)
    }

    @Test
    fun `nothing is due before maturity`() {
        val ex = L1()
        assertEquals(emptyList<EntryRecorded>(), Locks.duePayouts(ex.state("2026-11-15T23:59")))
    }

    @Test
    fun `L2 - breaking early returns the principal now, with no interest and no payout later`() {
        val ex = L1()
        ex.log.lockReturn(ex.lockId, ex.ada, 500, london("2026-10-28T18:00"), dev.housepoints.contracts.EntryId(Uuids.v7(9)), note = "Lock broken early")
        val state = ex.state("2026-11-16T09:00")
        assertEquals(emptyList<EntryRecorded>(), Locks.duePayouts(state))
        assertEquals(LockStatus.BROKEN, Locks.forChild(state, ex.ada).single().status)
        val week = state.account(ex.ada)!!.periods.single { it.startDate == LocalDate.of(2026, 10, 26) }
        assertEquals(week.opening, week.lowest)
    }

    @Test
    fun `L3 - reversing only the lock after its payout is flagged, never corrected`() {
        val ex = L1()
        ex.log.add(Locks.duePayouts(ex.state("2026-11-16T09:00")).single())
        ex.log.reverse(ex.lockId, ex.ada, -500, london("2026-10-14T12:00"), ex.log.phoneA)
        val flags = ex.state("2026-11-17T09:00").flags
        assertTrue(flags.contains(Flag.OrphanedLockReturn(ex.ada, EntryIds.lockPayout(ex.lockId))))
    }

    @Test
    fun `L3 - reversing the lock and its payout together is as if it never happened`() {
        val ex = L1()
        val asOf = "2026-11-23T09:00"
        val untouched = LogBuilder().apply {
            createFamily(); interestRate(0, from = "2026-08-01T00:00"); interestRate(100, from = "2026-10-12T00:00")
        }.let { log -> val ada = log.child("Ada"); log.award(ada, 1000, "2026-10-01T09:00"); Projection.project(log.ops(), london(asOf)).account(ada)!!.balance }

        ex.log.add(Locks.duePayouts(ex.state("2026-11-16T09:00")).single())
        ex.log.reverse(ex.lockId, ex.ada, -500, london("2026-10-14T12:00"), ex.log.phoneA)
        ex.log.reverse(EntryIds.lockPayout(ex.lockId), ex.ada, 541, london("2026-11-16T00:00"), ex.log.phoneA)
        val state = ex.state(asOf)
        assertEquals(untouched, state.account(ex.ada)!!.balance)
        assertTrue(state.flags.none { it is Flag.OrphanedLockReturn })
        assertEquals(emptyList<Lock>(), Locks.forChild(state, ex.ada))
    }

    @Test
    fun `new lock terms use the rate and cap in force plus the bonus`() {
        val ex = L1()
        val terms = Locks.termsNow(ex.state("2026-10-20T09:00"), weeks = 8)
        assertEquals(dev.housepoints.contracts.LockTerms(8, dev.housepoints.contracts.RateBp(200), Points(2000)), terms)
    }

    @Test
    fun `locks need at least the minimum and no more than the balance`() {
        val ex = L1()
        val state = ex.state("2026-10-20T09:00")
        assertEquals(Verdict.Denied(DenialReason.BELOW_MINIMUM), Rules.lock(state, ex.ada, Points(99)))
        assertEquals(Verdict.Denied(DenialReason.MORE_THAN_BALANCE), Rules.lock(state, ex.ada, Points(506)))
        assertEquals(Verdict.Allowed, Rules.lock(state, ex.ada, Points(505)))
    }

    @Test
    fun `R1 - a reward is spent from the balance and the catalogue is last-writer-wins`() {
        val log = LogBuilder().apply { createFamily() }
        val tom = log.child("Tom")
        val reward = RewardId(Uuids.random())
        log.add(RewardUpsert(reward, "Screen time, 30 minutes", IconKey("music"), Points(50), archived = false))
        log.award(tom, 120, "2026-10-13T09:00")
        val before = Projection.project(log.ops(), london("2026-10-14T09:00"))
        assertEquals(Verdict.Denied(DenialReason.MORE_THAN_BALANCE), Rules.redeem(before, tom, Points(121)))
        assertEquals(Verdict.Allowed, Rules.redeem(before, tom, Points(50)))
        assertEquals("Screen time, 30 minutes", before.rewards.single().title)

        log.redeem(tom, reward, 50, "Screen time, 30 minutes", "2026-10-14T10:00")
        assertEquals(Points(70), Projection.project(log.ops(), london("2026-10-14T11:00")).account(tom)!!.displayed)
    }
}
