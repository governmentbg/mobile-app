package io.uslugi.streamer.sensors;

import android.hardware.SensorManager;

import java.util.ArrayList;
import java.util.Iterator;

public abstract class AbstractSensor {
    private final ArrayList<SensorApi> listenerList = new ArrayList<>();

    public void registerListener(SensorManager sensorManager, SensorApi callback) {
        if (listenerList.isEmpty()) {
            enableSensor(sensorManager);
        }
        listenerList.add(callback);
    }

    public void unregisterListener(SensorManager sensorManager, SensorApi callback) {
        for (Iterator<SensorApi> iterator = listenerList.iterator(); iterator.hasNext(); ) {
            SensorApi sensorApi = iterator.next();
            if (callback == sensorApi) {
                iterator.remove();
                break;
            }
        }
        if (listenerList.isEmpty()) {
            disableSensor(sensorManager);
        }
    }

    protected void notifyListenersDataReceived(long timestamp, float[] values) {
        for (SensorApi sensorApi : listenerList) {
            sensorApi.onDataReceived(timestamp, values);
        }
    }

    protected void notifyListenersAccuracyChanged(int accuracy) {
        for (SensorApi sensorApi : listenerList) {
            sensorApi.onAccuracyChanged(accuracy);
        }
    }

    protected abstract void enableSensor(SensorManager sensorManager);

    protected abstract void disableSensor(SensorManager sensorManager);

    public void destroySensor(SensorManager sensorManager) {
        listenerList.clear();
    }
}
