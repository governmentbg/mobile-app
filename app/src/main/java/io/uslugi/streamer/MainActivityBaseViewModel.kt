package io.uslugi.streamer

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.uslugi.streamer.api.ServerApi
import io.uslugi.streamer.data.Section
import io.uslugi.streamer.helper.Constants
import io.uslugi.streamer.helper.SharedPreferencesHelper.storeElection
import io.uslugi.streamer.helper.SharedPreferencesHelper.storeRtmpUrl
import kotlinx.coroutines.launch
import org.json.JSONException

private const val TAG = "MainActivityBaseViewModel"

class MainActivityBaseViewModel : ViewModel() {

    // PROPERTIES ⤵

    private var _isLoggedIn = MutableLiveData<Boolean?>()
    val isLoggedIn: LiveData<Boolean?>
        get() = _isLoggedIn

    // PUBLIC METHODS ⤵

    /**
     * Trigger the execution of getting RTMP url
     * @param section - the [Section] object needed for creating the request body
     * @param context
     */
    fun retryLogin(section: Section, context: Context) {
        val client = ServerApi.getInstance()
        viewModelScope.launch {
            client.getRtmpUrl(section)?.onSuccess { response ->
                try {
                    response.let {
                        storeRtmpUrl(it?.stream_url, context)
                        storeElection(it?.election, context)
                        _isLoggedIn.value = true
                    }
                    Log.i(TAG, "mainActivityBaseViewModel.retryLogin onSuccess response")
                } catch (exception: JSONException) {
                    Log.e(
                        TAG,
                        "mainActivityBaseViewModel.retryLogin onSuccess exception - ${exception.message}"
                    )
                    makeNewGetRtmpUrlAttempt(section, context)
                }
            }?.onFailure { exception ->
                Log.e(TAG, "mainActivityBaseViewModel.retryLogin onFailure - ${exception.message}")
                makeNewGetRtmpUrlAttempt(section, context)
            }
        }
    }

    // PRIVATE METHODS ⤵

    /**
     * Make new attempt each 10 seconds to login
     * @param section - the [Section] object needed for creating the request body
     * @param context
     */
    private fun makeNewGetRtmpUrlAttempt(section: Section, context: Context) {
        Handler(Looper.getMainLooper()).postDelayed({
            retryLogin(section, context)
        }, Constants.DelayTimes.RETRY_TIME_DELAY)
    }
}