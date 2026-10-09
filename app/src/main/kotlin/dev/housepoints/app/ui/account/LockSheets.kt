package dev.housepoints.app.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import dev.housepoints.app.family.Action
import dev.housepoints.app.family.FamilyActions
import dev.housepoints.app.family.Outcome
import dev.housepoints.app.family.Refusal
import dev.housepoints.app.ui.components.HpIcons
import dev.housepoints.app.ui.components.OutcomeButton
import dev.housepoints.app.ui.components.QuietButton
import dev.housepoints.app.ui.components.Segmented
import dev.housepoints.app.ui.components.Stepper
import dev.housepoints.app.ui.format.Formats
import dev.housepoints.app.ui.format.Refusals
import dev.housepoints.app.ui.theme.Hp
import dev.housepoints.app.ui.theme.Radius
import dev.housepoints.app.ui.theme.Space
import dev.housepoints.contracts.ChildId
import dev.housepoints.contracts.InstantMs
import dev.housepoints.contracts.Points
import dev.housepoints.ledger.FamilyState
import dev.housepoints.ledger.Locks
import kotlinx.coroutines.launch

/** SPEC FR-47: lock part of a balance, with the payout shown before anything is recorded. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockSheet(
    state: FamilyState,
    child: ChildId,
    childName: String,
    formats: Formats,
    now: () -> InstantMs,
    perform: suspend (Action) -> Outcome,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val available = state.account(child)?.displayed ?: Points.ZERO
    var points by rememberSaveable { mutableStateOf(maxOf(Locks.MINIMUM.value, (available.value / 2 / STEP) * STEP)) }
    var weeks by rememberSaveable { mutableStateOf(DEFAULT_WEEKS) }
    var refusal by remember { mutableStateOf<Refusal?>(null) }
    val preview = Locks.preview(state, child, Points(points), weeks)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Hp.colors.surface, shape = Radius.sheet) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Space.l, vertical = Space.s), verticalArrangement = Arrangement.spacedBy(Space.l)) {
            Text("Lock some away", style = Hp.type.headline, color = Hp.colors.ink)
            Text(
                "Locked points earn a higher rate and can't be spent until they come back. $childName has ${formats.points(available)} points.",
                style = Hp.type.body, color = Hp.colors.inkMuted,
            )
            Stepper("Points", formats.points(Points(points)), onMinus = { points = (points - STEP).coerceAtLeast(Locks.MINIMUM.value) }, onPlus = { points += STEP })
            Column(verticalArrangement = Arrangement.spacedBy(Space.s)) {
                Text("For", style = Hp.type.label, color = Hp.colors.inkMuted)
                Segmented(TERMS.map { it to "$it weeks" }, weeks, { weeks = it })
            }
            if (preview != null) {
                Text(
                    "Back ${formats.dayShortMonth(preview.maturity)} as ${formats.points(preview.payout)} points: " +
                        "${formats.points(preview.interest)} more, at ${formats.percent(preview.terms.rate.value)} a week.",
                    style = Hp.type.body, color = Hp.colors.interest,
                )
            }
            refusal?.let { Text(Refusals.text(it), style = Hp.type.body, color = Hp.colors.deduct) }
            OutcomeButton("Lock away ${formats.points(Points(points))} for $weeks weeks", onClick = {
                scope.launch {
                    when (val outcome = perform(FamilyActions.lock(state, child, Points(points), weeks, now()))) {
                        is Outcome.Recorded -> { sheet.hide(); onDismiss() }
                        is Outcome.Refused -> refusal = outcome.reason
                        Outcome.NoFamily -> onDismiss()
                    }
                }
            }, icon = HpIcons.Lock)
        }
    }
}

/** A lock in force, and the one thing a parent can do before it matures: break it (SPEC FR-51). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockDetailSheet(row: LockRow, formats: Formats, now: () -> InstantMs, perform: suspend (Action) -> Outcome, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var confirming by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Hp.colors.surface, shape = Radius.sheet) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Space.l, vertical = Space.s), verticalArrangement = Arrangement.spacedBy(Space.l)) {
            Text(row.title, style = Hp.type.headline, color = Hp.colors.ink)
            Text(row.detail, style = Hp.type.body, color = Hp.colors.interest)
            Text(
                "Locked at ${formats.percent(row.lock.terms.rate.value)} a week for ${row.lock.terms.weeks} weeks, earning from ${formats.dayShortMonth(row.lock.earnsFrom)}.",
                style = Hp.type.body, color = Hp.colors.inkMuted,
            )
            if (!confirming) {
                QuietButton("Break the lock early", { confirming = true }, modifier = Modifier.fillMaxWidth())
            } else {
                Text(
                    "${formats.points(row.lock.principal)} points come back now. The interest it would have earned is lost.",
                    style = Hp.type.body, color = Hp.colors.ink,
                )
                OutcomeButton("Break it and return ${formats.points(row.lock.principal)}", onClick = {
                    scope.launch {
                        perform(FamilyActions.breakLock(row.lock, now()))
                        sheet.hide()
                        onDismiss()
                    }
                })
            }
        }
    }
}

private const val STEP = 50L
private const val DEFAULT_WEEKS = 4
private val TERMS = listOf(4, 8, 12)
