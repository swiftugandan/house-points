package dev.housepoints.ledger

import dev.housepoints.contracts.ChildId
import dev.housepoints.contracts.EntryId
import dev.housepoints.contracts.Micropoints
import dev.housepoints.contracts.Points
import dev.housepoints.contracts.RateBp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** SPEC "Worked examples": normative; each one is an acceptance test of the ledger. */
class SpecExamplesTest {
    private class Example1 {
        val log = LogBuilder().apply { createFamily() }
        val ada: ChildId = log.child("Ada")
        val awardE: EntryId

        init {
            log.chorePaid(ada, 200, "2026-10-05T17:00")
            awardE = log.award(ada, 100, "2026-10-07T18:00")
            log.cashOut(ada, 50, "2026-10-09T16:00")
            log.chorePaid(ada, 100, "2026-10-13T17:00")
            log.cashOut(ada, 150, "2026-10-15T16:00")
        }
    }

    private fun account(log: LogBuilder, child: ChildId, asOf: String): Account =
        Projection.project(log.ops(), london(asOf)).account(child)!!

    private fun closingByWeek(account: Account): Map<LocalDate, Micropoints> =
        account.periods.associate { it.startDate to it.closing }

    @Test
    fun `example 1 - compounding and the lowest-balance rule`() {
        val ex = Example1()
        val account = account(ex.log, ex.ada, "2026-11-02T00:00")
        val weeks = account.periods.sortedBy { it.start }
        assertEquals(4, weeks.size)

        assertEquals(Micropoints.ZERO, weeks[0].lowest)
        assertEquals(Micropoints.ZERO, weeks[0].interest)
        assertEquals(micro("250"), weeks[0].closing)

        assertEquals(micro("200"), weeks[1].lowest)
        assertEquals(micro("2"), weeks[1].interest)
        assertEquals(micro("202"), weeks[1].closing)

        assertEquals(micro("2.02"), weeks[2].interest)
        assertEquals(micro("204.02"), weeks[2].closing)

        assertEquals(micro("2.0402"), weeks[3].interest)
        assertEquals(micro("206.0602"), weeks[3].closing)
        assertEquals(Points(206), account.displayed)
    }

    @Test
    fun `example 1 - week 3 spans the clocks going back and is 169 hours`() {
        val ex = Example1()
        val week3 = account(ex.log, ex.ada, "2026-11-02T00:00").periods.single { it.startDate == LocalDate.of(2026, 10, 19) }
        assertEquals(169L * 3_600_000L, week3.end.value - week3.start.value)
    }

    @Test
    fun `example 2 - a late sync lowers a past week's interest`() {
        val ex = Example1()
        val beforeSync = account(ex.log, ex.ada, "2026-10-20T12:00")
        assertEquals(Points(202), beforeSync.displayed)

        ex.log.cashOut(ex.ada, 100, "2026-10-14T12:00", device = ex.log.phoneB)
        val afterSync = account(ex.log, ex.ada, "2026-10-20T12:00")
        val week2 = afterSync.periods.single { it.startDate == LocalDate.of(2026, 10, 12) }
        assertEquals(micro("100"), week2.lowest)
        assertEquals(micro("1"), week2.interest)
        assertEquals(Points(101), afterSync.displayed)
    }

    @Test
    fun `example 3 - both parents reverse the same award and it counts once`() {
        val ex = Example1()
        val at = london("2026-10-07T18:00")
        ex.log.reverse(ex.awardE, ex.ada, 100, at, ex.log.phoneA, lamport = 30)
        ex.log.reverse(ex.awardE, ex.ada, 100, at, ex.log.phoneB, lamport = 31)

        val account = account(ex.log, ex.ada, "2026-10-19T00:00")
        val closing = closingByWeek(account)
        assertEquals(micro("150"), closing[LocalDate.of(2026, 10, 5)])
        assertEquals(micro("101"), closing[LocalDate.of(2026, 10, 12)])
        val award = account.lines.single { it.entry.entryId == ex.awardE }
        assertEquals(dev.housepoints.contracts.EntryIds.reversal(ex.awardE), award.reversedBy)
    }

    @Test
    fun `example 4 - a mid-week rate change applies from the next week`() {
        val ex = Example1()
        ex.log.interestRate(200, from = "2026-10-21T00:00")
        val weeks = account(ex.log, ex.ada, "2026-11-02T00:00").periods.associateBy { it.startDate }
        assertEquals(RateBp(100), weeks.getValue(LocalDate.of(2026, 10, 19)).rate)
        assertEquals(micro("2.02"), weeks.getValue(LocalDate.of(2026, 10, 19)).interest)
        assertEquals(RateBp(200), weeks.getValue(LocalDate.of(2026, 10, 26)).rate)
        assertEquals(micro("4.0804"), weeks.getValue(LocalDate.of(2026, 10, 26)).interest)
        assertEquals(micro("208.1006"), weeks.getValue(LocalDate.of(2026, 10, 26)).closing)
    }

    @Test
    fun `example 5 - both parents pay out the same savings`() {
        val ex = Example1()
        ex.log.cashOut(ex.ada, 200, "2026-10-24T10:00", device = ex.log.phoneA, lamport = 50)
        ex.log.cashOut(ex.ada, 200, "2026-10-24T15:00", device = ex.log.phoneB, lamport = 50)
        val state = Projection.project(ex.log.ops(), london("2026-11-02T00:00"))
        val account = state.account(ex.ada)!!
        val week3 = account.periods.single { it.startDate == LocalDate.of(2026, 10, 19) }
        assertEquals(micro("-198"), week3.lowest)
        assertEquals(Micropoints.ZERO, week3.interest)
        assertEquals(micro("-198"), account.balance)
        assertTrue(state.flags.contains(Flag.Overdrawn(ex.ada, micro("-198"))))
    }

    @Test
    fun `example 6 - the cap limits the interest base`() {
        val log = LogBuilder().apply { createFamily() }
        val ada = log.child("Ada")
        log.award(ada, 3000, "2026-10-01T09:00")
        val week = account(log, ada, "2026-10-12T00:00").periods.single { it.startDate == LocalDate.of(2026, 10, 5) }
        assertEquals(micro("20"), week.interest)
    }

    @Test
    fun `example 7 - entries at the same instant apply as one net change`() {
        val log = LogBuilder().apply { createFamily() }
        val ada = log.child("Ada")
        log.award(ada, 300, "2026-10-01T09:00")
        log.cashOut(ada, 300, "2026-10-08T12:00")
        log.award(ada, 300, "2026-10-08T12:00")
        val week = account(log, ada, "2026-10-12T00:00").periods.single { it.startDate == LocalDate.of(2026, 10, 5) }
        assertEquals(micro("300"), week.lowest)
        assertEquals(micro("3"), week.interest)
    }

    @Test
    fun `example 8 - the later lamport wins between two policies for the same instant`() {
        val ex = Example1()
        ex.log.interestRate(150, from = "2026-11-02T00:00", device = ex.log.phoneA, lamport = 40)
        ex.log.interestRate(50, from = "2026-11-02T00:00", device = ex.log.phoneB, lamport = 42)
        val state = Projection.project(ex.log.ops(), london("2026-11-09T00:00"))
        val week5 = state.account(ex.ada)!!.periods.single { it.startDate == LocalDate.of(2026, 11, 2) }
        assertEquals(RateBp(50), week5.rate)
        val history = state.policies.interestHistory()
        assertTrue(history.any { it.policy.rate == RateBp(150) && it.superseded })
    }
}
