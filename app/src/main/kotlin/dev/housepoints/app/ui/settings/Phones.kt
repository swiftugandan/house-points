package dev.housepoints.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.housepoints.app.sync.PairingCodeView
import dev.housepoints.app.ui.components.HpIcons
import dev.housepoints.app.ui.components.OutcomeButton
import dev.housepoints.app.ui.components.QuietButton
import dev.housepoints.app.ui.components.Rule
import dev.housepoints.app.ui.components.SectionLabel
import dev.housepoints.app.ui.components.TopBar
import dev.housepoints.app.ui.onboarding.Field
import dev.housepoints.app.ui.theme.Hp
import dev.housepoints.app.ui.theme.Radius
import dev.housepoints.app.ui.theme.Space
import dev.housepoints.contracts.DeviceId
import dev.housepoints.ledger.DeviceRecord

/** SPEC FR-3, FR-4: pairing and removing phones. */
@Composable
fun PhonesScreen(
    devices: List<DeviceRecord>,
    self: DeviceId,
    lastSynced: (DeviceId) -> String?,
    pairingCode: String?,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
    onRemove: (DeviceRecord) -> Unit,
) {
    var showCode by rememberSaveable { mutableStateOf(false) }
    var removing by remember { mutableStateOf<DeviceRecord?>(null) }
    var renaming by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Hp.colors.ground)) {
        TopBar("Phones", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SectionLabel("In this family")
            Rule()
            devices.filter { !it.removed }.forEach { device ->
                val isSelf = device.id == self
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).background(Hp.colors.surface)
                        .clickable { if (isSelf) renaming = true else removing = device }.padding(horizontal = Space.l),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(HpIcons.Phone, contentDescription = null, tint = Hp.colors.ink, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(Space.m))
                    Column(Modifier.weight(1f)) {
                        Text(device.name.ifBlank { "A phone" } + if (isSelf) " (this phone)" else "", style = Hp.type.body, color = Hp.colors.ink)
                        Text(if (isSelf) "Tap to rename" else lastSynced(device.id) ?: "Not synced with this phone yet", style = Hp.type.caption, color = Hp.colors.inkMuted)
                    }
                    Icon(if (isSelf) HpIcons.Edit else HpIcons.ChevronRight, contentDescription = null, tint = Hp.colors.inkMuted, modifier = Modifier.size(20.dp))
                }
                Rule()
            }
            if (showCode && pairingCode != null) {
                PairingCodeView(pairingCode, Modifier.fillMaxWidth().padding(Space.l))
            }
        }
        if (!showCode) {
            OutcomeButton("Show pairing code", { showCode = true }, icon = HpIcons.Qr, enabled = pairingCode != null, modifier = Modifier.navigationBarsPadding().padding(Space.l))
        } else {
            Spacer(Modifier.navigationBarsPadding())
        }
    }
    removing?.let { device -> RemovePhoneSheet(device, onDismiss = { removing = null }, onRemove = { onRemove(device); removing = null; showCode = true }) }
    if (renaming) RenameSheet(devices.firstOrNull { it.id == self }?.name.orEmpty(), onDismiss = { renaming = false }, onRename = { onRename(it); renaming = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemovePhoneSheet(device: DeviceRecord, onDismiss: () -> Unit, onRemove: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Hp.colors.surface, shape = Radius.sheet) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Space.l, vertical = Space.s), verticalArrangement = Arrangement.spacedBy(Space.l)) {
            Text("Remove ${device.name.ifBlank { "this phone" }}?", style = Hp.type.headline, color = Hp.colors.ink)
            Text(
                "For a lost or replaced phone. Everything it recorded stays in the history. This phone gets a new pairing code, " +
                    "and every other phone you keep must scan it again before it can sync.",
                style = Hp.type.body, color = Hp.colors.ink,
            )
            OutcomeButton("Remove and make a new code", onRemove)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RenameSheet(current: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(current) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Hp.colors.surface, shape = Radius.sheet) {
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = Space.l, vertical = Space.s), verticalArrangement = Arrangement.spacedBy(Space.l)) {
            Field("This phone belongs to", name, { name = it })
            OutcomeButton("Save", { onRename(name) }, enabled = name.isNotBlank())
        }
    }
}

/** SPEC FR-46: an encrypted copy of the whole log, and restoring one. */
@Composable
fun BackupScreen(onBack: () -> Unit, onExport: (CharArray) -> Unit, onImport: (CharArray) -> Unit, message: String?) {
    var passphrase by rememberSaveable { mutableStateOf("") }
    var again by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(Hp.colors.ground).imePadding()) {
        TopBar("Back up", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.xl)) {
            Text(
                "Android's own backup already keeps a copy when it's switched on. This makes a separate file, locked with a passphrase, " +
                    "that you can keep wherever you like. It holds the history, not the pairing code: a restored phone pairs again.",
                style = Hp.type.body, color = Hp.colors.inkMuted,
            )
            dev.housepoints.app.ui.onboarding.Field("Passphrase", passphrase, { passphrase = it })
            dev.housepoints.app.ui.onboarding.Field("Passphrase again", again, { again = it })
            if (passphrase.isNotEmpty() && again.isNotEmpty() && passphrase != again) {
                Text("The two passphrases don't match.", style = Hp.type.body, color = Hp.colors.deduct)
            }
            if (message != null) Text(message, style = Hp.type.body, color = Hp.colors.ink)
            QuietButton("Restore from a file", { onImport(passphrase.toCharArray()) }, enabled = passphrase.length >= MIN_PASSPHRASE, modifier = Modifier.fillMaxWidth())
        }
        OutcomeButton(
            "Save an encrypted copy",
            { onExport(passphrase.toCharArray()) },
            enabled = passphrase.length >= MIN_PASSPHRASE && passphrase == again,
            icon = HpIcons.Share,
            modifier = Modifier.navigationBarsPadding().padding(Space.l),
        )
    }
}

@Composable
fun DiagnosticsScreen(lines: List<Pair<String, String>>, history: List<String>, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Hp.colors.ground)) {
        TopBar("Diagnostics", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Rule()
            lines.forEach { (label, value) ->
                Column(Modifier.fillMaxWidth().background(Hp.colors.surface).padding(horizontal = Space.l, vertical = 10.dp)) {
                    Text(label, style = Hp.type.label, color = Hp.colors.inkMuted)
                    Text(value, style = Hp.type.figure, color = Hp.colors.ink)
                }
                Rule()
            }
            SectionLabel("Last syncs")
            Rule()
            if (history.isEmpty()) Text("No syncs yet.", style = Hp.type.caption, color = Hp.colors.inkMuted, modifier = Modifier.padding(Space.l))
            history.forEach { line ->
                Text(line, style = Hp.type.caption, color = Hp.colors.ink, modifier = Modifier.fillMaxWidth().background(Hp.colors.surface).padding(horizontal = Space.l, vertical = 10.dp))
                Rule()
            }
        }
    }
}

private const val MIN_PASSPHRASE = 8
