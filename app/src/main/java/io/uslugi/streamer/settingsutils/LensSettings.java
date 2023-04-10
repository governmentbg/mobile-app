package io.uslugi.streamer.settingsutils;

import static io.uslugi.streamer.helper.Constants.Config.Video.ANTI_FLICKER;
import static io.uslugi.streamer.helper.Constants.Config.Video.ELECTRONIC_IMAGE_STABILIZATION;
import static io.uslugi.streamer.helper.Constants.Config.Video.EXPOSURE_COMPENSATION;
import static io.uslugi.streamer.helper.Constants.Config.Video.FOCUS_MODE;
import static io.uslugi.streamer.helper.Constants.Config.Video.NOISE_REDUCTION;
import static io.uslugi.streamer.helper.Constants.Config.Video.OPTICAL_IMAGE_STABILIZATION;
import static io.uslugi.streamer.helper.Constants.Config.Video.WHITE_BALANCE;

import android.content.Context;
import android.hardware.Camera;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.RggbChannelVector;

import com.wmspanel.libstream.FocusMode;
import io.uslugi.streamer.R;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class LensSettings {

    public static final int AF_MODE_CONTINUOUS_VIDEO = 0;
    public static final int AF_MODE_INFINITY = 1;

    public static final int AWB_MODE_AUTO = 0;
    public static final int AWB_MODE_CLOUDY_DAYLIGHT = 1;
    public static final int AWB_MODE_DAYLIGHT = 2;
    public static final int AWB_MODE_FLUORESCENT = 3;
    public static final int AWB_MODE_INCANDESCENT = 4;
    public static final int AWB_MODE_OFF = 5;
    public static final int AWB_MODE_SHADE = 6;
    public static final int AWB_MODE_TWILIGHT = 7;
    public static final int AWB_MODE_WARM_FLUORESCENT = 8;

    public static final Map<Integer, String> AWB_MAP_16 = createAwbMap16();

    private static Map<Integer, String> createAwbMap16() {
        final Map<Integer, String> result = new LinkedHashMap<>(); // LinkedHashMap maintains the insertion order
        result.put(AWB_MODE_AUTO, Camera.Parameters.WHITE_BALANCE_AUTO);
        result.put(AWB_MODE_CLOUDY_DAYLIGHT, Camera.Parameters.WHITE_BALANCE_CLOUDY_DAYLIGHT);
        result.put(AWB_MODE_DAYLIGHT, Camera.Parameters.WHITE_BALANCE_DAYLIGHT);
        result.put(AWB_MODE_FLUORESCENT, Camera.Parameters.WHITE_BALANCE_FLUORESCENT);
        result.put(AWB_MODE_INCANDESCENT, Camera.Parameters.WHITE_BALANCE_INCANDESCENT);
        result.put(AWB_MODE_OFF, Camera.Parameters.WHITE_BALANCE_AUTO);
        result.put(AWB_MODE_SHADE, Camera.Parameters.WHITE_BALANCE_SHADE);
        result.put(AWB_MODE_TWILIGHT, Camera.Parameters.WHITE_BALANCE_TWILIGHT);
        result.put(AWB_MODE_WARM_FLUORESCENT, Camera.Parameters.WHITE_BALANCE_WARM_FLUORESCENT);
        return Collections.unmodifiableMap(result);
    }

    public static final Map<Integer, Integer> AWB_MAP_21 = createAwbMap21();

    private static Map<Integer, Integer> createAwbMap21() {
        return Map.of(AWB_MODE_AUTO, CaptureRequest.CONTROL_AWB_MODE_AUTO, AWB_MODE_CLOUDY_DAYLIGHT, CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT, AWB_MODE_DAYLIGHT, CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT, AWB_MODE_FLUORESCENT, CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT, AWB_MODE_INCANDESCENT, CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT, AWB_MODE_OFF, CaptureRequest.CONTROL_AWB_MODE_OFF, AWB_MODE_SHADE, CaptureRequest.CONTROL_AWB_MODE_SHADE, AWB_MODE_TWILIGHT, CaptureRequest.CONTROL_AWB_MODE_TWILIGHT, AWB_MODE_WARM_FLUORESCENT, CaptureRequest.CONTROL_AWB_MODE_WARM_FLUORESCENT);
    }

    public static void readDefaultLensSetup(final Context context,
                                            final FocusMode lensSetup,
                                            final boolean useCamera2) {
        final int afMode = afMode();
        if (useCamera2) {
            switch (afMode) {
                case AF_MODE_INFINITY:
                    lensSetup.focusMode = CaptureRequest.CONTROL_AF_MODE_OFF;
                    lensSetup.focusDistance = 0.0f; // A value of 0.0f means infinity focus.
                    break;
                case AF_MODE_CONTINUOUS_VIDEO:
                default:
                    lensSetup.focusMode = CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO;
                    break;
            }
            lensSetup.antibandingMode = antiBandingMode21();
            lensSetup.videoStabilizationMode = videoStabilizationMode21();
            lensSetup.opticalStabilizationMode = opticalStabilizationMode21();
            lensSetup.noiseReductionMode = noiseReductionMode21();

            lensSetup.awbMode = awbMode21();
            final int factor = Integer.parseInt(context.getString(R.string.color_temperature_factor_default));
            lensSetup.colorCorrectionGains = computeTemperature(factor);

        } else {
            switch (afMode) {
                case AF_MODE_INFINITY:
                    lensSetup.focusMode16 = Camera.Parameters.FOCUS_MODE_INFINITY;
                    break;
                case AF_MODE_CONTINUOUS_VIDEO:
                default:
                    lensSetup.focusMode16 = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO;
                    break;
            }
            lensSetup.awbMode16 = awbMode16();
            lensSetup.antibandingMode16 = antiBandingMode16();
            lensSetup.videoStabilizationMode16 = videoStabilizationMode16();
        }
        lensSetup.exposureCompensation = exposureCompensation();
    }

    private static int afMode() {
        return FOCUS_MODE;
    }

    private static String antiBandingMode16() {
        return String.valueOf(ANTI_FLICKER);
    }

    private static int antiBandingMode21() {
        return ANTI_FLICKER;
    }

    private static String awbMode16() {
        return AWB_MAP_16.get(WHITE_BALANCE);
    }

    private static int awbMode21() {
        return CaptureRequest.CONTROL_AWB_MODE_AUTO;
    }

    private static int exposureCompensation() {
        return EXPOSURE_COMPENSATION;
    }

    private static int noiseReductionMode21() {
        return NOISE_REDUCTION;
    }

    private static int opticalStabilizationMode21() {
        return OPTICAL_IMAGE_STABILIZATION;
    }

    private static boolean videoStabilizationMode16() {
        return true;
    }

    private static int videoStabilizationMode21() {
        return ELECTRONIC_IMAGE_STABILIZATION;
    }

    // https://developer.android.com/reference/android/hardware/camera2/CaptureRequest#COLOR_CORRECTION_GAINS
    public static RggbChannelVector computeTemperature(final int factor) {
        return new RggbChannelVector(0.635f + (0.0208333f * factor),
                1.0f, 1.0f,
                3.7420394f + (-0.0287829f * factor));
    }

}
