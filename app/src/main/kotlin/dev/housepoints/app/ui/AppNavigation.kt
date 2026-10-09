package dev.housepoints.app.ui

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import dev.housepoints.app.AppGraph
import dev.housepoints.app.KeyState
import dev.housepoints.app.child.ChildActivity
import dev.housepoints.app.family.Action
import dev.housepoints.app.family.FamilyActions
import dev.housepoints.app.family.FamilySnapshot
import dev.housepoints.app.family.FamilyViewModel
import dev.housepoints.app.family.Outcome
import dev.housepoints.app.platform.Motion
import dev.housepoints.app.platform.StatementImage
import dev.housepoints.app.sync.JoinScreen
import dev.housepoints.app.sync.PairingCode
import dev.housepoints.app.sync.PhoneRow
import dev.housepoints.app.sync.SyncScreen
import dev.housepoints.app.ui.account.AccountModels
import dev.housepoints.app.ui.account.AccountScreen
import dev.housepoints.app.ui.account.GoalSheet
import dev.housepoints.app.ui.account.LineSheet
import dev.housepoints.app.ui.format.Formats
import dev.housepoints.app.ui.home.HomeModels
import dev.housepoints.app.ui.home.HomeScreen
import dev.housepoints.app.ui.onboarding.AddChildrenScreen
import dev.housepoints.app.ui.onboarding.CreateFamilyScreen
import dev.housepoints.app.ui.onboarding.StarterJobsScreen
import dev.housepoints.app.ui.onboarding.WelcomeScreen
import dev.housepoints.app.ui.payday.StatementScreen
import dev.housepoints.app.ui.payday.Statements
import dev.housepoints.app.ui.record.RecordSheet
import dev.housepoints.app.ui.record.RecordTab
import dev.housepoints.app.ui.settings.BackupScreen
import dev.housepoints.app.ui.settings.ChildrenSettings
import dev.housepoints.app.ui.settings.ChoreEditor
import dev.housepoints.app.ui.settings.DiagnosticsScreen
import dev.housepoints.app.ui.settings.JobsSettings
import dev.housepoints.app.ui.settings.MoneyRules
import dev.housepoints.app.ui.settings.PhonesScreen
import dev.housepoints.app.ui.settings.SettingsScreen
import dev.housepoints.app.ui.settings.ValuesSettings
import dev.housepoints.app.ui.theme.Hp
import dev.housepoints.contracts.ChildId
import dev.housepoints.contracts.ChoreId
import dev.housepoints.contracts.DeviceRemoved
import dev.housepoints.contracts.DeviceUpsert
import dev.housepoints.data.ExportCodec
import dev.housepoints.data.ExportResult
import dev.housepoints.ledger.FamilyState
import dev.housepoints.ledger.LedgerLine
import dev.housepoints.nearby.NearbyPermissions
import dev.housepoints.sync.FamilyKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.UUID

private data class RecordRequest(val child: ChildId?, val tab: RecordTab)

@Composable
fun AppNavigation(graph: AppGraph, family: FamilyViewModel, snapshot: FamilySnapshot) {
    val key by graph.key.collectAsState()
    when {
        snapshot is FamilySnapshot.Loading || key is KeyState.Loading -> Unit
        snapshot is FamilySnapshot.NoFamily && key is KeyState.Held -> JoiningFlow(graph, key as KeyState.Held)
        snapshot is FamilySnapshot.NoFamily -> OnboardingFlow(graph, family)
        snapshot is FamilySnapshot.Ready -> FamilyFlow(graph, family, snapshot.state)
    }
}

@Composable
private fun OnboardingFlow(graph: AppGraph, family: FamilyViewModel) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var phoneName by remember { mutableStateOf(graph.prefs.pendingPhoneName ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    fun useCode(text: String) {
        val decoded = PairingCode.decode(text)
        if (decoded == null) {
            error = "That isn't a House Points pairing code."
            return
        }
        graph.prefs.pendingPhoneName = phoneName.trim()
        graph.prefs.childrenStepDone = true
        graph.prefs.starterJobsOffered = true
        scope.launch { graph.store(decoded.first, decoded.second) }
    }
    NavHost(nav, startDestination = "welcome") {
        composable("welcome") { WelcomeScreen(onStart = { nav.navigate("create") }, onJoin = { nav.navigate("join") }) }
        composable("create") {
            val locale = Locale.getDefault()
            CreateFamilyScreen(
                defaultCurrency = Formats.defaultCurrency(locale),
                defaultWeekStart = WeekFields.of(locale).firstDayOfWeek.takeIf { it == DayOfWeek.SUNDAY } ?: DayOfWeek.MONDAY,
                zone = ZoneId.systemDefault(),
                onBack = { nav.popBackStack() },
                onCreate = { name, currency, weekStart, phone -> family.createFamily(name, currency, ZoneId.systemDefault(), weekStart, phone) },
            )
        }
        composable("join") {
            JoinScreen(
                onBack = { nav.popBackStack() },
                onScan = {
                    val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
                    GmsBarcodeScanning.getClient(context, options).startScan()
                        .addOnSuccessListener { barcode -> barcode.rawValue?.let { code = it; useCode(it) } }
                        .addOnFailureListener { error = "The scanner isn't available. Paste the code from the other phone instead." }
                },
                codeText = code,
                onCodeText = { code = it; error = null },
                onUseCode = { useCode(code) },
                error = error,
                phoneName = phoneName,
                onPhoneName = { phoneName = it },
            )
        }
    }
}

/** A key from a pairing code but no family ops yet: the first sync brings the family over (SPEC FR-3). */
@Composable
private fun JoiningFlow(graph: AppGraph, key: KeyState.Held) {
    val syncState by graph.sync.state.collectAsState()
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        graph.sync.startManual(graph.bluetooth, key.family, key.key, graph.phoneName())
    }
    SyncScreen(
        state = syncState,
        phones = emptyList(),
        recalculationText = { "" },
        history = emptyList(),
        onBack = null,
        onLook = { graph.sync.startManual(graph.localNetwork, key.family, key.key, graph.phoneName()) },
        onBluetooth = { permissions.launch(NearbyPermissions.required(Build.VERSION.SDK_INT).toTypedArray()) },
        onDone = { graph.sync.reset(); graph.resumeAutomatic() },
        title = "Joining the family",
        intro = "Open House Points on a phone that's already in the family, on the same Wi-Fi. " +
            "The family's history comes across by itself.",
    )
}

@Composable
private fun FamilyFlow(graph: AppGraph, family: FamilyViewModel, state: FamilyState) {
    val settings = state.family ?: return
    val locale = Locale.getDefault()
    val formats = remember(settings.zone, locale) { Formats(locale, settings.zone) }
    val today = formats.localDate(state.asOf)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val history by graph.history.collectAsState()
    var record by remember { mutableStateOf<RecordRequest?>(null) }

    // A phone that joined by pairing code names itself once the family has arrived.
    LaunchedEffect(state.devices) {
        val pending = graph.prefs.pendingPhoneName
        if (pending != null && state.devices.none { it.id == graph.deviceId && it.name.isNotBlank() }) {
            family.perform(Action.Record(listOf(DeviceUpsert(graph.deviceId, pending)))) { graph.prefs.pendingPhoneName = null }
        }
    }

    val children = state.children.filter { !it.archived }
    var childrenDone by remember { mutableStateOf(graph.prefs.childrenStepDone) }
    if (children.isEmpty() || !childrenDone) {
        AddChildrenScreen(state, onAdd = { family.perform(it) }, onNext = {
            graph.prefs.childrenStepDone = true
            childrenDone = true
        }, nextLabel = "Continue")
        return
    }
    var starterOffered by remember { mutableStateOf(graph.prefs.starterJobsOffered) }
    if (!starterOffered && state.chores.isEmpty()) {
        fun offered() {
            graph.prefs.starterJobsOffered = true
            starterOffered = true
        }
        StarterJobsScreen(
            state,
            onDone = { actions ->
                family.perform(Action.Record(actions.flatMap { (it as? Action.Record)?.payloads.orEmpty() }))
                offered()
            },
            onSkip = ::offered,
        )
        return
    }

    val lastSync = Relative.lastSync(state, history, graph.deviceId, family.now())
    val nav = rememberNavController()
    Box(Modifier.fillMaxSize()) {
        NavHost(nav, startDestination = "home") {
            composable("home") {
                val model = remember(state, today) { HomeModels.from(state, today, formats, locale) }
                val recalculations by graph.sync.recalculations.collectAsState()
                HomeScreen(
                    model = model,
                    notices = recalculations.map { "${state.child(it.child)?.name ?: "A child"} · Interest recalculated: ${formats.signed(it.change)}" },
                    onDismissNotices = graph.sync::dismissRecalculations,
                    syncLine = lastSync.line,
                    onSettings = { nav.navigate("settings") },
                    onSync = { nav.navigate("sync") },
                    onChild = { nav.navigate("account/$it") },
                    onRecordDue = { child, due ->
                        val action = FamilyActions.recordDueChore(child, due, family.now())
                        scope.launch {
                            val outcome = family.execute(action)
                            if (outcome is Outcome.Recorded) offerUndo(snackbar, family, "${state.child(child)?.name} · ${due.chore.title} · ${formats.signed(due.chore.points)}", outcome)
                        }
                    },
                    onMoreDue = { record = RecordRequest(it, RecordTab.CHORE) },
                    onBounties = { record = RecordRequest(null, RecordTab.CHORE) },
                    onRecord = { record = RecordRequest(null, RecordTab.CHORE) },
                    onAddChild = { nav.navigate("settings/children") },
                )
            }
            composable("account/{child}") { entry ->
                val child = entry.arguments?.getString("child")?.let { ChildId(UUID.fromString(it)) } ?: return@composable
                val model = remember(state, child, today) { AccountModels.from(state, child, today, formats, locale) } ?: return@composable
                var line by remember { mutableStateOf<LedgerLine?>(null) }
                var goal by remember { mutableStateOf(false) }
                var noLock by remember { mutableStateOf(false) }
                AccountScreen(
                    model = model,
                    onBack = { nav.popBackStack() },
                    onHandOver = {
                        if (ChildActivity.exitIsProtected(context)) context.startActivity(ChildActivity.intent(context, child)) else noLock = true
                    },
                    onRecord = { record = RecordRequest(child, RecordTab.CHORE) },
                    onLine = { line = it },
                    onPayday = { week -> nav.navigate("payday/$child/$week") },
                    onTick = { tick -> family.perform(FamilyActions.tick(tick.chore, tick.child, today, tick.done)) },
                    onGoal = { goal = true },
                )
                line?.let { selected ->
                    val row = AccountModels.lineModel(state, selected, formats)
                    val who = state.devices.firstOrNull { it.id == selected.recordedBy }?.name?.ifBlank { null } ?: "a phone"
                    LineSheet(
                        selected,
                        title = row.title,
                        detail = listOfNotNull(row.note, "${row.amount} on ${formats.longDay(formats.localDate(selected.entry.effectiveAt))}", row.amountSub).joinToString("\n"),
                        recordedBy = "Recorded on $who",
                        perform = family::execute,
                        onDismiss = { line = null },
                    )
                }
                if (noLock) {
                    NoScreenLockSheet(model.name, onHandOver = { noLock = false; context.startActivity(ChildActivity.intent(context, child)) }, onDismiss = { noLock = false })
                }
                if (goal) {
                    val active = state.activeGoal(child)
                    GoalSheet(
                        model.name, model.goal, formats,
                        onSave = { title, icon, target -> family.perform(FamilyActions.setGoal(state, child, title, icon, target)) },
                        onReached = active?.let { { family.perform(FamilyActions.closeGoal(it.id, reached = true)) } },
                        onDismiss = { goal = false },
                    )
                }
            }
            composable("payday/{child}/{week}") { entry ->
                val child = entry.arguments?.getString("child")?.let { ChildId(UUID.fromString(it)) } ?: return@composable
                val week = entry.arguments?.getString("week")?.let(LocalDate::parse) ?: return@composable
                val model = remember(state, child, week) { Statements.from(state, child, week, formats, locale) } ?: return@composable
                StatementScreen(model, animate = Motion.enabled(context), onClose = { nav.popBackStack() }, onShare = {
                    scope.launch { StatementImage(context).share(model) }
                })
            }
            composable("sync") { SyncRoute(graph, state, formats, history, onBack = { nav.popBackStack() }) }
            composable("settings") {
                SettingsScreen(settings.name, onBack = { nav.popBackStack() }, onOpen = { page ->
                    nav.navigate("settings/" + page.name.lowercase())
                })
            }
            composable("settings/children") {
                var adding by remember { mutableStateOf(false) }
                if (adding) {
                    AddChildrenScreen(state, onAdd = { family.perform(it) }, onNext = { adding = false }, nextLabel = "Done", onBack = { adding = false })
                } else {
                    ChildrenSettings(state, onBack = { nav.popBackStack() }, onAdd = { adding = true }, perform = { family.perform(it) })
                }
            }
            composable("settings/jobs") { JobsSettings(state, formats, locale, onBack = { nav.popBackStack() }, onEdit = { nav.navigate("settings/jobs/edit?chore=${it ?: ""}") }) }
            composable("settings/jobs/edit?chore={chore}") { entry ->
                val chore = entry.arguments?.getString("chore")?.takeIf { it.isNotBlank() }?.let { state.chore(ChoreId(UUID.fromString(it))) }
                ChoreEditor(state, chore, formats, locale, onBack = { nav.popBackStack() }, perform = { family.perform(it) })
            }
            composable("settings/values") { ValuesSettings(state, onBack = { nav.popBackStack() }, perform = { family.perform(it) }) }
            composable("settings/money") {
                MoneyRules(state, formats, family.now(), onBack = { nav.popBackStack() }, perform = { actions ->
                    family.perform(Action.Record(actions.flatMap { (it as? Action.Record)?.payloads.orEmpty() }))
                })
            }
            composable("settings/phones") { PhonesRoute(graph, family, state, formats, history, onBack = { nav.popBackStack() }) }
            composable("settings/backup") { BackupRoute(graph, state, onBack = { nav.popBackStack() }) }
            composable("settings/diagnostics") { DiagnosticsRoute(graph, state, formats, history, onBack = { nav.popBackStack() }) }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 88.dp)) { data ->
            Snackbar(data, containerColor = Hp.colors.ink, contentColor = Hp.colors.ground, actionColor = Hp.colors.ground)
        }
    }

    record?.let { request ->
        RecordSheet(
            state = state,
            formats = formats,
            today = today,
            now = family::now,
            preselected = request.child,
            startTab = request.tab,
            lastSyncNotice = lastSync.cashOutNotice,
            perform = family::execute,
            onDismiss = { record = null },
        )
    }
}

/** NFR-SEC-4: say plainly when the child view cannot be protected, before handing the phone over. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun NoScreenLockSheet(name: String, onHandOver: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Hp.colors.surface, shape = dev.housepoints.app.ui.theme.Radius.sheet) {
        androidx.compose.foundation.layout.Column(
            Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
        ) {
            androidx.compose.material3.Text("This phone has no screen lock", style = Hp.type.headline, color = Hp.colors.ink)
            androidx.compose.material3.Text(
                "Without a PIN, pattern or fingerprint, $name can leave the child view and use the rest of the phone. " +
                    "Set a screen lock in Android settings to keep the child view closed.",
                style = Hp.type.body, color = Hp.colors.ink,
            )
            dev.housepoints.app.ui.components.OutcomeButton("Hand to $name anyway", onHandOver)
        }
    }
}

/** Undo for one-tap records: a reversal, so the history stays honest (SPEC FR-16). */
private suspend fun offerUndo(snackbar: SnackbarHostState, family: FamilyViewModel, what: String, outcome: Outcome.Recorded) {
    val result = snackbar.showSnackbar(what, actionLabel = "Undo", withDismissAction = false, duration = SnackbarDuration.Long)
    if (result != SnackbarResult.ActionPerformed) return
    val state = family.state() ?: return
    outcome.entries.forEach { entry ->
        val line = state.account(entry.childId)?.lines?.firstOrNull { it.entry.entryId == entry.entryId } ?: return@forEach
        family.execute(FamilyActions.reverse(line, "Undone straight away"))
    }
}

@Composable
private fun SyncRoute(graph: AppGraph, state: FamilyState, formats: Formats, history: List<dev.housepoints.data.SyncHistoryEntry>, onBack: () -> Unit) {
    val key by graph.key.collectAsState()
    val syncState by graph.sync.state.collectAsState()
    val held = key as? KeyState.Held
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (held != null) graph.sync.startManual(graph.bluetooth, held.family, held.key, graph.phoneName())
    }
    val phones = state.devices.filter { !it.removed && it.id != graph.deviceId }.map { device ->
        PhoneRow(device.name.ifBlank { "A phone" }, Relative.lastSyncedWith(history, device.id, graph.clock.now()) ?: "Not synced yet")
    }
    fun leave() {
        graph.sync.reset()
        graph.resumeAutomatic()
        onBack()
    }
    SyncScreen(
        state = syncState,
        phones = phones,
        recalculationText = { recalc ->
            val name = state.child(recalc.child)?.name ?: "A child"
            "$name · Interest recalculated: ${formats.signed(recalc.change)}"
        },
        history = history.takeLast(HISTORY_SHOWN).asReversed().map { Relative.historyLine(it, state, formats) },
        onBack = ::leave,
        onLook = { if (held != null) graph.sync.startManual(graph.localNetwork, held.family, held.key, graph.phoneName()) },
        onBluetooth = { if (held != null) permissions.launch(NearbyPermissions.required(Build.VERSION.SDK_INT).toTypedArray()) },
        onDone = ::leave,
    )
}

@Composable
private fun PhonesRoute(graph: AppGraph, family: FamilyViewModel, state: FamilyState, formats: Formats, history: List<dev.housepoints.data.SyncHistoryEntry>, onBack: () -> Unit) {
    val key by graph.key.collectAsState()
    val scope = rememberCoroutineScope()
    val held = key as? KeyState.Held
    PhonesScreen(
        devices = state.devices,
        self = graph.deviceId,
        lastSynced = { Relative.lastSyncedWith(history, it, graph.clock.now()) },
        pairingCode = held?.let { PairingCode.encode(it.family, it.key) },
        onBack = onBack,
        onRename = { family.perform(FamilyActions.nameDevice(graph.deviceId, it)) },
        onRemove = { device ->
            val familyId = held?.family ?: return@PhonesScreen
            scope.launch {
                family.execute(Action.Record(listOf(DeviceRemoved(device.id))))
                graph.store(familyId, FamilyKey.generate())
            }
        },
    )
}

@Composable
private fun BackupRoute(graph: AppGraph, state: FamilyState, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var pendingPassphrase by remember { mutableStateOf<CharArray?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val passphrase = pendingPassphrase ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            message = withContext(Dispatchers.IO) {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext "That file couldn't be opened."
                when (val result = ExportCodec.decrypt(bytes, passphrase)) {
                    ExportResult.WrongPassphrase -> "That passphrase doesn't open this file."
                    is ExportResult.Corrupt -> "That file is damaged or isn't a House Points copy."
                    is ExportResult.Ok -> {
                        val familyId = state.family?.id
                        if (result.ops.any { it.familyId != familyId }) "That copy belongs to a different family."
                        else "Restored. ${graph.opLog.append(result.ops).added} entries were new to this phone."
                    }
                }
            }
        }
    }
    BackupScreen(
        onBack = onBack,
        onExport = { passphrase ->
            scope.launch {
                val file = withContext(Dispatchers.IO) {
                    val bytes = ExportCodec.encrypt(graph.opLog.all(), passphrase)
                    File(File(context.cacheDir, "shared").apply { mkdirs() }, "house-points-${LocalDate.now()}.hpx").apply { writeBytes(bytes) }
                }
                val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".files", file)
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "application/octet-stream"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(android.content.Intent.createChooser(send, "Save the encrypted copy"))
                message = "Encrypted copy made. Keep the passphrase somewhere safe: without it the copy can't be opened."
            }
        },
        onImport = { passphrase ->
            pendingPassphrase = passphrase
            picker.launch(arrayOf("*/*"))
        },
        message = message,
    )
}

@Composable
private fun DiagnosticsRoute(graph: AppGraph, state: FamilyState, formats: Formats, history: List<dev.housepoints.data.SyncHistoryEntry>, onBack: () -> Unit) {
    var counts by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(state) {
        val vector = graph.opLog.vector()
        counts = listOf(
            "This phone" to graph.deviceId.toString(),
            "Family" to (state.family?.id?.toString() ?: "none"),
            "Operations in the log" to graph.opLog.all().size.toString(),
            "Known per phone" to vector.entries.entries.joinToString("\n") { (device, seq) ->
                (state.devices.firstOrNull { it.id == device }?.name?.ifBlank { null } ?: device.toString().take(8)) + ": " + seq.value
            },
            "Flags" to state.flags.size.toString(),
        )
    }
    DiagnosticsScreen(counts, history.takeLast(HISTORY_SHOWN).asReversed().map { Relative.historyLine(it, state, formats) + " · " + it.detail.ifBlank { "ok" } }, onBack)
}

private const val HISTORY_SHOWN = 20
