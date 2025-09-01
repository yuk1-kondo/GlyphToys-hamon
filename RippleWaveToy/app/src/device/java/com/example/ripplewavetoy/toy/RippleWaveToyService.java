package com.example.ripplewavetoy.toy;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import androidx.annotation.Nullable;

import com.nothing.ketchum.Glyph;
import com.nothing.ketchum.GlyphMatrixManager;
import com.nothing.ketchum.GlyphToy;
import com.nothing.ketchum.GlyphException;

import java.util.Timer;
import java.util.TimerTask;
import java.util.Random;

/**
 * RippleWaveToy (device flavor)
 * 実機のGlyph Matrixに波紋を描画するToyサービス実装。
 */
public class RippleWaveToyService extends Service {
    private static final String TAG = "RippleWaveToy";

    private GlyphMatrixManager mGM;
    private GlyphMatrixManager.Callback mCallback;
    private SensorManager sensorManager;
    private Sensor accelerometer;

    // ===== Matrix geometry =====
    private static final int W = 25;
    private static final int H = 25;
    private static final float CX = (W - 1) * 0.5f;   // 12
    private static final float CY = (H - 1) * 0.5f;   // 12
    // 実表示は円。半径は外周LEDの中心あたりまで（調整可）
    private static final float RADIUS = 12.4f;

    // ===== Simulation params (禅スタイルの単発リング) =====
    private static class Profile {
        final float speed;   // v [pixels/frame]
        final float sigma;   // ガウシアンリングの太さ（標準偏差, px）
        final float damping; // α：距離による減衰
        Profile(float v, float s, float a){ speed=v; sigma=s; damping=a; }
    }
    private final Profile[] profiles = new Profile[] {
            new Profile(0.22f, 1.8f, 0.07f), // 柔らかめ（禅）
            new Profile(0.35f, 1.2f, 0.06f), // くっきり・速い
            new Profile(0.16f, 2.3f, 0.08f), // ゆったり・減衰強
    };
    private int profileIdx = 0;

    // 時間管理
    private float t = 0.0f;       // 経過フレーム
    private float dt = 1.0f;      // ステップ幅

    // 水滴（イベント）管理：複数の波源（最大3）
    private static class Drop {
        final float x, y;     // 発生位置
        float age;            // 発生からの経過時間（frame）
        Drop(float x, float y){ this.x=x; this.y=y; this.age=0f; }
    }
    private final Drop[] drops = new Drop[] {
            new Drop(CX, CY),  // 初期は中心
            null,
            null
    };

    // タイマー
    private Timer timer;
    private Timer autoDropTimer;
    private boolean isAodMode = false;
    private boolean isRainMode = false;
    private final Random random = new Random();
    private long lastShakeMs = 0L;
    // フレームバッファ（ARGB）
    private final int[] frameBuf = new int[W * H];

    // Toyイベント受け取り
    private final Handler serviceHandler = new Handler(Looper.getMainLooper()) {
        @Override public void handleMessage(Message msg) {
            if (msg.what == GlyphToy.MSG_GLYPH_TOY) {
                Bundle bundle = msg.getData();
                String event = bundle != null ? bundle.getString(GlyphToy.MSG_GLYPH_TOY_DATA) : null;
                if (GlyphToy.EVENT_CHANGE.equals(event)) {
                    onLongPress();
                } else if (GlyphToy.EVENT_AOD.equals(event)) {
                    isAodMode = true;
                    step();        // 低頻度で1ステップ
                    renderAndPresent();
                } else {
                    isAodMode = false;
                }
            } else {
                super.handleMessage(msg);
            }
        }
    };
    private final Messenger serviceMessenger = new Messenger(serviceHandler);

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Nullable @Override
    public IBinder onBind(Intent intent) {
        init();
        return serviceMessenger.getBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        stopTimer();
        stopAutoDropTimer();
        teardownSensors();
        if (mGM != null) { mGM.unInit(); }
        mGM = null;
        mCallback = null;
        return false;
    }

    private void init() {
        mGM = GlyphMatrixManager.getInstance(getApplicationContext());
        mCallback = new GlyphMatrixManager.Callback() {
            @Override public void onServiceConnected(ComponentName name) {
                boolean registered = tryRegisterWithFallback();
                android.util.Log.d(TAG, "onServiceConnected, registered=" + registered);
                if (!registered) return;
                // 初期状態：中心に水滴1つ
                isAodMode = false;
                resetScene();
                renderAndPresent();
                startTimer(40); // 25fps相当（=40ms）
                startAutoDropTimer(10_000L, 10_000L);
                setupSensors();
            }
            @Override public void onServiceDisconnected(ComponentName name) { }
        };
        mGM.init(mCallback);
    }

    private boolean tryRegisterWithFallback() {
        String[] candidates = new String[] {
                Glyph.DEVICE_23112,
                Glyph.DEVICE_23113,
                Glyph.DEVICE_24111,
                Glyph.DEVICE_23111,
                Glyph.DEVICE_22111,
                Glyph.DEVICE_20111,
        };
        for (String code : candidates) {
            try {
                boolean ok = mGM.register(code);
                if (ok) {
                    android.util.Log.i(TAG, "Registered target=" + code);
                    try {
                        mGM.setGlyphMatrixTimeout(false);
                    } catch (GlyphException ignore) {}
                    return true;
                }
            } catch (Throwable t) {
                android.util.Log.w(TAG, "register failed for code=" + code + ", " + t);
            }
        }
        android.util.Log.e(TAG, "All register candidates failed.");
        return false;
    }

    // ===== 長押しアクション =====
    private void onLongPress() {
        android.util.Log.d(TAG, "EVENT_CHANGE long-press");
        // モード切替: 禅(10s/滴) <-> 雨(ランダム多点)
        isRainMode = !isRainMode;
        if (isRainMode) {
            android.util.Log.i(TAG, "Switch to RAIN mode");
            startAutoDropTimer(0L, 800L); // 小雨のように0.8秒毎
            spawnRainBurst(5); // 切替時に軽く降らせる
        } else {
            android.util.Log.i(TAG, "Switch to ZEN mode");
            startAutoDropTimer(10_000L, 10_000L);
        }
    }

    // ===== タイマー =====
    private void startTimer(long periodMs) {
        stopTimer();
        timer = new Timer("RippleToyTimer");
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() {
                step();
                renderAndPresent();
            }
        }, 0, periodMs);
    }
    private void stopTimer() {
        if (timer != null) { try { timer.cancel(); } catch (Throwable ignored) {} timer = null; }
    }

    // ===== 自動ドロップ（10秒おき、AOD中は停止） =====
    private void startAutoDropTimer(long initialDelayMs, long periodMs) {
        stopAutoDropTimer();
        autoDropTimer = new Timer("RippleAutoDrop");
        autoDropTimer.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() {
                if (!isAodMode) { serviceHandler.post(() -> spawnTick()); }
            }
        }, initialDelayMs, periodMs);
    }
    private void stopAutoDropTimer() {
        if (autoDropTimer != null) { try { autoDropTimer.cancel(); } catch (Throwable ignored) {} autoDropTimer = null; }
    }

    // ===== シミュレーション1ステップ =====
    private void step() {
        t += dt;
        for (int i = 0; i < drops.length; i++) {
            if (drops[i] != null) drops[i].age += dt;
        }
        // 古いドロップを自動的に解放（十分減衰したら）
        removeFadedDrops();
    }

    private void resetScene() {
        t = 0f;
        for (int i = 0; i < drops.length; i++) drops[i] = null;
        // 禅: 初期は静寂（ドロップ無し）
    }

    private void addDrop(float x, float y) {
        for (int i = 0; i < drops.length; i++) {
            if (drops[i] == null) { drops[i] = new Drop(x, y); return; }
        }
        // いっぱいなら一番古いものを置き換え
        int oldest = 0;
        float maxAge = -1f;
        for (int i = 0; i < drops.length; i++) {
            if (drops[i] != null && drops[i].age > maxAge) { maxAge = drops[i].age; oldest = i; }
        }
        drops[oldest] = new Drop(x, y);
    }

    private void removeFadedDrops() {
        for (int i = 0; i < drops.length; i++) {
            Drop d = drops[i];
            if (d == null) continue;
            if (d.age > 400f) { drops[i] = null; }
        }
        // 禅: 全て消えたら静寂のまま
    }

    // ===== 雨/シェイク補助 =====
    private void spawnTick() {
        if (isRainMode) {
            // ランダム位置に弱い雨滴を1つ
            addDrop(clamp(gaussX(), 0f, W - 1), clamp(gaussY(), 0f, H - 1));
        } else {
            addDrop(CX, CY);
        }
    }

    private void spawnRainBurst(int count) {
        for (int k = 0; k < count; k++) {
            addDrop(clamp(gaussX(), 0f, W - 1), clamp(gaussY(), 0f, H - 1));
        }
    }

    private float gaussX() { return (float)(CX + random.nextGaussian() * (W * 0.18f)); }
    private float gaussY() { return (float)(CY + random.nextGaussian() * (H * 0.18f)); }
    private static float clamp(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }

    private void setupSensors() {
        try {
            sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
            if (sensorManager != null) {
                accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                if (accelerometer != null) {
                    sensorManager.registerListener(shakeListener, accelerometer, SensorManager.SENSOR_DELAY_UI);
                }
            }
        } catch (Throwable ignored) {}
    }
    private void teardownSensors() {
        try { if (sensorManager != null) sensorManager.unregisterListener(shakeListener); } catch (Throwable ignored) {}
        accelerometer = null;
        sensorManager = null;
    }

    private final SensorEventListener shakeListener = new SensorEventListener() {
        @Override public void onSensorChanged(SensorEvent event) {
            float ax = event.values[0], ay = event.values[1], az = event.values[2];
            float g = (float)Math.sqrt(ax*ax + ay*ay + az*az);
            long now = System.currentTimeMillis();
            if (g > 15.0f && now - lastShakeMs > 800) { // しっかり振ったら
                lastShakeMs = now;
                if (!isAodMode) spawnRainBurst(8);
            }
        }
        @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    // ===== 描画 =====
    private void renderAndPresent() {
        if (mGM == null) return;

        final Profile pf = profiles[profileIdx];
        final float v  = pf.speed;                // 伝播速度
        final float a  = pf.damping;              // 減衰
        final float sigma = pf.sigma;             // リング太さ

        int idx = 0;
        for (int j = 0; j < H; j++) {
            for (int i = 0; i < W; i++, idx++) {
                // 円マスク（実質表示領域）
                float dx = i - CX;
                float dy = j - CY;
                float rFromCenter = (float)Math.sqrt(dx*dx + dy*dy);
                if (rFromCenter > RADIUS + 0.5f) { frameBuf[idx] = 0; continue; }

                // 外周なだらかマスク：円の縁をソフトに
                float mask = smoothstep(RADIUS + 0.5f, RADIUS - 1.0f, rFromCenter);

                // 各Dropの単発リングを合成
                float sum = 0f;
                for (Drop d : drops) {
                    if (d == null) continue;
                    float rx = i - d.x;
                    float ry = j - d.y;
                    float r  = (float)Math.sqrt(rx*rx + ry*ry);

                    // 単発リング: r0 = v * age のガウシアンシェル
                    float r0 = v * d.age;
                    float dr = r - r0;
                    float shell = (float)Math.exp(-0.5f * (dr * dr) / (sigma * sigma));

                    // 距離減衰と年齢包絡
                    float amp = (float)Math.exp(-a * r) * envelope(d.age);

                    sum += amp * shell;
                }

                // 0..1 正規化し、マスク適用
                float baseN = 0.00f;  // 静寂
                float gain  = 1.10f;  // コントラスト
                float valN  = baseN + gain * sum;
                valN *= mask;
                if (valN < 0f) valN = 0f;
                if (valN > 1f) valN = 1f;

                // SDK想定の明度スロット（0..2040）へスケール
                int brightness = (int)(valN * 2040f + 0.5f);
                if (brightness < 0) brightness = 0;
                if (brightness > 2040) brightness = 2040;
                frameBuf[idx] = brightness;
            }
        }

        // Toyサービスでは setMatrixFrame を使用
        try {
            mGM.setMatrixFrame(frameBuf);
        } catch (GlyphException e) {
            android.util.Log.w(TAG, "setMatrixFrame failed, trying setAppMatrixFrame: " + e);
            try {
                mGM.setAppMatrixFrame(frameBuf);
            } catch (GlyphException e2) {
                android.util.Log.e(TAG, "setAppMatrixFrame also failed: " + e2);
            }
        }
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = clamp01((x - edge0) / (edge1 - edge0));
        return t * t * (3f - 2f * t);
    }
    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    // 水滴の包絡（0→最大→減衰）
    private static float envelope(float age) {
        float a = age;
        float attack = 6f;
        float fade   = 0.004f; // 小さいほどゆっくり消える
        float att = 1f - (float)Math.exp(-a / attack);
        float rel = (float)Math.exp(-fade * a);
        return att * rel;
    }
}


