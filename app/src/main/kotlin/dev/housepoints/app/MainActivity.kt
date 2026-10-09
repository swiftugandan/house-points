package dev.housepoints.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.housepoints.app.family.FamilySnapshot
import dev.housepoints.app.family.FamilyViewModel
import dev.housepoints.app.ui.AppNavigation
import dev.housepoints.app.ui.theme.Hp
import dev.housepoints.app.ui.theme.HousePointsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = (application as HousePointsApp).graph
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") // The factory only ever builds the one ViewModel type it is asked for here.
            override fun <T : ViewModel> create(modelClass: Class<T>): T = graph.familyViewModel() as T
        }
        setContent {
            HousePointsTheme {
                val family: FamilyViewModel = viewModel(factory = factory)
                val snapshot by family.snapshot.collectAsState()
                NotificationPermission(snapshot)
                Box(Modifier.fillMaxSize().background(Hp.colors.ground)) {
                    AppNavigation(graph, family, snapshot)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        (application as HousePointsApp).graph.setForeground(true)
    }

    override fun onResume() {
        super.onResume()
        (application as HousePointsApp).graph.repository.invalidate()
    }

    override fun onStop() {
        (application as HousePointsApp).graph.setForeground(false)
        super.onStop()
    }
}

/** Asks once for notification permission (API 33+) when a family exists, for the payday reminder. */
@Composable
private fun NotificationPermission(snapshot: FamilySnapshot) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(snapshot is FamilySnapshot.Ready) {
        if (snapshot is FamilySnapshot.Ready) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
