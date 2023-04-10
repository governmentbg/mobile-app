/*
 * Copyright 2011-2015 Tom Hromatka
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.uslugi.streamer.sensors;

import android.hardware.SensorManager;

public class SensorInclinometer extends AbstractSensor implements SensorApi {
    public static final int PITCH_INDEX = 0;
    public static final int ROLL_INDEX = 1;

    private static final double RAD_TO_DEG = 180.0f / Math.PI;
    private static final double ROLL_X_ZERO_THRESH = 0.25f;
    private static final double ROLL_Y_ZERO_THRESH = 0.25f;

    /* notify listeners no faster than at a 3 Hz rate (approximately 333 ms) */
    private static final long NOTIFY_LISTENERS_TIME_MS = 333;
    private static long lastTimeListenersNotified = System.currentTimeMillis();

    private static SensorInclinometer instance = null;
    private static final FilterMovingAverage filterMovingAverage =
            new FilterMovingAverage(FilterMovingAverage.DEFAULT_SAMPLE_EXPIRATION_NS);

    protected SensorInclinometer() {
    }

    public static SensorInclinometer getInstance() {
        if (null == instance) {
            instance = new SensorInclinometer();
        }
        return instance;
    }

    @Override
    protected void enableSensor(SensorManager sensorManager) {
        SensorOrientedAccelerometer.getInstance().registerListener(sensorManager, this);
    }

    @Override
    protected void disableSensor(SensorManager sensorManager) {
        SensorOrientedAccelerometer.getInstance().unregisterListener(sensorManager, this);
    }

    @Override
    public void destroySensor(SensorManager sensorManager) {
        super.destroySensor(sensorManager);
        disableSensor(sensorManager);
    }

    @Override
    public void onDataReceived(long timestamp, float[] accelValues) {

        filterMovingAverage.add(timestamp, accelValues);
        filterMovingAverage.removeExpired();

        float[] averagedAccelValues = filterMovingAverage.getMovingAverage();
        float[] pitchAndRoll = new float[2];

        pitchAndRoll[PITCH_INDEX] =
                (float) computePitch((double) averagedAccelValues[1], (double) averagedAccelValues[2]);
        pitchAndRoll[ROLL_INDEX] =
                (float) computeRoll((double) averagedAccelValues[0], (double) averagedAccelValues[1]);

        /*
         * SensorInclinometer generates the values[] array for onDataReceived() as follows:
         * 0 == pitch (degrees)
         * 1 == roll (degrees)
         *
         * Note to save power, the inclinometer data is only sent to the activity at approximately
         * three hertz.
         */

        long currentTime = System.currentTimeMillis();
        if ((currentTime - lastTimeListenersNotified) > NOTIFY_LISTENERS_TIME_MS) {
            lastTimeListenersNotified = currentTime;
            notifyListenersDataReceived(timestamp, pitchAndRoll);
        }
    }

    @Override
    public void onAccuracyChanged(int accelAccuracy) {
        filterMovingAverage.clear();
    }

    /**
     * Method to compute the pitch of the device
     *
     * @param y accelerometer value in the y axis (m/s^2)
     * @param z accelerometer value in the z axis (m/s^2)
     * @return the pitch of the phone in degrees
     */
    private double computePitch(double y, double z) {

        double pitch = 90.0f - (Math.atan2(y, z) * RAD_TO_DEG);

        if (pitch < -180.0f) {
            pitch += 360.0f;
        } else if (pitch > 180.0f) {
            pitch -= 360.0f;
        }

        return pitch;
    }

    /**
     * Method to compute the roll of the device
     *
     * @param x accelerometer value in the x axis (m/s^2)
     * @param y accelerometer value in the y axis (m/s^2)
     * @return the roll of the phone in degrees
     */
    private double computeRoll(double x, double y) {

        double roll = (Math.atan2(y, x) * RAD_TO_DEG) - 90.0f;

        if (roll < -180.0f) {
            roll += 360.0f;
        } else if (roll > 180.0f) {
            roll -= 360.0f;
        }

        if (x < ROLL_X_ZERO_THRESH && y < ROLL_Y_ZERO_THRESH) {
            /* zero out roll when both x and y are near-zero. */
            roll = 0.0f;
        }

        return roll;
    }
}
