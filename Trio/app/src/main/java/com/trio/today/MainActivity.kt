package com.trio.today

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.trio.today.ui.capture.QuickCaptureActivity
import com.trio.today.ui.later.LaterScreen
import com.trio.today.ui.setup.ReliabilityScreen
import com.trio.today.ui.theme.TrioTheme
import com.trio.today.ui.today.TodayScreen
import com.trio.today.ui.today.TodayViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The app has exactly three destinations, so it uses a small sealed screen
 * state rather than a navigation library. Adding one would mean a dependency,
 * a graph and a set of routes to maintain in exchange for nothing at this size.
 */
private sealed interface Screen {
    data object Today : Screen
    data object Later : Screen
    data object Reliability : Screen
}

class MainActivity : ComponentActivity() {

    private val viewModel: TodayViewModel by viewModels {
        val container = (application as TrioApp).container
        TodayViewModel.Factory(container.taskRepository, container.settingsStore)
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Reminders still schedule; only the visible notification is affected. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestNotificationPermissionIfNeeded()

        setContent {
            TrioTheme {
                var screen by remember { mutableStateOf<Screen>(Screen.Today) }
                var checkedSetup by remember { mutableStateOf(false) }

                val settings = (application as TrioApp).container.settingsStore

                // Show the reliability wizard once, on first run, and never again.
                LaunchedEffect(Unit) {
                    if (checkedSetup) return@LaunchedEffect
                    checkedSetup = true
                    if (!settings.hasSeenReliabilitySetup.first()) {
                        screen = Screen.Reliability
                    }
                }

                when (screen) {
                    Screen.Today -> TodayScreen(
                        viewModel = viewModel,
                        onOpenCapture = { startActivity(Intent(this, QuickCaptureActivity::class.java)) },
                        onOpenLater = { screen = Screen.Later },
                        onOpenTask = { /* Editing lands in a later stage; tapping is a no-op for now. */ },
                        modifier = Modifier.fillMaxSize(),
                    )

                    Screen.Later -> LaterScreenHost(
                        onBack = { screen = Screen.Today },
                    )

                    Screen.Reliability -> ReliabilityScreen(
                        onDone = {
                            lifecycleScope.launch { settings.setSeenReliabilitySetup(true) }
                            screen = Screen.Today
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    @Composable
    private fun LaterScreenHost(onBack: () -> Unit) {
        val container = (application as TrioApp).container
        val later by container.taskRepository.observeLater()
            .collectAsStateWithLifecycle(initialValue = emptyList())

        LaterScreen(
            viewModel = viewModel,
            laterTasks = later,
            onBack = onBack,
            modifier = Modifier.fillMaxSize(),
        )
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
