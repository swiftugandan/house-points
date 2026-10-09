package dev.housepoints.ledger

import dev.housepoints.contracts.ChildId
import dev.housepoints.contracts.EntryKind
import dev.housepoints.contracts.InstantMs
import dev.housepoints.contracts.Points
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GoalsAndBenchmarkTest {
    /**
     * Balance exactly 252 at Thu 22 Oct with 252 the lowest of the current week: interest is switched off until
     * that week so earlier paydays do not move the starting point of the independent simulation.
     */
    private fun steadyAda(paceWeeks: Boolean): Pair<FamilyState, ChildId> {
        val log = LogBuilder().apply { createFamily() }
        log.interestRate(0, from = "2026-08-01T00:00")
        log.interestRate(100, from = "2026-10-19T00:00")
        val ada = log.child("Ada")
        if (paceWeeks) {
            // +75 every week for the four completed weeks before the current one
            log.award(ada, 252, "2026-09-01T09:00")
            listOf("2026-09-22", "2026-09-29", "2026-10-06", "2026-10-13").forEach { log.chorePaid(ada, 75, "${it}T09:00") }
        } else {
            log.award(ada, 252, "2026-10-01T09:00")
        }
        return Projection.project(log.ops(), london("2026-10-22T12:00")) to ada
    }

    @Test
    fun `interest-only projection simulates the real rules week by week`() {
        val (state, ada) = steadyAda(paceWeeks = false)
        val result = Projections.toTarget(state, ada, Points(1200), Pace.INTEREST_ONLY)
        // Expected from an independent µpt simulation: 252 needs 157 paydays at 1%/week to reach 1,200.
        assertEquals(ProjectionResult.ReachedOn(LocalDate.of(2029, 10, 22), 157), result)
    }

    @Test
    fun `at-pace projection uses the mean of the last four completed weeks`() {
        val (state, ada) = steadyAda(paceWeeks = true)
        assertEquals(Points(75), Projections.pace(state, ada))
        assertTrue(Projections.toTarget(state, ada, Points(1200), Pace.RECENT) is ProjectionResult.ReachedOn)
    }

    @Test
    fun `no completed weeks means no pace yet`() {
        val log = LogBuilder().apply { createFamily() }
        val ada = log.child("Ada")
        log.award(ada, 10, "2026-10-20T09:00")
        val state = Projection.project(log.ops(), london("2026-10-22T12:00"))
        assertEquals(ProjectionResult.NotYet, Projections.toTarget(state, ada, Points(1200), Pace.RECENT))
    }

    @Test
    fun `what-if-I-wait counts interest only`() {
        val (state, ada) = steadyAda(paceWeeks = false)
        assertEquals(Points(326), Projections.afterWeeks(state, ada, 26))
    }

    @Test
    fun `sizing dataset recalculates within budget (NFR-PERF-1)`() {
        val log = LogBuilder().apply { createFamily() }
        val children = List(8) { log.child("Child $it", colorIndex = it) }
        val start = london("2016-10-03T00:00").value
        val week = 7L * 24 * 3_600_000
        for (w in 0 until 520) {
            for (child in children) {
                repeat(40) { i ->
                    val at = InstantMs(start + w * week + i * 3_600_000L * 4)
                    if (i % 10 == 9) log.entry(child, EntryKind.CASH_OUT, -5, at)
                    else log.entry(child, EntryKind.CHORE, 3, at)
                }
            }
        }
        val ops = log.ops()
        val asOf = InstantMs(start + 520 * week)
        Projection.project(ops, asOf) // warm-up
        val began = System.nanoTime()
        val state = Projection.project(ops, asOf)
        val millis = (System.nanoTime() - began) / 1_000_000
        println("NFR-PERF-1: ${ops.size} ops projected in $millis ms on the JVM")
        assertEquals(8, state.accounts.size)
        assertTrue("projection took $millis ms", millis < 3_000)
    }
}
