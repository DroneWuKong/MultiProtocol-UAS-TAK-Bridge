package com.dronewukong.takbridge.ui

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.preference.PreferenceManager
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.dronewukong.takbridge.R
import com.dronewukong.takbridge.cot.CotFormatter
import com.dronewukong.takbridge.mavlink.GpsPosition
import com.dronewukong.takbridge.mavlink.ProtocolRouter
import com.dronewukong.takbridge.mgrs.MgrsConverter
import com.dronewukong.takbridge.transport.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.io.FileOutputStream

/**
 * TAK Bridge — Map-First Main Activity
 *
 * Full-screen map showing live drone position and heading.
 * MGRS overlay on top, compact controls on bottom.
 *
 *   USB serial → auto-detect MAVLink / MSP / GHST
 *   → parse GPS → MGRS display → map marker with heading
 *   → CoT → TAK (multicast + TCP/TLS)
 */
class MainActivity : AppCompatActivity() {

    // ── UI refs ────────────────────────────────────────────────
    private lateinit var mapView: MapView
    private lateinit var usbStatusDot: View
    private lateinit var usbStatusText: TextView
    private lateinit var multicastDot: View
    private lateinit var tcpDot: View
    private lateinit var mgrsText: TextView
    private lateinit var latLonText: TextView
    private lateinit var fixText: TextView
    private lateinit var satsText: TextView
    private lateinit var spdText: TextView
    private lateinit var altText: TextView
    private lateinit var hdgText: TextView
    private lateinit var cotRateText: TextView
    private lateinit var spinnerProtocol: Spinner
    private lateinit var spinnerBaud: Spinner
    private lateinit var btnConnect: Button
    private lateinit var editCallsign: EditText
    private lateinit var editTakHost: EditText
    private lateinit var editTakPort: EditText
    private lateinit var checkTls: CheckBox
    private lateinit var btnTakConnect: Button
    private lateinit var btnLoadCert: Button
    private lateinit var certStatus: TextView
    private lateinit var btnCenterDrone: Button
    private lateinit var btnZoomIn: Button
    private lateinit var btnZoomOut: Button
    private lateinit var statusBar: TextView
    private lateinit var protocolStatus: TextView

    // ── Map overlays ───────────────────────────────────────────
    private var droneMarker: Marker? = null
    private var breadcrumbTrail: Polyline? = null
    private val trailPoints = mutableListOf<GeoPoint>()
    private var autoFollow = true

    companion object {
        private const val MAX_TRAIL_POINTS = 500
        private const val TRAIL_MIN_DISTANCE_M = 2.0 // Min meters between breadcrumbs
    }

    // ── Core components ────────────────────────────────────────
    private lateinit var usbTransport: UsbSerialTransport
    private lateinit var protocolRouter: ProtocolRouter
    private lateinit var takSender: TakSender

    // ── State ──────────────────────────────────────────────────
    private var lastPosition: GpsPosition? = null
    private var isBridgeActive = false
    private val handler = Handler(Looper.getMainLooper())
    private var gpsUpdateCount = 0
    private var lastRateCalcTime = System.currentTimeMillis()
    private var currentGpsHz = 0.0

    private val protocols = arrayOf("Auto", "MAVLink", "MSP", "GHST")
    private val baudRates = arrayOf(115200, 230400, 57600, 921600, 9600, 460800)
    private var tlsCertLocalPath = ""

    // ── Runnables ──────────────────────────────────────────────
    private val cotPushRunnable = object : Runnable {
        override fun run() {
            pushCoT()
            if (isBridgeActive) handler.postDelayed(this, takSender.config.updateIntervalMs)
        }
    }

    private val mspPollRunnable = object : Runnable {
        override fun run() {
            pollMspGps()
            if (isBridgeActive) handler.postDelayed(this, 500)
        }
    }

    private val rateDisplayRunnable = object : Runnable {
        override fun run() {
            updateRateDisplay()
            if (isBridgeActive) handler.postDelayed(this, 1000)
        }
    }

    private val certPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> importCert(uri) }
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on during demos
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // OSMDroid config (must be before setContentView)
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        Configuration.getInstance().userAgentValue = "TAKBridge/0.1"

        setContentView(R.layout.activity_main)

        bindViews()
        setupMap()
        setupSpinners()
        initComponents()
        loadConfig()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        saveConfig()
    }

    override fun onDestroy() {
        stopBridge()
        usbTransport.destroy()
        super.onDestroy()
    }

    // ── Setup ──────────────────────────────────────────────────

    private fun bindViews() {
        mapView = findViewById(R.id.mapView)
        usbStatusDot = findViewById(R.id.usbStatusDot)
        usbStatusText = findViewById(R.id.usbStatusText)
        multicastDot = findViewById(R.id.multicastDot)
        tcpDot = findViewById(R.id.tcpDot)
        mgrsText = findViewById(R.id.mgrsText)
        latLonText = findViewById(R.id.latLonText)
        fixText = findViewById(R.id.fixText)
        satsText = findViewById(R.id.satsText)
        spdText = findViewById(R.id.spdText)
        altText = findViewById(R.id.altText)
        hdgText = findViewById(R.id.hdgText)
        cotRateText = findViewById(R.id.cotRateText)
        spinnerProtocol = findViewById(R.id.spinnerProtocol)
        spinnerBaud = findViewById(R.id.spinnerBaud)
        btnConnect = findViewById(R.id.btnConnect)
        editCallsign = findViewById(R.id.editCallsign)
        editTakHost = findViewById(R.id.editTakHost)
        editTakPort = findViewById(R.id.editTakPort)
        checkTls = findViewById(R.id.checkTls)
        btnTakConnect = findViewById(R.id.btnTakConnect)
        btnLoadCert = findViewById(R.id.btnLoadCert)
        certStatus = findViewById(R.id.certStatus)
        btnCenterDrone = findViewById(R.id.btnCenterDrone)
        btnZoomIn = findViewById(R.id.btnZoomIn)
        btnZoomOut = findViewById(R.id.btnZoomOut)
        statusBar = findViewById(R.id.statusBar)
        protocolStatus = findViewById(R.id.protocolStatus)
    }

    private fun setupMap() {
        mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(16.0)
            // Default center — will move to drone position on first fix
            controller.setCenter(GeoPoint(38.8977, -77.0365)) // DC area default
            // Dark overlay for that tactical look
            overlayManager.tilesOverlay.setColorFilter(
                android.graphics.ColorMatrixColorFilter(
                    floatArrayOf(
                        -1f, 0f, 0f, 0f, 255f,  // invert red
                        0f, -1f, 0f, 0f, 255f,  // invert green
                        0f, 0f, -1f, 0f, 255f,  // invert blue
                        0f, 0f, 0f, 1f, 0f      // alpha unchanged
                    )
                )
            )
        }

        // Breadcrumb trail polyline
        breadcrumbTrail = Polyline().apply {
            outlinePaint.color = Color.parseColor("#4ECDC4")
            outlinePaint.strokeWidth = 4f
            outlinePaint.isAntiAlias = true
            outlinePaint.style = Paint.Style.STROKE
        }
        mapView.overlays.add(breadcrumbTrail)

        // Drone marker
        droneMarker = Marker(mapView).apply {
            val drawable = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_drone_marker)
            if (drawable != null) {
                icon = drawable
            }
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            title = "Drone"
            setInfoWindow(null) // No popup on tap
        }
        mapView.overlays.add(droneMarker)

        // Detect manual pan → disable auto-follow
        mapView.setOnTouchListener { _, _ ->
            autoFollow = false
            btnCenterDrone.alpha = 0.5f
            false
        }
    }

    private fun setupSpinners() {
        spinnerProtocol.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, protocols
        )
        spinnerBaud.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            baudRates.map { it.toString() }
        )
    }

    private fun initComponents() {
        usbTransport = UsbSerialTransport(this)
        usbTransport.onConnectionChanged = { connected ->
            runOnUiThread { updateUsbStatus(connected) }
        }

        protocolRouter = ProtocolRouter()
        protocolRouter.onGpsPosition = { pos ->
            lastPosition = pos
            gpsUpdateCount++
            runOnUiThread {
                updatePositionDisplay(pos)
                updateMapPosition(pos)
            }
        }
        protocolRouter.onProtocolDetected = { proto ->
            runOnUiThread {
                protocolStatus.text = protocolRouter.getStatusString()
                statusBar.text = "${proto.name} detected"
                if (proto == ProtocolRouter.Protocol.MSP || proto == ProtocolRouter.Protocol.GHST) {
                    handler.removeCallbacks(mspPollRunnable)
                    handler.postDelayed(mspPollRunnable, 500)
                }
            }
        }

        usbTransport.onDataReceived = { data, length ->
            protocolRouter.feed(data, length)
        }

        takSender = TakSender(this)
        takSender.onStatusChanged = {
            runOnUiThread { updateTakStatus() }
        }
    }

    private fun setupListeners() {
        btnConnect.setOnClickListener {
            if (usbTransport.isConnected) {
                stopBridge()
                usbTransport.disconnect()
            } else {
                usbTransport.baudRate = baudRates[spinnerBaud.selectedItemPosition]
                when (spinnerProtocol.selectedItemPosition) {
                    0 -> protocolRouter.reset()
                    1 -> protocolRouter.setProtocol(ProtocolRouter.Protocol.MAVLINK)
                    2 -> protocolRouter.setProtocol(ProtocolRouter.Protocol.MSP)
                    3 -> protocolRouter.setProtocol(ProtocolRouter.Protocol.GHST)
                }
                if (usbTransport.connect()) {
                    startBridge()
                } else {
                    statusBar.text = usbTransport.lastError ?: "Connection failed"
                    Toast.makeText(this, usbTransport.lastError, Toast.LENGTH_SHORT).show()
                }
            }
        }

        checkTls.setOnCheckedChangeListener { _, checked ->
            btnLoadCert.visibility = if (checked) View.VISIBLE else View.GONE
            certStatus.visibility = if (checked) View.VISIBLE else View.GONE
            if (checked && editTakPort.text.toString() == "8087") editTakPort.setText("8089")
            else if (!checked && editTakPort.text.toString() == "8089") editTakPort.setText("8087")
        }

        btnLoadCert.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            certPickerLauncher.launch(intent)
        }

        btnTakConnect.setOnClickListener {
            val host = editTakHost.text.toString().trim()
            val port = editTakPort.text.toString().toIntOrNull() ?: 8087
            if (host.isBlank()) {
                Toast.makeText(this, "Enter TAK Server IP", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            takSender.stop()
            val callsign = editCallsign.text.toString().trim().ifBlank { "DRONE-01" }
            takSender.updateConfig(takSender.config.copy(
                tcpEnabled = true, tcpHost = host, tcpPort = port,
                useTls = checkTls.isChecked, callsign = callsign
            ))
            if (checkTls.isChecked && tlsCertLocalPath.isNotBlank()) {
                takSender.tlsCertPath = tlsCertLocalPath
            }
            takSender.start()
            saveConfig()
            statusBar.text = "Connecting to $host:$port..."
        }

        // Map controls
        btnCenterDrone.setOnClickListener {
            autoFollow = true
            btnCenterDrone.alpha = 1.0f
            lastPosition?.let { pos ->
                if (pos.hasValidFix) {
                    mapView.controller.animateTo(GeoPoint(pos.lat, pos.lon))
                }
            }
        }
        btnZoomIn.setOnClickListener { mapView.controller.zoomIn() }
        btnZoomOut.setOnClickListener { mapView.controller.zoomOut() }
    }

    // ── Bridge control ─────────────────────────────────────────

    private fun startBridge() {
        val callsign = editCallsign.text.toString().trim().ifBlank { "DRONE-01" }
        takSender.updateConfig(TakConfig(
            multicastEnabled = true, callsign = callsign,
            uid = "TAKBridge-$callsign",
            tcpEnabled = takSender.config.tcpEnabled,
            tcpHost = takSender.config.tcpHost,
            tcpPort = takSender.config.tcpPort,
            useTls = takSender.config.useTls
        ))
        takSender.start()

        isBridgeActive = true
        gpsUpdateCount = 0
        lastRateCalcTime = System.currentTimeMillis()
        trailPoints.clear()

        handler.postDelayed(cotPushRunnable, 1000)
        handler.postDelayed(rateDisplayRunnable, 1000)

        val proto = protocolRouter.detectedProtocol
        if (proto == ProtocolRouter.Protocol.MSP || proto == ProtocolRouter.Protocol.GHST) {
            handler.postDelayed(mspPollRunnable, 500)
        }

        protocolStatus.text = protocolRouter.getStatusString()
        statusBar.text = "Bridge active — waiting for GPS fix"
    }

    private fun stopBridge() {
        isBridgeActive = false
        handler.removeCallbacks(cotPushRunnable)
        handler.removeCallbacks(mspPollRunnable)
        handler.removeCallbacks(rateDisplayRunnable)
        takSender.stop()
        statusBar.text = "Bridge stopped"
    }

    private fun pollMspGps() {
        if (!usbTransport.isConnected) return
        protocolRouter.getMspGpsRequest()?.let { usbTransport.write(it) }
    }

    private fun pushCoT() {
        val pos = lastPosition ?: return
        if (!pos.hasValidFix) return
        val cotXml = CotFormatter.buildEvent(
            position = pos, uid = takSender.config.uid,
            callsign = takSender.config.callsign,
            cotType = takSender.config.cotType,
            staleSeconds = takSender.config.staleSeconds
        )
        takSender.send(cotXml)
        runOnUiThread { updateTakStatus() }
    }

    // ── Map updates ────────────────────────────────────────────

    private fun updateMapPosition(pos: GpsPosition) {
        if (!pos.hasValidFix) return

        val geoPoint = GeoPoint(pos.lat, pos.lon)

        // Update drone marker position and rotation
        droneMarker?.apply {
            position = geoPoint
            rotation = -(pos.heading.toFloat()) // OSMDroid rotates counter-clockwise
        }

        // Add to breadcrumb trail
        if (trailPoints.isEmpty() || geoPoint.distanceToAsDouble(trailPoints.last()) > TRAIL_MIN_DISTANCE_M) {
            trailPoints.add(geoPoint)
            if (trailPoints.size > MAX_TRAIL_POINTS) {
                trailPoints.removeAt(0)
            }
            breadcrumbTrail?.setPoints(trailPoints)
        }

        // Auto-follow drone
        if (autoFollow) {
            mapView.controller.animateTo(geoPoint)
        }

        mapView.invalidate()
    }

    // ── Config persistence ─────────────────────────────────────

    private fun loadConfig() {
        val config = ConfigStore.loadTakConfig(this)
        editCallsign.setText(config.callsign)
        editTakHost.setText(config.tcpHost)
        editTakPort.setText(config.tcpPort.toString())
        checkTls.isChecked = config.useTls
        btnLoadCert.visibility = if (config.useTls) View.VISIBLE else View.GONE
        certStatus.visibility = if (config.useTls) View.VISIBLE else View.GONE
        takSender.updateConfig(config)

        val baud = ConfigStore.loadBaudRate(this)
        val baudIndex = baudRates.indexOf(baud)
        if (baudIndex >= 0) spinnerBaud.setSelection(baudIndex)

        val proto = ConfigStore.loadProtocol(this)
        spinnerProtocol.setSelection(when (proto) {
            ProtocolRouter.Protocol.MAVLINK -> 1
            ProtocolRouter.Protocol.MSP -> 2
            ProtocolRouter.Protocol.GHST -> 3
            else -> 0
        })

        val (certPath, _) = ConfigStore.loadTlsCertPath(this)
        if (certPath.isNotBlank()) {
            tlsCertLocalPath = certPath
            certStatus.text = File(certPath).name
        }
    }

    private fun saveConfig() {
        val callsign = editCallsign.text.toString().trim().ifBlank { "DRONE-01" }
        val host = editTakHost.text.toString().trim()
        val port = editTakPort.text.toString().toIntOrNull() ?: 8087
        ConfigStore.saveTakConfig(this, takSender.config.copy(
            callsign = callsign, tcpHost = host, tcpPort = port,
            useTls = checkTls.isChecked
        ))
        ConfigStore.saveBaudRate(this, baudRates[spinnerBaud.selectedItemPosition])
        ConfigStore.saveProtocol(this, when (spinnerProtocol.selectedItemPosition) {
            1 -> ProtocolRouter.Protocol.MAVLINK
            2 -> ProtocolRouter.Protocol.MSP
            3 -> ProtocolRouter.Protocol.GHST
            else -> ProtocolRouter.Protocol.AUTO_DETECT
        })
    }

    private fun importCert(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return
            val certFile = File(filesDir, "tak_client.p12")
            FileOutputStream(certFile).use { out -> inputStream.copyTo(out) }
            inputStream.close()
            tlsCertLocalPath = certFile.absolutePath
            certStatus.text = certFile.name
            ConfigStore.saveTlsCertPath(this, tlsCertLocalPath, "")
            Toast.makeText(this, "Certificate loaded", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Cert load failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ── UI Updates ─────────────────────────────────────────────

    private fun updateUsbStatus(connected: Boolean) {
        usbStatusDot.setBackgroundResource(if (connected) R.drawable.dot_green else R.drawable.dot_red)
        usbStatusText.text = if (connected) "USB" else "USB"
        usbStatusText.setTextColor(if (connected) Color.parseColor("#4ECDC4") else Color.parseColor("#888888"))
        btnConnect.text = if (connected) "STOP" else "CONNECT"
        if (!connected) {
            stopBridge()
            protocolStatus.text = ""
        }
    }

    private fun updatePositionDisplay(pos: GpsPosition) {
        if (pos.hasValidFix) {
            val mgrs = MgrsConverter.toMgrs(pos.lat, pos.lon)
            mgrsText.text = MgrsConverter.formatForDisplay(mgrs)
            latLonText.text = "${"%.6f".format(pos.lat)} / ${"%.6f".format(pos.lon)}"
        } else {
            mgrsText.text = "ACQUIRING FIX..."
            latLonText.text = "---.------ / ---.------"
        }

        fixText.text = pos.fixTypeString
        fixText.setTextColor(when {
            pos.fixType >= 5 -> Color.parseColor("#4ECDC4")  // RTK
            pos.fixType >= 3 -> Color.parseColor("#44FF44")  // 3D
            pos.fixType >= 2 -> Color.parseColor("#FFFF44")  // 2D
            else -> Color.parseColor("#FF4444")
        })

        satsText.text = if (pos.satellites >= 0) "${pos.satellites}sv" else "--sv"
        spdText.text = "${"%.1f".format(pos.groundSpeed)} m/s"
        altText.text = "${"%.0f".format(pos.altMsl)}m MSL"
        hdgText.text = "HDG ${"%.0f".format(pos.heading)}°"
    }

    private fun updateRateDisplay() {
        val now = System.currentTimeMillis()
        val elapsed = (now - lastRateCalcTime) / 1000.0
        if (elapsed > 0) currentGpsHz = gpsUpdateCount / elapsed
        gpsUpdateCount = 0
        lastRateCalcTime = now
        val totalCoT = takSender.multicastSentCount + takSender.tcpSentCount
        cotRateText.text = "CoT: $totalCoT | ${"%.0f".format(currentGpsHz)}Hz"
    }

    private fun updateTakStatus() {
        multicastDot.setBackgroundResource(
            if (takSender.isMulticastConnected) R.drawable.dot_green else R.drawable.dot_red
        )
        tcpDot.setBackgroundResource(
            if (takSender.isTcpConnected) R.drawable.dot_green else R.drawable.dot_red
        )
        takSender.lastError?.let { statusBar.text = it }
    }
}
