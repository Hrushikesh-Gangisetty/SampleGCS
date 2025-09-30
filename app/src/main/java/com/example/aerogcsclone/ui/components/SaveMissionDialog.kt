package com.example.aerogcsclone.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Dialog for saving mission templates with project and plot name input
 */
@Composable
fun SaveMissionDialog(
    onDismiss: () -> Unit,
    onSave: (projectName: String, plotName: String) -> Unit,
    isLoading: Boolean = false
) {
    var projectName by remember { mutableStateOf("") }
    var plotName by remember { mutableStateOf("") }
    var projectNameError by remember { mutableStateOf<String?>(null) }
    var plotNameError by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Save Mission Template",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Project Name Input
                OutlinedTextField(
                    value = projectName,
                    onValueChange = {
                        projectName = it
                        projectNameError = null
                    },
                    label = { Text("Project Name") },
                    placeholder = { Text("Enter project name") },
                    isError = projectNameError != null,
                    supportingText = projectNameError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Plot Name Input
                OutlinedTextField(
                    value = plotName,
                    onValueChange = {
                        plotName = it
                        plotNameError = null
                    },
                    label = { Text("Plot Name") },
                    placeholder = { Text("Enter plot name") },
                    isError = plotNameError != null,
                    supportingText = plotNameError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            // Validate inputs
                            var hasError = false

                            if (projectName.isBlank()) {
                                projectNameError = "Project name is required"
                                hasError = true
                            }

                            if (plotName.isBlank()) {
                                plotNameError = "Plot name is required"
                                hasError = true
                            }

                            if (!hasError) {
                                onSave(projectName.trim(), plotName.trim())
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}
