package com.orionxs.suivi.ui

import android.Manifest
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.orionxs.suivi.ble.Reading
import com.orionxs.suivi.data.HistoryDb
import com.orionxs.suivi.data.LiveState
import com.orionxs.suivi.data.Prefs
import com.orionxs.suivi.service.MonitorService
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Rend les vrais écrans (smoke test) et produit les captures du mode d'emploi. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xhdpi")
class ScreenshotsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val out = "../livraison/captures/"

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.POST_NOTIFICATIONS)
        rule.mainClock.autoAdvance = false
        @Suppress("DEPRECATION")
        shadowOf(android.bluetooth.BluetoothAdapter.getDefaultAdapter()).setEnabled(true)
    }

    private fun shot(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent { OrionTheme { androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) { content() } } }
        repeat(4) { rule.mainClock.advanceTimeBy(500); Thread.sleep(300); org.robolectric.shadows.ShadowLooper.idleMainLooper() }
        rule.onRoot().captureRoboImage(out + name)
    }

    private fun demoReading() = Reading(System.currentTimeMillis() - 3000, "DEMO", 0xA3F0, 0x0F, 4, 0, 13.26, 14.39, 25.0, 21.2, 0)

    @Test fun setup() = shot("setup.png") { SetupScreen(rememberFixActions(), initialStep = 1) {} }

    @Test fun cle() = shot("cle.png") { SetupScreen(rememberFixActions(), initialStep = 2) {} }

    @Test fun direct() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        Prefs(ctx).keyHex = "0df4d0395b7d1a876c0db9ac1a94f8b1"
        LiveState.reading = demoReading()
        LiveState.serviceStartedAt = System.currentTimeMillis()
        shot("direct.png") { LiveScreen(rememberFixActions()) {} }
    }

    @Test fun historique() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        val db = HistoryDb.get(ctx)
        val nowH = MonitorService.hourStart(System.currentTimeMillis())
        for (i in 23 downTo 0) {
            val h = nowH - i * 3_600_000L
            val day = ((h / 3_600_000L) % 24).toInt()
            val vin = 12.55 + 1.5 * kotlin.math.sin((day - 6) / 24.0 * 2 * Math.PI).coerceAtLeast(0.0)
            db.insertHour(h, if (i == 9) null else Reading(h + 40_000, "D", 0, 0x0F, 3, 0, vin, if (vin > 13.0) 14.35 else 13.45, 12.0, 10.0, 0))
        }
        shot("historique.png") { HistoryScreen() }
    }

    @Test fun aide() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        Prefs(ctx).keyHex = "0df4d0395b7d1a876c0db9ac1a94f8b1"
        shot("aide.png") { HelpScreen(rememberFixActions()) }
    }
}
