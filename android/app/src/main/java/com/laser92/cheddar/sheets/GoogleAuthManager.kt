package com.laser92.cheddar.sheets

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.services.sheets.v4.SheetsScopes
import com.laser92.cheddar.data.PreferencesManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesManager: PreferencesManager
) {

    private val sheetsScope = Scope(SheetsScopes.SPREADSHEETS)
    
    private val signInOptions: GoogleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestEmail()
        .requestScopes(sheetsScope)
        .build()

    private val signInClient: GoogleSignInClient = GoogleSignIn.getClient(context, signInOptions)

    fun getSignInIntent() = signInClient.signInIntent

    /**
     * Initiates the Google Sign-In process.
     * 
     * @param activity The host activity launching the sign-in intent.
     * @return The signed-in Google account.
     */
    suspend fun signIn(activity: Activity): GoogleSignInAccount {
        // Typically launched via Intent in Activity, but since the requirement 
        // asks for a suspend function returning the account, we can await silent sign-in
        // or expect the activity to handle the intent. We'll attempt a silent sign-in 
        // first or rely on the Intent result if called via traditional flow.
        
        // This is a simplified approach using play services tasks.
        return try {
            val account = signInClient.silentSignIn().await()
            saveAccountEmail(account.email)
            account
        } catch (e: Exception) {
            throw Exception("Sign in required. Launch signInClient.signInIntent from Activity.", e)
        }
    }

    /**
     * Gets the Google Account Credential needed for the Sheets API.
     * 
     * @return HttpRequestInitializer to be used with Google Sheets API client.
     */
    fun getCredential(): HttpRequestInitializer {
        val account = GoogleSignIn.getLastSignedInAccount(context)
            ?: throw IllegalStateException("User is not signed in.")
            
        return GoogleAccountCredential.usingOAuth2(
            context,
            listOf(SheetsScopes.SPREADSHEETS)
        ).apply {
            selectedAccount = account.account
        }
    }

    /**
     * Checks if a user is currently signed in and has the necessary scopes.
     */
    fun isSignedIn(): Boolean {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        return account != null && GoogleSignIn.hasPermissions(account, sheetsScope)
    }

    /**
     * Gets the saved Google Account Email.
     */
    fun getAccountEmail(): String? {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        return account?.email ?: runBlocking {
            preferencesManager.googleAccountEmailFlow.first()
        }
    }

    /**
     * Signs out the current user.
     */
    fun signOut() {
        signInClient.signOut()
        runBlocking {
            preferencesManager.updateGoogleAccountEmail(null)
        }
    }

    private suspend fun saveAccountEmail(email: String?) {
        email?.let {
            preferencesManager.updateGoogleAccountEmail(it)
        }
    }
}
