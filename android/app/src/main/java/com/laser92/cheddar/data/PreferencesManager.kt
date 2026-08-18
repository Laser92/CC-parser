package com.laser92.cheddar.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cheddar_prefs")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val dataStore = context.dataStore

    companion object {
        val SHEET_ID = stringPreferencesKey("sheet_id")
        val DEFAULT_CARD_NAME = stringPreferencesKey("default_card_name")
        val DEFAULT_STYLE = intPreferencesKey("default_style")
        val GOOGLE_ACCOUNT_EMAIL = stringPreferencesKey("google_account_email")
    }

    val sheetIdFlow: Flow<String> = dataStore.data.map { preferences ->
        preferences[SHEET_ID] ?: "1mmetc8XmMGdY3jsq6OBpc8VwhfP0wdKf-IbmHovtva8"
    }

    val defaultCardNameFlow: Flow<String> = dataStore.data.map { preferences ->
        preferences[DEFAULT_CARD_NAME] ?: "SBI"
    }

    val defaultStyleFlow: Flow<Int> = dataStore.data.map { preferences ->
        preferences[DEFAULT_STYLE] ?: 1
    }

    val googleAccountEmailFlow: Flow<String?> = dataStore.data.map { preferences ->
        preferences[GOOGLE_ACCOUNT_EMAIL]
    }

    suspend fun updateSheetId(sheetId: String) {
        dataStore.edit { preferences ->
            preferences[SHEET_ID] = sheetId
        }
    }

    suspend fun updateDefaultCardName(cardName: String) {
        dataStore.edit { preferences ->
            preferences[DEFAULT_CARD_NAME] = cardName
        }
    }

    suspend fun updateDefaultStyle(style: Int) {
        dataStore.edit { preferences ->
            preferences[DEFAULT_STYLE] = style
        }
    }

    suspend fun updateGoogleAccountEmail(email: String?) {
        dataStore.edit { preferences ->
            if (email == null) {
                preferences.remove(GOOGLE_ACCOUNT_EMAIL)
            } else {
                preferences[GOOGLE_ACCOUNT_EMAIL] = email
            }
        }
    }
}
