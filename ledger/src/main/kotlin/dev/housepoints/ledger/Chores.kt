package dev.housepoints.ledger

import dev.housepoints.contracts.ChildId
import dev.housepoints.contracts.ChoreKind
import dev.housepoints.contracts.EntryId
import dev.housepoints.contracts.EntryIds
import dev.housepoints.contracts.Recurrence
import java.time.LocalDate

/** A paid chore still to do: [occurrence] and [entryId] are what recording it will use (SPEC FR-18). */
public data class DueChore(val chore: ChoreRecord, val occurrence: LocalDate?, val entryId: EntryId)

/** An expected (unpaid) chore and whether it is ticked today (SPEC FR-7). */
public data class ExpectedChore(val chore: ChoreRecord, val done: Boolean)

public object Chores {
    /** Assigned chores due for [child] on [today] that have no entry in force yet, in chore order. */
    public fun dueFor(state: FamilyState, child: ChildId, today: LocalDate): List<DueChore> {
        val family = state.family ?: return emptyList()
        val periods = Periods(family.zone, family.weekStart)
        val recorded = state.account(child)?.lines.orEmpty()
            .filter { it.reversedBy == null && it.effect.value > 0 }
            .map { it.entry.entryId }
            .toSet()
        return state.chores
            .filter { it.kind == ChoreKind.ASSIGNED && !it.archived && child in it.assignees }
            .mapNotNull { chore -> occurrence(chore, child, today, periods) }
            .filter { it.entryId !in recorded }
    }

    public fun expectedFor(state: FamilyState, child: ChildId, today: LocalDate): List<ExpectedChore> =
        state.chores
            .filter { it.kind == ChoreKind.EXPECTED && !it.archived && child in it.assignees }
            .map { ExpectedChore(it, state.ticks[TickKey(it.id, child, today)] == true) }

    public fun bounties(state: FamilyState): List<ChoreRecord> =
        state.chores.filter { it.kind == ChoreKind.BOUNTY && !it.archived }

    private fun occurrence(chore: ChoreRecord, child: ChildId, today: LocalDate, periods: Periods): DueChore? =
        when (val recurrence = chore.recurrence) {
            Recurrence.Daily -> DueChore(chore, today, EntryIds.recurringChore(chore.id, child, today))
            is Recurrence.Weekdays -> if (today.dayOfWeek in recurrence.days) {
                DueChore(chore, today, EntryIds.recurringChore(chore.id, child, today))
            } else {
                null
            }
            Recurrence.Weekly -> periods.startDateOf(today).let { DueChore(chore, it, EntryIds.recurringChore(chore.id, child, it)) }
            Recurrence.Once -> DueChore(chore, null, EntryIds.onceChore(chore.id, child))
        }
}
