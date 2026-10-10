package dev.housepoints.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import dev.housepoints.app.AppGraph
import dev.housepoints.app.KeyState
import dev.housepoints.app.family.Action
import dev.housepoints.app.family.FamilyActions
import dev.housepoints.app.family.FamilySnapshot
import dev.housepoints.app.family.FamilyViewModel
import dev.housepoints.app.family.Outcome
import dev.housepoints.app.ui.format.Formats
import dev.housepoints.app.ui.onboarding.AddChildrenScreen
import dev.housepoints.app.ui.onboarding.StarterActivitiesScreen
import dev.housepoints.app.ui.record.RecordSheet
import dev.housepoints.app.ui.record.RecordTab
import dev.housepoints.app.ui.theme.Hp
import dev.housepoints.app.widget.WidgetLink
import dev.housepoints.contracts.DeviceUpsert
import dev.housepoints.ledger.FamilyState
import java.util.Locale

/** What the app shows, decided by whether this phone has a family, a key, or neither. */
@Composable
fun AppNavigation(graph: AppGraph, family: FamilyViewModel, snapshot: FamilySnapshot, link: WidgetLink?, onLinkHandled: () -> Unit) {
    val key by graph.key.collectAsState()
    when {
        snapshot is FamilySnapshot.Loading || key is KeyState.Loading -> Unit
        snapshot is FamilySnapshot.NoFamily && key is KeyState.Held -> JoiningFlow(graph, key as KeyState.Held)
        snapshot is FamilySnapshot.NoFamily -> OnboardingFlow(graph, family)
        snapshot is FamilySnapshot.Ready -> SetupGate(graph, family, snapshot.state) { FamilyFlow(graph, family, snapshot.state, link, onLinkHandled) }
    }
}

/** Finishes setup on this phone (name, children, starter activities) before the family screens appear. */
@Composable
private fun SetupGate(graph: AppGraph, family: FamilyViewModel, state: FamilyState, content: @Composable () -> Unit) {
    // A phone that joined by pairing code names itself once the family has arrived.
    LaunchedEffect(state.devices) {
        val pending = graph.prefs.pendingPhoneName
        if (pending != null && state.devices.none { it.id == graph.deviceId && it.name.isNotBlank() }) {
            family.perform(Action.Record(listOf(DeviceUpsert(graph.deviceId, pending)))) { graph.prefs.pendingPhoneName = null }
        }
    }
    var childrenDone by remember { mutableStateOf(graph.prefs.childrenStepDone) }
    var starterOffered by remember { mutableStateOf(graph.prefs.starterActivitiesOffered) }
    when {
        state.children.none { !it.archived } || !childrenDone -> AddChildrenScreen(
            state,
            onAdd = { family.perform(it) },
            onNext = {
                graph.prefs.childrenStepDone = true
                childrenDone = true
            },
            nextLabel = "Continue",
        )
        !starterOffered && state.chores.isEmpty() -> {
            fun offered() {
                graph.prefs.starterActivitiesOffered = true
                starterOffered = true
            }
            StarterActivitiesScreen(
                state,
                onDone = { actions ->
                    family.perform(Action.Record(actions.flatMap { (it as? Action.Record)?.payloads.orEmpty() }))
                    offered()
                },
                onSkip = ::offered,
            )
        }
        else -> content()
    }
}

@Composable
private fun FamilyFlow(graph: AppGraph, family: FamilyViewModel, state: FamilyState, link: WidgetLink?, onLinkHandled: () -> Unit) {
    val settings = state.family ?: return
    val locale = Locale.getDefault()
    val formats = remember(settings.zone, locale) { Formats(locale, settings.zone) }
    val snackbar = remember { SnackbarHostState() }
    val history by graph.history.collectAsState()
    var record by remember { mutableStateOf<RecordRequest?>(null) }
    val lastSync = Relative.lastSync(state, history, graph.deviceId, family.now())
    val nav = rememberNavController()
    val scope = FamilyScope(
        graph = graph,
        family = family,
        state = state,
        formats = formats,
        locale = locale,
        today = formats.localDate(state.asOf),
        nav = nav,
        coroutines = rememberCoroutineScope(),
        snackbar = snackbar,
        history = history,
        lastSync = lastSync,
        record = { record = it },
    )
    // SPEC FR-44: a widget tap opens Record or that child's account, once the family screens exist.
    LaunchedEffect(link) {
        when (link) {
            null -> return@LaunchedEffect
            WidgetLink.Record -> record = RecordRequest(null, RecordTab.CHORE)
            is WidgetLink.Account -> if (state.child(link.child) != null) {
                nav.navigate(Routes.account(link.child)) { popUpTo(Routes.HOME) { inclusive = false } }
            }
        }
        onLinkHandled()
    }
    Box(Modifier.fillMaxSize()) {
        NavHost(nav, startDestination = Routes.HOME) { familyDestinations(scope) }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = SNACKBAR_CLEARANCE)) { data ->
            Snackbar(data, containerColor = Hp.colors.ink, contentColor = Hp.colors.ground, actionColor = Hp.colors.ground)
        }
    }
    record?.let { request ->
        RecordSheet(
            state = state,
            formats = formats,
            today = scope.today,
            now = family::now,
            preselected = request.child,
            startTab = request.tab,
            lastSyncNotice = lastSync.cashOutNotice,
            perform = family::execute,
            onDismiss = { record = null },
        )
    }
}

/** Undo for one-tap records: a reversal, so the history stays honest (SPEC FR-16). */
internal suspend fun offerUndo(snackbar: SnackbarHostState, family: FamilyViewModel, what: String, outcome: Outcome.Recorded) {
    val result = snackbar.showSnackbar(what, actionLabel = "Undo", withDismissAction = false, duration = SnackbarDuration.Long)
    if (result != SnackbarResult.ActionPerformed) return
    val state = family.state() ?: return
    outcome.entries.forEach { entry ->
        val line = state.account(entry.childId)?.lines?.firstOrNull { it.entry.entryId == entry.entryId } ?: return@forEach
        family.execute(FamilyActions.reverse(state, line, "Undone straight away"))
    }
}

/** How many recent syncs the Sync and Diagnostics screens list. */
internal const val HISTORY_SHOWN = 20

/** Keeps the snackbar above the full-width Record button. */
private val SNACKBAR_CLEARANCE = 88.dp
