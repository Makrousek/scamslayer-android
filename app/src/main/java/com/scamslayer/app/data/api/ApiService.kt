package com.scamslayer.app.data.api

import com.scamslayer.app.data.model.CallStatusDto
import com.scamslayer.app.data.model.CustomPersonaDetail
import com.scamslayer.app.data.model.GeneratePersonaResponse
import com.scamslayer.app.data.model.InitiateCallRequest
import com.scamslayer.app.data.model.PersonaDto
import com.scamslayer.app.data.model.RecordingDto
import com.scamslayer.app.data.model.VoiceDto
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @GET("/api/personas")
    suspend fun getPersonas(@Query("user_number") userNumber: String = "", @Query("language") language: String = ""): List<PersonaDto>

    @GET("/api/recordings")
    suspend fun getRecordings(@Query("user_number") userNumber: String = ""): List<RecordingDto>

    @GET("/api/recordings/{id}")
    suspend fun getRecording(@Path("id") id: String): RecordingDto

    @POST("/api/recordings/{id}/rename")
    suspend fun renameRecording(@Path("id") id: String, @Body request: Map<String, String>)

    @DELETE("/api/recordings/{id}")
    suspend fun deleteRecording(@Path("id") id: String)

    @POST("/api/calls/initiate")
    suspend fun initiateCall(@Body request: InitiateCallRequest): CallStatusDto

    @POST("/api/calls/prepare")
    suspend fun prepareBridgeCall(@Body request: Map<String, String>)

    @POST("/api/users/settings")
    suspend fun updateUserSettings(@Body request: Map<String, String>)

    @POST("/api/users/fcm-token")
    suspend fun registerFcmToken(@Body request: Map<String, String>)

    @POST("/api/calls/callback")
    suspend fun callbackScammer(@Body request: Map<String, String>): Map<String, String>

    @GET("/api/calls/{callSid}/status")
    suspend fun getCallStatus(@Path("callSid") callSid: String): CallStatusDto

    // Custom personas
    @GET("/api/personas/custom/{id}")
    suspend fun getCustomPersonaDetail(@Path("id") id: String): CustomPersonaDetail

    @POST("/api/personas/generate")
    suspend fun generatePersona(@Body request: Map<String, @JvmSuppressWildcards Any>): GeneratePersonaResponse

    @DELETE("/api/personas/custom/{id}")
    suspend fun deleteCustomPersona(@Path("id") id: String)

    @POST("/api/personas/custom/{id}/regenerate-portrait")
    suspend fun regeneratePortrait(@Path("id") id: String): Map<String, String>

    @POST("/api/personas/custom/{id}/regenerate-description")
    suspend fun regenerateDescription(@Path("id") id: String): PersonaDto

    @POST("/api/personas/custom/{id}/profile")
    suspend fun updateProfile(@Path("id") id: String, @Body request: Map<String, @JvmSuppressWildcards Any>): PersonaDto

    @POST("/api/personas/custom/{id}/name")
    suspend fun updatePersonaName(@Path("id") id: String, @Body request: Map<String, String>)

    @POST("/api/personas/custom/{id}/voice")
    suspend fun updateVoice(@Path("id") id: String, @Body request: Map<String, @JvmSuppressWildcards Any>)

    @POST("/api/personas/custom/{id}/system-prompt")
    suspend fun updateSystemPrompt(@Path("id") id: String, @Body request: Map<String, String>)

    // Promo codes
    @POST("/api/promo/validate")
    suspend fun validatePromoCode(@Body request: Map<String, String>): Map<String, @JvmSuppressWildcards Any>

    @GET("/api/promo/status")
    suspend fun getPromoStatus(@Query("user_number") userNumber: String): Map<String, @JvmSuppressWildcards Any>

    // Voices
    @GET("/api/voices")
    suspend fun getVoices(): List<VoiceDto>

    @POST("/api/voices/test")
    suspend fun testVoice(@Body request: Map<String, @JvmSuppressWildcards Any>): ResponseBody
}
