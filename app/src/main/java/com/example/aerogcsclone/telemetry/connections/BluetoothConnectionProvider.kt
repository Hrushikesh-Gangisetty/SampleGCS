package com.example.aerogcsclone.Telemetry.connections

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import com.divpundir.mavlink.adapters.coroutines.CoroutinesMavConnection
import com.divpundir.mavlink.adapters.coroutines.asCoroutine
import com.example.aerogcsclone.telemetry.connections.BluetoothMavConnection

@SuppressLint("MissingPermission")
class BluetoothConnectionProvider(
    private val device: BluetoothDevice
) : MavConnectionProvider {
    override fun createConnection(): CoroutinesMavConnection {
        return BluetoothMavConnection(device).asCoroutine()
    }
}