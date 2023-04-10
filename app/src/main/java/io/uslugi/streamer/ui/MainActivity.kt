package io.uslugi.streamer.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import io.uslugi.streamer.R
import io.uslugi.streamer.databinding.ActivityMainBinding
import io.uslugi.streamer.helper.Constants.Extras.IS_TEST

class MainActivity : AppCompatActivity() {

    // PROPERTIES ⤵
    private lateinit var binding: ActivityMainBinding

    // OVERRIDES ⤵

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        supportActionBar?.hide()

        // Check for any extras
        checkExtras()

        setContentView(binding.root)
    }

    // PUBLIC METHODS ⤵

    // Start loader
    fun startLoader() {
        binding.mainLoader.visibility = View.VISIBLE
    }

    // Stop loader
    fun stopLoader() {
        binding.mainLoader.visibility = View.GONE
    }

    // PRIVATE ⤵

    // Check if it's opened by the streamer
    private fun checkExtras() {
        intent.extras?.let {
            val isTest = intent.getBooleanExtra(IS_TEST, false)

            if (isTest) {
                // Navigate to TestResultFragment, that we handle the test result and show the correct screen
                val navHostFragment =
                    supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
                val navController = navHostFragment.navController
                navController.navigate(R.id.testResultFragment)
            }
        }
    }
}