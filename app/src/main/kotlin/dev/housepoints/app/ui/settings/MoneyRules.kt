package dev.housepoints.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.housepoints.app.family.Action
import dev.housepoints.app.family.FamilyActions
import dev.housepoints.app.ui.components.OutcomeButton
import dev.housepoints.app.ui.components.Rule
import dev.housepoints.app.ui.components.Segmented
import dev.housepoints.app.ui.components.Stepper
import dev.housepoints.app.ui.components.TopBar
import dev.housepoints.app.ui.format.Formats
import dev.housepoints.app.ui.theme.Hp
import dev.housepoints.app.ui.theme.Space
import dev.housepoints.contracts.CurrencyCode
import dev.housepoints.contracts.ExchangeRate
import dev.housepoints.contracts.InstantMs
import dev.housepoints.contracts.MinorUnits
import dev.housepoints.contracts.PenaltyMode
import dev.housepoints.contracts.Points
import dev.housepoints.contracts.RateBp
import dev.housepoints.ledger.FamilyState
import dev.housepoints.ledger.Interest

/**
 * SPEC FR-30 to FR-32. Every change is a dated policy, effective now; interest changes apply from next
 * week by rule (FR-25), so the screen says so. Exchange changes show what each balance becomes in money.
 */
@Composable
fun MoneyRules(state: FamilyState, formats: Formats, now: InstantMs, onBack: () -> Unit, perform: (List<Action>) -> Unit) {
    val family = state.family ?: return
    val interest = state.policies.interestAt(now)
    var rateBp by rememberSaveable { mutableStateOf(interest?.rate?.value ?: 0) }
    var cap by rememberSaveable { mutableStateOf(interest?.cap?.value ?: 0L) }
    val exchange = state.policies.exchangeAt(now)
    var pointsPerMajor by rememberSaveable { mutableStateOf(pointsPerMajor(exchange, family.currency)) }
    var penalty by rememberSaveable { mutableStateOf(state.policies.penaltyAt(now)) }
    var minimum by rememberSaveable { mutableStateOf(state.policies.minCashOutAt(now).value) }
    var lockBonus by rememberSaveable { mutableStateOf(state.policies.lockBonusAt(now).value) }

    val yearOn200 = (1..WEEKS_IN_YEAR).fold(Points(EXAMPLE).toMicropoints()) { balance, _ ->
        balance + Interest.on(balance, RateBp(rateBp), if (cap > 0) Points(cap) else null)
    }.floorPoints()
    val minorPerMajor = minorPerMajor(family.currency)

    Column(Modifier.fillMaxSize().background(Hp.colors.ground)) {
        TopBar("Money rules", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.xl)) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                Stepper("Interest a week", formats.percent(rateBp), onMinus = { rateBp = (rateBp - RATE_STEP).coerceAtLeast(0) }, onPlus = { rateBp = (rateBp + RATE_STEP).coerceAtMost(RateBp.MAX) })
                Text(
                    "At ${formats.percent(rateBp)} a week, $EXAMPLE points left alone become ${formats.points(yearOn200)} in a year. " +
                        "Paid on the smallest amount held all week. A change starts from next week.",
                    style = Hp.type.body, color = Hp.colors.interest,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                Stepper("Interest paid on up to", if (cap > 0) formats.points(Points(cap)) else "No limit", onMinus = { cap = (cap - CAP_STEP).coerceAtLeast(0) }, onPlus = { cap += CAP_STEP })
                val weeklyMax = if (cap > 0) Interest.on(Points(cap).toMicropoints(), RateBp(rateBp), null).floorPoints() else null
                Text(
                    if (weeklyMax != null) "The most one child can earn in interest is ${formats.points(weeklyMax)} points a week." else "No limit: big savers cost more.",
                    style = Hp.type.caption, color = Hp.colors.inkMuted,
                )
            }
            Rule()
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                val one = formats.money(MinorUnits(minorPerMajor), family.currency)
                Stepper("Points per $one", formats.points(Points(pointsPerMajor)), onMinus = { pointsPerMajor = (pointsPerMajor - EXCHANGE_STEP).coerceAtLeast(EXCHANGE_STEP) }, onPlus = { pointsPerMajor += EXCHANGE_STEP })
                val newRate = ExchangeRate(pointsPerMajor, minorPerMajor)
                if (exchange != null && newRate != exchange && reduce(newRate) != reduce(exchange)) {
                    Text("Changing this changes what everyone's points are worth. Past cash-outs stay as they were.", style = Hp.type.body, color = Hp.colors.ink)
                    state.children.filter { !it.archived }.forEach { child ->
                        val balance = state.account(child.id)?.displayed ?: Points.ZERO
                        val was = formats.worth(balance, exchange, family.currency)
                        val becomes = formats.worth(balance, newRate, family.currency)
                        Text("${child.name}: ${formats.points(balance)} points, $was → $becomes", style = Hp.type.caption, color = Hp.colors.inkMuted)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                Stepper("Smallest cash-out", formats.points(Points(minimum)), onMinus = { minimum = (minimum - pointsPerMajor).coerceAtLeast(0) }, onPlus = { minimum += pointsPerMajor })
            }
            Rule()
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                Stepper("Locked savings earn an extra", formats.percent(lockBonus), onMinus = { lockBonus = (lockBonus - RATE_STEP).coerceAtLeast(0) }, onPlus = { lockBonus = (lockBonus + RATE_STEP).coerceAtMost(RateBp.MAX) })
                Text(
                    "Points locked away for 4, 8 or 12 weeks earn ${formats.percent(rateBp + lockBonus)} a week. A change applies to new locks only.",
                    style = Hp.type.caption, color = Hp.colors.inkMuted,
                )
            }
            Rule()
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                Text("Taking points away", style = Hp.type.label, color = Hp.colors.inkMuted)
                Segmented(listOf(PenaltyMode.NONE to "Never", PenaltyMode.CURRENT_WEEK to "This week's", PenaltyMode.ANY to "Any"), penalty, { penalty = it })
                Text(
                    when (penalty) {
                        PenaltyMode.NONE -> "Points are never taken away."
                        PenaltyMode.CURRENT_WEEK -> "Only points earned this week can be taken away. Savings stay safe."
                        PenaltyMode.ANY -> "Any points can be taken away. Savings are not protected."
                    },
                    style = Hp.type.caption, color = Hp.colors.inkMuted,
                )
            }
        }
        OutcomeButton("Save money rules", onClick = {
            val actions = buildList {
                if (interest?.rate?.value != rateBp || (interest.cap?.value ?: 0L) != cap) add(FamilyActions.setInterest(RateBp(rateBp), if (cap > 0) Points(cap) else null, now))
                val newRate = ExchangeRate(pointsPerMajor, minorPerMajor)
                if (exchange == null || reduce(newRate) != reduce(exchange)) add(FamilyActions.setExchange(newRate, now))
                if (penalty != state.policies.penaltyAt(now)) add(FamilyActions.setPenaltyMode(penalty, now))
                if (minimum != state.policies.minCashOutAt(now).value) add(FamilyActions.setMinCashOut(Points(minimum), now))
                if (lockBonus != state.policies.lockBonusAt(now).value) add(FamilyActions.setLockBonus(RateBp(lockBonus), now))
            }
            perform(actions)
            onBack()
        }, modifier = Modifier.navigationBarsPadding().padding(Space.l))
    }
}

private fun minorPerMajor(currency: CurrencyCode): Long =
    (0 until (currency.toCurrency()?.defaultFractionDigits?.coerceAtLeast(0) ?: 2)).fold(1L) { acc, _ -> acc * 10 }

private fun pointsPerMajor(rate: ExchangeRate?, currency: CurrencyCode): Long {
    val minor = minorPerMajor(currency)
    if (rate == null || !rate.isValid) return minor
    return (minor * rate.points / rate.minorUnits).coerceAtLeast(1)
}

private fun reduce(rate: ExchangeRate): Pair<Long, Long> {
    val gcd = java.math.BigInteger.valueOf(rate.points).gcd(java.math.BigInteger.valueOf(rate.minorUnits)).toLong().coerceAtLeast(1)
    return rate.points / gcd to rate.minorUnits / gcd
}

private const val RATE_STEP = 25
private const val CAP_STEP = 500L
private const val EXCHANGE_STEP = 10L
private const val EXAMPLE = 200L
private const val WEEKS_IN_YEAR = 52
