package io.uslugi.streamer.settingsutils;

import static io.uslugi.streamer.helper.Constants.Config.Recording.IS_RECORDING_ON;
import static io.uslugi.streamer.helper.Constants.Config.Recording.IS_SPLIT_VIDEO_ENABLED;
import static io.uslugi.streamer.helper.Constants.Config.Recording.LOG_TO_FILE;
import static io.uslugi.streamer.helper.Constants.Config.Recording.SPLIT_VIDEO_DURATION;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;

import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.wmspanel.libstream.Streamer;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import io.uslugi.libcommon.MediaFileUtils;
import io.uslugi.streamer.R;
import io.uslugi.streamer.data.Section;
import io.uslugi.streamer.data.SikSingleton;
import io.uslugi.streamer.helper.SharedPreferencesHelper;

public final class MediaFileSettings {
    private static final String folder = "SIK-Streamer";

    /**
     * Combine different values received from the QR code and use them for a file name.
     * Final name will be election_mode_udi_sik_timestamp
     * The timestamp is in format yyyyMMdd_HHmmss_SS
     *
     * @return the name of the video file used for election recording
     */
    private static String basename(String election, String mode, String udi, String sik) {
        return addValueToBasename(election)
                .concat(addValueToBasename(mode))
                .concat(addValueToBasename(udi))
                .concat(addValueToBasename(sik))
                .concat(new SimpleDateFormat("yyyyMMdd_HHmmss_SS", Locale.US).format(new Date()));
    }

    /**
     * Adds "_" at the end of the given value in case if it is not an empty string.
     *
     * @param value is a string to which will be added "_"
     * @return "value_" if value is not null or empty, otherwise it will return empty string
     */
    private static String addValueToBasename(String value) {
        if (value != null && !value.isEmpty()) {
            return value + "_";
        }
        return "";
    }

    public static boolean record(final Context context) {
        return recordOn() && writeAllowed(context);
    }

    public static boolean recordOn() {
        return IS_RECORDING_ON;
    }

    public static int recordingIntervalMillis() {

        if (!IS_SPLIT_VIDEO_ENABLED) {
            return 0;
        }
        final int interval = SPLIT_VIDEO_DURATION;
        if (interval < 1) {
            return 0;
        }
        return interval * 60_000;
    }

    public static boolean startRecord(final Context context,
                                      final Streamer streamer) {
        return startRecord(context, streamer, Streamer.Mode.AUDIO_VIDEO);
    }

    public static boolean startRecord(final Context context,
                                      final Streamer streamer,
                                      final Streamer.Mode mode) {
        return startRecord(context, streamer, mode, IS_SPLIT_VIDEO_ENABLED);
    }

    public static boolean splitRecord(final Context context,
                                      final Streamer streamer) {
        return startRecord(context, streamer, Streamer.Mode.AUDIO_VIDEO, IS_SPLIT_VIDEO_ENABLED);
    }

    private static boolean startRecord(final Context context,
                                       final Streamer streamer,
                                       final Streamer.Mode mode,
                                       final boolean split) {
        if (!record(context)) {
            return false;
        }

        final SharedPreferencesHelper myPref = SharedPreferencesHelper.INSTANCE;
        Section section = SikSingleton.INSTANCE.getCurrentSection();
        String election = myPref.getElection(context);
        String udi = myPref.getUdi(context);
        String baseName = basename(election, section.getCurrentMode(), udi, section.getSik());

        return MediaFileUtils.startRecordDCIM(
                    context, streamer,
                    folder, baseName,
                    mode, split);
    }

    public static String onSaveFinished(final Context context,
                                        final Uri uri,
                                        final Streamer.SaveMethod method,
                                        final MediaScannerConnection.OnScanCompletedListener callback) {
        if (uri == null || method == null) {
            return null;
        }
        return MediaFileUtils.onCompleted(context, uri, method, callback);
    }

    public static String logUri(final Context context) {
        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        return LOG_TO_FILE ? sp.getString(context.getString(R.string.log_uri_key), null) : null;
    }

    public static boolean writeAllowed(Context context) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            return true;
        }
        final int check = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        return check == PackageManager.PERMISSION_GRANTED;
    }
}
