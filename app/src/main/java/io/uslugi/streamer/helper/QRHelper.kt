package io.uslugi.streamer.helper

import com.google.gson.Gson
import io.uslugi.streamer.data.QrResult
import io.uslugi.streamer.data.Section

object QRHelper {
    /**
     * Parses the passed in QR string as [Section]
     * @param data - string to be parsed
     * @return Section object
     */
    fun parse(data: String): QrResult = Gson().fromJson(data, QrResult::class.java)
}