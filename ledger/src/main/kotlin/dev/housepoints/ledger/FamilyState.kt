package dev.housepoints.ledger

import dev.housepoints.contracts.ChildId
import dev.housepoints.contracts.ChoreId
import dev.housepoints.contracts.ChoreKind
import dev.housepoints.contracts.CurrencyCode
import dev.housepoints.contracts.DeviceId
import dev.housepoints.contracts.DisplayStyle
import dev.housepoints.contracts.EntryId
import dev.housepoints.contracts.EntryRecorded
import dev.housepoints.contracts.FamilyId
import dev.housepoints.contracts.GoalId
import dev.housepoints.contracts.GoalStatus
import dev.housepoints.contracts.IconKey
import dev.housepoints.contracts.InstantMs
import dev.housepoints.contracts.Lamport
import dev.housepoints.contracts.Micropoints
import dev.housepoints.contracts.OpId
import dev.housepoints.contracts.Points
import dev.housepoints.contracts.RateBp
import dev.housepoints.contracts.Recurrence
import dev.housepoints.contracts.ValueId
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/**
 * Everything the app shows, calculated from the op log at one instant (SAD §3.2). Immutable; a new
 * [FamilyState] is produced for every change. Two devices holding the same ops and the same [asOf]
 * produce equal states (SPEC NFR-DET-1).
 */
public data class FamilyState(
    val asOf: InstantMs,
    val family: FamilySettings?,
    val devices: List<DeviceRecord>,
    val children: List<ChildRecord>,
    val chores: List<ChoreRecord>,
    val values: List<ValueRecord>,
    val goals: List<GoalRecord>,
    val ticks: Map<TickKey, Boolean>,
    val policies: PolicyTimeline,
    val accounts: Map<ChildId, Account>,
    val flags: List<Flag>,
    val rewards: List<RewardRecord>,
) {
    public fun child(id: ChildId): ChildRecord? = children.firstOrNull { it.id == id }
    public fun chore(id: ChoreId): ChoreRecord? = chores.firstOrNull { it.id == id }
    public fun value(id: ValueId): ValueRecord? = values.firstOrNull { it.id == id }
    public fun account(id: ChildId): Account? = accounts[id]

    /** The child's single active goal (SPEC FR-9): the most recently created active one. */
    public fun activeGoal(child: ChildId): GoalRecord? =
        goals.lastOrNull { it.childId == child && it.status == GoalStatus.ACTIVE }

    public val isEmpty: Boolean get() = family == null
}

public data class FamilySettings(
    val id: FamilyId,
    val name: String,
    val currency: CurrencyCode,
    val zone: ZoneId,
    val weekStart: DayOfWeek,
)

public data class DeviceRecord(val id: DeviceId, val name: String, val removed: Boolean)

public data class ChildRecord(
    val id: ChildId,
    val name: String,
    val colorIndex: Int,
    val displayStyle: DisplayStyle,
    val archived: Boolean,
)

public data class ChoreRecord(
    val id: ChoreId,
    val title: String,
    val icon: IconKey,
    val kind: ChoreKind,
    val points: Points,
    val assignees: Set<ChildId>,
    val recurrence: Recurrence,
    val archived: Boolean,
)

public data class ValueRecord(val id: ValueId, val name: String, val icon: IconKey, val archived: Boolean)

public data class GoalRecord(
    val id: GoalId,
    val childId: ChildId,
    val title: String,
    val icon: IconKey,
    val target: Points,
    val status: GoalStatus,
)

public data class RewardRecord(val id: dev.housepoints.contracts.RewardId, val title: String, val icon: IconKey, val price: Points, val archived: Boolean)

public data class TickKey(val chore: ChoreId, val child: ChildId, val day: LocalDate)

/** One canonical ledger entry and what it does to the balance. */
public data class LedgerLine(
    val entry: EntryRecorded,
    /** The change this line makes; zero for entries that are malformed or whose target is missing. */
    val effect: Points,
    val recordedBy: DeviceId,
    val lamport: Lamport,
    /** The reversal cancelling this line, when there is one in force. */
    val reversedBy: EntryId?,
)

/** A completed week (SPEC FR-24 to FR-26). */
public data class PeriodSummary(
    val start: InstantMs,
    val end: InstantMs,
    val startDate: LocalDate,
    val opening: Micropoints,
    /** Sum of positive effects inside the period. */
    val credits: Points,
    /** Sum of negative effects inside the period (zero or negative). */
    val debits: Points,
    val lowest: Micropoints,
    /** When the lowest balance was reached; null when the opening balance was the lowest. */
    val lowestAt: InstantMs?,
    val rate: RateBp,
    val cap: Points?,
    val interest: Micropoints,
    val closing: Micropoints,
) {
    /** The amount that earned interest: the lowest balance, floored at zero and capped (SPEC FR-25). */
    val base: Micropoints
        get() {
            val floored = maxOf(Micropoints.ZERO, lowest)
            val capped = cap?.toMicropoints()
            return if (capped != null && floored > capped) capped else floored
        }
}

/** The week in progress at [FamilyState.asOf]. Its interest is not paid yet (SPEC FR-28). */
public data class CurrentPeriod(
    val start: InstantMs,
    val end: InstantMs,
    val startDate: LocalDate,
    val opening: Micropoints,
    val credits: Points,
    val debits: Points,
    val lowestSoFar: Micropoints,
    val rate: RateBp,
    val cap: Points?,
    /** Interest due at [end] if nothing else is taken out before then. */
    val expectedInterest: Micropoints,
)

public data class Account(
    val childId: ChildId,
    /** Balance at [FamilyState.asOf], including interest of completed periods. */
    val balance: Micropoints,
    /** Canonical entries, newest effective instant first. */
    val lines: List<LedgerLine>,
    /** Completed periods, newest first. */
    val periods: List<PeriodSummary>,
    val current: CurrentPeriod?,
) {
    val displayed: Points get() = balance.floorPoints()
}

/** Things a parent should look at; merges never correct these automatically (SPEC FR-36). */
public sealed interface Flag {
    public data class Overdrawn(val child: ChildId, val balance: Micropoints) : Flag

    /** SPEC FR-20; [first] sorts before [second]. */
    public data class PossibleDuplicate(val child: ChildId, val first: EntryId, val second: EntryId) : Flag

    public data class MalformedEntry(val op: OpId, val reason: String) : Flag

    /** Arithmetic overflow while calculating this child's balance; the account is left out. */
    public data class Overflow(val child: ChildId) : Flag

    /** Ops this version does not understand: another phone runs a newer version (SPEC FR-39). */
    public data class NewerVersionSeen(val unknownOps: Int) : Flag

    /** SPEC FR-52: a payout or early break whose lock has been reversed while it has not. */
    public data class OrphanedLockReturn(val child: ChildId, val entry: EntryId) : Flag
}
