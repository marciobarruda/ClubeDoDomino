package com.marcioarruda.clubedodomino.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

// Data class to hold session information
data class UserSession(
    val userName: String,
    val userEmail: String
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_session")

class UserSessionManager(context: Context) {

    private val dataStore = context.dataStore

    companion object {
        private val USER_NAME_KEY = stringPreferencesKey("user_name")
        private val USER_EMAIL_KEY = stringPreferencesKey("user_email")
    }

    suspend fun saveSession(userName: String, userEmail: String) {
        dataStore.edit { preferences ->
            preferences[USER_NAME_KEY] = userName
            preferences[USER_EMAIL_KEY] = userEmail
        }
    }

    val getSession: Flow<UserSession?> = dataStore.data
        .catch { exception ->
            // DataStore throws IOException when an error is encountered when reading data
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val userName = preferences[USER_NAME_KEY]
            val userEmail = preferences[USER_EMAIL_KEY]
            if (userName != null && userEmail != null) {
                UserSession(userName, userEmail)
            } else {
                null
            }
        }

    suspend fun clearSession() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
