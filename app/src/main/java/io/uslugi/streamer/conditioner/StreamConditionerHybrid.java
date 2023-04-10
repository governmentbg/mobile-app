package io.uslugi.streamer.conditioner;

import android.content.Context;

import com.wmspanel.libstream.Streamer;

class StreamConditionerHybrid extends StreamConditionerBase {

    private static final int NORMALIZATION_DELAY = 5_000; // Ignore lost packets during this time after bitrate change
    private static final int RECOVERY_ATTEMPT_INTERVAL = 30_000;
    private static final int RECOVERY_STEP_INTERVAL = 10_000;
    static final int STATS_INTERVAL = 5_000;

    private double mMinBitrate;

    @Override
    protected long checkInterval() {
        return 1000;
    }

    StreamConditionerHybrid(Context context) {
        super(context);
    }

    @Override
    public void start(Streamer streamer, int bitrate) {
        mFullBitrate = bitrate;
        mMinBitrate = bitrate * 0.25;
        super.start(streamer, bitrate);
    }

    @Override
    protected void check(long audioLost, long videoLost) {
        double newBitrate = mFullBitrate;
        for (int id : mConnectionId) {
            final StreamStats stats = mStreamStats.get(id);
            if (stats == null) {
                continue;
            }

            final double requiredBps = stats.getRequiredBps();
            final double realBps = stats.getRealBps();

            final int currentBitrateBps = mCurrentBitrate / 8;

            double reducedBitrate = mCurrentBitrate;
            //Log.d(TAG, String.format("Real %f Required %f", realBps, requiredBps));

            if (realBps < currentBitrateBps * 0.95
                    && realBps < requiredBps * 0.95) {
                final double ratio = realBps / requiredBps;
                reducedBitrate = Math.floor((mCurrentBitrate * ratio + 30_000) / 100_000.0) * 100_000.0;
                //Log.d(TAG, String.format("set ratio %f : %d -> %f", ratio, mCurrentBitrate, reducedBitrate));
            }
            if (reducedBitrate < newBitrate) {
                newBitrate = Math.max(reducedBitrate, mMinBitrate);
            }
        }

        final BitrateHistory prevBitrate = ListUtils.getLast(mBitrateHistory);
        final long curTime = System.currentTimeMillis();
        final long dtChange = curTime - prevBitrate.ts;
        if (dtChange < NORMALIZATION_DELAY) {
            return;
        }
        //Log.d(TAG, String.format(" currentBitrate %d newBitrate %7.0f", mCurrentBitrate, newBitrate));
        if (newBitrate >= mCurrentBitrate
                && mCurrentBitrate < mFullBitrate
                && canTryToRecover()) {
            final double step = Math.min(500_000.0, mFullBitrate * 0.1);
            newBitrate = Math.min(mFullBitrate, mCurrentBitrate + Math.max(100_000.0, step));
        }
        //Log.d(TAG, String.format("currentBitrate=%d newBitrate=%f", mCurrentBitrate, newBitrate));
        if (Math.abs((double) mCurrentBitrate - newBitrate) > 99_000) {
            changeBitrate((long) newBitrate);
        }
    }

    boolean canTryToRecover() {
        final long curTime = System.currentTimeMillis();
        if (mBitrateHistory.size() < 2) {
            return false;
        }
        final int i = mBitrateHistory.size() - 1;
        final BitrateHistory last = mBitrateHistory.get(i);
        final BitrateHistory prev = mBitrateHistory.get(i - 1);
        //Log.d(TAG, String.format("canTryToRecover: last %d prev %d", (int)last.bitrate, (int)prev.bitrate));
        final long dtChange = curTime - last.ts;
        if (last.bitrate < prev.bitrate && dtChange > RECOVERY_ATTEMPT_INTERVAL) {
            // First step after drop
            return true;
        } else if (last.bitrate > prev.bitrate && dtChange > RECOVERY_STEP_INTERVAL) {
            // Continue restoring bitrate
            return true;
        }
        return false;
    }

}
