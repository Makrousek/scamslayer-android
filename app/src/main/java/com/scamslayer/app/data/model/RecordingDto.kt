package com.scamslayer.app.data.model

import com.google.gson.annotations.SerializedName

data class RecordingDto(
    @SerializedName("id") val id: String,
    @SerializedName("call_sid") val callSid: String,
    @SerializedName("persona") val persona: String,
    @SerializedName("caller_number") val callerNumber: String?,
    @SerializedName("timestamp") val timestamp: String,
    @SerializedName("duration_seconds") val durationSeconds: Double,
    @SerializedName("title") val title: String?,
    @SerializedName("transcript") val transcript: String?
)
