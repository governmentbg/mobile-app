package io.uslugi.streamer.data;

import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;

@Entity
public class Connection {
    @Id
    public long id;

    public String name;
    public String url;
    public int mode;
    public boolean active;

    // rtsp and rtmp
    public String username;
    public String password;
    public int auth;

    // srt
    public String passphrase; // SRTO_PASSPHRASE
    public int pbkeylen; // SRTO_PBKEYLEN
    public int latency; // SRTO_LATENCY
    public int maxbw; // SRTO_MAXBW
    public String streamid; // SRTO_STREAMID
    public int srtMode; // 0:caller, 1:listener, 2:rendezvous
    public int retransmitalgo; // SRTO_RETRANSMITALGO

    // rist
    public int ristProfile; // 0:simple, 1:main, 2:advanced

    // ndi
    public String discovery;
    public String json;
    public String metadata;
    public boolean preview;
}
