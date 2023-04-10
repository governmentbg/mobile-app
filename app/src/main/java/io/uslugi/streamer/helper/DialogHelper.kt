package io.uslugi.streamer.helper

import android.content.Context
import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog

object DialogHelper {

    /**
     * Displays a Dialog message with three possible buttons:
     * - positive
     * - neutral
     * - negative
     * @param context - the parent context
     * @param title - dialog title
     * @param message[String] - dialog message
     * @param positiveButtonText - (optional) positive button text shown on the right-end of the dialog
     * @param neutralButtonText - (optional) neutral button text shown on the left-end of the dialog
     * @param negativeButtonText - (optional) negative button text shown in the middle of the dialog if all three buttons available.
     * @param onClick - `OnClickListener` for handling each button tap
     */
    fun displayAlertDialog(
        context: Context,
        title: String,
        message: String,
        positiveButtonText: String?,
        neutralButtonText: String?,
        negativeButtonText: String?,
        onClick: DialogInterface.OnClickListener
    ) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveButtonText, onClick)
            .setNeutralButton(neutralButtonText, onClick)
            .setNegativeButton(negativeButtonText, onClick)
            .setCancelable(false)
            .show()
    }
}