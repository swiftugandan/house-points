package dev.housepoints.app.child

import dev.housepoints.app.ui.account.AccountModels
import dev.housepoints.app.ui.components.JarModel
import dev.housepoints.app.ui.components.LedgerRowModel
import dev.housepoints.app.ui.format.Formats
import dev.housepoints.contracts.ChildId
import dev.housepoints.contracts.ChoreKind
import dev.housepoints.contracts.DisplayStyle
import dev.housepoints.contracts.EntryKind
import dev.housepoints.contracts.IconKey
import dev.housepoints.contracts.Points
import dev.housepoints.ledger.Chores
import dev.housepoints.ledger.FamilyState
import dev.housepoints.ledger.Pace
import dev.housepoints.ledger.ProjectionResult
import dev.housepoints.ledger.Projections
import java.time.LocalDate

/** Everything a child sees (SPEC FR-41); never anything they could act on. */
data class ChildViewModel(
    val name: String,
    val colorIndex: Int,
    val style: DisplayStyle,
    val balance: Points,
    val balanceText: String,
    val worth: String?,
    val jar: JarModel,
    val sentence: String,
    val goalTitle: String?,
    val goalIcon: IconKey?,
    val goalProgress: String?,
    val goalFraction: Float,
    val atPace: String?,
    val interestOnly: String?,
    val today: List<TodayJob>,
    val recent: List<LedgerRowModel>,
    val interestExplanation: String,
)

data class TodayJob(val title: String, val icon: IconKey, val done: Boolean)

object ChildViews {
    private const val RECENT_ROWS = 5

    fun from(state: FamilyState, child: ChildId, today: LocalDate, formats: Formats): ChildViewModel? {
        val family = state.family ?: return null
        val record = state.child(child) ?: return null
        val account = state.account(child)
        val balance = account?.displayed ?: Points.ZERO
        val goal = state.activeGoal(child)
        val jar = JarModel.of(balance, goal?.target)
        val rate = state.policies.exchangeAt(state.asOf)
        val interest = state.policies.interestAt(state.asOf)
        return ChildViewModel(
            name = record.name,
            colorIndex = record.colorIndex,
            style = record.displayStyle,
            balance = balance,
            balanceText = formats.points(balance),
            worth = formats.worth(balance, rate, family.currency),
            jar = jar,
            sentence = sentence(jar, balance),
            goalTitle = goal?.title,
            goalIcon = goal?.icon,
            goalProgress = goal?.let { "${formats.points(balance)} of ${formats.points(it.target)}" },
            goalFraction = goal?.let { if (it.target.value <= 0) 1f else (balance.value.toFloat() / it.target.value).coerceIn(0f, 1f) } ?: 0f,
            atPace = goal?.let { describe(Projections.toTarget(state, child, it.target, Pace.RECENT), formats) },
            interestOnly = goal?.let { describe(Projections.toTarget(state, child, it.target, Pace.INTEREST_ONLY), formats) },
            today = todayJobs(state, child, today),
            recent = account?.lines.orEmpty().take(RECENT_ROWS).map { AccountModels.lineModel(state, it, formats) },
            interestExplanation = interest?.let {
                "Interest is ${formats.percent(it.rate.value)} a week on the smallest amount you had all week."
            } ?: "Interest is paid each week on the smallest amount you had all week.",
        )
    }

    fun afterWeeks(state: FamilyState, child: ChildId, weeks: Int): Points = Projections.afterWeeks(state, child, weeks)

    private fun sentence(jar: JarModel, balance: Points): String = when {
        balance.value <= 0 -> "Your jar is empty. Jobs fill it up."
        jar.coinValue > 10 && jar.coins > 0 -> "You have ${jar.coins} big coins. Each big coin is ${jar.coinValue} points."
        jar.coins == 0 -> "You have a bit of a coin. Ten points make a coin."
        jar.partial -> "You have ${jar.coins} ${if (jar.coins == 1) "coin" else "coins"} and a bit."
        else -> "You have ${jar.coins} ${if (jar.coins == 1) "coin" else "coins"}."
    }

    private fun describe(result: ProjectionResult, formats: Formats): String = when (result) {
        ProjectionResult.AlreadyReached -> "You're there"
        is ProjectionResult.ReachedOn -> if (result.paydays <= 52) formats.shortDayMonthYear(result.payday) else formats.monthYear(result.payday)
        ProjectionResult.MoreThanTenYears -> "More than 10 years"
        ProjectionResult.NotAtCurrentPace -> "Not at your recent pace"
        ProjectionResult.NotYet -> "After your first full week"
    }

    /** Today's jobs: assigned ones that apply today (done or not) and the unpaid every-day ones. */
    private fun todayJobs(state: FamilyState, child: ChildId, today: LocalDate): List<TodayJob> {
        val due = Chores.dueFor(state, child, today).filter { it.occurrence == today }
        val doneToday = state.account(child)?.lines.orEmpty()
            .filter { it.reversedBy == null && it.entry.kind == EntryKind.CHORE && it.entry.chore?.occurrence == today }
            .mapNotNull { line -> line.entry.chore?.let { state.chore(it.choreId) } }
            .filter { it.kind == ChoreKind.ASSIGNED }
        val expected = Chores.expectedFor(state, child, today)
        return expected.map { TodayJob(it.chore.title, it.chore.icon, it.done) } +
            doneToday.map { TodayJob(it.title, it.icon, done = true) } +
            due.map { TodayJob(it.chore.title, it.chore.icon, done = false) }
    }
}
