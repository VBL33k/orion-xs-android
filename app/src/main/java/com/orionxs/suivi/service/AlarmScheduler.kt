package com.orionxs.suivi.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.orionxs.suivi.data.LogBook
import com.orionxs.suivi.data.Prefs

/**
 * Réveil toutes les 15 minutes (aux quarts d'heure), y compris en veille profonde :
 * relance le service s'il a été tué, rafraîchit le scan, complète l'historique.
 */
object AlarmScheduler {
    private const val PERIOD = 15 * 60_000L

    fun scheduleNext(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()
        val next = (now / PERIOD + 1) * PERIOD + 5_000 // 5 s après chaque quart d'heure
        val pi = PendingIntent.getBroadcast(
            context, 1, Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        try {
            val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
        } catch (e: Exception) {
            LogBook.log(context, "Alarme impossible : ${e.message}")
            runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi) }
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Prefs(context).setupDone) return
        AlarmScheduler.scheduleNext(context)
        MonitorService.start(context, MonitorService.ACTION_TICK)
    }
}

/** Redémarrage du téléphone ou mise à jour de l'app -> on relance la surveillance. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Prefs(context).setupDone) return
        LogBook.log(context, "Relance après ${intent.action?.substringAfterLast('.')}")
        AlarmScheduler.scheduleNext(context)
        MonitorService.start(context)
    }
}
