package io.uslugi.streamer.ui;

import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.RggbChannelVector;

import com.wmspanel.libstream.FocusMode;

public class LensSetup {
    public boolean focusLocked;
    public boolean exposureLocked;
    public boolean wbLocked;
    public int awbFactor = 65;
    private final boolean isFocusLockSupported;
    private FocusMode savedSetup;

    LensSetup(boolean isFocusLockSupported, FocusMode savedSetup) {
        this.isFocusLockSupported = isFocusLockSupported;
        this.savedSetup = savedSetup;
    }

    public boolean hasActiveLock() {
        return focusLocked || exposureLocked || wbLocked;
    }

    public void save(FocusMode lensSetup) {
        if (lensSetup != null) {
            savedSetup = new FocusMode(lensSetup);
        }
    }

    public void unlock(FocusMode lensSetup) {
        if (lensSetup != null && savedSetup != null) {
            if (focusLocked) {
                lensSetup.focusMode = savedSetup.focusMode;
                lensSetup.focusDistance = savedSetup.focusDistance;
            }
            if (exposureLocked) {
                lensSetup.aeMode = savedSetup.aeMode;
                lensSetup.sensorExposureTime = savedSetup.sensorExposureTime;
                lensSetup.sensorFrameDuration = savedSetup.sensorFrameDuration;
                lensSetup.sensorSensitivity = savedSetup.sensorSensitivity;
            }
            if (wbLocked) {
                lensSetup.awbMode = savedSetup.awbMode;
                lensSetup.colorCorrectionGains = copyTemperature(savedSetup.colorCorrectionGains);
            }
        }
        focusLocked = false;
        exposureLocked = false;
        wbLocked = false;
    }

    public void lock(FocusMode lensSetup, TotalCaptureResult result) {
        final Integer focusState = result.get(CaptureResult.CONTROL_AF_STATE);
        final Float focusDistance = result.get(CaptureResult.LENS_FOCUS_DISTANCE);
        if (focusLocked && focusState != null && focusDistance != null) {
            lensSetup.focusMode = CaptureRequest.CONTROL_AF_MODE_OFF;
            lensSetup.focusDistance = focusDistance;
        }
        final Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
        final Long sensorExposureTime = result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
        final Integer sensorSensitivity = result.get(CaptureResult.SENSOR_SENSITIVITY);
        final Long sensorFrameDuration = result.get(CaptureResult.SENSOR_FRAME_DURATION);
        if (exposureLocked && aeState != null
                && sensorExposureTime != null && sensorFrameDuration != null && sensorSensitivity != null) {
            lensSetup.aeMode = CaptureRequest.CONTROL_AE_MODE_OFF;
            lensSetup.sensorExposureTime = sensorExposureTime;
            lensSetup.sensorSensitivity = sensorSensitivity;
            lensSetup.sensorFrameDuration = sensorFrameDuration;
        }
        final Integer awbState = result.get(CaptureResult.CONTROL_AWB_STATE);
        final RggbChannelVector colorCorrectionGains = result.get(CaptureResult.COLOR_CORRECTION_GAINS);
        if (wbLocked && awbState != null && colorCorrectionGains != null) {
            lensSetup.awbMode = CaptureRequest.CONTROL_AWB_MODE_OFF;
            lensSetup.colorCorrectionGains = copyTemperature(colorCorrectionGains);
        }
    }

    public boolean isFocusLockSupported() {
        return isFocusLockSupported;
    }

    public String makeFocusInfo() {
        StringBuilder builder = new StringBuilder();
        if (focusLocked) {
            builder.append("AFL ");
        }
        if (exposureLocked) {
            builder.append("AEL ");
        }
        if (wbLocked) {
            builder.append("WBL ");
        }
        return builder.toString().trim();
    }

    private RggbChannelVector copyTemperature(RggbChannelVector colorCorrectionGains) {
        return new RggbChannelVector(
                colorCorrectionGains.getRed(),
                colorCorrectionGains.getGreenEven(),
                colorCorrectionGains.getGreenOdd(),
                colorCorrectionGains.getBlue());
    }
}
