package io.uslugi.streamer.ui.qrfragment

import io.uslugi.streamer.data.ErrorType

sealed class RegistrationStatusState {
    object ScanQrCode : RegistrationStatusState()
    object GetRtmpUrl : RegistrationStatusState()
    object Success : RegistrationStatusState()

    data class Failure(val exception: ErrorType) : RegistrationStatusState()
}