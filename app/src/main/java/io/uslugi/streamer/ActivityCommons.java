package io.uslugi.streamer;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.hardware.SensorManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.GestureDetectorCompat;

import com.wmspanel.libstream.FocusMode;
import com.wmspanel.libstream.Streamer;
import com.wmspanel.libstream.StreamerGL;

import io.uslugi.libcommon.AspectFrameLayout;
import io.uslugi.streamer.databinding.StreamerBinding;
import io.uslugi.streamer.helper.Formatter;
import io.uslugi.streamer.log.EventLog;
import io.uslugi.streamer.sensors.SensorApi;
import io.uslugi.streamer.sensors.SensorInclinometer;
import io.uslugi.streamer.sensors.SensorOrientedAccelerometer;
import io.uslugi.streamer.settingsutils.AudioSettings;
import io.uslugi.streamer.settingsutils.CameraSettings;
import io.uslugi.streamer.settingsutils.ConcurrentCameraSettings;
import io.uslugi.streamer.settingsutils.Settings;
import io.uslugi.streamer.ui.QuickSettingsPopup;

public abstract class ActivityCommons extends AppCompatActivity implements QuickSettingsPopup.CameraOptionsListener {

    protected StreamerBinding binding;

    protected void flipClick() {
        throw new UnsupportedOperationException();
    }

    protected void quickSettingsClick() {
    }

    protected void broadcastClick() {
        throw new UnsupportedOperationException();
    }

    protected Formatter mFormatter;

    protected QuickSettingsPopup mQuickSettings;
    protected FocusMode mFocusMode = new FocusMode();
    protected GestureDetectorCompat mDetector;

    protected int mVolumeKeysAction = Settings.ACTION_START_STOP;

    protected boolean mUseCamera2;
    protected Streamer.Size mGridSize;

    protected boolean mLiveRotation;
    protected boolean mLockOrientation;
    protected boolean mVerticalVideo;
    protected boolean mFullView;

    protected AlertDialog mAlert;

    protected Streamer.ConcurrentCameraMode mConcurrentCameraMode = Streamer.ConcurrentCameraMode.OFF;

    protected static class MyGestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onDown(@NonNull MotionEvent event) {
            // https://stackoverflow.com/questions/24326530/long-press-in-gesturedetector-also-fires-on-tap
            return true;
        }

        @Override
        public void onLongPress(@NonNull MotionEvent event) {}

        @Override
        public boolean onSingleTapUp(@NonNull MotionEvent event) {
            return false;
        }
    }

    protected SensorApi mInclinometerListener;

    protected Toast mToast;

    protected void showToast(String text) {
        showToast(Streamer.LoggerListener.Severity.ERROR, text);
    }

    private void showToast(Streamer.LoggerListener.Severity severity, String text) {
        if (BuildConfig.DEBUG) {
            if (!isFinishing()) {
                dismissToast();
                mToast = Toast.makeText(this, text, Toast.LENGTH_LONG);
                mToast.show();
            }
        }

        EventLog.getInstance().put(severity, text);
    }

    protected void showToast(@StringRes int resId) {
        showToast(getString(resId));
    }

    protected void dismissToast() {
        if (mToast != null) {
            mToast.cancel();
            mToast = null;
        }
    }

    protected boolean isPortrait() {
        boolean portrait;
        if (!isInMultiWindowMode()) {
            final int orientation = getResources().getConfiguration().orientation;
            portrait = orientation == Configuration.ORIENTATION_PORTRAIT;
        } else {
            final Context app = getApplicationContext();
            WindowManager manager = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
            int width;
            int height;
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                Display display = manager.getDefaultDisplay();
                DisplayMetrics metrics = new DisplayMetrics();
                display.getRealMetrics(metrics);
                width = metrics.widthPixels;
                height = metrics.heightPixels;
            } else {
                WindowMetrics metrics = manager.getMaximumWindowMetrics();
                width = metrics.getBounds().width();
                height = metrics.getBounds().height();
            }
            portrait = height >= width;
        }
        return portrait;
    }

    protected int videoOrientation() {
        if (isPortrait()) {
            return StreamerGL.Orientations.PORTRAIT;
        } else {
            return StreamerGL.Orientations.LANDSCAPE;
        }
    }

    protected int displayRotation() {
        final Display display;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            display = getWindowManager().getDefaultDisplay();
        } else {
            display = getDisplay();
        }
        return display != null ? display.getRotation() : Surface.ROTATION_90;
    }

    protected void updatePreviewRatio(AspectFrameLayout frame, Streamer.Size size) {
        if (frame == null || size == null) {
            return;
        }

        if (!isPortrait()) {
            frame.setAspectRatio(size.getRatio());
        } else {
            // Vertical video, so reverse aspect ratio
            frame.setAspectRatio(size.getVerticalRatio());
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //Log.v(TAG, "onCreate()");

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        final ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.hide();
        }

        binding = StreamerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.textCapture.setOnClickListener(v -> broadcastClick());

        binding.btnFlash.setOnClickListener(view -> flashClick());

        mFormatter = new Formatter(this);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        boolean consumeEvent = false;
        switch (keyCode) {
            case KeyEvent.KEYCODE_ZOOM_IN:
            case KeyEvent.KEYCODE_ZOOM_OUT:
                return zoomClick(keyCode);
            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (mVolumeKeysAction == Settings.ACTION_DO_NOTHING) {
                    return super.onKeyDown(keyCode, event);
                } else if (mVolumeKeysAction == Settings.ACTION_ZOOM) {
                    return zoomClick(keyCode);
                } else if (mVolumeKeysAction == Settings.ACTION_FLIP) {
                    flipClick();
                    return true;
                }
                // FALLTHROUGH
            case KeyEvent.KEYCODE_CAMERA:
                if (event.getRepeatCount() == 0) {
                    broadcastClick();
                }
                consumeEvent = true;
                break;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS: // media codes are for "selfie sticks" buttons
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_STOP:
                AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                if (am != null && am.isWiredHeadsetOn()) {
                    if (event.getRepeatCount() == 0) {
                        broadcastClick();
                    }
                    consumeEvent = true;
                }
                break;
            default:
                break;
        }
        return consumeEvent || super.onKeyDown(keyCode, event);
    }

    protected void updateFlashButton(boolean isOn) {
        if (isOn) {
            binding.btnFlash.setAlpha(1.0f);
            binding.btnFlash.setBackgroundResource(R.drawable.button);
            binding.btnFlash.setImageResource(R.drawable.flash_on);
        } else {
            binding.btnFlash.setAlpha(0.3f);
            binding.btnFlash.setBackgroundResource(R.drawable.button_inverse);
            binding.btnFlash.setImageResource(R.drawable.flash_off);
        }
    }

    protected void updateStopped(boolean broadcasting) {
        if (!broadcasting) {
            binding.textCapture.setText(R.string.STREAMER_START_TEXT);
        }
    }

    @Override
    public void onCameraOptionChange(@NonNull String option, @NonNull String value) {
        if (mQuickSettings == null || mFocusMode == null) {
            return;
        }

        // Camera options will be applied to active lens only
        // Initial values should be set from "Settings/Video/Start new les with" section

        if (mUseCamera2) {
            onCamera2OptionChange(option);
        } else {
            onCameraOptionChange(option);
        }
    }

    protected void onCameraOptionChange(@NonNull String option) {}

    protected void onCamera2OptionChange(@NonNull String option) {}

    protected void releaseQuickSettings() {}

    @Override
    public void onFlip(@NonNull final String cameraId, final String physicalCameraId) {
        flip(cameraId, physicalCameraId);
    }

    @Override
    public void onConcurrentCameraSelect(final String cameraId) {
        selectConcurrentCamera(cameraId);
    }

    abstract void selectConcurrentCamera(final String cameraId);

    @Override
    public void flashClick() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onZoom(final float scaleFactor) {
        zoom(scaleFactor);
    }

    abstract boolean zoom(float scaleFactor);

    abstract boolean zoomClick(int keyCode);

    @Override
    public void onGain(final float gainDb, final boolean save) {
        setGain(AudioSettings.audioGain(gainDb));
    }

    abstract void setGain(float gain);

    @Override
    public void onSetPreferredAudioDevice(AudioDeviceInfo deviceInfo) {
        setPreferredAudioDevice(deviceInfo);
    }

    abstract void setPreferredAudioDevice(AudioDeviceInfo deviceInfo);

    @Override
    public void onFocus(final int focusMode, final float focusDistance) {
        focus(focusMode, focusDistance);
    }

    abstract void focus(final int focusMode, final float focusDistance);

    abstract String lock(boolean lockFocus, boolean lockExposure, boolean lockWb);

    abstract void flip(final String cameraId, final String physicalCameraId);

    protected void readPreferences() {
        mConcurrentCameraMode = ConcurrentCameraSettings.mode();

        if (mConcurrentCameraMode == Streamer.ConcurrentCameraMode.OFF) {
            mUseCamera2 = CameraSettings.isUsingCamera2(this);

            mLiveRotation = Settings.liveRotation();
            mLockOrientation = Settings.lockOrientationOnStreamStart();

            mVerticalVideo = Settings.verticalVideo();

            mFullView = Settings.fullScreenPreview();
        } else {
            mUseCamera2 = true;

            mLiveRotation = false;
            mLockOrientation = false;

            mVerticalVideo = Settings.verticalVideo();

            mFullView = false;
        }
    }

    protected int initialOrientation() {
        if (mLiveRotation) {
            return ActivityInfo.SCREEN_ORIENTATION_SENSOR;
        } else {
            return mVerticalVideo ?
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        }
    }

    protected void showDialog(AlertDialog.Builder dialog) {
        if (!isFinishing()) {
            dismissDialog();
            mAlert = dialog.show();
        }
    }

    protected void dismissDialog() {
        if (mAlert != null && mAlert.isShowing()) {
            mAlert.dismiss();
        }
    }

    protected void showPermissionsDeniedDialog() {
        final AlertDialog.Builder alert = new AlertDialog.Builder(this)
                .setTitle(R.string.permissions_denied)
                .setMessage(R.string.permissions_denied_hint)
                .setNegativeButton(R.string.permissions_quit, (dialog, which) -> finish())
                .setPositiveButton(R.string.permissions_try_again, (dialog, which) -> {
                    final Intent intent = new Intent(this, LaunchActivity.class);
                    finish();
                    startActivity(intent);
                })
                .setCancelable(false);
        showDialog(alert);
    }

    private boolean hasPermission(String permission) {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    protected boolean hasPermissions() {
        final boolean camera = AudioSettings.radioMode() || hasPermission(Manifest.permission.CAMERA);
        final boolean mic = hasPermission(Manifest.permission.RECORD_AUDIO);
        return camera && mic;
    }

    protected SensorManager getSensorManager() {
        return (SensorManager) getSystemService(Context.SENSOR_SERVICE);
    }

    protected void registerInclinometer() {
        if (mInclinometerListener != null) {
            return;
        }
        mInclinometerListener = new SensorApi() {
            @Override
            public void onDataReceived(long timestamp, float[] values) {
                //Log.v(TAG, "pitch=" + values[0] + " roll=" + values[1]);
            }

            @Override
            public void onAccuracyChanged(int accuracy) {

            }
        };
        SensorOrientedAccelerometer.getInstance().setOrientation(getResources().getConfiguration().orientation);
        SensorInclinometer.getInstance().registerListener(getSensorManager(), mInclinometerListener);
    }

    protected void unregisterInclinometer() {
        if (mInclinometerListener == null) {
            return;
        }

        SensorInclinometer.getInstance().unregisterListener(getSensorManager(), mInclinometerListener);
        mInclinometerListener = null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Settings.showInclinometer()) {
            registerInclinometer();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterInclinometer();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mInclinometerListener != null) {
            SensorOrientedAccelerometer.getInstance().setOrientation(getResources().getConfiguration().orientation);
        }
    }

}
