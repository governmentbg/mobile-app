package io.uslugi.streamer.settingsutils;

import static io.uslugi.streamer.helper.Constants.Config.Advanced.DO_NOT_CHECK_NETWORK_PRESENCE;
import static io.uslugi.streamer.helper.Constants.Config.Connection.IDLE_TIMEOUT;
import static io.uslugi.streamer.helper.Constants.Config.Connection.MAX_CONNECTIONS;
import static io.uslugi.streamer.helper.Constants.Config.Connection.RECONNECT_TIMEOUT;
import static io.uslugi.streamer.helper.Constants.Config.Connection.RECONNECT_TIMEOUT_NO_NETWORK;

import android.content.Context;

import io.uslugi.libcommon.PlatformUtils;
import io.uslugi.streamer.data.Connection;
import io.uslugi.streamer.data.Connection_;
import io.uslugi.streamer.ObjectBox;

import java.util.List;

import io.objectbox.Box;

public class ConnectivitySettings {

    public static int connMax() {
        return MAX_CONNECTIONS;
    }

    public static List<Connection> connections() {
        final Box<Connection> connectionBox = ObjectBox.get().boxFor(Connection.class);
        return connectionBox.query()
                .equal(Connection_.active, true)
                .order(Connection_.name)
                .build()
                .find(0, connMax());
    }

    // Advanced options / Don't check network connection
    public static boolean isConnected(final Context context) {
        if (DO_NOT_CHECK_NETWORK_PRESENCE) {
            return true;
        }
        return PlatformUtils.isConnected(context);
    }

    public static int idleTimeoutMs() {
        return timeoutMs(IDLE_TIMEOUT);
    }

    public static int retryTimeoutMs(final Context context) {
        return isConnected(context) ? reconnectTimeoutMs() : reconnectTimeoutNoNwMs();
    }

    private static int reconnectTimeoutMs() {
        return timeoutMs(RECONNECT_TIMEOUT);
    }

    private static int reconnectTimeoutNoNwMs() {
        return timeoutMs(RECONNECT_TIMEOUT_NO_NETWORK);
    }

    private static int timeoutMs(int seconds) {
        return seconds * 1000;
    }
}
