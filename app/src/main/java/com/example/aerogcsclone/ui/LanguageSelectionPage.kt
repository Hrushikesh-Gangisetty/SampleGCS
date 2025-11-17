package com.example.aerogcsclone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.aerogcsclone.navigation.Screen
import com.example.aerogcsclone.telemetry.SharedViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelectionPage(
    navController: NavController,
    sharedViewModel: SharedViewModel
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedLanguage by remember { mutableStateOf("Select Language") }

    val languages = listOf("English", "Telugu")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "Select Language",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // Language Dropdown
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selectedLanguage,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Language", color = Color.White) },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = "Dropdown",
                            tint = Color.White
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.Gray,
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.Gray
                    ),
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(0.8f)
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(Color.DarkGray)
                ) {
                    languages.forEach { language ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = language,
                                    color = Color.White
                                )
                            },
                            onClick = {
                                selectedLanguage = language
                                expanded = false

                                // Set language in SharedViewModel (this updates both TTS and UI strings)
                                when (language) {
                                    "English" -> sharedViewModel.setLanguage("en")
                                    "Telugu" -> sharedViewModel.setLanguage("te")
                                }
                            },
                            colors = MenuDefaults.itemColors(
                                textColor = Color.White
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Continue Button
            Button(
                onClick = {
                    if (selectedLanguage != "Select Language") {
                        navController.navigate(Screen.Connection.route) {
                            popUpTo(Screen.LanguageSelection.route) { inclusive = true }
                        }
                    }
                },
                enabled = selectedLanguage != "Select Language",
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1E88E5),
                    contentColor = Color.White,
                    disabledContainerColor = Color.Gray,
                    disabledContentColor = Color.DarkGray
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(50.dp)
            ) {
                Text(
                    text = "Continue",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
