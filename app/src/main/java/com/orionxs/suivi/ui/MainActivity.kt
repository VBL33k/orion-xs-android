package com.orionxs.suivi.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.orionxs.suivi.data.LiveState
import com.orionxs.suivi.data.LogBook
import com.orionxs.suivi.data.Prefs
import com.orionxs.suivi.service.AlarmScheduler
import com.orionxs.suivi.service.MonitorService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { OrionTheme { OrionApp() } }
    }

    override fun onStart() {
        super.onStart()
        LiveState.uiVisible.value = true
        if (Prefs(this).setupDone) {
            AlarmScheduler.scheduleNext(this)
            MonitorService.start(this)
        }
    }

    override fun onStop() {
        LiveState.uiVisible.value = false
        super.onStop()
    }
}

@Composable
private fun OrionApp() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var setupDone by remember { mutableStateOf(prefs.setupDone) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val fix = rememberFixActions()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (setupDone) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                listOf(
                    Triple("Direct", Icons.Filled.Bolt, 0),
                    Triple("Historique", Icons.Filled.History, 1),
                    Triple("Aide", Icons.AutoMirrored.Filled.HelpOutline, 2),
                ).forEach { (label, icon, i) ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Icon(icon, null) },
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (!setupDone) {
                SetupScreen(fix) {
                    prefs.setupDone = true
                    if (prefs.monitorSince == 0L) prefs.monitorSince = System.currentTimeMillis()
                    LogBook.log(ctx, "Installation terminée")
                    AlarmScheduler.scheduleNext(ctx)
                    MonitorService.start(ctx)
                    setupDone = true
                }
            } else when (tab) {
                0 -> LiveScreen(fix) { tab = 2 }
                1 -> HistoryScreen()
                else -> HelpScreen(fix)
            }
        }
    }
}
