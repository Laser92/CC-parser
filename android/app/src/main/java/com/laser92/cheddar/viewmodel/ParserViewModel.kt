package com.laser92.cheddar.viewmodel

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.laser92.cheddar.data.PreferencesManager
import com.laser92.cheddar.export.XlsxWriter
import com.laser92.cheddar.parser.ImageParser
import com.laser92.cheddar.parser.PdfParser
import com.laser92.cheddar.sheets.GoogleAuthManager
import com.laser92.cheddar.sheets.ReconcileResult
import com.laser92.cheddar.sheets.SheetsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ParserViewModel @Inject constructor(
    private val pdfParser: PdfParser,
    private val imageParser: ImageParser,
    private val xlsxWriter: XlsxWriter,
    private val sheetsRepository: SheetsRepository,
    private val preferencesManager: PreferencesManager,
    private val authManager: GoogleAuthManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ParserUiState())
    val uiState: StateFlow<ParserUiState> = _uiState.asStateFlow()

    init {
        // Initialize state from auth manager and preferences
        viewModelScope.launch {
            _uiState.update { 
                it.copy(
                    isSignedIn = authManager.isSignedIn(),
                    accountEmail = authManager.getAccountEmail()
                )
            }
        }
    }

    fun selectFile(uri: Uri, fileName: String, fileSize: Long) {
        val isPdf = fileName.endsWith(".pdf", ignoreCase = true)
        _uiState.update { 
            it.copy(
                selectedFileUri = uri,
                selectedFileName = fileName,
                selectedFileSize = "${fileSize / 1024} KB",
                isPdf = isPdf
            )
        }
    }

    fun removeFile() {
        _uiState.update { 
            it.copy(
                selectedFileUri = null,
                selectedFileName = null,
                selectedFileSize = null,
                isPdf = false,
                password = "",
                reconcileResult = null
            )
        }
    }

    fun setPassword(password: String) {
        _uiState.update { it.copy(password = password) }
    }

    fun setCardName(name: String) {
        _uiState.update { it.copy(cardName = name) }
    }

    fun setStyle(style: Int) {
        _uiState.update { it.copy(style = style) }
    }

    fun setSheetId(id: String) {
        _uiState.update { it.copy(sheetId = id) }
        // Note: You could also save this to preferencesManager if it was implemented
    }

    fun getSignInIntent() = authManager.getSignInIntent()

    fun handleSignInResult(email: String?) {
        viewModelScope.launch {
            _uiState.update { 
                it.copy(
                    isSignedIn = email != null,
                    accountEmail = email,
                    success = if (email != null) "Signed in as $email" else "Sign-in failed"
                )
            }
        }
    }

    fun signOut() {
        authManager.signOut()
        _uiState.update { 
            it.copy(isSignedIn = false, accountEmail = null)
        }
    }

    fun downloadXlsx() {
        val uri = _uiState.value.selectedFileUri ?: return
        val isPdf = _uiState.value.isPdf
        val password = _uiState.value.password.takeIf { it.isNotBlank() }
        val cardName = _uiState.value.cardName
        val style = _uiState.value.style

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingAction = "xlsx") }
            try {
                withContext(Dispatchers.IO) {
                    val transactions = if (isPdf) {
                        pdfParser.extractTransactions(uri, password)
                    } else {
                        imageParser.extractTransactions(uri)
                    }

                    if (transactions.isEmpty()) {
                        throw Exception("No transactions found in file.")
                    }

                    // Save to public Downloads/Cheddar directory
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val cheddarDir = File(downloadsDir, "Cheddar")
                    if (!cheddarDir.exists()) cheddarDir.mkdirs()
                    
                    val outputFile = File(cheddarDir, "Statement_${System.currentTimeMillis()}.xlsx")
                    
                    val written = xlsxWriter.writeToXlsx(transactions, outputFile, cardName, style)
                    
                    if (written > 0) {
                        _uiState.update { it.copy(exportedFile = outputFile) }
                    } else {
                        throw Exception("Failed to write transactions to XLSX.")
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Failed to process") }
            } finally {
                _uiState.update { it.copy(isProcessing = false, processingAction = null) }
            }
        }
    }

    fun dismissExportedFile() {
        _uiState.update { it.copy(exportedFile = null) }
    }

    fun addToSheets() {
        val uri = _uiState.value.selectedFileUri ?: return
        val isPdf = _uiState.value.isPdf
        val password = _uiState.value.password.takeIf { it.isNotBlank() }
        val cardName = _uiState.value.cardName
        val sheetId = _uiState.value.sheetId

        if (sheetId.isBlank()) {
            _uiState.update { it.copy(error = "Google Sheet ID is missing! Configure it in Settings.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, processingAction = "sheets") }
            try {
                withContext(Dispatchers.IO) {
                    val transactions = if (isPdf) {
                        pdfParser.extractTransactions(uri, password)
                    } else {
                        imageParser.extractTransactions(uri)
                    }

                    if (transactions.isEmpty()) {
                        throw Exception("No transactions found in file.")
                    }

                    val result = sheetsRepository.reconcile(transactions, cardName, sheetId)
                    _uiState.update { it.copy(success = "Added to Google Sheets!", reconcileResult = result) }
                }
            } catch (e: Throwable) {
                _uiState.update { it.copy(error = e.message ?: "Failed to sync: ${e.javaClass.simpleName}") }
            } finally {
                _uiState.update { it.copy(isProcessing = false, processingAction = null) }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null, success = null) }
    }

    fun dismissResults() {
        _uiState.update { it.copy(reconcileResult = null) }
    }
}

data class ParserUiState(
    val selectedFileUri: Uri? = null,
    val selectedFileName: String? = null,
    val selectedFileSize: String? = null,
    val isPdf: Boolean = false,
    val password: String = "",
    val cardName: String = "SBI",
    val style: Int = 1,
    val isProcessing: Boolean = false,
    val processingAction: String? = null,
    val error: String? = null,
    val success: String? = null,
    val exportedFile: File? = null,
    val reconcileResult: ReconcileResult? = null,
    val isSignedIn: Boolean = false,
    val accountEmail: String? = null,
    val sheetId: String = "1mmetc8XmMGdY3jsq6OBpc8VwhfP0wdKf-IbmHovtva8"
)
