package io.uslugi.streamer.conditioner;

final class BitrateHistory {
    long ts;
    long bitrate;

    BitrateHistory(long ts, long bitrate) {
        this.ts = ts;
        this.bitrate = bitrate;
    }

}
