package io.uslugi.streamer.sensors;

import android.content.res.Configuration;
import android.hardware.SensorManager;

/**
 * The "standard" accelerometer generates data based upon the orientation of the accelerometer
 * chip. On different devices, the chip is mounted in different orientations.  This sensor
 * class attempts to handle that.
 */
public class SensorOrientedAccelerometer extends AbstractSensor implements SensorApi {
    private static SensorOrientedAccelerometer instance = null;

    private static final int NEGATIVE_SIGN = -1;
    private static final int POSITIVE_SIGN = 1;

    private static final int X_INDEX = 0;
    private static final int Y_INDEX = 1;
    private static final int Z_INDEX = 2;

    private boolean orientationSet = false;

    /* intentionally uninitialized.  these will be initialized in initializeOrientation() */
    private int xIndex = X_INDEX;
    private int yIndex = Y_INDEX;
    private int zIndex = Z_INDEX;
    private int xSign = NEGATIVE_SIGN;
    private int ySign = POSITIVE_SIGN;
    private int zSign = NEGATIVE_SIGN;

    protected SensorOrientedAccelerometer() {
    }

    public static SensorOrientedAccelerometer getInstance() {
        if (null == instance) {
            instance = new SensorOrientedAccelerometer();
        }
        return instance;
    }

    @Override
    protected void enableSensor(SensorManager sensorManager) {
        SensorAccelerometer.getInstance().registerListener(sensorManager, this);
    }

    @Override
    protected void disableSensor(SensorManager sensorManager) {
        SensorAccelerometer.getInstance().unregisterListener(sensorManager, this);
    }

    @Override
    public void destroySensor(SensorManager sensorManager) {
        super.destroySensor(sensorManager);
        disableSensor(sensorManager);
    }

    /**
     * This class's listener for new sensor data from SensorAccelerometer.  Required
     * via the SensorApi implementation.
     *
     * @param timestamp time at which this measurement occurred
     * @param values    array of accelerometer measurements (x == 0, y == 1, z == 2)
     */
    @Override
    public void onDataReceived(long timestamp, float[] values) {
        if (!orientationSet) {
            /* we may not yet know the orientation if we are in landscape mode.  now that we
             * have accelerometer data, we can know for sure.
             */
            calculateLandscapeOrientation(values[X_INDEX]);
            orientationSet = true;
        }

        float[] rotatedAccelValues = new float[3];
        rotatedAccelValues[0] = xSign * values[xIndex];
        rotatedAccelValues[1] = ySign * values[yIndex];
        rotatedAccelValues[2] = zSign * values[zIndex];

        notifyListenersDataReceived(timestamp, rotatedAccelValues);
    }

    @Override
    public void onAccuracyChanged(int accuracy) {

    }

    /**
     * this method should be called when the orientation of the device changes. This will allow
     * the oriented accelerometer sensor to reconfigure its orientation
     *
     * @param orientation Configuration.ORIENTATION_* value
     */
    public void setOrientation(int orientation) {
        switch (orientation) {
            case Configuration.ORIENTATION_LANDSCAPE:
                xIndex = Y_INDEX;
                yIndex = X_INDEX;
                zIndex = Z_INDEX;
                xSign = NEGATIVE_SIGN;
                ySign = POSITIVE_SIGN;
                zSign = NEGATIVE_SIGN;

                /* we can't claim the orientation is set (yet).  This is because landscape mode
                 * has two valid positions - left side down or right side down.
                 */
                orientationSet = false;
                break;

            case Configuration.ORIENTATION_PORTRAIT:
            default:
                xIndex = X_INDEX;
                yIndex = Y_INDEX;
                zIndex = Z_INDEX;
                xSign = POSITIVE_SIGN;
                ySign = POSITIVE_SIGN;
                zSign = NEGATIVE_SIGN;
                orientationSet = true;
                break;
        }

        /* notify listeners that our orientation has changed */
        onAccuracyChanged(0);
    }

    /**
     * When the device is in landscape mode, we don't know if the left side is up or the right
     * side is up.  Once we have raw accelerometer measurements, we can determine which side is up.
     *
     * @param xAccelValue x-axis accelerometer measurement (m/s^2)
     */
    private void calculateLandscapeOrientation(float xAccelValue) {
        if (xAccelValue < 0.0f) {
            /* right side of device is down */
            xSign = POSITIVE_SIGN;
            ySign = NEGATIVE_SIGN;
        } else {
            /* left side of device is down */
            xSign = NEGATIVE_SIGN;
            ySign = POSITIVE_SIGN;
        }
    }
}
