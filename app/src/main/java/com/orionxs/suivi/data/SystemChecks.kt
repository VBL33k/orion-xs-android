package com.orionxs.suivi.data

import android.Manifest
import android.app.ActivityManager
import android.app.AlarmManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

/** Toutes les conditions nécessaires au bon fonctionnement. */
data class Health(
    val scanPerm: Boolean,
    val bgLocationNeeded: Boolean,
    val bgLocation: Boolean,
    val notifNeeded: Boolean,
    val notif: Boolean,
    val bluetoothOn: Boolean,
    val locationNeeded: Boolean,
    val locationOn: Boolean,
    val batteryUnrestricted: Boolean,
    val backgroundRestricted: Boolean,
    val storageNeeded: Boolean,
    val storage: Boolean,
    val exactAlarms: Boolean,
    val keyOk: Boolean,
) {
    /** Tout ce qui empêche le suivi en arrière-plan. */
    val blocking: Boolean
        get() = !scanPerm || !bluetoothOn || (locationNeeded && !locationOn) || !keyOk
    val allGood: Boolean
        get() = !blocking && batteryUnrestricted && !backgroundRestricted &&
            (!bgLocationNeeded || bgLocation) && (!notifNeeded || notif) && (!storageNeeded || storage)
}

object SystemChecks {
    private fun granted(c: Context, p: String) = ContextCompat.checkSelfPermission(c, p) == PackageManager.PERMISSION_GRANTED

    /** Permissions à demander pour le Bluetooth (selon la version d'Android). */
    fun scanPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    /** Toutes les permissions « simples » demandées en une fois. */
    fun basicPermissions(): Array<String> = buildList {
        addAll(scanPermissions())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }.toTypedArray()

    fun hasScanPermission(c: Context) = scanPermissions().all { granted(c, it) } ||
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && granted(c, Manifest.permission.ACCESS_FINE_LOCATION))

    fun bgLocationNeeded() = Build.VERSION.SDK_INT in Build.VERSION_CODES.Q..Build.VERSION_CODES.R

    fun bluetoothOn(c: Context): Boolean = runCatching {
        (c.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter?.isEnabled == true
    }.getOrDefault(false)

    fun hasBluetoothLe(c: Context) = c.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

    fun check(c: Context): Health {
        val pm = c.getSystemService(Context.POWER_SERVICE) as PowerManager
        val am = c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val lm = c.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val alarm = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return Health(
            scanPerm = hasScanPermission(c),
            bgLocationNeeded = bgLocationNeeded(),
            bgLocation = !bgLocationNeeded() || granted(c, Manifest.permission.ACCESS_BACKGROUND_LOCATION),
            notifNeeded = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
            notif = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || granted(c, Manifest.permission.POST_NOTIFICATIONS),
            bluetoothOn = bluetoothOn(c),
            locationNeeded = Build.VERSION.SDK_INT < Build.VERSION_CODES.S,
            locationOn = LocationManagerCompat.isLocationEnabled(lm),
            batteryUnrestricted = pm.isIgnoringBatteryOptimizations(c.packageName),
            backgroundRestricted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && am.isBackgroundRestricted,
            storageNeeded = Build.VERSION.SDK_INT <= Build.VERSION_CODES.P,
            storage = Build.VERSION.SDK_INT > Build.VERSION_CODES.P || granted(c, Manifest.permission.WRITE_EXTERNAL_STORAGE),
            exactAlarms = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarm.canScheduleExactAlarms(),
            keyOk = Prefs(c).keyBytes != null,
        )
    }
}
