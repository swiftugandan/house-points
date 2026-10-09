package dev.housepoints.app.ui

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.housepoints.app.AppGraph
import dev.housepoints.app.KeyState
import dev.housepoints.app.family.Action
import dev.housepoints.app.family.FamilyActions
import dev.housepoints.app.family.FamilyViewModel
import dev.housepoints.app.sync.PairingCode
import dev.housepoints.app.sync.PhoneRow
import dev.housepoints.app.sync.SyncScreen
import dev.housepoints.app.ui.format.Formats
import dev.housepoints.app.ui.settings.PhonesScreen
import dev.housepoints.contracts.DeviceRemoved
import dev.housepoints.data.SyncHistoryEntry
import dev.housepoints.ledger.FamilyState
import dev.housepoints.nearby.NearbyPermissions
import dev.housepoints.sync.FamilyKey
import kotlinx.coroutines.launch

@Composable
internal fun SyncRoute(graph: AppGraph, state: FamilyState, formats: Formats, history: List<SyncHistoryEntry>, onBack: () -> Unit) {
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
internal fun PhonesRoute(graph: AppGraph, family: FamilyViewModel, state: FamilyState, formats: Formats, history: List<SyncHistoryEntry>, onBack: () -> Unit) {
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
