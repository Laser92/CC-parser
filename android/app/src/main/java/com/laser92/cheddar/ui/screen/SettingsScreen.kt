package com.laser92.cheddar.ui.screen

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.laser92.cheddar.ui.theme.*
import com.laser92.cheddar.viewmodel.ParserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ParserViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    val signInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.handleSignInResult(result.data)
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = TextPrimary
                )
            )
        },
        containerColor = BgPrimary
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                text = "Google Account",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(BgCard)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column {
                    if (uiState.isSignedIn) {
                        Text("Signed in as", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(uiState.accountEmail ?: "Unknown Account", color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.signOut() },
                            colors = ButtonDefaults.buttonColors(containerColor = BgCardHover, contentColor = Danger)
                        ) {
                            Text("Sign Out")
                        }
                    } else {
                        Text("Service Account Active", color = TextPrimary)
                        Text("Falling back to local service_account.json since you are not signed in.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { signInLauncher.launch(viewModel.getSignInIntent()) },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentStart, contentColor = Color.White)
                        ) {
                            Text("Sign in with Google instead")
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = "Google Sheets",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            OutlinedTextField(
                value = uiState.sheetId,
                onValueChange = { viewModel.setSheetId(it) },
                label = { Text("Sheet ID", color = TextSecondary) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BorderFocus,
                    unfocusedBorderColor = BorderSubtle,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.isSignedIn) {
                if (uiState.isLoadingSpreadsheets) {
                    CircularProgressIndicator(color = AccentStart)
                } else if (uiState.availableSpreadsheets.isNotEmpty()) {
                    var expanded by remember { mutableStateOf(false) }
                    
                    val selectedSheetName = uiState.availableSpreadsheets.find { it.first == uiState.sheetId }?.second ?: "Or pick from Drive"
                    
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = selectedSheetName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Pick from your Drive", color = TextSecondary) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentStart,
                                unfocusedBorderColor = BorderSubtle,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.background(BgCardHover)
                        ) {
                            uiState.availableSpreadsheets.forEach { (id, name) ->
                                DropdownMenuItem(
                                    text = { Text(name, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    onClick = {
                                        viewModel.setSheetId(id)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                } else {
                    Text("No spreadsheets found or failed to load.", color = Danger)
                    Button(onClick = { viewModel.fetchSpreadsheets() }) {
                        Text("Retry")
                    }
                }
            } else {
                Text("Sign in to automatically list your Google Drive spreadsheets.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
