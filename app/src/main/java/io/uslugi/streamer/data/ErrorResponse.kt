package io.uslugi.streamer.data

enum class ErrorType {
    SERVER_ERROR,
    INVALID_UDI,
    FAILED_STORING_UDI,
    FAILED_STORING_SECTION,
    BAD_QR,
    NO_INTERNET,
    GET_RTMP_URL_ERROR,
    FAILED_STORING_RTMP_URL
}
