package com.laser92.cheddar.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.laser92.cheddar.model.COLUMN_TYPES
import com.laser92.cheddar.model.ColumnConfig
import com.laser92.cheddar.model.DEFAULT_COLUMNS
import com.laser92.cheddar.ui.theme.AccentStart
import com.laser92.cheddar.ui.theme.BgPrimary
import com.laser92.cheddar.ui.theme.TextMuted
import com.laser92.cheddar.ui.theme.TextPrimary
import com.laser92.cheddar.ui.theme.TextSecondary
import com.laser92.cheddar.viewmodel.ParserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColumnSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ParserViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var columns by remember { mutableStateOf(uiState.columns) }

    LaunchedEffect(uiState.columns) {
        columns = uiState.columns
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Column Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BgPrimary,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextPrimary
                )
            )
        },
        bottomBar = {
            Surface(color = BgPrimary, tonalElevation = 8.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = { columns = DEFAULT_COLUMNS },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                    ) {
                        Text("Reset to Default")
                    }
                    Button(
                        onClick = {
                            viewModel.updateColumns(columns)
                            onNavigateBack()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentStart)
                    ) {
                        Text("Save")
                    }
                }
            }
        },
        containerColor = BgPrimary
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(columns) { index, column ->
                ColumnRow(
                    index = index,
                    column = column,
                    onUpdate = { newCol ->
                        columns = columns.toMutableList().apply { set(index, newCol) }
                    },
                    onDelete = {
                        columns = columns.toMutableList().apply { removeAt(index) }
                    }
                )
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            columns = columns + ColumnConfig("custom", "New Column")
                        }
                        .border(1.dp, TextMuted, RoundedCornerShape(8.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+ Add Column", color = TextPrimary)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColumnRow(
    index: Int,
    column: ColumnConfig,
    onUpdate: (ColumnConfig) -> Unit,
    onDelete: () -> Unit
) {
    val columnLetter = (65 + index).toChar().toString() // A, B, C, ...

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E1E24))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2D2D36)),
                contentAlignment = Alignment.Center
            ) {
                Text(columnLetter, color = AccentStart, style = MaterialTheme.typography.titleMedium)
            }

            var expanded by remember { mutableStateOf(false) }
            
            Box(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = COLUMN_TYPES.find { it.first == column.type }?.second ?: "Unknown",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Type") },
                    trailingIcon = {
                        Icon(Icons.Default.ArrowDropDown, "Dropdown", modifier = Modifier.clickable { expanded = true })
                    },
                    modifier = Modifier.fillMaxWidth().clickable { expanded = true },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentStart,
                        unfocusedBorderColor = Color(0xFF3E3E4A),
                        focusedLabelColor = AccentStart,
                        unfocusedLabelColor = TextSecondary,
                        disabledBorderColor = Color(0xFF3E3E4A),
                        disabledTextColor = TextPrimary
                    ),
                    enabled = false
                )
                
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(Color(0xFF2D2D36))
                ) {
                    COLUMN_TYPES.forEach { (type, name) ->
                        DropdownMenuItem(
                            text = { Text(name, color = TextPrimary) },
                            onClick = {
                                onUpdate(column.copy(type = type))
                                expanded = false
                            }
                        )
                    }
                }
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Close, contentDescription = "Delete", tint = TextSecondary)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = column.label,
            onValueChange = { onUpdate(column.copy(label = it)) },
            label = { Text("Label") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = AccentStart,
                unfocusedBorderColor = Color(0xFF3E3E4A),
                focusedLabelColor = AccentStart,
                unfocusedLabelColor = TextSecondary
            )
        )

        if (column.type == "custom") {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = column.formula,
                onValueChange = { onUpdate(column.copy(formula = it)) },
                label = { Text("Formula (use {row} for current row)") },
                placeholder = { Text("=IF(B{row}<>0,B{row}-C{row},\"\")") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = AccentStart,
                    unfocusedBorderColor = Color(0xFF3E3E4A),
                    focusedLabelColor = AccentStart,
                    unfocusedLabelColor = TextSecondary
                )
            )
        }
    }
}
