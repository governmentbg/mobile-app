package io.uslugi.streamer.settingsutils;

import static io.uslugi.streamer.helper.Constants.Config.Audio.AUDIO_ONLY_CAPTURE;
import static io.uslugi.streamer.helper.Constants.Config.Audio.MICROPHONE_DIRECTION;
import static io.uslugi.streamer.helper.Constants.Config.Audio.MICROPHONE_DIRECTION_FOLLOWS_CAMERA;
import static io.uslugi.streamer.helper.Constants.Config.Audio.SOURCE;
import static io.uslugi.streamer.helper.Constants.Config.Audio.USE_BLUETOOTH_MIC;

import android.annotation.TargetApi;
import android.media.AudioDeviceInfo;
import android.media.MicrophoneDirection;
import android.media.audiofx.AudioEffect;
import android.os.Build;

import io.uslugi.libcommon.CameraInfo;
import com.wmspanel.libstream.AudioConfig;
import io.uslugi.streamer.helper.Constants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AudioSettings {

    private static final List<UUID> AUDIO_EFFECTS = new ArrayList<>(Arrays.asList(
            AudioEffect.EFFECT_TYPE_AEC, AudioEffect.EFFECT_TYPE_AGC, AudioEffect.EFFECT_TYPE_NS));

    public static AudioConfig newAudioConfig() {
        final AudioConfig audioConfig = new AudioConfig();
        audioConfig.audioSource = source();
        audioConfig.bitRate = bitRate();
        audioConfig.channelCount = channelCount();
        audioConfig.sampleRate = sampleRate();
        audioConfig.gain = audioGain();
        audioConfig.preferredMicrophoneDirection = preferredMicrophoneDirection();

        return audioConfig;
    }

    private static int bitRate() {
        int bitRate;
        bitRate = AudioConfig.calcBitRate(
                sampleRate(), channelCount(), AudioConfig.AAC_PROFILE);

        // Log.d(TAG, "audio_bitrate=" + bitRate);

        return bitRate;
    }

    private static int source() {
        return SOURCE;
    }

    /**
     * Audio channel set to MONO
     * @return the value corresponding to MONO -> 1
     */
    public static int channelCount() {
        return Constants.Config.Audio.CHANNEL;
    }

    /**
     * Sets the Audio's sample rate to 44100 Hz
     * @return 44100 hz for the sample rate
     */
    private static int sampleRate() {
        return Constants.Config.Audio.SAMPLE_RATE;
    }

    public static boolean useBluetooth() {
        return USE_BLUETOOTH_MIC;
    }

    public static boolean radioMode() {
        return AUDIO_ONLY_CAPTURE;
    }

    /**
     * Sets Audio Gain to +10 dB
     * @return 10 dB as float
     */
    public static float audioGainDb() {
        return Constants.Config.Audio.GAIN;
    }

    public static float audioGain(float gainDb) {
        return (float) Math.pow(10.0, gainDb / 20.0);
    }

    public static float audioGain() {
        return audioGain(audioGainDb());
    }

    public static boolean isVOIPEffect(UUID type) {
        return AUDIO_EFFECTS.contains(type);
    }

    public static final Map<Integer, String> AUDIO_DEVICE_TYPE_23 = createAudioDeviceTypeMap23();

    private static Map<Integer, String> createAudioDeviceTypeMap23() {
        final Map<Integer, String> result = new HashMap<>();
        result.put(AudioDeviceInfo.TYPE_BUILTIN_MIC, "BUILTIN_MIC");
        result.put(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, "BLUETOOTH_SCO");
        result.put(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "BLUETOOTH_A2DP");
        result.put(AudioDeviceInfo.TYPE_WIRED_HEADSET, "WIRED_HEADSET");
        result.put(AudioDeviceInfo.TYPE_HDMI, "HDMI");
        result.put(AudioDeviceInfo.TYPE_TELEPHONY, "TELEPHONY");
        result.put(AudioDeviceInfo.TYPE_DOCK, "DOCK");
        result.put(AudioDeviceInfo.TYPE_USB_ACCESSORY, "USB_ACCESSORY");
        result.put(AudioDeviceInfo.TYPE_USB_DEVICE, "USB_DEVICE");
        result.put(AudioDeviceInfo.TYPE_USB_HEADSET, "USB_HEADSET");
        result.put(AudioDeviceInfo.TYPE_FM_TUNER, "FM_TUNER");
        result.put(AudioDeviceInfo.TYPE_TV_TUNER, "TV_TUNER");
        result.put(AudioDeviceInfo.TYPE_LINE_ANALOG, "LINE_ANALOG");
        result.put(AudioDeviceInfo.TYPE_LINE_DIGITAL, "LINE_DIGITAL");
        result.put(AudioDeviceInfo.TYPE_IP, "IP");
        result.put(AudioDeviceInfo.TYPE_BUS, "BUS");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            result.put(AudioDeviceInfo.TYPE_REMOTE_SUBMIX, "REMOTE_SUBMIX");
            result.put(AudioDeviceInfo.TYPE_BLE_HEADSET, "BLE_HEADSET");
        }
        result.put(AudioDeviceInfo.TYPE_HDMI_ARC, "HDMI_ARC");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            result.put(AudioDeviceInfo.TYPE_HDMI_EARC, "HDMI_EARC");
        }
        return Collections.unmodifiableMap(result);
    }

    @TargetApi(Build.VERSION_CODES.Q)
    public static boolean microphoneDirectionFollowsCamera() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false;
        }

        return MICROPHONE_DIRECTION_FOLLOWS_CAMERA;
    }

    @TargetApi(Build.VERSION_CODES.Q)
    private static int preferredMicrophoneDirection() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return -1;
        }

        return MICROPHONE_DIRECTION;
    }

    @TargetApi(Build.VERSION_CODES.Q)
    public static int preferredMicrophoneDirection(final CameraInfo activeCameraInfo) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return -1;
        }
        if (activeCameraInfo == null) {
            return MicrophoneDirection.MIC_DIRECTION_UNSPECIFIED;
        }
        if (activeCameraInfo.lensFacing == CameraInfo.LENS_FACING_FRONT) {
            return MicrophoneDirection.MIC_DIRECTION_TOWARDS_USER;
        } else {
            return MicrophoneDirection.MIC_DIRECTION_AWAY_FROM_USER;
        }
    }
}
