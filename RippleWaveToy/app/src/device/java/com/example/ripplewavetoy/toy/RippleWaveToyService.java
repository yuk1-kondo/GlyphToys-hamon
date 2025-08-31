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

import androidx.annotation.Nullable;

import com.nothing.ketchum.Glyph;
import com.nothing.ketchum.GlyphMatrixManager;
import com.nothing.ketchum.GlyphToy;
import com.nothing.ketchum.GlyphException;

import java.util.Timer;
import java.util.TimerTask;

/**
 * RippleWaveToy (device flavor)
 * 実機のGlyph Matrixに波紋を描画するToyサービス実装。
 */
public class RippleWaveToyService extends Service {
    private static final String TAG = "RippleWaveToy";

    private GlyphMatrixManager mGM;
    private GlyphMatrixManager.Callback mCallback;

    // ===== Matrix geometry =====
    private static final int W = 25;
    private static final int H = 25;
    private static final float CX = (W - 1) * 0.5f;   // 12
    private static final float CY = (H - 1) * 0.5f;   // 12
    // 実表示は円。半径は外周LEDの中心あたりまで（調整可）
    private static final float RADIUS = 12.4f;

    // ===== Simulation params (プロファイル切替) =====
    private static class Profile {
        final float wavelength;   // λ [pixels]
        final float speed;        // v [pixels/frame]
        final float damping;      // α：距離による減衰
        Profile(float wl, float v, float a){ wavelength=wl; speed=v; damping=a; }
    }
    private final Profile[] profiles = new Profile[] {
            new Profile(4.0f,  0.22f, 0.06f), // 柔らかめ
            new Profile(3.0f,  0.35f, 0.05f), // くっきり・速い
            new Profile(5.5f,  0.16f, 0.08f), // ゆったり・減衰強
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
                    step();        // 低頻度で1ステップ
                    renderAndPresent();
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
                resetScene();
                renderAndPresent();
                startTimer(40); // 25fps相当（=40ms）
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
        // 1) プロファイル切替
        profileIdx = (profileIdx + 1) % profiles.length;
        // 2) 新しい水滴を中心に追加（空きスロットがあれば）
        addDrop(CX, CY);
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
        drops[0] = new Drop(CX, CY);
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
        boolean any = false;
        for (Drop d : drops) if (d != null) { any = true; break; }
        if (!any) drops[0] = new Drop(CX, CY);
    }

    // ===== 描画 =====
    private void renderAndPresent() {
        if (mGM == null) return;

        final Profile pf = profiles[profileIdx];
        final float wl = pf.wavelength;           // λ
        final float v  = pf.speed;                // 伝播速度
        final float a  = pf.damping;              // 減衰

        int idx = 0;
        for (int j = 0; j < H; j++) {
            for (int i = 0; i < W; i++, idx++) {
                // 円マスク（実質表示領域）
                float dx = i - CX;
                float dy = j - CY;
                float rFromCenter = (float)Math.sqrt(dx*dx + dy*dy);
                if (rFromCenter > RADIUS + 0.5f) { frameBuf[idx] = 0xFF000000; continue; }

                // 外周なだらかマスク：円の縁をソフトに
                float mask = smoothstep(RADIUS + 0.5f, RADIUS - 1.0f, rFromCenter);

                // 各Dropの波を合成
                float sum = 0f;
                for (Drop d : drops) {
                    if (d == null) continue;
                    float rx = i - d.x;
                    float ry = j - d.y;
                    float r  = (float)Math.sqrt(rx*rx + ry*ry);

                    // フェーズ： 2π * ( (r - v*t_drop) / λ )
                    float phase = (float)(2.0 * Math.PI * ((r - v * d.age) / wl));

                    // 距離減衰（e^{-a r}）と年齢による立ち上がり/減衰
                    float amp = (float)Math.exp(-a * r) * envelope(d.age);

                    sum += amp * (float)Math.cos(phase);
                }

                // sum を 0..255 へマッピング
                float base = 40f;
                float scale = 200f; // コントラストを強めに
                float val = base + scale * sum;
                // マスク適用
                val *= mask;

                // クリップ
                if (val < 0f)   val = 0f;
                if (val > 255f) val = 255f;
                // SDK想定の明度スロット（0..2040）へスケール
                int brightness = (int)((val / 255f) * 2040f + 0.5f);
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


