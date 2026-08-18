package com.laser92.cheddar.ui.screen

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.laser92.cheddar.ui.theme.*
import com.laser92.cheddar.viewmodel.ParserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ParserViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                viewModel.handleSignInResult(account.email)
            } catch (e: Exception) {
                viewModel.handleSignInResult(null)
            }
        }
    }

    Scaffold(
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
                if (uiState.isSignedIn) {
                    Column {
                        Text("Signed in as ${uiState.accountEmail}", color = TextPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = { viewModel.signOut() }) {
                            Text("Sign Out", color = TextPrimary)
                        }
                    }
                } else {
                    Button(
                        onClick = { googleSignInLauncher.launch(viewModel.getSignInIntent()) },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentStart)
                    ) {
                        Text("Sign in with Google", color = TextPrimary)
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
        }
    }
}
