package io.uslugi.streamer.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import io.uslugi.streamer.R
import io.uslugi.streamer.databinding.FragmentLoginBinding

class LoginFragment : Fragment() {

    // PROPERTIES ⤵

    private lateinit var binding: FragmentLoginBinding

    //  Launcher, that we need to show camera permission dialog
    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                scanQR()
            } else {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.PERMISSION_DENIED_CAMERA),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    // OVERRIDES ⤵

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentLoginBinding.inflate(layoutInflater)

        setUpLogInButton()
        setUpVersionData()

        return binding.root
    }

    // PRIVATE METHODS ⤵

    private fun setUpLogInButton() = binding.scanQrButton.setOnClickListener { scanQR() }

    // Triggers the scanning process by navigating to `QRFragment`
    // Also handles the camera permission
    private fun scanQR() {
        val isCameraPermissionGranted = context?.let {
            ContextCompat.checkSelfPermission(it, Manifest.permission.CAMERA)
        }

        if (isCameraPermissionGranted == PackageManager.PERMISSION_DENIED) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        } else {
            val action = LoginFragmentDirections.actionLoginFragmentToQrFragment()
            findNavController().navigate(action)
        }
    }

    /**
     * Sets up the version data text view which has the following format:
     * - Версия: 1.0 (1000)
     */
    @SuppressLint("SetTextI18n")
    private fun setUpVersionData() {
        val versionLabel = getString(R.string.VERSION_LABEL)
        val versionData = getString(R.string.VERSION_DATA)

        binding.versionInfo.text = "$versionLabel $versionData"
    }
}