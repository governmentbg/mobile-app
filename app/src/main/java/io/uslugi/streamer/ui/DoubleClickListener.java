package io.uslugi.streamer.ui;

import android.os.SystemClock;
import android.view.View;
import android.view.View.OnClickListener;

public abstract class DoubleClickListener implements OnClickListener {

    // The time in which the second tap should be done in order to qualify as
    // a double click
    private static final long DEFAULT_QUALIFICATION_SPAN = 200;
    private final long doubleClickQualificationSpanInMillis;
    private long timestampLastClick;

    public DoubleClickListener() {
        doubleClickQualificationSpanInMillis = DEFAULT_QUALIFICATION_SPAN;
        timestampLastClick = 0;
    }

    @Override
    public void onClick(View v) {
        if ((SystemClock.elapsedRealtime() - timestampLastClick) < doubleClickQualificationSpanInMillis) {
            onDoubleClick();
        }
        timestampLastClick = SystemClock.elapsedRealtime();
    }

    public abstract void onDoubleClick();
}
