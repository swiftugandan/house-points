package dev.housepoints.app.ui.payday

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import dev.housepoints.app.ui.components.Avatar
import dev.housepoints.app.ui.components.DoubleRule
import dev.housepoints.app.ui.components.HpIcons
import dev.housepoints.app.ui.components.OutcomeButton
import dev.housepoints.app.ui.components.Rule
import dev.housepoints.app.ui.components.TopBar
import dev.housepoints.app.ui.format.Formats
import dev.housepoints.app.ui.theme.Hp
import dev.housepoints.app.ui.theme.Radius
import dev.housepoints.app.ui.theme.Space
import dev.housepoints.contracts.ChildId
import dev.housepoints.contracts.EntryKind
import dev.housepoints.contracts.Points
import dev.housepoints.ledger.FamilyState
import dev.housepoints.ledger.Interest
import dev.housepoints.ledger.PeriodSummary
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** The weekly statement (SPEC FR-42), as text ready for screen and image. */
data class StatementModel(
    val childName: String,
    val colorIndex: Int,
    val weekLabel: String,
    val paydayLabel: String,
    val startedWith: String,
    val earned: String,
    val earnedDetail: String?,
    val spent: String,
    val spentDetail: String?,
    val interest: String,
    val interestDetail: String,
    val closing: String,
    val explanation: String,
)

object Statements {
    fun from(state: FamilyState, child: ChildId, weekStart: LocalDate, formats: Formats, locale: Locale): StatementModel? {
        val family = state.family ?: return null
        val record = state.child(child) ?: return null
        val account = state.account(child) ?: return null
        val period = account.periods.firstOrNull { it.startDate == weekStart } ?: return null
        val inWeek = account.lines.filter { it.entry.effectiveAt >= period.start && it.entry.effectiveAt < period.end && it.reversedBy == null && it.entry.kind != EntryKind.REVERSAL }
        val cashOuts = inWeek.filter { it.entry.kind == EntryKind.CASH_OUT }.mapNotNull { it.entry.cashOut }
        val cashedMoney = cashOuts.fold(0L) { sum, c -> sum + c.money.value }
        val earnedCount = inWeek.count { it.effect.value > 0 }
        return StatementModel(
            childName = record.name,
            colorIndex = record.colorIndex,
            weekLabel = formats.weekRange(period.startDate) + " " + period.startDate.year,
            paydayLabel = formats.longDay(period.startDate.plusWeeks(1)),
            startedWith = formats.points(period.opening.floorPoints()),
            earned = formats.signed(period.credits),
            earnedDetail = when (earnedCount) { 0 -> null; 1 -> "1 entry"; else -> "$earnedCount entries" },
            spent = formats.signed(period.debits),
            spentDetail = if (cashedMoney > 0) formats.money(dev.housepoints.contracts.MinorUnits(cashedMoney), family.currency) + " cashed out" else null,
            interest = if (period.interest.value == 0L) "0" else formats.signedDecimal(period.interest.value),
            interestDetail = interestDetail(period, formats),
            closing = formats.points(period.closing.floorPoints()),
            explanation = explanation(state, period, inWeek, formats, locale),
        )
    }

    private fun interestDetail(period: PeriodSummary, formats: Formats): String = when {
        period.rate.value == 0 -> "No interest was set for this week"
        period.base.value <= 0 -> "Nothing stayed in for the whole week"
        period.cap != null && period.lowest > period.cap!!.toMicropoints() ->
            "${formats.percent(period.rate.value)} of ${formats.points(period.cap!!)}, the most that earns interest"
        else -> "${formats.percent(period.rate.value)} of ${formats.points(period.base.floorPoints())}, your smallest balance"
    }

    private fun explanation(state: FamilyState, period: PeriodSummary, inWeek: List<dev.housepoints.ledger.LedgerLine>, formats: Formats, locale: Locale): String {
        val zone = requireNotNull(state.family).zone
        val lowest = formats.points(maxOf(Points.ZERO, period.lowest.floorPoints()))
        val first = if (period.lowestAt == null) {
            "Your balance stayed at $lowest or more all week, so that's what earned the interest."
        } else {
            val at = period.lowestAt!!
            val day = java.time.Instant.ofEpochMilli(at.value).atZone(zone).dayOfWeek.getDisplayName(TextStyle.FULL, locale)
            val what = when (inWeek.firstOrNull { it.entry.effectiveAt == at }?.entry?.kind) {
                EntryKind.CASH_OUT -> "cash-out"
                EntryKind.DEDUCTION -> "deduction"
                else -> "change"
            }
            "Your smallest balance this week was $lowest, after $day's $what. That's what earned the interest."
        }
        val next = state.policies.interestAt(period.end)
        val nextInterest = next?.let { Interest.on(period.closing, it.rate, it.cap) }?.floorPoints() ?: Points.ZERO
        val second = if (nextInterest > Points.ZERO) " Leave it all in, and next payday adds about ${formats.points(nextInterest)} more." else ""
        return first + second
    }
}

@Composable
fun StatementScreen(model: StatementModel, animate: Boolean, onClose: () -> Unit, onShare: () -> Unit) {
    val colors = Hp.colors
    val stamp = remember { Animatable(if (animate) 0f else 1f) }
    val counted = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(animate) {
        if (animate) {
            stamp.animateTo(1f, tween(STAMP_MS, easing = FastOutSlowInEasing))
            counted.animateTo(1f, tween(COUNT_MS, easing = FastOutSlowInEasing))
        }
    }
    Column(Modifier.fillMaxSize().background(colors.ground)) {
        TopBar("Payday", onClose, backIcon = HpIcons.Close) {
            Text(model.paydayLabel, style = Hp.type.caption, color = colors.inkMuted, modifier = Modifier.padding(end = Space.s))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Space.l)) {
            Column(
                Modifier.fillMaxWidth().background(colors.surface, Radius.medium).border(1.dp, colors.rule, Radius.medium).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(Space.l),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(model.childName, model.colorIndex, size = 40.dp)
                    Spacer(Modifier.width(Space.m))
                    Column {
                        Text("${model.childName}'s week", style = Hp.type.headline, color = colors.ink)
                        Text(model.weekLabel, style = Hp.type.caption, color = colors.inkMuted)
                    }
                }
                Column {
                    StatementLine("Started with", null, model.startedWith)
                    Rule()
                    StatementLine("Earned", model.earnedDetail, model.earned)
                    Rule()
                    StatementLine("Spent or taken away", model.spentDetail, model.spent)
                }
                Column(
                    Modifier.fillMaxWidth().alpha(stamp.value).scale(1.06f - 0.06f * stamp.value)
                        .border(3.dp, colors.interest, Radius.small).padding(horizontal = 14.dp, vertical = Space.m)
                        .semantics(mergeDescendants = true) {},
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Interest", style = Hp.type.title, color = colors.interest)
                            Text(model.interestDetail, style = Hp.type.caption, color = colors.interest)
                        }
                        Text(model.interest, style = Hp.type.figureLarge, color = colors.interest)
                    }
                }
                DoubleRule(colors.ink)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("Now in the account", style = Hp.type.title.copy(fontSize = Hp.type.body.fontSize), color = colors.ink, modifier = Modifier.weight(1f))
                    Text(model.closing, style = Hp.type.display, color = colors.ink, modifier = Modifier.alpha(0.25f + 0.75f * counted.value))
                }
                Text(model.explanation, style = Hp.type.body, color = colors.ink)
            }
        }
        OutcomeButton("Share ${model.childName}'s statement", onShare, icon = HpIcons.Share, modifier = Modifier.navigationBarsPadding().padding(Space.l))
    }
}

@Composable
private fun StatementLine(label: String, detail: String?, amount: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = Hp.type.body, color = Hp.colors.ink)
            if (detail != null) Text(detail, style = Hp.type.caption, color = Hp.colors.inkMuted)
        }
        Text(amount, style = Hp.type.figure, color = Hp.colors.ink)
    }
}

private const val STAMP_MS = 220
private const val COUNT_MS = 600
