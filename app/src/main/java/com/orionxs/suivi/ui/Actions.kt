package com.orionxs.suivi.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.orionxs.suivi.data.LogBook
import com.orionxs.suivi.data.SystemChecks
import com.orionxs.suivi.service.MonitorService

/** Actions de « réparation » proposées à l'utilisateur, chacune en un seul bouton. */
class FixActions(
    val requestPermissions: () -> Unit,
    val requestBackgroundLocation: () -> Unit,
    val requestBattery: () -> Unit,
    val enableBluetooth: () -> Unit,
    val openLocationSettings: () -> Unit,
    val openAppSettings: () -> Unit,
    val openAlarmSettings: () -> Unit,
)

@SuppressLint("BatteryLife")
@Composable
fun rememberFixActions(): FixActions {
    val ctx = LocalContext.current
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        LogBook.log(ctx, "Autorisations : " + res.entries.joinToString { "${it.key.substringAfterLast('.')}=${if (it.value) "oui" else "non"}" })
        MonitorService.start(ctx)
    }
    val bgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        LogBook.log(ctx, "Localisation arrière-plan : ${if (it) "oui" else "non"}")
    }
    val simpleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        MonitorService.start(ctx)
    }
    return remember {
        FixActions(
            requestPermissions = { permLauncher.launch(SystemChecks.basicPermissions()) },
            requestBackgroundLocation = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            },
            requestBattery = {
                tryStart(ctx, simpleLauncher, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
                    || tryStart(ctx, simpleLauncher, appDetails(ctx))
            },
            enableBluetooth = {
                tryStart(ctx, simpleLauncher, Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                    || tryStart(ctx, simpleLauncher, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            },
            openLocationSettings = { tryStart(ctx, simpleLauncher, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
            openAppSettings = { tryStart(ctx, simpleLauncher, appDetails(ctx)) },
            openAlarmSettings = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    tryStart(ctx, simpleLauncher, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
            },
        )
    }
}

private fun appDetails(ctx: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))

private fun tryStart(
    ctx: Context,
    launcher: androidx.activity.result.ActivityResultLauncher<Intent>,
    intent: Intent,
): Boolean = try {
    launcher.launch(intent); true
} catch (e: Exception) {
    LogBook.log(ctx, "Impossible d'ouvrir ${intent.action} : ${e.javaClass.simpleName}")
    Toast.makeText(ctx, "Impossible d'ouvrir ce réglage", Toast.LENGTH_SHORT).show()
    false
}
