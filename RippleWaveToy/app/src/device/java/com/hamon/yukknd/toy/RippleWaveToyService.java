package com.hamon.yukknd.toy;

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

public class RippleWaveToyService extends Service {
    private static final String TAG = "HamonToy";
    private static final int MODE_ZEN = 0;
    private static final int MODE_HAMON = 1;
    private static final int MODE_RAIN = 2;
    private int mode = MODE_ZEN;
    private GlyphMatrixManager mGM;
    private GlyphMatrixManager.Callback mCallback;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private static final int W = 25;
    private static final int H = 25;
    private static final float CX = (W - 1) * 0.5f;
    private static final float CY = (H - 1) * 0.5f;
    private static final float RADIUS = 12.4f;
    private static final float FPS = 25f;
    private static class Profile {
        final float speed;
        final float sigma;
        final float damping;
        Profile(float v, float s, float a){ speed=v; sigma=s; damping=a; }
    }
    private final Profile[] profiles = new Profile[] {
            new Profile(RADIUS / (2.5f * FPS), 1.8f, 0.07f),
            new Profile(RADIUS / (2.5f * FPS), 1.6f, 0.07f),
            new Profile(RADIUS / (2.5f * FPS), 2.1f, 0.07f),
    };
    private int profileIdx = 0;
    private float t = 0.0f;
    private float dt = 1.0f;
    private static class Drop {
        final float x, y;
        float age;
        final float weight;
        final float sigmaScale;
        final float speedScale;
        final float dampingScale;
        Drop(float x, float y){ this(x, y, 1f, 1f, 1f, 1f); }
        Drop(float x, float y, float weight, float sigmaScale){ this(x, y, weight, sigmaScale, 1f, 1f); }
        Drop(float x, float y, float weight, float sigmaScale, float speedScale, float dampingScale){
            this.x=x; this.y=y; this.age=0f; this.weight=weight; this.sigmaScale=sigmaScale; this.speedScale=speedScale; this.dampingScale=dampingScale;
        }
    }
    private static final int MAX_DROPS = 8;
    private final Drop[] drops = new Drop[MAX_DROPS];
    private Timer timer;
    private Timer autoDropTimer;
    private boolean isAodMode = false;
    private boolean isRainMode = false;
    private final Random random = new Random();
    private long lastShakeMs = 0L;
    private final int[] frameBuf = new int[W * H];
    private final Handler serviceHandler = new Handler(Looper.getMainLooper()) {
        @Override public void handleMessage(Message msg) {
            if (msg.what == GlyphToy.MSG_GLYPH_TOY) {
                Bundle bundle = msg.getData();
                String event = bundle != null ? bundle.getString(GlyphToy.MSG_GLYPH_TOY_DATA) : null;
                if (GlyphToy.EVENT_CHANGE.equals(event)) {
                    onLongPress();
                } else if (GlyphToy.EVENT_AOD.equals(event)) {
                    isAodMode = true;
                    step();
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
                isAodMode = false;
                resetScene();
                mode = MODE_ZEN;
                spawnTick();
                renderAndPresent();
                startTimer(40);
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
                    try { mGM.setGlyphMatrixTimeout(false); } catch (GlyphException ignore) {}
                    return true;
                }
            } catch (Throwable t) {
                android.util.Log.w(TAG, "register failed for code=" + code + ", " + t);
            }
        }
        android.util.Log.e(TAG, "All register candidates failed.");
        return false;
    }

    private void onLongPress() {
        android.util.Log.d(TAG, "EVENT_CHANGE long-press");
        mode = (mode + 1) % 3;
        switch (mode) {
            case MODE_ZEN:
                android.util.Log.i(TAG, "Switch to ZEN mode");
                startAutoDropTimer(10_000L, 10_000L);
                break;
            case MODE_HAMON:
                android.util.Log.i(TAG, "Switch to HAMON mode");
                startAutoDropTimer(10_000L, 10_000L);
                spawnTick();
                break;
            case MODE_RAIN:
                android.util.Log.i(TAG, "Switch to RAIN mode");
                startAutoDropTimer(200L, 1400L);
                spawnRainBurst(3);
                break;
        }
    }

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

    private void step() {
        t += dt;
        for (int i = 0; i < drops.length; i++) {
            if (drops[i] != null) drops[i].age += dt;
        }
        removeFadedDrops();
    }

    private void resetScene() {
        t = 0f;
        for (int i = 0; i < drops.length; i++) drops[i] = null;
    }

    private void addDrop(float x, float y) { addDrop(x, y, 1f, 1f); }
    private void addDrop(float x, float y, float weight, float sigmaScale) { addDrop(x, y, weight, sigmaScale, 1f, 1f); }
    private void addDrop(float x, float y, float weight, float sigmaScale, float speedScale, float dampingScale) {
        for (int i = 0; i < drops.length; i++) {
            if (drops[i] == null) { drops[i] = new Drop(x, y, weight, sigmaScale, speedScale, dampingScale); return; }
        }
        int oldest = 0;
        float maxAge = -1f;
        for (int i = 0; i < drops.length; i++) {
            if (drops[i] != null && drops[i].age > maxAge) { maxAge = drops[i].age; oldest = i; }
        }
        final float MIN_REPLACE_AGE_FRAMES = 50f;
        if (mode == MODE_RAIN && maxAge < MIN_REPLACE_AGE_FRAMES) {
            int youngest = 0;
            float minAge = Float.MAX_VALUE;
            for (int i = 0; i < drops.length; i++) {
                if (drops[i] != null && drops[i].age < minAge) { minAge = drops[i].age; youngest = i; }
            }
            if (minAge < MIN_REPLACE_AGE_FRAMES * 0.5f) { return; }
        }
        drops[oldest] = new Drop(x, y, weight, sigmaScale, speedScale, dampingScale);
    }

    private void removeFadedDrops() {
        for (int i = 0; i < drops.length; i++) {
            Drop d = drops[i];
            if (d == null) continue;
            if (d.age > 520f) { drops[i] = null; }
        }
    }

    private void spawnTick() {
        if (mode == MODE_RAIN) { spawnRainStep(); }
        else { addDrop(CX, CY); }
    }
    private void spawnRainBurst(int count) { for (int k = 0; k < count; k++) addRainRandomDrop(); }
    private void spawnRainStep() {
        double u = random.nextDouble();
        int n;
        if (u < 0.35) n = 0;
        else if (u < 0.85) n = 1;
        else if (u < 0.97) n = 2;
        else n = 3;
        for (int i = 0; i < n; i++) addRainRandomDrop();
    }
    private void addRainRandomDrop() {
        double theta = 2.0 * Math.PI * random.nextDouble();
        double rad = RADIUS * Math.sqrt(random.nextDouble());
        float x = clamp((float)(CX + rad * Math.cos(theta)), 0f, W - 1);
        float y = clamp((float)(CY + rad * Math.sin(theta)), 0f, H - 1);
        float weight = 0.8f + (random.nextFloat() * 0.6f);
        if (random.nextFloat() < 0.10f) weight = 1.3f + random.nextFloat() * 0.5f;
        float sigmaScale = 1.0f + (random.nextFloat() * 0.35f);
        float speedScale = 1.02f + (random.nextFloat() * 0.24f);
        if (random.nextFloat() < 0.06f) speedScale = 0.92f + (random.nextFloat() * 0.10f);
        float dampingScale = 0.9f + (random.nextFloat() * 0.3f);
        addDrop(x, y, weight, sigmaScale, speedScale, dampingScale);
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
            if (g > 15.0f && now - lastShakeMs > 800) {
                lastShakeMs = now;
                if (!isAodMode && mode == MODE_RAIN) spawnRainBurst(8);
            }
        }
        @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    private void renderAndPresent() {
        if (mGM == null) return;
        final Profile pf = profiles[profileIdx];
        final float v  = pf.speed;
        final float a  = pf.damping;
        final float sigma = pf.sigma;
        int idx = 0;
        for (int j = 0; j < H; j++) {
            for (int i = 0; i < W; i++, idx++) {
                float dx = i - CX;
                float dy = j - CY;
                float rFromCenter = (float)Math.sqrt(dx*dx + dy*dy);
                if (rFromCenter > RADIUS + 1.2f) { frameBuf[idx] = 0; continue; }
                float mask = smoothstep(RADIUS + 1.2f, RADIUS - 0.2f, rFromCenter);
                float sum = 0f;
                for (Drop d : drops) {
                    if (d == null) continue;
                    float rx = i - d.x;
                    float ry = j - d.y;
                    float r  = (float)Math.sqrt(rx*rx + ry*ry);
                    float vDrop = v * d.speedScale;
                    float aDrop = a / d.dampingScale;
                    float r0 = vDrop * d.age;
                    float dr0 = r - r0;
                    float sig0 = sigma * (d.sigmaScale);
                    float shell0 = (float)Math.exp(-0.5f * (dr0 * dr0) / (sig0 * sig0));
                    float amp0 = (float)Math.exp(-aDrop * r) * envelope(d.age / d.dampingScale) * d.weight;
                    sum += amp0 * shell0;
                    if (mode == MODE_HAMON) {
                        float delta = 3.8f;
                        int trails = 3;
                        for (int k = 1; k <= trails; k++) {
                            float rk = r0 - k * delta;
                            if (rk < 0f) break;
                            float drk = r - rk;
                            float sigmak = (sigma * (1.0f + 0.25f * k)) * d.sigmaScale;
                            float shellk = (float)Math.exp(-0.5f * (drk * drk) / (sigmak * sigmak));
                            float envk = envelope(Math.max(0f, (d.age - 2.0f * k)) / d.dampingScale);
                            float dampk = (float)Math.exp(-aDrop * r);
                            float gaink = (float)Math.pow(0.72f, k) * d.weight;
                            sum += gaink * dampk * envk * shellk;
                        }
                    }
                }
                float baseN = 0.00f;
                float gain  = 1.10f;
                float valN  = baseN + gain * sum;
                valN *= mask;
                if (valN < 0f) valN = 0f;
                if (valN > 1f) valN = 1f;
                int brightness = (int)(valN * 2040f + 0.5f);
                if (brightness < 0) brightness = 0;
                if (brightness > 2040) brightness = 2040;
                frameBuf[idx] = brightness;
            }
        }
        try {
            mGM.setMatrixFrame(frameBuf);
        } catch (GlyphException e) {
            android.util.Log.w(TAG, "setMatrixFrame failed, trying setAppMatrixFrame: " + e);
            try { mGM.setAppMatrixFrame(frameBuf); } catch (GlyphException e2) { android.util.Log.e(TAG, "setAppMatrixFrame also failed: " + e2); }
        }
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = clamp01((x - edge0) / (edge1 - edge0));
        return t * t * (3f - 2f * t);
    }
    private static float clamp01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }
    private static float envelope(float age) {
        float attack = 4f;
        float fade   = 0.0065f;
        float att = 1f - (float)Math.exp(-age / attack);
        float rel = (float)Math.exp(-fade * age);
        return att * rel;
    }
}


