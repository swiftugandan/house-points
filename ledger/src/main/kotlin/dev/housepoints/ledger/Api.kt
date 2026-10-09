package dev.housepoints.ledger

import dev.housepoints.contracts.ChildId
import dev.housepoints.contracts.DeviceId
import dev.housepoints.contracts.InstantMs
import dev.housepoints.contracts.Lamport
import dev.housepoints.contracts.MinorUnits
import dev.housepoints.contracts.Op
import dev.housepoints.contracts.Points
import dev.housepoints.contracts.Policy
import java.time.LocalDate

public object Projection {
    public fun project(ops: Collection<Op>, asOf: InstantMs): FamilyState = TODO("green")
}

public data class PolicyChange<P : Policy>(
    val policy: P,
    val effectiveFrom: InstantMs,
    val recordedBy: DeviceId,
    val lamport: Lamport,
    val superseded: Boolean,
)

public class PolicyTimeline {
    public fun interestHistory(): List<PolicyChange<Policy.Interest>> = TODO("green")
}

public sealed interface Verdict {
    public data object Allowed : Verdict
    public data class Denied(val reason: DenialReason) : Verdict
}

public sealed interface CashOutVerdict {
    public data class Allowed(val money: MinorUnits) : CashOutVerdict
    public data class Denied(val reason: DenialReason) : CashOutVerdict
}

public enum class DenialReason { PENALTIES_OFF, MORE_THAN_THIS_WEEK, MORE_THAN_BALANCE, BELOW_MINIMUM, NOT_CONVERTIBLE, NO_FAMILY, NOT_POSITIVE }

public object Rules {
    public fun maxDeduction(state: FamilyState, child: ChildId): Points = TODO("green")
    public fun deduction(state: FamilyState, child: ChildId, amount: Points): Verdict = TODO("green")
    public fun cashOut(state: FamilyState, child: ChildId, amount: Points): CashOutVerdict = TODO("green")
}

public data class DueChore(val chore: ChoreRecord, val occurrence: LocalDate?)

public object Chores {
    public fun dueFor(state: FamilyState, child: ChildId, today: LocalDate): List<DueChore> = TODO("green")
    public fun bounties(state: FamilyState): List<ChoreRecord> = TODO("green")
}

public enum class Pace { INTEREST_ONLY, RECENT }

public sealed interface ProjectionResult {
    public data object AlreadyReached : ProjectionResult
    public data class ReachedOn(val payday: LocalDate, val paydays: Int) : ProjectionResult
    public data object MoreThanTenYears : ProjectionResult
    public data object NotAtCurrentPace : ProjectionResult
    public data object NotYet : ProjectionResult
}

public object Projections {
    public fun pace(state: FamilyState, child: ChildId): Points? = TODO("green")
    public fun toTarget(state: FamilyState, child: ChildId, target: Points, pace: Pace): ProjectionResult = TODO("green")
    public fun afterWeeks(state: FamilyState, child: ChildId, weeks: Int): Points = TODO("green")
}
