package dev.housepoints.contracts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/**
 * What an [Op] says happened. Each concrete payload has a stable [PayloadType] string; the JSON body is
 * stored and forwarded byte-for-byte, so a device that does not understand a type still keeps it (SPEC FR-39).
 *
 * Upsert payloads are per-field last-writer-wins registers (SPEC FR-35): a non-null field is a write of that
 * field, a null field is "not written by this op".
 */
public sealed interface Payload

/** Payload type identifiers. Never rename one: they are stored in every log. */
public object PayloadType {
    public const val FAMILY_CREATED: String = "family.created"
    public const val DEVICE_UPSERT: String = "device.upsert"
    public const val DEVICE_REMOVED: String = "device.removed"
    public const val CHILD_UPSERT: String = "child.upsert"
    public const val CHORE_UPSERT: String = "chore.upsert"
    public const val VALUE_UPSERT: String = "value.upsert"
    public const val GOAL_UPSERT: String = "goal.upsert"
    public const val ENTRY_RECORDED: String = "ledger.entry"
    public const val POLICY_SET: String = "policy.set"
    public const val TICK_SET: String = "tick.set"

    /** contracts 0.2.0. */
    public const val REWARD_UPSERT: String = "reward.upsert"

    /** contracts 0.2.0. */
    public const val LOCK_POLICY_SET: String = "policy.lock"
}

@Serializable
public data class FamilyCreated(
    val name: String,
    val currency: CurrencyCode,
    @Serializable(with = ZoneIdSerializer::class) val zone: ZoneId,
    @Serializable(with = DayOfWeekSerializer::class) val weekStart: DayOfWeek,
) : Payload

@Serializable
public data class DeviceUpsert(
    val deviceId: DeviceId,
    val name: String? = null,
) : Payload

@Serializable
public data class DeviceRemoved(val deviceId: DeviceId) : Payload

@Serializable
public data class ChildUpsert(
    val childId: ChildId,
    val name: String? = null,
    val colorIndex: Int? = null,
    val displayStyle: DisplayStyle? = null,
    val archived: Boolean? = null,
) : Payload

@Serializable
public sealed interface Recurrence {
    @Serializable @SerialName("daily")
    public data object Daily : Recurrence

    @Serializable @SerialName("weekdays")
    public data class Weekdays(val days: Set<@Serializable(with = DayOfWeekSerializer::class) DayOfWeek>) : Recurrence

    /** Once per period; the occurrence date is the period's start date (SPEC FR-18). */
    @Serializable @SerialName("weekly")
    public data object Weekly : Recurrence

    @Serializable @SerialName("once")
    public data object Once : Recurrence
}

@Serializable
public data class ChoreUpsert(
    val choreId: ChoreId,
    val title: String? = null,
    val icon: IconKey? = null,
    val kind: ChoreKind? = null,
    val points: Points? = null,
    val assignees: Set<ChildId>? = null,
    val recurrence: Recurrence? = null,
    val archived: Boolean? = null,
) : Payload

@Serializable
public data class ValueUpsert(
    val valueId: ValueId,
    val name: String? = null,
    val icon: IconKey? = null,
    val archived: Boolean? = null,
) : Payload

@Serializable
public data class GoalUpsert(
    val goalId: GoalId,
    val childId: ChildId? = null,
    val title: String? = null,
    val icon: IconKey? = null,
    val target: Points? = null,
    val status: GoalStatus? = null,
) : Payload

/** Which occurrence of a chore an entry pays for. `occurrence` is null for bounty and once chores. */
@Serializable
public data class ChoreRef(
    val choreId: ChoreId,
    @Serializable(with = LocalDateSerializer::class) val occurrence: LocalDate? = null,
)

/** The money handed over for a cash-out, at the rate in force (SPEC FR-14). */
@Serializable
public data class CashOut(
    val money: MinorUnits,
    val currency: CurrencyCode,
    val rate: ExchangeRate,
)

/**
 * One ledger entry (SPEC FR-11 to FR-17). [points] is signed: positive for CHORE and AWARD, negative for
 * DEDUCTION and CASH_OUT, non-zero for ADJUSTMENT. A REVERSAL's effect is always the negation of its target,
 * whatever [points] says; [points] is kept for the record.
 */
@Serializable
public data class EntryRecorded(
    val entryId: EntryId,
    val childId: ChildId,
    val kind: EntryKind,
    val points: Points,
    val effectiveAt: InstantMs,
    val note: String = "",
    val chore: ChoreRef? = null,
    val valueId: ValueId? = null,
    val reverses: EntryId? = null,
    val cashOut: CashOut? = null,
    /** contracts 0.2.0, SPEC FR-47: on the adjustment that locks points away. */
    val lock: LockTerms? = null,
    /** contracts 0.2.0, SPEC FR-50/51: on the adjustment that returns a lock's points (payout or early break). */
    val lockPayout: EntryId? = null,
    /** contracts 0.2.0, SPEC FR-55: on the adjustment that redeems a reward. */
    val rewardId: RewardId? = null,
) : Payload

/** A lock's frozen terms (SPEC FR-47): the payout depends on these and the lock entry alone. */
@Serializable
public data class LockTerms(val weeks: Int, val rate: RateBp, val cap: Points?)

/** contracts 0.2.0, SPEC FR-54. Per-field last-writer-wins, like the other upserts. */
@Serializable
public data class RewardUpsert(
    val rewardId: RewardId,
    val title: String? = null,
    val icon: IconKey? = null,
    val price: Points? = null,
    val archived: Boolean? = null,
) : Payload

/** contracts 0.2.0, SPEC FR-48: the bonus on top of the interest rate for new locks, from [effectiveFrom]. */
@Serializable
public data class LockPolicySet(val bonus: RateBp, val effectiveFrom: InstantMs) : Payload

@Serializable
public sealed interface Policy {
    @Serializable @SerialName("exchange")
    public data class Exchange(val rate: ExchangeRate) : Policy

    /** [cap] null means no cap. */
    @Serializable @SerialName("interest")
    public data class Interest(val rate: RateBp, val cap: Points?) : Policy

    @Serializable @SerialName("penalty")
    public data class Penalty(val mode: PenaltyMode) : Policy

    @Serializable @SerialName("minCashOut")
    public data class MinCashOut(val minimum: Points) : Policy
}

/** Sets a policy from [effectiveFrom] onward (SPEC FR-31). */
@Serializable
public data class PolicySet(
    val policy: Policy,
    val effectiveFrom: InstantMs,
) : Payload

/** Expected-chore tick (SPEC FR-7); last writer wins per (chore, child, day). */
@Serializable
public data class TickSet(
    val choreId: ChoreId,
    val childId: ChildId,
    @Serializable(with = LocalDateSerializer::class) val day: LocalDate,
    val done: Boolean,
) : Payload

/** A payload this version does not understand. Kept verbatim and forwarded in syncs (SPEC FR-39). */
public data class UnknownPayload(val type: String, val json: String) : Payload
