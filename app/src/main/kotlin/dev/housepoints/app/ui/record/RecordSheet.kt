package dev.housepoints.app.ui.record

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.housepoints.app.family.Action
import dev.housepoints.app.family.FamilyActions
import dev.housepoints.app.family.Outcome
import dev.housepoints.app.family.Refusal
import dev.housepoints.app.ui.components.Avatar
import dev.housepoints.app.ui.components.DoneBadge
import dev.housepoints.app.ui.components.HpIcons
import dev.housepoints.app.ui.components.OutcomeButton
import dev.housepoints.app.ui.components.PickTile
import dev.housepoints.app.ui.components.Segmented
import dev.housepoints.app.ui.components.Stepper
import dev.housepoints.app.ui.format.Formats
import dev.housepoints.app.ui.format.Refusals
import dev.housepoints.app.ui.theme.Hp
import dev.housepoints.app.ui.theme.Radius
import dev.housepoints.app.ui.theme.Space
import dev.housepoints.contracts.ChildId
import dev.housepoints.contracts.ChoreId
import dev.housepoints.contracts.ChoreKind
import dev.housepoints.contracts.EntryRecorded
import dev.housepoints.contracts.InstantMs
import dev.housepoints.contracts.PenaltyMode
import dev.housepoints.contracts.Points
import dev.housepoints.contracts.ValueId
import dev.housepoints.ledger.Chores
import dev.housepoints.ledger.DenialReason
import dev.housepoints.ledger.FamilyState
import dev.housepoints.ledger.Rules
import dev.housepoints.ledger.RewardRecord
import java.time.LocalDate
import kotlinx.coroutines.launch

enum class RecordTab(val label: String) { CHORE("Chore"), AWARD("Award"), CASH_OUT("Spend"), TAKE_AWAY("Deduct") }

/** DESIGN.md "Record sheet": Who → What → Detail → one outcome button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordSheet(
    state: FamilyState,
    formats: Formats,
    today: LocalDate,
    now: () -> InstantMs,
    preselected: ChildId?,
    startTab: RecordTab,
    lastSyncNotice: String?,
    perform: suspend (Action) -> Outcome,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val children = state.children.filter { !it.archived }
    var selected by remember { mutableStateOf(setOfNotNull(preselected ?: children.singleOrNull()?.id)) }
    var tab by rememberSaveable { mutableStateOf(startTab) }
    var refusal by remember { mutableStateOf<Refusal?>(null) }
    var day by remember { mutableStateOf(today) }
    val zone = requireNotNull(state.family).zone
    val at: () -> InstantMs = { RecordDate.effectiveAt(day, today, now(), zone) }
    val singleChildTab = tab == RecordTab.CASH_OUT || tab == RecordTab.TAKE_AWAY

    fun submit(action: Action) {
        scope.launch {
            when (val outcome = perform(action)) {
                is Outcome.Recorded -> {
                    sheetState.hide()
                    onDismiss()
                }
                is Outcome.Refused -> refusal = outcome.reason
                Outcome.NoFamily -> refusal = Refusal.Rule(DenialReason.NO_FAMILY)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Hp.colors.surface,
        shape = Radius.sheet,
        dragHandle = { Box(Modifier.padding(top = Space.s).size(width = 36.dp, height = 4.dp).clip(Radius.small).background(Hp.colors.rule)) },
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding()
                .padding(horizontal = Space.l, vertical = Space.s),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("Record", style = Hp.type.headline, color = Hp.colors.ink, modifier = Modifier.semantics { heading() })
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                Text("Who", style = Hp.type.label, color = Hp.colors.inkMuted)
                Row(horizontalArrangement = Arrangement.spacedBy(Space.l)) {
                    children.forEach { child ->
                        val isOn = child.id in selected
                        Column(
                            Modifier.widthIn(min = 64.dp).toggleable(isOn, role = Role.Checkbox) { on ->
                                refusal = null
                                selected = when {
                                    singleChildTab -> setOf(child.id)
                                    on -> selected + child.id
                                    else -> selected - child.id
                                }
                            },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Space.xs),
                        ) {
                            Box {
                                Box(
                                    Modifier.border(if (isOn) 3.dp else 1.dp, if (isOn) Hp.colors.action else Hp.colors.rule, CircleShape).padding(4.dp),
                                ) { Avatar(child.name, child.colorIndex, size = 52.dp) }
                                if (isOn) DoneBadge(Modifier.align(Alignment.TopEnd), size = 22.dp)
                            }
                            Text(child.name, style = if (isOn) Hp.type.label else Hp.type.caption, color = Hp.colors.ink)
                        }
                    }
                }
            }
            Segmented(
                options = RecordTab.entries.filter { it != RecordTab.TAKE_AWAY || state.policies.penaltyAt(now()) != PenaltyMode.NONE }.map { it to it.label },
                selected = tab,
                onSelect = {
                    tab = it
                    refusal = null
                    if (it == RecordTab.CASH_OUT || it == RecordTab.TAKE_AWAY) selected = selected.take(1).toSet()
                },
            )
            if (!singleChildTab) WhenPicker(today, day, formats) { day = it; refusal = null }
            when (tab) {
                RecordTab.CHORE -> ChoreDetail(state, day, selected, at, formats, refusal, ::submit) { refusal = it }
                RecordTab.AWARD -> AwardDetail(state, selected, at, formats, refusal, ::submit)
                RecordTab.CASH_OUT -> SpendDetail(state, selected.firstOrNull(), now, formats, lastSyncNotice, refusal, ::submit)
                RecordTab.TAKE_AWAY -> TakeAwayDetail(state, selected.firstOrNull(), now, formats, refusal, ::submit)
            }
        }
    }
}

private fun names(state: FamilyState, ids: Set<ChildId>): String {
    val list = state.children.filter { it.id in ids }.map { it.name }
    return when (list.size) {
        0 -> ""
        1 -> list[0]
        else -> list.dropLast(1).joinToString(", ") + " and " + list.last()
    }
}

@Composable
private fun RefusalText(refusal: Refusal?) {
    if (refusal != null) Text(Refusals.text(refusal), style = Hp.type.body, color = Hp.colors.deduct)
}

private enum class When { TODAY, YESTERDAY, EARLIER }

/** SPEC FR-17: chores and awards can be recorded for an earlier day, up to a week back. */
@Composable
private fun WhenPicker(today: LocalDate, day: LocalDate, formats: Formats, onDay: (LocalDate) -> Unit) {
    val choice = when (day) {
        today -> When.TODAY
        today.minusDays(1) -> When.YESTERDAY
        else -> When.EARLIER
    }
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        Text("When", style = Hp.type.label, color = Hp.colors.inkMuted)
        Segmented(
            listOf(When.TODAY to "Today", When.YESTERDAY to "Yesterday", When.EARLIER to "Earlier"),
            choice,
            onSelect = {
                when (it) {
                    When.TODAY -> onDay(today)
                    When.YESTERDAY -> onDay(today.minusDays(1))
                    When.EARLIER -> onDay(today.minusDays(2))
                }
            },
        )
        if (choice == When.EARLIER) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                RecordDate.choices(today).drop(2).forEach { option ->
                    val on = option == day
                    Box(
                        Modifier.heightIn(min = Space.touch).clip(Radius.small)
                            .background(if (on) Hp.colors.action else Hp.colors.sunk)
                            .selectable(on, role = Role.RadioButton) { onDay(option) }
                            .padding(horizontal = Space.m),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(formats.shortDay(option), style = Hp.type.label, color = if (on) Hp.colors.onAction else Hp.colors.ink)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoreDetail(
    state: FamilyState, day: LocalDate, selected: Set<ChildId>, at: () -> InstantMs, formats: Formats,
    refusal: Refusal?, submit: (Action) -> Unit, refuse: (Refusal) -> Unit,
) {
    val chores = state.chores.filter { !it.archived && it.kind != ChoreKind.EXPECTED }
    var choreId by remember { mutableStateOf<ChoreId?>(null) }
    if (chores.isEmpty()) {
        Text("No paid activities yet. Add them in Settings, Activities.", style = Hp.type.body, color = Hp.colors.inkMuted)
        return
    }
    Column {
        chores.forEach { chore ->
            val isOn = chore.id == choreId
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(Radius.small)
                    .background(if (isOn) Hp.colors.sunk else Hp.colors.surface)
                    .selectable(isOn, role = Role.RadioButton) { choreId = chore.id }
                    .padding(horizontal = Space.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(HpIcons.of(chore.icon), contentDescription = null, tint = Hp.colors.ink, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Text(chore.title, style = if (isOn) Hp.type.title.copy(fontSize = Hp.type.body.fontSize) else Hp.type.body, color = Hp.colors.ink)
                    if (chore.kind == ChoreKind.BOUNTY) Text("Bounty activity", style = Hp.type.caption, color = Hp.colors.inkMuted)
                }
                Text(formats.points(chore.points), style = Hp.type.figure, color = Hp.colors.ink)
            }
        }
    }
    RefusalText(refusal)
    val chore = chores.firstOrNull { it.id == choreId }
    val label = if (chore == null || selected.isEmpty()) "Choose an activity" else {
        val each = if (selected.size > 1) " each" else ""
        "Add ${formats.points(chore.points)}$each to ${names(state, selected)}"
    }
    OutcomeButton(label, enabled = chore != null && selected.isNotEmpty(), onClick = {
        val picked = chore ?: return@OutcomeButton
        val instant = at()
        val alreadyThere = state.accounts.values.flatMap { it.lines }.filter { it.reversedBy == null }.map { it.entry.entryId }.toSet()
        val payloads = selected.flatMap { child ->
            val due = Chores.dueFor(state, child, day).firstOrNull { it.chore.id == picked.id }
            val action = if (due != null) FamilyActions.recordDueChore(child, due, instant) else FamilyActions.recordBounty(state, setOf(child), picked.id, instant)
            (action as? Action.Record)?.payloads.orEmpty()
        }.filter { (it as? EntryRecorded)?.entryId !in alreadyThere }
        if (payloads.isEmpty()) refuse(Refusal.AlreadyRecorded) else submit(Action.Record(payloads))
    })
}

@Composable
private fun AwardDetail(state: FamilyState, selected: Set<ChildId>, at: () -> InstantMs, formats: Formats, refusal: Refusal?, submit: (Action) -> Unit) {
    val values = state.values.filter { !it.archived }
    var valueId by remember { mutableStateOf<ValueId?>(values.firstOrNull()?.id) }
    var points by rememberSaveable { mutableStateOf(10L) }
    var note by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        Text("For", style = Hp.type.label, color = Hp.colors.inkMuted)
        values.chunked(VALUES_PER_ROW).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                row.forEach { value ->
                    PickTile(HpIcons.of(value.icon), value.name, value.id == valueId, onClick = { valueId = value.id }, modifier = Modifier.weight(1f))
                }
                repeat(VALUES_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    Stepper("Points", formats.points(Points(points)), onMinus = { points = (points - AWARD_STEP).coerceAtLeast(AWARD_STEP) }, onPlus = { points += AWARD_STEP })
    NoteField(note, { note = it }, "What happened")
    RefusalText(refusal)
    val each = if (selected.size > 1) " each" else ""
    OutcomeButton(
        if (selected.isEmpty()) "Choose who" else "Add ${formats.points(Points(points))}$each to ${names(state, selected)}",
        enabled = selected.isNotEmpty(),
        onClick = { submit(FamilyActions.award(selected, valueId, Points(points), note, at())) },
    )
}

private enum class SpendOn { MONEY, REWARD }

/** SPEC FR-14, FR-55: spending is either money handed over or a reward from the family's shop. */
@Composable
private fun SpendDetail(
    state: FamilyState, child: ChildId?, now: () -> InstantMs, formats: Formats, lastSyncNotice: String?,
    refusal: Refusal?, submit: (Action) -> Unit,
) {
    val rewards = state.rewards.filter { !it.archived }
    var on by rememberSaveable { mutableStateOf(SpendOn.MONEY) }
    if (rewards.isNotEmpty()) Segmented(listOf(SpendOn.MONEY to "Money", SpendOn.REWARD to "A reward"), on, { on = it })
    if (on == SpendOn.MONEY || rewards.isEmpty()) {
        CashOutDetail(state, child, now, formats, lastSyncNotice, refusal, submit)
    } else {
        RewardDetail(state, child, rewards, now, formats, refusal, submit)
    }
}

@Composable
private fun RewardDetail(
    state: FamilyState, child: ChildId?, rewards: List<RewardRecord>, now: () -> InstantMs, formats: Formats,
    refusal: Refusal?, submit: (Action) -> Unit,
) {
    var picked by remember { mutableStateOf<RewardRecord?>(null) }
    val available = child?.let { state.account(it)?.displayed } ?: Points.ZERO
    Text("Available: ${formats.points(available)} points", style = Hp.type.body, color = Hp.colors.inkMuted)
    Column {
        rewards.forEach { reward ->
            val isOn = reward.id == picked?.id
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(Radius.small)
                    .background(if (isOn) Hp.colors.sunk else Hp.colors.surface)
                    .selectable(isOn, role = Role.RadioButton) { picked = reward }
                    .padding(horizontal = Space.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(HpIcons.of(reward.icon), contentDescription = null, tint = Hp.colors.ink, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(Space.m))
                Text(reward.title, style = if (isOn) Hp.type.title.copy(fontSize = Hp.type.body.fontSize) else Hp.type.body, color = Hp.colors.ink, modifier = Modifier.weight(1f))
                Text(formats.points(reward.price), style = Hp.type.figure, color = if (reward.price > available) Hp.colors.inkMuted else Hp.colors.ink)
            }
        }
    }
    RefusalText(refusal)
    val reward = picked
    OutcomeButton(
        if (child == null || reward == null) "Choose a reward" else "Spend ${formats.points(reward.price)} on ${reward.title}",
        enabled = child != null && reward != null,
        onClick = { if (child != null && reward != null) submit(FamilyActions.redeem(state, child, reward, now())) },
    )
}

@Composable
private fun CashOutDetail(
    state: FamilyState, child: ChildId?, now: () -> InstantMs, formats: Formats, lastSyncNotice: String?,
    refusal: Refusal?, submit: (Action) -> Unit,
) {
    val family = state.family ?: return
    val rate = state.policies.exchangeAt(now())
    val step = CashOutSteps.stepPoints(rate, family.currency)
    val available = child?.let { state.account(it)?.displayed } ?: Points.ZERO
    val minimum = state.policies.minCashOutAt(now())
    var points by remember(child) { mutableStateOf(CashOutSteps.initial(available, step, minimum).value) }
    val money = rate?.moneyFor(Points(points))
    Text(
        "Available: ${formats.points(available)} points" + (formats.worth(available, rate, family.currency)?.let { " ($it)" } ?: ""),
        style = Hp.type.body, color = Hp.colors.inkMuted,
    )
    Stepper(
        "Amount",
        money?.let { formats.money(it, family.currency) } ?: formats.points(Points(points)),
        onMinus = { points = (points - step).coerceAtLeast(step) },
        onPlus = { points += step },
    )
    Text("${formats.points(Points(points))} points", style = Hp.type.caption, color = Hp.colors.inkMuted)
    if (lastSyncNotice != null) Text(lastSyncNotice, style = Hp.type.body, color = Hp.colors.inkMuted)
    RefusalText(refusal)
    val name = names(state, setOfNotNull(child))
    OutcomeButton(
        if (child == null || money == null) "Choose who" else "Pay $name ${formats.money(money, family.currency)}",
        enabled = child != null,
        onClick = { child?.let { submit(FamilyActions.cashOut(state, it, Points(points), now())) } },
    )
}

@Composable
private fun TakeAwayDetail(state: FamilyState, child: ChildId?, now: () -> InstantMs, formats: Formats, refusal: Refusal?, submit: (Action) -> Unit) {
    val max = child?.let { Rules.maxDeduction(state, it) } ?: Points.ZERO
    var points by remember(child) { mutableStateOf(minOf(DEDUCTION_STEP, max.value).coerceAtLeast(1L)) }
    var reason by rememberSaveable { mutableStateOf("") }
    val rule = when (state.policies.penaltyAt(now())) {
        PenaltyMode.CURRENT_WEEK -> "Up to ${formats.points(max)} points: only this week's earnings can be taken away."
        PenaltyMode.ANY -> "Up to ${formats.points(max)} points."
        PenaltyMode.NONE -> "Taking points away is switched off."
    }
    Text(rule, style = Hp.type.body, color = Hp.colors.inkMuted)
    Stepper("Points", formats.points(Points(points)), onMinus = { points = (points - DEDUCTION_STEP).coerceAtLeast(1L) }, onPlus = { points += DEDUCTION_STEP })
    NoteField(reason, { reason = it }, "Reason")
    RefusalText(refusal)
    OutcomeButton(
        if (child == null) "Choose who" else "Take ${formats.points(Points(points))} from ${names(state, setOf(child))}",
        enabled = child != null && max > Points.ZERO,
        onClick = { child?.let { submit(FamilyActions.deduction(state, it, Points(points), reason, now())) } },
    )
}

@Composable
fun NoteField(value: String, onChange: (String) -> Unit, label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
        Text(label, style = Hp.type.label, color = Hp.colors.inkMuted)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = Hp.type.body.copy(color = Hp.colors.ink),
            minLines = 2,
            shape = Radius.small,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Hp.colors.action, unfocusedBorderColor = Hp.colors.inkMuted,
                focusedContainerColor = Hp.colors.ground, unfocusedContainerColor = Hp.colors.ground,
                cursorColor = Hp.colors.action,
            ),
        )
    }
}

private const val VALUES_PER_ROW = 3
private const val AWARD_STEP = 5L
private const val DEDUCTION_STEP = 5L
