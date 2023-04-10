package io.uslugi.streamer.sensors;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class FilterMovingAverage {
    public static final double SEC_TO_NANOSEC = 1e9;
    /* expire samples after 1/2 of a second */
    public static final double DEFAULT_SAMPLE_EXPIRATION_NS = 0.5 * SEC_TO_NANOSEC;

    private static class TimestampAndData {
        private final long timestamp;
        private final float[] data;

        public TimestampAndData(long timestamp, float[] data) {
            this.timestamp = timestamp;
            this.data = data;
        }

        public long getTimestamp() {
            return this.timestamp;
        }

        public float[] getData() {
            return this.data;
        }
    }

    private final List<TimestampAndData> timestampAndDataList = new ArrayList<>();
    private final double samplesExpireAfterNanoseconds;

    public FilterMovingAverage(double samplesExpireAfterNanoseconds) {
        this.samplesExpireAfterNanoseconds = samplesExpireAfterNanoseconds;
    }

    public synchronized float[] getMovingAverage() {
        TimestampAndData oneSample = timestampAndDataList.get(0);
        float[] averages = new float[oneSample.getData().length];
        for (TimestampAndData thisSample : timestampAndDataList) {
            for (int index = 0; index < averages.length; index++) {
                averages[index] += thisSample.getData()[index];
            }
        }
        for (int index = 0; index < averages.length; index++) {
            averages[index] = averages[index] / timestampAndDataList.size();
        }
        return averages;
    }

    public synchronized void add(long timestamp, float[] data) {
        TimestampAndData newSample = new TimestampAndData(timestamp, data);
        timestampAndDataList.add(newSample);
    }

    public synchronized void clear() {
        timestampAndDataList.clear();
    }

    public synchronized void removeExpired() {
        TimestampAndData mostCurrentSample = timestampAndDataList.get(timestampAndDataList.size() - 1);
        long currentTime = mostCurrentSample.getTimestamp();
        for (Iterator<TimestampAndData> iterator = timestampAndDataList.iterator(); iterator.hasNext(); ) {
            TimestampAndData thisSample = iterator.next();
            if (thisSample.getTimestamp() < (currentTime - (long) samplesExpireAfterNanoseconds)) {
                iterator.remove();
            }
        }
    }
}
