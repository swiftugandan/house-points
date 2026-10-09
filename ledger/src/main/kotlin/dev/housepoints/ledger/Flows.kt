package dev.housepoints.ledger

import dev.housepoints.contracts.EntryKind
import dev.housepoints.contracts.EntryRecorded
import dev.housepoints.contracts.InstantMs
import dev.housepoints.contracts.Points

/** True for a lock and anything returning one (payout or early break): a transfer, not earning or spending (SPEC FR-53). */
public val EntryRecorded.movesLockedSavings: Boolean get() = lock != null || lockPayout != null

/**
 * What moved through an account in `[from, until)`, as a person reads it. Uses the same in-force rule as
 * [PeriodSummary.credits] and [PeriodSummary.debits], so `earned + spent + locked == credits + debits`.
 */
public data class Flows(val earned: Points, val spent: Points, val locked: Points) {
    public companion object {
        public fun of(account: Account, from: InstantMs, until: InstantMs): Flows =
            account.lines
                .filter { it.entry.kind != EntryKind.REVERSAL && it.reversedBy == null && it.entry.effectiveAt >= from && it.entry.effectiveAt < until }
                .fold(Flows(Points.ZERO, Points.ZERO, Points.ZERO)) { flows, line ->
                    when {
                        line.entry.movesLockedSavings -> flows.copy(locked = flows.locked + line.effect)
                        line.effect > Points.ZERO -> flows.copy(earned = flows.earned + line.effect)
                        else -> flows.copy(spent = flows.spent + line.effect)
                    }
                }
    }
}
