package io.uslugi.streamer.conditioner;

import android.content.Context;

import com.wmspanel.libstream.Streamer;

class StreamConditionerLogarithmicDescend extends StreamConditionerBase {

    private static final long NORMALIZATION_DELAY = 1_500; //Ignore lost frames during this time after bitrate change
    private static final long LOST_ESTIMATE_INTERVAL = 10_000; //Period for lost frame count
    private static final long LOST_TOLERANCE = 4; //Maximum acceptable number of lost frames
    private static final long RECOVERY_ATTEMPT_INTERVAL = 60_000;

    private int mMinBitRate;

    StreamConditionerLogarithmicDescend(Context context) {
        super(context);
    }

    @Override
    public void start(Streamer streamer, int bitrate) {
        mFullBitrate = bitrate;
        mMinBitRate = bitrate / 4;
        super.start(streamer, bitrate);
        if (TEST_MODE) {
            mSimulateLoss = true;
        }
    }

    @Override
    protected void check(long audioLost, long videoLost) {
        long curTime = System.currentTimeMillis();
        LossHistory prevLost = ListUtils.getLast(mLossHistory);
        BitrateHistory prevBitrate = ListUtils.getLast(mBitrateHistory);
        long lastChange = Math.max(prevBitrate.ts, prevLost.ts);
        if (prevLost.audio != audioLost || prevLost.video != videoLost) {

            // Log.d(TAG, "Lost frames " + audioLost + "+" + videoLost);

            long dtChange = curTime - prevBitrate.ts;
            mLossHistory.add(new LossHistory(curTime, audioLost, videoLost));
            if (prevBitrate.bitrate <= mMinBitRate || dtChange < NORMALIZATION_DELAY) {
                return;
            }
            long estimatePeriod = Math.max(prevBitrate.ts + NORMALIZATION_DELAY, curTime - LOST_ESTIMATE_INTERVAL);
            if (countLostForInterval(estimatePeriod) >= LOST_TOLERANCE) {
                long newBitrate = Math.max(mMinBitRate, prevBitrate.bitrate * 1000 / 1414);
                changeBitrate(newBitrate);
                if (TEST_MODE && newBitrate == mMinBitRate) {
                    mSimulateLoss = false;
                }
            }
        } else if (prevBitrate.bitrate != ListUtils.getFirst(mBitrateHistory).bitrate &&
                curTime - lastChange >= RECOVERY_ATTEMPT_INTERVAL) {
            long newBitrate = Math.min(mFullBitrate, prevBitrate.bitrate * 1415 / 1000);
            if (TEST_MODE && newBitrate == mFullBitrate) {
                mSimulateLoss = true;
            }
            changeBitrate(newBitrate);
        }
    }
}
