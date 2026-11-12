package com.orionxs.suivi.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.orionxs.suivi.ble.Reading
import com.orionxs.suivi.data.HistoryDb
import com.orionxs.suivi.service.MonitorService
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DbTest {
    @Test fun sinceAndGaps() {
        val db = HistoryDb.get(ApplicationProvider.getApplicationContext<Application>())
        val nowH = MonitorService.hourStart(System.currentTimeMillis())
        for (i in 5 downTo 0) db.insertHour(nowH - i * 3_600_000L, Reading(0, "", 0, 15, 0, 0, 12.0, 13.0, null, null, 0))
        assertEquals(6, db.all().size)
        assertEquals(6, db.since(System.currentTimeMillis() - 24 * 3_600_000L).size)
        assertEquals(3, db.since(nowH - 2 * 3_600_000L).size)
        assertEquals(nowH, db.since(0, descending = true).first().hour)
    }
}
