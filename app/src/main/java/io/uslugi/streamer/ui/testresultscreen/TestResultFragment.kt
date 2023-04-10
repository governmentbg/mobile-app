package io.uslugi.streamer.ui.testresultscreen

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources.getDrawable
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import io.uslugi.streamer.R
import io.uslugi.streamer.api.LoadingStatusState
import io.uslugi.streamer.data.Section
import io.uslugi.streamer.data.SikSingleton
import io.uslugi.streamer.data.SikSingleton.getCurrentSection
import io.uslugi.streamer.databinding.FragmentTestResultBinding
import io.uslugi.streamer.helper.Constants
import io.uslugi.streamer.helper.SharedPreferencesHelper
import io.uslugi.streamer.ui.MainActivity
import kotlinx.coroutines.launch

class TestResultFragment : Fragment() {

    // PROPERTIES ⤵

    private lateinit var binding: FragmentTestResultBinding
    private lateinit var viewModel: TestResultFragmentViewModel

    // OVERRIDES ⤵

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentTestResultBinding.inflate(layoutInflater)
        viewModel = ViewModelProvider(this)[TestResultFragmentViewModel::class.java]


        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val section = getCurrentSection()
        val udi = SharedPreferencesHelper.getUdi(requireContext())

        // Start check test result
        viewModel.checkTestResult(section.mode, udi)

        // Observe test results and set Succeeded or Failed screen
        observeLoadingState(section)
    }

    // PRIVATE METHODS ⤵

    /**
     * The observe the different states of loadingState:
     *
     * LoadingStatusState.START -> do nothing
     * LoadingStatusState.LOADING -> show loader
     * LoadingStatusState.SUCCESS -> hide loader and set succeeded screen
     * LoadingStatusState.FAILURE -> hide loader and set failed screen
     *
     * @param [section] - needs to handle tests results
     *
     */
    private fun observeLoadingState(section: Section) =
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.loadingState.flowWithLifecycle(
                viewLifecycleOwner.lifecycle
            ).collect { state ->

                when (state) {
                    LoadingStatusState.START -> {}

                    LoadingStatusState.LOADING -> {
                        (activity as MainActivity).startLoader()
                    }

                    LoadingStatusState.SUCCESS -> {
                        (activity as MainActivity).stopLoader()
                        setUpCloseButton()
                        setUpViewVisibility()
                        handleCheckResult(section, state)
                    }

                    is LoadingStatusState.FAILURE -> {
                        (activity as MainActivity).stopLoader()
                        setUpCloseButton()
                        setUpViewVisibility()
                        handleCheckResult(section, state)
                    }
                }
            }
        }

    /**
     * Defines how the screen would look like. There are two things that
     * are used as conditions: (1) section's mode and the (2) state.
     *
     * The screen will be green-like in case of TEST_SIK mode or SUCCESS state. The only difference
     * would be the TextView's text.
     *
     * The screen will be red-like if we are dealing with FAILURE state
     *
     * @param section - the [Section] object
     * @param state - the [LoadingStatusState] of the screen
     */
    private fun handleCheckResult(section: Section, state: LoadingStatusState) {
        val isGreen =
            (state == LoadingStatusState.SUCCESS || section.mode == Constants.Mode.TEST_SIK)

        // Determine screen's background color
        val backgroundColor = if (isGreen) {
            requireContext().getColor(R.color.sik_succeeded)
        } else {
            requireContext().getColor(R.color.sik_failed)
        }

        // Determine screen's icon resource
        val drawableRes = if (isGreen) {
            R.drawable.ic_success
        } else {
            R.drawable.ic_failed
        }

        // Determine screen's text
        val text = if (section.mode == Constants.Mode.TEST_SIK) {
            getString(R.string.TEST_SIK_SUCCESS_OR_FAIL_MESSAGE, section.sik)
        } else if (state == LoadingStatusState.SUCCESS) {
            getString(R.string.TEST_SETUP_SUCCESS_MESSAGE)
        } else {
            getString(R.string.TEST_SETUP_FAIL_MESSAGE)
        }

        // Determine screen's background
        binding.apply {
            if (section.mode == Constants.Mode.TEST_SIK) {
                testResultCl.background = getDrawable(requireContext(), R.drawable.background_login)
            } else if (state == LoadingStatusState.SUCCESS) {
                testResultCl.setBackgroundColor(backgroundColor)
            } else {
                testResultCl.setBackgroundColor(backgroundColor)
            }
        }

        // Assign the values to the respective UI elements
        binding.apply {
            resultLogo.setImageDrawable(getDrawable(requireContext(), drawableRes))
            resultText.text = text
        }
    }

    // Set up views visibility
    private fun setUpViewVisibility() {
        binding.apply {
            resultText.visibility = View.VISIBLE
            resultLogo.visibility = View.VISIBLE
            resultText.visibility = View.VISIBLE
        }
    }

    // Set up canceled button
    private fun setUpCloseButton() =
        binding.closeButton.apply {
            visibility = View.VISIBLE
            setOnClickListener {
                // Reset Section
                SikSingleton.resetSection()
                // Navigates the user to login fragment
                findNavController().navigate(TestResultFragmentDirections.actionTestResultFragmentToLoginFragment())
            }
        }
}