package dev.housepoints.ledger

import dev.housepoints.contracts.MinorUnits
import dev.housepoints.contracts.PenaltyMode
import dev.housepoints.contracts.Points
import org.junit.Assert.assertEquals
import org.junit.Test

/** SPEC FR-13 and FR-14: checked only on the recording phone, never during a merge. */
class RulesTest {
    private fun stateWith(mode: PenaltyMode, block: LogBuilder.(dev.housepoints.contracts.ChildId) -> Unit): Pair<FamilyState, dev.housepoints.contracts.ChildId> {
        val log = LogBuilder().apply { createFamily() }
        log.penalty(mode, from = london("2026-01-01T00:00"))
        val ada = log.child("Ada")
        log.block(ada)
        return Projection.project(log.ops(), london("2026-10-15T12:00")) to ada
    }

    @Test
    fun `no deductions when penalties are off`() {
        val (state, ada) = stateWith(PenaltyMode.NONE) { award(it, 100, "2026-10-13T09:00") }
        assertEquals(Verdict.Denied(DenialReason.PENALTIES_OFF), Rules.deduction(state, ada, Points(10)))
    }

    @Test
    fun `current-week mode allows up to this week's earnings less this week's deductions`() {
        val (state, ada) = stateWith(PenaltyMode.CURRENT_WEEK) {
            award(it, 500, "2026-10-06T09:00")
            chorePaid(it, 40, "2026-10-13T09:00")
            award(it, 20, "2026-10-14T09:00")
            entry(it, dev.housepoints.contracts.EntryKind.DEDUCTION, -15, london("2026-10-14T10:00"), note = "rude")
        }
        assertEquals(Points(45), Rules.maxDeduction(state, ada))
        assertEquals(Verdict.Allowed, Rules.deduction(state, ada, Points(45)))
        assertEquals(Verdict.Denied(DenialReason.MORE_THAN_THIS_WEEK), Rules.deduction(state, ada, Points(46)))
    }

    @Test
    fun `any mode allows up to the balance`() {
        val (state, ada) = stateWith(PenaltyMode.ANY) { award(it, 120, "2026-10-06T09:00") }
        assertEquals(Points(120), Rules.maxDeduction(state, ada))
        assertEquals(Verdict.Allowed, Rules.deduction(state, ada, Points(120)))
        assertEquals(Verdict.Denied(DenialReason.MORE_THAN_BALANCE), Rules.deduction(state, ada, Points(121)))
    }

    @Test
    fun `cash-out needs the minimum, the balance and an exact conversion`() {
        val (state, ada) = stateWith(PenaltyMode.CURRENT_WEEK) { award(it, 250, "2026-10-06T09:00") }
        assertEquals(CashOutVerdict.Allowed(MinorUnits(150)), Rules.cashOut(state, ada, Points(150)))
        assertEquals(CashOutVerdict.Denied(DenialReason.BELOW_MINIMUM), Rules.cashOut(state, ada, Points(99)))
        assertEquals(CashOutVerdict.Denied(DenialReason.MORE_THAN_BALANCE), Rules.cashOut(state, ada, Points(251)))
    }
}
