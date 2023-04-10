package io.uslugi.streamer.overlay;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.MenuItem;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import io.uslugi.libcommon.CameraInfo;
import io.uslugi.libcommon.CameraRegistry;
import com.wmspanel.libstream.Streamer;
import com.wmspanel.libstream.StreamerGL;
import com.wmspanel.libstream.StreamerGLBuilder;
import com.wmspanel.libstream.VideoConfig;

import io.uslugi.streamer.BuildConfig;
import io.uslugi.streamer.R;
import io.uslugi.streamer.databinding.OverlayPreviewBinding;
import io.uslugi.streamer.settingsutils.Settings;
import io.uslugi.streamer.cameramanager.CameraListHelper;
import io.uslugi.streamer.settingsutils.CameraSettings;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class OverlayPreviewActivity extends AppCompatActivity implements Streamer.Listener, OverlayLoaderListener {
    private final String TAG = "OverlayPreview";

    private StreamerGL mStreamerGL;
    private SurfaceHolder mHolder;
    private Handler mHandler;
    private OverlayPreviewBinding binding;
    private OverlayLoader mOverlayLoader;

    protected Toast mToast;
    protected boolean mVerticalVideo;

    protected void showToast(String text) {
        if (BuildConfig.DEBUG) {
            if (!isFinishing()) {
                dismissToast();
                mToast = Toast.makeText(this, text, Toast.LENGTH_LONG);
                mToast.show();
            }
        }
    }

    protected void dismissToast() {
        if (mToast != null) {
            mToast.cancel();
            mToast = null;
        }
    }


    @Override
    public Handler getHandler() {
        return mHandler;
    }

    @Override
    public void onConnectionStateChanged(int connectionId, Streamer.ConnectionState state, Streamer.Status status, JSONObject info) {

    }

    @Override
    public void onVideoCaptureStateChanged(Streamer.CaptureState state) {
        if (Streamer.CaptureState.ENCODER_FAIL == state || Streamer.CaptureState.FAILED == state) {
            if (mStreamerGL != null) {
                mStreamerGL.stopVideoCapture();
            }
            showToast(getString(R.string.video_status_fail));
        }
    }

    @Override
    public void onAudioCaptureStateChanged(Streamer.CaptureState state) {

    }

    @Override
    public void onRecordStateChanged(Streamer.RecordState state, Uri uri, Streamer.SaveMethod method) {

    }

    @Override
    public void onSnapshotStateChanged(Streamer.RecordState state, Uri uri, Streamer.SaveMethod method) {

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHandler = new Handler(Looper.getMainLooper());
        binding = OverlayPreviewBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        setContentView(view);
        binding.surfaceView.getHolder().addCallback(mPreviewHolderCallback);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        final ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mVerticalVideo = Settings.verticalVideo();

        setRequestedOrientation(mVerticalVideo ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }

    @Override
    protected void onStop() {
        releaseStreamer();
        super.onStop();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    protected SurfaceHolder.Callback mPreviewHolderCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(@NonNull SurfaceHolder holder) {
            // Log.v(TAG, "surfaceCreated()");

            if (mHolder != null) {
                Log.e(TAG, "SurfaceHolder already exists"); // should never happen
                return;
            }

            mHolder = holder;
            // We got surface to draw on, start streamer creation
            createStreamer(mHolder);
        }

        @Override
        public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
            //Log.v(TAG, "surfaceChanged() " + width + "x" + height);
            if (mStreamerGL != null) {
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

    protected void createStreamer(SurfaceHolder holder) {
        if (mStreamerGL != null) {
            return;
        }
        boolean useCamera2 = CameraSettings.isUsingCamera2(this);

        final StreamerGLBuilder builder = new StreamerGLBuilder();
        final List<CameraInfo> cameraList = CameraRegistry.getCameraList(this, useCamera2);

        if (cameraList.size() == 0) {
            showToast(getString(R.string.no_camera_found));
            return;
        }
        builder.setContext(this);
        builder.setListener(this);
        builder.setCamera2(useCamera2);

        final VideoConfig videoConfig = new VideoConfig();

        // get default camera id (it was set by user in settings)
        final CameraInfo activeCameraInfo = CameraSettings.getActiveCameraInfo(cameraList);
        if (activeCameraInfo == null) {
            return;
        }

        final Streamer.Size videoSize = CameraSettings.getVideoSize(this, activeCameraInfo);
        //noinspection SuspiciousNameCombination
        videoConfig.videoSize = mVerticalVideo ? new Streamer.Size(videoSize.height, videoSize.width) : videoSize;

        builder.setVideoConfig(videoConfig);

        CameraListHelper.addCameras(this,
                builder,
                cameraList,
                activeCameraInfo,
                videoSize);

        builder.setSurface(holder.getSurface());
        builder.setSurfaceSize(new Streamer.Size(binding.surfaceView.getWidth(), binding.surfaceView.getHeight()));
        builder.setDisplayRotation(displayRotation());
        final int videoOrientation = isPortrait() ? StreamerGL.Orientations.PORTRAIT : StreamerGL.Orientations.LANDSCAPE;
        builder.setVideoOrientation(videoOrientation);

        mStreamerGL = builder.build(Streamer.Mode.VIDEO_ONLY);

        if (mStreamerGL != null) {
            mStreamerGL.startVideoCapture();
            updatePreviewRatio();
            mOverlayLoader = new OverlayLoader(this);
            mOverlayLoader.setListener(this);
            mOverlayLoader.setVideoSize(videoSize);
            mOverlayLoader.loadActiveOnly();
        }
    }

    protected void releaseStreamer() {
        if (mStreamerGL == null) {
            return;
        }
        mStreamerGL.stopVideoCapture();
        mStreamerGL.release();
        mStreamerGL = null;
        if (mOverlayLoader != null) {
            mOverlayLoader.cancel(true);
            mOverlayLoader = null;
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

    private void updatePreviewRatio() {
        if (mStreamerGL == null) {
            return;
        }
        Streamer.Size size = mStreamerGL.getActiveCameraVideoSize();
        if (size == null) {
            return;
        }
        double ratio = isPortrait() ? size.getVerticalRatio() : size.getRatio();
        binding.previewAfl.setAspectRatio(ratio);
    }

    protected boolean isPortrait() {
        boolean portrait;
        if (!isInMultiWindowMode()) {
            final int orientation = getResources().getConfiguration().orientation;
            portrait = orientation == Configuration.ORIENTATION_PORTRAIT;
        } else {
            final Context app = getApplicationContext();
            WindowManager manager = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
            Display display = manager.getDefaultDisplay();
            DisplayMetrics metrics = new DisplayMetrics();
            display.getMetrics(metrics);
            int width = metrics.widthPixels;
            int height = metrics.heightPixels;
            portrait = height >= width;
        }
        return portrait;
    }

    @Override
    public void onImageLoadComplete(OverlayLoader source) {
        if (mStreamerGL != null) {
            HashMap<Long, StreamerGLBuilder.OverlayConfig> overlays = source.getOverlays();
            mStreamerGL.setOverlays(new ArrayList<>(overlays.values()));
        }
    }

    @Override
    public void onImageLoaded(String name) {

    }

    @Override
    public void onLoadError(String name, String error) {
        new Handler(Looper.getMainLooper()).post(() -> {
            String message = String.format(getString(R.string.layer_test_error), name, error);
            showToast(message);
        });
    }
}
