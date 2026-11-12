package com.orionxs.suivi.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.orionxs.suivi.R
import com.orionxs.suivi.ble.Reading
import com.orionxs.suivi.ble.VictronDecoder
import com.orionxs.suivi.ble.VictronTexts
import com.orionxs.suivi.data.CsvExporter
import com.orionxs.suivi.data.HistoryDb
import com.orionxs.suivi.data.LiveState
import com.orionxs.suivi.data.LogBook
import com.orionxs.suivi.data.Prefs
import com.orionxs.suivi.data.SystemChecks
import com.orionxs.suivi.data.VictronSeen
import com.orionxs.suivi.ui.MainActivity
import com.orionxs.suivi.ui.fmtV
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * Service de premier plan : écoute en continu les annonces Bluetooth du chargeur,
 * met à jour l'affichage et enregistre une mesure par heure.
 */
class MonitorService : Service() {

    companion object {
        const val ACTION_START = "com.orionxs.suivi.START"
        const val ACTION_TICK = "com.orionxs.suivi.TICK"
        const val ACTION_RELOAD = "com.orionxs.suivi.RELOAD"
        private const val NOTIF_ID = 42
        private const val CHANNEL = "suivi"
        private const val HOUR = 3_600_000L

        /** Démarre (ou réveille) le service si les conditions sont réunies. */
        fun start(context: Context, action: String = ACTION_START) {
            val prefs = Prefs(context)
            if (!prefs.setupDone || !SystemChecks.hasScanPermission(context)) return
            try {
                ContextCompat.startForegroundService(
                    context, Intent(context, MonitorService::class.java).setAction(action)
                )
            } catch (e: Exception) {
                LogBook.log(context, "Démarrage du service refusé : ${e.javaClass.simpleName} ${e.message}")
            }
        }

        fun hourStart(t: Long): Long {
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = t
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var prefs: Prefs
    private var keyBytes: ByteArray? = null
    private var scanner: BluetoothLeScanner? = null
    private val scanStarts = ArrayDeque<Long>()
    private var lastNotifAt = 0L
    private var lastNotifText = ""
    private var lastRecordedHour = 0L
    private var restartPending = false
    private var lastRestartAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        keyBytes = prefs.keyBytes
        lastRecordedHour = prefs.lastRecordedHour
        createChannel()
        goForeground("Démarrage…")
        LiveState.serviceRunning = true
        LiveState.serviceStartedAt = System.currentTimeMillis()
        LogBook.log(this, "Service démarré (Android ${Build.VERSION.RELEASE}, ${Build.MANUFACTURER} ${Build.MODEL})")
        if (prefs.monitorSince == 0L) prefs.monitorSince = System.currentTimeMillis()

        ContextCompat.registerReceiver(
            this, btReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED
        )
        // Écran affiché -> scan rapide ; écran caché -> scan économe.
        scope.launch { LiveState.uiVisible.drop(1).collect { restartScan("affichage ${if (it) "ouvert" else "fermé"}") } }
        handler.postDelayed(watchdog, 60_000)
        io.execute { CsvExporter.updateAutoFile(this) } // recrée le fichier s'il a été supprimé
        AlarmScheduler.scheduleNext(this)
        startScan()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Obligatoire à chaque startForegroundService()
        goForeground(lastNotifText.ifEmpty { "Recherche du chargeur…" })
        when (intent?.action) {
            ACTION_RELOAD -> {
                keyBytes = prefs.keyBytes
                LiveState.devices.clear()
                LogBook.log(this, "Clé rechargée")
                restartScan("nouvelle clé")
            }
            ACTION_TICK -> onTick()
            "com.orionxs.suivi.DEMO" -> if (com.orionxs.suivi.BuildConfig.DEBUG) startDemo()
            else -> if (!LiveState.scanning) startScan()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        LogBook.log(this, "Service arrêté")
        handler.removeCallbacksAndMessages(null)
        stopScan()
        runCatching { unregisterReceiver(btReceiver) }
        scope.cancel()
        LiveState.serviceRunning = false
        super.onDestroy()
    }

    // ------------------------------------------------------------------ Bluetooth

    @SuppressLint("MissingPermission")
    private fun startScan() {
        restartPending = false
        if (!SystemChecks.hasScanPermission(this)) {
            LiveState.lastScanError = "Autorisation Bluetooth manquante"; return
        }
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled) {
            LiveState.scanning = false
            LiveState.lastScanError = "Bluetooth éteint"
            updateNotification("Bluetooth éteint", force = true)
            return
        }
        // Android refuse plus de 5 démarrages de scan en 30 s : on temporise.
        val now = SystemClock.elapsedRealtime()
        while (scanStarts.isNotEmpty() && now - scanStarts.first() > 30_000) scanStarts.removeFirst()
        if (scanStarts.size >= 4) {
            restartPending = true
            handler.postDelayed({ startScan() }, 30_000 - (now - scanStarts.first()) + 1_000)
            return
        }
        stopScan()
        val s = adapter.bluetoothLeScanner ?: run { LiveState.lastScanError = "Scanner indisponible"; return }
        val visible = LiveState.uiVisible.value
        val settings = ScanSettings.Builder()
            .setScanMode(if (visible) ScanSettings.SCAN_MODE_LOW_LATENCY else ScanSettings.SCAN_MODE_LOW_POWER)
            .setReportDelay(0)
            .build()
        // Écran éteint, Android (et surtout Samsung) exige un filtre : on filtre sur Victron (0x02E1).
        // Écran de l'app affiché : scan sans filtre pour compter tous les appareils BLE (diagnostic).
        val filters = if (visible) emptyList() else listOf(
            ScanFilter.Builder()
                .setManufacturerData(VictronDecoder.VICTRON_COMPANY_ID, byteArrayOf(0x10), byteArrayOf(0xFF.toByte()))
                .build()
        )
        try {
            s.startScan(filters, settings, callback)
            scanner = s
            scanStarts.addLast(now)
            LiveState.scanning = true
            LiveState.scanFiltered = !visible
            LiveState.scanStartedAt = System.currentTimeMillis()
            LiveState.lastScanError = null
        } catch (e: Exception) {
            LiveState.scanning = false
            LiveState.lastScanError = "${e.javaClass.simpleName}: ${e.message}"
            LogBook.log(this, "Échec du scan : ${LiveState.lastScanError}")
            handler.postDelayed({ startScan() }, 15_000)
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        val s = scanner ?: return
        runCatching { if (SystemChecks.bluetoothOn(this)) s.stopScan(callback) }
        scanner = null
        LiveState.scanning = false
    }

    private fun restartScan(reason: String) {
        if (restartPending) return
        lastRestartAt = System.currentTimeMillis()
        LogBook.log(this, "Relance du scan ($reason)")
        startScan()
    }

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) = handle(result)
        override fun onBatchScanResults(results: MutableList<ScanResult>) = results.forEach { handle(it) }
        override fun onScanFailed(errorCode: Int) {
            LiveState.scanning = false
            LiveState.lastScanError = "Code erreur scan $errorCode" +
                if (errorCode == 2) " (redémarrer le Bluetooth du téléphone)" else ""
            LogBook.log(this@MonitorService, "Scan échoué : code $errorCode")
            handler.postDelayed({ startScan() }, 20_000)
        }
    }

    private fun handle(result: ScanResult) {
        LiveState.bleSeen.incrementAndGet()
        val data = result.scanRecord?.getManufacturerSpecificData(VictronDecoder.VICTRON_COMPANY_ID) ?: return
        val header = VictronDecoder.parseHeader(data) ?: return
        LiveState.victronAdverts.incrementAndGet()
        val now = System.currentTimeMillis()
        val address = result.device?.address ?: "?"
        val res = VictronDecoder.decode(data, keyBytes, address, now)
        val status = when (res) {
            is VictronDecoder.Result.Ok -> "OK"
            VictronDecoder.Result.KeyMismatch -> "Clé incorrecte"
            VictronDecoder.Result.NoKey -> "Pas de clé"
            is VictronDecoder.Result.Unsupported -> "Autre appareil"
            is VictronDecoder.Result.Invalid -> "Trame invalide (${res.why})"
        }
        val prev = LiveState.devices[address]
        if (prev == null) LogBook.log(this, "Appareil Victron vu : $address ${VictronTexts.recordType(header.recordType)} " +
            "modèle 0x${header.modelId.toString(16)} → $status")
        LiveState.devices[address] = VictronSeen(address, header.modelId, header.recordType, status, result.rssi, now,
            (prev?.count ?: 0) + 1)

        when (res) {
            is VictronDecoder.Result.Ok -> onReading(res.reading)
            VictronDecoder.Result.KeyMismatch -> LiveState.lastKeyMismatchAt = now
            else -> {}
        }
    }

    private fun onReading(r: Reading) {
        LiveState.reading = r
        LiveState.lastVictronAt = r.timestamp
        if (prefs.lastDevice != r.address) {
            prefs.lastDevice = r.address
            LogBook.log(this, "Chargeur décodé : ${r.address} (entrée ${fmtV(r.inputVoltage)}, sortie ${fmtV(r.outputVoltage)})")
        }
        maybeRecordHour(r)
        updateNotification("Entrée ${fmtV(r.inputVoltage)} V  •  Sortie ${fmtV(r.outputVoltage)} V")
    }

    // ------------------------------------------------------------------ Historique

    /** Première mesure reçue dans chaque heure = mesure de l'heure. */
    private fun maybeRecordHour(r: Reading) {
        val hour = hourStart(r.timestamp)
        if (hour <= lastRecordedHour) return
        lastRecordedHour = hour
        prefs.lastRecordedHour = hour
        io.execute {
            val db = HistoryDb.get(this)
            fillGaps(db, hour)
            db.insertHour(hour, r)
            CsvExporter.updateAutoFile(this)
            LiveState.historyVersion.value++
        }
    }

    /** Marque « pas de mesure » les heures écoulées sans données (max 72 h). */
    private fun fillGaps(db: HistoryDb, upToExclusive: Long) {
        val since = hourStart(prefs.monitorSince)
        val last = db.since(upToExclusive - 72 * HOUR, descending = true).firstOrNull()?.hour
        var h = maxOf(since, (last ?: since - HOUR) + HOUR, upToExclusive - 72 * HOUR)
        while (h < upToExclusive) { db.insertHour(h, null); h += HOUR }
    }

    private fun onTick() {
        // Réveil par l'alarme (toutes les 15 min) : on garde le processeur éveillé un instant.
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        runCatching { pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OrionXS:tick").acquire(45_000) }
        AlarmScheduler.scheduleNext(this)
        checkHealth()
        val current = hourStart(System.currentTimeMillis())
        io.execute {
            val db = HistoryDb.get(this)
            val before = db.count()
            fillGaps(db, current)
            if (db.count() != before) { CsvExporter.updateAutoFile(this); LiveState.historyVersion.value++ }
        }
    }

    private val watchdog = object : Runnable {
        override fun run() {
            checkHealth()
            handler.postDelayed(this, 60_000)
        }
    }

    private fun checkHealth() {
        val now = System.currentTimeMillis()
        when {
            !LiveState.scanning && !restartPending -> startScan()
            // Android dégrade les scans de plus de 30 min : on relance toutes les 20 min.
            now - LiveState.scanStartedAt > 20 * 60_000 -> restartScan("rafraîchissement périodique")
            now - LiveState.lastVictronAt > 3 * 60_000 && now - LiveState.scanStartedAt > 3 * 60_000 &&
                now - lastRestartAt > 5 * 60_000 -> restartScan("aucune donnée depuis 3 min")
        }
        if (now - LiveState.lastVictronAt > 5 * 60_000) {
            updateNotification(if (LiveState.lastVictronAt == 0L) "Recherche du chargeur…" else "Chargeur hors de portée", force = false)
        }
    }

    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                BluetoothAdapter.STATE_ON -> {
                    LogBook.log(context, "Bluetooth activé"); handler.postDelayed({ startScan() }, 1_500)
                }
                BluetoothAdapter.STATE_OFF -> {
                    LogBook.log(context, "Bluetooth désactivé")
                    scanner = null; LiveState.scanning = false
                    updateNotification("Bluetooth éteint", force = true)
                }
            }
        }
    }

    // ------------------------------------------------------------------ Démo (version debug uniquement)

    /** Simule un Orion XS (trames chiffrées avec la clé enregistrée) + 3 jours d'historique. */
    private fun startDemo() {
        val key = keyBytes ?: return
        LogBook.log(this, "Mode démo")
        io.execute {
            val db = HistoryDb.get(this)
            val nowH = hourStart(System.currentTimeMillis())
            for (i in 72 downTo 1) {
                val h = nowH - i * HOUR
                val day = ((h / HOUR) % 24).toInt()
                val vin = 12.6 + 1.4 * kotlin.math.sin(day / 24.0 * 2 * Math.PI).coerceAtLeast(0.0) + (i % 5) * 0.02
                val r = Reading(h + 60_000, "DEMO", 0xA3F0, 0x0F, 3, 0, vin, 13.2 + (if (vin > 13.2) 1.2 else 0.3), null, 12.0, 0)
                db.insertHour(h, if (i == 30 || i == 31) null else r)
            }
            if (prefs.monitorSince == 0L || prefs.monitorSince > nowH - 72 * HOUR) prefs.monitorSince = nowH - 72 * HOUR
            LiveState.historyVersion.value++
        }
        var iv = 1
        val tick = object : Runnable {
            override fun run() {
                val plain = java.nio.ByteBuffer.allocate(14).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    .put(4).put(0).putShort((1438 + iv % 3).toShort()).putShort(212).putShort((1324 + iv % 4).toShort())
                    .putShort(245).putInt(0).array()
                val enc = VictronDecoder.decrypt(key, iv, plain)
                val frame = byteArrayOf(0x10, 0, 0xF0.toByte(), 0xA3.toByte(), 0x0F, (iv and 0xFF).toByte(), (iv shr 8).toByte(), key[0]) + enc
                (VictronDecoder.decode(frame, key, "DE:MO:00:00:00:01") as? VictronDecoder.Result.Ok)?.let {
                    LiveState.victronAdverts.incrementAndGet(); onReading(it.reading)
                }
                iv++
                handler.postDelayed(this, 2_000)
            }
        }
        handler.post(tick)
    }

    // ------------------------------------------------------------------ Notification

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Suivi du chargeur", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Affiche les tensions en continu"
                setShowBadge(false)
            }
        )
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_bolt)
            .setContentTitle("Chargeur Orion XS")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(pi)
            .build()
    }

    private fun goForeground(text: String) {
        try {
            ServiceCompat.startForeground(
                this, NOTIF_ID, buildNotification(text),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
            )
        } catch (e: Exception) {
            LogBook.log(this, "startForeground impossible : ${e.javaClass.simpleName} ${e.message}")
        }
    }

    private fun updateNotification(text: String, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (text == lastNotifText) return
        if (!force && now - lastNotifAt < 30_000) return
        lastNotifAt = now
        lastNotifText = text
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(text)) }
    }
}
