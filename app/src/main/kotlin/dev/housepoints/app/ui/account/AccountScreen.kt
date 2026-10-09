package dev.housepoints.app.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.unit.dp
import dev.housepoints.app.family.Action
import dev.housepoints.app.family.FamilyActions
import dev.housepoints.app.family.Outcome
import dev.housepoints.app.family.Refusal
import dev.housepoints.app.ui.components.Balance
import dev.housepoints.app.ui.components.DoneBadge
import dev.housepoints.app.ui.components.HpIcons
import dev.housepoints.app.ui.components.LedgerRow
import dev.housepoints.app.ui.components.NoticeRow
import dev.housepoints.app.ui.components.OutcomeButton
import dev.housepoints.app.ui.components.PaydayRow
import dev.housepoints.app.ui.components.PickTile
import dev.housepoints.app.ui.components.ProgressBar
import dev.housepoints.app.ui.components.QuietButton
import dev.housepoints.app.ui.components.Rule
import dev.housepoints.app.ui.components.SectionLabel
import dev.housepoints.app.ui.components.Stepper
import dev.housepoints.app.ui.components.TextAction
import dev.housepoints.app.ui.components.TopBar
import dev.housepoints.app.ui.components.WeekHeader
import dev.housepoints.app.ui.format.Formats
import dev.housepoints.app.ui.format.Refusals
import dev.housepoints.app.ui.onboarding.Field
import dev.housepoints.app.ui.record.NoteField
import dev.housepoints.app.ui.theme.Hp
import dev.housepoints.app.ui.theme.Radius
import dev.housepoints.app.ui.theme.Space
import dev.housepoints.contracts.ChildId
import dev.housepoints.contracts.IconKey
import dev.housepoints.contracts.Points
import dev.housepoints.ledger.LedgerLine
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun AccountScreen(
    model: AccountModel,
    onBack: () -> Unit,
    onHandOver: () -> Unit,
    onRecord: () -> Unit,
    onLine: (LedgerLine) -> Unit,
    onPayday: (LocalDate) -> Unit,
    onTick: (ExpectedTick) -> Unit,
    onGoal: () -> Unit,
) {
    val colors = Hp.colors
    Column(Modifier.fillMaxSize().background(colors.ground)) {
        TopBar(model.name, onBack) {
            QuietButton("Hand to ${model.name}", onHandOver, icon = HpIcons.Phone)
        }
        LazyColumn(Modifier.weight(1f)) {
            item {
                Column(Modifier.padding(horizontal = Space.l, vertical = Space.l), verticalArrangement = Arrangement.spacedBy(Space.m)) {
                    Balance(model.balance, model.balanceText, model.worth)
                    val goal = model.goal
                    if (goal != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Saving for ", style = Hp.type.caption, color = colors.ink)
                                Text(goal.title, style = Hp.type.label, color = colors.ink, modifier = Modifier.weight(1f))
                                Text(goal.progress, style = Hp.type.caption.copy(fontFamily = Hp.type.figure.fontFamily), color = colors.inkMuted)
                            }
                            ProgressBar(goal.fraction, colors.child(model.colorIndex).fill, "Goal progress ${goal.progress}")
                        }
                        TextAction("Change goal", onGoal)
                    } else {
                        TextAction("Set a savings goal", onGoal)
                    }
                    Text(model.paydayLine, style = Hp.type.caption, color = colors.interest)
                }
            }
            items(model.notices) { notice ->
                NoticeRow(notice.title, notice.body, titleColor = if (notice.isWarning) colors.deduct else colors.ink)
            }
            if (model.expected.isNotEmpty()) {
                item { SectionLabel("Every day, unpaid") }
                item { Rule() }
                items(model.expected, key = { "x" + it.chore.id }) { expected ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).background(colors.surface)
                            .toggleable(expected.done, role = Role.Checkbox) { onTick(ExpectedTick(model.childId, expected.chore.id, it)) }
                            .padding(horizontal = Space.l),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(HpIcons.of(expected.chore.icon), contentDescription = null, tint = colors.ink, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(Space.m))
                        Text(expected.chore.title, style = Hp.type.body, color = colors.ink, modifier = Modifier.weight(1f))
                        if (expected.done) DoneBadge(size = 26.dp) else Box(Modifier.size(26.dp).clip(Radius.medium).background(colors.sunk))
                    }
                    Rule()
                }
            }
            model.weeks.forEach { week ->
                item { WeekHeader(week.label, week.closing) }
                item { Rule() }
                week.payday?.let { payday ->
                    item { PaydayRow(payday.date, payday.title, payday.detail, payday.amount, onClick = { onPayday(payday.periodStart) }) }
                }
                items(week.rows, key = { it.first.entry.entryId.toString() }) { (line, row) -> LedgerRow(row, onClick = { onLine(line) }) }
                if (week.rows.isEmpty() && week.payday == null) {
                    item { Text("Nothing yet.", style = Hp.type.body, color = colors.inkMuted, modifier = Modifier.padding(Space.l)) }
                }
            }
            item { Spacer(Modifier.size(Space.xxl)) }
        }
        OutcomeButton("Record for ${model.name}", onRecord, icon = HpIcons.Plus, modifier = Modifier.navigationBarsPadding().padding(Space.l))
    }
}

data class ExpectedTick(val child: ChildId, val chore: dev.housepoints.contracts.ChoreId, val done: Boolean)

/** One entry, with the only thing you can do to the past: reverse it, with a reason (SPEC FR-16). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LineSheet(
    line: LedgerLine,
    title: String,
    detail: String,
    recordedBy: String,
    perform: suspend (Action) -> Outcome,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var reason by rememberSaveable { mutableStateOf("") }
    var refusal by remember { mutableStateOf<Refusal?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Hp.colors.surface, shape = Radius.sheet) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(horizontal = Space.l, vertical = Space.s),
            verticalArrangement = Arrangement.spacedBy(Space.l),
        ) {
            Text(title, style = Hp.type.headline, color = Hp.colors.ink)
            Text(detail, style = Hp.type.body, color = Hp.colors.ink)
            Text(recordedBy, style = Hp.type.caption, color = Hp.colors.inkMuted)
            if (line.reversedBy == null && line.effect != Points.ZERO) {
                Text(
                    "Reversing keeps both entries in the history and cancels this one as if it never happened, including any interest it earned.",
                    style = Hp.type.caption, color = Hp.colors.inkMuted,
                )
                NoteField(reason, { reason = it }, "Why it's being reversed")
                refusal?.let { Text(Refusals.text(it), style = Hp.type.body, color = Hp.colors.deduct) }
                OutcomeButton("Reverse this entry", onClick = {
                    scope.launch {
                        when (val outcome = perform(FamilyActions.reverse(line, reason))) {
                            is Outcome.Recorded -> { sheet.hide(); onDismiss() }
                            is Outcome.Refused -> refusal = outcome.reason
                            Outcome.NoFamily -> onDismiss()
                        }
                    }
                }, icon = HpIcons.Undo)
            }
        }
    }
}

/** SPEC FR-9: one active goal per child; setting a new one retires the old. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalSheet(
    childName: String,
    current: GoalModel?,
    formats: Formats,
    onSave: (title: String, icon: IconKey, target: Points) -> Unit,
    onReached: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by rememberSaveable { mutableStateOf(current?.title ?: "") }
    var icon by remember { mutableStateOf(current?.icon ?: IconKey("gift")) }
    var target by rememberSaveable { mutableStateOf(500L) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Hp.colors.surface, shape = Radius.sheet) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(horizontal = Space.l, vertical = Space.s),
            verticalArrangement = Arrangement.spacedBy(Space.l),
        ) {
            Text("$childName is saving for", style = Hp.type.headline, color = Hp.colors.ink)
            Field("What", title, { title = it }, placeholder = "e.g. Headphones")
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                GOAL_ICONS.forEach { key ->
                    PickTile(HpIcons.of(IconKey(key)), "", icon.key == key, onClick = { icon = IconKey(key) }, modifier = Modifier.weight(1f))
                }
            }
            Stepper("Target points", formats.points(Points(target)), onMinus = { target = (target - GOAL_STEP).coerceAtLeast(GOAL_STEP) }, onPlus = { target += GOAL_STEP })
            OutcomeButton("Save goal", onClick = { onSave(title, icon, Points(target)); onDismiss() }, enabled = title.isNotBlank())
            if (onReached != null) QuietButton("Mark the current goal as reached", { onReached(); onDismiss() }, modifier = Modifier.fillMaxWidth())
        }
    }
}

private val GOAL_ICONS = listOf("gift", "headphones", "kite", "bike", "book", "music")
private const val GOAL_STEP = 50L
