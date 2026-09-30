package com.dronewukong.takbridge.bridge

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.dronewukong.takbridge.cot.CotFormatter
import com.dronewukong.takbridge.mavlink.GpsPosition
import com.dronewukong.takbridge.mavlink.ProtocolRouter
import com.dronewukong.takbridge.transport.*
import org.json.JSONObject

/** Main-thread session state; both replay and physical USB feed the same parser/publication path. */
class BridgeSession(private val context: Context, private val clock: () -> Long = System::currentTimeMillis) {
    val usb = UsbSerialTransport(context)
    val router = ProtocolRouter(clock)
    val sender = TakSender(context)
    private val main = Handler(Looper.getMainLooper())
    private val observers = linkedSetOf<() -> Unit>()
    var active = false; private set
    var simulation = false; private set
    var lastPosition: GpsPosition? = null; private set
    var lastCot = ""; private set
    var previewCount = 0L; private set
    var status = "Choose a connection or run the software demo"; private set
    private var profile = ConnectionProfile.TAC_MAVLINK
    private var sourceProtocol = ProtocolRouter.Protocol.MAVLINK
    private var nextCotAt = 0L
    private var replayStep = 0
    private var replayGps = byteArrayOf()
    private var replayNoFix = byteArrayOf()
    val running: Boolean get() = active || outputOnly
    private var outputOnly = false

    init {
        sender.updateConfig(ConfigStore.loadTakConfig(context))
        sender.tlsCertPath = ConfigStore.loadTlsCertPath(context)
        sender.tlsCaPath = ConfigStore.loadTlsCaPath(context)
        sender.onStatusChanged = { notifyChanged() }
        router.onGpsPosition = { lastPosition = it; notifyChanged() }
        usb.onDataReceived = { bytes, length -> if (active && !simulation) router.feed(bytes, length) }
        usb.onConnectionChanged = { connected ->
            if (!connected) {
                lastPosition = null
                router.setProtocol(sourceProtocol)
                // Permission denial, cable loss and read errors all invalidate the source immediately.
                // Reconnection is explicit: never switch to another radio or repeat permission prompts.
                if (active && !simulation) {
                    active = false
                    main.removeCallbacks(tick)
                    sender.stop()
                    outputOnly = false
                    status = usb.lastError ?: "Bridge stopped"
                }
            } else status = "USB connected · waiting for telemetry and GPS"
            notifyChanged()
        }
    }

    fun observe(observer: () -> Unit) { observers.add(observer); observer() }
    fun removeObserver(observer: () -> Unit) { observers.remove(observer) }
    private fun notifyChanged() { observers.toList().forEach { it() } }

    /** Hardware boundary: this is the only session method opening a physical serial port. */
    fun startUsb(candidate: UsbSerialTransport.Candidate) {
        stop()
        loadSettings()
        active = true
        status = "Allow USB access in the Android prompt"
        usb.connect(candidate)
        if (active) { sender.start(); main.postDelayed(tick, 500) }
        notifyChanged()
    }

    private fun loadSettings() {
        profile = ConfigStore.loadConnectionProfile(context)
        sourceProtocol = ConfigStore.loadProtocol(context)
        router.setProtocol(sourceProtocol)
        usb.baudRate = ConfigStore.loadBaudRate(context)
        usb.assertDtr = ConfigStore.loadDtr(context)
        usb.allowMspPolling = profile.pollsMsp && sourceProtocol == ProtocolRouter.Protocol.MSP
        sender.updateConfig(ConfigStore.loadTakConfig(context))
    }

    /** Software bypass for USB only. Real fix/freshness checks remain in force; no network output. */
    fun startReplay() {
        stop()
        fun fixture(name: String) = context.assets.open("replay/$name.hex").bufferedReader().use {
            it.readText().replace(Regex("\\s+"), "").chunked(2).map { hex -> hex.toInt(16).toByte() }.toByteArray()
        }
        replayGps = fixture("mav2-gps"); replayNoFix = fixture("mav2-no-fix")
        simulation = true; active = true; replayStep = 0; previewCount = 0
        sourceProtocol = ProtocolRouter.Protocol.MAVLINK
        router.setProtocol(sourceProtocol)
        status = "SIMULATION · local CoT preview only"
        main.post(tick)
        notifyChanged()
    }

    fun connectOutput() {
        check(!simulation) { "Stop simulation before connecting live TAK output" }
        sender.updateConfig(ConfigStore.loadTakConfig(context))
        sender.start()
        outputOnly = true
        status = "Connecting TAK output · waiting for fresh aircraft GPS"
        notifyChanged()
    }

    fun stop() {
        active = false; simulation = false; outputOnly = false
        main.removeCallbacks(tick)
        usb.disconnect()
        router.reset(); lastPosition = null; lastCot = ""; nextCotAt = 0
        sender.stop(); status = "Bridge stopped"
        notifyChanged()
    }

    fun fail(message: String) {
        stop()
        status = message
        notifyChanged()
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!active) return
            if (simulation) {
                // Ten fixes, seven seconds of silence (stale), three explicit no-fix frames, repeat.
                val phase = replayStep++ % 20
                if (phase < 10) router.feed(replayGps, replayGps.size)
                else if (phase >= 17) router.feed(replayNoFix, replayNoFix.size)
            } else if (usb.isConnected) {
                usb.allowMspPolling = profile.pollsMsp && router.detectedProtocol == ProtocolRouter.Protocol.MSP
                router.getMspGpsRequest(profile.pollsMsp)?.let { usb.write(it) }
            }
            val now = clock()
            if (now >= nextCotAt) {
                publish(now)
                nextCotAt = now + sender.config.updateIntervalMs
            }
            notifyChanged()
            if (active) main.postDelayed(this, if (simulation) 1000 else 500)
        }
    }

    internal fun publish(now: Long = clock()) {
        val pos = lastPosition ?: return
        if (!active || (!simulation && !usb.isConnected) || !pos.hasValidFix || !pos.isFresh(now)) return
        val cfg = sender.config
        val xml = CotFormatter.buildDroneSA(pos = pos,
            uid = if (simulation) "TAKBridge-SIMULATION" else cfg.uid,
            callsign = if (simulation) "SIMULATION" else cfg.callsign, cotType = cfg.cotType, staleSec = cfg.staleSeconds, nowMs = now)
        lastCot = xml
        if (simulation) previewCount++ else sender.send(xml, pos.timestampMs + 5000)
    }

    fun isFresh(pos: GpsPosition) = pos.isFresh(clock())

    val gpsStage: String get() = when {
        lastPosition == null -> "No GPS received"
        lastPosition?.let { isFresh(it) } != true -> "GPS STALE · output paused"
        lastPosition?.hasValidFix != true -> "No valid fix · output paused"
        else -> "Fresh GPS"
    }

    fun diagnostics(): String = JSONObject().apply {
        put("app", "TAK Bridge 0.3.0")
        put("mode", if (simulation) "SIMULATION_LOCAL_ONLY" else "USB")
        put("running", running); put("usb_connected", usb.isConnected)
        put("permission_pending", usb.isPermissionPending)
        put("profile", profile.name); put("baud", usb.baudRate); put("dtr", usb.assertDtr)
        put("protocol", router.detectedProtocol.name); put("bytes", router.bytesProcessed)
        put("mav_frames", router.mavlinkParser.framesReceived)
        put("ghst_crsf_frames", router.ghstParser.framesReceived)
        put("crc_errors", router.mavlinkParser.crcErrors + router.ghstParser.crcErrors)
        put("gps_stage", gpsStage)
        put("gps_age_ms", lastPosition?.let { clock() - it.timestampMs } ?: -1)
        put("multicast_ready", sender.isMulticastConnected); put("server_connected", sender.isTcpConnected)
        put("multicast_sent", sender.multicastSentCount); put("server_sent", sender.tcpSentCount)
        put("output_error_present", sender.lastError != null); put("local_previews", previewCount)
        put("note", "Sent counters show socket writes, not confirmation of display in ATAK. Coordinates, identity, endpoints and credentials omitted.")
    }.toString(2)
}
