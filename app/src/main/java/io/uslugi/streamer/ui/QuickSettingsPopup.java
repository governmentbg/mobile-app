package io.uslugi.streamer.ui;

import android.media.AudioDeviceInfo;
import androidx.annotation.NonNull;
import java.util.HashSet;

public class QuickSettingsPopup {
    public interface CameraOptionsListener {
        void onCameraOptionChange(@NonNull final String option, @NonNull final String value);
        void onOptionSetChange(@NonNull final String option, HashSet<Long> value);
        void onZoom(final float scaleFactor);
        void flashClick();
        void onFocus(final int focusMode, final float focusDistance);
        void onFlip(@NonNull final String cameraId, final String physicalCameraId);
        void onConcurrentCameraSelect(final String cameraId);
        void onGain(final float gainDb, final boolean save);
        void onSetPreferredAudioDevice(AudioDeviceInfo deviceInfo);
    }
}