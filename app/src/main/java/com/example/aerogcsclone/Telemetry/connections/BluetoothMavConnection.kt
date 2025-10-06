package com.example.aerogcsclone.Telemetry.connections

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.divpundir.mavlink.connection.MavConnection
import com.divpundir.mavlink.connection.StreamState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

@SuppressLint("MissingPermission") // Permissions are checked before this class is instantiated
class BluetoothMavConnection(
    private val bluetoothDevice: BluetoothDevice
) : MavConnection {

    companion object {
        // The standard UUID for the Serial Port Profile (SPP)
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private val _streamState = MutableStateFlow<StreamState>(StreamState.Inactive)
    override val streamState = _streamState.asStateFlow()

    @Throws(IOException::class)
    override suspend fun connect() {
        withContext(Dispatchers.IO) {
            try {
                // Create an RFCOMM socket to connect to the SPP service
                socket = bluetoothDevice.createRfcommSocketToServiceRecord(SPP_UUID)
                socket?.connect() // This is a blocking call

                // Get the input and output streams
                inputStream = socket?.inputStream
                outputStream = socket?.outputStream

                _streamState.value = StreamState.Active
            } catch (e: IOException) {
                // Clean up and re-throw the exception to be handled by the caller
                close()
                throw e
            }
        }
    }

    @Throws(IOException::class)
    override suspend fun close() {
        withContext(Dispatchers.IO) {
            _streamState.value = StreamState.Inactive
            try {
                inputStream?.close()
            } catch (e: IOException) { /* Ignore */ }
            try {
                outputStream?.close()
            } catch (e: IOException) { /* Ignore */ }
            try {
                socket?.close()
            } catch (e: IOException) { /* Ignore */ }
            socket = null
            inputStream = null
            outputStream = null
        }
    }

    @Throws(IOException::class)
    override fun read(): Int {
        val stream = inputStream?: throw IOException("Bluetooth connection is not active.")
        return stream.read() // This is a blocking call
    }

    @Throws(IOException::class)
    override fun write(data: ByteArray) {
        val stream = outputStream?: throw IOException("Bluetooth connection is not active.")
        stream.write(data) // This can be a blocking call
    }
}