package io.uslugi.streamer.settingsutils;

import static io.uslugi.streamer.helper.Constants.Config.Video.CONCURRENT_CAMERA_MODE;
import static io.uslugi.streamer.helper.Constants.Config.Video.RESOLUTION_HEIGHT;
import static io.uslugi.streamer.helper.Constants.Config.Video.RESOLUTION_WIDTH;

import android.os.Build;

import com.wmspanel.libstream.Streamer;

public class ConcurrentCameraSettings {
    public static Streamer.ConcurrentCameraMode mode() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return Streamer.ConcurrentCameraMode.OFF;
        }

        switch (CONCURRENT_CAMERA_MODE) {
            case 1:
                return Streamer.ConcurrentCameraMode.PICTURE_IN_PICTURE;
            case 2:
                return Streamer.ConcurrentCameraMode.SIDE_BY_SIDE;
            case 0:
            default:
                return Streamer.ConcurrentCameraMode.OFF;
        }
    }

    public static Streamer.Size videoSize() {
        return new Streamer.Size(RESOLUTION_WIDTH, RESOLUTION_HEIGHT);
    }
}
