package com.example.aerogcsclone.Telemetry.connections

import com.divpundir.mavlink.adapters.coroutines.CoroutinesMavConnection
import com.divpundir.mavlink.adapters.coroutines.asCoroutine
import com.divpundir.mavlink.connection.tcp.TcpClientMavConnection
import com.divpundir.mavlink.definitions.common.CommonDialect

class TcpConnectionProvider(
    private val host: String,
    private val port: Int
) : MavConnectionProvider {
    override fun createConnection(): CoroutinesMavConnection {
        return TcpClientMavConnection(
            host,
            port,
            CommonDialect
        ).asCoroutine()
    }
}