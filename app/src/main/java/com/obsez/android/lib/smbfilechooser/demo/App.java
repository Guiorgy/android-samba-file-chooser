package com.obsez.android.lib.smbfilechooser.demo;

import android.app.Application;
import android.content.Context;

import com.squareup.leakcanary.LeakCanary;

public final class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        if (LeakCanary.isInAnalyzerProcess(this)) {
            // This process is dedicated to LeakCanary for heap analysis.
            // You should not init your app in this process.
            return;
        }
        LeakCanary.install(this);
        // Normal app init code...
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        if (BuildConfig.DEBUG) {
            androidx.multidex.MultiDex.install(this);
        }
    }
}
