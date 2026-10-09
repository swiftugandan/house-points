package dev.housepoints.app.child

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import dev.housepoints.app.HousePointsApp
import dev.housepoints.app.family.FamilySnapshot
import dev.housepoints.app.platform.Motion
import dev.housepoints.app.ui.format.Formats
import dev.housepoints.app.ui.theme.HousePointsTheme
import dev.housepoints.contracts.ChildId
import dev.housepoints.contracts.DisplayStyle
import java.util.Locale
import java.util.UUID

/**
 * The phone handed to a child (SPEC FR-40, NFR-SEC-4). It asks Android to pin the screen, hides the system
 * bars, and needs the parent's fingerprint or screen lock to leave. A child can still leave if pinning is
 * switched off in Android settings; the app does not claim otherwise.
 */
class ChildActivity : FragmentActivity() {
    private var speech: TextToSpeech? = null
    private var speechReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = askToLeave()
        })
        speech = TextToSpeech(this) { status -> speechReady = status == TextToSpeech.SUCCESS }

        val child = intent.getStringExtra(EXTRA_CHILD)?.let { runCatching { ChildId(UUID.fromString(it)) }.getOrNull() }
        if (child == null) {
            finish()
            return
        }
        val graph = (application as HousePointsApp).graph
        val animate = Motion.enabled(this)
        setContent {
            HousePointsTheme {
                val snapshot by graph.repository.snapshot.collectAsState()
                val ready = snapshot as? FamilySnapshot.Ready ?: return@HousePointsTheme
                val state = ready.state
                val family = state.family ?: return@HousePointsTheme
                val formats = remember(family.zone) { Formats(Locale.getDefault(), family.zone) }
                val today = formats.localDate(state.asOf)
                val model = remember(state, child) { ChildViews.from(state, child, today, formats) } ?: return@HousePointsTheme
                if (model.style == DisplayStyle.PICTURE) {
                    PictureView(model, animate, onSpeak = ::speak, onExit = ::askToLeave)
                } else {
                    NumberView(model, afterWeeks = { ChildViews.afterWeeks(state, child, it) }, formatPoints = formats::points, onExit = ::askToLeave)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        pin()
    }

    override fun onDestroy() {
        speech?.shutdown()
        super.onDestroy()
    }

    private fun speak(sentence: String) {
        if (speechReady) speech?.speak(sentence, TextToSpeech.QUEUE_FLUSH, null, "sentence")
    }

    /** Screen pinning asks the person to confirm; when Android has it switched off this does nothing. */
    private fun pin() {
        val manager = getSystemService(ActivityManager::class.java)
        if (manager?.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) {
            try {
                startLockTask()
            } catch (e: IllegalArgumentException) {
                // Pinning unavailable on this device: the in-app parent check still guards leaving.
            } catch (e: SecurityException) {
                // As above: some vendor builds refuse lock task for non-default launchers.
            }
        }
    }

    private fun askToLeave() {
        val allowed = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
        if (!exitIsProtected(this)) {
            // No screen lock set up: there is nothing to check against, so leaving cannot be protected.
            leave()
            return
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = leave()
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Give the phone back")
                .setSubtitle("A parent unlocks to leave the child view")
                .setAllowedAuthenticators(allowed)
                .build(),
        )
    }

    private fun leave() {
        val manager = getSystemService(ActivityManager::class.java)
        if (manager?.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE) stopLockTask()
        finish()
    }

    companion object {
        private const val EXTRA_CHILD = "child"

        /** True when leaving can be guarded by the device's biometrics or screen lock. */
        fun exitIsProtected(context: Context): Boolean =
            BiometricManager.from(context).canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

        fun intent(context: Context, child: ChildId): Intent =
            Intent(context, ChildActivity::class.java).putExtra(EXTRA_CHILD, child.toString())
    }
}
