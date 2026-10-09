package dev.housepoints.app.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.housepoints.app.ui.components.OutcomeButton
import dev.housepoints.app.ui.theme.Hp
import dev.housepoints.app.ui.theme.Radius
import dev.housepoints.app.ui.theme.Space

/** NFR-SEC-4: say plainly when the child view cannot be protected, before handing the phone over. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoScreenLockSheet(name: String, onHandOver: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Hp.colors.surface, shape = Radius.sheet) {
        Column(
            Modifier.navigationBarsPadding().padding(horizontal = Space.l, vertical = Space.s),
            verticalArrangement = Arrangement.spacedBy(Space.l),
        ) {
            Text("This phone has no screen lock", style = Hp.type.headline, color = Hp.colors.ink)
            Text(
                "Without a PIN, pattern or fingerprint, $name can leave the child view and use the rest of the phone. " +
                    "Set a screen lock in Android settings to keep the child view closed.",
                style = Hp.type.body, color = Hp.colors.ink,
            )
            OutcomeButton("Hand to $name anyway", onHandOver)
        }
    }
}
