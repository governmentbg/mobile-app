package io.uslugi.streamer;

import com.wmspanel.libstream.ConnectionConfig;
import com.wmspanel.libstream.RistConfig;
import com.wmspanel.libstream.SrtConfig;
import com.wmspanel.libstream.Streamer;

import io.uslugi.streamer.data.Connection;
import io.uslugi.streamer.settingsutils.ConnectivitySettings;

import java.net.URI;
import java.net.URISyntaxException;

import inet.ipaddr.HostName;
import inet.ipaddr.IPAddress;

public class ConnectionHelper {

    public static Connection newConnection(final String name,
                                           final String url) {
        final Connection connection = new Connection();
        initConnection(connection, name, url);

        return connection;
    }

    private static void initConnection(final Connection connection,
                                       final String name,
                                       final String url) {
        connection.name = name;
        connection.url = url;
        connection.pbkeylen = 16;
        connection.latency = 2000;
        connection.ristProfile = RistConfig.RistProfile.MAIN.ordinal();
    }

    public static ConnectionConfig toConnectionConfig(final Connection connection) {
        final ConnectionConfig config = new ConnectionConfig();

        config.uri = connection.url;
        config.mode = Streamer.Mode.values()[connection.mode];
        config.auth = Streamer.Auth.values()[connection.auth];
        config.username = connection.username;
        config.password = connection.password;
        config.idleTimeoutMs = ConnectivitySettings.idleTimeoutMs();

        return config;
    }

    public static SrtConfig toSrtConfig(final Connection connection) throws URISyntaxException {
        final SrtConfig config = new SrtConfig();

        // android.net.Uri breaks IPv6 addresses in the wrong places, use Java’s own URI class
        final URI uri = new URI(connection.url);

        final IPAddress address = new HostName(uri.getHost()).asAddress();
        if (address != null && address.isIPv6()) {
            // Convert literal IPv6 address to IPv6 address
            // An IPv6 address is represented as eight groups of four hexadecimal digits
            // 2001:0db8:85a3:0000:0000:8a2e:0370:7334
            // Literal IPv6 addresses are enclosed in square brackets
            // https://[2001:db8:85a3:8d3:1319:8a2e:370:7348]:443
            config.host = address.toFullString();
        } else {
            config.host = uri.getHost();
        }

        config.port = uri.getPort();
        config.mode = Streamer.Mode.values()[connection.mode];
        config.connectMode = connection.srtMode; // 0:caller, 1:listener, 2:rendezvous
        config.latency = connection.latency; // SRTO_PEERLATENCY
        config.passphrase = connection.passphrase; // SRTO_PASSPHRASE
        config.pbkeylen = connection.pbkeylen; // SRTO_PBKEYLEN
        config.streamid = connection.streamid; // SRTO_STREAMID
        config.maxbw = connection.maxbw; // SRTO_MAXBW
        config.retransmitalgo = connection.retransmitalgo; // SRTO_RETRANSMITALGO
        config.idleTimeout = ConnectivitySettings.idleTimeoutMs(); // SRTO_PEERIDLETIMEO

        return config;
    }

    public static RistConfig toRistConfig(final Connection connection) {
        final RistConfig config = new RistConfig();

        config.uri = connection.url;
        config.mode = Streamer.Mode.values()[connection.mode];
        config.profile = RistConfig.RistProfile.values()[connection.ristProfile];

        return config;
    }

    public static boolean isMaxbwLow(final SrtConfig config) {
        return config.maxbw > 0 && config.maxbw < 10500;
    }

}
