package com.example.aerogcsclone.ui.logs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.aerogcsclone.authentication.AuthViewModel
import com.example.aerogcsclone.database.tlog.FlightEntity
import com.example.aerogcsclone.export.ExportFormat
import com.example.aerogcsclone.uimain.TopNavBar
import com.example.aerogcsclone.viewmodel.TlogViewModel
import com.example.aerogcsclone.Telemetry.SharedViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    telemetryViewModel: SharedViewModel,
    tlogViewModel: TlogViewModel = viewModel()
) {
    val telemetryState by telemetryViewModel.telemetryState.collectAsState()
    val uiState by tlogViewModel.uiState.collectAsState()
    val flights by tlogViewModel.flights.collectAsState(initial = emptyList())

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Navigation Bar
        TopNavBar(
            telemetryState = telemetryState,
            authViewModel = authViewModel,
            navController = navController
        )

        // Main Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp) // Reduced vertical padding
        ) {
            // Header with Export All button - Made more compact
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp), // Reduced bottom padding
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Flight Logs",
                    fontSize = 20.sp, // Reduced font size
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Button(
                    onClick = { tlogViewModel.exportAllFlights() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                    enabled = !uiState.isExporting && flights.isNotEmpty(),
                    modifier = Modifier.height(36.dp), // Reduced button height
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp) // Compact padding
                ) {
                    if (uiState.isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp), // Smaller indicator
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = "Export All",
                            modifier = Modifier.size(14.dp) // Smaller icon
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export All", fontSize = 12.sp) // Smaller text
                }
            }

            // Statistics Card - Made more compact
            StatsCard(
                totalFlights = uiState.totalFlights,
                totalFlightTime = uiState.totalFlightTime,
                modifier = Modifier.padding(bottom = 8.dp) // Reduced spacing
            )

            // Active Flight Status
            if (uiState.isFlightActive) {
                ActiveFlightCard(
                    flightId = uiState.currentFlightId,
                    onEndFlight = { tlogViewModel.endFlight() },
                    modifier = Modifier.padding(bottom = 8.dp) // Reduced spacing
                )
            }

            // Error Message
            uiState.errorMessage?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.1f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp) // Reduced spacing
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp), // Reduced padding
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = "Error",
                            tint = Color.Red,
                            modifier = Modifier.size(16.dp) // Smaller icon
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = error,
                            color = Color.Red,
                            fontSize = 12.sp // Smaller text
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = { tlogViewModel.clearError() },
                            modifier = Modifier.size(24.dp) // Smaller button
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.Red,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Export Success Message
            uiState.exportMessage?.let { message ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Green.copy(alpha = 0.1f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp) // Reduced spacing
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp), // Reduced padding
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = Color.Green,
                            modifier = Modifier.size(16.dp) // Smaller icon
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = message,
                            color = Color.Green,
                            fontSize = 12.sp // Smaller text
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = { tlogViewModel.clearExportMessage() },
                            modifier = Modifier.size(24.dp) // Smaller button
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.Green,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Flights List Header - Made more compact
            Text(
                text = "Recent Flights",
                fontSize = 16.sp, // Reduced font size
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 4.dp) // Reduced spacing
            )

            // Flights List - Takes remaining space
            LazyColumn(
                modifier = Modifier.fillMaxSize(), // Takes all remaining space
                verticalArrangement = Arrangement.spacedBy(6.dp) // Reduced spacing between items
            ) {
                items(flights) { flight ->
                    FlightItem(
                        flight = flight,
                        onViewDetails = { /* Navigate to flight details */ },
                        onDelete = { tlogViewModel.deleteFlight(flight.id) },
                        onExport = { format -> tlogViewModel.exportFlight(flight, format) },
                        modifier = Modifier.fillMaxWidth() // Removed bottom padding since we use spacedBy
                    )
                }

                if (flights.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.Gray.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(32.dp)
                                    .fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.FlightTakeoff,
                                    contentDescription = "No flights",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No flights recorded yet",
                                    color = Color.Gray,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatsCard(
    totalFlights: Int,
    totalFlightTime: Long,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.6f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(
                icon = Icons.Default.Flight,
                label = "Total Flights",
                value = totalFlights.toString()
            )

            HorizontalDivider(
                modifier = Modifier
                    .height(40.dp)
                    .width(1.dp),
                color = Color.White.copy(alpha = 0.3f)
            )

            StatItem(
                icon = Icons.Default.AccessTime,
                label = "Flight Time",
                value = formatDuration(totalFlightTime)
            )
        }
    }
}

@Composable
fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp
        )
    }
}

@Composable
fun ActiveFlightCard(
    flightId: Long?,
    onEndFlight: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Green.copy(alpha = 0.2f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.RadioButtonChecked,
                contentDescription = "Active",
                tint = Color.Green,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Flight Active",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Flight ID: $flightId",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
            Button(
                onClick = onEndFlight,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text("End Flight")
            }
        }
    }
}

@Composable
fun FlightItem(
    flight: FlightEntity,
    onViewDetails: () -> Unit,
    onDelete: () -> Unit,
    onExport: (ExportFormat) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .clickable { onViewDetails() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (flight.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (flight.isCompleted) "Completed" else "In Progress",
                    tint = if (flight.isCompleted) Color.Green else Color(0xFFFFA500),
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = dateFormat.format(Date(flight.startTime)),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row {
                        flight.flightDuration?.let { duration ->
                            Text(
                                text = "Duration: ${formatDuration(duration)}",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }

                        flight.area?.let { area ->
                            Text(
                                text = " • Area: ${String.format(Locale.getDefault(), "%.2f", area)} ha",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Export button
                IconButton(onClick = { showExportDialog = true }) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "Export",
                        tint = Color(0xFF1E88E5)
                    )
                }

                // Delete button
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color.Red
                    )
                }
            }
        }
    }

    // Export Format Selection Dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Flight Log") },
            text = {
                Column(
                    modifier = Modifier.height(300.dp) // Set fixed height to enable scrolling
                ) {
                    Text("Choose export format:")
                    Spacer(modifier = Modifier.height(16.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(ExportFormat.values()) { format ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.Gray.copy(alpha = 0.1f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onExport(format)
                                        showExportDialog = false
                                    }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = format.displayName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        text = format.description,
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Flight") },
            text = { Text("Are you sure you want to delete this flight? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun formatDuration(durationMs: Long): String {
    val hours = durationMs / (1000 * 60 * 60)
    val minutes = (durationMs % (1000 * 60 * 60)) / (1000 * 60)

    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "<1m"
    }
}
