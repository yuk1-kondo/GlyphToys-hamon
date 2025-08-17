package com.example.ripplewavetoy.toy;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.os.*;
import androidx.annotation.Nullable;

// エミュレーター用ビルドではGlyph Matrix SDKを使わない
// import com.nothing.gdk.glyph.Glyph;
// import com.nothing.gdk.glyph.GlyphMatrixFrame;
// import com.nothing.gdk.glyph.GlyphMatrixManager;
// import com.nothing.gdk.glyph.GlyphToy;

import java.util.Timer;
import java.util.TimerTask;

/**
 * RippleWaveToy
 * - 中心(または任意点)から同心円の波紋が伝播
 * - 長押し: 新しい水滴を落とす & プロファイル切替 (速度/波長/減衰)
 * - AOD: 毎分イベントで1ステップだけ進める省電力描画
 * - 25×25の円形実表示を意識して円マスクを適用
 * 
 * 注意: このサービスは実機でのみ動作します
 */
public class RippleWaveToyService extends Service {

    // エミュレーター用ビルドでは動作しない
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

    // 以下のコードは実機用ビルドでのみ有効
    /*
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
        final float wavelength;   // λ [pixels]：波の間隔
        final float speed;        // v [pixels/frame]：外向きの伝播速度
        final float damping;      // α：距離による減衰（大きいほど減衰強）
        Profile(float wl, float v, float a){ wavelength=wl; speed=v; damping=a; }
    }
    private final Profile[] profiles = new Profile[] {
            new Profile(4.0f,  0.22f, 0.06f), // 柔らかめ
            new Profile(3.0f,  0.35f, 0.05f), // くっきり・速い
            new Profile(5.5f,  0.16f, 0.08f), // ゆったり・減衰強
    };
    private int profileIdx = 0;

    // 時間管理
    private float t = 0.0f;       // 経過フレーム（連続値）
    private float dt = 1.0f;      // 1ステップ分の時間（速度と合わせて実効速度を調整）

    // 水滴（イベント）管理：複数の波源を重ねられるようにしておく（最大3）
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
                String event = msg.getData().getString(GlyphToy.MSG_GLYPH_TOY_DATA);
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

    @Nullable @Override
    public IBinder onBind(Intent intent) {
        init();
        return serviceMessenger.getBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        stopTimer();
        if (mGM != null) {
            try { mGM.closeAppMatrix(); } catch (Throwable ignored) {}
            mGM.unInit();
        }
        mGM = null;
        mCallback = null;
        return false;
    }

    private void init() {
        mGM = GlyphMatrixManager.getInstance(getApplicationContext());
        mCallback = new GlyphMatrixManager.Callback() {
            @Override public void onServiceConnected(ComponentName name) {
                mGM.register(Glyph.DEVICE_23112);
                // 初期状態：中心に水滴1つ
                resetScene();
                renderAndPresent();
                startTimer(40); // 25fps相当（=40ms）
            }
            @Override public void onServiceDisconnected(ComponentName name) {}
        };
        mGM.init(mCallback);
    }

    // ===== 長押しアクション =====
    private void onLongPress() {
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
        // ageや距離減衰でほぼ無影響なら解放
        for (int i = 0; i < drops.length; i++) {
            Drop d = drops[i];
            if (d == null) continue;
            if (d.age > 400f) { drops[i] = null; }
        }
        // すべて消えたら1つ追加して静的になり過ぎないように
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

        // 画素ループ（左上→右下）
        int idx = 0;
        for (int j = 0; j < H; j++) {
            for (int i = 0; i < W; i++, idx++) {
                // 円マスク（実質表示領域）
                float dx = i - CX;
                float dy = j - CY;
                float rFromCenter = (float)Math.sqrt(dx*dx + dy*dy);
                if (rFromCenter > RADIUS + 0.5f) { frameBuf[idx] = 0xFF000000; continue; }

                // （オプション）外周なだらかマスク：円の縁をソフトに
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

                // sum ∈ [-? , +?] を 0..255 へマッピング
                // ベース輝度をやや下げ、波が"白く浮かぶ"感じに（お好みで）
                float base = 12f;
                float scale = 115f; // コントラスト
                float val = base + scale * sum;
                // マスク適用
                val *= mask;

                // クリップ
                if (val < 0f)   val = 0f;
                if (val > 255f) val = 255f;
                int iv = (int)(val + 0.5f);

                // ARGB：白の明度のみ変える
                int argb = 0xFF000000 | (iv << 16) | (iv << 8) | iv;
                frameBuf[idx] = argb;
            }
        }

        // 生配列でフレーム更新（Toyサービスなので setMatrixFrame を使用）
        mGM.setMatrixFrame(frameBuf);
    }

    // 円縁を柔らかくするスムーズステップ
    private static float smoothstep(float edge0, float edge1, float x) {
        float t = clamp01((x - edge0) / (edge1 - edge0));
        return t * t * (3f - 2f * t);
    }
    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    // 水滴の"鳴き"を時間で包絡（0→最大→減衰）
    private static float envelope(float age) {
        // 立ち上がり素早く→ゆっくり減衰（お好みで調整）
        float a = age;
        float attack = 6f;
        float fade   = 0.004f; // 小さいほどゆっくり消える
        float att = 1f - (float)Math.exp(-a / attack);
        float rel = (float)Math.exp(-fade * a);
        return att * rel;
    }
    */
}
