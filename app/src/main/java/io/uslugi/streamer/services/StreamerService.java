package io.uslugi.streamer.services;

import static io.uslugi.streamer.log.EventLog.Logd;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioRecordingConfiguration;
import android.media.AudioRouting;
import android.media.MediaFormat;
import android.media.MicrophoneInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.text.SpannableString;
import android.util.Log;
import android.view.Surface;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.text.HtmlCompat;

import io.uslugi.libcommon.PlatformUtils;
import io.uslugi.libcommon.UriResult;
import io.uslugi.libcommon.sntp.SntpUpdater;
import com.wmspanel.libstream.ConnectionConfig;
import com.wmspanel.libstream.RistConfig;
import com.wmspanel.libstream.SrtConfig;
import com.wmspanel.libstream.Streamer;
import com.wmspanel.libstream.StreamerGL;

import io.uslugi.streamer.ConnectionHelper;
import io.uslugi.streamer.helper.Formatter;
import io.uslugi.streamer.R;
import io.uslugi.streamer.conditioner.StreamConditionerBase;
import io.uslugi.streamer.data.Connection;
import io.uslugi.streamer.log.EventLog;
import io.uslugi.streamer.overlay.OverlayManager;
import io.uslugi.streamer.settingsutils.ConnectivitySettings;
import io.uslugi.streamer.settingsutils.MediaFileSettings;
import io.uslugi.streamer.settingsutils.Settings;

import org.json.JSONObject;

import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;

public class StreamerService extends Service implements Streamer.Listener, SensorEventListener, SntpUpdater.Listener {

    private static final String TAG = "StreamerService";

    private StreamerGL mStreamer;
    protected boolean mBroadcastOn;

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private Streamer.CaptureState mVideoCaptureState = Streamer.CaptureState.FAILED;
    private Streamer.CaptureState mAudioCaptureState = Streamer.CaptureState.FAILED;

    private final IBinder mBinder = new LocalBinder();
    // list of active connections
    private final Map<Integer, Connection> mConnectionId = new HashMap<>();

    private int mRetryPending;

    private Timer mCheckBatteryLevelTimer;
    private Timer mUpdateStatisticsTimer;

    private StreamConditionerBase mConditioner;

    private static final String CHANNEL_ID = "com.uslugi.streamer.channel.foreground_service";
    private static final int NOTIFY_ID = 101;
    private NotificationCompat.Builder mBuilder;
    private NotificationManagerCompat mNotificationManager;

    private static final String STOP_BROADCAST_ACTION_ID = "com.uslugi.streamer.action.stop_broadcast";
    private static final String MUTE_ACTION_ID = "com.uslugi.streamer.action.mute";
    private static final String PAUSE_ACTION_ID = "com.uslugi.streamer.action.pause";
    private static final String EXIT_ACTION_ID = "com.uslugi.streamer.action.exit";

    private Listener mListener;
    private Listener mPostmortalListener;
    private boolean mUseBluetooth;
    private AudioDeviceInfo mNextBluetoothDevice;

    NotificationCompat.Action mStopAction;
    NotificationCompat.Action mPauseAction;
    NotificationCompat.Action mExitAction;

    private boolean mIsRecordOn;
    private int mRestartRecordInterval;

    protected boolean mLiveRotation;
    private String mPausedCameraId;

    private int mRotation = 0;
    private int mOrientation = 0;

    private final int mNormalOrientation = StreamerGL.Orientations.PORTRAIT;
    private long mLastOrientationReportTime = 0;
    private long mLastOrientationChangeTime = 0;

    private AudioRecordingConfiguration mAudioRecordingConfiguration;

    private OverlayManager mOverlayManager;

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        long now = System.nanoTime();
        // 0.1s
        long ORIENTATION_CHECK_PERIOD_NS = 100_000_000;
        if (now - mLastOrientationReportTime < ORIENTATION_CHECK_PERIOD_NS) {
            return;
        }
        mLastOrientationReportTime = now;
        if (sensorEvent.sensor.getType() == Sensor.TYPE_GAME_ROTATION_VECTOR && sensorEvent.values.length >= 4) {

            float[] rotationMatrix = new float[16];
            float[] orientation = new float[3];
            SensorManager.getRotationMatrixFromVector(rotationMatrix, sensorEvent.values);
            SensorManager.remapCoordinateSystem(rotationMatrix,
                    SensorManager.AXIS_X, SensorManager.AXIS_Z,
                    rotationMatrix);
            SensorManager.getOrientation(rotationMatrix, orientation);
            //double a = orientation[0] / Math.PI * 180.0; //Rotation around vertical axis
            double b = orientation[1] / Math.PI * 180.0; //Rotation around horizontal axis (0 - upright, 90 - face up, -90 - face down)
            double c = 180 - orientation[2] / Math.PI * 180.0; //Rotation perpendicular to screen (0 - horizontal, 90 - counter-clockwise, 270 - clockwise)
//            Log.i(TAG, "Sensor changed: a=" + a  + " b=" + b + " c=" + c);
            if (b > -30 && b < 60) { // Consider as stand front orientation
                int rotation = getRotation(c);
                if (rotation >= 0 && rotation != mRotation) {
                    long dt = now - mLastOrientationChangeTime;
                    // 0.5s
                    long ORIENTATION_CHANGE_PERIOD_NS = 500_000_000;
                    if (dt > ORIENTATION_CHANGE_PERIOD_NS) {
                        setRotation(rotation);
                        mLastOrientationChangeTime = now;
                    }
                }
            }
        }

    }

    int getRotation(double angle) {
        if (angle > 330 || angle < 30) {
            return Surface.ROTATION_180;
        } else if (angle > 60 && angle < 120) {
            return Surface.ROTATION_270;
        } else if (angle > 150 && angle < 210) {
            return Surface.ROTATION_0;
        } else if (angle > 240 && angle < 300) {
            return Surface.ROTATION_90;
        }
        return -1;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    interface Listener {
        Handler getHandler();

        void updateStreamerState();

        void onConnectionError(String errorText);

        void onSave(String path);

        void onAudioDelivered(byte[] data, int channelCount, int sampleRate);

        void onScoStatusChanged(int status);

        void serviceDied();

        void stopForeground();

        void updatePreviewRatio(Streamer.Size size);

        void onAudioRecordingConfigurationChanged(AudioRecordingConfiguration configuration);

        void setActiveMicrophonesInfo(List<MicrophoneInfo> activeMicrophones);

        void updateBattery(Intent intent);
    }

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    static class LocalBinder extends Binder {}

    private PowerManager.OnThermalStatusChangedListener mThermalStatusListener;

    private BroadcastReceiver mBatteryReceiver;

    private final Runnable mCheckBatteryLevel = this::onBatteryStatus;

    private final Runnable mStopRecord = () -> {
        if (mStreamer != null) {
            mStreamer.stopRecord();
        }
    };

    private final Runnable mSplitRecord = () -> {
        if (mStreamer == null) {
            return;
        }
        if (!MediaFileSettings.splitRecord(StreamerService.this, mStreamer)) {
            stopRecord();
            maybeStopBroadcast();
        }
    };

    protected Streamer.AudioCallback mAudioCallback = new Streamer.AudioCallback() {
        @Override
        public void onAudioDelivered(int audioFormat, byte[] data, int audioInputLength,
                                     int channelCount, int sampleRate, int samplesPerFrame) {
            if (mListener != null) {
                mListener.onAudioDelivered(data, channelCount, sampleRate);
            }
        }
    };

    private class RetryRunnable implements Runnable {
        private final Connection connection;

        RetryRunnable(Connection connection) {
            this.connection = connection;
        }

        @Override
        public void run() {
            if (mBroadcastOn && mRetryPending > 0) {
                mRetryPending--;
                if (isStreamerReady()) {
                    createConnection(connection);
                }
                maybeStopBroadcast();
            }
        }
    }

    @Override
    public void onConnectionStateChanged(final int connectionId,
                                         final Streamer.ConnectionState state,
                                         final Streamer.Status status,
                                         final JSONObject info) {
        Logd(TAG, String.format(Locale.US, "onConnectionStateChanged, id=%1$d state=%2$s",
                connectionId, state.toString()));

        if (!mConnectionId.containsKey(connectionId)) {
            // ignore already released connection
            return;
        }

        switch (state) {
            case INITIALIZED:
            case CONNECTED:
            case SETUP:
            case RECORD:
                break;
            case DISCONNECTED:
            default:
                // save info for auto-retry
                final Connection connection = mConnectionId.get(connectionId);
                // remove from active connections list
                releaseConnection(connectionId);

                final int retryTimeout = ConnectivitySettings.retryTimeoutMs(this);

                String errorText = Formatter.getMessage(this, connection, status, info, retryTimeout);

                if (!errorText.isEmpty()) {
                    if (mListener != null) {
                        mListener.onConnectionError(errorText);
                    }
                }

                // do not try to reconnect in case of wrong credentials
                if (status != Streamer.Status.AUTH_FAIL && retryTimeout > 0) {
                    mHandler.postDelayed(new RetryRunnable(connection), retryTimeout);
                    mRetryPending++;
                }
                maybeStopBroadcast();
                break;
        }
    }

    @Override
    public void onVideoCaptureStateChanged(final Streamer.CaptureState state) {
        Logd(TAG, "onVideoCaptureStateChanged, state=" + state);
        mVideoCaptureState = state;
        switch (state) {
            case STARTED:
                if (Settings.picturesAsPreviewed()) {
                    // default values: preview -> false, stream -> true
                    if (mStreamer != null) {
                        mStreamer.setFrontMirror(false, false);
                    }
                }
                break;
            case STOPPED:
                break;
            case FAILED:
            case ENCODER_FAIL:
            default:
                releaseStreamer(false);
                final SpannableString formattedBody = formattedBody(state == Streamer.CaptureState.ENCODER_FAIL
                        ? getString(R.string.video_status_encoder_fail) : getString(R.string.video_status_fail));
                mBuilder.setWhen(System.currentTimeMillis())
                        .setSmallIcon(R.drawable.ic_stat_qwe)
                        .setLargeIcon(null)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(formattedBody))
                        .setContentText(formattedBody);
                mNotificationManager.notify(NOTIFY_ID, mBuilder.build());
                break;
        }
    }

    @Override
    public void onAudioCaptureStateChanged(final Streamer.CaptureState state) {
        Logd(TAG, "onAudioCaptureStateChanged, state=" + state);
        mAudioCaptureState = state;
        switch (state) {
            case STARTED:
            case STOPPED:
                break;
            case FAILED:
            case ENCODER_FAIL:
            default:
                releaseStreamer(false);
                final SpannableString formattedBody = formattedBody(state == Streamer.CaptureState.ENCODER_FAIL
                        ? getString(R.string.audio_status_encoder_fail) : getString(R.string.audio_status_fail));
                mBuilder.setWhen(System.currentTimeMillis())
                        .setSmallIcon(R.drawable.ic_stat_qwe)
                        .setLargeIcon(null)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(formattedBody))
                        .setContentText(formattedBody);
                mNotificationManager.notify(NOTIFY_ID, mBuilder.build());
                break;
        }
    }

    @Override
    public void onRecordStateChanged(Streamer.RecordState state, Uri uri, Streamer.SaveMethod method) {
        Logd(TAG, "onRecordStateChanged, state=" + state);
        switch (state) {
            case STARTED:
                if (mRestartRecordInterval > 0) {
                    mHandler.postDelayed(mSplitRecord, mRestartRecordInterval);
                }
                break;
            case STOPPED:
                onSaveFinished(uri, method);
                if (mRestartRecordInterval > 0) {
                    MediaFileSettings.startRecord(this, mStreamer, Streamer.Mode.AUDIO_ONLY);
                }
                break;
            case FAILED:
                mIsRecordOn = false;
                maybeStopBroadcast();
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
                if (u != null && mListener != null) {
                    mListener.onSave(p);
                }
            });
        });
        if (displayName != null && !displayName.isEmpty() && mListener != null) {
            mListener.onSave(displayName);
        }
    }

    @Override
    public Handler getHandler() {
        return mHandler;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        //Log.v(TAG, "onCreate");

        mNotificationManager = NotificationManagerCompat.from(this);

        createNotificationChannel();
        mBuilder = new NotificationCompat.Builder(this, CHANNEL_ID);
        mBuilder.setCategory(NotificationCompat.CATEGORY_SERVICE);

        final int flags = PendingIntent.FLAG_IMMUTABLE;

        // "Start/Stop broadcast" button
        final Intent startIntent = new Intent(getApplicationContext(), StreamerService.class);
        startIntent.setAction(STOP_BROADCAST_ACTION_ID);
        final PendingIntent stopPendingIntent = PendingIntent.getService(getApplicationContext(), 0, startIntent, flags);

        mStopAction = new NotificationCompat.Action(
                android.R.drawable.ic_media_play,
                getString(R.string.action_title_start),
                stopPendingIntent);

        final Intent muteIntent = new Intent(getApplicationContext(), StreamerService.class);
        muteIntent.setAction(MUTE_ACTION_ID);

        final Intent pauseIntent = new Intent(getApplicationContext(), StreamerService.class);
        pauseIntent.setAction(PAUSE_ACTION_ID);
        final PendingIntent pausePendingIntent = PendingIntent.getService(getApplicationContext(), 0, pauseIntent, flags);

        mPauseAction = new NotificationCompat.Action(
                android.R.drawable.ic_media_pause,
                getString(R.string.action_title_pause),
                pausePendingIntent);


        final Intent exitIntent = new Intent(getApplicationContext(), StreamerService.class);
        exitIntent.setAction(EXIT_ACTION_ID);
        final PendingIntent exitPendingIntent = PendingIntent.getService(getApplicationContext(), 0, exitIntent, flags);

        mExitAction = new NotificationCompat.Action(
                android.R.drawable.ic_delete,
                getString(R.string.action_title_exit),
                exitPendingIntent);

        final SpannableString formattedBody = formattedBody(getString(R.string.notification_idle));
        mBuilder.setWhen(System.currentTimeMillis())
                .setAutoCancel(false)
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_stat_qwe)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(formattedBody))
                .setContentText(formattedBody);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //Log.v(TAG, "onStartCommand: " + startId);
        if (intent != null) {
            String action = intent.getAction();
            if (STOP_BROADCAST_ACTION_ID.equals(action)) {
                if (mBroadcastOn) {
                    stopBroadcast();
                    cancelPause();
                } else {
                    startBroadcast();
                }
            } else if (MUTE_ACTION_ID.equals(action)) {
                if (mListener != null) {
                    mListener.updateStreamerState();
                }
            } else if (PAUSE_ACTION_ID.equals(action)) {
                togglePause();
                if (mListener != null) {
                    mListener.updateStreamerState();
                }
            } else if (EXIT_ACTION_ID.equals(action)) {
                if (mListener != null) {
                    mListener.stopForeground();
                }

                stopForeground(true);
                releaseStreamer(true);
            }

            // Logd(TAG, intent.getAction());
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.i(TAG, "onTaskRemoved");
        stop();
    }

    public void stop() {
        stopForeground(true);
        releaseStreamer(true);
    }


    @Override
    public void onDestroy() {
        //Log.v(TAG, "onDestroy");

        releaseStreamer(true);
        if (mPostmortalListener != null) {
            mPostmortalListener.serviceDied();
            mPostmortalListener = null;
        }
        mListener = null;
        super.onDestroy();
    }

    @Override
    public void onTrimMemory(int level) {
        // Determine which lifecycle or system event was raised.
        switch (level) {
            case ComponentCallbacks2.TRIM_MEMORY_MODERATE:
                stopRecord();
                break;
            case ComponentCallbacks2.TRIM_MEMORY_COMPLETE:
                releaseStreamer(true);
                break;
            case ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN:
            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE:
            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW:
            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL:
            case ComponentCallbacks2.TRIM_MEMORY_BACKGROUND:
            default:
                break;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private void createNotificationChannel() {
        final int importance = android.app.NotificationManager.IMPORTANCE_LOW;
        final NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                importance);
        channel.setDescription(getString(R.string.channel_description));
        channel.setShowBadge(false);
        mNotificationManager.createNotificationChannel(channel);
    }

    private void createConnection(Connection connection) {
        final String scheme = Uri.parse(connection.url).getScheme();
        if (!UriResult.isSupported(scheme)) {
            return;
        }

        final int connectionId;
        try {
            if (UriResult.isSrt(scheme)) {
                final SrtConfig config = ConnectionHelper.toSrtConfig(connection);
                if (ConnectionHelper.isMaxbwLow(config)) {
                    config.maxbw = 0;
                    if (mListener != null) {
                        mListener.onConnectionError(getString(R.string.low_maxbw_warning));
                    }
                }
                connectionId = mStreamer.createConnection(config);
            } else if (UriResult.isRist(scheme)) {
                final RistConfig config = ConnectionHelper.toRistConfig(connection);
                connectionId = mStreamer.createConnection(config);
            } else {
                final ConnectionConfig config = ConnectionHelper.toConnectionConfig(connection);
                if (UriResult.isRtmp(scheme) &&
                        MediaFormat.MIMETYPE_VIDEO_HEVC.equals(mStreamer.getVideoCodecType())) {
                    if (mListener != null) {
                        mListener.onConnectionError(getString(R.string.hevc_over_rtmp_warning, connection.name));
                    }
                }
                connectionId = mStreamer.createConnection(config);
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
        //Logd(TAG, String.format(Locale.US, "createConnection, id=%1$d", connectionId));
    }

    private void releaseConnections() {
        // cancel auto-retry attempts
        mRetryPending = 0;
        // release active connections
        final Integer[] idList = new Integer[mConnectionId.size()];
        mConnectionId.keySet().toArray(idList);
        for (Integer id : idList) {
            releaseConnection(id);
        }
        if (mConditioner != null) {
            mConditioner.stop();
        }
        // clear data
        mConnectionId.clear();
        // remove ongoing notification
        // update activity UI if app is in focus
        notifyClient();
    }

    private void releaseConnection(int connectionId) {
        if (mStreamer == null || connectionId == -1) {
            return;
        }
        mConnectionId.remove(connectionId);
        mStreamer.releaseConnection(connectionId);
        if (mConditioner != null) {
            mConditioner.removeConnection(connectionId);
        }
        //Logd(TAG, String.format(Locale.US, "releaseConnection, id=%1$d", connectionId));
    }

    private boolean startRecord() {
        stopRecord();
        mRestartRecordInterval = MediaFileSettings.recordingIntervalMillis();
        final Streamer.Mode mode =
                Streamer.Mode.AUDIO_VIDEO;
        return MediaFileSettings.startRecord(this, mStreamer, mode);
    }

    private void stopRecord() {
        mIsRecordOn = false;
        mHandler.removeCallbacks(mStopRecord);
        mHandler.removeCallbacks(mSplitRecord);
        mRestartRecordInterval = 0;
        if (mStreamer != null) {
            mStreamer.stopRecord();
        }
    }

    protected void startAudioCapture() {
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
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
                mStreamer.startAudioCapture(mAudioCallback, mAudioRecordingStateListener);
            } else {
                mStreamer.startAudioCapture(null, mAudioRecordingStateListener);
            }
        }
    }

    @SuppressLint("RestrictedApi")
    private void releaseStreamer(boolean exitService) {
        if (mUpdateStatisticsTimer != null) {
            mUpdateStatisticsTimer.cancel();
            mUpdateStatisticsTimer = null;
        }
        stopPowerMonitoring();

        stopRecord();
        releaseConnections();

        if (mStreamer != null) {
            mStreamer.release();
            mStreamer = null;
        }
        mConditioner = null;
        mHandler.removeCallbacksAndMessages(null);
        if (mOverlayManager != null) {
            mOverlayManager.cancel();
            mOverlayManager = null;
        }
        if (mUseBluetooth) {
            try {
                AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                if (am != null) {
                    am.setMode(AudioManager.MODE_NORMAL);
                    am.stopBluetoothSco();
                }
                unregisterReceiver(mBluetoothScoStateReceiver);
            } catch (Exception ignored) {
            }
            mUseBluetooth = false;
        }

        mVideoCaptureState = Streamer.CaptureState.FAILED;
        mAudioCaptureState = Streamer.CaptureState.FAILED;
        if (exitService) {
            stopSelf();
        } else {
            mBuilder.mActions.clear();
            mBuilder.setWhen(System.currentTimeMillis());
            mNotificationManager.notify(NOTIFY_ID, mBuilder.build());
        }
        EventLog.getInstance().close();
    }

    private void notifyClient() {
        if (mListener != null && mListener.getHandler() != null) {
            mListener.getHandler().post(() -> {
                if (mListener != null) {
                    mListener.updateStreamerState();
                }
            });
        }
    }

    public void startBroadcast() {
        int status;

        status = canStart();

        if (status == 0) {
            final List<Connection> connections = ConnectivitySettings.connections();
            for (Connection connection : connections) {
                createConnection(connection);
            }
            mIsRecordOn = startRecord();

            if (mConnectionId.isEmpty() && !mIsRecordOn) {
                status = R.string.unknown_fail;
            } else {
                mBroadcastOn = true;
                if (mOverlayManager != null) {
                    mOverlayManager.setPauseMode(this, OverlayManager.PauseMode.OFF);
                }
                mStreamer.setVideoOrientation(mOrientation);
                mStopAction.title = getString(R.string.action_title_stop);
                if (mConditioner != null && !mConnectionId.isEmpty()) {
                    mConditioner.start(mStreamer);
                }
            }
            notifyClient();
        }
        if (status == 0) {
            status = R.string.notification_connecting;
        }

        setNotificationText(status);
    }

    int canStart() {
        if (!isStreamerReady()) {
            return waitCause();
        }

        final List<Connection> connectionsList = ConnectivitySettings.connections();

        if (!MediaFileSettings.record(this)) {
            if (connectionsList.isEmpty()) {
                return R.string.no_uri;
            }
            if (!ConnectivitySettings.isConnected(this)) {
                return R.string.not_connected;
            }
        }
        return 0;
    }

    int waitCause() {
        return mUseBluetooth && mAudioCaptureState != Streamer.CaptureState.STARTED ?
                R.string.no_bluetooth : R.string.please_wait;
    }

    public void stopBroadcast() {
        mBroadcastOn = false;
        if (Settings.standbyEnabled()) {
            if (mOverlayManager != null) {
                mOverlayManager.setPauseMode(this, OverlayManager.PauseMode.PRE_STANDBY);
            }
        }

        stopRecord();
        releaseConnections();
        notifyClient();
        mStopAction.title = getString(R.string.action_title_start);
        setNotificationText(R.string.notification_idle);
    }

    public StreamerGL getStreamer() {
        return mStreamer;
    }

    @SuppressLint("RestrictedApi")
    private void setNotificationText(int resId) {
        final SpannableString formattedBody = formattedBody(getString(resId));
        mBuilder.setWhen(System.currentTimeMillis())
                .setSmallIcon(mBroadcastOn ? android.R.drawable.stat_sys_upload : R.drawable.ic_stat_qwe)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(formattedBody))
                .setContentText(formattedBody);

        mBuilder.mActions.clear();
        mBuilder.addAction(mStopAction);
        if (mBroadcastOn) {
            mBuilder.addAction(mPauseAction);
        } else {
            mBuilder.addAction(mExitAction);
        }

        mNotificationManager.notify(NOTIFY_ID, mBuilder.build());
    }

    public boolean isStreamerReady() {
        if (mStreamer == null) {
            return false;
        }

        return mVideoCaptureState == Streamer.CaptureState.STARTED
                    && mAudioCaptureState == Streamer.CaptureState.STARTED;
    }

    private boolean haveConnectionsOrRetry() {
        // no active connections and no pending retry -> all connection attempts failed
        return (mStreamer != null
                && (mConnectionId.size() > 0 || mRetryPending > 0));
    }

    public boolean isRecordOn() {
        return mStreamer != null && mIsRecordOn;
    }

    public boolean isBroadcastOn() {
        return haveConnectionsOrRetry() || isRecordOn();
    }

    public void setRotation(int displayRotation) {
        int videoOrientation;
        boolean isPortrait;
        if (mNormalOrientation == StreamerGL.Orientations.PORTRAIT) {
            isPortrait = displayRotation == Surface.ROTATION_0 || displayRotation == Surface.ROTATION_180;
        } else {
            isPortrait = displayRotation == Surface.ROTATION_90 || displayRotation == Surface.ROTATION_270;
        }
        videoOrientation = isPortrait ? StreamerGL.Orientations.PORTRAIT : StreamerGL.Orientations.LANDSCAPE;
        setRotation(videoOrientation, displayRotation);
    }

    public void setRotation(int videoOrientation,
                            int displayRotation) {
        mRotation = displayRotation;
        mOrientation = videoOrientation;

        if (mStreamer != null) {
            mStreamer.setDisplayRotation(displayRotation);
            if (!(mLiveRotation && isBroadcastOn())) {
                mStreamer.setVideoOrientation(videoOrientation);
            }
        }
    }

    public void togglePause() {
        if (!isStreamerReady()) {
            return;
        }
        final boolean standyEnabled = Settings.standbyEnabled();
        final boolean isActive = isBroadcastOn();
        final boolean toggleCamera = !standyEnabled;
        if (!isActive) {
            if (!standyEnabled) {
                return;
            }
            startBroadcast();
        }
        if (isPaused()) {
            cancelPause();
            if (mOverlayManager != null) {
                mOverlayManager.setPauseMode(this, OverlayManager.PauseMode.OFF);
            }
        } else {
            if (toggleCamera) {
                mPausedCameraId = mStreamer.getActiveCameraId();
                if (mPausedCameraId == null) {
                    return;
                }
                mStreamer.flip(null, null);
            }
            mStreamer.setSilence(true);
            mPauseAction.title = getString(R.string.action_title_resume);
            if (mOverlayManager != null) {
                mOverlayManager.setPauseMode(this, isActive ? OverlayManager.PauseMode.PAUSE : OverlayManager.PauseMode.STANDBY);
            }
        }
    }

    void cancelPause() {
        if (!isStreamerReady()) {
            return;
        }
        final boolean toggleCamera = !Settings.standbyEnabled();

        if (toggleCamera) {
            if (mPausedCameraId == null) {
                return;
            }
            mStreamer.flip(mPausedCameraId, null);
            mPausedCameraId = null;
        }

        mPauseAction.title = getString(R.string.action_title_pause);
        if (mListener != null) {
            Streamer.Size size = mStreamer.getActiveCameraVideoSize();
            mListener.updatePreviewRatio(size);
        }
    }

    public boolean isPaused() {
        if (!isBroadcastOn() || mOverlayManager == null) {
            return false;
        }
        final OverlayManager.PauseMode mode = mOverlayManager.getPauseMode();
        return OverlayManager.PauseMode.PAUSE == mode || OverlayManager.PauseMode.STANDBY == mode;
    }

    protected BroadcastReceiver mBluetoothScoStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED.equals(intent.getAction())) {
                int state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1);
                Logd(TAG, "Audio SCO state: " + state);
                switch (state) {
                    case AudioManager.SCO_AUDIO_STATE_CONNECTED:
                        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                        if (mStreamer != null && am != null) {
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
                if (mListener != null) {
                    mListener.onScoStatusChanged(state);
                }
            } else {
                int btState = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
                if (btState == BluetoothProfile.STATE_CONNECTED) {
                    startAudioCapture();
                }
            }
        }
    };

    private SpannableString formattedBody(String body) {
        return new SpannableString(HtmlCompat.fromHtml(body, HtmlCompat.FROM_HTML_MODE_LEGACY));
    }

    private void maybeStopBroadcast() {
        if (!haveConnectionsOrRetry()) {
            if (!isRecordOn()) {
                stopBroadcast();
            }
        }
    }

    protected Streamer.AudioRecordingStateListener mAudioRecordingStateListener = new Streamer.AudioRecordingStateListener() {
        @Override
        public Handler getHandler() {
            return mHandler;
        }

        @Override
        public void onAudioRecordingConfigurationChanged(AudioRecordingConfiguration configuration) {
            //Log.d(TAG, "onAudioRecordingConfigurationChanged");
            mAudioRecordingConfiguration = configuration;
            notifyAudioRecordingConfigurationChanged();
        }

        @Override
        public void onRoutingChanged(AudioRouting router) {
            //Log.d(TAG, "onRoutingChanged");
        }
    };

    private void notifyAudioRecordingConfigurationChanged() {
        if (mStreamer != null && mListener != null && mAudioRecordingConfiguration != null) {
            mListener.onAudioRecordingConfigurationChanged(mAudioRecordingConfiguration);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                mListener.setActiveMicrophonesInfo(mStreamer.getActiveMicrophones());
            }
        }
    }

    private void stopPowerMonitoring() {
        if (mCheckBatteryLevelTimer != null) {
            mCheckBatteryLevelTimer.cancel();
            mCheckBatteryLevelTimer = null;
        }
        mHandler.removeCallbacks(mCheckBatteryLevel);

        if (mBatteryReceiver != null) {
            unregisterReceiver(mBatteryReceiver);
            mBatteryReceiver = null;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
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
            if (mListener != null) {
                mListener.updateBattery(status);
            }
        }
    }

    @SuppressLint("DefaultLocale")
    @Override
    public void onNtpUpdate(Date trueTime) {
        if (mStreamer != null) {
            mStreamer.setPreciseTime(trueTime);
        }
        Date now = new Date();
        SimpleDateFormat df = new SimpleDateFormat("H:m:s.SSS");
        String ntpStr = df.format(trueTime);
        String localStr = df.format(now);
        long dt = now.getTime() - trueTime.getTime();
        float diffSec = (dt / 1000f);
        localStr += String.format(" (%+6.3f s)", diffSec);
        EventLog.getInstance().put(Streamer.LoggerListener.Severity.INFO, "NTP time: " + ntpStr);
        EventLog.getInstance().put(Streamer.LoggerListener.Severity.INFO, "Local time: " + localStr);
    }

    @Override
    public void onNtpError(String error) {
        EventLog.getInstance().put(Streamer.LoggerListener.Severity.ERROR, "NTP update error: " + error);
    }

}
