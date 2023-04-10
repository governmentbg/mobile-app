package io.uslugi.streamer.ui;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathEffect;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;

import io.uslugi.streamer.R;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

public class AudioLevelMeter extends View implements ValueAnimator.AnimatorUpdateListener {
    static private final float measureInterval = 0.1f; //Interval for update volume meter in seconds

    private float[] rms;
    private double[] sum;
    private int count = 0;
    private float duration = 0.0f;

    private final Paint mPaint;
    private final Path mPath;
    private ValueAnimator[] mAnimators;
    private int mWidth;
    private int mHeight;

    private int mChannels;
    private final int mLedCount;
    private int mRedCount;
    private int mYellowCount;

    private float[] mValue;
    private boolean mNeedUpdateTicks = true;

    static private final double conversion16Base = Math.pow(2.0, 15);
    static private final float dbRangeMin = -80.0f;
    static private final float dbRangeMax = 0.0f;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mCanAnimate;

    public void setChannels(int mChannels) {
        this.mChannels = mChannels;
        if (mAnimators != null) {
            for (Animator a : mAnimators) {
                a.removeAllListeners();
            }
        }
        initChannels();
    }

    public AudioLevelMeter(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.AudioLevelMeter);
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPath = new Path();

        mPaint.setColor(0xFFFF0000);
        mPaint.setARGB(255, 0, 255, 0);
        mPaint.setStyle(Paint.Style.STROKE);

        mChannels = attributes.getInt(R.styleable.AudioLevelMeter_channels, 1);
        if (mChannels <= 0) {
            mChannels = 1;
        }

        mLedCount = attributes.getInt(R.styleable.AudioLevelMeter_ledCount, 30);
        mRedCount = attributes.getInt(R.styleable.AudioLevelMeter_redCount, 0);
        mYellowCount = attributes.getInt(R.styleable.AudioLevelMeter_redCount, 0);
        if (mRedCount <= 0 || mRedCount >= mLedCount) {
            mRedCount = (mLedCount + 9) / 10;
        }
        if (mYellowCount <= 0 || mYellowCount >= mLedCount) {
            mYellowCount = (mLedCount + 2) / 3 - mRedCount;
        }
        initChannels();
        attributes.recycle();
    }

    void initChannels() {
        mAnimators = new ValueAnimator[mChannels];

        for (int i = 0; i < mChannels; i++) {
            ValueAnimator animator = ValueAnimator.ofFloat(dbRangeMin, dbRangeMin);
            animator.setDuration((long) (measureInterval * 1000.0f));
            animator.addUpdateListener(this);
            animator.setInterpolator(new LinearInterpolator());
            mAnimators[i] = animator;
        }

        rms = new float[mChannels];
        sum = new double[mChannels];
        mValue = new float[mChannels];
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        final float barH = (float) Math.max(mWidth, mHeight);
        final float barW = (float) Math.min(mWidth, mHeight) / (float) mChannels;
        final float strokeLen = barH / (float) mLedCount;
        if (mNeedUpdateTicks) {
            mPaint.setStrokeWidth(barW * 0.9f);

            final float strokeFill = strokeLen * 2.0f / 3.0f;
            final float strokeBlank = strokeLen * 1.0f / 3.0f;
            final float[] intervals = {strokeFill, strokeBlank};
            PathEffect dash = new DashPathEffect(intervals, strokeLen);
            mPaint.setPathEffect(dash);

            setGradient();
            mNeedUpdateTicks = false;
        }
        for (int i = 0; i < mChannels; i++) {
            float numStrokes = Math.round(mLedCount * mValue[i]);
            float len = numStrokes * strokeLen;
            mPath.reset();
            if (mWidth > mHeight) {
                mPath.moveTo(0, barW * (i + 0.5f));
                mPath.lineTo(len, barW * (i + 0.5f));
            } else {
                mPath.moveTo(barW * (i + 0.5f), barH);
                mPath.lineTo(barW * (i + 0.5f), barH - len);
            }
            canvas.drawPath(mPath, mPaint);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        final ViewGroup.LayoutParams barParams = getLayoutParams();
        mWidth = Math.min(barParams.width, barParams.height);
        mHeight = Math.max(barParams.width, barParams.height);

        mNeedUpdateTicks = true;
        setMeasuredDimension(mWidth, mHeight);
    }

    private void setGradient() {
        final int clrRed = 0xffff0000;
        final int clrYellow = 0xffffff00;
        final int clrGreen = 0xff00ff00;

        final float redPos = (float) (mLedCount - mRedCount) / (float) mLedCount;
        final float yellowPos = (float) (mLedCount - mRedCount - mYellowCount) / (float) mLedCount;

        final int[] gradientColors;
        final float[] locations;

        locations = new float[]{0f, (yellowPos - 0.01f), (yellowPos), (redPos - 0.01f), (redPos)};
        gradientColors = new int[]{clrGreen, clrGreen, clrYellow, clrYellow, clrRed};

        final LinearGradient gradient;
        if (mWidth > mHeight) {
            gradient = new LinearGradient(0f, 0f, (float) mWidth, 0f, gradientColors, locations, Shader.TileMode.CLAMP);
        } else {
            gradient = new LinearGradient(0f, (float) mHeight, 0f, 0f, gradientColors, locations, Shader.TileMode.CLAMP);

        }
        mPaint.setShader(gradient);
    }

    public void putBuffer(byte[] data, int channelCount, int sampleRate) {
        ShortBuffer shortBuffer = ByteBuffer.wrap(data).order(ByteOrder.nativeOrder()).asShortBuffer();
        double channelFactor = 1.0 / channelCount;
        int ch = 0;
        while (shortBuffer.hasRemaining()) {
            short val = shortBuffer.get();
            double f = val / conversion16Base;
            sum[ch] += f * f;
            if (mChannels > channelCount) {
                sum[ch + 1] += f * f;
            }
            if (ch == channelCount - 1) {
                count++;
            }
            ch = (ch + 1) % channelCount;
            duration += channelFactor / sampleRate;
            if (duration > measureInterval) {
                updateValue();
            }
        }
    }

    private void updateValue() {
        if (!mCanAnimate) {
            return;
        }

        final float[] val1 = new float[mChannels];
        final float[] val2 = new float[mChannels];

        for (int i = 0; i < mChannels; i++) {
            double v = sum[i] == 0.0f ? -100.0f : 10.0f * Math.log(Math.sqrt(sum[i] / count));
            float newValue = rms[i] * 0.1f + (float) v * 0.9f;
            if (newValue < dbRangeMin) {
                newValue = dbRangeMin;
            } else if (newValue > dbRangeMax) {
                newValue = dbRangeMax;
            }

            val1[i] = rms[i];
            val2[i] = newValue;

            rms[i] = newValue;
            sum[i] = 0;
        }
        duration -= measureInterval;
        count = 0;

        mHandler.post(() -> {
            if (!mCanAnimate) {
                return;
            }
            if (mAnimators != null && mAnimators.length == mChannels) {
                for (int i = 0; i < mChannels; i++) {
                    mAnimators[i].cancel();
                    mAnimators[i].setFloatValues(val1[i], val2[i]);
                    mAnimators[i].start();
                }
            }
        });
    }

    @Override
    public void onAnimationUpdate(ValueAnimator animation) {
        if (!mCanAnimate) {
            return;
        }
        if (mAnimators != null && mAnimators.length == mChannels) {
            if (animation == mAnimators[mChannels - 1]) {
                for (int i = 0; i < mChannels; i++) {
                    float val = (Float) mAnimators[i].getAnimatedValue();
                    mValue[i] = (val - dbRangeMin) / (dbRangeMax - dbRangeMin);
                    invalidate();
                }
            }
        }
    }

    public void pauseAnimating() {
        mCanAnimate = false;
        mHandler.removeCallbacksAndMessages(null);
        mHandler.post(() -> {
            if (mAnimators != null && mAnimators.length == mChannels) {
                for (int i = 0; i < mChannels; i++) {
                    mAnimators[i].cancel();
                }
            }
        });
    }

    public void startAnimating() {
        mCanAnimate = true;
    }

}
