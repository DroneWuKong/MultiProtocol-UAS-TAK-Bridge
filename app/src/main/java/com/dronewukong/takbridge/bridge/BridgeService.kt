package com.dronewukong.takbridge.bridge

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.dronewukong.takbridge.R
import com.dronewukong.takbridge.ui.MainActivity

/** User-started connected-device service. Activity teardown never stops the bridge. */
class BridgeService : Service() {
    companion object {
        const val CONNECT = "bridge.CONNECT"
        const val REPLAY = "bridge.REPLAY"
        const val OUTPUT = "bridge.OUTPUT"
        const val STOP = "bridge.STOP"
        private const val CHANNEL = "bridge-session"
        private const val NOTIFICATION = 10
        fun start(context: Context, action: String, device: String = "", port: Int = 0) {
            ContextCompat.startForegroundService(context, Intent(context, BridgeService::class.java)
                .setAction(action).putExtra("device", device).putExtra("port", port))
        }
    }
    private lateinit var session: BridgeSession
    private var wakeLock: PowerManager.WakeLock? = null
    private var handlingCommand = false
    private var lastNotificationText = ""
    private var wakeLockRenewedAt = 0L
    private val observer: () -> Unit = {
        if (!handlingCommand) {
            if (!session.running) stopSelf()
            else {
                val text = "${session.simulation}:${session.gpsStage}"
                if (text != lastNotificationText) {
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification())
                    lastNotificationText = text
                }
                val now = android.os.SystemClock.elapsedRealtime()
                if (wakeLock?.isHeld != true || now - wakeLockRenewedAt >= 5 * 60 * 1000L) {
                    wakeLock?.acquire(10 * 60 * 1000L)
                    wakeLockRenewedAt = now
                }
            }
        }
    }
    override fun onCreate() {
        super.onCreate()
        session = BridgeApplication.session(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Active telemetry bridge", NotificationManager.IMPORTANCE_LOW))
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TAKBridge:session").apply { setReferenceCounted(false) }
        handlingCommand = true
        session.observe(observer)
        handlingCommand = false
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP || intent == null) {
            session.stop(); stopSelf(); return START_NOT_STICKY
        }
        ServiceCompat.startForeground(this, NOTIFICATION, notification(),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0)
        handlingCommand = true
        try {
            when (intent.action) {
                CONNECT -> {
                    val candidate = session.usb.listPorts().firstOrNull {
                        it.device.deviceName == intent.getStringExtra("device") &&
                            it.portIndex == intent.getIntExtra("port", 0)
                    }
                    if (candidate != null) session.startUsb(candidate) else session.fail("USB device removed — reconnect it")
                }
                REPLAY -> session.startReplay()
                OUTPUT -> session.connectOutput()
                else -> session.stop()
            }
        } catch (e: Exception) {
            session.fail("Unable to start bridge: ${e.message}")
        } finally { handlingCommand = false }
        observer()
        // Process death requires a fresh user-started connection, never a stale position restore.
        return START_NOT_STICKY
    }
    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, BridgeService::class.java).setAction(STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_drone_marker)
            .setContentTitle(if (session.simulation) "TAK Bridge · SIMULATION" else "TAK Bridge running")
            .setContentText(session.gpsStage).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(0, "Stop", stop).build()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        session.removeObserver(observer)
        session.stop()
        if (wakeLock?.isHeld == true) wakeLock?.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
