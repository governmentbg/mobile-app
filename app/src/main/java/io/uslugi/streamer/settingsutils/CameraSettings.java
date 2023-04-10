package io.uslugi.streamer.settingsutils;

import static io.uslugi.streamer.helper.Constants.Config.Advanced.PREFERRED_CAMERA_API;
import static io.uslugi.streamer.helper.Constants.Config.Video.DEFAULT_CAMERA;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import io.uslugi.libcommon.CameraInfo;
import io.uslugi.libcommon.CameraRegistry;
import com.wmspanel.libstream.Streamer;
import io.uslugi.streamer.R;
import io.uslugi.streamer.cameramanager.CameraListHelper;
import io.uslugi.streamer.helper.Constants;

import java.util.List;

public class CameraSettings {
    private static final String TAG = "CameraSettings";

    public static final int API_CAMERA = 1;
    public static final int API_CAMERA2 = 2;

    public static boolean isUsingCamera2(final Context context) {
        switch (PREFERRED_CAMERA_API) {
            case API_CAMERA:
                return false;
            case API_CAMERA2:
                return true;
            default:
                return CameraRegistry.allowCamera2Support(context);
        }
    }

    public static CameraInfo getActiveCameraInfo(final List<CameraInfo> cameraList) {
        if (cameraList == null || cameraList.size() == 0) {

            Log.e(TAG, "no camera found");

            return null;
        }

        final String cameraId = DEFAULT_CAMERA;
        CameraInfo cameraInfo = CameraListHelper.findById(cameraId, cameraList);
        if (cameraInfo == null) {
            cameraInfo = cameraList.get(0);
        }
        return cameraInfo;
    }

    public static Streamer.FpsRange getFpsRange(final Context context,
                                                final CameraInfo cameraInfo) {
        return cameraInfo.findFpsRange(fpsRange(context));
    }

    public static Streamer.Size getVideoSize(final Context context,
                                             final CameraInfo cameraInfo) {
        // migrate stored value from index to width/height
        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        if (sp.contains("video_size_list")) {
            final Streamer.Size videoSize;
            String videoSizeList = sp.getString("video_size_list", null);
            int sizeIndex = (videoSizeList != null) ? Integer.parseInt(videoSizeList) : 0;
            if (sizeIndex < 0 || sizeIndex >= cameraInfo.recordSizes.size()) {
                videoSize = new Streamer.Size(1920, 1080);
            } else {
                videoSize = cameraInfo.recordSizes.get(sizeIndex);
            }
            sp.edit().remove("video_size_list")
                    .putInt(context.getString(R.string.stream_width), videoSize.width)
                    .putInt(context.getString(R.string.stream_height), videoSize.height).apply();
            return videoSize;
        }

        return videoSize();
    }

    /**
     * Returns the video resolution which is 1280x720
     * @return Streamer.Size(1280, 720)
     */
    public static Streamer.Size videoSize() {
        return new Streamer.Size(
                Constants.Config.Video.RESOLUTION_WIDTH,
                Constants.Config.Video.RESOLUTION_HEIGHT
        );
    }

    public static Streamer.FpsRange fpsRange(final Context context) {
        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);

        final int fpsMin = sp.getInt(context.getString(R.string.fps_range_min_key), -1);
        final int fpsMax = sp.getInt(context.getString(R.string.fps_range_max_key), -1);

        if (fpsMin < 0 || fpsMax < 0) {
            return null;
        }

        return new Streamer.FpsRange(fpsMin, fpsMax);
    }

    public static void fillZoomSteps(final List<Float> zoomSteps,
                                     final float maxZoom) {
        final int stepsPer2XFactor = 20;
        final int nSteps = (int) ((stepsPer2XFactor * Math.log(maxZoom + 1.0e-11)) / Math.log(2.0));
        final double scaleFactor = Math.pow(maxZoom, 1.0 / (double) nSteps);
        double zoom = 1.0;

        for (int i = 0; i < nSteps - 1; i++) {
            zoom *= scaleFactor;
            zoomSteps.add((float) Math.round(zoom * 100) / 100f);
            //Log.d(TAG, "zoom: " + zoom);
        }
    }

    public static float findNextZoom(final boolean zoomIn,
                                     final List<Float> zoomSteps,
                                     final float maxZoom,
                                     final float scaleFactor) {
        float nextZoom;
        if (zoomIn) {
            nextZoom = maxZoom;
            for (float zoom : zoomSteps) {
                if (zoom > scaleFactor) {
                    //Log.d(TAG, "scaleFactor=" + scaleFactor + " zoom=" + zoom);
                    nextZoom = zoom;
                    break;
                }
            }
        } else {
            nextZoom = 1.0f;
            for (int i = zoomSteps.size() - 1; i >= 0; i--) {
                final float zoom = zoomSteps.get(i);
                if (zoom < scaleFactor) {
                    //Log.d(TAG, "scaleFactor=" + scaleFactor + " zoom=" + zoom);
                    nextZoom = zoom;
                    break;
                }
            }
        }
        return nextZoom;
    }

}
