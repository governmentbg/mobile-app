package io.uslugi.streamer.settingsutils;

import static io.uslugi.streamer.helper.Constants.Config.Advanced.CIRCULAR_BUFFER_DURATION;
import static io.uslugi.streamer.helper.Constants.Config.Advanced.HORIZON_STREAM_DEMO;
import static io.uslugi.streamer.helper.Constants.Config.Advanced.MIRROR_FRONT_CAMERA;
import static io.uslugi.streamer.helper.Constants.Config.Display.SHOW_AUDIO_LEVEL_METER;
import static io.uslugi.streamer.helper.Constants.Config.Display.SHOW_HORIZON_LEVEL;
import static io.uslugi.streamer.helper.Constants.Config.Overlays.SHOW_LAYERS_ON_PREVIEW;
import static io.uslugi.streamer.helper.Constants.Config.Overlays.STANDBY_LAYERS_ENABLED;
import static io.uslugi.streamer.helper.Constants.Config.Advanced.USE_CUSTOM_BUFFER_DURATION;
import static io.uslugi.streamer.helper.Constants.Config.Advanced.VOLUME_KEYS_ACTION;
import static io.uslugi.streamer.helper.Constants.Config.Video.LIVE_ROTATION;
import static io.uslugi.streamer.helper.Constants.Config.Video.VIDEO_ORIENTATION;

import com.wmspanel.libstream.AudioConfig;
import com.wmspanel.libstream.Streamer;
import com.wmspanel.libstream.VideoConfig;
import io.uslugi.streamer.helper.Constants;

public final class Settings {
    public static final int ADAPTIVE_BITRATE_OFF = 0;
    public static final int ADAPTIVE_BITRATE_LOG_DESC = 1;
    public static final int ADAPTIVE_BITRATE_LADDER_ASC = 2;
    public static final int ADAPTIVE_BITRATE_HYBRID = 3;

    public static final int ACTION_DO_NOTHING = 0;
    public static final int ACTION_START_STOP = 1;
    public static final int ACTION_ZOOM = 2;
    public static final int ACTION_FLIP = 3;

    // Video / Camera
    public static boolean liveRotation() {
        if (HORIZON_STREAM_DEMO) {
            return true;
        }
        return LIVE_ROTATION;
    }

    public static boolean lockOrientationOnStreamStart() {
        return false;
    }

    public static boolean verticalVideo() {
        if (VIDEO_ORIENTATION.equals("0")) return false;
        else return true;
    }

    // Video / Adaptive bitrate streaming
    public static int adaptiveBitrate() {
        return Constants.Config.Video.ADAPTIVE_BITRATE_STREAMING_MODE_VALUE;
    }

    /**
     * Returns whether the Adaptive frame rate is turned on
     * @return true
     */
    public static boolean adaptiveFps() {
        return Constants.Config.Video.ADAPTIVE_FRAME_RATE;
    }

    // Advanced options / Mirror front camera
    public static boolean picturesAsPreviewed() {
        return MIRROR_FRONT_CAMERA;
    }

    // Advanced options / Volume keys
    public static int volumeKeysAction() {
        return VOLUME_KEYS_ACTION;
    }

    // Advanced options / Encoder buffer size

    // buffer size is video frames per second + audio frames per second
    // average video frameRate is approximately 30fps
    // audio also is frame based (for AAC, it's 1024 samples frames),
    // so for 44.1 kHz, you can have audio duration only with a 23 ms granularity
    //
    // 1 second buffer will need 30 video frames + 42 audio frames
    //
    // at least 1 second buffer is mandatory for rtmp and rtsp
    public static int maxBufferItems(final AudioConfig audioConfig,
                                     final VideoConfig videoConfig) {
        if (!USE_CUSTOM_BUFFER_DURATION) {
            return Streamer.MAX_BUFFER_ITEMS;
        }

        double audioFramesPerSec = audioConfig != null ? audioConfig.sampleRate / 1024.0 : 0;
        double videoFramesPerSec = videoConfig != null ? videoConfig.fps : 0;

        double maxBufferItems = ((audioFramesPerSec + videoFramesPerSec) * CIRCULAR_BUFFER_DURATION) / 1000.0;
        if (maxBufferItems < Streamer.MIN_AV_BUFFER_ITEMS
                && audioConfig != null && videoConfig != null) {
            maxBufferItems = Streamer.MIN_AV_BUFFER_ITEMS; // audio+video need more room for interleaving compensation
        }

        return (int) Math.floor(maxBufferItems);
    }

    // Display
    public static boolean showAudioMeter() {
        return SHOW_AUDIO_LEVEL_METER;
    }

    public static boolean showInclinometer() {
        return SHOW_HORIZON_LEVEL;
    }

    // Overlays
    public static boolean showPreviewLayers() {
        return SHOW_LAYERS_ON_PREVIEW;
    }

    public static boolean standbyEnabled() {
        return STANDBY_LAYERS_ENABLED;
    }

    public static boolean fullScreenPreview() {
        return false;
    }
}
