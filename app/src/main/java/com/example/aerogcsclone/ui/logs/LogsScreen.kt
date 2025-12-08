package com.example.aerogcsclone.ui.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.example.aerogcsclone.authentication.AuthViewModel
import com.example.aerogcsclone.database.tlog.FlightEntity
import com.example.aerogcsclone.export.ExportFormat
import com.example.aerogcsclone.viewmodel.TlogViewModel
//import com.example.aerogcsclone.Telemetry.SharedViewModel
import com.example.aerogcsclone.telemetry.SharedViewModel
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

    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A0E27),
                        Color(0xFF1A1F3A),
                        Color(0xFF0F1419)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
                // Modern Header Section
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Title with icon
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF2196F3),
                                            Color(0xFF1976D2)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.FlightTakeoff,
                                contentDescription = "Flight Logs",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Flight Logs",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "${flights.size} missions recorded",
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }

                    // Export All Button with modern design
                    Button(
                        onClick = { tlogViewModel.exportAllFlights() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            disabledContainerColor = Color.Gray.copy(alpha = 0.3f)
                        ),
                        enabled = !uiState.isExporting && flights.isNotEmpty(),
                        modifier = Modifier
                            .height(48.dp)
                            .shadow(8.dp, RoundedCornerShape(24.dp))
                            .background(
                                if (!uiState.isExporting && flights.isNotEmpty()) {
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFF1E88E5),
                                            Color(0xFF1565C0)
                                        )
                                    )
                                } else {
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Gray.copy(alpha = 0.3f),
                                            Color.Gray.copy(alpha = 0.3f)
                                        )
                                    )
                                },
                                shape = RoundedCornerShape(24.dp)
                            ),
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        if (uiState.isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "Export All",
                                modifier = Modifier.size(20.dp),
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Export All",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }

            // Modern Statistics Card
            StatsCard(
                totalFlights = uiState.totalFlights,
                totalFlightTime = uiState.totalFlightTime,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Active Flight Status
            if (uiState.isFlightActive) {
                ActiveFlightCard(
                    flightId = uiState.currentFlightId,
                    onEndFlight = { tlogViewModel.endFlight() },
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // Error Message with modern styling
            uiState.errorMessage?.let { error ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFF5252).copy(alpha = 0.15f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .shadow(4.dp, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF5252).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = "Error",
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = error,
                            color = Color(0xFFFF5252),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { tlogViewModel.clearError() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Export Success Message with modern styling
            uiState.exportMessage?.let { message ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF4CAF50).copy(alpha = 0.15f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .shadow(4.dp, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4CAF50).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Success",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = message,
                            color = Color(0xFF4CAF50),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { tlogViewModel.clearExportMessage() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Modern Flights List Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.History,
                    contentDescription = "History",
                    tint = Color(0xFF64B5F6),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Recent Flights",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Flights List with modern cards
            if (flights.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1E2844).copy(alpha = 0.6f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(8.dp, RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(48.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            Color(0xFF64B5F6).copy(alpha = 0.3f),
                                            Color.Transparent
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.FlightTakeoff,
                                contentDescription = "No flights",
                                tint = Color(0xFF64B5F6),
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No flights recorded yet",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Start a mission to begin logging",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    flights.forEach { flight ->
                        FlightItem(
                            flight = flight,
                            onViewDetails = { /* Navigate to flight details */ },
                            onDelete = { tlogViewModel.deleteFlight(flight.id) },
                            onExport = { format -> tlogViewModel.exportFlight(flight, format) },
                            modifier = Modifier.fillMaxWidth()
                        )
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
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        modifier = modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF1E3A8A).copy(alpha = 0.8f),
                            Color(0xFF1E293B).copy(alpha = 0.9f)
                        )
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatItem(
                    icon = Icons.Default.Flight,
                    label = "Total Flights",
                    value = totalFlights.toString(),
                    color = Color(0xFF60A5FA)
                )

                Box(
                    modifier = Modifier
                        .height(60.dp)
                        .width(2.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.3f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                StatItem(
                    icon = Icons.Default.AccessTime,
                    label = "Flight Time",
                    value = formatDuration(totalFlightTime),
                    color = Color(0xFF34D399)
                )
            }
        }
    }
}

@Composable
fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color = Color(0xFF60A5FA)
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            color.copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = value,
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
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
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        modifier = modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF10B981).copy(alpha = 0.25f),
                            Color(0xFF059669).copy(alpha = 0.3f)
                        )
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF10B981),
                                    Color(0xFF059669)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.RadioButtonChecked,
                        contentDescription = "Active",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🟢 Flight Active",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Flight ID: $flightId",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Button(
                    onClick = onEndFlight,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .height(44.dp)
                        .shadow(6.dp, RoundedCornerShape(22.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFFEF4444),
                                    Color(0xFFDC2626)
                                )
                            ),
                            shape = RoundedCornerShape(22.dp)
                        ),
                    shape = RoundedCornerShape(22.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = "End",
                        modifier = Modifier.size(18.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "End Flight",
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
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
    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF1E293B).copy(alpha = 0.9f),
                            Color(0xFF0F172A).copy(alpha = 0.95f)
                        )
                    )
                )
                .clickable { onViewDetails() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Status indicator with gradient circle
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                if (flight.isCompleted) {
                                    Brush.radialGradient(
                                        colors = listOf(
                                            Color(0xFF10B981).copy(alpha = 0.3f),
                                            Color.Transparent
                                        )
                                    )
                                } else {
                                    Brush.radialGradient(
                                        colors = listOf(
                                            Color(0xFFFFA500).copy(alpha = 0.3f),
                                            Color.Transparent
                                        )
                                    )
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (flight.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = if (flight.isCompleted) "Completed" else "In Progress",
                            tint = if (flight.isCompleted) Color(0xFF10B981) else Color(0xFFFFA500),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Flight info
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = dateFormat.format(Date(flight.startTime)),
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = timeFormat.format(Date(flight.startTime)),
                            color = Color(0xFF60A5FA),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Action buttons with modern styling
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Export button
                        IconButton(
                            onClick = { showExportDialog = true },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    Color(0xFF1E88E5).copy(alpha = 0.15f)
                                )
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "Export",
                                tint = Color(0xFF60A5FA),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Delete button
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    Color(0xFFEF4444).copy(alpha = 0.15f)
                                )
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Flight details section with modern cards
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    flight.flightDuration?.let { duration ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1E3A8A).copy(alpha = 0.2f))
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.AccessTime,
                                    contentDescription = "Duration",
                                    tint = Color(0xFF60A5FA),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = formatDuration(duration),
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Duration",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    flight.area?.let { area ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF059669).copy(alpha = 0.2f))
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Landscape,
                                    contentDescription = "Area",
                                    tint = Color(0xFF34D399),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = String.format(Locale.getDefault(), "%.2f ha", area),
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Area",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modern Export Format Selection Dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF1E88E5),
                                        Color(0xFF1565C0)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = "Export",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Export Flight Log",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.height(300.dp)
                ) {
                    Text(
                        "Choose export format:",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(ExportFormat.entries.toTypedArray()) { format ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF1E293B).copy(alpha = 0.6f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .shadow(4.dp, RoundedCornerShape(12.dp))
                                    .clickable {
                                        onExport(format)
                                        showExportDialog = false
                                    },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF60A5FA).copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.InsertDriveFile,
                                            contentDescription = format.displayName,
                                            tint = Color(0xFF60A5FA),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = format.displayName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = format.description,
                                            fontSize = 12.sp,
                                            color = Color.White.copy(alpha = 0.6f)
                                        )
                                    }
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "Select",
                                        tint = Color(0xFF60A5FA),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { showExportDialog = false },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color(0xFF60A5FA)
                    )
                ) {
                    Text("Cancel", fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    // Modern Delete Confirmation Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFFEF4444),
                                        Color(0xFFDC2626)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Delete Flight",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color(0xFFEF4444)
                    )
                }
            },
            text = {
                Column {
                    Text(
                        "Are you sure you want to delete this flight?",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "⚠️ This action cannot be undone.",
                        fontSize = 13.sp,
                        color = Color(0xFFEF4444).copy(alpha = 0.8f)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .shadow(6.dp, RoundedCornerShape(20.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFFEF4444),
                                    Color(0xFFDC2626)
                                )
                            ),
                            shape = RoundedCornerShape(20.dp)
                        ),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(18.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Delete",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color(0xFF60A5FA)
                    )
                ) {
                    Text("Cancel", fontWeight = FontWeight.SemiBold)
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
