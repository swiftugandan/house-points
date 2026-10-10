package dev.housepoints.app.ui.format

import dev.housepoints.app.family.Refusal
import dev.housepoints.ledger.DenialReason

/** Plain explanations, written for a parent standing in the kitchen (PRODUCT.md "Voice"). */
object Refusals {
    fun text(refusal: Refusal): String = when (refusal) {
        Refusal.NoteRequired -> "Add a few words about what happened. They stay in the history."
        Refusal.NoChildSelected -> "Choose who this is for."
        Refusal.AmountMustBePositive -> "The amount needs to be more than zero."
        Refusal.AmountMustNotBeZero -> "The amount can't be zero."
        Refusal.UnknownChore -> "That activity no longer exists."
        Refusal.AlreadyReversed -> "This entry has already been reversed."
        Refusal.AlreadyRecorded -> "That activity is already recorded for that day."
        Refusal.LockHasReturns -> "This lock's points have already come back. Reverse the lock itself to undo both together."
        Refusal.InvalidPolicy -> "Those numbers don't make a valid rule."
        is Refusal.Rule -> when (refusal.reason) {
            DenialReason.NO_FAMILY -> "Set up the family first."
            DenialReason.NOT_POSITIVE -> "The amount needs to be more than zero."
            DenialReason.PENALTIES_OFF -> "Taking points away is switched off in Money rules."
            DenialReason.MORE_THAN_THIS_WEEK -> "You can only take away what was earned this week."
            DenialReason.MORE_THAN_BALANCE -> "That's more than is in the account."
            DenialReason.BELOW_MINIMUM -> "That's below the smallest cash-out set in Money rules."
            DenialReason.NOT_CONVERTIBLE -> "That amount doesn't convert to an exact sum of money."
        }
    }
}
