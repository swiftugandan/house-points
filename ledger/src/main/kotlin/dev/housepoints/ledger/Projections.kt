package dev.housepoints.ledger

import dev.housepoints.contracts.ChildId
import dev.housepoints.contracts.Micropoints
import dev.housepoints.contracts.Points
import java.time.LocalDate

public enum class Pace { INTEREST_ONLY, RECENT }

public sealed interface ProjectionResult {
    public data object AlreadyReached : ProjectionResult
    public data class ReachedOn(val payday: LocalDate, val paydays: Int) : ProjectionResult
    public data object MoreThanTenYears : ProjectionResult
    public data object NotAtCurrentPace : ProjectionResult
    public data object NotYet : ProjectionResult
}

/**
 * Goal dates and "what if I wait" (SPEC FR-10, FR-41). They run the real interest rules forward, week by
 * week, using each future week's policy, so a projection can never disagree with what payday will do.
 */
public object Projections {
    public const val HORIZON_WEEKS: Int = 520
    private const val PACE_WINDOW: Int = 4

    /** Mean net non-interest change over the last (up to) four completed weeks; null with none. */
    public fun pace(state: FamilyState, child: ChildId): Points? {
        val recent = state.account(child)?.periods?.take(PACE_WINDOW).orEmpty()
        if (recent.isEmpty()) return null
        val total = recent.fold(Points.ZERO) { sum, week -> sum + week.credits + week.debits }
        return Points(Math.floorDiv(total.value, recent.size.toLong()))
    }

    public fun toTarget(state: FamilyState, child: ChildId, target: Points, pace: Pace): ProjectionResult {
        val account = state.account(child) ?: return ProjectionResult.NotYet
        if (account.displayed >= target) return ProjectionResult.AlreadyReached
        val weekly = when (pace) {
            Pace.INTEREST_ONLY -> Points.ZERO
            Pace.RECENT -> {
                val recent = pace(state, child) ?: return ProjectionResult.NotYet
                if (recent <= Points.ZERO) return ProjectionResult.NotAtCurrentPace
                recent
            }
        }
        val goal = target.toMicropoints()
        var reachedOn: ProjectionResult = ProjectionResult.MoreThanTenYears
        simulate(state, child, weekly, HORIZON_WEEKS) { payday, number, balance ->
            if (balance >= goal) {
                reachedOn = ProjectionResult.ReachedOn(payday, number)
                false
            } else {
                true
            }
        }
        return reachedOn
    }

    /** Balance after [weeks] more paydays with interest only. */
    public fun afterWeeks(state: FamilyState, child: ChildId, weeks: Int): Points {
        var last = state.account(child)?.balance ?: Micropoints.ZERO
        simulate(state, child, Points.ZERO, weeks) { _, _, balance ->
            last = balance
            true
        }
        return last.floorPoints()
    }

    /**
     * Calls [onPayday] after each simulated payday with its date, its number (1 = the end of the current
     * week) and the balance; stops when it returns false or after [maxWeeks].
     */
    private fun simulate(
        state: FamilyState,
        child: ChildId,
        weekly: Points,
        maxWeeks: Int,
        onPayday: (LocalDate, Int, Micropoints) -> Boolean,
    ) {
        val family = state.family ?: return
        val account = state.account(child) ?: return
        val current = account.current ?: return
        val periods = Periods(family.zone, family.weekStart)
        val step = weekly.toMicropoints()

        var period = periods.containing(current.start)
        var balance = account.balance
        var lowest = current.lowestSoFar
        for (number in 1..maxWeeks) {
            val policy = state.policies.interestAt(period.start)
            val interest = if (policy == null) Micropoints.ZERO else InterestEngine.interestOn(lowest, policy.rate, policy.cap)
            balance = balance + interest
            if (!onPayday(period.endDate, number, balance)) return
            period = periods.next(period)
            lowest = balance
            balance = balance + step
            if (balance < lowest) lowest = balance
        }
    }
}
