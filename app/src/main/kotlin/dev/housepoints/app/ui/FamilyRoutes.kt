package dev.housepoints.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import dev.housepoints.app.AppGraph
import dev.housepoints.app.child.ChildActivity
import dev.housepoints.app.family.Action
import dev.housepoints.app.family.FamilyActions
import dev.housepoints.app.family.FamilyViewModel
import dev.housepoints.app.family.Outcome
import dev.housepoints.app.platform.Motion
import dev.housepoints.app.platform.StatementImage
import dev.housepoints.app.ui.account.AccountModels
import dev.housepoints.app.ui.account.AccountScreen
import dev.housepoints.app.ui.account.GoalSheet
import dev.housepoints.app.ui.account.LineSheet
import dev.housepoints.app.ui.account.NoScreenLockSheet
import dev.housepoints.app.ui.format.Formats
import dev.housepoints.app.ui.home.HomeModels
import dev.housepoints.app.ui.home.HomeScreen
import dev.housepoints.app.ui.onboarding.AddChildrenScreen
import dev.housepoints.app.ui.payday.StatementScreen
import dev.housepoints.app.ui.payday.Statements
import dev.housepoints.app.ui.record.RecordTab
import dev.housepoints.app.ui.settings.ChildrenSettings
import dev.housepoints.app.ui.settings.ChoreEditor
import dev.housepoints.app.ui.settings.JobsSettings
import dev.housepoints.app.ui.settings.MoneyRules
import dev.housepoints.app.ui.settings.SettingsScreen
import dev.housepoints.app.ui.settings.ValuesSettings
import dev.housepoints.contracts.ChildId
import dev.housepoints.contracts.ChoreId
import dev.housepoints.data.SyncHistoryEntry
import dev.housepoints.ledger.FamilyState
import dev.housepoints.ledger.LedgerLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

/** Where new versions are published; opened in the browser only when the parent asks. */
private const val RELEASES_URL = "https://github.com/swiftugandan/house-points/releases/latest"

/** Opens the Record sheet, optionally for one child and on a given tab. */
internal data class RecordRequest(val child: ChildId?, val tab: RecordTab)

/** Everything a family screen needs, so route functions don't each take a long parameter list. */
internal class FamilyScope(
    val graph: AppGraph,
    val family: FamilyViewModel,
    val state: FamilyState,
    val formats: Formats,
    val locale: Locale,
    val today: LocalDate,
    val nav: NavHostController,
    val coroutines: CoroutineScope,
    val snackbar: SnackbarHostState,
    val history: List<SyncHistoryEntry>,
    val lastSync: LastSync,
    val record: (RecordRequest) -> Unit,
) {
    fun back() {
        nav.popBackStack()
    }
}

internal object Routes {
    const val HOME = "home"
    const val SYNC = "sync"
    const val SETTINGS = "settings"
    const val CHILD_ARG = "child"
    const val WEEK_ARG = "week"
    const val CHORE_ARG = "chore"

    fun account(child: ChildId) = "account/$child"
    fun payday(child: ChildId, week: LocalDate) = "payday/$child/$week"
    fun settings(page: String) = "settings/$page"
    fun editChore(chore: ChoreId?) = "settings/jobs/edit?chore=${chore ?: ""}"
}

internal fun NavGraphBuilder.familyDestinations(f: FamilyScope) {
    composable(Routes.HOME) { HomeRoute(f) }
    composable("account/{${Routes.CHILD_ARG}}") { entry ->
        entry.arguments?.getString(Routes.CHILD_ARG)?.let { AccountRoute(f, ChildId(UUID.fromString(it))) }
    }
    composable("payday/{${Routes.CHILD_ARG}}/{${Routes.WEEK_ARG}}") { entry ->
        val child = entry.arguments?.getString(Routes.CHILD_ARG)?.let { ChildId(UUID.fromString(it)) }
        val week = entry.arguments?.getString(Routes.WEEK_ARG)?.let(LocalDate::parse)
        if (child != null && week != null) PaydayRoute(f, child, week)
    }
    composable(Routes.SYNC) { SyncRoute(f.graph, f.state, f.formats, f.history, onBack = f::back) }
    settingsDestinations(f)
}

private fun NavGraphBuilder.settingsDestinations(f: FamilyScope) {
    composable(Routes.SETTINGS) {
        val context = LocalContext.current
        val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
        SettingsScreen(
            f.state.family?.name.orEmpty(),
            version = version,
            onBack = f::back,
            onOpen = { page -> f.nav.navigate(Routes.settings(page.name.lowercase())) },
            onCheckForUpdate = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL))) },
        )
    }
    composable(Routes.settings("children")) {
        var adding by remember { mutableStateOf(false) }
        if (adding) {
            AddChildrenScreen(f.state, onAdd = { f.family.perform(it) }, onNext = { adding = false }, nextLabel = "Done", onBack = { adding = false })
        } else {
            ChildrenSettings(f.state, onBack = f::back, onAdd = { adding = true }, perform = { f.family.perform(it) })
        }
    }
    composable(Routes.settings("jobs")) {
        JobsSettings(f.state, f.formats, f.locale, onBack = f::back, onEdit = { f.nav.navigate(Routes.editChore(it)) })
    }
    composable("settings/jobs/edit?${Routes.CHORE_ARG}={${Routes.CHORE_ARG}}") { entry ->
        val chore = entry.arguments?.getString(Routes.CHORE_ARG)?.takeIf { it.isNotBlank() }?.let { f.state.chore(ChoreId(UUID.fromString(it))) }
        ChoreEditor(f.state, chore, f.formats, f.locale, onBack = f::back, perform = { f.family.perform(it) })
    }
    composable(Routes.settings("values")) { ValuesSettings(f.state, onBack = f::back, perform = { f.family.perform(it) }) }
    composable(Routes.settings("money")) {
        MoneyRules(f.state, f.formats, f.family.now(), onBack = f::back, perform = { actions ->
            f.family.perform(Action.Record(actions.flatMap { (it as? Action.Record)?.payloads.orEmpty() }))
        })
    }
    composable(Routes.settings("phones")) { PhonesRoute(f.graph, f.family, f.state, f.formats, f.history, onBack = f::back) }
    composable(Routes.settings("backup")) { BackupRoute(f.graph, f.state, onBack = f::back) }
    composable(Routes.settings("diagnostics")) { DiagnosticsRoute(f.graph, f.state, f.formats, f.history, onBack = f::back) }
}

@Composable
private fun HomeRoute(f: FamilyScope) {
    val model = remember(f.state, f.today) { HomeModels.from(f.state, f.today, f.formats, f.locale) }
    val recalculations by f.graph.sync.recalculations.collectAsState()
    HomeScreen(
        model = model,
        notices = recalculations.map { "${f.state.child(it.child)?.name ?: "A child"} · Interest recalculated: ${f.formats.signed(it.change)}" },
        onDismissNotices = f.graph.sync::dismissRecalculations,
        syncLine = f.lastSync.line,
        onSettings = { f.nav.navigate(Routes.SETTINGS) },
        onSync = { f.nav.navigate(Routes.SYNC) },
        onChild = { f.nav.navigate(Routes.account(it)) },
        onRecordDue = { child, due ->
            val action = FamilyActions.recordDueChore(child, due, f.family.now())
            f.coroutines.launch {
                val outcome = f.family.execute(action)
                val what = "${f.state.child(child)?.name} · ${due.chore.title} · ${f.formats.signed(due.chore.points)}"
                if (outcome is Outcome.Recorded) offerUndo(f.snackbar, f.family, what, outcome)
            }
        },
        onMoreDue = { f.record(RecordRequest(it, RecordTab.CHORE)) },
        onBounties = { f.record(RecordRequest(null, RecordTab.CHORE)) },
        onRecord = { f.record(RecordRequest(null, RecordTab.CHORE)) },
        onAddChild = { f.nav.navigate(Routes.settings("children")) },
    )
}

@Composable
private fun AccountRoute(f: FamilyScope, child: ChildId) {
    val context = LocalContext.current
    val model = remember(f.state, child, f.today) { AccountModels.from(f.state, child, f.today, f.formats, f.locale) } ?: return
    var line by remember { mutableStateOf<LedgerLine?>(null) }
    var goal by remember { mutableStateOf(false) }
    var noLock by remember { mutableStateOf(false) }
    fun handOver() = context.startActivity(ChildActivity.intent(context, child))
    AccountScreen(
        model = model,
        onBack = f::back,
        onHandOver = { if (ChildActivity.exitIsProtected(context)) handOver() else noLock = true },
        onRecord = { f.record(RecordRequest(child, RecordTab.CHORE)) },
        onLine = { line = it },
        onPayday = { week -> f.nav.navigate(Routes.payday(child, week)) },
        onTick = { tick -> f.family.perform(FamilyActions.tick(tick.chore, tick.child, f.today, tick.done)) },
        onGoal = { goal = true },
    )
    line?.let { selected -> EntrySheet(f, selected) { line = null } }
    if (noLock) {
        NoScreenLockSheet(model.name, onHandOver = { noLock = false; handOver() }, onDismiss = { noLock = false })
    }
    if (goal) {
        val active = f.state.activeGoal(child)
        GoalSheet(
            model.name, model.goal, f.formats,
            onSave = { title, icon, target -> f.family.perform(FamilyActions.setGoal(f.state, child, title, icon, target)) },
            onReached = active?.let { { f.family.perform(FamilyActions.closeGoal(it.id, reached = true)) } },
            onDismiss = { goal = false },
        )
    }
}

@Composable
private fun EntrySheet(f: FamilyScope, selected: LedgerLine, onDismiss: () -> Unit) {
    val row = AccountModels.lineModel(f.state, selected, f.formats)
    val who = f.state.devices.firstOrNull { it.id == selected.recordedBy }?.name?.ifBlank { null } ?: "a phone"
    val day = f.formats.longDay(f.formats.localDate(selected.entry.effectiveAt))
    LineSheet(
        selected,
        title = row.title,
        detail = listOfNotNull(row.note, "${row.amount} on $day", row.amountSub).joinToString("\n"),
        recordedBy = "Recorded on $who",
        reversal = { reason ->
            if (selected.entry.lock != null) FamilyActions.reverseLock(f.state, selected, reason)
            else FamilyActions.reverse(f.state, selected, reason)
        },
        perform = f.family::execute,
        onDismiss = onDismiss,
    )
}

@Composable
private fun PaydayRoute(f: FamilyScope, child: ChildId, week: LocalDate) {
    val context = LocalContext.current
    val model = remember(f.state, child, week) { Statements.from(f.state, child, week, f.formats, f.locale) } ?: return
    StatementScreen(model, animate = Motion.enabled(context), onClose = f::back, onShare = {
        f.coroutines.launch { StatementImage(context).share(model) }
    })
}
