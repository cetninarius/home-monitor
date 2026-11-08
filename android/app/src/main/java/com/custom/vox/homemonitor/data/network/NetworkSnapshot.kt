package com.custom.vox.homemonitor.data.network

data class NetworkSnapshot(
    val online: Boolean,
    val validatedInternet: Boolean,
    val transportLabel: String,
    val ipv4: String?,
    val gateway: String?,
)
