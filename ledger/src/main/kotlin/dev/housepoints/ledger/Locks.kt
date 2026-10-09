package dev.housepoints.ledger

import dev.housepoints.contracts.ChildId
import dev.housepoints.contracts.EntryId
import dev.housepoints.contracts.EntryIds
import dev.housepoints.contracts.EntryKind
import dev.housepoints.contracts.EntryRecorded
import dev.housepoints.contracts.InstantMs
import dev.housepoints.contracts.LockTerms
import dev.housepoints.contracts.Micropoints
import dev.housepoints.contracts.Points
import dev.housepoints.contracts.RateBp

public enum class LockStatus { LOCKED, DUE, PAID, BROKEN }

/**
 * One locked deposit (SPEC FR-47 to FR-53). Everything here is a pure function of the lock entry and its
 * frozen terms, so every phone computes the same pot and payout whatever else it has synced.
 */
public data class Lock(
    val lockId: EntryId,
    val child: ChildId,
    val principal: Points,
    val terms: LockTerms,
    val earnsFrom: InstantMs,
    val maturity: InstantMs,
    val payout: Points,
    val status: LockStatus,
    /** Pot balance after each paid week: index 0 is the principal. */
    val pot: List<Micropoints>,
    /** The payday ending each pot week, in order. */
    val paydays: List<InstantMs>,
) {
    /** The pot as it stands at [t]: principal plus interest for the pot weeks completed by then. */
    public fun potAt(t: InstantMs): Micropoints = pot[paydays.count { it <= t }]

    /** Interest the pot will have earned by maturity. */
    public val interest: Points get() = payout - principal
}

public object Locks {
    /** SPEC FR-47: the smallest amount that can be locked. */
    public val MINIMUM: Points = Points(100)

    /** Locks in force for [child] (reversed locks are gone, as if never made), oldest first. */
    public fun forChild(state: FamilyState, child: ChildId): List<Lock> {
        val family = state.family ?: return emptyList()
        val lines = state.account(child)?.lines.orEmpty()
        val periods = Periods(family.zone, family.weekStart)
        val returns = lines.filter { it.reversedBy == null && it.entry.lockPayout != null }.groupBy { it.entry.lockPayout }
        return lines
            .filter { it.reversedBy == null && it.entry.lock != null && it.entry.kind == EntryKind.ADJUSTMENT && it.effect < Points.ZERO }
            .sortedBy { it.entry.effectiveAt }
            .map { line -> lockOf(line, periods, returns[line.entry.entryId].orEmpty(), state.asOf) }
    }

    /** Payouts that have matured and are not yet recorded (SPEC FR-50); the app records these. */
    public fun duePayouts(state: FamilyState): List<EntryRecorded> =
        state.children.flatMap { child -> forChild(state, child.id) }
            .filter { it.status == LockStatus.DUE }
            .map { lock ->
                EntryRecorded(
                    entryId = EntryIds.lockPayout(lock.lockId),
                    childId = lock.child,
                    kind = EntryKind.ADJUSTMENT,
                    points = lock.payout,
                    effectiveAt = lock.maturity,
                    note = "Locked savings back, with ${lock.interest.value} interest",
                    lockPayout = lock.lockId,
                )
            }

    /** Terms for a lock made now (SPEC FR-47, FR-48): today's rate plus the bonus, today's cap. */
    public fun termsNow(state: FamilyState, weeks: Int): LockTerms {
        val interest = state.policies.interestAt(state.asOf)
        val bonus = state.policies.lockBonusAt(state.asOf)
        return LockTerms(weeks, RateBp((interest?.rate?.value ?: 0) + bonus.value), interest?.cap)
    }

    /** SPEC FR-52: returns (payout or early break) still in force whose lock has been reversed. */
    internal fun orphanedReturns(accounts: Map<ChildId, Account>): List<Flag.OrphanedLockReturn> =
        accounts.flatMap { (child, account) ->
            val reversedLocks = account.lines.filter { it.entry.lock != null && it.reversedBy != null }.map { it.entry.entryId }.toSet()
            account.lines
                .filter { it.reversedBy == null && it.entry.lockPayout in reversedLocks }
                .map { Flag.OrphanedLockReturn(child, it.entry.entryId) }
        }.sortedBy { it.entry }

    private fun lockOf(line: LedgerLine, periods: Periods, returns: List<LedgerLine>, asOf: InstantMs): Lock {
        val terms = requireNotNull(line.entry.lock)
        val principal = -line.effect
        var period = periods.next(periods.containing(line.entry.effectiveAt))
        val earnsFrom = period.start
        val pot = ArrayList<Micropoints>(terms.weeks + 1).apply { add(principal.toMicropoints()) }
        val paydays = ArrayList<InstantMs>(terms.weeks)
        repeat(terms.weeks.coerceAtLeast(0)) {
            val balance = pot.last()
            pot += balance + Interest.on(balance, terms.rate, terms.cap)
            paydays += period.end
            period = periods.next(period)
        }
        val maturity = paydays.lastOrNull() ?: earnsFrom
        val status = when {
            returns.any { it.entry.entryId == EntryIds.lockPayout(line.entry.entryId) } -> LockStatus.PAID
            returns.isNotEmpty() -> LockStatus.BROKEN
            maturity <= asOf -> LockStatus.DUE
            else -> LockStatus.LOCKED
        }
        return Lock(line.entry.entryId, line.entry.childId, principal, terms, earnsFrom, maturity, pot.last().floorPoints(), status, pot, paydays)
    }
}
