package com.hamon.yukknd.toy;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

public class RippleWaveToyService extends Service {
    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Nullable @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return false;
    }
}


