package io.uslugi.streamer.ui;

import android.content.Intent;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import io.uslugi.streamer.BuildConfig;
import com.wmspanel.libstream.Streamer;
import io.uslugi.streamer.log.EventLog;
import io.uslugi.streamer.ui.batterymeter.BatteryMeterView;

public class PowerIndication {
    private static final String TAG = "PowerIndication";

    public static void updateBattery(Intent batteryStatus, BatteryMeterView batteryView) {
        final int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        final int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        final double frac = level / (double) scale;
        final int percent = (int) Math.round(100 * frac);

        final int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        final boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING;

        batteryView.setChargeLevel(percent);
        batteryView.setCharging(isCharging);

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Battery level: " + percent);
        }
    }

    public static void updateThermalStatus(int status, ImageView indicator) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return;
        }
        final int visibility;
        switch (status) {
            case PowerManager.THERMAL_STATUS_CRITICAL:
            case PowerManager.THERMAL_STATUS_EMERGENCY:
            case PowerManager.THERMAL_STATUS_SHUTDOWN:
                visibility = View.VISIBLE;
                break;
            default:
                visibility = View.INVISIBLE;
                break;
        }
        indicator.setVisibility(visibility);

        EventLog.getInstance().put(
                Streamer.LoggerListener.Severity.INFO, "Thermal status: " + status);
    }

}
