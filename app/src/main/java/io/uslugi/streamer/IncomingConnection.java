package io.uslugi.streamer;

import io.objectbox.annotation.Entity;
import io.uslugi.streamer.data.Connection;

@Entity
public class IncomingConnection extends Connection {
    public int buffering;
}
