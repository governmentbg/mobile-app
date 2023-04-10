package io.uslugi.streamer.data

data class QrResult(
    val mode: String,
    var udi: String? = null,
    val sik: String? = null,
    val key: String
)