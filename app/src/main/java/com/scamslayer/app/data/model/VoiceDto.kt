package com.scamslayer.app.data.model

import com.google.gson.annotations.SerializedName

data class VoiceDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("gender") val gender: String,
    @SerializedName("age_group") val ageGroup: String
)

data class CustomPersonaDetail(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String,
    @SerializedName("portrait_url") val portraitUrl: String?,
    @SerializedName("voice_id") val voiceId: String,
    @SerializedName("voice_settings") val voiceSettings: Map<String, Double>,
    @SerializedName("age") val age: Int = 40,
    @SerializedName("gender") val gender: String = "male",
    @SerializedName("original_description") val originalDescription: String = "",
    @SerializedName("system_prompt") val systemPrompt: String = "",
    @SerializedName("is_custom") val isCustom: Boolean = true
)

data class GeneratePersonaResponse(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String,
    @SerializedName("portrait_url") val portraitUrl: String?,
    @SerializedName("voice_id") val voiceId: String,
    @SerializedName("voice_settings") val voiceSettings: Map<String, Double>,
    @SerializedName("is_custom") val isCustom: Boolean = true
)
