package io.uslugi.streamer.settingsutils;

import static io.uslugi.streamer.helper.Constants.Config.Advanced.ALLOW_ALL_CAMERA_RESOLUTIONS;
import static io.uslugi.streamer.helper.Constants.Config.Advanced.CONVERT_BETWEEN_ANDROID_CAMERA_TIMESTAMP_AND_SYSTEM_TIME;
import static io.uslugi.streamer.helper.Constants.Config.Video.BITRATE_MODE;
import static io.uslugi.streamer.helper.Constants.Config.Video.KEYFRAME_FREQUENCY;

import android.content.Context;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;

import io.uslugi.libcommon.MediaCodecUtils;
import com.wmspanel.libstream.Streamer;
import com.wmspanel.libstream.VideoConfig;
import io.uslugi.streamer.data.Connection;
import io.uslugi.streamer.data.Connection_;
import io.uslugi.streamer.ObjectBox;
import io.uslugi.streamer.R;
import io.uslugi.streamer.helper.Constants;
import io.uslugi.streamer.log.EventLog;

public class VideoEncoderSettings {

    public static VideoConfig newVideoConfig(final Context context,
                                             final Streamer.Size videoSize) {
        final VideoConfig config = new VideoConfig();

        // This is for old Google Nexus 6P (2015) and Google Pixel (2016) devices only
        // Don't set this in your production app
        config.discardCameraTimestamp = CONVERT_BETWEEN_ANDROID_CAMERA_TIMESTAMP_AND_SYSTEM_TIME;

        // "video/avc" or "video/hevc"
        config.type = mimeType();

        if (verifyResolution()) {
            config.videoSize = MediaCodecUtils.verifyResolution(config.type, videoSize);
        } else {
            config.videoSize = videoSize;
        }

        // https://developer.android.com/reference/android/media/MediaFormat.html#KEY_FRAME_RATE
        config.fps = Constants.Config.Video.FPS;

        // https://developer.android.com/reference/android/media/MediaFormat.html#KEY_I_FRAME_INTERVAL
        config.keyFrameInterval = keyFrameInterval();

        // http://developer.android.com/reference/android/media/MediaFormat.html#KEY_PROFILE
        // http://developer.android.com/reference/android/media/MediaFormat.html#KEY_LEVEL
        config.profileLevel = profileLevel(context, config.type);

        // https://developer.android.com/reference/android/media/MediaFormat.html#KEY_BIT_RATE
        config.bitRate = Constants.Config.Video.BITRATE;

        // https://developer.android.com/reference/android/media/MediaFormat#KEY_BITRATE_MODE
        config.bitRateMode = bitRateMode();

        config.seiData = CONVERT_BETWEEN_ANDROID_CAMERA_TIMESTAMP_AND_SYSTEM_TIME ? VideoConfig.SeiDataType.TIME_INFO : VideoConfig.SeiDataType.NONE;

        return config;
    }

    public static int keyFrameInterval() {
        return KEYFRAME_FREQUENCY;
    }

    /**
     * Creates video profile and returns it
     * @param context - the [Context]
     * @param mimeType - the mime type
     * @return the MediaCodecInfo.CodecProfileLevel
     */
    public static MediaCodecInfo.CodecProfileLevel profileLevel(final Context context,
                                                                final String mimeType) {
        try {
            final int profile = Constants.Config.Video.PROFILE;
            final MediaCodecInfo info = MediaCodecUtils.selectCodec(mimeType);

            if (info != null) {
                final MediaCodecInfo.CodecCapabilities capabilities = info.getCapabilitiesForType(mimeType);
                for (MediaCodecInfo.CodecProfileLevel profileLevel : capabilities.profileLevels) {
                    if (profileLevel.profile == profile) {
                        return profileLevel;
                    }
                }
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            EventLog.getInstance().put(Streamer.LoggerListener.Severity.ERROR, context.getString(R.string.ERROR_CREATING_H264_PROFILE));
        }

        return null;
    }

    public static int bitRateMode() {
        return BITRATE_MODE;
    }

    public static String mimeType() {
        return MediaFormat.MIMETYPE_VIDEO_AVC;
    }

    public static boolean verifyResolution() {
        return !ALLOW_ALL_CAMERA_RESOLUTIONS;
    }

    public static VideoConfig newNdiPreviewVideoConfig(VideoConfig mainConfig) {
        VideoConfig lowestBandwidth = null;
        if (ObjectBox.get().boxFor(Connection.class).query()
                .equal(Connection_.active, true)
                .equal(Connection_.preview, true).build().count() > 0) {
            lowestBandwidth = new VideoConfig();
            lowestBandwidth.type = mainConfig.type;
            lowestBandwidth.videoSize = new Streamer.Size(640, 360);
            lowestBandwidth.fps = Math.min(mainConfig.fps, 30.0f);
            lowestBandwidth.bitRate = MediaFormat.MIMETYPE_VIDEO_HEVC.equals(mainConfig.type) ?
                    250_000 : 500_000;
            lowestBandwidth.bitRateMode = mainConfig.bitRateMode;
        }
        return lowestBandwidth;
    }
}
