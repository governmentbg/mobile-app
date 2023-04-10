package io.uslugi.streamer.ui.testresultscreen

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.uslugi.streamer.api.CheckApi
import io.uslugi.streamer.api.LoadingStatusState
import io.uslugi.streamer.data.TestCheckResultRequest
import io.uslugi.streamer.exception.ApplicationException
import io.uslugi.streamer.exception.ApplicationExceptions.FAILED_INITIAL_TEST
import io.uslugi.streamer.exception.ApplicationExceptions.FAILED_SIK_TEST
import io.uslugi.streamer.helper.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private const val CHECK_CALL_DELAY = 3000L

class TestResultFragmentViewModel : ViewModel() {

    // PROPERTIES ⤵

    // Tracks the loading state
    val loadingState = MutableStateFlow<LoadingStatusState>(LoadingStatusState.START)

    // Check test result attempts count
    private var checksCounter = 1

    // PUBLIC ⤵

    /**
     * The function make a call to determine whether the rest was successful.
     *
     * @param mode - needs for [TestCheckResultRequest] object
     * @param udi - needs for [TestCheckResultRequest] object
     */
    fun checkTestResult(mode: String, udi: String) {
        viewModelScope.launch {
            loadingState.emit(LoadingStatusState.LOADING)

            viewModelScope.launch {
                try {
                    CheckApi.getInstance()
                        .checkTestResult(TestCheckResultRequest(mode, udi))
                        ?.onSuccess { response ->
                            response?.let {
                                if (
                                    it.result == Constants.Response.OK &&
                                    it.error.isNullOrBlank()
                                ) {
                                    loadingState.emit(LoadingStatusState.SUCCESS)
                                } else {
                                    isMakeNewCheckAttempt(mode, udi)
                                }
                            }
                        }?.onFailure {
                            isMakeNewCheckAttempt(mode, udi)
                        }
                } catch (exception: Exception) {
                    isMakeNewCheckAttempt(mode, udi)
                }
            }
        }
    }

    // PRIVATE ⤵

    /**
     * Check if the check attempts are less than six. If yes continue with the attempts, if not
     * set loadingState value to FAILURE.
     */
    private suspend fun isMakeNewCheckAttempt(mode: String, udi: String) {
        // Add one attempt more
        checksCounter++

        if (checksCounter < 6) {
            Handler(Looper.getMainLooper()).postDelayed({
                checkTestResult(mode, udi)
            }, CHECK_CALL_DELAY)
        } else {
            if (mode == Constants.Mode.TEST_SETUP) {
                loadingState.emit(
                    LoadingStatusState.FAILURE(
                        ApplicationException(
                            FAILED_INITIAL_TEST
                        )
                    )
                )
            } else {
                loadingState.emit(LoadingStatusState.FAILURE(ApplicationException(FAILED_SIK_TEST)))
            }
        }
    }
}