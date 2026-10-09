package dev.housepoints.app.sync

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.housepoints.app.ui.components.HpIcons
import dev.housepoints.app.ui.components.NoticeRow
import dev.housepoints.app.ui.components.OutcomeButton
import dev.housepoints.app.ui.components.QuietButton
import dev.housepoints.app.ui.components.Rule
import dev.housepoints.app.ui.components.SectionLabel
import dev.housepoints.app.ui.components.TopBar
import dev.housepoints.app.ui.theme.Hp
import dev.housepoints.app.ui.theme.Radius
import dev.housepoints.app.ui.theme.Space
import dev.housepoints.sync.RejectReason
import dev.housepoints.sync.SyncOutcome

data class PhoneRow(val name: String, val detail: String)

@Composable
fun SyncScreen(
    state: SyncState,
    phones: List<PhoneRow>,
    recalculationText: (Recalculation) -> String,
    history: List<String>,
    onBack: (() -> Unit)?,
    onLook: () -> Unit,
    onBluetooth: () -> Unit,
    onDone: () -> Unit,
    title: String = "Sync",
    intro: String = "Phones on the same Wi-Fi sync by themselves while House Points is open on both. " +
        "Nothing goes over the internet.",
) {
    val colors = Hp.colors
    Column(Modifier.fillMaxSize().background(colors.ground)) {
        TopBar(title, onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(intro, style = Hp.type.body, color = colors.inkMuted, modifier = Modifier.padding(Space.l))
            if (phones.isNotEmpty()) {
                Rule()
                phones.forEach { phone ->
                    Row(Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = Space.l, vertical = Space.m), verticalAlignment = Alignment.CenterVertically) {
                        Icon(HpIcons.Phone, contentDescription = null, tint = colors.ink, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(Space.m))
                        Column {
                            Text(phone.name, style = Hp.type.title.copy(fontSize = Hp.type.body.fontSize), color = colors.ink)
                            Text(phone.detail, style = Hp.type.caption, color = colors.inkMuted)
                        }
                    }
                    Rule()
                }
            }
            Column(Modifier.padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.m)) {
                when (state) {
                    SyncState.Idle -> Unit
                    is SyncState.Searching -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = colors.action, strokeWidth = 2.dp)
                        Spacer(Modifier.width(Space.m))
                        Text(
                            if (state.peers.isEmpty()) "Looking for the other phone. Open House Points on it too." else "Found ${state.peers.first().name.ifBlank { "the other phone" }}. Connecting…",
                            style = Hp.type.body, color = colors.ink,
                        )
                    }
                    is SyncState.Syncing -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = colors.action, strokeWidth = 2.dp)
                        Spacer(Modifier.width(Space.m))
                        Text("Syncing with ${state.peerName}…", style = Hp.type.body, color = colors.ink)
                    }
                    is SyncState.Finished -> Text(summary(state), style = Hp.type.title, color = colors.ink)
                    is SyncState.Failed -> Text(state.message, style = Hp.type.body, color = colors.deduct)
                }
            }
            if (state is SyncState.Finished && state.recalculated.isNotEmpty()) {
                Rule()
                state.recalculated.forEach { recalc ->
                    NoticeRow(recalculationText(recalc), "Entries recorded on the other phone changed a past week's smallest balance, so that week's interest changed too.")
                }
            }
            if (state !is SyncState.Searching && state !is SyncState.Syncing) {
                QuietButton(
                    "Not on the same Wi-Fi? Use Bluetooth",
                    onBluetooth,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Space.l),
                )
            }
            if (history.isNotEmpty()) {
                SectionLabel("Recent syncs")
                Rule()
                history.forEach { line ->
                    Text(line, style = Hp.type.caption, color = colors.inkMuted, modifier = Modifier.fillMaxWidth().background(colors.surface).padding(horizontal = Space.l, vertical = 10.dp))
                    Rule()
                }
            }
        }
        val busy = state is SyncState.Searching || state is SyncState.Syncing
        OutcomeButton(
            label = when (state) {
                is SyncState.Finished -> "Done"
                is SyncState.Failed -> "Try again"
                else -> "Sync now"
            },
            onClick = if (state is SyncState.Finished) onDone else onLook,
            enabled = !busy,
            icon = if (state is SyncState.Finished) null else HpIcons.Sync,
            modifier = Modifier.navigationBarsPadding().padding(Space.l),
        )
    }
}

private fun summary(state: SyncState.Finished): String = when (val outcome = state.outcome) {
    is SyncOutcome.Completed -> {
        val entries = if (state.newEntries == 1) "1 new entry came in." else "${state.newEntries} new entries came in."
        "Synced with ${state.peerName}. $entries"
    }
    is SyncOutcome.Rejected -> when (outcome.reason) {
        RejectReason.WRONG_FAMILY -> "That phone belongs to a different family."
        RejectReason.AUTHENTICATION_FAILED -> "That phone has an old pairing code. Pair it again from Settings, Phones."
        RejectReason.PROTOCOL_VERSION -> "That phone runs a different version of House Points. Update both phones."
        RejectReason.PROTOCOL_ERROR, RejectReason.TAMPERED -> "The sync was stopped because the data didn't check out. Nothing was changed."
    }
    is SyncOutcome.Interrupted -> "The phones lost touch. ${outcome.received} entries arrived and are kept; sync again for the rest."
}

/** The pairing code, as a QR code and as text (SPEC FR-3). */
@Composable
fun PairingCodeView(code: String, modifier: Modifier = Modifier) {
    val bitmap = remember(code) { PairingCode.qr(code, QR_PIXELS) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.m)) {
        Image(
            bitmap, contentDescription = null, filterQuality = FilterQuality.None,
            modifier = Modifier.fillMaxWidth(0.8f).aspectRatio(1f).background(androidx.compose.ui.graphics.Color.White, Radius.medium)
                .semantics { contentDescription = "Pairing QR code" },
        )
        Text("Scan this with the other phone: Settings, Phones, or \"Join the family\" on a new phone.", style = Hp.type.caption, color = Hp.colors.inkMuted)
        SelectionContainer { Text(code, style = Hp.type.caption.copy(fontFamily = Hp.type.figure.fontFamily), color = Hp.colors.inkMuted) }
    }
}

@Composable
fun JoinScreen(onBack: () -> Unit, onScan: () -> Unit, codeText: String, onCodeText: (String) -> Unit, onUseCode: () -> Unit, error: String?, phoneName: String, onPhoneName: (String) -> Unit) {
    Column(Modifier.fillMaxSize().background(Hp.colors.ground)) {
        TopBar("Join the family", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Space.l), verticalArrangement = Arrangement.spacedBy(Space.xl)) {
            Text(
                "On a phone already in the family, open Settings, Phones, Show pairing code. Then scan it here.",
                style = Hp.type.body, color = Hp.colors.inkMuted,
            )
            dev.housepoints.app.ui.onboarding.Field("This phone belongs to", phoneName, onPhoneName, placeholder = "e.g. Alex's phone")
            QuietButton("Scan the pairing code", onScan, icon = HpIcons.Qr, modifier = Modifier.fillMaxWidth(), enabled = phoneName.isNotBlank())
            dev.housepoints.app.ui.onboarding.Field("Or paste the code", codeText, onCodeText, placeholder = "hp1:…")
            if (error != null) Text(error, style = Hp.type.body, color = Hp.colors.deduct)
        }
        OutcomeButton("Use this code", onUseCode, enabled = codeText.isNotBlank() && phoneName.isNotBlank(), modifier = Modifier.navigationBarsPadding().padding(Space.l))
    }
}

private const val QR_PIXELS = 512
