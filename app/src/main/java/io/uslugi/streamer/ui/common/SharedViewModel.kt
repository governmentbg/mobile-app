package io.uslugi.streamer.ui.common

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.uslugi.streamer.BuildConfig
import io.uslugi.streamer.api.ServerApi
import io.uslugi.streamer.data.ErrorType
import io.uslugi.streamer.data.Section
import io.uslugi.streamer.helper.Constants
import io.uslugi.streamer.helper.SharedPreferencesHelper
import io.uslugi.streamer.ui.qrfragment.RegistrationStatusState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONException

class SharedViewModel : ViewModel() {

    // PROPERTIES

    /**
     * Use it to observe the registration state
     */
    private var _registrationStatusState =
        MutableStateFlow<RegistrationStatusState>(RegistrationStatusState.ScanQrCode)
    val registrationStatusState = _registrationStatusState.asStateFlow()

    // PUBLIC METHODS ⤵

    /**
     * Trigger the execution of getting RTMP url
     * @param section - the [Section] object needed for creating the request body
     */
    suspend fun login(section: Section, context: Context) {
        val client = ServerApi.getInstance()
        client.getRtmpUrl(section)?.onSuccess { response ->
            try {
                response.let {
                    // Get successful RTMP and finalize the registration
                    storeRtmpUrl(it?.stream_url, context)
                    storeElection(it?.election, context)
                    setRegistrationStatusState(RegistrationStatusState.Success)

                    it?.keyenc?.let { keyenc ->
                        // Store keyenc in the encrypted shared preference
                        SharedPreferencesHelper.storeKeyenc(
                            keyenc = keyenc,
                            sharedPref = SharedPreferencesHelper.getEncryptedSharedPreferences(
                                Constants.SharedPreferences.SIK_ENCRYPTED_SHARED_PREFERENCES,
                                context
                            )
                        )
                    }
                }
            } catch (exception: JSONException) {
                viewModelScope.launch {
                    if (BuildConfig.DEBUG) {
                        Toast.makeText(
                            context,
                            "JSON грешка: ${exception.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    setRegistrationStatusState(
                        RegistrationStatusState.Failure(ErrorType.GET_RTMP_URL_ERROR)
                    )
                }
            }
        }?.onFailure { exception ->
            var errorType = ErrorType.GET_RTMP_URL_ERROR
            when (exception.message) {
                "Invalid UDI" -> {
                    errorType = ErrorType.INVALID_UDI
                }
            }
            viewModelScope.launch {
                if (BuildConfig.DEBUG) {
                    Toast.makeText(
                        context,
                        "Грешка: ${exception.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }

                setRegistrationStatusState(
                    RegistrationStatusState.Failure(
                        errorType
                    )
                )
            }
        }
    }

    // PRIVATE METHODS ⤵

    // Set registration status state
    suspend fun setRegistrationStatusState(registrationStatusState: RegistrationStatusState) =
        _registrationStatusState.emit(registrationStatusState)

    /**
     * Store RTMP in shared preferences
     * @param [url] the UDI to be added in shared preferences
     */
    private fun storeRtmpUrl(url: String?, context: Context) {
        try {
            SharedPreferencesHelper.storeRtmpUrl(url = url, context = context)
        } catch (e: Exception) {
            RegistrationStatusState.Failure(ErrorType.FAILED_STORING_RTMP_URL)
        }
    }

    /**
     * Store to shared preference the passed in [String]
     * @param election - the election name
     */
    private fun storeElection(election: String?, context: Context) {
        SharedPreferencesHelper.storeElection(election = election, context = context)
    }
}