package com.orionxs.suivi.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.orionxs.suivi.ble.Reading

/** Une ligne d'historique = une heure. Valeurs nulles = pas de mesure pendant cette heure. */
data class HourRow(
    val hour: Long,
    val measuredAt: Long?,
    val vin: Double?,
    val vout: Double?,
    val iout: Double?,
    val state: Int?,
    val error: Int?,
)

class HistoryDb private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "historique.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE mesures (heure INTEGER PRIMARY KEY, mesure_a INTEGER, vin REAL, vout REAL, " +
                "iout REAL, etat INTEGER, erreur INTEGER)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    /** Insère la mesure de l'heure (ignorée si l'heure est déjà enregistrée). */
    fun insertHour(hour: Long, r: Reading?): Boolean {
        val cv = ContentValues().apply {
            put("heure", hour)
            if (r != null) {
                put("mesure_a", r.timestamp)
                r.inputVoltage?.let { put("vin", it) }
                r.outputVoltage?.let { put("vout", it) }
                r.outputCurrent?.let { put("iout", it) }
                r.state?.let { put("etat", it) }
                r.error?.let { put("erreur", it) }
            }
        }
        return writableDatabase.insertWithOnConflict("mesures", null, cv, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }

    fun since(fromHour: Long, descending: Boolean = false): List<HourRow> =
        readableDatabase.rawQuery(
            // Valeur numérique directement dans la requête : un paramètre « ? » est lié en texte
            // et la comparaison avec la clé primaire entière échoue.
            "SELECT heure, mesure_a, vin, vout, iout, etat, erreur FROM mesures WHERE heure >= $fromHour ORDER BY heure " +
                if (descending) "DESC" else "ASC",
            null
        ).use { c -> buildList { while (c.moveToNext()) add(c.toRow()) } }

    fun all(descending: Boolean = true): List<HourRow> = since(0L, descending)

    fun count(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM mesures", null)
        .use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun countValid(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM mesures WHERE vin IS NOT NULL", null)
        .use { if (it.moveToFirst()) it.getInt(0) else 0 }

    private fun Cursor.toRow() = HourRow(
        hour = getLong(0),
        measuredAt = if (isNull(1)) null else getLong(1),
        vin = if (isNull(2)) null else getDouble(2),
        vout = if (isNull(3)) null else getDouble(3),
        iout = if (isNull(4)) null else getDouble(4),
        state = if (isNull(5)) null else getInt(5),
        error = if (isNull(6)) null else getInt(6),
    )

    companion object {
        @Volatile private var instance: HistoryDb? = null
        fun get(context: Context): HistoryDb =
            instance ?: synchronized(this) { instance ?: HistoryDb(context).also { instance = it } }
    }
}
