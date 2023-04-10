package io.uslugi.streamer.sensors;

public interface SensorApi {
    void onDataReceived(long timestamp, float[] values);

    void onAccuracyChanged(int accuracy);
}
