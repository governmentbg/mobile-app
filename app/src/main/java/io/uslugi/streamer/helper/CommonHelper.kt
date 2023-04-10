package io.uslugi.streamer.helper

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.view.WindowManager

/**
 * Common helper class for various helper methods
 */
object CommonHelper {

    // PUBLIC METHODS ⤵

    /**
     * Checks whether the device has internet connection
     * @return Boolean
     */
    fun isOnline(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities =
            connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)

        if (capabilities != null) {
            when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    return true
                }
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                    return true
                }
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                    return true
                }
            }
        }

        return false
    }

    /**
     * Set up brightness according [isDecreaseBrightness] parameter
     *
     * @param isDecreaseBrightness - if it's true decrease brightness to 50%, if false increase to 100%
     */
    fun setUpBrightness(activity: Activity?, isDecreaseBrightness: Boolean = true) {
        val layout: WindowManager.LayoutParams? = activity?.window?.attributes

        if (isDecreaseBrightness) {
            // Make brightness 50%
            layout?.screenBrightness = 0.5f
        } else {
            // Make brightness 100%
            layout?.screenBrightness = 1.0f
        }

        activity?.window?.attributes = layout
    }
}