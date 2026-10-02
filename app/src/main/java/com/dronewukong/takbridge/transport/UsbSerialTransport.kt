package com.dronewukong.takbridge.transport

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.dronewukong.takbridge.mavlink.MspGpsParser
import com.hoho.android.usbserial.driver.*
import com.hoho.android.usbserial.util.SerialInputOutputManager

/** Android permission + CDC lifecycle, including controller composite HID/CDC devices. */
class UsbSerialTransport(private val context: Context) {
    data class Candidate(val driver: UsbSerialDriver, val portIndex: Int) {
        val device: UsbDevice get() = driver.device
        val label: String get() = "USB serial " +
            "[%04X:%04X] port %d".format(device.vendorId, device.productId, portIndex + 1)
    }
    private val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val main = Handler(Looper.getMainLooper())
    private val permissionAction = "${context.packageName}.USB_PERMISSION"
    private var serialPort: UsbSerialPort? = null
    private var ioManager: SerialInputOutputManager? = null
    private var pending: Candidate? = null
    private var activeDevice: UsbDevice? = null
    private var generation = 0
    var isConnected = false; private set
    val isPermissionPending: Boolean get() = pending != null
    var deviceName = ""; private set
    var lastError: String? = null; private set
    var baudRate = 115200
    var assertDtr = true
    var allowMspPolling = false
    var onDataReceived: ((ByteArray, Int) -> Unit)? = null
    var onConnectionChanged: ((Boolean) -> Unit)? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == permissionAction) {
                if (intent.data?.lastPathSegment != generation.toString()) return
                val candidate = pending ?: return
                pending = null
                // Verify the actual permission, rather than trusting broadcast extras.
                if (manager.hasPermission(candidate.device)) open(candidate)
                else fail("USB permission denied — press CONNECT to try again")
            } else if (intent.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                @Suppress("DEPRECATION")
                val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                if (device?.deviceName == activeDevice?.deviceName ||
                    (device != null && device.deviceName == pending?.device?.deviceName)) {
                    fail("USB disconnected")
                }
            }
        }
    }

    init {
        ContextCompat.registerReceiver(context, receiver, IntentFilter(permissionAction).apply {
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun listPorts(): List<Candidate> {
        val table = UsbSerialProber.getDefaultProbeTable().apply {
            addProduct(0x35B6, 0x0004, CdcAcmSerialDriver::class.java)
        }
        return UsbSerialProber(table).findAllDrivers(manager).flatMap { driver ->
            driver.ports.indices.map { Candidate(driver, it) }
        }.sortedWith(compareBy({ it.device.deviceName }, { it.portIndex }))
    }

    fun connect(candidate: Candidate) {
        if (isConnected || pending != null || serialPort != null) disconnect()
        lastError = null
        generation++
        if (!manager.hasPermission(candidate.device)) {
            pending = candidate
            val intent = PendingIntent.getBroadcast(context, 0,
                Intent(permissionAction).setPackage(context.packageName)
                    .setData(Uri.parse("takbridge://usb/$generation")),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            try { manager.requestPermission(candidate.device, intent) }
            catch (e: Exception) { fail("USB permission request: ${e.message}") }
        } else open(candidate)
    }

    private fun open(candidate: Candidate) {
        if (manager.deviceList.values.none { it.deviceName == candidate.device.deviceName }) {
            fail("USB device removed — reconnect it"); return
        }
        val connection = manager.openDevice(candidate.device)
        if (connection == null) { fail("Unable to open USB device"); return }
        try {
            val port = candidate.driver.ports[candidate.portIndex]
            serialPort = port
            port.open(connection)
            port.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            try { port.setDTR(assertDtr) }
            catch (e: UnsupportedOperationException) { if (assertDtr) throw e }
            activeDevice = candidate.device
            deviceName = candidate.label
            val session = ++generation
            isConnected = true
            lastError = null
            onConnectionChanged?.invoke(true)
            ioManager = SerialInputOutputManager(port, object : SerialInputOutputManager.Listener {
                override fun onNewData(data: ByteArray) {
                    main.post {
                        if (isConnected && generation == session) onDataReceived?.invoke(data, data.size)
                    }
                }
                override fun onRunError(e: Exception) {
                    main.post { if (generation == session) fail("USB read: ${e.message}") }
                }
            }).also { it.start() }
        } catch (e: Exception) {
            connection.close()
            fail("USB connect: ${e.message}")
        }
    }

    private fun fail(message: String) { lastError = message; disconnect() }

    fun disconnect() {
        generation++
        pending = null
        isConnected = false
        ioManager?.stop(); ioManager = null
        try { serialPort?.setDTR(false) } catch (_: Exception) { }
        try { serialPort?.close() } catch (_: Exception) { }
        serialPort = null; activeDevice = null
        onConnectionChanged?.invoke(false)
    }

    /** Only the explicit direct-FC profile permits this single read request. */
    fun write(data: ByteArray) {
        if (!allowMspPolling || !data.contentEquals(MspGpsParser.MSP_REQUEST_RAW_GPS)) return
        try { serialPort?.write(data, 1000) }
        catch (e: Exception) { fail("MSP polling: ${e.message}") }
    }

    fun destroy() { disconnect(); context.unregisterReceiver(receiver) }
}
