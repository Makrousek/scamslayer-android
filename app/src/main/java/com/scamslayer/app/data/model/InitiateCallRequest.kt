package com.scamslayer.app.data.model

import com.google.gson.annotations.SerializedName

data class InitiateCallRequest(
    @SerializedName("callerNumber") val callerNumber: String,
    @SerializedName("personaId") val personaId: String
)
