package com.scamslayer.app.data.model

import com.google.gson.annotations.SerializedName

data class CallStatusDto(
    @SerializedName("callSid") val callSid: String,
    @SerializedName("status") val status: String,
    @SerializedName("persona") val persona: String,
    @SerializedName("duration") val duration: Int
)
