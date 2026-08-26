package com.laser92.cheddar.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.laser92.cheddar.ui.component.FilePicker
import com.laser92.cheddar.ui.component.GradientButton
import com.laser92.cheddar.ui.component.DocumentScannerAnimation
import com.laser92.cheddar.ui.component.StatCard
import com.laser92.cheddar.ui.component.TransactionList
import com.laser92.cheddar.ui.theme.*
import com.laser92.cheddar.viewmodel.ParserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: ParserViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            uri?.let {
                var fileName = "document"
                var fileSize = 0L
                context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        if (nameIndex != -1) fileName = cursor.getString(nameIndex)
                        if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                    }
                }
                viewModel.selectFile(it, fileName, fileSize)
            }
        }
    )

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.dismissError()
        }
    }

    LaunchedEffect(uiState.success) {
        uiState.success?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.dismissError() // Or dismissSuccess
        }
    }

    LaunchedEffect(uiState.exportedFile) {
        uiState.exportedFile?.let { file ->
            android.widget.Toast.makeText(context, "Saved to Downloads: ${file.name}", android.widget.Toast.LENGTH_LONG).show()
            try {
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file
                )
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(android.content.Intent.createChooser(intent, "Open Statement"))
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "No app found to open Excel files", android.widget.Toast.LENGTH_SHORT).show()
            }
            viewModel.dismissExportedFile()
        }
    }

    Scaffold(
        modifier = Modifier.then(
            if (uiState.reconcileResult != null) Modifier.blur(16.dp) else Modifier
        ),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { 
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text("Cheddar")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "v1.3.1", 
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                            modifier = Modifier
                                .border(1.dp, TextMuted, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextPrimary)
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
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // Header
            Text(
                text = "Parse your bank statements with ease",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Main Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color(0x1AFFFFFF)) // Glassmorphism semi-transparent
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(32.dp))
                    .blur(radius = if (uiState.reconcileResult != null) 8.dp else 0.dp)
                    .padding(32.dp)
                    .animateContentSize()
            ) {
                FilePicker(
                    selectedFileName = uiState.selectedFileName,
                    selectedFileSize = uiState.selectedFileSize,
                    onFileClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                    onRemoveFile = { viewModel.removeFile() }
                )

                AnimatedVisibility(
                    visible = uiState.isPdf,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    var passwordVisible by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = uiState.password,
                        onValueChange = { viewModel.setPassword(it) },
                        label = { Text("PDF Password (if any)", color = TextSecondary) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            val image = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(imageVector = image, contentDescription = "Toggle password", tint = TextSecondary)
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BorderFocus,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = uiState.cardName,
                    onValueChange = { viewModel.setCardName(it) },
                    label = { Text("Card Name", color = TextSecondary) },
                    placeholder = { Text("SBI", color = TextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BorderFocus,
                        unfocusedBorderColor = BorderSubtle,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    GradientButton(
                        text = "Download XLSX",
                        onClick = { viewModel.downloadXlsx() },
                        modifier = Modifier.weight(1f),
                        loading = uiState.isProcessing && uiState.processingAction == "xlsx",
                        enabled = !uiState.isProcessing && uiState.selectedFileUri != null
                    )
                    
                    GradientButton(
                        text = "Add to Sheets",
                        onClick = { viewModel.addToSheets() },
                        modifier = Modifier.weight(1f),
                        loading = uiState.isProcessing && uiState.processingAction == "sheets",
                        enabled = !uiState.isProcessing && uiState.selectedFileUri != null,
                        gradientStart = SheetsGreen,
                        gradientEnd = SheetsGreenLight
                    )
                }
            }

            // Removed inline Results Section; it is now a popup dialog.
        }
    }

    // Scanning Overlay Popup
    if (uiState.isProcessing) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { /* non-dismissable while processing */ },
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color(0xFF1A1A22))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(32.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    DocumentScannerAnimation(modifier = Modifier.size(80.dp))
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = if (uiState.processingAction == "xlsx") "Parsing..." else "Uploading...",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }
            }
        }
    }

    // Summary Dialog
    uiState.reconcileResult?.let { result ->
        val totalAmount = result.addedTransactions.sumOf { it.amount }
        val formatter = java.text.NumberFormat.getNumberInstance(java.util.Locale("en", "IN"))
        formatter.minimumFractionDigits = 2
        formatter.maximumFractionDigits = 2
        val formattedAmount = formatter.format(totalAmount)
        
        val tabName = result.addedTransactions.firstOrNull()?.date?.let { dateStr ->
            // Date is something like 16/07/2026. Let's just say "Google Sheets".
            // Or extract month/year if we wanted. "Google Sheets" is safer.
            "Google Sheets"
        } ?: "Google Sheets"

        androidx.compose.material3.AlertDialog(
            onDismissRequest = { viewModel.dismissResults() },
            title = {
                Text(
                    text = "Parse Complete",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
            },
            text = {
                Column {
                    Text(
                        text = "Successfully added ${result.addedTransactions.size} new transactions to $tabName.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Skipped ${result.skippedTransactions.size} duplicates.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Total amount processed: \u20B9$formattedAmount",
                        style = MaterialTheme.typography.titleMedium,
                        color = Success,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { viewModel.dismissResults() }) {
                    Text("OK", color = AccentStart)
                }
            },
            containerColor = Color(0xFF1E1E24),
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }
}
