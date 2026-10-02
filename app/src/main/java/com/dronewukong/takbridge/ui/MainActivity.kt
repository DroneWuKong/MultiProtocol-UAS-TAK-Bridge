package com.dronewukong.takbridge.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.dronewukong.takbridge.R
import com.dronewukong.takbridge.bridge.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import android.Manifest
import android.os.Build
import android.text.InputType
import com.dronewukong.takbridge.mavlink.GpsPosition
import com.dronewukong.takbridge.mavlink.ProtocolRouter
import com.dronewukong.takbridge.mgrs.CoordinateFormatter
import com.dronewukong.takbridge.transport.*
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

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
    private lateinit var coordFormatLabel: TextView
    private lateinit var latLonText: TextView
    private lateinit var fixText: TextView
    private lateinit var satsText: TextView
    private lateinit var spdText: TextView
    private lateinit var altText: TextView
    private lateinit var hdgText: TextView
    private lateinit var cotRateText: TextView
    private lateinit var spinnerConnectionProfile: Spinner
    private lateinit var checkDtr: CheckBox
    private var profile = ConnectionProfile.CONTROLLER_MAVLINK
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

    // ── Navigation ─────────────────────────────────────────────
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var mapFragment: MapFragment
    private lateinit var toolsFragment: ToolsFragment
    private var activeFragment: androidx.fragment.app.Fragment? = null

    // ── Map overlays ───────────────────────────────────────────
    private var droneMarker: Marker? = null
    private var breadcrumbTrail: Polyline? = null
    private val trailPoints = mutableListOf<GeoPoint>()
    private var autoFollow = true

    companion object {
        private const val MAX_TRAIL_POINTS = 500
        private const val TRAIL_MIN_DISTANCE_M = 2.0 // Min meters between breadcrumbs
    }

    private lateinit var session: BridgeSession
    private var lastPosition: GpsPosition? = null
    private var coordFormat = CoordinateFormatter.Format.MGRS
    private val protocols = arrayOf("Auto (passive)", "MAVLink 1/2", "MSP", "GHST / CRSF")
    private val baudRates = arrayOf(115200, 230400, 57600, 921600, 9600, 460800)
    private var connectionSettingsExpanded = false
    private var importingCa = false
    private var pendingDiagnostics: String? = null
    private val sessionObserver: () -> Unit = { renderSession() }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val diagnosticExporter = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(pendingDiagnostics ?: session.diagnostics()) }
                Toast.makeText(this, "Diagnostics saved", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show() }
        }
        pendingDiagnostics = null
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

        connectionSettingsExpanded = savedInstanceState?.getBoolean("connectionSettingsExpanded") ?: false
        importingCa = savedInstanceState?.getBoolean("importingCa") ?: false
        pendingDiagnostics = savedInstanceState?.getString("pendingDiagnostics")

        // OSMDroid config (must be before setContentView)
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this))
        Configuration.getInstance().userAgentValue = "TAKBridge/0.1"

        setContentView(R.layout.activity_main)
        // Consume system/keyboard insets once, outside both the content and tabs.
        // A fixed-height bottom bar previously squeezed its labels under icons.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.appRoot)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
            WindowInsetsCompat.CONSUMED
        }

        // FragmentManager restores its own instances after activity recreation.
        mapFragment = supportFragmentManager.findFragmentByTag("map") as? MapFragment
            ?: MapFragment().also {
                supportFragmentManager.beginTransaction()
                    .add(R.id.fragmentContainer, it, "map").commit()
            }
        toolsFragment = supportFragmentManager.findFragmentByTag("tools") as? ToolsFragment
            ?: ToolsFragment().also {
                supportFragmentManager.beginTransaction()
                    .add(R.id.fragmentContainer, it, "tools").hide(it).commit()
            }
        activeFragment = if (mapFragment.isHidden) toolsFragment else mapFragment

        bottomNav = findViewById(R.id.bottomNav)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_map   -> showFragment(mapFragment)
                R.id.nav_tools -> showFragment(toolsFragment)
            }
            true
        }

    }

    /** Called only after the map fragment has actually inflated its controls. */
    internal fun onMapViewCreated(view: View) {
        bindViews(view)
        setupMap()
        setupSpinners()
        session = BridgeApplication.session(this)
        loadConfig()
        setupListeners()
        renderSession()
        val toggle = view.findViewById<Button>(R.id.btnConnectionSettings)
        fun updateSettings() {
            val settings = view.findViewById<View>(R.id.connectionSettings)
            settings.visibility = if (connectionSettingsExpanded) View.VISIBLE else View.GONE
            view.findViewById<View>(R.id.topPanel).visibility = if (connectionSettingsExpanded) View.GONE else View.VISIBLE
            view.findViewById<View>(R.id.mapControls).visibility = if (connectionSettingsExpanded) View.GONE else View.VISIBLE
            toggle.text = if (connectionSettingsExpanded) "Close connection settings −" else "Connection settings +"
            toggle.contentDescription = if (connectionSettingsExpanded) "Collapse connection settings" else "Expand connection settings"
        }
        toggle.setOnClickListener { connectionSettingsExpanded = !connectionSettingsExpanded; updateSettings() }
        updateSettings()
    }

    override fun onResume() {
        super.onResume()
        if (::mapView.isInitialized) mapView.onResume()
        if (::session.isInitialized) session.observe(sessionObserver)
    }

    override fun onPause() {
        super.onPause()
        if (::session.isInitialized) session.removeObserver(sessionObserver)
        if (::mapView.isInitialized) { mapView.onPause(); saveConfig() }
    }

    private fun showFragment(fragment: androidx.fragment.app.Fragment) {
        if (fragment === activeFragment) return
        supportFragmentManager.beginTransaction()
            .hide(activeFragment!!)
            .show(fragment)
            .commit()
        activeFragment = fragment
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (activeFragment === toolsFragment) {
            toolsFragment.onBackPressed { bottomNav.selectedItemId = R.id.nav_map }
            return
        }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    override fun onDestroy() {
        if (::session.isInitialized) session.removeObserver(sessionObserver)
        super.onDestroy()
    }

    // ── Setup ──────────────────────────────────────────────────

    private fun bindViews(view: View) {
        mapView = view.findViewById(R.id.mapView)
        usbStatusDot = view.findViewById(R.id.usbStatusDot)
        usbStatusText = view.findViewById(R.id.usbStatusText)
        multicastDot = view.findViewById(R.id.multicastDot)
        tcpDot = view.findViewById(R.id.tcpDot)
        mgrsText = view.findViewById(R.id.mgrsText)
        coordFormatLabel = view.findViewById(R.id.coordFormatLabel)
        latLonText = view.findViewById(R.id.latLonText)
        fixText = view.findViewById(R.id.fixText)
        satsText = view.findViewById(R.id.satsText)
        spdText = view.findViewById(R.id.spdText)
        altText = view.findViewById(R.id.altText)
        hdgText = view.findViewById(R.id.hdgText)
        cotRateText = view.findViewById(R.id.cotRateText)
        spinnerConnectionProfile = view.findViewById(R.id.spinnerConnectionProfile)
        checkDtr = view.findViewById(R.id.checkDtr)
        spinnerProtocol = view.findViewById(R.id.spinnerProtocol)
        spinnerBaud = view.findViewById(R.id.spinnerBaud)
        btnConnect = view.findViewById(R.id.btnConnect)
        editCallsign = view.findViewById(R.id.editCallsign)
        editTakHost = view.findViewById(R.id.editTakHost)
        editTakPort = view.findViewById(R.id.editTakPort)
        checkTls = view.findViewById(R.id.checkTls)
        btnTakConnect = view.findViewById(R.id.btnTakConnect)
        btnLoadCert = view.findViewById(R.id.btnLoadCert)
        certStatus = view.findViewById(R.id.certStatus)
        btnCenterDrone = view.findViewById(R.id.btnCenterDrone)
        btnZoomIn = view.findViewById(R.id.btnZoomIn)
        btnZoomOut = view.findViewById(R.id.btnZoomOut)
        statusBar = view.findViewById(R.id.statusBar)
        protocolStatus = view.findViewById(R.id.protocolStatus)
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
                        // Inverse luminance mapped into the Prismo charcoal palette.
                        // Keep street/label contrast without neon colors from RGB inversion.
                        -0.1063f, -0.3576f, -0.0361f, 0f, 148f,
                        -0.11693f, -0.39336f, -0.03971f, 0f, 169f,
                        -0.120119f, -0.404088f, -0.040793f, 0f, 182f,
                        0f, 0f, 0f, 1f, 0f      // alpha unchanged
                    )
                )
            )
        }

        // Breadcrumb trail polyline
        breadcrumbTrail = Polyline().apply {
            outlinePaint.color = ContextCompat.getColor(this@MainActivity, R.color.prismo_teal)
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
        spinnerConnectionProfile.adapter = ArrayAdapter(this,
            R.layout.spinner_item, ConnectionProfile.values().map { it.label })
        spinnerProtocol.adapter = ArrayAdapter(
            this, R.layout.spinner_item, protocols
        )
        spinnerBaud.adapter = ArrayAdapter(
            this, R.layout.spinner_item,
            baudRates.map { it.toString() }
        )
    }

    private fun setupListeners() {
        // Tap coordinate or label to cycle format: MGRS → DD → DMS → UTM → MGRS
        val cycleFormat = View.OnClickListener {
            coordFormat = coordFormat.next()
            coordFormatLabel.text = "${coordFormat.label}  \u25BC"
            ConfigStore.saveCoordFormat(this, coordFormat)
            lastPosition?.let { pos -> updatePositionDisplay(pos) }
        }
        mgrsText.setOnClickListener(cycleFormat)
        coordFormatLabel.setOnClickListener(cycleFormat)

        spinnerConnectionProfile.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = ConnectionProfile.values()[position]
                if (selected != profile) {
                    profile = selected
                    checkDtr.isChecked = profile.dtr
                    spinnerProtocol.setSelection(profile.protocol.ordinal)
                    spinnerBaud.setSelection(0)
                    saveConfig()
                }
            }
        }
        btnConnect.setOnClickListener {
            if (session.running) session.stop()
            else {
                saveConfig()
                val ports = session.usb.listPorts()
                if (ports.isEmpty()) statusBar.text = "No USB serial interface — use a data cable and controller telemetry USB mode"
                else if (ports.size == 1) connectUsbPort(ports.single())
                else androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Choose USB serial port")
                    .setItems(ports.map { it.label }.toTypedArray()) { _, which -> connectUsbPort(ports[which]) }
                    .setNegativeButton("Cancel", null).show()
            }
        }
        findViewById<Button>(R.id.btnReplay).setOnClickListener {
            if (session.running) session.stop()
            else { saveConfig(); startServiceAction(BridgeService.REPLAY) }
        }
        findViewById<Button>(R.id.btnDiagnostics).setOnClickListener {
            val report = session.diagnostics()
            androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Connection diagnostics")
                .setMessage(report)
                .setPositiveButton("Save report") { _, _ ->
                    pendingDiagnostics = report
                    diagnosticExporter.launch("tak-bridge-diagnostics.json")
                }
                .setNeutralButton("CoT preview") { _, _ ->
                    androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Latest CoT · ${session.gpsStage}")
                        .setMessage(session.lastCot.ifBlank { "No fresh valid GPS has produced an event yet." })
                        .setPositiveButton("Close", null).show()
                }.setNegativeButton("Close", null).show()
        }

        checkTls.setOnCheckedChangeListener { _, checked ->
            btnLoadCert.visibility = if (checked) View.VISIBLE else View.GONE
            certStatus.visibility = if (checked) View.VISIBLE else View.GONE
            if (checked && editTakPort.text.toString() == "8087") editTakPort.setText("8089")
            else if (!checked && editTakPort.text.toString() == "8089") editTakPort.setText("8087")
        }

        btnLoadCert.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this).setTitle("TAK certificates")
                .setItems(arrayOf("Import client .p12", "Import server CA .pem / .crt", "Clear certificates")) { _, choice ->
                    if (choice == 2) {
                        session.stop()
                        session.sender.tlsCertPath.takeIf { it.isNotBlank() }?.let { File(it).delete() }
                        session.sender.tlsCaPath.takeIf { it.isNotBlank() }?.let { File(it).delete() }
                        session.sender.tlsCertPath = ""; session.sender.tlsCertPassword = ""; session.sender.tlsCaPath = ""
                        ConfigStore.saveTlsCertPath(this, ""); ConfigStore.saveTlsCaPath(this, "")
                        updateCertStatus()
                    } else {
                        importingCa = choice == 1
                        certPickerLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
                        })
                    }
                }.show()
        }
        btnTakConnect.setOnClickListener {
            if (session.simulation) {
                statusBar.text = "Stop simulation before connecting live TAK output"
                return@setOnClickListener
            }
            val host = editTakHost.text.toString().trim()
            val port = editTakPort.text.toString().toIntOrNull()
            if (host.isBlank() || port == null || port !in 1..65535) {
                statusBar.text = "Enter a server hostname and a port from 1 to 65535"
                return@setOnClickListener
            }
            fun connect() {
                saveConfig()
                ConfigStore.saveTakConfig(this, ConfigStore.loadTakConfig(this).copy(tcpEnabled = true))
                startServiceAction(BridgeService.OUTPUT)
            }
            if (checkTls.isChecked && session.sender.tlsCertPath.isNotBlank()) {
                passwordDialog("Unlock client certificate") { password ->
                    lifecycleScope.launch {
                        try {
                            withContext(Dispatchers.IO) { TlsCredentials.loadClient(File(session.sender.tlsCertPath), password) }
                            session.sender.tlsCertPassword = password
                            connect()
                        } catch (e: Exception) { statusBar.text = "Client certificate/password invalid: ${e.message}" }
                    }
                }
            } else connect()
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

    private fun startServiceAction(action: String, device: String = "", port: Int = 0) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        try { BridgeService.start(this, action, device, port) }
        catch (e: Exception) { statusBar.text = "Unable to start bridge: ${e.message}" }
    }

    private fun connectUsbPort(port: UsbSerialTransport.Candidate) {
        startServiceAction(BridgeService.CONNECT, port.device.deviceName, port.portIndex)
    }

    private fun setConnectionControlsEnabled(enabled: Boolean) {
        spinnerConnectionProfile.isEnabled = enabled
        spinnerProtocol.isEnabled = enabled
        spinnerBaud.isEnabled = enabled
        checkDtr.isEnabled = enabled
        editCallsign.isEnabled = enabled
    }

    private fun renderSession() {
        if (!::mapView.isInitialized) return
        val connected = session.usb.isConnected
        usbStatusDot.setBackgroundResource(if (connected) R.drawable.dot_green else R.drawable.dot_red)
        usbStatusText.text = if (session.simulation) "SIM" else "USB"
        usbStatusDot.contentDescription = if (connected) "USB connected" else "USB disconnected"
        btnConnect.text = if (session.running) "Stop" else "Connect"
        setConnectionControlsEnabled(!session.active)
        btnLoadCert.isEnabled = !session.running
        findViewById<Button>(R.id.btnReplay).text = if (session.simulation) "Stop demo" else "Software demo"
        findViewById<Button>(R.id.btnReplay).isEnabled = !session.running || session.simulation
        findViewById<CheckBox>(R.id.checkMulticast).isEnabled = !session.running
        val pos = session.lastPosition
        if (pos != null) {
            updatePositionDisplay(pos)
            if (pos !== lastPosition) updateMapPosition(pos)
            if (!session.isFresh(pos)) {
                mgrsText.text = "GPS STALE"; fixText.text = "Stale"
                droneMarker?.isEnabled = false; mapView.invalidate()
            }
        } else {
            mgrsText.text = "NO LIVE POSITION"; latLonText.text = "---.------ / ---.------"
            fixText.text = "No Fix"; satsText.text = "--sv"; altText.text = "--m MSL"
            spdText.text = "-- m/s"; hdgText.text = "HDG --"
            droneMarker?.isEnabled = false; mapView.invalidate()
        }
        if (pos == null && lastPosition != null) { trailPoints.clear(); breadcrumbTrail?.setPoints(trailPoints) }
        lastPosition = pos
        protocolStatus.text = if (session.active) session.router.getStatusString() else ""
        cotRateText.text = if (session.simulation) "SIM · ${session.previewCount} local previews"
            else "CoT sent: ${session.sender.multicastSentCount + session.sender.tcpSentCount}"
        multicastDot.setBackgroundResource(if (session.sender.isMulticastConnected) R.drawable.dot_green else R.drawable.dot_red)
        tcpDot.setBackgroundResource(if (session.sender.isTcpConnected) R.drawable.dot_green else R.drawable.dot_red)
        multicastDot.contentDescription = if (session.sender.isMulticastConnected) "Local TAK socket ready" else "Local TAK socket inactive"
        tcpDot.contentDescription = if (session.sender.isTcpConnected) "TAK server connected" else "TAK server disconnected"
        statusBar.text = if (session.simulation) "SIMULATION · ${session.gpsStage} · local output only"
            else session.sender.lastError ?: if (session.active) "${session.status} · ${session.gpsStage}" else session.status
    }

    // ── Map updates ────────────────────────────────────────────

    private fun updateMapPosition(pos: GpsPosition) {
        droneMarker?.isEnabled = pos.hasValidFix && session.isFresh(pos)
        if (!pos.hasValidFix || !session.isFresh(pos)) { mapView.invalidate(); return }

        val geoPoint = GeoPoint(pos.lat, pos.lon)

        // Update drone marker position and rotation
        droneMarker?.apply {
            position = geoPoint
            rotation = -(pos.heading.coerceAtLeast(0.0).toFloat()) // OSMDroid rotates counter-clockwise
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
        findViewById<CheckBox>(R.id.checkMulticast).isChecked = config.multicastEnabled

        val baud = ConfigStore.loadBaudRate(this)
        val baudIndex = baudRates.indexOf(baud)
        if (baudIndex >= 0) spinnerBaud.setSelection(baudIndex)

        profile = ConfigStore.loadConnectionProfile(this)
        spinnerConnectionProfile.setSelection(profile.ordinal)
        checkDtr.isChecked = ConfigStore.loadDtr(this)
        val proto = ConfigStore.loadProtocol(this)
        spinnerProtocol.setSelection(when (proto) {
            ProtocolRouter.Protocol.MAVLINK -> 1
            ProtocolRouter.Protocol.MSP -> 2
            ProtocolRouter.Protocol.GHST -> 3
            else -> 0
        })

        updateCertStatus()

        // Coordinate format
        coordFormat = ConfigStore.loadCoordFormat(this)
        coordFormatLabel.text = "${coordFormat.label}  \u25BC"
    }

    private fun saveConfig() {
        val callsign = editCallsign.text.toString().trim().ifBlank { "DRONE-01" }
        val host = editTakHost.text.toString().trim()
        val port = editTakPort.text.toString().toIntOrNull()?.takeIf { it in 1..65535 } ?: ConfigStore.loadTakConfig(this).tcpPort
        ConfigStore.saveTakConfig(this, ConfigStore.loadTakConfig(this).copy(
            uid = "TAKBridge-$callsign",
            multicastEnabled = findViewById<CheckBox>(R.id.checkMulticast).isChecked,
            callsign = callsign, tcpHost = host, tcpPort = port,
            useTls = checkTls.isChecked
        ))
        ConfigStore.saveConnectionProfile(this, profile, checkDtr.isChecked)
        ConfigStore.saveBaudRate(this, baudRates[spinnerBaud.selectedItemPosition])
        ConfigStore.saveProtocol(this, when (spinnerProtocol.selectedItemPosition) {
            1 -> ProtocolRouter.Protocol.MAVLINK
            2 -> ProtocolRouter.Protocol.MSP
            3 -> ProtocolRouter.Protocol.GHST
            else -> ProtocolRouter.Protocol.AUTO_DETECT
        })
    }

    private fun passwordDialog(title: String, action: (String) -> Unit) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSaveEnabled = false
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
            hint = "Certificate password (blank if none)"
            setText(session.sender.tlsCertPassword)
        }
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle(title).setView(input)
            .setMessage("Password stays in memory until the app process ends.")
            .setPositiveButton("Continue") { _, _ -> action(input.text.toString()) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun importCert(uri: Uri) {
        val ca = importingCa
        fun import(password: String) {
            lifecycleScope.launch {
                try {
                    val file = withContext(Dispatchers.IO) {
                        val temp = File.createTempFile("cert-import", ".tmp", filesDir)
                        try {
                            contentResolver.openInputStream(uri)!!.use { input ->
                                val bytes = input.readBytesLimited(1024 * 1024)
                                temp.writeBytes(bytes)
                            }
                            if (ca) TlsCredentials.loadCa(temp) else TlsCredentials.loadClient(temp, password)
                            val destination = File(filesDir, if (ca) "tak_ca.pem" else "tak_client.p12")
                            check(temp.renameTo(destination)) { "Cannot save certificate" }
                            destination
                        } finally { temp.delete() }
                    }
                    if (ca) {
                        session.sender.tlsCaPath = file.absolutePath
                        ConfigStore.saveTlsCaPath(this@MainActivity, file.absolutePath)
                    } else {
                        session.sender.tlsCertPath = file.absolutePath; session.sender.tlsCertPassword = password
                        ConfigStore.saveTlsCertPath(this@MainActivity, file.absolutePath)
                    }
                    updateCertStatus()
                    statusBar.text = "Certificate validated · reconnect TAK to apply"
                } catch (e: Exception) { statusBar.text = "Certificate import failed: ${e.message}" }
            }
        }
        if (ca) import("") else passwordDialog("Import client certificate") { import(it) }
    }

    private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            require(out.size() + count <= limit) { "Certificate file exceeds 1 MB" }
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }

    private fun updateCertStatus() {
        certStatus.text = "Client: ${if (session.sender.tlsCertPath.isBlank()) "none" else "imported"} · " +
            "CA: ${if (session.sender.tlsCaPath.isBlank()) "client chain / system" else "imported"}"
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("connectionSettingsExpanded", connectionSettingsExpanded)
        outState.putBoolean("importingCa", importingCa)
        outState.putString("pendingDiagnostics", pendingDiagnostics)
        super.onSaveInstanceState(outState)
    }

    private fun updatePositionDisplay(pos: GpsPosition) {
        if (pos.hasValidFix) {
            mgrsText.text = CoordinateFormatter.formatPrimary(pos.lat, pos.lon, coordFormat)
            latLonText.text = CoordinateFormatter.formatSecondary(pos.lat, pos.lon, coordFormat)
        } else {
            mgrsText.text = "ACQUIRING FIX..."
            latLonText.text = "---.------ / ---.------"
        }

        fixText.text = pos.fixTypeString
        fixText.setTextColor(when {
            pos.fixType >= 5 -> ContextCompat.getColor(this, R.color.prismo_teal)  // RTK
            pos.fixType >= 3 -> ContextCompat.getColor(this, R.color.prismo_teal)  // 3D
            pos.fixType >= 2 -> ContextCompat.getColor(this, R.color.prismo_amber)  // 2D
            else -> ContextCompat.getColor(this, R.color.prismo_red)
        })

        satsText.text = if (pos.satellites >= 0) "${pos.satellites}sv" else "--sv"
        spdText.text = if (pos.groundSpeed >= 0) "${"%.1f".format(pos.groundSpeed)} m/s" else "-- m/s"
        altText.text = "${"%.0f".format(pos.altMsl)}m MSL"
        hdgText.text = if (pos.heading >= 0) "HDG ${"%.0f".format(pos.heading)}\u00B0" else "HDG --"
    }

}
