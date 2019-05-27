package com.obsez.android.lib.smbfilechooser.demo;

import android.app.Application;
import android.content.Context;

public final class App extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        if (BuildConfig.DEBUG) {
            androidx.multidex.MultiDex.install(this);
        }
    }
}
