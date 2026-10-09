package dev.housepoints.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.housepoints.app.family.Action
import dev.housepoints.app.family.FamilyActions
import dev.housepoints.app.ui.components.Avatar
import dev.housepoints.app.ui.components.HpIcons
import dev.housepoints.app.ui.components.OutcomeButton
import dev.housepoints.app.ui.components.QuietButton
import dev.housepoints.app.ui.components.Rule
import dev.housepoints.app.ui.components.SectionLabel
import dev.housepoints.app.ui.components.Segmented
import dev.housepoints.app.ui.components.Stepper
import dev.housepoints.app.ui.components.TopBar
import dev.housepoints.app.ui.format.Formats
import dev.housepoints.app.ui.onboarding.Field
import dev.housepoints.app.ui.theme.Hp
import dev.housepoints.app.ui.theme.Space
import dev.housepoints.contracts.ChildId
import dev.housepoints.contracts.ChoreId
import dev.housepoints.contracts.ChoreKind
import dev.housepoints.contracts.IconKey
import dev.housepoints.contracts.Points
import dev.housepoints.contracts.Recurrence
import dev.housepoints.ledger.ChoreRecord
import dev.housepoints.ledger.FamilyState
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun JobsSettings(state: FamilyState, formats: Formats, locale: Locale, onBack: () -> Unit, onEdit: (ChoreId?) -> Unit) {
    val groups = listOf(
        ChoreKind.EXPECTED to "Unpaid, everyone does them",
        ChoreKind.ASSIGNED to "Paid jobs",
        ChoreKind.BOUNTY to "Bounty jobs, anyone can do them",
    )
    Column(Modifier.fillMaxSize().background(Hp.colors.ground)) {
        TopBar("Jobs", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            groups.forEach { (kind, label) ->
                val chores = state.chores.filter { it.kind == kind && !it.archived }
                SectionLabel(label)
                Rule()
                if (chores.isEmpty()) {
                    Text("None yet.", style = Hp.type.caption, color = Hp.colors.inkMuted, modifier = Modifier.fillMaxWidth().background(Hp.colors.surface).padding(Space.l))
                    Rule()
                }
                chores.forEach { chore ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp).background(Hp.colors.surface).clickable { onEdit(chore.id) }.padding(horizontal = Space.l),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(HpIcons.of(chore.icon), contentDescription = null, tint = Hp.colors.ink, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(Space.m))
                        Column(Modifier.weight(1f)) {
                            Text(chore.title, style = Hp.type.body, color = Hp.colors.ink)
                            Text(describe(chore, state, locale), style = Hp.type.caption, color = Hp.colors.inkMuted)
                        }
                        if (kind != ChoreKind.EXPECTED) Text(formats.points(chore.points), style = Hp.type.figure, color = Hp.colors.ink)
                    }
                    Rule()
                }
            }
            Spacer(Modifier.size(Space.xl))
        }
        OutcomeButton("Add a job", { onEdit(null) }, icon = HpIcons.Plus, modifier = Modifier.navigationBarsPadding().padding(Space.l))
    }
}

private fun describe(chore: ChoreRecord, state: FamilyState, locale: Locale): String {
    val who = if (chore.kind == ChoreKind.BOUNTY) "Anyone" else state.children.filter { it.id in chore.assignees }.joinToString(", ") { it.name }.ifEmpty { "Nobody yet" }
    val often = when (val r = chore.recurrence) {
        Recurrence.Daily -> "every day"
        Recurrence.Weekly -> "once a week"
        Recurrence.Once -> if (chore.kind == ChoreKind.BOUNTY) "any time" else "once"
        is Recurrence.Weekdays -> r.days.sorted().joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
    }
    return "$who · $often"
}

private enum class Often(val label: String) { DAILY("Every day"), SOME_DAYS("Some days"), WEEKLY("Weekly"), ONCE("Once") }

/** Add or edit a job (SPEC FR-6). Edits never change entries already recorded (SPEC FR-11). */
@Composable
fun ChoreEditor(state: FamilyState, chore: ChoreRecord?, formats: Formats, locale: Locale, onBack: () -> Unit, perform: (Action) -> Unit) {
    var title by rememberSaveable { mutableStateOf(chore?.title ?: "") }
    var icon by remember { mutableStateOf(chore?.icon ?: IconKey("bin")) }
    var kind by rememberSaveable { mutableStateOf(chore?.kind ?: ChoreKind.ASSIGNED) }
    var points by rememberSaveable { mutableStateOf(chore?.points?.value ?: 20L) }
    var who by remember { mutableStateOf(chore?.assignees ?: state.children.filter { !it.archived }.map { it.id }.toSet()) }
    val initialOften = when (chore?.recurrence) {
        null, Recurrence.Daily -> Often.DAILY
        Recurrence.Weekly -> Often.WEEKLY
        Recurrence.Once -> Often.ONCE
        is Recurrence.Weekdays -> Often.SOME_DAYS
    }
    var often by rememberSaveable { mutableStateOf(initialOften) }
    var days by remember { mutableStateOf((chore?.recurrence as? Recurrence.Weekdays)?.days ?: setOf(DayOfWeek.SATURDAY)) }
    Column(Modifier.fillMaxSize().background(Hp.colors.ground).imePadding()) {
        TopBar(if (chore == null) "New job" else "Edit job", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.xl)) {
            Field("What", title, { title = it }, placeholder = "e.g. Put the bins out")
            IconGrid(JOB_ICONS, icon) { icon = it }
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                Text("Kind", style = Hp.type.label, color = Hp.colors.inkMuted)
                Segmented(listOf(ChoreKind.EXPECTED to "Unpaid", ChoreKind.ASSIGNED to "Paid", ChoreKind.BOUNTY to "Bounty"), kind, { kind = it })
                Text(
                    when (kind) {
                        ChoreKind.EXPECTED -> "Everyone does these because they live here. Ticked off, never paid."
                        ChoreKind.ASSIGNED -> "Belongs to particular children and earns points each time it's due."
                        ChoreKind.BOUNTY -> "An extra job anyone can do. Paid every time it's done."
                    },
                    style = Hp.type.caption, color = Hp.colors.inkMuted,
                )
            }
            if (kind != ChoreKind.EXPECTED) {
                Stepper("Points", formats.points(Points(points)), onMinus = { points = (points - POINT_STEP).coerceAtLeast(POINT_STEP) }, onPlus = { points += POINT_STEP })
            }
            if (kind != ChoreKind.BOUNTY) {
                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    Text("Who", style = Hp.type.label, color = Hp.colors.inkMuted)
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.l)) {
                        state.children.filter { !it.archived }.forEach { child ->
                            val on = child.id in who
                            Column(
                                Modifier.toggleable(on, role = Role.Checkbox) { who = if (it) who + child.id else who - child.id },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(Modifier.border(if (on) 3.dp else 1.dp, if (on) Hp.colors.action else Hp.colors.rule, CircleShape).padding(3.dp)) {
                                    Avatar(child.name, child.colorIndex, size = 44.dp)
                                }
                                Text(child.name, style = if (on) Hp.type.label else Hp.type.caption, color = Hp.colors.ink)
                            }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                    Text("How often", style = Hp.type.label, color = Hp.colors.inkMuted)
                    Segmented(Often.entries.map { it to it.label }, often, { often = it })
                    if (often == Often.SOME_DAYS) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                            DayOfWeek.entries.forEach { day ->
                                val on = day in days
                                Box(
                                    Modifier.weight(1f).heightIn(min = 48.dp).clip(CircleShape)
                                        .background(if (on) Hp.colors.action else Hp.colors.sunk)
                                        .toggleable(on, role = Role.Checkbox) { days = if (it) days + day else (days - day).ifEmpty { days } },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(day.getDisplayName(TextStyle.NARROW, locale), style = Hp.type.label, color = if (on) Hp.colors.onAction else Hp.colors.ink)
                                }
                            }
                        }
                    }
                }
            }
            if (chore != null) {
                QuietButton("Remove this job", { perform(FamilyActions.archiveChore(chore.id)); onBack() }, modifier = Modifier.fillMaxWidth())
            }
        }
        OutcomeButton(
            if (chore == null) "Add this job" else "Save changes",
            enabled = title.isNotBlank() && (kind == ChoreKind.BOUNTY || who.isNotEmpty()),
            onClick = {
                val recurrence = when {
                    kind == ChoreKind.BOUNTY -> Recurrence.Once
                    else -> when (often) {
                        Often.DAILY -> Recurrence.Daily
                        Often.SOME_DAYS -> Recurrence.Weekdays(days)
                        Often.WEEKLY -> Recurrence.Weekly
                        Often.ONCE -> Recurrence.Once
                    }
                }
                val assignees: Set<ChildId> = if (kind == ChoreKind.BOUNTY) emptySet() else who
                val value = if (kind == ChoreKind.EXPECTED) Points.ZERO else Points(points)
                perform(FamilyActions.saveChore(chore?.id, title, icon, kind, value, assignees, recurrence))
                onBack()
            },
            modifier = Modifier.navigationBarsPadding().padding(Space.l),
        )
    }
}

private val JOB_ICONS = listOf("bin", "dishes", "bed", "toys", "plant", "car", "laundry", "table", "broom", "shirt", "dog", "book", "pencil", "music", "bike", "star")
private const val POINT_STEP = 5L
