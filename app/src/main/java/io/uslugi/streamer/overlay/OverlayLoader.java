package io.uslugi.streamer.overlay;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.Layout;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Log;

import androidx.core.text.HtmlCompat;

import io.uslugi.libcommon.sntp.SntpUpdater;

import io.uslugi.streamer.R;
import com.wmspanel.libstream.Streamer;
import com.wmspanel.libstream.StreamerGLBuilder;
import io.uslugi.streamer.data.ImageLayerConfig;
import io.uslugi.streamer.data.ImageLayerConfig_;
import io.uslugi.streamer.ObjectBox;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import io.objectbox.Box;
import io.objectbox.query.QueryBuilder;

public class OverlayLoader extends AsyncTask<Long, Integer, Long> {


    static final long MAX_DOWNLOAD_SIZE = 10_000_000;
    static final long MAX_BITMAP_SIZE = 40_000_000;
    static final int BUFFER_SIZE = 64 * 1024;
    static private final String CONTENT_TYPE = "Content-Type";
    static private final String CONTENT_LENGTH = "Content-Length";
    static private final String IF_NONE_MATCH = "If-None-Match";
    static private final String ETAG = "Etag";
    static private final String LAST_MODIFIED = "Last-Modified";
    static private final String LAST_MODIFIED_DATE_PATTERN = "EEE, dd MMM yyyy HH:mm:ss zzz";
    static private final String IF_MODIFIED_SINCE = "If-Modified-Since";
    static private final String overlaysFolder = "overlays";

    static private final HashMap<Long, Bitmap> mBitmapCache = new HashMap<>();
    private OverlayLoaderListener mListener;
    private final WeakReference<Context> mWeakContext;
    private Streamer.Size mVideoSize = new Streamer.Size(1920, 1080);
    private static final String TAG = "OverlayLoader";
    private final HashMap<Long, StreamerGLBuilder.OverlayConfig> mStreamerOverlays = new LinkedHashMap<>();
    private int mOverlayFlags;
    private boolean mLoadUpdatedOnly = false;

    public OverlayLoader(Context context) {
        mWeakContext = new WeakReference<>(context);
        mOverlayFlags = StreamerGLBuilder.OverlayConfig.DEFAULT_FLAGS;
    }

    public void setListener(OverlayLoaderListener listener) {
        mListener = listener;
    }

    public void setLoadUpdatedOnly() {
        mLoadUpdatedOnly = true;
    }

    // Get list of overlays including predefined
    HashMap<Long, StreamerGLBuilder.OverlayConfig> getOverlays() {
        return mStreamerOverlays;
    }

    public Streamer.Size getVideoSize() {
        return mVideoSize;
    }

    void setVideoSize(Streamer.Size videoSize) {
        mVideoSize = videoSize;
    }

    void setDrawOnPreview(boolean enable) {
        if (enable) {
            mOverlayFlags = mOverlayFlags | StreamerGLBuilder.OverlayConfig.DRAW_ON_PREVIEW;
        } else {
            mOverlayFlags = mOverlayFlags & ~StreamerGLBuilder.OverlayConfig.DRAW_ON_PREVIEW;
        }
    }

    @Override
    protected Long doInBackground(Long... id) {
        long[] idList = new long[id.length];
        for (int i = 0; i < id.length; i++) {
            idList[i] = id[i];
        }

        final Box<ImageLayerConfig> layerBox = ObjectBox.get().boxFor(ImageLayerConfig.class);
        QueryBuilder<ImageLayerConfig> query = layerBox.query();
        query.in(ImageLayerConfig_.id, idList).order(ImageLayerConfig_.zIndex);
        List<ImageLayerConfig> active = query.build().find();
        loadList(active);

        return null;
    }

    @Override
    protected void onCancelled(Long aLong) {
        mWeakContext.clear();
    }

    public void loadActiveOnly() {
        final Box<ImageLayerConfig> layerBox = ObjectBox.get().boxFor(ImageLayerConfig.class);
        QueryBuilder<ImageLayerConfig> query = layerBox.query();
        query.equal(ImageLayerConfig_.active, true).order(ImageLayerConfig_.zIndex);
        List<ImageLayerConfig> active = query.build().find();

        Long[] idList = new Long[active.size()];
        for (int i = 0; i < active.size(); i++) {
            idList[i] = active.get(i).id;
        }
        loadIdList(idList);
    }

    public void loadIdList(Long[] list) {
        execute(list);
    }

    private void loadList(List<ImageLayerConfig> list) {
        if (mWeakContext.get() == null) return;
        mStreamerOverlays.clear();
        for (ImageLayerConfig config : list) {
            boolean loaded = false;
            if (config.type == ImageLayerType.TEXT.ordinal()) {
                loaded = loadHtml(config);
            } else {
                String urlStr = config.url;
                if (android.text.TextUtils.isEmpty(urlStr)) {
                    continue;
                }
                Uri url = Uri.parse(urlStr);
                String scheme = url.getScheme();
                if (scheme == null) {
                    continue;
                }
                if ("content".equals(scheme)) {
                    if (!mLoadUpdatedOnly) {
                        loaded = loadLocalFile(url, config);
                    }
                } else {
                    loaded = loadRemoteFile(url, config);
                }
            }
            if (isCancelled()) {
                break;
            }
            if (loaded && mListener != null) {
                mListener.onImageLoaded(config.name);
            }
        }
        if (mListener != null && !isCancelled()) {
            mListener.onImageLoadComplete(this);
        }
    }

    private String parseTags(String original) {
        StringBuilder output = new StringBuilder();
        Pattern pattern = Pattern.compile("<%(.*?)%>");
        Matcher matcher = pattern.matcher(original);
        //Log.d(TAG, original);
        int lastIndex = 0;
        while (matcher.find()) {
            output.append(original, lastIndex, matcher.start())
                    .append(evaluate(matcher.group(1)));
            lastIndex = matcher.end();
        }
        if (lastIndex < original.length()) {
            output.append(original, lastIndex, original.length());
        }
        //Log.d(TAG, output.toString());
        return output.toString();
    }

    private String evaluate(String original) {
        StringBuilder output = new StringBuilder();
        Pattern pattern = Pattern.compile("date\\((.*?)\\)");
        Matcher matcher = pattern.matcher(original);
        String format = "yyyy-mm-dd hh:mm:ss";
        Locale locale = Locale.getDefault();
        if (matcher.find()) {
            String all = matcher.group(1);
            if (!all.equals("")) {
                Pattern patternParam = Pattern.compile("[\"'](.*?)[\"']");
                Matcher matcherParam = patternParam.matcher(all);
                while (matcherParam.find()) {
                    String value = matcherParam.group(1).trim();
                    try {
                        format = value;
                    } catch (IllegalArgumentException ignored) {
                        Locale l = parseLocale(value.trim());
                        if (l != null) {
                            //Log.d(TAG, "Found locale quoted " + l);
                            locale = l;
                        }
                    }
                }
                String[] items = all.split("\\s*,\\s*");
                for (String item : items) {
                    Locale l = parseLocale(item.trim());
                    if (l != null) {
                        //Log.d(TAG, "Found locale " + l);
                        locale = l;
                    }
                }
            }
        }
        Date date = SntpUpdater.maybeGetTime();
        DateFormat dateFormat = new SimpleDateFormat(format, locale);
        output.append(dateFormat.format(date));
        return output.toString();
    }

    private static Locale parseLocale(String str) {
        Locale l = null;
        try {
            l = toLocale(str);
        } catch (IllegalArgumentException ignored) {
        }
        return l;
    }

    private static Locale toLocale(String str) {
        if (str == null) {
            return null;
        }
        int len = str.length();
        if (len != 2 && len != 5 && len < 7) {
            throw new IllegalArgumentException("Invalid locale format: " + str);
        }
        char ch0 = str.charAt(0);
        char ch1 = str.charAt(1);
        if (ch0 < 'a' || ch0 > 'z' || ch1 < 'a' || ch1 > 'z') {
            throw new IllegalArgumentException("Invalid locale format: " + str);
        }
        if (len == 2) {
            return new Locale(str, "");
        } else {
            if (str.charAt(2) != '_') {
                throw new IllegalArgumentException("Invalid locale format: " + str);
            }
            char ch3 = str.charAt(3);
            if (ch3 == '_') {
                return new Locale(str.substring(0, 2), "", str.substring(4));
            }
            char ch4 = str.charAt(4);
            if (ch3 < 'A' || ch3 > 'Z' || ch4 < 'A' || ch4 > 'Z') {
                throw new IllegalArgumentException("Invalid locale format: " + str);
            }
            if (len == 5) {
                return new Locale(str.substring(0, 2), str.substring(3, 5));
            } else {
                if (str.charAt(5) != '_') {
                    throw new IllegalArgumentException("Invalid locale format: " + str);
                }
                return new Locale(str.substring(0, 2), str.substring(3, 5), str.substring(6));
            }
        }
    }

    private boolean loadHtml(ImageLayerConfig config) {
        if (android.text.TextUtils.isEmpty(config.cacheUrl)) {
            return false;
        }
        Spanned text = HtmlCompat.fromHtml(parseTags(config.cacheUrl), HtmlCompat.FROM_HTML_MODE_LEGACY);

        final int fontSize = 16;
        final float scale = 2.75f; // xxhdpi
        TextPaint paint = new TextPaint();
        paint.setTextSize((int) (fontSize * scale));

        StaticLayout tempLayout = new StaticLayout(text, paint, 10000,
                android.text.Layout.Alignment.ALIGN_NORMAL, 1f, 0f, true);
        int lineCount = tempLayout.getLineCount();
        float textWidth = 0;
        for (int i = 0; i < lineCount; i++) {
            float lineWidth = tempLayout.getLineWidth(i);
            if (lineWidth > textWidth) {
                textWidth = lineWidth;
            }
        }

        int w = (int) textWidth + 1;

        StaticLayout layout;
        StaticLayout.Builder sb = StaticLayout.Builder.obtain(text, 0, text.length(), paint, w)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false);
        layout = sb.build();

        Bitmap picture = Bitmap.createBitmap(layout.getWidth(), layout.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(picture);
        layout.draw(canvas);

        StreamerGLBuilder.OverlayConfig overlay = createOverlay(picture, config);
        mStreamerOverlays.put(config.id, overlay);
        return true;
    }

    @Nullable
    synchronized static Bitmap getFromCache(long id) {
        if (mBitmapCache.containsKey(id)) {
            return mBitmapCache.get(id);
        }
        return null;
    }

    synchronized static void putToMemoryCache(long id, Bitmap image) {
        mBitmapCache.put(id, image);
    }

    synchronized static void removeFromMemoryCache(long id) {
        mBitmapCache.remove(id);
    }

    private boolean loadMemoryCachedFile(ImageLayerConfig config) {
        Bitmap cached = getFromCache(config.id);
        if (cached != null) {
            StreamerGLBuilder.OverlayConfig overlay = createOverlay(cached, config);
            mStreamerOverlays.put(config.id, overlay);
            //Log.d(TAG, "Loaded from memory cache:" + config.id);
            return true;
        }
        return false;
    }

    private boolean loadDiskCachedFile(ImageLayerConfig config) {
        if (isDiskCachedFileExist(config)) {
            final Uri cacheUri = Uri.parse(config.cacheUrl);
            //Log.d(TAG, "Loaded from disk cache:" + config.id);
            return loadLocalFile(cacheUri, config);
        }
        return false;
    }

    private boolean loadCachedFile(ImageLayerConfig config) {
        if (loadMemoryCachedFile(config)) {
            return true;
        }
        return loadDiskCachedFile(config);
    }

    private boolean loadLocalFile(Uri url, ImageLayerConfig config) {
        final Context context = mWeakContext.get();
        if (isCancelled() || context == null) {
            return false;
        }
        if (loadMemoryCachedFile(config)) {
            return true;
        }
        final ContentResolver resolver = context.getContentResolver();
        try {
            InputStream file;
            if ("content".equals(url.getScheme())) {
                file = resolver.openInputStream(url);
            } else {
                file = new FileInputStream(url.toString());
            }
            if (file == null) {
                if (mListener != null) {
                    mListener.onLoadError(config.name, context.getString(R.string.layer_error_invalid_url));
                }
                return false;

            }
            Bitmap picture = BitmapFactory.decodeStream(file);
            file.close();
            long size = picture.getByteCount();
            if (size > MAX_BITMAP_SIZE) {
                if (mListener != null) {
                    mListener.onLoadError(config.name, context.getString(R.string.layer_error_too_large));
                }
                return false;
            }
            if (picture == null) {
                Log.e(TAG, "Failed to load image");
                if (mListener != null) {
                    mListener.onLoadError(config.name, context.getString(R.string.layer_error_bad_image));
                }
                return false;
            }
            StreamerGLBuilder.OverlayConfig overlay = createOverlay(picture, config);
            mStreamerOverlays.put(config.id, overlay);
            putToMemoryCache(config.id, picture);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to open file");
            if (mListener != null) {
                mListener.onLoadError(config.name, "Open error: " + e.getLocalizedMessage());
            }
        }
        return false;
    }

    private StreamerGLBuilder.OverlayConfig createOverlay(Bitmap picture, ImageLayerConfig config) {
        float scale = config.displaySize;
        StreamerGLBuilder.OverlayConfig.ScaleMode scaleMode = StreamerGLBuilder.OverlayConfig.ScaleMode.SCREEN_FILL;
        final StreamerGLBuilder.OverlayConfig.PosMode posMode = StreamerGLBuilder.OverlayConfig.PosMode.NORMALIZED;

        if (scale == 0.0f) {
            scaleMode = StreamerGLBuilder.OverlayConfig.ScaleMode.ORIGIN;
            scale = 1.0f;
        }
        float posX = config.displayPosX;
        float posY = 1.0f - config.displayPosY;
        int flags = mOverlayFlags;

        return new StreamerGLBuilder.OverlayConfig(picture, scale, posX, posY, scaleMode, posMode, flags);
    }

    private boolean isDiskCachedFileExist(ImageLayerConfig config) {
//        return false;
        String localPath = config.cacheUrl;
        if (localPath == null) {
            return false;
        }
        File tmp = new File(localPath);
        return tmp.canRead();
    }

    private boolean loadRemoteFile(Uri uri, ImageLayerConfig config) {
        final Context context = mWeakContext.get();

        if (isCancelled() || context == null) {
            return false;
        }

        if (!mLoadUpdatedOnly && loadCachedFile(config)) {
            return true;
        }

        URL remoteUrl;
        try {
            remoteUrl = new URL(uri.toString());
        } catch (MalformedURLException e) {
            if (mListener != null) {
                mListener.onLoadError(config.name, context.getString(R.string.layer_error_invalid_url));
            }
            return false;
        }
        final String downloadedFile = downloadFile(remoteUrl, config);

        if (downloadedFile == null) {
            return false;
        }
        config.cacheUrl = downloadedFile;
        updateRecord(config);

        Uri localUrl = Uri.parse(downloadedFile);
        return loadLocalFile(localUrl, config);
    }

    private String downloadFile(URL remoteUrl, ImageLayerConfig config) {
        final Context context = mWeakContext.get();
        if (isCancelled() || context == null) {
            return null;
        }
        try {
            HttpURLConnection urlConnection = (HttpURLConnection) remoteUrl.openConnection();
            if (config.httpEtag != null) {
                urlConnection.setRequestProperty(IF_NONE_MATCH, "\"" + config.httpEtag + "\"");
            }
            // When used in combination with If-None-Match, it is ignored,
            // unless the server doesn't support If-None-Match.
            if (config.httpLastModified != null) {
                urlConnection.setRequestProperty(IF_MODIFIED_SINCE, config.httpLastModified);
            }
            urlConnection.setConnectTimeout(3_000);
            int code = urlConnection.getResponseCode();
            if (code >= 400) {
                String error = String.format(context.getString(R.string.layer_error_server_status), code);
                if (mListener != null) {
                    mListener.onLoadError(config.name, error);
                }
                urlConnection.disconnect();
                return null;
            }
            String error = validateHeader(urlConnection);
            if (error != null) {
                if (mListener != null) {
                    mListener.onLoadError(config.name, error);
                }
                return null;
            }

            boolean isUpdated = isUpdated(urlConnection, config);
            if (mLoadUpdatedOnly && !isUpdated) {
                return null;
            }

            String filename = getFileName(remoteUrl, urlConnection);

            InputStream dataIn = urlConnection.getInputStream();
            File outDir = new File(context.getCacheDir(), overlaysFolder);
            if (!outDir.exists()) {
                boolean created = outDir.mkdirs();
                if (!created) {
                    Log.e(TAG, "Failed to create directory " + outDir.getPath());
                    if (mListener != null) {
                        mListener.onLoadError(config.name, context.getString(R.string.layer_error_save_failed));
                    }
                }
            }
            removeFromMemoryCache(config.id);
            File outFile = new File(outDir, filename);
            if (outFile.exists()) {
                //Log.d(TAG, "Delete cache=" + outFile.getAbsolutePath());
                outFile.delete();
            }
            String outPath = outFile.getAbsolutePath();
            FileOutputStream dataOut = new FileOutputStream(outFile);
            int readSize;
            final byte[] buffer = new byte[BUFFER_SIZE];
            do {
                readSize = dataIn.read(buffer, 0, BUFFER_SIZE);
                if (readSize > 0) {
                    dataOut.write(buffer, 0, readSize);
                }
            } while (readSize >= 0 && !isCancelled());
            dataOut.close();
            dataIn.close();
            if (isCancelled()) {
                if (!outFile.delete()) {
                    Log.w(TAG, "Delete failed " + outPath);
                }
                outPath = null;
            }
            urlConnection.disconnect();
            return outPath;
        } catch (IOException e) {
            Log.e(TAG, "Failed to download file:" + e.getMessage());
            if (mListener != null) {
                mListener.onLoadError(config.name, context.getString(R.string.layer_error_download_failed));
            }
        }
        return null;
    }

    private @Nullable
    String validateHeader(HttpURLConnection urlConnection) {
        final Context context = mWeakContext.get();
        if (isCancelled() || context == null) {
            return null;
        }

        final String contentType = urlConnection.getHeaderField(CONTENT_TYPE);
        if (contentType == null || !contentType.startsWith("image/")) {
            Log.d(TAG, CONTENT_TYPE + ": " + contentType);
            return context.getString(R.string.layer_error_mime_type);
        }
        final String sizeStr = urlConnection.getHeaderField(CONTENT_LENGTH);
        long size = 0;
        if (sizeStr != null) {
            try {
                size = Long.parseLong(sizeStr);
            } catch (NumberFormatException e) {
                size = Long.MAX_VALUE;
            }
        }
        if (size > MAX_DOWNLOAD_SIZE) {
            return context.getString(R.string.layer_error_too_large);
        }
        return null;
    }

    private boolean isUpdated(HttpURLConnection urlConnection, ImageLayerConfig config) throws IOException {
        //Log.d(TAG, "isUpdated? " + config.name + " id=" + config.id);

        int code = urlConnection.getResponseCode();
        //Log.d(TAG, "HTTP code=" + code);
        if (code == 304) {
            //Log.d(TAG, "HTTP 304 Not Modified");
            return false;
        }

        final String etag = urlConnection.getHeaderField(ETAG);
        //Log.d(TAG, "Stored etag=" + config.httpEtag + " etag=" + etag);
        if (etag != null) {
            if (config.httpEtag == null) {
                config.httpEtag = etag;
                updateRecord(config);
            } else {
                if (etag.equals(config.httpEtag)) {
                    return false;
                }
                config.httpEtag = etag;
            }
        }

        final String lastModified = urlConnection.getHeaderField(LAST_MODIFIED);
        //Log.d(TAG, "Last-Modified=" + lastModified);
        Date d = httpLastModifiedDate(lastModified);
        if (d != null) {
            if (config.httpLastModified == null) {
                config.httpLastModified = lastModified;
                updateRecord(config);
            } else {
                Date prev = httpLastModifiedDate(config.httpLastModified);
                if (d.getTime() == prev.getTime()) {
                    return false;
                }
                config.httpLastModified = lastModified;
            }
        }
        return true;
    }

    private Date httpLastModifiedDate(String lastModified) {
        Date d = null;
        if (lastModified != null) {
            SimpleDateFormat format = new SimpleDateFormat(LAST_MODIFIED_DATE_PATTERN);
            try {
                d = format.parse(lastModified);
            } catch (ParseException ignored) {
            }
        }
        return d;
    }

    private String getFileName(URL remoteUrl, HttpURLConnection urlConnection) {
        String filename = null;
        final String filePath = remoteUrl.getFile();
        if (filePath != null) {
            int slashPos = filePath.lastIndexOf("/");
            if (slashPos > 0) {
                filename = filePath.substring(slashPos + 1);
            }
        }
        if (filename == null || filename.isEmpty()) {
            final String disposition = urlConnection.getHeaderField("Content-Disposition");
            if (disposition != null) {
                filename = disposition.replaceFirst("(?i)^.*filename=\"?([^\"]+)\"?.*$", "$1");
            }
        }
        if (filename == null) {
            filename = UUID.randomUUID().toString();
            final String contentType = urlConnection.getHeaderField(CONTENT_TYPE);
            final int slashPos = contentType.indexOf("/");
            if (slashPos > 0) {
                final String ext = contentType.substring(slashPos + 1);
                filename = String.format("%s.%s", filename, ext);
            }
        }
        return filename;
    }

    private void updateRecord(ImageLayerConfig record) {
        final Box<ImageLayerConfig> box = ObjectBox.get().boxFor(ImageLayerConfig.class);
        box.put(record);
    }

}
