package com.dronewukong.takbridge.transport

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import kotlinx.coroutines.*
import java.util.concurrent.Executors

/**
 * USB Serial connection to drone flight controller.
 *
 * Handles device detection, permission, connect/disconnect lifecycle.
 * Feeds raw bytes to a callback — let the MAVLink parser handle framing.
 */
class UsbSerialTransport(private val context: Context) {

    companion object {
        private const val TAG = "UsbSerial"
        private const val DEFAULT_BAUD = 115200
        private const val READ_TIMEOUT = 1000
    }

    private var serialPort: UsbSerialPort? = null
    private var ioManager: SerialInputOutputManager? = null
    private val executor = Executors.newSingleThreadExecutor()

    var isConnected: Boolean = false; private set
    var deviceName: String = ""; private set
    var lastError: String? = null; private set
    var baudRate: Int = DEFAULT_BAUD

    // Callbacks
    var onDataReceived: ((ByteArray, Int) -> Unit)? = null
    var onConnectionChanged: ((Boolean) -> Unit)? = null

    /**
     * Scan for and connect to the first available USB serial device.
     */
    fun connect(): Boolean {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)

        if (drivers.isEmpty()) {
            lastError = "No USB serial devices found"
            Log.w(TAG, lastError!!)
            return false
        }

        val driver = drivers[0]
        val connection = usbManager.openDevice(driver.device)
        if (connection == null) {
            lastError = "USB permission denied — tap notification to grant"
            Log.w(TAG, lastError!!)
            return false
        }

        try {
            serialPort = driver.ports[0].apply {
                open(connection)
                setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            }

            deviceName = "${driver.device.productName ?: "Unknown"} (${driver.device.vendorId}:${driver.device.productId})"

            // Start reading
            ioManager = SerialInputOutputManager(serialPort, object : SerialInputOutputManager.Listener {
                override fun onNewData(data: ByteArray) {
                    onDataReceived?.invoke(data, data.size)
                }

                override fun onRunError(e: Exception) {
                    Log.e(TAG, "USB read error", e)
                    lastError = "USB: ${e.message}"
                    disconnect()
                }
            }).also {
                executor.submit(it)
            }

            isConnected = true
            lastError = null
            Log.i(TAG, "Connected to $deviceName at $baudRate baud")
            onConnectionChanged?.invoke(true)
            return true

        } catch (e: Exception) {
            lastError = "USB connect: ${e.message}"
            Log.e(TAG, lastError!!, e)
            disconnect()
            return false
        }
    }

    fun disconnect() {
        try {
            ioManager?.stop()
            serialPort?.close()
        } catch (e: Exception) {
            Log.e(TAG, "USB disconnect error", e)
        }
        ioManager = null
        serialPort = null
        isConnected = false
        onConnectionChanged?.invoke(false)
    }

    /**
     * Write bytes to the serial port (for MSP request polling).
     */
    fun write(data: ByteArray) {
        serialPort?.write(data, READ_TIMEOUT)
    }

    /**
     * List available USB serial devices (for UI picker).
     */
    fun listDevices(): List<UsbDevice> {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        return UsbSerialProber.getDefaultProber()
            .findAllDrivers(usbManager)
            .map { it.device }
    }

    fun destroy() {
        disconnect()
        executor.shutdown()
    }
}
