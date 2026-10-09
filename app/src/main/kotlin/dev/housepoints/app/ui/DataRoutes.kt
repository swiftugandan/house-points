package dev.housepoints.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import dev.housepoints.app.AppGraph
import dev.housepoints.app.ui.format.Formats
import dev.housepoints.app.ui.settings.BackupScreen
import dev.housepoints.app.ui.settings.DiagnosticsScreen
import dev.housepoints.data.ExportCodec
import dev.housepoints.data.ExportResult
import dev.housepoints.data.SyncHistoryEntry
import dev.housepoints.ledger.FamilyState
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun BackupRoute(graph: AppGraph, state: FamilyState, onBack: () -> Unit) {
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
                val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "application/octet-stream"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, "Save the encrypted copy"))
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
internal fun DiagnosticsRoute(graph: AppGraph, state: FamilyState, formats: Formats, history: List<SyncHistoryEntry>, onBack: () -> Unit) {
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
