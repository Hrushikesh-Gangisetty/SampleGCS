package com.example.aerogcsclone.telemetry.connections

import com.divpundir.mavlink.adapters.coroutines.CoroutinesMavConnection

/**
 * An interface for providing a MAVLink connection.
 * This abstraction allows the repository to be transport-agnostic.
 */
interface MavConnectionProvider {
    fun createConnection(): CoroutinesMavConnection
}