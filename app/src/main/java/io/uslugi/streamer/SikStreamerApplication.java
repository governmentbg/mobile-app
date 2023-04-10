package io.uslugi.streamer;

import android.app.Activity;

import androidx.multidex.MultiDexApplication;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class SikStreamerApplication extends MultiDexApplication implements MultiDexApplication.ActivityLifecycleCallbacks {

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(this);
        ObjectBox.init(this);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        unregisterActivityLifecycleCallbacks(this);
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle bundle) {}

    @Override
    public void onActivityStarted(@NonNull Activity activity) {}

    @Override
    public void onActivityResumed(@NonNull Activity activity) {}

    @Override
    public void onActivityPaused(@NonNull Activity activity) {}

    @Override
    public void onActivityStopped(@NonNull Activity activity) {}

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle bundle) {}

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {}
}
