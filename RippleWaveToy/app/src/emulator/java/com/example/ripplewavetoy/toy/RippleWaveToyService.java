package com.example.ripplewavetoy.toy;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

/**
 * RippleWaveToy (emulator flavor)
 * エミュレーター用ビルドでは実機SDKを使用しないスタブ実装。
 */
public class RippleWaveToyService extends Service {
    @Override
    public void onCreate() {
        super.onCreate();
        // エミュレーターでは何もしない
    }

    @Nullable @Override
    public IBinder onBind(Intent intent) {
        // エミュレーターでは何もしない
        return null;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // エミュレーターでは何もしない
        return false;
    }
}


