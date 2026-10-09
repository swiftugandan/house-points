package dev.housepoints.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.housepoints.app.family.Action
import dev.housepoints.app.family.FamilyActions
import dev.housepoints.app.ui.components.Avatar
import dev.housepoints.app.ui.components.DoneBadge
import dev.housepoints.app.ui.components.HpIcons
import dev.housepoints.app.ui.components.IconAction
import dev.housepoints.app.ui.components.OutcomeButton
import dev.housepoints.app.ui.components.QuietButton
import dev.housepoints.app.ui.components.Rule
import dev.housepoints.app.ui.components.Segmented
import dev.housepoints.app.ui.components.TextAction
import dev.housepoints.app.ui.theme.Hp
import dev.housepoints.app.ui.theme.Radius
import dev.housepoints.app.ui.theme.Space
import dev.housepoints.contracts.ChoreKind
import dev.housepoints.contracts.CurrencyCode
import dev.housepoints.contracts.DisplayStyle
import dev.housepoints.contracts.IconKey
import dev.housepoints.contracts.Points
import dev.housepoints.contracts.Recurrence
import dev.housepoints.ledger.FamilyState
import java.time.DayOfWeek
import java.time.ZoneId

@Composable
fun WelcomeScreen(onStart: () -> Unit, onJoin: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Hp.colors.ground).statusBarsPadding().navigationBarsPadding().padding(Space.l),
        verticalArrangement = Arrangement.spacedBy(Space.l),
    ) {
        Spacer(Modifier.weight(1f))
        Text("House Points", style = Hp.type.display, color = Hp.colors.ink, modifier = Modifier.semantics { heading() })
        Text(
            "A family bank on your phone. Children earn points for jobs and for the way they treat people. " +
                "Points grow with interest while they are left alone, and can be swapped for real money.",
            style = Hp.type.body, color = Hp.colors.inkMuted,
        )
        Text("Everything stays on the parents' phones. No account, no internet.", style = Hp.type.body, color = Hp.colors.inkMuted)
        Spacer(Modifier.weight(1f))
        OutcomeButton("Start a family on this phone", onStart)
        QuietButton("Join the family on another phone", onJoin, modifier = Modifier.fillMaxWidth(), icon = HpIcons.Qr)
    }
}

/** SPEC FR-1: name, currency, week start; the timezone is this phone's and is fixed from here on (NG-8). */
@Composable
fun CreateFamilyScreen(defaultCurrency: CurrencyCode, defaultWeekStart: DayOfWeek, zone: ZoneId, onBack: () -> Unit, onCreate: (String, CurrencyCode, DayOfWeek, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("Our family") }
    var phone by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable { mutableStateOf(defaultCurrency.code) }
    var weekStart by rememberSaveable { mutableStateOf(defaultWeekStart) }
    val currencies = (listOf(defaultCurrency.code) + COMMON_CURRENCIES).distinct()
    Column(
        Modifier.fillMaxSize().background(Hp.colors.ground).statusBarsPadding().imePadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(Space.l),
        verticalArrangement = Arrangement.spacedBy(Space.xl),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconAction(HpIcons.Back, "Back", onBack)
            Text("Start a family", style = Hp.type.headline, color = Hp.colors.ink, modifier = Modifier.semantics { heading() })
        }
        Field("Family name", name, { name = it })
        Field("This phone belongs to", phone, { phone = it }, placeholder = "e.g. Sam's phone")
        Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
            Text("Currency", style = Hp.type.label, color = Hp.colors.inkMuted)
            Segmented(currencies.take(4).map { it to it }, currency, { currency = it })
            Text("Points are worth real money in this currency. The default is 1 point = 1 penny or cent.", style = Hp.type.caption, color = Hp.colors.inkMuted)
        }
        Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
            Text("Weeks start on", style = Hp.type.label, color = Hp.colors.inkMuted)
            Segmented(listOf(DayOfWeek.MONDAY to "Monday", DayOfWeek.SUNDAY to "Sunday", DayOfWeek.SATURDAY to "Saturday"), weekStart, { weekStart = it })
            Text("Interest is paid as each week ends, at midnight ($zone). This can't be changed later.", style = Hp.type.caption, color = Hp.colors.inkMuted)
        }
        OutcomeButton(
            "Create the family",
            enabled = name.isNotBlank() && phone.isNotBlank(),
            onClick = { onCreate(name, CurrencyCode(currency), weekStart, phone) },
        )
    }
}

/** A labelled single-line field. The keyboard's Done key runs [onDone] (when given) and closes the keyboard. */
@Composable
fun Field(label: String, value: String, onChange: (String) -> Unit, placeholder: String? = null, modifier: Modifier = Modifier, onDone: (() -> Unit)? = null) {
    val focus = LocalFocusManager.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.s)) {
        Text(label, style = Hp.type.label, color = Hp.colors.inkMuted)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            placeholder = placeholder?.let { { Text(it, style = Hp.type.body, color = Hp.colors.inkMuted) } },
            modifier = Modifier.fillMaxWidth(),
            textStyle = Hp.type.body.copy(color = Hp.colors.ink),
            shape = Radius.small,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                onDone?.invoke()
                focus.clearFocus()
            }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Hp.colors.action, unfocusedBorderColor = Hp.colors.inkMuted,
                focusedContainerColor = Hp.colors.surface, unfocusedContainerColor = Hp.colors.surface,
                cursorColor = Hp.colors.action,
            ),
        )
    }
}

/** Add children one at a time; each gets the next identity colour. */
@Composable
fun AddChildrenScreen(state: FamilyState, onAdd: (Action) -> Unit, onNext: () -> Unit, nextLabel: String, onBack: (() -> Unit)? = null) {
    var name by rememberSaveable { mutableStateOf("") }
    var style by rememberSaveable { mutableStateOf(DisplayStyle.NUMBER) }
    val children = state.children.filter { !it.archived }
    Column(Modifier.fillMaxSize().background(Hp.colors.ground).imePadding()) {
        Column(
            Modifier.weight(1f).statusBarsPadding().verticalScroll(rememberScrollState()),
        ) {
            Row(Modifier.padding(start = if (onBack != null) Space.xs else Space.l, top = Space.m, end = Space.l), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) IconAction(HpIcons.Back, "Back", onBack)
                Text("Children", style = Hp.type.headline, color = Hp.colors.ink, modifier = Modifier.semantics { heading() })
            }
            Spacer(Modifier.size(Space.l))
            if (children.isNotEmpty()) Rule()
            children.forEach { child ->
                Row(Modifier.fillMaxWidth().background(Hp.colors.surface).padding(horizontal = Space.l, vertical = Space.m), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(child.name, child.colorIndex, size = 40.dp)
                    Spacer(Modifier.width(Space.m))
                    Text(child.name, style = Hp.type.title, color = Hp.colors.ink, modifier = Modifier.weight(1f))
                    Text(if (child.displayStyle == DisplayStyle.PICTURE) "Pictures" else "Numbers", style = Hp.type.caption, color = Hp.colors.inkMuted)
                }
                Rule()
            }
            Column(Modifier.padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.l)) {
                fun add() {
                    if (name.isBlank()) return
                    onAdd(FamilyActions.addChild(state, name, style))
                    name = ""
                }
                Field(if (children.isEmpty()) "First child's name" else "Another child's name", name, { name = it }, onDone = ::add)
                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    Text("Their view", style = Hp.type.label, color = Hp.colors.inkMuted)
                    Segmented(listOf(DisplayStyle.PICTURE to "Pictures", DisplayStyle.NUMBER to "Numbers"), style, { style = it })
                    Text(
                        if (style == DisplayStyle.PICTURE) "A jar of coins and pictures for jobs. For children still learning to read, roughly 5 to 8."
                        else "Real figures, the full history and \"what if I wait?\". Roughly 9 and up.",
                        style = Hp.type.caption, color = Hp.colors.inkMuted,
                    )
                }
                QuietButton(
                    if (name.isBlank()) "Add a child" else "Add ${name.trim()}",
                    onClick = ::add,
                    enabled = name.isNotBlank(),
                    icon = HpIcons.Plus,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        OutcomeButton(nextLabel, onNext, enabled = children.isNotEmpty(), modifier = Modifier.navigationBarsPadding().padding(Space.l))
    }
}

data class StarterJob(val title: String, val icon: String, val kind: ChoreKind, val points: Long, val recurrence: Recurrence, val note: String)

private val STARTER_JOBS = listOf(
    StarterJob("Make your bed", "bed", ChoreKind.EXPECTED, 0, Recurrence.Daily, "Every day, unpaid"),
    StarterJob("Clear your plate", "table", ChoreKind.EXPECTED, 0, Recurrence.Daily, "Every day, unpaid"),
    StarterJob("Empty the dishwasher", "dishes", ChoreKind.ASSIGNED, 15, Recurrence.Daily, "Every day, 15 points"),
    StarterJob("Put the bins out", "bin", ChoreKind.ASSIGNED, 20, Recurrence.Weekly, "Once a week, 20 points"),
    StarterJob("Tidy your room", "toys", ChoreKind.ASSIGNED, 25, Recurrence.Weekly, "Once a week, 25 points"),
    StarterJob("Wash the car", "car", ChoreKind.BOUNTY, 150, Recurrence.Once, "Bounty job, 150 points"),
)

/** Suggested jobs, all switched on, assigned to every child; editable later in Settings, Jobs. */
@Composable
fun StarterJobsScreen(state: FamilyState, onDone: (List<Action>) -> Unit, onSkip: () -> Unit) {
    var chosen by rememberSaveable { mutableStateOf(STARTER_JOBS.indices.toList()) }
    val everyone = state.children.filter { !it.archived }.map { it.id }.toSet()
    Column(Modifier.fillMaxSize().background(Hp.colors.ground)) {
        Column(Modifier.weight(1f).statusBarsPadding().verticalScroll(rememberScrollState())) {
            Text("Starter jobs", style = Hp.type.headline, color = Hp.colors.ink, modifier = Modifier.padding(Space.l).semantics { heading() })
            Text(
                "Unpaid jobs are things everyone does because they live here. Paid jobs earn points. Bounty jobs are extras anyone can do.",
                style = Hp.type.body, color = Hp.colors.inkMuted, modifier = Modifier.padding(horizontal = Space.l),
            )
            Spacer(Modifier.size(Space.l))
            Rule()
            STARTER_JOBS.forEachIndexed { index, job ->
                val on = index in chosen
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).background(Hp.colors.surface)
                        .toggleable(on, role = Role.Checkbox) { chosen = if (it) chosen + index else chosen - index }
                        .padding(horizontal = Space.l),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(HpIcons.of(IconKey(job.icon)), contentDescription = null, tint = Hp.colors.ink, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(Space.m))
                    Column(Modifier.weight(1f)) {
                        Text(job.title, style = Hp.type.title.copy(fontSize = Hp.type.body.fontSize), color = Hp.colors.ink)
                        Text(job.note, style = Hp.type.caption, color = Hp.colors.inkMuted)
                    }
                    if (on) DoneBadge(size = 26.dp) else Spacer(Modifier.size(26.dp))
                }
                Rule()
            }
            TextAction("Skip for now", onSkip, modifier = Modifier.padding(Space.s))
        }
        OutcomeButton(
            if (chosen.isEmpty()) "Continue without jobs" else "Add ${chosen.size} jobs",
            onClick = {
                onDone(
                    chosen.sorted().map { STARTER_JOBS[it] }.map { job ->
                        FamilyActions.saveChore(
                            null, job.title, IconKey(job.icon), job.kind, Points(job.points),
                            if (job.kind == ChoreKind.BOUNTY) emptySet() else everyone, job.recurrence,
                        )
                    },
                )
            },
            modifier = Modifier.navigationBarsPadding().padding(Space.l),
        )
    }
}

private val COMMON_CURRENCIES = listOf("GBP", "EUR", "USD", "ZAR", "AUD", "CAD", "NZD", "KES", "NGN", "INR")
