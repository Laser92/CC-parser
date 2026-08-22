package com.laser92.cheddar.drive

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.laser92.cheddar.sheets.GoogleAuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriveRepository @Inject constructor(
    private val authManager: GoogleAuthManager
) {
    private val jsonFactory = GsonFactory.getDefaultInstance()

    private fun getDriveService(): Drive {
        if (!authManager.isSignedIn()) {
            throw Exception("User is not signed in. Please sign in first.")
        }
        val credential = authManager.getCredential()

        return Drive.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            jsonFactory,
            credential
        ).setApplicationName("Cheddar").build()
    }

    suspend fun getSpreadsheets(): List<File> = withContext(Dispatchers.IO) {
        val service = getDriveService()
        val result = service.files().list()
            .setQ("mimeType='application/vnd.google-apps.spreadsheet' and trashed=false")
            .setSpaces("drive")
            .setFields("nextPageToken, files(id, name)")
            .execute()

        result.files ?: emptyList()
    }
}
