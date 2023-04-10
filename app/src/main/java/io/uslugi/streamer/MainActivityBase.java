package io.uslugi.streamer;

import static io.uslugi.streamer.helper.Constants.DelayTimes.RETRY_TIME_DELAY;
import static io.uslugi.streamer.helper.Constants.DelayTimes.START_RECORDING_TIME;
import static io.uslugi.streamer.helper.Constants.DelayTimes.STOP_RECORDING_TIME_TEST;
import static io.uslugi.streamer.log.EventLog.Logd;

import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioRecordingConfiguration;
import android.media.AudioRouting;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.wmspanel.libstream.SrtConfig;
import com.wmspanel.libstream.Streamer;

import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

import io.uslugi.libcommon.PlatformUtils;
import io.uslugi.libcommon.UriResult;
import io.uslugi.libcommon.sntp.SntpUpdater;
import io.uslugi.streamer.conditioner.StreamConditionerBase;
import io.uslugi.streamer.data.Connection;
import io.uslugi.streamer.data.Section;
import io.uslugi.streamer.data.SikSingleton;
import io.uslugi.streamer.helper.CommonHelper;
import io.uslugi.streamer.helper.Constants;
import io.uslugi.streamer.helper.Formatter;
import io.uslugi.streamer.helper.SharedPreferencesHelper;
import io.uslugi.streamer.settingsutils.AudioSettings;
import io.uslugi.streamer.settingsutils.ConnectivitySettings;
import io.uslugi.streamer.settingsutils.MediaFileSettings;
import io.uslugi.streamer.settingsutils.Settings;
import io.uslugi.streamer.ui.MainActivity;
import io.uslugi.streamer.ui.PowerIndication;

// Base class for Streamer activities
// Holds connection logic, preferences, UI and Activity state transition
public abstract class MainActivityBase extends ActivityCommons implements Streamer.Listener, SntpUpdater.Listener {
    private final String TAG = "MainActivityBase";

    protected Handler mHandler;
    protected Streamer mStreamer;
    protected boolean mBroadcastOn;
    protected int mRetryPending;

    private final Map<Integer, Connection> mConnectionId = new HashMap<>();
    private final Map<Integer, Streamer.ConnectionState> mConnectionState = new HashMap<>();
    private final Map<Long, String> mConnectionInfoMessages = new LinkedHashMap<>();

    protected Streamer.CaptureState mVideoCaptureState = Streamer.CaptureState.FAILED;
    protected Streamer.CaptureState mAudioCaptureState = Streamer.CaptureState.FAILED;

    private long mBroadcastStartTime;

    private Timer mCheckBatteryLevelTimer;
    private Timer mUpdateStatisticsTimer;

    protected boolean mIsMuted;
    private boolean mUseBluetooth;
    private AudioDeviceInfo mNextBluetoothDevice;

    private boolean mIsRecordOn;
    private int mRestartRecordInterval;

    protected ScaleGestureDetector mScaleGestureDetector;
    protected float mScaleFactor;

    protected StreamConditionerBase mConditioner;

    private PowerManager.OnThermalStatusChangedListener mThermalStatusListener;

    private final BroadcastReceiver mBatteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            onBatteryStatus();
        }
    };

    private final Runnable mCheckBatteryLevel = this::onBatteryStatus;

    protected SntpUpdater mNtpUpdater;

    private Section mSection;

    private MainActivityBaseViewModel viewModel;

    private final Runnable mSplitRecord = () -> {
        if (mStreamer != null) {
            if (!MediaFileSettings.splitRecord(MainActivityBase.this, mStreamer)) {
                stopRecord();
                maybeStopBroadcast();
            }
        }
    };

    protected final Runnable mUpdateStatistics = new Runnable() {
        @Override
        public void run() {
            if (mStreamer == null) {
                return;
            }

            final long curTime = System.currentTimeMillis();
            final long duration = (curTime - mBroadcastStartTime) / 1000L;
            binding.broadcastTime.setText(mFormatter.timeToString(duration));

            if (mConnectionId.keySet().isEmpty()) {
                return;
            }

            for (int id : mConnectionId.keySet()) {
                Streamer.ConnectionState state = mConnectionState.get(id);
                if (state == null) {
                    continue;
                }
            }
        }
    };

    protected class RetryRunnable implements Runnable {
        private final Connection connection;

        public RetryRunnable(Connection connection) {
            this.connection = connection;
        }

        @Override
        public void run() {
            if (mBroadcastOn) {
                mRetryPending--;
                if (isStreamerReady()) {
                    createConnection(connection);
                }
                maybeStopBroadcast();
            }
        }
    }

    protected BroadcastReceiver mBluetoothScoStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED.equals(intent.getAction())) {
                int state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1);
                //Logd(TAG, "Audio SCO state: " + state);
                switch (state) {
                    case AudioManager.SCO_AUDIO_STATE_CONNECTED:
                        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
                        if (mStreamer != null && am != null) {
                            showToast(getString(R.string.bluetooth_connected));
                            if (mNextBluetoothDevice == null) {
                                am.setMode(AudioManager.MODE_IN_COMMUNICATION);
                                if (Settings.showAudioMeter()) {
                                    mStreamer.startAudioCapture(mAudioCallback, mAudioRecordingStateListener);
                                } else {
                                    mStreamer.startAudioCapture(null, mAudioRecordingStateListener);
                                }
                            } else {
                                mStreamer.setPreferredDevice(mNextBluetoothDevice);
                                mNextBluetoothDevice = null;
                            }
                        }
                        break;
                    default:
                        break;
                }
            } else {
                int btState = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
                if (btState == BluetoothProfile.STATE_CONNECTED) {
                    startAudioCapture();
                }
            }
        }
    };

    /**
     * Demo of custom audio input processing in app code, implementing
     * {@link Streamer#setSilence(boolean)}.
     * <p>
     * Changing the volume of an audio signal must be done by applying a gain (multiplication)
     * and optionally clipping if your system has a limited dynamic range.
     * <p>
     * Audio callback runs on separate thread.
     */
    protected Streamer.AudioCallback mAudioCallback = new Streamer.AudioCallback() {
        /**
         * @param audioFormat {@link android.media.AudioFormat#ENCODING_PCM_16BIT}
         * @param data - the data
         * @param audioInputLength {@link android.media.AudioRecord#read(byte[], int, int)}
         * @param channelCount - the channel count
         * @param sampleRate - the sample rate
         * @param samplesPerFrame AAC frame size (1024 samples)
         */
        @Override
        public void onAudioDelivered(int audioFormat, byte[] data, int audioInputLength,
                                     int channelCount, int sampleRate, int samplesPerFrame) {

            binding.audioLevelMeter.putBuffer(data, channelCount, sampleRate);

            // If your app needs advanced audio processing (boost input volume, etc.), you can modify
            // raw pcm data before it goes to aac encoder.
            //Arrays.fill(data, (byte) 0); // "Mute" audio
        }
    };

    protected Streamer.AudioRecordingStateListener mAudioRecordingStateListener = new Streamer.AudioRecordingStateListener() {
        @Override
        public Handler getHandler() {
            return mHandler;
        }

        @Override
        public void onAudioRecordingConfigurationChanged(AudioRecordingConfiguration configuration) {
            Log.d(TAG, "onAudioRecordingConfigurationChanged");

        }

        @Override
        public void onRoutingChanged(AudioRouting router) {
            Log.d(TAG, "onRoutingChanged");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //Log.v(TAG, "onCreate()");

        viewModel = new ViewModelProvider(this).get(MainActivityBaseViewModel.class);

        // Observe is the user logged
        viewModel.isLoggedIn().observe(this, isSucceed -> {
            if (mBroadcastOn && isSucceed) {
                if (isStreamerReady()) {
                    Connection connection =
                            getConnection(SharedPreferencesHelper.INSTANCE.getRtmpUrl(getApplicationContext()));

                    // Try to reconnect
                    if (connection != null) {
                        mHandler.postDelayed(new RetryRunnable(connection), RETRY_TIME_DELAY);
                        mRetryPending++;
                    }
                }
            }
        });

        mHandler = new Handler(Looper.getMainLooper());
        mFormatter = new Formatter(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        //Log.v(TAG, "onStart(), orientation=" + getResources().getConfiguration().orientation);
        binding.audioLevelMeter.setChannels(AudioSettings.channelCount());
        binding.audioLevelMeter.setVisibility(Settings.showAudioMeter() ? View.VISIBLE : View.INVISIBLE);
        binding.previewAfl.setAspectRatio(-1.0);

    }

    @Override
    protected void onRestart() {
        super.onRestart();
        //Log.v(TAG, "onRestart(), orientation=" + getResources().getConfiguration().orientation);
    }

    @Override
    protected void onResume() {
        super.onResume();
        //Log.v(TAG, "onResume(), orientation=" + getResources().getConfiguration().orientation);

        mSection = getSection();
        automateStartTestRecording();
        mUpdateStatisticsTimer = new Timer();
        mUpdateStatisticsTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                mHandler.post(mUpdateStatistics);
            }
        }, 1000, 1000);

        startPowerMonitoring();

        if (AudioSettings.useBluetooth()) {
            registerForBluetooth();
        }

        mVolumeKeysAction = Settings.volumeKeysAction();

        binding.audioLevelMeter.startAnimating();
    }

    private void registerForBluetooth() {
        mUseBluetooth = true;
        IntentFilter filter = new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED);
        filter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        registerReceiver(mBluetoothScoStateReceiver, filter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        //Log.v(TAG, "onPause(), orientation=" + getResources().getConfiguration().orientation);

        binding.audioLevelMeter.pauseAnimating();

        releaseQuickSettings();

        stopRespondingToTouchEvents();

        // stop UI update
        if (mUpdateStatisticsTimer != null) {
            mUpdateStatisticsTimer.cancel();
            mUpdateStatisticsTimer = null;
        }

        stopPowerMonitoring();

        // Applications should release the camera immediately in onPause()
        // https://developer.android.com/guide/topics/media/camera.html#release-camera
        releaseStreamer();

        // Discard adaptive bitrate calculator
        mConditioner = null;

        if (mUseBluetooth) {
            try {
                AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
                if (am != null) {
                    am.setMode(AudioManager.MODE_NORMAL);
                    am.stopBluetoothSco();
                }
                unregisterReceiver(mBluetoothScoStateReceiver);
            } catch (Exception ignored) {
            }
        }
        mUseBluetooth = false;
        mNextBluetoothDevice = null;

        dismissDialog();
        dismissToast();

        if (mNtpUpdater != null) {
            mNtpUpdater.cancel();
        }

    }

    @Override
    protected void onStop() {
        super.onStop();
        //Log.v(TAG, "onStop(), orientation=" + getResources().getConfiguration().orientation);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.getViewModelStore().clear();
        //Log.v(TAG, "onDestroy(), orientation=" + getResources().getConfiguration().orientation);
        mHandler.removeCallbacksAndMessages(null);
    }

    // Applications should release the camera immediately in onPause()
    // https://developer.android.com/guide/topics/media/camera.html#release-camera
    protected void releaseStreamer() {
        // check if Streamer instance exists
        if (mStreamer == null) {
            return;
        }
        // stop broadcast
        releaseConnections();
        // stop mp4 recording
        mStreamer.stopRecord();
        // cancel audio and video capture
        mStreamer.stopAudioCapture();
        mStreamer.stopVideoCapture();
        // finally release streamer, after release(), the object is no longer available
        // if a Streamer is in released state, all methods will throw an IllegalStateException
        mStreamer.release();
        // sanitize Streamer object holder
        mStreamer = null;
    }

    protected boolean isStreamerReady() {
        if (mStreamer == null) {
            startRecording();

            return false;
        }

        // Larix always wants to get both video and audio capture running,
        // regardless of connection modes; But if you need only audio in your app
        // (you should only call to startAudioCapture() in this case),
        // then you can skip video state check
        final boolean isStreamerReady = isAudioCaptureStarted() && isVideoCaptureStarted();

        if (!isStreamerReady) {
            Logd(TAG, "AudioCaptureState=" + mAudioCaptureState);
            Logd(TAG, "VideoCaptureState=" + mVideoCaptureState);
            if (mUseBluetooth && !isAudioCaptureStarted()) {
                showToast(getString(R.string.no_bluetooth));
            } else {
                showToast(getString(R.string.please_wait));
            }
            startRecording();

            return false;
        }

        // In case of test QR code
        handleTestCases();

        return true;
    }

    protected boolean isAudioCaptureStarted() {
        return mAudioCaptureState == Streamer.CaptureState.STARTED;
    }

    protected boolean isVideoCaptureStarted() {
        return mVideoCaptureState == Streamer.CaptureState.STARTED;
    }

    protected boolean createConnections() {
        if (!isStreamerReady()) {
            return false;
        }

        String HOME_STREAM_URL = SharedPreferencesHelper.INSTANCE.getRtmpUrl(getApplicationContext());

        if (
                HOME_STREAM_URL.equals(Constants.StringPlaceholders.EMPTY) &&
                        mSection.getCurrentMode().equals(Constants.Mode.REAL)
        ) {
            // Try to get new RtmpUrl and make new connection
            viewModel.retryLogin(mSection, this);
        } else {

            if (BuildConfig.DEBUG) {
                HOME_STREAM_URL = BuildConfig.HOME_SERVER_URL;
            }

            Connection connection = getConnection(HOME_STREAM_URL);
            if (connection == null) {
                return false;
            }

            createConnection(connection);
        }

        mIsRecordOn = startRecord();

        if (mConnectionId.isEmpty() && !mIsRecordOn) {
            return false;
        }

        mBroadcastStartTime = System.currentTimeMillis();
        displayStatistics(true);

        if (!mConnectionId.isEmpty()) {
            if (mConditioner != null) {
                mConditioner.start(mStreamer);
            }
        } else {
            if (BuildConfig.DEBUG) {
                binding.recOnlyWarning.setText(R.string.recording_to_file_initial);
                binding.recOnlyWarning.setVisibility(View.VISIBLE);
            }
        }
        return true;
    }

    protected void createConnection(Connection connection) {
        final String scheme = Uri.parse(connection.url).getScheme();
        if (!UriResult.isSupported(scheme)) {
            return;
        }

        final int connectionId;
        try {
            if (UriResult.isSrt(scheme)) {
                // https://github.com/Haivision/srt/tree/master/docs

                final SrtConfig config = ConnectionHelper.toSrtConfig(connection);
                if (ConnectionHelper.isMaxbwLow(config)) {
                    config.maxbw = 0;
                    showToast(R.string.low_maxbw_warning);
                }
                connectionId = mStreamer.createConnection(config);

            } else if (UriResult.isRist(scheme)) {
                connectionId = mStreamer.createConnection(ConnectionHelper.toRistConfig(connection));

            } else {
                // Aliyun CDN supports private RTMP codec id for HEVC
                // There is a patch for FFMPEG to publish 265 over FLV:
                // https://github.com/CDN-Union/Code/tree/master/flv265-ChinaNetCenter
                if (UriResult.isRtmp(scheme) &&
                        MediaFormat.MIMETYPE_VIDEO_HEVC.equals(mStreamer.getVideoCodecType())) {
                    showToast(getString(R.string.hevc_over_rtmp_warning, connection.name));
                }

                connectionId = mStreamer.createConnection(ConnectionHelper.toConnectionConfig(connection));
            }
        } catch (URISyntaxException e) {
            return;
        }

        if (connectionId != -1) {
            mConnectionId.put(connectionId, connection);

            if (mConditioner != null) {
                mConditioner.addConnection(connectionId);
            }
        }
    }

    protected void releaseConnections() {
        mBroadcastOn = false;

        final Integer[] idList = new Integer[mConnectionId.size()];
        mConnectionId.keySet().toArray(idList);
        for (Integer id : idList) {
            releaseConnection(id);
        }
        mRetryPending = 0;

        binding.textCapture.setText(R.string.STREAMER_START_TEXT);

        // we don't plan to reconnect automatically, so stop stream recording
        stopRecord();

        if (mConditioner != null) {
            mConditioner.stop();
        }
        // don't keep mute state after restart
        mute(false);

        displayStatistics(false);
        mConnectionInfoMessages.clear();
    }

    protected void releaseConnection(int connectionId) {
        if (mStreamer != null && connectionId != -1) {
            mConnectionId.remove(connectionId);
            mConnectionState.remove(connectionId);
            mStreamer.releaseConnection(connectionId);
            if (mConditioner != null) {
                mConditioner.removeConnection(connectionId);
            }
        }
    }

    protected void displayStatistics(boolean show) {
        final int isVisible = show ? View.VISIBLE : View.INVISIBLE;

        binding.broadcastTime.setText(show ? "00:00:00" : "");
        binding.broadcastTime.setVisibility(isVisible);
    }

    @Override
    protected void broadcastClick() {
        if (mStreamer == null) {
            // preventing accidental touch issues
            return;
        }
        if (!mBroadcastOn) {
            if (createConnections()) {
                mBroadcastOn = true;
                binding.textCapture.setText(R.string.STREAMER_STOP_TEXT);
            }
        } else {
            releaseConnections();
        }
    }

    @Override
    public void flashClick() {
        if (mStreamer != null && mVideoCaptureState == Streamer.CaptureState.STARTED) {
            mStreamer.toggleTorch();
        }
    }

    protected void mute(boolean mute) {
        if (mStreamer == null) {
            return;
        }
        // How to mute audio:
        // Option 1 - stop audio capture and as result stop sending audio packets to server
        // Some players can stop playback if client keeps sending video, but sends no audio packets
        // Option 2 (workaround) - set PCM sound level to zero and encode
        // This produces silence in audio stream
        if (isAudioCaptureStarted()) {
            mIsMuted = mute;
            mStreamer.setSilence(mIsMuted);
        }
    }

    @Override
    public void onConnectionStateChanged(int connectionId, Streamer.ConnectionState state, Streamer.Status status, JSONObject info) {
        Logd(TAG, "onConnectionStateChanged, connectionId=" + connectionId + ", state=" + state + ", status=" + status);

        if (mStreamer == null) {
            return;
        }

        if (!mConnectionId.containsKey(connectionId)) {
            return;
        }

        mConnectionState.put(connectionId, state);

        switch (state) {
            case INITIALIZED:
            case SETUP:
            case CONNECTED:
                break;
            case RECORD:
                // Hide warning dialog
                binding.recOnlyWarning.setVisibility(View.INVISIBLE);
                break;
            case DISCONNECTED:
            default:
                if (BuildConfig.DEBUG) {
                    // Show warning dialog only in DEBUG
                    binding.recOnlyWarning.setText(R.string.recording_to_file_initial);
                    binding.recOnlyWarning.setVisibility(View.VISIBLE);
                }

                // save info for auto-retry and error message
                final Connection connection = mConnectionId.get(connectionId);
                // remove from active connections list
                releaseConnection(connectionId);

                if (status == Streamer.Status.UNKNOWN_FAIL && CommonHelper.INSTANCE.isOnline(this)) {
                    // Try to get new RtmpUrl and make new connection
                    viewModel.retryLogin(mSection, this);
                } else {
                    // Try with the current RtmpUrl
                    mHandler.postDelayed(new RetryRunnable(connection), RETRY_TIME_DELAY);
                    mRetryPending++;
                }

                // show error message including connection name
                final String msg = Formatter.getMessage(this, connection, status, info, (int) RETRY_TIME_DELAY);
                mConnectionInfoMessages.put(connection.id, msg);
                showToast(msg);

                // if there is nothing to retry, then stop broadcast
                maybeStopBroadcast();
                break;
        }
    }

    @Override
    public void onVideoCaptureStateChanged(Streamer.CaptureState state) {
        Logd(TAG, "onVideoCaptureStateChanged, state=" + state);

        mVideoCaptureState = state;

        switch (state) {
            case STARTED:
                // can start broadcasting video
                // mVideoCaptureState will be checked in createConnections()
                break;
            case STOPPED:
                // stop confirmation
                break;
            case ENCODER_FAIL:
            case FAILED:
            default:
                stopRespondingToTouchEvents();
                if (mStreamer != null) {
                    stopRecord();
                    mStreamer.stopVideoCapture();
                }
                showToast(state == Streamer.CaptureState.ENCODER_FAIL
                        ? getString(R.string.video_status_encoder_fail) : getString(R.string.video_status_fail));
                break;
        }
    }

    @Override
    public void onAudioCaptureStateChanged(Streamer.CaptureState state) {
        Logd(TAG, "onAudioCaptureStateChanged, state=" + state);

        mAudioCaptureState = state;

        switch (state) {
            case STARTED:
                // can start broadcasting audio
                // mAudioCaptureState will be checked in createConnection()
                break;
            case STOPPED:
                // stop confirmation
                break;
            case ENCODER_FAIL:
            case FAILED:
            default:
                if (mStreamer != null) {
                    stopRecord();
                    mStreamer.stopAudioCapture();
                }
                showToast(state == Streamer.CaptureState.ENCODER_FAIL
                        ? getString(R.string.audio_status_encoder_fail) : getString(R.string.audio_status_fail));
                break;
        }
    }

    @Override
    public void onRecordStateChanged(Streamer.RecordState state, Uri uri, Streamer.SaveMethod method) {
        Logd(TAG, "onRecordStateChanged, state=" + state);

        binding.recIndicator.setVisibility(state == Streamer.RecordState.STARTED ? View.VISIBLE : View.INVISIBLE);

        switch (state) {
            case STARTED:
                CommonHelper.INSTANCE.setUpBrightness(this, true);

                if (mRestartRecordInterval > 0) {
                    mHandler.postDelayed(mSplitRecord, mRestartRecordInterval);
                }
                break;
            case STOPPED:
                CommonHelper.INSTANCE.setUpBrightness(this, false);
                handleOnStopButtonClicked(uri, method);
                break;
            case FAILED:
                showToast(getString(R.string.err_record_failed));
                mIsRecordOn = false;
                maybeStopBroadcast();
                break;
            case INITIALIZED:
            default:
                break;
        }
    }

    @Override
    public void onSnapshotStateChanged(Streamer.RecordState state, Uri uri, Streamer.SaveMethod method) {
        if (state == Streamer.RecordState.STOPPED) {
            onSaveFinished(uri, method);
        }
    }

    private void onSaveFinished(Uri uri, Streamer.SaveMethod method) {
        final String displayName = MediaFileSettings.onSaveFinished(this, uri, method, (p, u) -> {
            new Handler(Looper.getMainLooper()).post(() -> {
                if (u != null) {
                    showToast(getString(R.string.saved_to, p));
                }
            });
        });
        if (displayName != null && !displayName.isEmpty()) {
            showToast(getString(R.string.saved_to, displayName));
        }
    }

    @Override
    public Handler getHandler() {
        return mHandler;
    }

    boolean doubleBackToExitPressedOnce = false;

    @Override
    public void onBackPressed() {
        //Log.v(TAG, "onBackPressed");
        if (doubleBackToExitPressedOnce || isFinishing()) {
            super.onBackPressed();
            return;
        }

        this.doubleBackToExitPressedOnce = true;
        Toast.makeText(this, R.string.press_again_to_quit, Toast.LENGTH_SHORT).show();

        new Handler(Looper.getMainLooper()).postDelayed(() -> doubleBackToExitPressedOnce = false, 2000);
    }

    protected void startAudioCapture() {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (mStreamer == null || am == null) {
            return;
        }
        if (mUseBluetooth) {
            try {
                // On Lollipop 5.0, the method throws an exception if there is no Bluetooth mic connected.
                // This behavior does not happen on earlier versions of Android.
                // https://issuetracker.google.com/issues/37029016
                am.startBluetoothSco();
            } catch (Exception ignored) {
            }
            // Wait for mBluetoothScoStateReceiver -> AudioManager.SCO_AUDIO_STATE_CONNECTED
        } else {
            if (Settings.showAudioMeter()) {
                // Pass Streamer.AudioCallback instance to access raw pcm audio and calculate audio level
                mStreamer.startAudioCapture(mAudioCallback, mAudioRecordingStateListener);
            } else {
                // If your app doesn't need it, then you can bypass Streamer.AudioCallback implementation
                mStreamer.startAudioCapture(null, mAudioRecordingStateListener);
            }
        }
    }

    protected void startVideoCapture() {
        if (mStreamer == null) {
            return;
        }
        mStreamer.startVideoCapture();
        if (mNtpUpdater != null && mNtpUpdater.isActive()) {
            mStreamer.setPreciseTime(mNtpUpdater.getTime());
        }
    }

    protected void stopRespondingToTouchEvents() {
    }

    private boolean startRecord() {
        stopRecord();
        final boolean result = MediaFileSettings.startRecord(this, mStreamer);
        if (result) {
            mRestartRecordInterval = MediaFileSettings.recordingIntervalMillis();
        }
        return result;
    }

    private void stopRecord() {
        mIsRecordOn = false;

        mHandler.removeCallbacks(mSplitRecord);
        mRestartRecordInterval = 0;

        binding.recIndicator.setVisibility(View.INVISIBLE);
        binding.recOnlyWarning.setVisibility(View.INVISIBLE);
        if (mStreamer != null) {
            mStreamer.stopRecord();
        }
    }

    private void maybeStopBroadcast() {
        if (mConnectionId.isEmpty() && mRetryPending == 0) {
            if (mIsRecordOn) {
                if (BuildConfig.DEBUG) {
                    // Show only in DEBUG
                    binding.recOnlyWarning.setText(R.string.recording_to_file);
                    binding.recOnlyWarning.setVisibility(View.VISIBLE);
                }
            } else {
                // no active connections, no recording, no pending retries -> stop session
                releaseConnections();
            }
        }
    }

    @Override
    protected void setGain(float gain) {
        if (mStreamer != null) {
            mStreamer.setGain(gain);
        }
    }

    @Override
    protected void setPreferredAudioDevice(AudioDeviceInfo deviceInfo) {
        if (mStreamer == null || !isAudioCaptureStarted()) {
            return;
        }
        if (deviceInfo.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                || deviceInfo.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                || deviceInfo.getType() == AudioDeviceInfo.TYPE_BLE_HEADSET) {
            if (!mUseBluetooth) {
                AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
                if (am == null) {
                    return;
                }
                registerForBluetooth();
                try {
                    mNextBluetoothDevice = deviceInfo;
                    am.startBluetoothSco();
                } catch (Exception ignored) {
                }
                return;
            }
        }
        mStreamer.setPreferredDevice(deviceInfo);
    }

    private void startPowerMonitoring() {
        mCheckBatteryLevelTimer = new Timer();
        mCheckBatteryLevelTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                mHandler.post(mCheckBatteryLevel);
            }
        }, 500, 60_000);

        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_POWER_CONNECTED);
        intentFilter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        registerReceiver(mBatteryReceiver, intentFilter);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mThermalStatusListener = status -> PowerIndication.updateThermalStatus(
                    status, binding.thermometer);
            final PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null) {
                pm.addThermalStatusListener(mThermalStatusListener);
            }
        }
    }

    private void stopPowerMonitoring() {
        if (mCheckBatteryLevelTimer != null) {
            mCheckBatteryLevelTimer.cancel();
            mCheckBatteryLevelTimer = null;
        }
        mHandler.removeCallbacks(mCheckBatteryLevel);
        unregisterReceiver(mBatteryReceiver);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            final PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && mThermalStatusListener != null) {
                pm.removeThermalStatusListener(mThermalStatusListener);
                mThermalStatusListener = null;
            }
        }
    }

    private void onBatteryStatus() {
        final Intent status = PlatformUtils.getBatteryStatus(this);
        if (status != null) {
            if (PlatformUtils.isBatteryLevelCritical(status)) {
                stopRecord();
                maybeStopBroadcast();
            }
            PowerIndication.updateBattery(status, binding.batteryView);
        }
    }

    /**
     * The method handle the different cases according the section's mode
     */
    private void handleOnStopButtonClicked(Uri uri, Streamer.SaveMethod method) {
        switch (getSection().getCurrentMode()) {
            case Constants.Mode.TEST_SETUP:
            case Constants.Mode.TEST_SIK:
                onSaveFinished(uri, method);
                navigateToMainActivity(true);
                break;
            case Constants.Mode.REAL:
            case Constants.Mode.UNKNOWN:
                onSaveFinished(uri, method);
                navigateToMainActivity(false);
                break;
            default:
                break;
        }
    }

    /**
     * The method navigate to MainActivity(LoginFragment or TestResultFragment).
     */
    private void navigateToMainActivity(Boolean isFromTest) {
        // Reset RtmpUrl
        SharedPreferencesHelper.INSTANCE.storeRtmpUrl(Constants.StringPlaceholders.EMPTY, this);
        // Create the Intent object of MainActivityBase to MainActivity
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        // add putExtra in case of isFromTest is true to notify Main activity, that should
        // show a test result screen. If isFromTest is false navigate to the LoginScreen
        if (isFromTest) {
            intent.putExtra(Constants.Extras.IS_TEST, true);
        }
        // start the Intent
        startActivity(intent);
    }

    @Override
    public void onNtpUpdate(Date trueTime) {
        if (mStreamer != null) {
            mStreamer.setPreciseTime(trueTime);
        }
    }

    /**
     * Automate start recording. Start recording in two seconds and stop in ten(in test cases).
     */
    private void automateStartTestRecording() {
        startRecording();

        if (
                Objects.equals(mSection.getCurrentMode(), Constants.Mode.TEST_SETUP) ||
                        Objects.equals(mSection.getCurrentMode(), Constants.Mode.TEST_SIK)
        ) {
            // Hide Capture button
            binding.textCapture.setVisibility(View.INVISIBLE);
        }
    }

    /**
     * Start recording with 5 seconds delay
     */
    private void startRecording() {
        mHandler.postDelayed(() -> {
            // Start recording in five seconds
            binding.textCapture.performClick();
        }, START_RECORDING_TIME);
    }

    /**
     * Handle QR code test scenarios
     */
    private void handleTestCases() {
        if (
                Objects.equals(mSection.getCurrentMode(), Constants.Mode.TEST_SETUP) ||
                        Objects.equals(mSection.getCurrentMode(), Constants.Mode.TEST_SIK)
        ) {
            mHandler.postDelayed(() -> {
                // Stop recording in ten seconds
                binding.textCapture.callOnClick();
            }, STOP_RECORDING_TIME_TEST);
        } else {
            // Show Capture button
            binding.textCapture.setVisibility(View.VISIBLE);
        }
    }

    @NonNull
    private Section getSection() {
        return SikSingleton.INSTANCE.getCurrentSection();
    }

    @Nullable
    // Create a new connection in case of problems with the current Rtmpl url
    private Connection getConnection(String HOME_STREAM_URL) {
        Connection connection = ConnectionHelper.newConnection(Constants.StringPlaceholders.EMPTY, HOME_STREAM_URL);

        if (!MediaFileSettings.record(this)) {
            if (!ConnectivitySettings.isConnected(this)) {
                showToast(R.string.not_connected);
                return null;
            }
        }

        mConnectionInfoMessages.put(connection.id, getString(R.string.connection_status, connection.name, getString(R.string.connecting)));

        return connection;
    }
}
