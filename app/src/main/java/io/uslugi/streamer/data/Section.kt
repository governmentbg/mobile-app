package io.uslugi.streamer.data

import io.uslugi.streamer.helper.Constants

data class Section(
    val mode: String,
    val udi: String? = null,
    val sik: String? = null,
    val key: String
) {
    // In case of blanc mode we setup it in real
    fun getCurrentMode(): String = mode.ifBlank { Constants.Mode.UNKNOWN }
}

fun QrResult.asSection(): Section =
    Section(mode = this.mode, udi = this.udi, sik = this.sik, key = this.key)
