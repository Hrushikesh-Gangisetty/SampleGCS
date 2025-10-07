package com.example.aerogcsclone.telemetry.connections

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import com.divpundir.mavlink.adapters.coroutines.CoroutinesMavConnection
import com.divpundir.mavlink.adapters.coroutines.asCoroutine
import com.example.aerogcsclone.telemetry.connections.BluetoothMavConnection
import com.example.aerogcsclone.telemetry.connections.MavConnectionProvider

@SuppressLint("MissingPermission")
class BluetoothConnectionProvider(
    private val device: BluetoothDevice
) : MavConnectionProvider {
    override fun createConnection(): CoroutinesMavConnection {
        return BluetoothMavConnection(device).asCoroutine()
    }
}