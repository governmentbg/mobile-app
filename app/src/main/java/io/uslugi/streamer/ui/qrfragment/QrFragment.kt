package io.uslugi.streamer.ui.qrfragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.budiyev.android.codescanner.*
import com.google.zxing.Result
import io.uslugi.streamer.R
import io.uslugi.streamer.data.ErrorType
import io.uslugi.streamer.data.Section
import io.uslugi.streamer.data.SikSingleton.getCurrentSection
import io.uslugi.streamer.data.SikSingleton.setUpSection
import io.uslugi.streamer.data.asSection
import io.uslugi.streamer.databinding.FragmentQrBinding
import io.uslugi.streamer.helper.ChaCha20Helper.decryptData
import io.uslugi.streamer.helper.ChaCha20Helper.getCipher
import io.uslugi.streamer.helper.ChaCha20Helper.getNonce
import io.uslugi.streamer.helper.CommonHelper
import io.uslugi.streamer.helper.Constants.SharedPreferences.SIK_ENCRYPTED_SHARED_PREFERENCES
import io.uslugi.streamer.helper.QRHelper
import io.uslugi.streamer.helper.SharedPreferencesHelper
import io.uslugi.streamer.helper.SharedPreferencesHelper.getEncryptedSharedPreferences
import io.uslugi.streamer.helper.SharedPreferencesHelper.getKeyenc
import io.uslugi.streamer.ui.MainActivity
import io.uslugi.streamer.ui.common.SharedViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Base64.getDecoder

private const val TAG = "QRFragment"

/**
 * Fragment that takes care of scanning a QR code related to section registration
 */
class QrFragment : Fragment() {

    // PROPERTIES ⤵

    private lateinit var binding: FragmentQrBinding
    private lateinit var codeScanner: CodeScanner
    private lateinit var mainActivity: MainActivity
    private val sharedViewModel: SharedViewModel by activityViewModels()

    // OVERRIDES ⤵

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentQrBinding.inflate(layoutInflater)

        mainActivity = activity as MainActivity

        // Set the action bar
        setActionBar()

        // Set CodeScanners
        initializeCodeScanner()

        // Start the registration process
        // 1.Scan QR code
        // 2.Get RTMP url
        // 3.Success and navigate to the streaming
        startRegistrationProcess()

        return binding.root
    }

    override fun onResume() {
        super.onResume()

        if (::codeScanner.isInitialized) {
            codeScanner.startPreview()
        }
    }

    override fun onPause() {
        if (::codeScanner.isInitialized) {
            codeScanner.releaseResources()
        }

        super.onPause()
    }

    override fun onStop() {
        // Hide loading when we navigate to next screen
        mainActivity.stopLoader()

        super.onStop()
    }

    // PRIVATE METHODS ⤵

    // Set action bar
    private fun setActionBar() {
        mainActivity.supportActionBar?.setBackgroundDrawable(null)
    }

    // Start the registration process
    private fun startRegistrationProcess() {
        // Create lifecycle flow to follow the different stages of the registration
        viewLifecycleOwner.lifecycleScope.launch {
            sharedViewModel.registrationStatusState.flowWithLifecycle(
                viewLifecycleOwner.lifecycle
            ).collect { state ->
                when (state) {
                    // 1. Scan QR code
                    RegistrationStatusState.ScanQrCode -> scanQrCode()

                    // 2. Get RTMP url
                    RegistrationStatusState.GetRtmpUrl -> {
                        // Show loading
                        mainActivity.startLoader()

                        try {
                            val section = getCurrentSection()
                            sharedViewModel.login(section, requireContext())
                        } catch (exception: Exception) {
                            // JSON parsing is failed
                            sharedViewModel.setRegistrationStatusState(
                                RegistrationStatusState.Failure(ErrorType.BAD_QR)
                            )
                        }
                    }

                    // 3. Steps 1 and 2 succeeded -> section is registered
                    RegistrationStatusState.Success -> {
                        registrationSuccessful()
                        sharedViewModel.setRegistrationStatusState(RegistrationStatusState.ScanQrCode)
                    }

                    // 99. Handle all errors that might occur in steps 1 to 3
                    is RegistrationStatusState.Failure -> {
                        // Handle errors
                        handleRegistrationErrors(state)
                    }
                }
            }
        }
    }

    // Handle the different types of registration errors
    private fun handleRegistrationErrors(state: RegistrationStatusState.Failure) =
        when (state.exception) {
            ErrorType.NO_INTERNET -> {
                handleFailedRegistration(getString(R.string.ERROR_NO_INTERNET))
            }
            ErrorType.BAD_QR -> {
                handleFailedRegistration(getString(R.string.ERROR_QR_CODE_TITLE))
            }
            ErrorType.FAILED_STORING_UDI -> {
                handleFailedRegistration(getString(R.string.ERROR_FAILED_STORING_UDI))
            }
            ErrorType.FAILED_STORING_SECTION -> {
                handleFailedRegistration(getString(R.string.ERROR_FAILED_STORING_SECTION))
            }
            ErrorType.SERVER_ERROR -> {
                handleFailedRegistration(getString(R.string.ERROR_INTERNAL_SERVER_ERROR_MESSAGE))
            }
            ErrorType.INVALID_UDI -> {
                handleFailedRegistration(getString(R.string.ERROR_INVALID_UDI))
            }
            ErrorType.GET_RTMP_URL_ERROR -> {
                handleFailedRegistration(getString(R.string.ERROR_UNKNOWN_EXCEPTION))
            }
            ErrorType.FAILED_STORING_RTMP_URL -> {
                handleFailedRegistration(getString(R.string.ERROR_FAILED_STORING_RTMP))
            }
        }

    // Handler failure during the Registration
    private fun handleFailedRegistration(errorMessage: String) {

        // Stop scanning
        codeScanner.stopPreview()

        // Log the error
        Log.e(TAG, "Error message - $errorMessage")

        // We move to the next screen because we always have to record the election/tests
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            sharedViewModel.setRegistrationStatusState(RegistrationStatusState.Success)
        }
    }

    // Starts the camera and the process of looking for QR code
    private fun scanQrCode() {
        // Callbacks
        codeScanner.decodeCallback = DecodeCallback {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    processQrResult(it)

                    if (CommonHelper.isOnline(requireContext())) {
                        sharedViewModel.setRegistrationStatusState(RegistrationStatusState.GetRtmpUrl)
                    } else {
                        codeScanner.stopPreview()
                        sharedViewModel.setRegistrationStatusState(
                            RegistrationStatusState.Failure(ErrorType.NO_INTERNET)
                        )
                    }
                } catch (exception: Exception) {
                    Log.e("DECRYPT", "$exception/${exception.localizedMessage}")

                    // If the QR code is not in JSON format
                    sharedViewModel.setRegistrationStatusState(
                        RegistrationStatusState.Failure(ErrorType.BAD_QR)
                    )
                }
            }
        }
    }

    /**
     * Store UDI in shared preferences
     * @param [udi] the UDI to be added in shared preferences
     */
    private fun storeUdi(udi: String) {
        try {
            SharedPreferencesHelper.storeUdi(udi = udi, context = requireContext())
        } catch (e: Exception) {
            RegistrationStatusState.Failure(ErrorType.FAILED_STORING_UDI)
        }
    }

    // Set CodeScanners properties
    private fun initializeCodeScanner() {
        val scannerView = binding.scannerView
        codeScanner = CodeScanner(requireContext(), scannerView)

        // Parameters (default values)
        codeScanner.apply {
            formats = CodeScanner.TWO_DIMENSIONAL_FORMATS // list of type BarcodeFormat,
            autoFocusMode = AutoFocusMode.SAFE // or CONTINUOUS
            scanMode = ScanMode.SINGLE // or CONTINUOUS or PREVIEW
            isAutoFocusEnabled = true // Whether to enable auto focus or not
            isFlashEnabled = false // Whether to enable flash or not

            errorCallback = ErrorCallback {
                Toast.makeText(
                    requireContext(),
                    "${getString(R.string.ERROR_PROBLEM_WITH_ACCESS_TO_CAMERA)} (${it.message})",
                    Toast.LENGTH_LONG
                ).show()
            }

            scannerView.setOnClickListener { codeScanner.startPreview() }
        }
    }

    /**
     * Process the QR code [Result]
     * 1. parse QR
     * 2. try to get `udi` if present in Shared Preferences
     * 3. store [Section]
     * 4. store `udi`
     * @param result - the QR code result
     */
    private fun processQrResult(result: Result) {
        try {
            val qrResult = QRHelper.parse(result.text)
            // If there is no UDI in the scanned QR then we use the stored
            if (qrResult.udi.isNullOrBlank()) {
                qrResult.udi = SharedPreferencesHelper.getUdi(requireContext())
            }
            // Persist section data to SharedPreference
            setUpSection(qrResult.asSection())
            qrResult.udi?.let { storeUdi(it) }
        } catch (e: Exception) {
            // If we fail to parse the QR, it means it's encrypted
            // So we try to decrypt it and then proceed
            tryToDecryptQrCode(result)
        }
    }

    /**
     * Try to decrypt the QR code [Result]
     * @param result - the QR code [Result] object
     */
    private fun tryToDecryptQrCode(result: Result) {
        val decodedResult = getDecoder().decode(result.text)
        val nonce = getNonce(decodedResult)
        val cipher = getCipher(decodedResult)

        // Extract decryption key
        val keyenc = getKeyenc(
            getEncryptedSharedPreferences(SIK_ENCRYPTED_SHARED_PREFERENCES, requireContext())
        )

        keyenc?.let { unwrappedKey ->
            // Decode the decryption key
            val decodedKey = getDecoder().decode(unwrappedKey)

            // Try to decrypt the data
            val decryptedJson = decryptData(
                cipherText = cipher,
                key = decodedKey,
                nonce = nonce
            )

            // Try to parse the decrypted data
            val parsedQrResult = decryptedJson?.let { QRHelper.parse(it) }

            parsedQrResult?.let { unwrappedQrResult ->
                // If there is no UDI in the scanned QR then we use the stored
                if (unwrappedQrResult.udi.isNullOrBlank()) {
                    unwrappedQrResult.udi = SharedPreferencesHelper.getUdi(requireContext())
                }

                // Persist section data to SharedPreference
                setUpSection(unwrappedQrResult.asSection())
            }
        }
    }

    // Navigate to streaming after successful registration and getting GetRtmp url
    private fun registrationSuccessful() = navigateToDashboard()

    // Navigates the user to streaming
    private fun navigateToDashboard() =
        findNavController().navigate(QrFragmentDirections.actionQrFragmentToLaunchActivity())
}