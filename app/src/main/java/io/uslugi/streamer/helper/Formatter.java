package io.uslugi.streamer.helper;

import android.content.Context;
import android.content.res.Configuration;

import io.uslugi.libcommon.UriResult;
import com.wmspanel.libstream.Streamer;

import io.uslugi.streamer.R;
import io.uslugi.streamer.data.Connection;
import io.uslugi.streamer.log.EventLog;

import org.json.JSONObject;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class Formatter {

    private static final Map<UriResult.Error, Integer> URI_ERROR_MAP = createUriErrorMap();

    private static Map<UriResult.Error, Integer> createUriErrorMap() {
        return Map.of(UriResult.Error.MISSING_URI, R.string.missing_uri, UriResult.Error.MISSING_HOST, R.string.missing_host, UriResult.Error.MISSING_SCHEME, R.string.missing_scheme, UriResult.Error.MISSING_APP_STREAM, R.string.missing_app_stream, UriResult.Error.MISSING_PORT, R.string.missing_port, UriResult.Error.STREAMID_FOUND, R.string.streamid_found, UriResult.Error.USERINFO_FOUND, R.string.userinfo_found);
    }

    private final Locale mLocale;

    public Formatter(final Context context) {
        final Configuration configuration = context.getResources().getConfiguration();
        mLocale = configuration.getLocales().get(0);
    }

    public String timeToString(final long time) {
        final long ss = time % 60;
        final long mm = (time / 60) % 60;
        final long hh = time / 3600;
        return String.format(mLocale, "%02d:%02d:%02d", hh, mm, ss);
    }

    public String countdownToString(final long timeMillis) {
        final long time = TimeUnit.MILLISECONDS.toSeconds(timeMillis);
        final long ss = time % 60;
        final long mm = (time / 60) % 60;
        return String.format(mLocale, "%02d:%02d", mm, ss);
    }

    public String trafficToString(final long bytes) {
        if (bytes < 1024) {
            // B
            return String.format(mLocale, "%4dB", bytes);

        } else if (bytes < 1024 * 1024) {
            // KB
            return String.format(mLocale, "%3.1fKB", (double) bytes / 1024);

        } else if (bytes < 1024 * 1024 * 1024) {
            // MB
            return String.format(mLocale, "%3.1fMB", (double) bytes / (1024 * 1024));
        } else {
            // GB
            return String.format(mLocale, "%3.1fGB", (double) bytes / (1024 * 1024 * 1024));
        }
    }

    public String bandwidthToString(final long bps) {
        if (bps < 1000) {
            // bps
            return String.format(mLocale, "%4dbps", bps);

        } else if (bps < 1000 * 1000) {
            // Kbps
            return String.format(mLocale, "%3.1fKbps", (double) bps / 1000);

        } else if (bps < 1000 * 1000 * 1000) {
            // Mbps
            return String.format(mLocale, "%3.1fMbps", (double) bps / (1000 * 1000));

        } else {
            // Gbps
            return String.format(mLocale, "%3.1fGbps", (double) bps / (1000 * 1000 * 1000));
        }
    }

    public static String getMessage(final Context ctx,
                                    final UriResult uriResult) {

        if (uriResult.error == UriResult.Error.INVALID_URI
                && uriResult.syntaxException != null) {
            return uriResult.syntaxException.getMessage();
        }

        if (uriResult.error == UriResult.Error.UNSUPPORTED_SCHEME) {
            final int resId = uriResult.isPlayback ?
                    R.string.unsupported_talkback_scheme
                    : R.string.unsupported_scheme;
            return ctx.getString(resId, uriResult.scheme);
        }

        final Integer id = URI_ERROR_MAP.get(uriResult.error);
        final int resId = id == null ? R.string.invalid_uri : id;
        return ctx.getString(resId);
    }

    public static String getMessage(final Context ctx,
                                    final Connection connection,
                                    final Streamer.Status status,
                                    final JSONObject info,
                                    final int retryTimeout) {
        String msg;

        if (status == Streamer.Status.CONN_FAIL) {
            msg = ctx.getString(R.string.connection_status_fail, connection.name);

        } else if (status == Streamer.Status.AUTH_FAIL) {
            final String details = info.toString();

            boolean badType = false;
            if (details.contains("authmod=adobe")
                    && connection.auth != Streamer.Auth.RTMP.ordinal()
                    && connection.auth != Streamer.Auth.AKAMAI.ordinal()) {
                badType = true;
            } else if (details.contains("authmod=llnw")
                    && connection.auth != Streamer.Auth.LLNW.ordinal()) {
                badType = true;
            }

            if (badType) {
                msg = ctx.getString(R.string.connection_status, connection.name, ctx.getString(R.string.unsupported_auth));
            } else {
                msg = ctx.getString(R.string.connection_status_auth_fail, connection.name);
            }

        } else if (status == Streamer.Status.TIMEOUT) {
            msg = ctx.getString(R.string.connection_status_timeout, connection.name);

        } else {
            if (info.length() == 0) {
                msg = ctx.getString(R.string.connection_status_unknown_fail, connection.name);
            } else {
                msg = ctx.getString(R.string.connection_status_fail_with_info, connection.name, info.toString());
            }
        }

        if (status != Streamer.Status.AUTH_FAIL && retryTimeout > 0) {
            final int timeout = (int) TimeUnit.MILLISECONDS.toSeconds(retryTimeout);
            msg = ctx.getString(R.string.connection_status_retrying, msg, timeout);
        }

        EventLog.getInstance().put(Streamer.LoggerListener.Severity.ERROR, msg);

        return msg;
    }

}
