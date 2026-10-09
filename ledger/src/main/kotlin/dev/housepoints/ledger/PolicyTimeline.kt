package dev.housepoints.ledger

import dev.housepoints.contracts.DeviceId
import dev.housepoints.contracts.ExchangeRate
import dev.housepoints.contracts.InstantMs
import dev.housepoints.contracts.Lamport
import dev.housepoints.contracts.PenaltyMode
import dev.housepoints.contracts.Points
import dev.housepoints.contracts.Policy
import dev.housepoints.contracts.RateBp

public data class PolicyChange<out P : Policy>(
    val policy: P,
    val effectiveFrom: InstantMs,
    val recordedBy: DeviceId,
    val lamport: Lamport,
    /** Another change of the same kind with the same [effectiveFrom] won (SPEC FR-31). */
    val superseded: Boolean,
)

/**
 * Every policy change, per kind, in force-resolution order (SPEC FR-31): the change in force at `t` is the
 * one with the greatest `effectiveFrom ≤ t`, ties going to the greater `(lamport, deviceId)`.
 */
public data class PolicyTimeline(
    val exchange: List<PolicyChange<Policy.Exchange>>,
    val interest: List<PolicyChange<Policy.Interest>>,
    val penalty: List<PolicyChange<Policy.Penalty>>,
    val minCashOut: List<PolicyChange<Policy.MinCashOut>>,
    /** SPEC FR-48: the bonus on top of the interest rate for new locks. */
    val lockBonus: List<LockBonusChange>,
) {
    public fun lockBonusAt(t: InstantMs): RateBp =
        lockBonus.lastOrNull { it.effectiveFrom <= t }?.bonus ?: DEFAULT_LOCK_BONUS

    public fun exchangeAt(t: InstantMs): ExchangeRate? = inForce(exchange, t)?.rate
    public fun interestAt(t: InstantMs): Policy.Interest? = inForce(interest, t)
    public fun penaltyAt(t: InstantMs): PenaltyMode = inForce(penalty, t)?.mode ?: PenaltyMode.NONE
    public fun minCashOutAt(t: InstantMs): Points = inForce(minCashOut, t)?.minimum ?: Points.ZERO

    public fun interestHistory(): List<PolicyChange<Policy.Interest>> = interest

    public companion object {
        public val EMPTY: PolicyTimeline = PolicyTimeline(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

        /** SPEC FR-48 default: locks earn one percentage point a week more than the account. */
        public val DEFAULT_LOCK_BONUS: RateBp = RateBp(100)

        internal fun <P : Policy> resolve(changes: List<Pair<PolicyChange<P>, OpKey>>): List<PolicyChange<P>> {
            val ordered = changes.sortedWith(compareBy<Pair<PolicyChange<P>, OpKey>> { it.first.effectiveFrom }.thenBy { it.second })
            return ordered.mapIndexed { index, (change, _) ->
                val next = ordered.getOrNull(index + 1)?.first
                change.copy(superseded = next != null && next.effectiveFrom == change.effectiveFrom)
            }
        }

        private fun <P : Policy> inForce(changes: List<PolicyChange<P>>, t: InstantMs): P? =
            changes.lastOrNull { it.effectiveFrom <= t }?.policy
    }
}

/** A lock-bonus setting (SPEC FR-48), resolved like the other policies. */
public data class LockBonusChange(val bonus: RateBp, val effectiveFrom: InstantMs, val recordedBy: DeviceId, val lamport: Lamport)
