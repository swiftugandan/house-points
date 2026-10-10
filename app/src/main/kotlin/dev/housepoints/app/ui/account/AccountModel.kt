package dev.housepoints.app.ui.account

import dev.housepoints.app.ui.components.LedgerRowKind
import dev.housepoints.app.ui.components.LedgerRowModel
import dev.housepoints.app.ui.format.Formats
import dev.housepoints.contracts.ChildId
import dev.housepoints.contracts.ChoreKind
import dev.housepoints.contracts.EntryKind
import dev.housepoints.contracts.EntryRecorded
import dev.housepoints.contracts.IconKey
import dev.housepoints.contracts.InstantMs
import dev.housepoints.contracts.Micropoints
import dev.housepoints.contracts.Points
import dev.housepoints.ledger.Account
import dev.housepoints.ledger.Chores
import dev.housepoints.ledger.ExpectedChore
import dev.housepoints.ledger.FamilyState
import dev.housepoints.ledger.Flag
import dev.housepoints.ledger.LedgerLine
import dev.housepoints.ledger.Lock
import dev.housepoints.ledger.LockStatus
import dev.housepoints.ledger.Locks
import dev.housepoints.contracts.EntryIds
import dev.housepoints.ledger.PeriodSummary
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

data class AccountModel(
    val childId: ChildId,
    val name: String,
    val colorIndex: Int,
    val balance: Points,
    val balanceText: String,
    val worth: String?,
    val goal: GoalModel?,
    val paydayLine: String,
    val notices: List<Notice>,
    val expected: List<ExpectedChore>,
    val weeks: List<WeekModel>,
    val locks: List<LockRow>,
)

/** SPEC FR-53: a lock as the parent sees it, with what it will bring back. */
data class LockRow(val lock: Lock, val title: String, val detail: String)

data class GoalModel(val title: String, val icon: IconKey, val fraction: Float, val progress: String)

data class Notice(val title: String, val body: String, val isWarning: Boolean)

data class WeekModel(
    val label: String,
    val closing: String?,
    val payday: PaydayRowModel?,
    val rows: List<Pair<LedgerLine, LedgerRowModel>>,
)

data class PaydayRowModel(val periodStart: LocalDate, val date: String, val title: String, val detail: String?, val amount: String)

object AccountModels {
    fun from(state: FamilyState, child: ChildId, today: LocalDate, formats: Formats, locale: Locale): AccountModel? {
        val family = state.family ?: return null
        val record = state.child(child) ?: return null
        val account = state.account(child)
        val balance = account?.displayed ?: Points.ZERO
        val rate = state.policies.exchangeAt(state.asOf)
        return AccountModel(
            childId = child,
            name = record.name,
            colorIndex = record.colorIndex,
            balance = balance,
            balanceText = formats.points(balance),
            worth = formats.worth(balance, rate, family.currency),
            goal = state.activeGoal(child)?.let { goal ->
                GoalModel(
                    goal.title, goal.icon,
                    if (goal.target.value <= 0) 1f else (balance.value.toFloat() / goal.target.value).coerceIn(0f, 1f),
                    "${formats.points(balance)} of ${formats.points(goal.target)}",
                )
            },
            paydayLine = paydayLine(account, family.zone, locale),
            notices = notices(state, child, formats, locale),
            expected = Chores.expectedFor(state, child, today),
            weeks = account?.let { weeks(state, it, formats) }.orEmpty(),
            locks = lockRows(state, child, formats),
        )
    }

    fun lockRows(state: FamilyState, child: ChildId, formats: Formats): List<LockRow> =
        Locks.forChild(state, child).filter { it.status == LockStatus.LOCKED || it.status == LockStatus.DUE }.map { lock ->
            LockRow(
                lock,
                title = "Locked away: ${formats.points(lock.principal)}",
                detail = if (lock.status == LockStatus.DUE) "Coming back now, with ${formats.points(lock.interest)} more"
                else "Back ${formats.dayShortMonth(lock.maturity)} with about ${formats.points(lock.interest)} more",
            )
        }

    fun lineModel(state: FamilyState, line: LedgerLine, formats: Formats): LedgerRowModel {
        val entry = line.entry
        val (title, note, kind) = when (entry.kind) {
            EntryKind.CHORE -> {
                val chore = entry.chore?.let { state.chore(it.choreId) }
                Triple(chore?.title ?: "Activity", if (chore?.kind == ChoreKind.BOUNTY) "Bounty activity" else null, LedgerRowKind.NORMAL)
            }
            EntryKind.AWARD -> Triple(
                "Award" + (entry.valueId?.let { state.value(it) }?.let { " · ${it.name}" } ?: ""), entry.note, LedgerRowKind.NORMAL,
            )
            EntryKind.DEDUCTION -> Triple("Taken away", entry.note, LedgerRowKind.DEDUCTION)
            EntryKind.CASH_OUT -> Triple("Cash out", null, LedgerRowKind.NORMAL)
            EntryKind.ADJUSTMENT -> adjustmentTitle(state, entry)
            EntryKind.REVERSAL -> Triple("Correction", "Reversed: ${entry.note}", LedgerRowKind.REVERSAL)
        }
        return rowModel(state, line, formats, title, note, kind)
    }

    private fun adjustmentTitle(state: FamilyState, entry: EntryRecorded): Triple<String, String?, LedgerRowKind> {
        val lock = entry.lock
        val returned = entry.lockPayout
        val reward = entry.rewardId
        return when {
            lock != null -> Triple("Locked away · ${lock.weeks} weeks", null, LedgerRowKind.NORMAL)
            returned != null && entry.entryId == EntryIds.lockPayout(returned) -> Triple("Locked savings back", null, LedgerRowKind.NORMAL)
            returned != null -> Triple("Lock broken early", "Points back, no interest", LedgerRowKind.NORMAL)
            reward != null -> {
                val title = state.rewards.firstOrNull { it.id == reward }?.title ?: entry.note.removePrefix("Reward: ")
                Triple("Reward · $title", null, LedgerRowKind.NORMAL)
            }
            else -> Triple("Adjustment", entry.note, LedgerRowKind.NORMAL)
        }
    }

    private fun rowModel(state: FamilyState, line: LedgerLine, formats: Formats, title: String, note: String?, kind: LedgerRowKind): LedgerRowModel {
        val entry = line.entry
        val reversalNote = if (line.reversedBy != null) {
            val reason = state.account(entry.childId)?.lines?.firstOrNull { it.entry.entryId == line.reversedBy }?.entry?.note
            "Reversed" + (reason?.let { ": $it" } ?: "")
        } else {
            null
        }
        return LedgerRowModel(
            date = formats.shortDay(entry.effectiveAt),
            title = title,
            note = reversalNote ?: note,
            amount = formats.signed(line.effect),
            amountSub = entry.cashOut?.let { "${formats.money(it.money, it.currency)} paid" },
            kind = kind,
            reversed = line.reversedBy != null,
        )
    }

    private fun weeks(state: FamilyState, account: Account, formats: Formats): List<WeekModel> {
        val current = account.current
        val groups = mutableListOf<WeekModel>()
        if (current != null) {
            val rows = linesIn(account, current.start, current.end).map { it to lineModel(state, it, formats) }
            groups += WeekModel("This week · ${formats.weekRange(current.startDate)}", null, null, rows)
        }
        account.periods.forEach { period ->
            val rows = linesIn(account, period.start, period.end).map { it to lineModel(state, it, formats) }
            groups += WeekModel(formats.weekRange(period.startDate), formats.points(period.closing.floorPoints()), paydayRow(period, formats), rows)
        }
        return groups
    }

    private fun linesIn(account: Account, from: InstantMs, to: InstantMs): List<LedgerLine> =
        account.lines.filter { it.entry.effectiveAt >= from && it.entry.effectiveAt < to }

    fun paydayRow(period: PeriodSummary, formats: Formats): PaydayRowModel {
        val base = period.base
        val (title, detail) = when {
            period.rate.value == 0 -> "Payday" to "No interest was set for this week"
            base.value <= 0L -> "Payday" to "Smallest balance this week was ${formats.points(period.lowest.floorPoints())}"
            else -> "Payday · ${formats.percent(period.rate.value)} of ${formats.points(base.floorPoints())}" to null
        }
        val amount = if (period.interest == Micropoints.ZERO) "0" else formats.signedDecimal(period.interest.value)
        return PaydayRowModel(period.startDate, formats.shortDay(period.end), title, detail, amount)
    }

    private fun paydayLine(account: Account?, zone: java.time.ZoneId, locale: Locale): String {
        val current = account?.current ?: return "Interest is paid as each week ends."
        val day = java.time.Instant.ofEpochMilli(current.end.value).atZone(zone).dayOfWeek.getDisplayName(TextStyle.FULL, locale)
        val expected = current.expectedInterest.floorPoints()
        return if (expected > Points.ZERO) {
            "Payday $day: about ${expected.value} ${if (expected.value == 1L) "point" else "points"} of interest, if nothing is taken out before then."
        } else {
            "Payday is $day. Points left in for a whole week earn interest."
        }
    }

    private fun notices(state: FamilyState, child: ChildId, formats: Formats, locale: Locale): List<Notice> =
        state.flags.mapNotNull { flag ->
            when {
                flag is Flag.Overdrawn && flag.child == child -> Notice(
                    "Overdrawn by ${formats.points(-flag.balance.floorPoints())}",
                    "More was paid out than was in the account, probably on two phones while they were apart. " +
                        "Decide together: reverse a cash-out if the money came back, or let it be earned back.",
                    isWarning = true,
                )
                flag is Flag.PossibleDuplicate && flag.child == child -> {
                    val line = state.account(child)?.lines?.firstOrNull { it.entry.entryId == flag.first }
                    val title = line?.let { lineModel(state, it, formats).title } ?: "An entry"
                    val day = line?.entry?.effectiveAt?.let { formats.localDate(it).dayOfWeek.getDisplayName(TextStyle.FULL, locale) }
                    Notice("Possible duplicate", "\"$title\" was recorded on both phones" + (day?.let { " on $it" } ?: "") + ". Reverse one if it only happened once.", isWarning = false)
                }
                else -> null
            }
        }
}
