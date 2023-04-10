package io.uslugi.streamer.cameramanager;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import io.uslugi.libcommon.CameraInfo;

import com.wmspanel.libstream.CameraConfig;
import com.wmspanel.libstream.Streamer;
import com.wmspanel.libstream.StreamerGLBuilder;
import io.uslugi.streamer.settingsutils.CameraSettings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CameraListHelper {
    private static final String TAG = "CameraListHelper";

    public static List<String> addCameras(final Context context,
                                          final StreamerGLBuilder builder,
                                          final List<CameraInfo> cameraList,
                                          final CameraInfo activeCameraInfo,
                                          final Streamer.Size videoSize) {
        return addDefaultCameras(
                    context, builder, cameraList, activeCameraInfo, videoSize);
    }

    public static List<String> addDefaultCameras(final Context context,
                                                 final StreamerGLBuilder builder,
                                                 final List<CameraInfo> cameraList,
                                                 final CameraInfo activeCameraInfo,
                                                 final Streamer.Size videoSize) {
        // start adding cameras from default camera, then add second camera
        // larix uses same resolution for camera preview and stream to simplify setup
        final List<String> uuids = new ArrayList<>();

        // add first camera to flip list, make sure you called setVideoConfig before
        final CameraConfig cameraConfig = new CameraConfig();
        cameraConfig.cameraId = activeCameraInfo.cameraId;
        cameraConfig.videoSize = activeCameraInfo.findVideoSize(videoSize);
        cameraConfig.fpsRange = CameraSettings.getFpsRange(context, activeCameraInfo);

        addCamera(cameraConfig, builder, uuids);
        Log.d(TAG, "Camera #" + cameraConfig.cameraId + " resolution: " + cameraConfig.videoSize);

        // set start position in flip list to camera id
        builder.setCameraId(activeCameraInfo.cameraId);

        final boolean canFlip = cameraList.size() > 1;
        if (canFlip) {
            // loop through the available cameras
            for (CameraInfo cameraInfo : cameraList) {
                if (cameraInfo.cameraId.equals(activeCameraInfo.cameraId)) {
                    continue;
                }
                // add next camera to flip list
                final CameraConfig flipCameraConfig = new CameraConfig();
                flipCameraConfig.cameraId = cameraInfo.cameraId;
                flipCameraConfig.videoSize = cameraInfo.findVideoSize(videoSize);
                flipCameraConfig.fpsRange = CameraSettings.getFpsRange(context, cameraInfo);

                addCamera(flipCameraConfig, builder, uuids);
                Log.d(TAG, "Camera #" + flipCameraConfig.cameraId + " resolution: " + flipCameraConfig.videoSize);
            }
        }
        return uuids;
    }

    public static List<String> addConcurrentCameras() {

        return new ArrayList<>();
    }

    public static Map<String, CameraInfo> toMap(final List<CameraInfo> cameraList,
                                                final boolean includePhysicalCameras) {
        // LinkedHashMap presents the items in the insertion order
        final Map<String, CameraInfo> map = new LinkedHashMap<>();
        for (CameraInfo info : cameraList) {
            map.put(uuid(info.cameraId, null), info);
            if (!includePhysicalCameras) {
                continue;
            }
            for (CameraInfo subInfo : info.physicalCameras) {
                map.put(uuid(info.cameraId, subInfo.cameraId), subInfo);
            }
        }
        return map;
    }

    public static String uuid(final String id, final String physicalId) {
        // JSONObject's empty string or null -> no physical camera ID
        return TextUtils.isEmpty(physicalId) ? id : id + ":" + physicalId;
    }

    @Nullable
    public static CameraInfo findById(final String id, final List<CameraInfo> cameraList) {
        if (cameraList != null && !TextUtils.isEmpty(id)) {
            for (CameraInfo info : cameraList) {
                if (info.cameraId.equals(id)) {
                    return info;
                }
            }
        }
        return null;
    }

    private static void addCamera(CameraConfig cameraConfig,
                                  StreamerGLBuilder builder,
                                  List<String> uuids) {
        builder.addCamera(cameraConfig);
        uuids.add(uuid(cameraConfig.cameraId, cameraConfig.physicalCameraId));
    }
}
