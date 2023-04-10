package io.uslugi.streamer.sensors;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class SensorAccelerometer extends AbstractSensor implements SensorEventListener {
    private static SensorAccelerometer instance = null;

    protected SensorAccelerometer() {}

    public static SensorAccelerometer getInstance() {
        if (null == instance) {
            instance = new SensorAccelerometer();
        }
        return instance;
    }

    @Override
    protected void enableSensor(SensorManager sensorManager) {
        Sensor sensorAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(this, sensorAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void disableSensor(SensorManager sensorManager) {
        sensorManager.unregisterListener(this);
    }

    @Override
    public void destroySensor(SensorManager sensorManager) {
        super.destroySensor(sensorManager);
        disableSensor(sensorManager);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        /*
         * SensorAccelerometer generates the values[] array for onDataReceived() as follows:
         * 0 == x accelerometer measurement (m/s^2)
         * 1 == y accelerometer measurement (m/s^2)
         * 2 == z accelerometer measurement (m/s^2)
         */
        notifyListenersDataReceived(event.timestamp, event.values);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        notifyListenersAccuracyChanged(accuracy);
    }
}
