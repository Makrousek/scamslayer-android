package com.scamslayer.app.data.model

import com.google.gson.annotations.SerializedName

data class PersonaDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("description") val description: String,
    @SerializedName("is_custom") val isCustom: Boolean = false,
    @SerializedName("portrait_url") val portraitUrl: String? = null
)
