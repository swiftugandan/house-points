package dev.housepoints.app.ui

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import dev.housepoints.app.AppGraph
import dev.housepoints.app.KeyState
import dev.housepoints.app.family.FamilyViewModel
import dev.housepoints.app.sync.JoinScreen
import dev.housepoints.app.sync.PairingCode
import dev.housepoints.app.sync.SyncScreen
import dev.housepoints.app.ui.format.Formats
import dev.housepoints.app.ui.onboarding.CreateFamilyScreen
import dev.housepoints.app.ui.onboarding.WelcomeScreen
import dev.housepoints.nearby.NearbyPermissions
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
internal fun OnboardingFlow(graph: AppGraph, family: FamilyViewModel) {
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
        graph.prefs.starterActivitiesOffered = true
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
internal fun JoiningFlow(graph: AppGraph, key: KeyState.Held) {
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
