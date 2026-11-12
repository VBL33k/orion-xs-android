package com.orionxs.suivi.data

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Petit journal persistant, joint au diagnostic WhatsApp. */
object LogBook {
    private const val MAX_LINES = 400
    private val lines = ArrayDeque<String>()
    private var loaded = false
    private var file: File? = null
    private val fmt = SimpleDateFormat("dd/MM HH:mm:ss", Locale.FRANCE)

    @Synchronized
    private fun ensureLoaded(context: Context) {
        if (loaded) return
        val f = File(context.applicationContext.filesDir, "journal.txt")
        file = f
        if (f.exists()) runCatching { f.readLines().takeLast(MAX_LINES).forEach { lines.addLast(it) } }
        loaded = true
    }

    @Synchronized
    fun log(context: Context, message: String) {
        ensureLoaded(context)
        val line = fmt.format(Date()) + "  " + message
        Log.i("OrionXS", message)
        lines.addLast(line)
        while (lines.size > MAX_LINES) lines.removeFirst()
        runCatching {
            val f = file!!
            f.appendText(line + "\n")
            if (f.length() > 200_000) f.writeText(lines.joinToString("\n", postfix = "\n"))
        }
    }

    @Synchronized
    fun tail(context: Context, n: Int): List<String> {
        ensureLoaded(context)
        return lines.toList().takeLast(n)
    }
}
