package com.scamslayer.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "scamslayer_settings")

class SettingsRepository(private val context: Context) {

    companion object {
        private val KEY_SELECTED_PERSONA_ID = stringPreferencesKey("selected_persona_id")
        private val KEY_SHOW_ON_ALL_CALLS = booleanPreferencesKey("show_on_all_calls")
        private val KEY_BACKEND_URL = stringPreferencesKey("backend_url")
        private val KEY_TWILIO_FORWARD_NUMBER = stringPreferencesKey("twilio_forward_number")
        private val KEY_USER_PHONE_NUMBER = stringPreferencesKey("user_phone_number")
        private val KEY_CALLBACK_MODE = booleanPreferencesKey("callback_mode")

        private const val DEFAULT_BACKEND_URL = "https://hamstring-science-safeguard.ngrok-free.dev"
        private const val DEFAULT_TWILIO_NUMBER = "+420910924371"

        @Volatile
        private var instance: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository {
            return instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    val selectedPersonaId: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_SELECTED_PERSONA_ID] ?: ""
        }

    val showOnAllCalls: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_SHOW_ON_ALL_CALLS] ?: false
        }

    val backendUrl: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_BACKEND_URL] ?: DEFAULT_BACKEND_URL
        }

    val twilioForwardNumber: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_TWILIO_FORWARD_NUMBER] ?: DEFAULT_TWILIO_NUMBER
        }

    val userPhoneNumber: Flow<String> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_USER_PHONE_NUMBER] ?: ""
        }

    suspend fun setSelectedPersonaId(personaId: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SELECTED_PERSONA_ID] = personaId
        }
    }

    suspend fun setShowOnAllCalls(show: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SHOW_ON_ALL_CALLS] = show
        }
    }

    suspend fun setBackendUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_BACKEND_URL] = url
        }
    }

    suspend fun setTwilioForwardNumber(number: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_TWILIO_FORWARD_NUMBER] = number
        }
    }

    suspend fun getShowOnAllCallsSync(): Boolean {
        return context.dataStore.data.first()[KEY_SHOW_ON_ALL_CALLS] ?: false
    }

    suspend fun getSelectedPersonaIdSync(): String {
        return context.dataStore.data.first()[KEY_SELECTED_PERSONA_ID] ?: ""
    }

    suspend fun getBackendUrlSync(): String {
        return context.dataStore.data.first()[KEY_BACKEND_URL] ?: DEFAULT_BACKEND_URL
    }

    suspend fun getTwilioForwardNumberSync(): String {
        return context.dataStore.data.first()[KEY_TWILIO_FORWARD_NUMBER] ?: DEFAULT_TWILIO_NUMBER
    }

    val callbackMode: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_CALLBACK_MODE] ?: false
        }

    suspend fun setCallbackMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_CALLBACK_MODE] = enabled
        }
    }

    suspend fun getCallbackModeSync(): Boolean {
        return context.dataStore.data.first()[KEY_CALLBACK_MODE] ?: false
    }

    suspend fun setUserPhoneNumber(number: String) {
        context.dataStore.edit { preferences ->
            preferences[KEY_USER_PHONE_NUMBER] = number
        }
    }

    suspend fun getUserPhoneNumberSync(): String {
        return context.dataStore.data.first()[KEY_USER_PHONE_NUMBER] ?: ""
    }
}
