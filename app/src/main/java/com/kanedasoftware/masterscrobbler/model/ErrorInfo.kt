package com.kanedasoftware.masterscrobbler.model


import com.google.gson.annotations.SerializedName

data class ErrorInfo(
    val error: Int = 0,
    val message: String = ""
)