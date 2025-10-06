package com.example.aerogcsclone.Telemetry

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Application-level coroutine scope for background operations
 * Used by TelemetryRepository and other long-running operations
 */
object AppScope : CoroutineScope {
    override val coroutineContext = SupervisorJob() + Dispatchers.IO
}
