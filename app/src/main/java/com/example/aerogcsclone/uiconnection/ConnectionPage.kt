package com.example.aerogcsclone.uiconnection

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.aerogcsclone.Telemetry.ConnectionType
import com.example.aerogcsclone.Telemetry.PairedDevice
import com.example.aerogcsclone.Telemetry.SharedViewModel
import com.example.aerogcsclone.navigation.Screen
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission")
@Composable
fun ConnectionPage(navController: NavController, viewModel: SharedViewModel) {
    var isConnecting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    var connectionJob by remember { mutableStateOf<Job?>(null) }
    var showPopup by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val connectionType by viewModel.connectionType

    // When the page is shown, get the paired Bluetooth devices
    LaunchedEffect(Unit) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
        if (bluetoothAdapter != null) {
            try {
                val pairedBtDevices = bluetoothAdapter.bondedDevices
                viewModel.setPairedDevices(pairedBtDevices)
            } catch (se: SecurityException) {
                errorMessage = "Bluetooth permission missing."
            }
        }
    }

    // React to connection state changes from the ViewModel
    LaunchedEffect(viewModel) {
        viewModel.isConnected.collectLatest { isConnected ->
            if (isConnected) {
                isConnecting = false
                connectionJob?.cancel()
                navController.navigate(Screen.SelectFlyingMethod.route) {
                    popUpTo(Screen.Connection.route) { inclusive = true }
                }
            }
        }
    }

    fun startConnection() {
        isConnecting = true
        errorMessage = ""
        connectionJob?.cancel() // Cancel any previous job
        connectionJob = coroutineScope.launch {
            viewModel.connect() // Ask the ViewModel to connect

            // Set a timeout for the connection attempt
            delay(10000) // 10-second timeout

            // If we are still in a 'connecting' state after the timeout, it failed.
            if (isConnecting) {
                isConnecting = false
                errorMessage = "Connection timed out. Please check your settings and try again."
                showPopup = true
                viewModel.cancelConnection() // Clean up the failed attempt
            }
        }
    }

    fun cancelConnection() {
        connectionJob?.cancel()
        isConnecting = false
        errorMessage = ""
        coroutineScope.launch {
            viewModel.cancelConnection()
        }
    }

    val isConnectEnabled = !isConnecting && when (connectionType) {
        ConnectionType.TCP -> viewModel.ipAddress.value.isNotBlank() && viewModel.port.value.isNotBlank()
        ConnectionType.BLUETOOTH -> viewModel.selectedDevice.value != null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF535350))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Connect to Drone",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(20.dp))

            val tabs = listOf("TCP/IP", "Bluetooth")
            TabRow(selectedTabIndex = connectionType.ordinal, containerColor = Color(0xFF333330)) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = connectionType.ordinal == index,
                        onClick = { viewModel.onConnectionTypeChange(ConnectionType.values()[index]) },
                        text = { Text(title, color = Color.White) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            when (connectionType) {
                ConnectionType.TCP -> TcpConnectionContent(viewModel)
                ConnectionType.BLUETOOTH -> BluetoothConnectionContent(viewModel)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(
                    onClick = { startConnection() },
                    modifier = Modifier.weight(1f),
                    enabled = isConnectEnabled
                ) {
                    Text(if (isConnecting) "Connecting..." else "Connect")
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = { cancelConnection() },
                    modifier = Modifier.weight(1f),
                    enabled = isConnecting
                ) {
                    Text("Cancel")
                }
            }

            if (errorMessage.isNotEmpty() && !showPopup) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(errorMessage, color = MaterialTheme.colorScheme.error)
            }
        }

        if (showPopup) {
            AlertDialog(
                onDismissRequest = { showPopup = false },
                title = { Text("Connection Failed") },
                text = { Text(errorMessage) },
                confirmButton = {
                    Button(onClick = { showPopup = false }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

@Composable
fun TcpConnectionContent(viewModel: SharedViewModel) {
    val ipAddress by viewModel.ipAddress
    val port by viewModel.port

    OutlinedTextField(
        value = ipAddress,
        onValueChange = { viewModel.onIpAddressChange(it) },
        label = { Text("IP Address", color = Color.White) },
        modifier = Modifier.fillMaxWidth(),
        textStyle = LocalTextStyle.current.copy(color = Color.White)
    )

    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = port,
        onValueChange = { viewModel.onPortChange(it) },
        label = { Text("Port", color = Color.White) },
        modifier = Modifier.fillMaxWidth(),
        textStyle = LocalTextStyle.current.copy(color = Color.White)
    )
}

@Composable
fun BluetoothConnectionContent(viewModel: SharedViewModel) {
    val pairedDevices by viewModel.pairedDevices.collectAsState()
    val selectedDevice by viewModel.selectedDevice

    if (pairedDevices.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
            Text("No paired Bluetooth devices found.", color = Color.White)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxWidth().height(150.dp)) {
            items(pairedDevices) { device ->
                DeviceRow(
                    device = device,
                    isSelected = device.address == selectedDevice?.address,
                    onClick = { viewModel.onDeviceSelected(device) }
                )
            }
        }
    }
}

@Composable
fun DeviceRow(device: PairedDevice, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else Color.Transparent)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(device.name, color = Color.White, style = MaterialTheme.typography.bodyLarge)
            Text(device.address, color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
        }
    }
}