package dev.housepoints.ledger

import dev.housepoints.contracts.ChildId
import dev.housepoints.contracts.EntryId
import dev.housepoints.contracts.EntryRecorded
import dev.housepoints.contracts.InstantMs
import dev.housepoints.contracts.LockTerms
import dev.housepoints.contracts.Micropoints
import dev.housepoints.contracts.Points

public enum class LockStatus { LOCKED, DUE, PAID, BROKEN }

public data class Lock(
    val lockId: EntryId,
    val child: ChildId,
    val principal: Points,
    val terms: LockTerms,
    val earnsFrom: InstantMs,
    val maturity: InstantMs,
    val payout: Points,
    val status: LockStatus,
) {
    public fun potAt(t: InstantMs): Micropoints = TODO("green")
}

public object Locks {
    public fun forChild(state: FamilyState, child: ChildId): List<Lock> = TODO("green")
    public fun duePayouts(state: FamilyState): List<EntryRecorded> = TODO("green")
    public fun termsNow(state: FamilyState, weeks: Int): LockTerms = TODO("green")
}
