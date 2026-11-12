package com.orionxs.suivi.data

import com.orionxs.suivi.ble.Reading
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Appareil Victron vu en Bluetooth (pour le diagnostic). */
data class VictronSeen(
    val address: String,
    val modelId: Int,
    val recordType: Int,
    val status: String,   // "OK", "Clé incorrecte", "Pas de clé", "Non pris en charge"
    val rssi: Int,
    val lastSeen: Long,
    val count: Long,
)

/** État partagé entre le service d'arrière-plan et l'écran (même processus). */
object LiveState {
    @Volatile var reading: Reading? = null
    @Volatile var serviceRunning = false
    @Volatile var serviceStartedAt = 0L
    @Volatile var scanning = false
    @Volatile var scanFiltered = true
    @Volatile var scanStartedAt = 0L
    @Volatile var lastScanError: String? = null
    @Volatile var lastVictronAt = 0L
    @Volatile var lastKeyMismatchAt = 0L
    val bleSeen = AtomicLong(0)
    val victronAdverts = AtomicLong(0)
    val devices = ConcurrentHashMap<String, VictronSeen>()

    /** L'écran est-il affiché ? (scan plus rapide et non filtré quand oui) */
    val uiVisible = MutableStateFlow(false)

    /** Incrémenté à chaque nouvelle mesure horaire enregistrée. */
    val historyVersion = MutableStateFlow(0)
}
