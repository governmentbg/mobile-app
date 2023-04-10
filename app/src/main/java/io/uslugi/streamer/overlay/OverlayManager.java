package io.uslugi.streamer.overlay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import io.uslugi.libcommon.sntp.SntpUpdater;

import com.wmspanel.libstream.Streamer;
import com.wmspanel.libstream.StreamerGL;
import com.wmspanel.libstream.StreamerGLBuilder;

import io.uslugi.streamer.data.ImageLayerConfig;
import io.uslugi.streamer.data.ImageLayerConfig_;
import io.uslugi.streamer.ObjectBox;
import io.uslugi.streamer.settingsutils.Settings;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;

import io.objectbox.Box;
import io.objectbox.query.QueryBuilder;

public class OverlayManager implements OverlayLoaderListener {
    public enum PauseMode {
        OFF,
        PRE_STANDBY, //State when we start encoding, but not streaming
        STANDBY,
        PAUSE
    }

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private OverlayLoader mOverlayLoader;
    private OverlayLoader mPauseLoader;
    private OverlayLoader mRefreshLoader;
    private HashMap<Long, StreamerGLBuilder.OverlayConfig> mOverlays;
    private HashMap<Long, StreamerGLBuilder.OverlayConfig> mPauseOverlays;
    private HashMap<Long, StreamerGLBuilder.OverlayConfig> mStandbyOverlays;
    private HashMap<Long, StreamerGLBuilder.OverlayConfig> mPreStandbyOverlays;

    private final HashMap<Long, Integer> mRefreshList = new HashMap<>();
    private final HashMap<Long, Long> mLastRefreshTimeList = new HashMap<>();

    private PauseMode mPauseMode = PauseMode.OFF;
    private PauseMode mPauseLoading = PauseMode.OFF;
    private final Streamer.Size mVideoSize;
    protected static final String TAG = "OverlayManager";
    private final WeakReference<StreamerGL> mWeakStreamerGL;
    private final WeakReference<Context> mWeakContext;
    private final boolean mStandbyEnabled;

    static public long BLACK_FRAME_ID = -1;
    static public long SHADE_FRAME_ID = -2;
    static public long PAUSE_FRAME_ID = -3;
    static public long STANDBY_FRAME_ID = -4;

    private HashMap<Long, StreamerGLBuilder.OverlayConfig> mPredefinedOverlays;

    private long mPreciseTimeOffset;

    public OverlayManager(StreamerGL streamer, Context context, Streamer.Size videoSize) {
        mWeakStreamerGL = new WeakReference<>(streamer);
        mWeakContext = new WeakReference<>(context);
        mOverlayLoader = createOverlayLoader(context, videoSize);
        mOverlayLoader.loadActiveOnly();
        mVideoSize = videoSize;
        mStandbyEnabled = Settings.standbyEnabled();
        if (mStandbyEnabled) {
            mPauseMode = PauseMode.PRE_STANDBY;
            loadPreStandbyOverlays(context);
        } else {
            loadPauseOverlays(context);
        }
        makeRefreshList();
        mRefreshLoader = createRefreshLoader(context, videoSize);
    }

    protected OverlayLoader createOverlayLoader(Context context, Streamer.Size videoSize) {
        OverlayLoader loader = new OverlayLoader(context);
        loader.setListener(this);
        boolean previewLayers = Settings.showPreviewLayers();
        loader.setDrawOnPreview(previewLayers);
        loader.setVideoSize(videoSize);
        return loader;
    }

    public void updateOverlayList(HashSet<Long> value) {
        Context context = mWeakContext.get();
        if (context == null) return;
        Long[] idList = new Long[value.size()];
        value.toArray(idList);
        if (mPauseMode != PauseMode.PRE_STANDBY) {
            mOverlayLoader.cancel(true);
            mOverlayLoader = createOverlayLoader(context, mVideoSize);
            mOverlayLoader.loadIdList(idList);
        } else {
            loadPreStandbyOverlays(context);
        }
    }

    public void setPauseMode(Context context, PauseMode mode) {
        StreamerGL streamer = mWeakStreamerGL.get();
        if (streamer == null) {
            return;
        }
        mPauseMode = mode;

        switch (mode) {
            case OFF:
                if (mOverlays != null) {
                    setOverlays(mOverlays);
                }
                break;
            case PAUSE:
                if (mPauseOverlays != null) {
                    setOverlays(mPauseOverlays);
                } else {
                    loadPauseOverlays(context);
                }
                break;
            case STANDBY:
                if (mStandbyOverlays != null) {
                    setOverlays(mStandbyOverlays);
                } else {
                    loadStandbyOverlays(context);
                }
                break;
            case PRE_STANDBY:
                if (mPreStandbyOverlays != null) {
                    setOverlays(mPreStandbyOverlays);
                }
                break;
        }
    }

    private void setOverlays(HashMap<Long, StreamerGLBuilder.OverlayConfig> overlays) {
        StreamerGL streamer = mWeakStreamerGL.get();
        if (streamer == null) {
            return;
        }
        streamer.setOverlays(new ArrayList<>(overlays.values()));
        maybeStartOverlayRefresh();
    }

    private void maybeStartOverlayRefresh() {
        Context context = mWeakContext.get();
        if (context == null) return;

        if (mRefreshList.size() > 0) {
            postRefreshOverlaysList(new ArrayList<>(mRefreshList.keySet()));
        }
    }

    private void makeRefreshList() {
        Context context = mWeakContext.get();
        if (context == null) return;
        mRefreshList.clear();

        List<Long> off = new ArrayList<>();

        final Box<ImageLayerConfig> layerBox = ObjectBox.get().boxFor(ImageLayerConfig.class);
        QueryBuilder<ImageLayerConfig> active = layerBox.query();
        long[] activeIds = active.equal(ImageLayerConfig_.active, true).build().findIds();
        for (long element : activeIds) {
            off.add(element);
        }

        QueryBuilder<ImageLayerConfig> query = layerBox.query();
        query.greater(ImageLayerConfig_.updateInterval, 0);

        List<ImageLayerConfig> refresh = query.build().find();
        if (refresh.size() > 0) {
            final long now = thisSecondPrecise();
            for (ImageLayerConfig config : refresh) {
                if (off.contains(config.id)) {
                    mRefreshList.put(config.id, config.updateInterval * 1000);
                    mLastRefreshTimeList.put(config.id, now);
                }
            }
            mHandler.postAtTime(this::findOffset, nextSecond());
        }
    }

    public PauseMode getPauseMode() {
        return mPauseMode;
    }

    public void cancel() {
        if (mOverlayLoader != null) {
            mOverlayLoader.cancel(false);
        }
        if (mPauseLoader != null) {
            mPauseLoader.cancel(false);
        }
        if (mRefreshLoader != null) {
            mRefreshLoader.cancel(true);
        }
        mHandler.removeCallbacksAndMessages(null);
    }

    public void onImageLoadComplete(OverlayLoader source) {

        mHandler.post(() -> {

            StreamerGL streamer = mWeakStreamerGL.get();
            if (streamer == null) {
                return;
            }
            if (source == mOverlayLoader) {
                mOverlays = source.getOverlays();
                if (mPauseMode == PauseMode.OFF) {
                    setOverlays(mOverlays);
                }
            } else if (source == mPauseLoader) {
                Context context = mWeakContext.get();
                if (context == null) return;
                HashMap<Long, StreamerGLBuilder.OverlayConfig> overlaysMap = getOverlays(source, mPauseLoading);
                if (mPauseMode == mPauseLoading) {
                    setOverlays(overlaysMap);
                }
                if (mPauseLoading == PauseMode.PAUSE) {
                    mPauseOverlays = overlaysMap;
                    mPauseLoading = PauseMode.OFF;
                } else if (mPauseLoading == PauseMode.STANDBY) {
                    mStandbyOverlays = overlaysMap;
                    loadPauseOverlays(context);
                }
                if (mPauseLoading == PauseMode.PRE_STANDBY) {
                    mPreStandbyOverlays = overlaysMap;
                    loadPauseOverlays(context);
                }
            } else if (source == mRefreshLoader) {
                copyAndSetOverlayList();
                mHandler.postAtTime(this::maybeRefreshOverlaysList, nextSecondPrecise());
            }
        });
    }

    private HashMap<Long, StreamerGLBuilder.OverlayConfig> getOverlays(OverlayLoader source, PauseMode mode) {
        HashMap<Long, StreamerGLBuilder.OverlayConfig> originOverlays = source.getOverlays();
        if (source == mOverlayLoader) {
            return originOverlays;
        }
        long[] predefinedId = null;
        switch (mode) {
            case STANDBY:
                predefinedId = new long[]{BLACK_FRAME_ID, SHADE_FRAME_ID, STANDBY_FRAME_ID};
                break;
            case PAUSE:
                if (mStandbyEnabled) {
                    predefinedId = new long[]{BLACK_FRAME_ID, SHADE_FRAME_ID, PAUSE_FRAME_ID};
                } else {
                    predefinedId = new long[]{PAUSE_FRAME_ID};
                }
                break;
            case PRE_STANDBY:
                predefinedId = new long[]{BLACK_FRAME_ID};

        }

        if (mPredefinedOverlays == null) {
            mPredefinedOverlays = getPredefined();
        }
        HashMap<Long, StreamerGLBuilder.OverlayConfig> result = new LinkedHashMap<>();
        if (predefinedId != null) {
            for (long id : predefinedId) {
                if (mPredefinedOverlays.containsKey(id)) {
                    result.put(id, mPredefinedOverlays.get(id));
                }
            }
        }
        result.putAll(originOverlays);
        return result;
    }

    private void loadPreStandbyOverlays(Context context) {
        final Box<ImageLayerConfig> layerBox = ObjectBox.get().boxFor(ImageLayerConfig.class);
        QueryBuilder<ImageLayerConfig> query = layerBox.query();
        query.equal(ImageLayerConfig_.active, true).order(ImageLayerConfig_.zIndex);

        if (mPauseLoader != null) {
            mPauseLoader.cancel(true);
        }
        mPauseLoader = createOverlayLoader(context, mVideoSize);
        mPauseLoader.setDrawOnPreview(false);
        mPauseLoading = PauseMode.PRE_STANDBY;
    }

    private void loadStandbyOverlays(Context context) {
        mPauseLoader = createOverlayLoader(context, mVideoSize);
        mPauseLoader.setDrawOnPreview(false);

        mPauseLoading = PauseMode.STANDBY;
    }

    private void loadPauseOverlays(Context context) {
        mPauseLoader = createOverlayLoader(context, mVideoSize);
        mPauseLoader.setDrawOnPreview(false);

        mPauseLoading = PauseMode.PAUSE;
    }

    private HashMap<Long, StreamerGLBuilder.OverlayConfig> getPredefined() {
        Log.i(TAG, "Creating bitmap");
        Context context = mWeakContext.get();
        if (context == null) return null;
        HashMap<Long, StreamerGLBuilder.OverlayConfig> result = new HashMap<>();
        int BLACK_FRAME_WIDTH = 64;
        int BLACK_FRAME_HEIGHT = 64;
        Bitmap blackFrame = Bitmap.createBitmap(BLACK_FRAME_WIDTH, BLACK_FRAME_HEIGHT, Bitmap.Config.ARGB_8888);
        if (blackFrame == null) {
            Log.e(TAG, "Create bitmap failed");
            return null;
        }
        blackFrame.eraseColor(Color.argb(255, 0, 0, 0));
        StreamerGLBuilder.OverlayConfig blackOverlay =
                new StreamerGLBuilder.OverlayConfig(blackFrame, 1f, 0f, 0f,
                        StreamerGLBuilder.OverlayConfig.ScaleMode.SCREEN_FILL, StreamerGLBuilder.OverlayConfig.PosMode.NORMALIZED,
                        StreamerGLBuilder.OverlayConfig.DRAW_ON_STREAM);
        result.put(BLACK_FRAME_ID, blackOverlay);

        Bitmap shadeFrame = Bitmap.createBitmap(BLACK_FRAME_WIDTH, BLACK_FRAME_HEIGHT, Bitmap.Config.ARGB_8888);
        if (shadeFrame == null) {
            Log.e(TAG, "Create bitmap failed");
            return null;
        }
        shadeFrame.eraseColor(Color.argb(128, 0, 0, 64));
        StreamerGLBuilder.OverlayConfig shadeOverlay =
                new StreamerGLBuilder.OverlayConfig(shadeFrame, 1f, 0.5f, 0.5f,
                        StreamerGLBuilder.OverlayConfig.ScaleMode.SCREEN_FILL, StreamerGLBuilder.OverlayConfig.PosMode.NORMALIZED,
                        StreamerGLBuilder.OverlayConfig.DRAW_ON_PREVIEW);
        result.put(SHADE_FRAME_ID, shadeOverlay);


        TextUtils textUtils = new TextUtils(context);
        textUtils.setFontSize(18);
        textUtils.setColor(android.graphics.Color.rgb(255, 128, 0));

        Bitmap pauseText = textUtils.createImageFromText("Paused");
        StreamerGLBuilder.OverlayConfig pauseOverlay =
                new StreamerGLBuilder.OverlayConfig(pauseText, 1f, 0.5f, 1.0f,
                        StreamerGLBuilder.OverlayConfig.ScaleMode.ORIGIN, StreamerGLBuilder.OverlayConfig.PosMode.NORMALIZED,
                        StreamerGLBuilder.OverlayConfig.DRAW_ON_PREVIEW);
        result.put(PAUSE_FRAME_ID, pauseOverlay);

        Bitmap standbyText = textUtils.createImageFromText("Standby");
        StreamerGLBuilder.OverlayConfig standbyOverlay =
                new StreamerGLBuilder.OverlayConfig(standbyText, 1f, 0.5f, 1.0f,
                        StreamerGLBuilder.OverlayConfig.ScaleMode.ORIGIN, StreamerGLBuilder.OverlayConfig.PosMode.NORMALIZED,
                        StreamerGLBuilder.OverlayConfig.DRAW_ON_PREVIEW);
        result.put(STANDBY_FRAME_ID, standbyOverlay);

        return result;
    }

    @Override
    public void onImageLoaded(String name) {
        //Log.i(TAG, "Layer " + name + " loaded");
    }

    @Override
    public void onLoadError(String name, String error) {
        Log.w(TAG, "Layer " + name + " failed: " + error);
    }

    private void maybeRefreshOverlaysList() {
        final long now = thisSecondPrecise();
        List<Long> idList = new ArrayList<>();
        for (Long id : mRefreshList.keySet()) {
            if (mLastRefreshTimeList.get(id) + mRefreshList.get(id) <= now) {
                idList.add(id);
                mLastRefreshTimeList.put(id, now);
            }
        }
        postRefreshOverlaysList(idList);
    }

    private void postRefreshOverlaysList(List<Long> idList) {
        Context context = mWeakContext.get();
        if (context == null) return;
        if (idList.size() > 0) {
            mHandler.post(() -> {
                if (mRefreshLoader != null) {
                    mRefreshLoader.cancel(true);
                }
                mRefreshLoader = createRefreshLoader(context, mRefreshLoader.getVideoSize());
                mRefreshLoader.loadIdList(idList.toArray(new Long[0]));
            });
        } else {
            mHandler.postAtTime(this::maybeRefreshOverlaysList, nextSecondPrecise());
        }
    }

    private void copyAndSetOverlayList() {
        copyRefreshedOverlaysList(mOverlays);
        copyRefreshedOverlaysList(mPreStandbyOverlays);
        copyRefreshedOverlaysList(mStandbyOverlays);
        copyRefreshedOverlaysList(mPauseOverlays);
        switch (mPauseMode) {
            case OFF:
                setRefreshedOverlaysList(mOverlays);
                break;
            case PRE_STANDBY:
                setRefreshedOverlaysList(mPreStandbyOverlays);
                break;
            case STANDBY:
                setRefreshedOverlaysList(mStandbyOverlays);
                break;
            case PAUSE:
                setRefreshedOverlaysList(mPauseOverlays);
                break;
        }
    }

    private void copyRefreshedOverlaysList(HashMap<Long, StreamerGLBuilder.OverlayConfig> activeOverlays) {
        if (activeOverlays == null) return;
        Context context = mWeakContext.get();
        if (context == null) return;
        HashMap<Long, StreamerGLBuilder.OverlayConfig> updOverlaysMap = mRefreshLoader.getOverlays();
        for (Long id : activeOverlays.keySet()) {
            if (updOverlaysMap.containsKey(id)) {
                activeOverlays.get(id).bitmap = updOverlaysMap.get(id).bitmap;
            }
        }
    }

    private void setRefreshedOverlaysList(HashMap<Long, StreamerGLBuilder.OverlayConfig> activeOverlays) {
        if (activeOverlays == null) return;
        Context context = mWeakContext.get();
        if (context == null) return;
        StreamerGL streamer = mWeakStreamerGL.get();
        if (streamer == null) return;
        HashMap<Long, StreamerGLBuilder.OverlayConfig> updOverlaysMap = mRefreshLoader.getOverlays();
        //Log.d(TAG, "Overlays updated: " + updOverlaysMap.size());
        long idx = 0;
        List<Integer> indices = new ArrayList<>();
        for (Long id : activeOverlays.keySet()) {
            if (updOverlaysMap.containsKey(id)) {
                indices.add((int) idx);
                //Log.d(TAG, "id=" + id);
            }
            idx++;
        }
        int[] upd = new int[indices.size()];
        for (int i = 0; i < indices.size(); i++) {
            upd[i] = indices.get(i);
            //Log.d(TAG, "upd[i]=" + upd[i]);
        }
        streamer.updateOverlays(upd);
    }

    protected OverlayLoader createRefreshLoader(Context context, Streamer.Size videoSize) {
        OverlayLoader loader = new OverlayLoader(context);
        loader.setListener(this);
        loader.setVideoSize(videoSize);
        loader.setLoadUpdatedOnly();
        return loader;
    }

    private long thisSecond() {
        return 1000 * (SystemClock.uptimeMillis() / 1000);
    }

    private long nextSecond() {
        final long now = SystemClock.uptimeMillis();

        //Log.d(TAG, millis(now) + " " + millis(next));

        return now + (1000 - now % 1000);
    }

    private long thisSecondPrecise() {
        return thisSecond() + mPreciseTimeOffset;
    }

    private long nextSecondPrecise() {
        return nextSecond() + mPreciseTimeOffset;
    }

    private void findOffset() {
        final long millisUp = SystemClock.uptimeMillis() % 1000;
        final long millisNtp = SntpUpdater.maybeGetTime().getTime() % 1000;

        //Log.d(TAG, "system=" + millis(millisUp) + " ntp=" + millis(millisNtp));

        if (millisUp > millisNtp) {
            mHandler.postAtTime(this::findOffset, nextSecond());
            return;
        }
        mPreciseTimeOffset = 1000 - (millisNtp - millisUp);
        mHandler.postAtTime(this::findOffset, nextSecond() + 60_000);
    }
}
