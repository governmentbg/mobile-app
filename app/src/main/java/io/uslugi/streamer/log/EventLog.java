package io.uslugi.streamer.log;

import static io.uslugi.streamer.helper.Constants.APP_NAME;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.annotation.NonNull;

import io.uslugi.streamer.R;
import com.wmspanel.libstream.Streamer;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class EventLog {
    private final String TAG = "EventLog";

    private final Object lock = new Object();

    public interface EventLogListener {
        void onEvent(String text);
    }

    private class Element {
        Date date = new Date();
        Streamer.LoggerListener.Severity severity;
        String tag;
        String message;

        @NonNull
        @Override
        public String toString() {
            return String.format(mLocale, mFmtLog, mDateFormat.format(date), message);
        }

        Element(Streamer.LoggerListener.Severity severity, String tag, String message) {
            this.severity = severity;
            this.tag = tag;
            this.message = message;
        }

        void toLogcat() {
            switch (severity) {
                case ERROR:
                    Log.e(tag, message);
                    break;
                case WARN:
                    Log.w(tag, message);
                    break;
                case INFO:
                default:
                    Log.i(tag, message);
                    break;
            }
        }

    }

    private final List<Element> mLog = new ArrayList<>();
    public static final int MAX_COUNT = 500;
    private final int MAX_COUNT_DECREMENT = 250;

    private Locale mLocale = new Locale("en", "US");
    private String mFmtLog = "%1$s %2$s";
    private DateFormat mDateFormat = DateFormat.getDateInstance();

    private String mSafUri;
    private ContentResolver mResolver;
    private boolean mWriteLogcat;

    private File mTempFile;
    private PrintWriter mTempWriter;

    private EventLogListener mListener;

    private static final EventLog instance = new EventLog();

    public static EventLog getInstance() {
        return instance;
    }

    public void init(Context context,
                     String safUri,
                     boolean writeLogcat) {
        final Configuration configuration = context.getResources().getConfiguration();
        mLocale = configuration.getLocales().get(0);
        mFmtLog = context.getString(R.string.fmt_log);
        mDateFormat = DateFormat.getTimeInstance(DateFormat.MEDIUM, mLocale);
        mSafUri = safUri;
        mResolver = context.getContentResolver();
        mWriteLogcat = writeLogcat;
        mLog.clear();
        synchronized (lock) {
            openTempFile(context);
        }
    }

    public void close() {
        synchronized (lock) {
            if (mTempWriter != null) {
                mTempWriter.close();
                mTempWriter = null;
                copyToSAF();
            }
            if (mTempFile != null) {
                mTempFile.delete();
            }
        }
    }

    private void openTempFile(Context context) {
        try {
            mTempFile = new File(context.getCacheDir(), "log.tmp");
            if (mTempFile.exists()) {
                copyToSAF();
                mTempFile.delete();
            }
            if (mSafUri != null && mTempFile.createNewFile()) {
                mTempWriter = new PrintWriter(new FileOutputStream(mTempFile));
            }
        } catch (IOException ignore) {
        }
    }

    private void copyToSAF() {
        if (mSafUri == null || mResolver == null || mTempFile == null) {
            return;
        }

        String date = new SimpleDateFormat("yyyyMMdd_HHmmss_SS", Locale.US).format(new Date());
        String filename = APP_NAME + "-" + date;

        try {
            final Uri treeUri = Uri.parse(mSafUri);
            final String documentId = DocumentsContract.getTreeDocumentId(treeUri);
            final Uri docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId);
            if (docUri != null) {
                final Uri recordUri = DocumentsContract.createDocument(mResolver, docUri, "text/plain", filename);
                if (recordUri != null) {
                    final ParcelFileDescriptor parcel = mResolver.openFileDescriptor(recordUri, "rw");
                    if (parcel != null) {
                        final FileDescriptor fd = parcel.getFileDescriptor();
                        if (fd != null) {
                            try (FileInputStream is = new FileInputStream(mTempFile);
                                 FileOutputStream os = new FileOutputStream(fd)) {
                                byte[] buf = new byte[512 * 1024];
                                for (int len; (len = is.read(buf)) > 0; ) {
                                    os.write(buf, 0, len);
                                }
                            } catch (IOException ignored) {
                            }
                        }
                    }
                }
            }
        } catch (IOException | IllegalArgumentException | IllegalStateException | SecurityException
                | UnsupportedOperationException e) {
            Log.e(TAG, Log.getStackTraceString(e));
        }
    }

    public void put(Streamer.LoggerListener.Severity severity, String message) {
        put(severity, "EventLog", message);
    }

    public void put(Streamer.LoggerListener.Severity severity, String tag, String message) {
        synchronized (lock) {
            final Element item = new Element(severity, tag, message);
            if (mLog.size() >= MAX_COUNT) {
                mLog.subList(0, MAX_COUNT_DECREMENT).clear();
            }
            mLog.add(item);
            if (mTempWriter != null) {
                mTempWriter.println(item);
                mTempWriter.checkError();
            }
            if (mWriteLogcat) {
                item.toLogcat();
            }
            if (mListener != null) {
                mListener.onEvent(item.toString());
            }
        }
    }

    public void setListener(EventLogListener listener) {
        mListener = listener;
    }

    @NonNull
    @Override
    public String toString() {
        synchronized (lock) {
            final StringBuilder sb = new StringBuilder();
            for (Element element : mLog) {
                sb.append(element).append("\n");
            }
            return sb.toString();
        }
    }

    public static void Logd(String TAG, String message) {
        EventLog.getInstance().put(Streamer.LoggerListener.Severity.INFO, TAG, message);
    }
}
