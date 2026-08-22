package com.laser92.cheddar.sheets

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.services.drive.DriveScopes
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import com.laser92.cheddar.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scopes = listOf(
        Scope(SheetsScopes.SPREADSHEETS),
        Scope(DriveScopes.DRIVE_READONLY)
    )

    private val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(scopes[0], scopes[1])
        .build()

    private val googleSignInClient: GoogleSignInClient = GoogleSignIn.getClient(context, gso)

    private val _signedInAccount = MutableStateFlow<GoogleSignInAccount?>(GoogleSignIn.getLastSignedInAccount(context))
    val signedInAccount: StateFlow<GoogleSignInAccount?> = _signedInAccount.asStateFlow()

    fun getSignInIntent(): Intent {
        return googleSignInClient.signInIntent
    }

    fun handleSignInResult(intent: Intent?): Result<GoogleSignInAccount> {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(intent)
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            _signedInAccount.value = account
            Result.success(account!!)
        } catch (e: com.google.android.gms.common.api.ApiException) {
            _signedInAccount.value = null
            val errorMsg = "Sign-in failed. Status code: ${e.statusCode}" + 
                if (e.statusCode == 10) " (DEVELOPER_ERROR: Check SHA-1 in Google Cloud)" else ""
            Result.failure(Exception(errorMsg))
        } catch (e: Exception) {
            _signedInAccount.value = null
            Result.failure(e)
        }
    }

    fun getCredential(): HttpRequestInitializer {
        val account = _signedInAccount.value?.account
        if (account != null) {
            val credential = GoogleAccountCredential.usingOAuth2(context, listOf(SheetsScopes.SPREADSHEETS, DriveScopes.DRIVE_READONLY))
            credential.selectedAccount = account
            return credential
        } else {
            val inputStream = context.resources.openRawResource(R.raw.service_account)
            val credentials = GoogleCredentials.fromStream(inputStream)
                .createScoped(listOf(SheetsScopes.SPREADSHEETS))
            return HttpCredentialsAdapter(credentials)
        }
    }

    fun isSignedIn(): Boolean {
        return _signedInAccount.value != null
    }

    fun getAccountEmail(): String? {
        return _signedInAccount.value?.email
    }

    fun signOut() {
        googleSignInClient.signOut().addOnCompleteListener {
            _signedInAccount.value = null
        }
    }
}
