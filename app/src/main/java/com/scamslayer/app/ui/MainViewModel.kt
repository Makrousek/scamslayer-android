package com.scamslayer.app.ui

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.messaging.FirebaseMessaging
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import com.scamslayer.app.data.SettingsRepository
import com.scamslayer.app.data.api.ApiClient
import com.scamslayer.app.data.model.CustomPersonaDetail
import com.scamslayer.app.data.model.GeneratePersonaResponse
import com.scamslayer.app.data.model.PersonaDto
import com.scamslayer.app.data.model.RecordingDto
import com.scamslayer.app.data.model.VoiceDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UiState(
    val personas: List<PersonaDto> = emptyList(),
    val recordings: List<RecordingDto> = emptyList(),
    val voices: List<VoiceDto> = emptyList(),
    val isLoadingPersonas: Boolean = false,
    val isLoadingRecordings: Boolean = false,
    val isGeneratingPersona: Boolean = false,
    val personasError: String? = null,
    val recordingsError: String? = null,
    val snackbarMessage: String? = null,
    val forwardingFailed: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = SettingsRepository.getInstance(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val selectedPersonaId: StateFlow<String> = settingsRepository.selectedPersonaId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val showOnAllCalls: StateFlow<Boolean> = settingsRepository.showOnAllCalls
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val backendUrl: StateFlow<String> = settingsRepository.backendUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "http://10.0.2.2:8000")

    val twilioForwardNumber: StateFlow<String> = settingsRepository.twilioForwardNumber
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val userPhoneNumber: StateFlow<String> = settingsRepository.userPhoneNumber
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val callbackMode: StateFlow<Boolean> = settingsRepository.callbackMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _isSetupComplete = MutableStateFlow<Boolean?>(null) // null = checking
    val isSetupComplete: StateFlow<Boolean?> = _isSetupComplete.asStateFlow()

    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    init {
        checkSetup()
    }

    private fun checkSetup() {
        viewModelScope.launch {
            val phone = settingsRepository.getUserPhoneNumberSync()
            _isSetupComplete.value = phone.isNotBlank()
            if (phone.isNotBlank()) {
                loadPersonas()
                loadRecordings()
                registerFcmToken()
                checkForwardingStatus()
                checkPremiumStatus()
            }
        }
    }

    fun completeSetup(phoneNumber: String) {
        viewModelScope.launch {
            settingsRepository.setUserPhoneNumber(phoneNumber)
            _isSetupComplete.value = true
            loadPersonas()
            loadRecordings()
            registerFcmToken()
            checkForwardingStatus()
        }
    }

    /**
     * Query *#67# via USSD to check if busy-forwarding is active to our Twilio number.
     * Updates the toggle to match actual carrier state.
     */
    @Suppress("MissingPermission")
    private fun checkForwardingStatus() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val hasCallPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasCallPermission) return@launch

            val twilioNumber = settingsRepository.getTwilioForwardNumberSync()
            if (twilioNumber.isBlank()) return@launch

            // Last 9 digits for matching (carriers format numbers differently)
            val needle = twilioNumber.replace("+", "").takeLast(9)

            try {
                val tm = context.getSystemService(TelephonyManager::class.java)
                val handler = android.os.Handler(android.os.Looper.getMainLooper())

                tm.sendUssdRequest("*#67#", object : TelephonyManager.UssdResponseCallback() {
                    override fun onReceiveUssdResponse(
                        telephonyManager: TelephonyManager,
                        request: String,
                        response: CharSequence
                    ) {
                        val responseStr = response.toString()
                        Log.i("MainViewModel", "Forwarding status response: $responseStr")
                        val isForwarded = responseStr.contains(needle)
                        // Only update UI state, do NOT trigger USSD toggle
                        viewModelScope.launch {
                            settingsRepository.setShowOnAllCalls(isForwarded)
                        }
                    }

                    override fun onReceiveUssdResponseFailed(
                        telephonyManager: TelephonyManager,
                        request: String,
                        failureCode: Int
                    ) {
                        Log.w("MainViewModel", "Forwarding status check failed: $failureCode")
                    }
                }, handler)
            } catch (e: Exception) {
                Log.w("MainViewModel", "Failed to check forwarding status: ${e.message}")
            }
        }
    }

    private fun checkPremiumStatus() {
        viewModelScope.launch {
            try {
                val url = settingsRepository.getBackendUrlSync()
                val userNumber = settingsRepository.getUserPhoneNumberSync()
                val result = ApiClient.getService(url).getPromoStatus(userNumber)
                _isPremium.value = result["premium"] == true
            } catch (e: Exception) {
                Log.w("MainViewModel", "Failed to check premium: ${e.message}")
            }
        }
    }

    fun validatePromoCode(code: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val url = settingsRepository.getBackendUrlSync()
                val userNumber = settingsRepository.getUserPhoneNumberSync()
                val result = ApiClient.getService(url).validatePromoCode(
                    mapOf("code" to code, "user_number" to userNumber)
                )
                val premium = result["premium"] == true
                _isPremium.value = premium
                val status = result["status"]?.toString() ?: ""
                val msg = when (status) {
                    "activated" -> "Kód aktivován! Máte plný přístup."
                    "already_active" -> "Už máte plný přístup."
                    else -> "Kód aktivován."
                }
                onResult(true, msg)
            } catch (e: retrofit2.HttpException) {
                val msg = when (e.code()) {
                    404 -> "Neplatný kód"
                    410 -> "Kód již byl vyčerpán"
                    else -> "Chyba: ${e.message()}"
                }
                onResult(false, msg)
            } catch (e: Exception) {
                onResult(false, "Chyba: ${e.message}")
            }
        }
    }

    private fun registerFcmToken() {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            Log.i("MainViewModel", "FCM token: $token")
            viewModelScope.launch {
                try {
                    val backendUrl = settingsRepository.getBackendUrlSync()
                    val userNumber = settingsRepository.getUserPhoneNumberSync()
                    if (backendUrl.isBlank()) return@launch

                    ApiClient.getService(backendUrl).registerFcmToken(
                        mapOf("user_number" to userNumber, "fcm_token" to token)
                    )
                    Log.i("MainViewModel", "FCM token registered on backend")
                } catch (e: Exception) {
                    Log.w("MainViewModel", "Failed to register FCM token: ${e.message}")
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopVoicePreview()
    }

    @Suppress("MissingPermission")
    private fun autoDetectPhoneNumber() {
        viewModelScope.launch {
            // Only auto-fill if user hasn't set it manually
            val current = settingsRepository.getUserPhoneNumberSync()
            if (current.isNotBlank()) return@launch

            try {
                val ctx = getApplication<Application>()
                val hasPermission = ContextCompat.checkSelfPermission(
                    ctx, Manifest.permission.READ_PHONE_NUMBERS
                ) == PackageManager.PERMISSION_GRANTED

                if (!hasPermission) return@launch

                val tm = ctx.getSystemService(TelephonyManager::class.java)
                val number = tm?.line1Number
                if (!number.isNullOrBlank()) {
                    settingsRepository.setUserPhoneNumber(number)
                    Log.i("MainViewModel", "Auto-detected phone number: $number")
                }
            } catch (e: Exception) {
                Log.d("MainViewModel", "Could not auto-detect phone number: ${e.message}")
            }
        }
    }

    fun loadPersonas() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingPersonas = true, personasError = null)
            try {
                val url = settingsRepository.getBackendUrlSync()
                val userNumber = settingsRepository.getUserPhoneNumberSync()
                val personas = ApiClient.getService(url).getPersonas(userNumber = userNumber)
                _uiState.value = _uiState.value.copy(
                    personas = personas,
                    isLoadingPersonas = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingPersonas = false,
                    personasError = e.message ?: "Nepodařilo se načíst persony"
                )
            }
        }
    }

    fun loadRecordings() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingRecordings = true, recordingsError = null)
            try {
                val url = settingsRepository.getBackendUrlSync()
                resolvedBackendUrl = url
                val userNumber = settingsRepository.getUserPhoneNumberSync()
                val recordings = ApiClient.getService(url).getRecordings(userNumber = userNumber)
                _uiState.value = _uiState.value.copy(
                    recordings = recordings.sortedByDescending { it.timestamp },
                    isLoadingRecordings = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingRecordings = false,
                    recordingsError = e.message ?: "Nepodařilo se načíst nahrávky"
                )
            }
        }
    }

    fun selectPersona(personaId: String) {
        viewModelScope.launch {
            settingsRepository.setSelectedPersonaId(personaId)
            // Sync to backend so Twilio webhook always knows the user's persona
            try {
                val userNumber = settingsRepository.getUserPhoneNumberSync()
                if (userNumber.isNotBlank()) {
                    val url = settingsRepository.getBackendUrlSync()
                    ApiClient.getService(url).updateUserSettings(
                        mapOf("user_number" to userNumber, "persona_id" to personaId)
                    )
                }
            } catch (e: Exception) {
                Log.d("MainViewModel", "Failed to sync persona to backend: ${e.message}")
            }
        }
    }

    fun setCallbackMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setCallbackMode(enabled)
            if (enabled) {
                // Disable forwarding when callback is enabled
                settingsRepository.setShowOnAllCalls(false)
            }
        }
    }

    fun testCallPersona(personaId: String) {
        viewModelScope.launch {
            try {
                val url = settingsRepository.getBackendUrlSync()
                val userNumber = settingsRepository.getUserPhoneNumberSync()
                ApiClient.getService(url).prepareBridgeCall(
                    mapOf("persona_id" to personaId, "user_number" to userNumber)
                )
                val twilioNumber = settingsRepository.getTwilioForwardNumberSync()
                val context = getApplication<Application>()
                context.startActivity(Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$twilioNumber")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    snackbarMessage = "Nepodařilo se připravit testovací hovor: ${e.message}"
                )
            }
        }
    }

    fun setShowOnAllCalls(show: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShowOnAllCalls(show)
            if (show) {
                // Disable callback when forwarding is enabled
                settingsRepository.setCallbackMode(false)
            }
            toggleCarrierForwarding(show)
        }
    }

    /**
     * Activate/deactivate carrier call forwarding via USSD.
     * Uses sendUssdRequest() for fully automated execution (no dialer UI).
     * Falls back to ACTION_CALL, then ACTION_DIAL if needed.
     */
    @Suppress("MissingPermission")
    private suspend fun toggleCarrierForwarding(enable: Boolean) {
        val context = getApplication<Application>()
        val twilioNumber = settingsRepository.getTwilioForwardNumberSync()

        if (enable && twilioNumber.isBlank()) {
            _uiState.value = _uiState.value.copy(
                snackbarMessage = "Twilio číslo není nastaveno!"
            )
            return
        }

        val ussdCode = if (enable) "**67*$twilioNumber#" else "##67#"

        val hasCallPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasCallPermission) {
            // No permission — open dialer for manual confirmation
            dialUssd(context, ussdCode)
            _uiState.value = _uiState.value.copy(
                snackbarMessage = "Stiskněte Volat pro potvrzení přesměrování"
            )
            return
        }

        // Try sendUssdRequest (fully automated, no UI)
        try {
            val tm = context.getSystemService(TelephonyManager::class.java)
            val handler = android.os.Handler(android.os.Looper.getMainLooper())

            tm.sendUssdRequest(ussdCode, object : TelephonyManager.UssdResponseCallback() {
                override fun onReceiveUssdResponse(
                    telephonyManager: TelephonyManager,
                    request: String,
                    response: CharSequence
                ) {
                    Log.i("MainViewModel", "USSD response: $response")
                    _uiState.value = _uiState.value.copy(
                        snackbarMessage = if (enable) "Přesměrování aktivováno" else "Přesměrování deaktivováno"
                    )
                }

                override fun onReceiveUssdResponseFailed(
                    telephonyManager: TelephonyManager,
                    request: String,
                    failureCode: Int
                ) {
                    Log.w("MainViewModel", "USSD failed ($failureCode)")
                    viewModelScope.launch {
                        settingsRepository.setShowOnAllCalls(false)
                    }
                    _uiState.value = _uiState.value.copy(
                        forwardingFailed = true,
                        snackbarMessage = "Přesměrování nefunguje u vašeho tarifu. Zkontrolujte, zda nemáte předplacenou kartu."
                    )
                }
            }, handler)
        } catch (e: Exception) {
            Log.w("MainViewModel", "sendUssdRequest threw, falling back to ACTION_CALL", e)
            callUssd(context, ussdCode)
        }
    }

    private fun callUssd(context: android.content.Context, ussdCode: String) {
        try {
            @Suppress("MissingPermission")
            context.startActivity(Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:${Uri.encode(ussdCode)}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        } catch (e: Exception) {
            Log.w("MainViewModel", "ACTION_CALL failed, falling back to DIAL", e)
            dialUssd(context, ussdCode)
        }
    }

    private fun dialUssd(context: android.content.Context, ussdCode: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:${Uri.encode(ussdCode)}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                snackbarMessage = "Chyba přesměrování: ${e.message}"
            )
        }
    }

    fun setBackendUrl(url: String) {
        viewModelScope.launch {
            settingsRepository.setBackendUrl(url)
            // Reload data with new URL
            loadPersonas()
            loadRecordings()
        }
    }

    fun setTwilioForwardNumber(number: String) {
        viewModelScope.launch {
            settingsRepository.setTwilioForwardNumber(number)
        }
    }

    fun setUserPhoneNumber(number: String) {
        viewModelScope.launch {
            settingsRepository.setUserPhoneNumber(number)
        }
    }

    fun renameRecording(recordingId: String, title: String) {
        viewModelScope.launch {
            try {
                val url = settingsRepository.getBackendUrlSync()
                ApiClient.getService(url).renameRecording(recordingId, mapOf("title" to title))
                // Update local list
                _uiState.value = _uiState.value.copy(
                    recordings = _uiState.value.recordings.map {
                        if (it.id == recordingId) it.copy(title = title) else it
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    snackbarMessage = "Přejmenování selhalo: ${e.message}"
                )
            }
        }
    }

    fun deleteRecording(recordingId: String) {
        viewModelScope.launch {
            try {
                val url = settingsRepository.getBackendUrlSync()
                ApiClient.getService(url).deleteRecording(recordingId)
                _uiState.value = _uiState.value.copy(
                    recordings = _uiState.value.recordings.filter { it.id != recordingId }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    snackbarMessage = "Nepodařilo se smazat nahrávku: ${e.message}"
                )
            }
        }
    }

    fun clearSnackbar() {
        _uiState.value = _uiState.value.copy(snackbarMessage = null)
    }

    private var resolvedBackendUrl: String? = null

    fun getAudioUrl(recordingId: String): String {
        return ApiClient.getAudioUrl(recordingId, resolvedBackendUrl ?: backendUrl.value)
    }

    fun shareAudioFile(recordingId: String, title: String) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val ctx = getApplication<Application>()
                val baseUrl = resolvedBackendUrl ?: backendUrl.value
                val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl.dropLast(1) else baseUrl
                val mp3Url = "$normalizedUrl/api/recordings/$recordingId/audio.mp3"

                // Download MP3 to cache
                val cacheDir = java.io.File(ctx.cacheDir, "shared_audio")
                cacheDir.mkdirs()
                val safeTitle = title.take(30).replace(Regex("[^a-zA-Z0-9]"), "_")
                val file = java.io.File(cacheDir, "scamslayer_$safeTitle.mp3")

                Log.i("MainViewModel", "Downloading MP3 from $mp3Url")
                val request = okhttp3.Request.Builder().url(mp3Url).build()
                val response = ApiClient.okHttpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    Log.w("MainViewModel", "MP3 download failed: ${response.code}")
                    _uiState.value = _uiState.value.copy(snackbarMessage = "Stahování selhalo: HTTP ${response.code}")
                    return@launch
                }

                response.body!!.byteStream().use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                Log.i("MainViewModel", "Downloaded ${file.length()} bytes to ${file.path}")

                val uri = androidx.core.content.FileProvider.getUriForFile(
                    ctx, "${ctx.packageName}.fileprovider", file
                )

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "audio/mpeg"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "ScamSlayer: $title")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(Intent.createChooser(shareIntent, "Share Recording").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (e: Exception) {
                Log.e("MainViewModel", "Share audio failed", e)
                _uiState.value = _uiState.value.copy(
                    snackbarMessage = "Sdílení selhalo: ${e.message ?: e.javaClass.simpleName}"
                )
            }
        }
    }

    // ---------------------------------------------------------------
    // Custom Persona generation
    // ---------------------------------------------------------------

    fun loadCustomPersonaDetail(
        personaId: String,
        onResult: (CustomPersonaDetail?, String?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val url = settingsRepository.getBackendUrlSync()
                val detail = ApiClient.getService(url).getCustomPersonaDetail(personaId)
                onResult(detail, null)
            } catch (e: Exception) {
                onResult(null, e.message ?: "Failed to load")
            }
        }
    }

    fun loadVoices() {
        viewModelScope.launch {
            try {
                val url = settingsRepository.getBackendUrlSync()
                val voices = ApiClient.getService(url).getVoices()
                _uiState.value = _uiState.value.copy(voices = voices)
            } catch (e: Exception) {
                Log.w("MainViewModel", "Failed to load voices: ${e.message}")
            }
        }
    }

    fun generateCustomPersona(
        description: String,
        age: Int,
        gender: String,
        onSuccess: (GeneratePersonaResponse) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGeneratingPersona = true)
            try {
                val url = settingsRepository.getBackendUrlSync()
                val userNumber = settingsRepository.getUserPhoneNumberSync()
                val result = ApiClient.getService(url).generatePersona(
                    mapOf(
                        "description" to description,
                        "age" to age,
                        "gender" to gender,
                        "user_number" to userNumber
                    )
                )
                _uiState.value = _uiState.value.copy(isGeneratingPersona = false)
                // Reload personas to include the new one
                loadPersonas()
                onSuccess(result)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isGeneratingPersona = false)
                onError(e.message ?: "Generation failed")
            }
        }
    }

    fun deleteCustomPersona(personaId: String) {
        viewModelScope.launch {
            try {
                val url = settingsRepository.getBackendUrlSync()
                ApiClient.getService(url).deleteCustomPersona(personaId)
                // Remove from local list
                _uiState.value = _uiState.value.copy(
                    personas = _uiState.value.personas.filter { it.id != personaId }
                )
                // If it was selected, clear selection
                if (selectedPersonaId.value == personaId) {
                    val first = _uiState.value.personas.firstOrNull()
                    if (first != null) selectPersona(first.id)
                }
                _uiState.value = _uiState.value.copy(snackbarMessage = "Persona smazána")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    snackbarMessage = "Smazání selhalo: ${e.message}"
                )
            }
        }
    }

    fun regeneratePortrait(personaId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                val url = settingsRepository.getBackendUrlSync()
                ApiClient.getService(url).regeneratePortrait(personaId)
                loadPersonas()
                onDone()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    snackbarMessage = "Regenerace portrétu selhala: ${e.message}"
                )
                onDone()
            }
        }
    }

    fun regenerateDescription(personaId: String, onDone: (PersonaDto?) -> Unit) {
        viewModelScope.launch {
            try {
                val url = settingsRepository.getBackendUrlSync()
                val updated = ApiClient.getService(url).regenerateDescription(personaId)
                loadPersonas()
                onDone(updated)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    snackbarMessage = "Regenerace popisu selhala: ${e.message}"
                )
                onDone(null)
            }
        }
    }

    fun updateProfile(personaId: String, description: String, age: Int, gender: String = "", onDone: (PersonaDto?) -> Unit) {
        viewModelScope.launch {
            try {
                val url = settingsRepository.getBackendUrlSync()
                val request = mutableMapOf<String, Any>("description" to description, "age" to age)
                if (gender.isNotBlank()) request["gender"] = gender
                val updated = ApiClient.getService(url).updateProfile(personaId, request)
                loadPersonas()
                onDone(updated)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    snackbarMessage = "Aktualizace profilu selhala: ${e.message}"
                )
                onDone(null)
            }
        }
    }

    fun updatePersonaName(personaId: String, name: String) {
        viewModelScope.launch {
            try {
                val url = settingsRepository.getBackendUrlSync()
                ApiClient.getService(url).updatePersonaName(personaId, mapOf("name" to name))
                loadPersonas()
            } catch (e: Exception) {
                Log.w("MainViewModel", "Failed to update name: ${e.message}")
            }
        }
    }

    fun updateVoice(personaId: String, voiceId: String, voiceSettings: Map<String, Double>) {
        viewModelScope.launch {
            try {
                val url = settingsRepository.getBackendUrlSync()
                ApiClient.getService(url).updateVoice(
                    personaId,
                    mapOf("voice_id" to voiceId, "voice_settings" to voiceSettings)
                )
            } catch (e: Exception) {
                Log.w("MainViewModel", "Failed to update voice: ${e.message}")
            }
        }
    }

    fun updateSystemPrompt(personaId: String, systemPrompt: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val url = settingsRepository.getBackendUrlSync()
                ApiClient.getService(url).updateSystemPrompt(personaId, mapOf("system_prompt" to systemPrompt))
                onDone()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    snackbarMessage = "Aktualizace promptu selhala: ${e.message}"
                )
                onDone()
            }
        }
    }

    fun testVoice(voiceId: String, stability: Float, similarity: Float) {
        viewModelScope.launch {
            try {
                stopVoicePreview()
                _previewPlaying.value = "test"

                val url = settingsRepository.getBackendUrlSync()
                val normalizedUrl = if (url.endsWith("/")) url.dropLast(1) else url

                // Download the test audio
                val requestBody = okhttp3.RequestBody.create(
                    "application/json".toMediaTypeOrNull(),
                    com.google.gson.Gson().toJson(
                        mapOf(
                            "voice_id" to voiceId,
                            "voice_settings" to mapOf(
                                "stability" to stability.toDouble(),
                                "similarity_boost" to similarity.toDouble()
                            ),
                            "text" to "Ahoj, tady testovací ukázka hlasu. Jak to zní? Doufám, že se vám líbí."
                        )
                    )
                )
                val request = okhttp3.Request.Builder()
                    .url("$normalizedUrl/api/voices/test")
                    .post(requestBody)
                    .build()

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val response = ApiClient.okHttpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val cacheDir = java.io.File(getApplication<Application>().cacheDir, "voice_test")
                        cacheDir.mkdirs()
                        val file = java.io.File(cacheDir, "test_voice.mp3")
                        response.body!!.byteStream().use { input ->
                            file.outputStream().use { output -> input.copyTo(output) }
                        }

                        val player = MediaPlayer().apply {
                            setDataSource(file.absolutePath)
                            setOnCompletionListener {
                                _previewPlaying.value = null
                                it.release()
                                mediaPlayer = null
                            }
                            setOnErrorListener { mp, _, _ ->
                                _previewPlaying.value = null
                                mp.release()
                                mediaPlayer = null
                                true
                            }
                            prepare()
                            start()
                        }
                        mediaPlayer = player
                    } else {
                        _previewPlaying.value = null
                    }
                }
            } catch (e: Exception) {
                Log.w("MainViewModel", "Voice test error: ${e.message}")
                _previewPlaying.value = null
            }
        }
    }

    fun getFullUrl(path: String): String {
        val base = resolvedBackendUrl ?: backendUrl.value
        val normalizedUrl = if (base.endsWith("/")) base.dropLast(1) else base
        return "$normalizedUrl$path"
    }

    // Voice preview playback
    private var mediaPlayer: MediaPlayer? = null

    private val _previewPlaying = MutableStateFlow<String?>(null)
    val previewPlaying: StateFlow<String?> = _previewPlaying.asStateFlow()

    fun playVoicePreview(personaId: String) {
        viewModelScope.launch {
            try {
                stopVoicePreview()
                _previewPlaying.value = personaId

                val url = settingsRepository.getBackendUrlSync()
                val normalizedUrl = if (url.endsWith("/")) url.dropLast(1) else url
                val previewUrl = "$normalizedUrl/api/personas/$personaId/preview"

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val request = okhttp3.Request.Builder().url(previewUrl).build()
                    val response = ApiClient.okHttpClient.newCall(request).execute()
                    if (!response.isSuccessful) {
                        _previewPlaying.value = null
                        return@withContext
                    }
                    val cacheDir = java.io.File(getApplication<Application>().cacheDir, "voice_preview")
                    cacheDir.mkdirs()
                    val file = java.io.File(cacheDir, "${personaId}_preview.mp3")
                    response.body!!.byteStream().use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    }

                    val player = MediaPlayer().apply {
                        setDataSource(file.absolutePath)
                        setOnCompletionListener {
                            _previewPlaying.value = null
                            it.release()
                            mediaPlayer = null
                        }
                        setOnErrorListener { mp, _, _ ->
                            _previewPlaying.value = null
                            mp.release()
                            mediaPlayer = null
                            true
                        }
                        prepare()
                        start()
                    }
                    mediaPlayer = player
                }
            } catch (e: Exception) {
                Log.w("MainViewModel", "Voice preview error: ${e.message}")
                _previewPlaying.value = null
            }
        }
    }

    fun stopVoicePreview() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        _previewPlaying.value = null
    }
}
