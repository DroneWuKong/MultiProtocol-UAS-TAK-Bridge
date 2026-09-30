package com.dronewukong.takbridge

import android.content.Intent
import android.os.Looper
import android.widget.TextView
import com.dronewukong.takbridge.bridge.*
import com.dronewukong.takbridge.transport.*
import com.dronewukong.takbridge.ui.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = ClockedBridgeApplication::class)
class BridgeLifecycleTest {
    private val app get() = RuntimeEnvironment.getApplication() as BridgeApplication
    private fun advance(seconds: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds))

    @Test fun foregroundReplaySurvivesActivityDestructionAndRecreation() {
        val controller = Robolectric.buildService(BridgeService::class.java).create()
        val service = controller.get()
        service.onStartCommand(Intent(app, BridgeService::class.java).setAction(BridgeService.REPLAY), 0, 1)
        advance(1)
        val session = app.session
        assertTrue(session.active)
        assertTrue(session.lastPosition!!.hasValidFix)
        assertTrue(session.lastCot.contains("SIMULATION"))
        Robolectric.buildActivity(MainActivity::class.java).use { activity -> activity.setup() }
        assertTrue("Closing the screen must not own connection shutdown", session.active)
        val count = session.previewCount
        advance(2)
        assertTrue(session.previewCount > count)
        Robolectric.buildActivity(MainActivity::class.java).use { activity ->
            val screen = activity.setup().get()
            assertTrue(screen.findViewById<TextView>(R.id.statusBar).text.contains("SIMULATION"))
        }
        service.onStartCommand(Intent(app, BridgeService::class.java).setAction(BridgeService.STOP), 0, 2)
        assertFalse(session.active)
        assertNull(session.lastPosition)
        assertEquals("", session.lastCot)
        val stoppedCount = session.previewCount
        advance(10)
        assertEquals(stoppedCount, session.previewCount)
        controller.destroy()
    }

    @Test fun replayExercisesFreshStaleAndNoFixWithoutOpeningOutputs() {
        val session = app.session
        ConfigStore.saveTakConfig(app, TakConfig(tcpEnabled = true, tcpHost = "127.0.0.1", tcpPort = 9))
        session.startReplay()
        advance(9)
        assertTrue(session.previewCount > 0)
        advance(7)
        assertTrue(session.gpsStage.startsWith("GPS STALE"))
        val count = session.previewCount
        session.publish()
        assertEquals(count, session.previewCount)
        advance(2)
        assertFalse(session.lastPosition!!.hasValidFix)
        assertEquals(count, session.previewCount)
        assertFalse(session.usb.isConnected)
        assertFalse(session.sender.isMulticastConnected)
        assertFalse(session.sender.isTcpConnected)
        assertEquals(0, session.sender.tcpSentCount)
        assertEquals(0, session.sender.multicastSentCount)
        try { session.connectOutput(); fail("Demo must stay local") } catch (_: IllegalStateException) { }
        session.stop()
    }

    @Test fun acceleratedTwoHourReplayAndTwentyRestartsNeverRestoreOldFix() {
        val session = app.session
        session.startReplay()
        advance(7200)
        assertTrue(session.active)
        assertTrue(session.previewCount > 1000)
        repeat(20) {
            session.stop()
            assertNull(session.lastPosition)
            assertEquals(0, session.router.bytesProcessed)
            session.startReplay()
            assertNull(session.lastPosition)
            advance(1)
            assertTrue(session.lastPosition!!.hasValidFix)
        }
        session.stop()
    }

    @Test fun nullIntentDoesNotRestartAnUnattendedConnection() {
        val controller = Robolectric.buildService(BridgeService::class.java).create()
        app.session.startReplay(); advance(1)
        assertEquals(android.app.Service.START_NOT_STICKY, controller.get().onStartCommand(null, 0, 1))
        assertFalse(app.session.running)
        assertNull(app.session.lastPosition)
        controller.destroy()
    }

    @Test fun diagnosticsExcludeIdentityLocationEndpointsAndPasswords() {
        val session = app.session
        session.sender.updateConfig(TakConfig(callsign = "PRIVATE-CALLSIGN", tcpHost = "private.example"))
        session.sender.tlsCertPassword = "secret-password"
        session.startReplay(); advance(1)
        val report = session.diagnostics()
        listOf("PRIVATE-CALLSIGN", "private.example", "secret-password", "\"lat\"", "\"lon\"").forEach {
            assertFalse(report.contains(it))
        }
        assertTrue(report.contains("SIMULATION_LOCAL_ONLY"))
        session.stop()
    }

    @Test fun upgradeRemovesLegacyPlaintextCertificatePassword() {
        val prefs = app.getSharedPreferences("tak_bridge_config", 0)
        prefs.edit().putString("tls_cert_password", "old-secret").putString("tls_cert_path", "client.p12").commit()
        assertEquals("client.p12", ConfigStore.loadTlsCertPath(app))
        assertFalse(prefs.contains("tls_cert_password"))
    }
}

/** One injected wall clock for parser, publication and UI; Android timers advance deterministically. */
class ClockedBridgeApplication : BridgeApplication() {
    private val epoch = System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime()
    override fun createSession() = BridgeSession(this) { epoch + android.os.SystemClock.elapsedRealtime() }
}
