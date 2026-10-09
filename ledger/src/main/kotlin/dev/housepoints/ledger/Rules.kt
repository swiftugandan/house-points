package dev.housepoints.ledger

import dev.housepoints.contracts.ChildId
import dev.housepoints.contracts.EntryKind
import dev.housepoints.contracts.MinorUnits
import dev.housepoints.contracts.PenaltyMode
import dev.housepoints.contracts.Points

public sealed interface Verdict {
    public data object Allowed : Verdict
    public data class Denied(val reason: DenialReason) : Verdict
}

public sealed interface CashOutVerdict {
    public data class Allowed(val money: MinorUnits) : CashOutVerdict
    public data class Denied(val reason: DenialReason) : CashOutVerdict
}

public enum class DenialReason {
    NO_FAMILY,
    NOT_POSITIVE,
    PENALTIES_OFF,
    MORE_THAN_THIS_WEEK,
    MORE_THAN_BALANCE,
    BELOW_MINIMUM,
    NOT_CONVERTIBLE,
}

/**
 * Checks a phone makes before it records something (SPEC FR-13, FR-14). They read the recording phone's
 * view only and are never applied during a merge (SPEC FR-36).
 */
public object Rules {
    public fun maxDeduction(state: FamilyState, child: ChildId): Points {
        val account = state.account(child) ?: return Points.ZERO
        val balance = maxOf(Points.ZERO, account.displayed)
        return when (state.policies.penaltyAt(state.asOf)) {
            PenaltyMode.NONE -> Points.ZERO
            PenaltyMode.ANY -> balance
            PenaltyMode.CURRENT_WEEK -> minOf(balance, maxOf(Points.ZERO, thisWeeksAllowance(account)))
        }
    }

    public fun deduction(state: FamilyState, child: ChildId, amount: Points): Verdict {
        if (state.family == null) return Verdict.Denied(DenialReason.NO_FAMILY)
        if (amount <= Points.ZERO) return Verdict.Denied(DenialReason.NOT_POSITIVE)
        val mode = state.policies.penaltyAt(state.asOf)
        if (mode == PenaltyMode.NONE) return Verdict.Denied(DenialReason.PENALTIES_OFF)
        if (amount <= maxDeduction(state, child)) return Verdict.Allowed
        val balance = state.account(child)?.displayed ?: Points.ZERO
        val reason = if (mode == PenaltyMode.CURRENT_WEEK && amount <= balance) {
            DenialReason.MORE_THAN_THIS_WEEK
        } else {
            DenialReason.MORE_THAN_BALANCE
        }
        return Verdict.Denied(reason)
    }

    public fun cashOut(state: FamilyState, child: ChildId, amount: Points): CashOutVerdict {
        if (state.family == null) return CashOutVerdict.Denied(DenialReason.NO_FAMILY)
        if (amount <= Points.ZERO) return CashOutVerdict.Denied(DenialReason.NOT_POSITIVE)
        if (amount < state.policies.minCashOutAt(state.asOf)) return CashOutVerdict.Denied(DenialReason.BELOW_MINIMUM)
        val available = state.account(child)?.displayed ?: Points.ZERO
        if (amount > available) return CashOutVerdict.Denied(DenialReason.MORE_THAN_BALANCE)
        val money = state.policies.exchangeAt(state.asOf)?.moneyFor(amount)
            ?: return CashOutVerdict.Denied(DenialReason.NOT_CONVERTIBLE)
        return CashOutVerdict.Allowed(money)
    }

    /** SPEC FR-47: at least [Locks.MINIMUM], at most what is in the account. */
    public fun lock(state: FamilyState, child: ChildId, amount: Points): Verdict {
        if (state.family == null) return Verdict.Denied(DenialReason.NO_FAMILY)
        if (amount < Locks.MINIMUM) return Verdict.Denied(DenialReason.BELOW_MINIMUM)
        val available = state.account(child)?.displayed ?: Points.ZERO
        return if (amount > available) Verdict.Denied(DenialReason.MORE_THAN_BALANCE) else Verdict.Allowed
    }

    /** SPEC FR-55: like a cash-out against the balance, with no minimum. */
    public fun redeem(state: FamilyState, child: ChildId, price: Points): Verdict {
        if (state.family == null) return Verdict.Denied(DenialReason.NO_FAMILY)
        if (price <= Points.ZERO) return Verdict.Denied(DenialReason.NOT_POSITIVE)
        val available = state.account(child)?.displayed ?: Points.ZERO
        return if (price > available) Verdict.Denied(DenialReason.MORE_THAN_BALANCE) else Verdict.Allowed
    }

    /** Chore credits plus awards in the current week, less deductions already recorded this week. */
    private fun thisWeeksAllowance(account: Account): Points {
        val current = account.current ?: return Points.ZERO
        val inWeek = account.lines.filter {
            it.reversedBy == null && it.entry.effectiveAt >= current.start && it.entry.effectiveAt < current.end
        }
        val earned = inWeek.filter { it.entry.kind == EntryKind.CHORE || it.entry.kind == EntryKind.AWARD }
            .fold(Points.ZERO) { sum, line -> sum + line.effect }
        val deducted = inWeek.filter { it.entry.kind == EntryKind.DEDUCTION }
            .fold(Points.ZERO) { sum, line -> sum + line.effect }
        return earned + deducted
    }
}
