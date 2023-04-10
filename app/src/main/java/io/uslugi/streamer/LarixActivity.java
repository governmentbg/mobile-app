package io.uslugi.streamer;

import static android.content.DialogInterface.BUTTON_NEGATIVE;
import static android.content.DialogInterface.BUTTON_POSITIVE;
import static io.uslugi.streamer.log.EventLog.Logd;

import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.hardware.camera2.TotalCaptureResult;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.GestureDetectorCompat;

import com.wmspanel.libstream.AudioConfig;
import com.wmspanel.libstream.Streamer;
import com.wmspanel.libstream.StreamerGL;
import com.wmspanel.libstream.StreamerGLBuilder;
import com.wmspanel.libstream.VideoConfig;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.uslugi.libcommon.CameraInfo;
import io.uslugi.libcommon.CameraRegistry;
import io.uslugi.streamer.cameramanager.CameraListHelper;
import io.uslugi.streamer.conditioner.StreamConditionerBase;
import io.uslugi.streamer.data.Section;
import io.uslugi.streamer.data.SikSingleton;
import io.uslugi.streamer.helper.Constants;
import io.uslugi.streamer.helper.DialogHelper;
import io.uslugi.streamer.log.EventLog;
import io.uslugi.streamer.overlay.OverlayManager;
import io.uslugi.streamer.settingsutils.AudioSettings;
import io.uslugi.streamer.settingsutils.CameraSettings;
import io.uslugi.streamer.settingsutils.ConcurrentCameraSettings;
import io.uslugi.streamer.settingsutils.LensSettings;
import io.uslugi.streamer.settingsutils.MediaFileSettings;
import io.uslugi.streamer.settingsutils.Settings;
import io.uslugi.streamer.settingsutils.VideoEncoderSettings;
import io.uslugi.streamer.ui.LensSetup;
import io.uslugi.streamer.ui.SetupHolder;

public class LarixActivity extends MainActivityBase {
    protected final String TAG = "LarixActivity";

    protected StreamerGL mStreamerGL;

    protected SurfaceHolder mHolder;

    private OverlayManager mOverlayManager;

    private String mPausedCameraId;

    private List<Float> mZoomSteps;

    private Map<String, CameraInfo> mCameraInfoMap = new LinkedHashMap<>();
    private boolean mMicrophoneDirectionFollowsCamera;

    private String flashStatus = Camera.Parameters.FLASH_MODE_OFF;

    protected class MyOnTouchListener implements View.OnTouchListener {
        public boolean onTouch(View v, MotionEvent event) {
            //Logd(TAG, "SurfaceView: onTouch");
            if (mStreamer == null || mVideoCaptureState != Streamer.CaptureState.STARTED) {
                return false;
            }
            boolean canZoom = mStreamer.isZoomSupported();
            return mDetector.onTouchEvent(event) ||
                    (canZoom && mScaleGestureDetector.onTouchEvent(event));
        }
    }

    protected class MyScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector scaleGestureDetector) {
            return zoom(mScaleFactor * scaleGestureDetector.getScaleFactor());
        }
    }

    protected SurfaceHolder.Callback mPreviewHolderCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(@NonNull SurfaceHolder holder) {
            //Log.v(TAG, "surfaceCreated()");

            if (mHolder != null) {
                Log.e(TAG, "SurfaceHolder already exists"); // should never happens
                return;
            }

            mHolder = holder;
            // We got surface to draw on, start streamer creation
            createStreamer(mHolder);
        }

        @Override
        public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
            //Log.v(TAG, "surfaceChanged() " + width + "x" + height);
            if (mStreamer != null) {
                mStreamerGL.setSurfaceSize(new Streamer.Size(width, height));
            }
        }

        @Override
        public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
            //Log.v(TAG, "surfaceDestroyed()");
            mHolder = null;
            releaseStreamer();
        }
    };

    private final Runnable mFocusRunnable = () -> {
        if (mStreamerGL != null) {
            mStreamerGL.focus(mFocusMode);
        }
    };

    private final Streamer.CaptureCallback mCaptureCallback = new Streamer.CaptureCallback() {
        @Override
        public Handler getHandler() {
            return mHandler;
        }

        @Override
        public void onCaptureCompleted(@NonNull TotalCaptureResult result) {
            final LensSetup settings = SetupHolder.getInstance().getLensSetup(
                    mStreamerGL.getActiveCameraId(), mStreamerGL.getActivePhysicalCameraId());
            if (!settings.hasActiveLock()) {
                return;
            }
            settings.lock(mFocusMode, result);
            focus();
        }
    };

    private final Streamer.LoggerListener mLoggerListener =
            (severity, message) -> EventLog.getInstance().put(severity, message);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding.surfaceView.getHolder().addCallback(mPreviewHolderCallback);

        // detect pinch-to-zoom
        mScaleGestureDetector = new ScaleGestureDetector(this, new MyScaleListener());
        // detect long press
        mDetector = new GestureDetectorCompat(this, new MyGestureListener());

        // Setup screen preventing dim and sleep
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onResume() {
        super.onResume();

        readPreferences();

        mMicrophoneDirectionFollowsCamera = AudioSettings.microphoneDirectionFollowsCamera();

        setRequestedOrientation(initialOrientation());

        // Opening camera is an expensive task for every android application
        // The speed of camera opening depends on the hardware capacity
        // StreamerGL will run camera operations in separate thread, after completion it will notify UI
        // The progress bar shows a cyclic animation while camera operation runs in background
        binding.indicator.setVisibility(View.VISIBLE);

        // it is transition from onPause() to onResume(), we already have surface
        if (mHolder != null) {
            //Log.v(TAG, "Resuming after pause");
            createStreamer(mHolder);
        }
        // else it is transition
        // onCreate() -> onStart() -> onResume()
        // or
        // onPause() -> surfaceDestroyed() -> onStop() -> onRestart() -> onStart() -> onResume()
        // in both scenarios we should wait for surfaceCreated callback

        // forward touch event to gesture detectors
        binding.surfaceView.setOnTouchListener(new MyOnTouchListener());

        mScaleFactor = 0.0f;
    }

    protected void createStreamer(SurfaceHolder holder) {
        //Log.v(TAG, "createStreamer()");
        if (mStreamer != null) {
            return;
        }

        if (!hasPermissions()) {
            showPermissionsDeniedDialog();
            return;
        }

        EventLog.getInstance().init(this, MediaFileSettings.logUri(this), BuildConfig.DEBUG);

        final StreamerGLBuilder builder = new StreamerGLBuilder();
        builder.setLoggerListener(mLoggerListener);

        final List<CameraInfo> cameraList = CameraRegistry.getCameraList(this, mUseCamera2);

        if (cameraList.size() == 0) {
            showToast(getString(R.string.no_camera_found));
            return;
        }

        // common
        builder.setContext(this);
        // Streamer.Listener implementation, see MainActivityBase class for details
        builder.setListener(this);
        builder.setUserAgent(Constants.APP_NAME + "/" + Streamer.VERSION_NAME);

        // audio
        final AudioConfig audioConfig = AudioSettings.newAudioConfig();
        builder.setAudioConfig(audioConfig);

        // video
        builder.setCamera2(mUseCamera2);

        // focus mode is a very basic interface to set initial camera parameters
        // it supports focus modes (auto/infinity), awb, anti-flicker and exposure compensation

        // setFocusMode() is optional, if you are satisfied with automatic camera controls, skip it

        // if you need to tweak camera parameters on-the-fly, update mFocusMode and use focus()
        // to set new values; refer to MyGestureListener fo details
        // camera parameters will be validated and if parameter is not supported it will be set to auto mode
        LensSettings.readDefaultLensSetup(this, mFocusMode, mUseCamera2);
        builder.setFocusMode(mFocusMode);

        // get default camera id (it was set by user in settings)
        final CameraInfo activeCameraInfo = CameraSettings.getActiveCameraInfo(cameraList);
        if (activeCameraInfo == null) {
            showToast(getString(R.string.no_camera_found));
            return;
        }

        // Stream resolution is not tied to camera preview sizes
        // For example, you need gorgeous FullHD preview and smaller stream to save traffic
        // To achieve this, you should first set VideoConfig.videoSize to 640x360,
        // then pass 1920x1080 to addCamera()

        // Nonetheless, we highly recommend to set same resolution for camera preview
        // and stream for better image scaling that will be done by camera sensor in this case

        // The Android Compatibility Definition Document (CDD) defines a set of mandatory resolutions
        // https://source.android.com/compatibility/cdd.html

        // If you need same resolution for all devices, check if resolution is listed in section 5.2 of CDD,
        // please note that resolution list is not stable and changes from v4.3 to v5.1
        // On API 21 you can check if resolution is supported with MediaCodecInfo.VideoCapabilities#getVideoCapabilities()

        // Larix itself rely on camera preview resolution set
        // http://stackoverflow.com/questions/32076412/find-the-mediacodec-supported-video-encoding-resolutions-under-jelly-bean-mr2

        // If stream and camera resolutions are different, then video will be
        // up-scaled or down-scaled to fill 100% of stream

        // If aspect ratios of stream and camera are different, video will be letterboxed
        // or pillarboxed to fit stream
        // https://en.wikipedia.org/wiki/Letterboxing_(filming)

        // This is useful option for camera flip, for example if back camera can do 1280x720 HD
        // and front camera can only produce 640x480

        // Camera preview resolution
        final Streamer.Size cameraPreviewSize;
        // Stream resolution
        final Streamer.Size videoSize;

        videoSize = (mConcurrentCameraMode == Streamer.ConcurrentCameraMode.OFF) ?
                CameraSettings.getVideoSize(this, activeCameraInfo)
                : ConcurrentCameraSettings.videoSize();

        cameraPreviewSize = videoSize;

        // To get vertical video just swap width and height
        // do not modify videoSize itself because Android camera is always landscape
        //noinspection SuspiciousNameCombination
        final Streamer.Size encoderVideoSize = mVerticalVideo ?
                new Streamer.Size(videoSize.height, videoSize.width) : videoSize;

        final VideoConfig videoConfig = VideoEncoderSettings.newVideoConfig(
                this, encoderVideoSize);
        builder.setVideoConfig(videoConfig);

        // Optional NDI preview resolution stream
        builder.setNdiPreviewConfig(VideoEncoderSettings.newNdiPreviewVideoConfig(videoConfig));

        if (!videoConfig.videoSize.equals(encoderVideoSize)) {
            final String msg = getString(R.string.unsupported_resolution, encoderVideoSize);
            final AlertDialog.Builder alert = new AlertDialog.Builder(this)
                    .setTitle(R.string.unsupported_resolution_title)
                    .setMessage(msg)
                    .setPositiveButton(android.R.string.ok, null)
                    .setCancelable(true);
            showDialog(alert);
        }

        Logd(TAG, "Стрийм резолюция: " + videoConfig.videoSize);

        builder.setMaxBufferItems(Settings.maxBufferItems(audioConfig, videoConfig));

        // camera preview and it's size, if surface view size changes, then
        // app should pass new size to streamer (refer to surfaceChanged() for details)

        // show the camera preview without the black stripes on the sides
        builder.setFullView(mFullView);

        // note that camera preview is not affected by setVideoOrientation
        // preview will fill entire rectangle, app should take care of aspect ratio
        // larix wraps preview surface with AspectFrameLayout to maintain a specific aspect ratio
        builder.setSurface(holder.getSurface());
        builder.setSurfaceSize(new Streamer.Size(binding.surfaceView.getWidth(), binding.surfaceView.getHeight()));

        // orientation will be later changed to actual device orientation when user press "Broadcast" button
        // or will be updated dynamically from onConfigurationChanged listener if Live rotation is on
        // with corresponding mStreamerGL.setVideoOrientation(...) method call
        //
        // setVideoOrientation() doesn't change stream resolution set by VideoConfig.videoSize, for example:
        //
        // stream is 1920x1080, orientation is LANDSCAPE -> horizontal video
        // stream is 1920x1080, orientation is PORTRAIT -> pillarboxed horizontal video
        //
        // stream is 1080x1920, orientation is PORTRAIT -> vertical video
        // stream is 1080x1920, orientation is LANDSCAPE -> letterboxed vertical video
        //
        // try to turn "Video/Live rotation" and "Video/Vertical stream" on/off
        // to see these options in action
        //
        // refer to onConfigurationChanged, mCaptureButtonListener and mLockOrientation flag
        // to see how exactly Live rotation is implemented
        builder.setVideoOrientation(videoOrientation());

        // http://developer.android.com/reference/android/hardware/Camera.html#setDisplayOrientation%28int%29
        // will be updated from onConfigurationChanged and mCaptureButtonListener
        // with corresponding mStreamerGL.setDisplayRotation(...) method call
        // this value is required to make correct video rotation
        builder.setDisplayRotation(displayRotation());

        final List<String> uuids;

        final Streamer.ConcurrentCameraInfo concurrentCameraInfo = new Streamer.ConcurrentCameraInfo();
        if (mConcurrentCameraMode == Streamer.ConcurrentCameraMode.OFF) {
            CameraListHelper.addCameras(this,
                    builder,
                    cameraList,
                    activeCameraInfo,
                    cameraPreviewSize);
        } else {
            uuids = CameraListHelper.addConcurrentCameras();
            if (uuids.isEmpty()) {
                return;
            }
            builder.getCameraId();

            concurrentCameraInfo.mode = mConcurrentCameraMode;
            concurrentCameraInfo.cameraIds.addAll(uuids);
        }

        builder.setConcurrentCameraMode(mConcurrentCameraMode);

        mCameraInfoMap = CameraListHelper.toMap(cameraList, true);
        if (mMicrophoneDirectionFollowsCamera) {
            audioConfig.preferredMicrophoneDirection = AudioSettings.preferredMicrophoneDirection(activeCameraInfo);
        }

        // if you absolutely do not plan to broadcast video, skip video setup
        // and call builder.build(Streamer.MODE.AUDIO_ONLY)
        // this will save some resources because video encoder will not be created
        // in this case app is responsible for:
        // 1) do not call startVideoCapture()
        // 2) limit connection mode to Streamer.MODE.AUDIO_ONLY
        mStreamerGL = builder.build();

        if (mStreamerGL != null) {
            mStreamer = mStreamerGL;

            // Streamer build succeeded, can start Video/Audio capture
            // call startVideoCapture, wait for onVideoCaptureStateChanged callback
            startVideoCapture();
            // call startAudioCapture, wait for onAudioCaptureStateChanged callback
            startAudioCapture();

            // Safe margins frame's aspect ratio should match outgoing stream aspect ratio
            mGridSize = videoSize;

            // Deal with preview's aspect ratio
            updatePreviewRatio();

            mOverlayManager = new OverlayManager(mStreamerGL, this, videoConfig.videoSize);

            SetupHolder.getInstance().init(cameraList, mFocusMode);
        }

        mConditioner = StreamConditionerBase.newInstance(this,
                videoConfig.bitRate, activeCameraInfo);

        updateFlashButton(mStreamerGL.isTorchOn());
        setDefaultFlashMode();
    }

    protected boolean shouldUpdateVideoOrientation() {
        return mLiveRotation && !mLockOrientation;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (mStreamer == null) {
            return;
        }

        if (shouldUpdateVideoOrientation()) {
            mStreamerGL.setVideoOrientation(videoOrientation());
        }

        // Set display rotation to flip image correctly, should be called always
        mStreamerGL.setDisplayRotation(displayRotation());

        updatePreviewRatio();
    }

    protected void updatePreviewRatio() {
        if (!mFullView) {
            updatePreviewRatio(binding.previewAfl, mStreamerGL.getActiveCameraVideoSize());
        }
    }

    @Override
    protected void broadcastClick() {
        if (mStreamer == null) {
            // preventing accidental touch issues
            return;
        }
        final OverlayManager.PauseMode pauseMode = mOverlayManager.getPauseMode();
        if (mPausedCameraId != null) {
            mStreamerGL.flip(mPausedCameraId, null);
            mute(mIsMuted);

            mPausedCameraId = null;
            updateStopped(mBroadcastOn);
            mOverlayManager.setPauseMode(this, OverlayManager.PauseMode.OFF);
            updatePreviewRatio();
            return;
        }
        if (OverlayManager.PauseMode.PAUSE == pauseMode || OverlayManager.PauseMode.STANDBY == pauseMode) {
            mute(mIsMuted);

            updateStopped(mBroadcastOn);
            mOverlayManager.setPauseMode(this, OverlayManager.PauseMode.OFF);
            return;
        }

        if (!mBroadcastOn) {
            startBroadcast();
            mOverlayManager.setPauseMode(this, OverlayManager.PauseMode.OFF);
        } else {
            Section section = SikSingleton.INSTANCE.getCurrentSection();
            if (section.getCurrentMode().equals(Constants.Mode.REAL) || section.getCurrentMode().equals(Constants.Mode.UNKNOWN)) {
                createDialog();
            } else {
                stopRecording(this);
            }
        }
    }

    /**
     * Create alert dialog.
     * If the user click on the negative button he/she will keep the screen and recording
     * If the user click on the negative button he/she will stop the recording and navigate to the
     * Login screen
     */
    private void createDialog() {
        DialogHelper.INSTANCE.displayAlertDialog(
                this,
                getString(R.string.ATTENTION_TEXT),
                getString(R.string.STOP_RECORDING_MESSAGE_TEXT),
                getString(R.string.CONFIRM_COMPLETE_RECORDING_TEXT),
                null,
                getString(R.string.NO_TEXT),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        switch (id) {
                            case BUTTON_NEGATIVE:
                                dialog.dismiss();
                                break;
                            case BUTTON_POSITIVE:
                                stopRecording(LarixActivity.this);
                                break;
                        }
                    }
                }
        );
    }

    private void stopRecording(LarixActivity context) {
        releaseConnections();

        if (mLiveRotation && mLockOrientation) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
        }

        // We don't get onConfigurationChanged notify immediately, should update manually
        mStreamerGL.setDisplayRotation(displayRotation());
        if (Settings.standbyEnabled()) {
            mOverlayManager.setPauseMode(context, OverlayManager.PauseMode.PRE_STANDBY);
        }
    }

    private void startBroadcast() {
        if (!createConnections()) {
            return;
        }
        mBroadcastOn = true;
        binding.textCapture.setText(R.string.STREAMER_STOP_TEXT);

        mStreamerGL.setDisplayRotation(displayRotation());

        // Don't try to set landscape/reverse_landscape orientation manually
        // With API 18+ just need to set SCREEN_ORIENTATION_LOCKED
        if (mLiveRotation && mLockOrientation) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        }
        mStreamerGL.setVideoOrientation(videoOrientation());
    }

    @Override
    protected void flipClick() {
        flip(null, null);
    }

    @Override
    protected void flip(final String cameraId, final String physicalCameraId) {
        if (mStreamer == null || mVideoCaptureState != Streamer.CaptureState.STARTED) {
            // preventing accidental touch issues
            return;
        }
        if (mConditioner != null) {
            mConditioner.pause();
        }
        mHandler.removeCallbacks(mFocusRunnable);
        if (cameraId == null) {
            mStreamerGL.flip();
        } else {
            mStreamerGL.flip(cameraId, physicalCameraId);
        }
        // Also you can switch to required camera using mStreamerGL.flip(String cameraId) API
        // For example mStreamerGL.flip("1") will switch to front camera

        // camera is changed, so update aspect ratio to actual value
        updatePreviewRatio();

        updateFpsRanges();

        onPostFlip();
    }

    @Override
    void selectConcurrentCamera(final String cameraId) {
        if (mStreamer == null || mVideoCaptureState != Streamer.CaptureState.STARTED) {
            // preventing accidental touch issues
            return;
        }
        mStreamerGL.selectConcurrentCamera(cameraId);
        onPostFlip();
    }

    private void onPostFlip() {
        mFocusMode = mStreamerGL.getFocusMode();

        // camera is changed, update zoom level
        mZoomSteps = null;

        mScaleFactor = mStreamerGL.getZoom(
                mStreamerGL.getActiveCameraId(), mStreamerGL.getActivePhysicalCameraId());

        if (mMicrophoneDirectionFollowsCamera) {
            final CameraInfo activeCameraInfo = mCameraInfoMap.get(CameraListHelper.uuid(
                    mStreamerGL.getActiveCameraId(),
                    mStreamerGL.getActivePhysicalCameraId()));
            mStreamer.setPreferredMicrophoneDirection(
                    AudioSettings.preferredMicrophoneDirection(activeCameraInfo));
        }
    }

    @Override
    protected void quickSettingsClick() {
        if (mStreamer == null || mVideoCaptureState != Streamer.CaptureState.STARTED) {
            // preventing accidental touch issues
            return;
        }
        super.quickSettingsClick();
    }

    @Override
    public void onVideoCaptureStateChanged(Streamer.CaptureState state) {
        super.onVideoCaptureStateChanged(state);
        binding.indicator.setVisibility(View.INVISIBLE);
        if (state == Streamer.CaptureState.STARTED && Settings.picturesAsPreviewed()) {
            // default values: preview -> false, stream -> true
            if (mStreamerGL != null) {
                mStreamerGL.setFrontMirror(false, false);
            }
        }
    }

    @Override
    protected void releaseStreamer() {
        super.releaseStreamer();

        mStreamerGL = null;
        if (mOverlayManager != null) {
            mOverlayManager.cancel();
        }
        mOverlayManager = null;
        EventLog.getInstance().close();
    }

    @Override
    protected void stopRespondingToTouchEvents() {
        binding.surfaceView.setOnTouchListener(null);
    }

    private void updateFpsRanges() {
        if (mConditioner == null) {
            return;
        }
        mConditioner.setCameraInfo(CameraListHelper.findById(
                mStreamerGL.getActiveCameraId(),
                CameraRegistry.getCameraList(this, mUseCamera2)));
        if (mBroadcastOn) {
            mConditioner.resume();
        }
    }

    @Override
    public void onCameraOptionChange(@NonNull String option, @NonNull String value) {
        super.onCameraOptionChange(option, value);
        focus();
    }

    @Override
    public void onOptionSetChange(@NonNull String option, HashSet<Long> value) {
        if (getString(R.string.layers_active_list_key).equals(option) && mOverlayManager != null) {
            mOverlayManager.updateOverlayList(value);
        }
    }

    @Override
    protected boolean zoom(float scaleFactor) {
        if (mStreamer == null || mVideoCaptureState != Streamer.CaptureState.STARTED) {
            return false;
        }

        // Don't let the object get too small or too large.
        mScaleFactor = Math.max(1.0f, Math.min(scaleFactor, mStreamer.getMaxZoom()));
        //Logd(TAG, "Max zoom=" + mStreamer.getMaxZoom() + " want zoom=" + mScaleFactor);

        final float delta = Math.abs(mScaleFactor - mStreamer.getZoom());
        if (mScaleFactor > 1.0f && delta < 0.01f) {
            return false;
        }

        mScaleFactor = Math.round(mScaleFactor * 100) / 100f;
        mStreamer.zoomTo(mScaleFactor);

        return true; // consume touch event
    }

    @Override
    protected boolean zoomClick(int keyCode) {
        if (mStreamer == null || mVideoCaptureState != Streamer.CaptureState.STARTED) {
            return false;
        }
        if (!mStreamer.isZoomSupported()) {
            return true; // consume click anyway
        }
        if (mZoomSteps == null) {
            mZoomSteps = new ArrayList<>();
            CameraSettings.fillZoomSteps(mZoomSteps, mStreamer.getMaxZoom());
        }
        final boolean zoomIn =
                keyCode == KeyEvent.KEYCODE_ZOOM_IN || keyCode == KeyEvent.KEYCODE_VOLUME_UP;
        final float nextZoom = CameraSettings.findNextZoom(
                zoomIn, mZoomSteps, mStreamer.getMaxZoom(), mScaleFactor);
        zoom(nextZoom);

        return true;
    }

    @Override
    protected void focus(final int focusMode, final float focusDistance) {
        mFocusMode.focusMode = focusMode;
        mFocusMode.focusDistance = focusDistance;
        focus();
    }

    private void focus() {
        mHandler.removeCallbacks(mFocusRunnable);
        mHandler.post(mFocusRunnable);
    }

    @Override
    protected String lock(boolean lockFocus, boolean lockExposure, boolean lockWb) {
        if (mStreamerGL == null || mStreamerGL.getActiveCameraId() == null) {
            return null;
        }
        final LensSetup settings = SetupHolder.getInstance().getLensSetup(
                mStreamerGL.getActiveCameraId(), mStreamerGL.getActivePhysicalCameraId());
        String info = null;
        if (settings.hasActiveLock()) {
            settings.unlock(mFocusMode);
            focus();
        } else {
            settings.save(mStreamerGL.getFocusMode());
            settings.focusLocked = settings.isFocusLockSupported() && lockFocus;
            settings.exposureLocked = lockExposure;
            settings.wbLocked = lockWb;
            if (settings.hasActiveLock()) {
                info = settings.makeFocusInfo();
                mStreamerGL.getLensState(mCaptureCallback);
            }
        }
        return info;
    }

    // FLASH logic START

    private void switchFlashModes() {
        if (mStreamerGL == null || mStreamerGL.getActiveCameraId() == null) {
            return;
        }

        mStreamerGL.toggleTorch();

        switch (flashStatus) {
            case Camera.Parameters.FLASH_MODE_TORCH:
                flashStatus = Camera.Parameters.FLASH_MODE_OFF;
                break;
            case Camera.Parameters.FLASH_MODE_OFF:
                flashStatus = Camera.Parameters.FLASH_MODE_TORCH;
                break;
        }
    }

    private void setDefaultFlashMode() {
        Camera.Parameters p = mStreamerGL.getCameraParameters();
        flashStatus = Camera.Parameters.FLASH_MODE_OFF;
        p.setFlashMode(flashStatus);
        mStreamerGL.setCameraParameters(p);
    }

    @Override
    public void flashClick() {
        switchFlashModes();
        updateFlashButton(mStreamerGL.isTorchOn());
    }

    // FLASH logic END

    @Override
    public void onNtpUpdate(Date trueTime) {
        super.onNtpUpdate(trueTime);
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
