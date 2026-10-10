package dev.housepoints.app.family

import dev.housepoints.contracts.CashOut
import dev.housepoints.contracts.ChildId
import dev.housepoints.contracts.ChildUpsert
import dev.housepoints.contracts.ChoreId
import dev.housepoints.contracts.ChoreKind
import dev.housepoints.contracts.ChoreRef
import dev.housepoints.contracts.ChoreUpsert
import dev.housepoints.contracts.CurrencyCode
import dev.housepoints.contracts.DeviceId
import dev.housepoints.contracts.DeviceUpsert
import dev.housepoints.contracts.DisplayStyle
import dev.housepoints.contracts.EntryIds
import dev.housepoints.contracts.EntryKind
import dev.housepoints.contracts.EntryRecorded
import dev.housepoints.contracts.ExchangeRate
import dev.housepoints.contracts.FamilyCreated
import dev.housepoints.contracts.GoalId
import dev.housepoints.contracts.GoalStatus
import dev.housepoints.contracts.GoalUpsert
import dev.housepoints.contracts.IconKey
import dev.housepoints.contracts.InstantMs
import dev.housepoints.contracts.Payload
import dev.housepoints.contracts.PenaltyMode
import dev.housepoints.contracts.Points
import dev.housepoints.contracts.Policy
import dev.housepoints.contracts.PolicySet
import dev.housepoints.contracts.RateBp
import dev.housepoints.contracts.Recurrence
import dev.housepoints.contracts.TickSet
import dev.housepoints.contracts.Uuids
import dev.housepoints.contracts.ValueId
import dev.housepoints.contracts.ValueUpsert
import dev.housepoints.ledger.CashOutVerdict
import dev.housepoints.ledger.DenialReason
import dev.housepoints.ledger.DueChore
import dev.housepoints.ledger.FamilyState
import dev.housepoints.ledger.LedgerLine
import dev.housepoints.ledger.Lock
import dev.housepoints.ledger.Locks
import dev.housepoints.ledger.RewardRecord
import dev.housepoints.contracts.LockPolicySet
import dev.housepoints.contracts.RewardId
import dev.housepoints.contracts.RewardUpsert
import dev.housepoints.ledger.Rules
import dev.housepoints.ledger.Verdict
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/** What a parent asked for, turned into the payloads to record, or the reason it cannot be recorded. */
sealed interface Action {
    data class Record(val payloads: List<Payload>) : Action
    data class Refused(val reason: Refusal) : Action
}

/** Why an action was not recorded; [Rule] carries the ledger's own recording rules (SPEC FR-13, FR-14). */
sealed interface Refusal {
    data class Rule(val reason: DenialReason) : Refusal
    data object NoteRequired : Refusal
    data object NoChildSelected : Refusal
    data object AmountMustBePositive : Refusal
    data object AmountMustNotBeZero : Refusal
    data object UnknownChore : Refusal
    data object AlreadyReversed : Refusal
    data object AlreadyRecorded : Refusal

    /** SPEC FR-52: a lock and its payout or break are only reversed together. */
    data object LockHasReturns : Refusal
    data object InvalidPolicy : Refusal
}

/**
 * Pure translation from parent intent to payloads (SPEC FR-1 to FR-17). The checks that apply at recording
 * time come from [Rules] and use this phone's current view only (SPEC FR-36).
 */
object FamilyActions {
    /** SPEC FR-30 defaults, in force from the beginning of time so they cover every week. */
    fun defaultPolicies(): List<Payload> = listOf(
        PolicySet(Policy.Exchange(ExchangeRate(points = 1, minorUnits = 1)), InstantMs(0)),
        PolicySet(Policy.Interest(RateBp(100), Points(2000)), InstantMs(0)),
        PolicySet(Policy.Penalty(PenaltyMode.CURRENT_WEEK), InstantMs(0)),
        PolicySet(Policy.MinCashOut(Points(100)), InstantMs(0)),
    )

    /** SPEC FR-8 default values. */
    val defaultValues: List<Pair<String, IconKey>> = listOf(
        "Kindness" to IconKey("heart"),
        "Honesty" to IconKey("shield"),
        "Effort" to IconKey("mountain"),
        "Courage" to IconKey("star"),
        "Helping" to IconKey("people"),
    )

    fun createFamily(
        name: String,
        currency: CurrencyCode,
        zone: ZoneId,
        weekStart: DayOfWeek,
        self: DeviceId,
        phoneName: String,
    ): List<Payload> =
        listOf(FamilyCreated(name.trim(), currency, zone, weekStart), DeviceUpsert(self, phoneName.trim())) +
            defaultPolicies() +
            defaultValues.map { (value, icon) -> ValueUpsert(ValueId(Uuids.random()), value, icon, archived = false) }

    fun nameDevice(self: DeviceId, name: String): Action.Record = Action.Record(listOf(DeviceUpsert(self, name.trim())))

    fun addChild(state: FamilyState, name: String, style: DisplayStyle): Action.Record {
        val nextColour = state.children.size
        return Action.Record(listOf(ChildUpsert(ChildId(Uuids.random()), name.trim(), nextColour, style, archived = false)))
    }

    fun editChild(child: ChildId, name: String? = null, style: DisplayStyle? = null, colorIndex: Int? = null, archived: Boolean? = null): Action.Record =
        Action.Record(listOf(ChildUpsert(child, name?.trim(), colorIndex, style, archived)))

    fun saveChore(
        id: ChoreId?,
        title: String,
        icon: IconKey,
        kind: ChoreKind,
        points: Points,
        assignees: Set<ChildId>,
        recurrence: Recurrence,
    ): Action.Record = Action.Record(
        listOf(ChoreUpsert(id ?: ChoreId(Uuids.random()), title.trim(), icon, kind, points, assignees, recurrence, archived = false)),
    )

    fun archiveChore(id: ChoreId): Action.Record = Action.Record(listOf(ChoreUpsert(id, archived = true)))

    fun saveValue(id: ValueId?, name: String, icon: IconKey): Action.Record =
        Action.Record(listOf(ValueUpsert(id ?: ValueId(Uuids.random()), name.trim(), icon, archived = false)))

    fun archiveValue(id: ValueId): Action.Record = Action.Record(listOf(ValueUpsert(id, archived = true)))

    /** A new active goal replaces the child's current one (SPEC FR-9). */
    fun setGoal(state: FamilyState, child: ChildId, title: String, icon: IconKey, target: Points): Action {
        if (target <= Points.ZERO) return Action.Refused(Refusal.AmountMustBePositive)
        val retire = state.activeGoal(child)?.let { GoalUpsert(it.id, status = GoalStatus.ABANDONED) }
        val goal = GoalUpsert(GoalId(Uuids.random()), child, title.trim(), icon, target, GoalStatus.ACTIVE)
        return Action.Record(listOfNotNull(retire, goal))
    }

    fun closeGoal(goal: GoalId, reached: Boolean): Action.Record =
        Action.Record(listOf(GoalUpsert(goal, status = if (reached) GoalStatus.REACHED else GoalStatus.ABANDONED)))

    /** A due chore, with the deterministic id both phones would use (SPEC FR-18). */
    fun recordDueChore(child: ChildId, due: DueChore, now: InstantMs): Action.Record = Action.Record(
        listOf(
            EntryRecorded(
                due.entryId, child, EntryKind.CHORE, due.chore.points, now,
                chore = ChoreRef(due.chore.id, due.occurrence),
            ),
        ),
    )

    /** A bounty activity (or any chore done outside its schedule): a fresh id, so repeats are separate entries. */
    fun recordBounty(state: FamilyState, children: Set<ChildId>, chore: ChoreId, now: InstantMs): Action {
        val record = state.chore(chore) ?: return Action.Refused(Refusal.UnknownChore)
        if (children.isEmpty()) return Action.Refused(Refusal.NoChildSelected)
        if (record.points <= Points.ZERO) return Action.Refused(Refusal.AmountMustBePositive)
        return Action.Record(
            children.map { child ->
                EntryRecorded(EntryIds.random(now.value), child, EntryKind.CHORE, record.points, now, chore = ChoreRef(chore))
            },
        )
    }

    /** SPEC FR-12: the note is required. */
    fun award(children: Set<ChildId>, value: ValueId?, points: Points, note: String, now: InstantMs): Action {
        if (children.isEmpty()) return Action.Refused(Refusal.NoChildSelected)
        if (points <= Points.ZERO) return Action.Refused(Refusal.AmountMustBePositive)
        if (note.isBlank()) return Action.Refused(Refusal.NoteRequired)
        return Action.Record(
            children.map { child ->
                EntryRecorded(EntryIds.random(now.value), child, EntryKind.AWARD, points, now, note.trim(), valueId = value)
            },
        )
    }

    fun deduction(state: FamilyState, child: ChildId, points: Points, reason: String, now: InstantMs): Action {
        if (reason.isBlank()) return Action.Refused(Refusal.NoteRequired)
        return when (val verdict = Rules.deduction(state, child, points)) {
            is Verdict.Denied -> Action.Refused(Refusal.Rule(verdict.reason))
            Verdict.Allowed -> Action.Record(
                listOf(EntryRecorded(EntryIds.random(now.value), child, EntryKind.DEDUCTION, -points, now, reason.trim())),
            )
        }
    }

    fun cashOut(state: FamilyState, child: ChildId, points: Points, now: InstantMs): Action {
        val family = state.family ?: return Action.Refused(Refusal.Rule(DenialReason.NO_FAMILY))
        return when (val verdict = Rules.cashOut(state, child, points)) {
            is CashOutVerdict.Denied -> Action.Refused(Refusal.Rule(verdict.reason))
            is CashOutVerdict.Allowed -> {
                val rate = state.policies.exchangeAt(state.asOf) ?: return Action.Refused(Refusal.Rule(DenialReason.NOT_CONVERTIBLE))
                Action.Record(
                    listOf(
                        EntryRecorded(
                            EntryIds.random(now.value), child, EntryKind.CASH_OUT, -points, now,
                            cashOut = CashOut(verdict.money, family.currency, rate),
                        ),
                    ),
                )
            }
        }
    }

    /** SPEC FR-15: the note is required. */
    fun adjustment(child: ChildId, points: Points, note: String, now: InstantMs): Action {
        if (points == Points.ZERO) return Action.Refused(Refusal.AmountMustNotBeZero)
        if (note.isBlank()) return Action.Refused(Refusal.NoteRequired)
        return Action.Record(listOf(EntryRecorded(EntryIds.random(now.value), child, EntryKind.ADJUSTMENT, points, now, note.trim())))
    }

    /**
     * SPEC FR-16: takes effect at the reversed entry's instant; the id is deterministic. A lock with a payout or
     * break, and a payout or break itself, can only be reversed together through [reverseLock] (SPEC FR-52).
     */
    fun reverse(state: FamilyState, line: LedgerLine, reason: String): Action {
        if (line.reversedBy != null) return Action.Refused(Refusal.AlreadyReversed)
        if (reason.isBlank()) return Action.Refused(Refusal.NoteRequired)
        if (line.entry.lockPayout != null || (line.entry.lock != null && returnsOf(state, line).isNotEmpty())) {
            return Action.Refused(Refusal.LockHasReturns)
        }
        return Action.Record(listOf(reversalOf(line, reason)))
    }

    /** SPEC FR-52: reverse a lock and every payout or break of it, as one recorded action. */
    fun reverseLock(state: FamilyState, lockLine: LedgerLine, reason: String): Action {
        if (lockLine.entry.lock == null) return reverse(state, lockLine, reason)
        if (lockLine.reversedBy != null) return Action.Refused(Refusal.AlreadyReversed)
        if (reason.isBlank()) return Action.Refused(Refusal.NoteRequired)
        return Action.Record((listOf(lockLine) + returnsOf(state, lockLine)).map { reversalOf(it, reason) })
    }

    private fun returnsOf(state: FamilyState, lockLine: LedgerLine): List<LedgerLine> =
        state.account(lockLine.entry.childId)?.lines.orEmpty()
            .filter { it.reversedBy == null && it.entry.lockPayout == lockLine.entry.entryId }

    private fun reversalOf(line: LedgerLine, reason: String): EntryRecorded {
        val target = line.entry
        return EntryRecorded(
            EntryIds.reversal(target.entryId), target.childId, EntryKind.REVERSAL, -line.effect,
            target.effectiveAt, reason.trim(), reverses = target.entryId,
        )
    }

    /** SPEC FR-47: lock part of the balance with today's terms, frozen into the entry. */
    fun lock(state: FamilyState, child: ChildId, points: Points, weeks: Int, now: InstantMs): Action =
        when (val verdict = Rules.lock(state, child, points)) {
            is Verdict.Denied -> Action.Refused(Refusal.Rule(verdict.reason))
            Verdict.Allowed -> Action.Record(
                listOf(
                    EntryRecorded(
                        EntryIds.random(now.value), child, EntryKind.ADJUSTMENT, -points, now,
                        "Locked away for $weeks weeks", lock = Locks.termsNow(state, weeks),
                    ),
                ),
            )
        }

    /** SPEC FR-51: the principal comes back now, with no interest; no payout will follow. */
    fun breakLock(lock: Lock, now: InstantMs): Action.Record = Action.Record(
        listOf(
            EntryRecorded(
                EntryIds.random(now.value), lock.child, EntryKind.ADJUSTMENT, lock.principal, now,
                "Lock broken early: ${lock.principal.value} back, no interest", lockPayout = lock.lockId,
            ),
        ),
    )

    fun saveReward(id: RewardId?, title: String, icon: IconKey, price: Points): Action.Record =
        Action.Record(listOf(RewardUpsert(id ?: RewardId(Uuids.random()), title.trim(), icon, price, archived = false)))

    fun archiveReward(id: RewardId): Action.Record = Action.Record(listOf(RewardUpsert(id, archived = true)))

    /** SPEC FR-55: spends the reward's price, checked like a cash-out; readable as an adjustment on v0.1.0. */
    fun redeem(state: FamilyState, child: ChildId, reward: RewardRecord, now: InstantMs): Action =
        when (val verdict = Rules.redeem(state, child, reward.price)) {
            is Verdict.Denied -> Action.Refused(Refusal.Rule(verdict.reason))
            Verdict.Allowed -> Action.Record(
                listOf(
                    EntryRecorded(
                        EntryIds.random(now.value), child, EntryKind.ADJUSTMENT, -reward.price, now,
                        "Reward: ${reward.title}", rewardId = reward.id,
                    ),
                ),
            )
        }

    fun setLockBonus(bonus: RateBp, from: InstantMs): Action =
        if (!bonus.isValid) Action.Refused(Refusal.InvalidPolicy) else Action.Record(listOf(LockPolicySet(bonus, from)))

    fun tick(chore: ChoreId, child: ChildId, day: LocalDate, done: Boolean): Action.Record =
        Action.Record(listOf(TickSet(chore, child, day, done)))

    fun setInterest(rate: RateBp, cap: Points?, from: InstantMs): Action =
        if (!rate.isValid || (cap != null && cap <= Points.ZERO)) Action.Refused(Refusal.InvalidPolicy)
        else Action.Record(listOf(PolicySet(Policy.Interest(rate, cap), from)))

    fun setExchange(rate: ExchangeRate, from: InstantMs): Action =
        if (!rate.isValid) Action.Refused(Refusal.InvalidPolicy) else Action.Record(listOf(PolicySet(Policy.Exchange(rate), from)))

    fun setPenaltyMode(mode: PenaltyMode, from: InstantMs): Action.Record =
        Action.Record(listOf(PolicySet(Policy.Penalty(mode), from)))

    fun setMinCashOut(minimum: Points, from: InstantMs): Action =
        if (minimum < Points.ZERO) Action.Refused(Refusal.InvalidPolicy)
        else Action.Record(listOf(PolicySet(Policy.MinCashOut(minimum), from)))
}
