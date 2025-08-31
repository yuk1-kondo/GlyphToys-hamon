package com.example.ripplewavetoy.simulator;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.widget.ImageView;
import android.os.Handler;
import android.os.Looper;
import com.example.ripplewavetoy.R;

import java.util.Timer;
import java.util.TimerTask;

/**
 * RippleWaveSimulator
 * エミュレーター用の波紋シミュレーター
 * 25x25のグリッドを画面に表示して波紋の動作を確認可能
 */
public class RippleWaveSimulatorActivity extends Activity {

    private ImageView matrixView;
    private TextView profileText;
    private Button longPressButton;
    private Button aodButton;
    
    // シミュレーション用の変数（RippleWaveToyServiceと同じ）
    private static final int W = 25;
    private static final int H = 25;
    private static final float CX = (W - 1) * 0.5f;
    private static final float CY = (H - 1) * 0.5f;
    private static final float RADIUS = 12.4f;
    
    private static class Profile {
        final float wavelength, speed, damping;
        final String name;
        Profile(float wl, float v, float a, String n){ 
            wavelength=wl; speed=v; damping=a; name=n; 
        }
    }
    private final Profile[] profiles = new Profile[] {
            new Profile(4.0f,  0.22f, 0.06f, "柔らかめ"),
            new Profile(3.0f,  0.35f, 0.05f, "くっきり・速い"),
            new Profile(5.5f,  0.16f, 0.08f, "ゆったり・減衰強"),
    };
    private int profileIdx = 0;
    
    private float t = 0.0f;
    private float dt = 1.0f;
    
    private static class Drop {
        final float x, y;
        float age;
        Drop(float x, float y){ this.x=x; this.y=y; this.age=0f; }
    }
    private final Drop[] drops = new Drop[] {
            new Drop(CX, CY), null, null
    };
    
    private Timer timer;
    private final int[] frameBuf = new int[W * H];
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simulator);
        
        matrixView = findViewById(R.id.matrix_view);
        profileText = findViewById(R.id.profile_text);
        longPressButton = findViewById(R.id.long_press_button);
        aodButton = findViewById(R.id.aod_button);
        
        longPressButton.setOnClickListener(v -> onLongPress());
        aodButton.setOnClickListener(v -> onAOD());
        
        initSimulation();
        updateProfileText();
    }
    
    private void initSimulation() {
        resetScene();
        renderAndPresent();
        startTimer(100); // 10fps for emulator
    }
    
    private void onLongPress() {
        profileIdx = (profileIdx + 1) % profiles.length;
        addDrop(CX, CY);
        updateProfileText();
    }
    
    private void onAOD() {
        step();
        renderAndPresent();
    }
    
    private void updateProfileText() {
        Profile pf = profiles[profileIdx];
        profileText.setText(String.format("プロファイル: %s (λ=%.1f, v=%.2f, α=%.2f)", 
            pf.name, pf.wavelength, pf.speed, pf.damping));
    }
    
    private void startTimer(long periodMs) {
        stopTimer();
        timer = new Timer("RippleSimTimer");
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() {
                step();
                uiHandler.post(() -> renderAndPresent());
            }
        }, 0, periodMs);
    }
    
    private void stopTimer() {
        if (timer != null) { 
            try { timer.cancel(); } catch (Throwable ignored) {} 
            timer = null; 
        }
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
        drops[0] = new Drop(CX, CY);
    }
    
    private void addDrop(float x, float y) {
        for (int i = 0; i < drops.length; i++) {
            if (drops[i] == null) { drops[i] = new Drop(x, y); return; }
        }
        int oldest = 0;
        float maxAge = -1f;
        for (int i = 0; i < drops.length; i++) {
            if (drops[i] != null && drops[i].age > maxAge) { 
                maxAge = drops[i].age; oldest = i; 
            }
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
    
    private void renderAndPresent() {
        final Profile pf = profiles[profileIdx];
        final float wl = pf.wavelength;
        final float v = pf.speed;
        final float a = pf.damping;
        
        int idx = 0;
        for (int j = 0; j < H; j++) {
            for (int i = 0; i < W; i++, idx++) {
                float dx = i - CX;
                float dy = j - CY;
                float rFromCenter = (float)Math.sqrt(dx*dx + dy*dy);
                if (rFromCenter > RADIUS + 0.5f) { 
                    frameBuf[idx] = 0xFF000000; continue; 
                }
                
                float mask = smoothstep(RADIUS + 0.5f, RADIUS - 1.0f, rFromCenter);
                
                float sum = 0f;
                for (Drop d : drops) {
                    if (d == null) continue;
                    float rx = i - d.x;
                    float ry = j - d.y;
                    float r = (float)Math.sqrt(rx*rx + ry*ry);
                    
                    float phase = (float)(2.0 * Math.PI * ((r - v * d.age) / wl));
                    float amp = (float)Math.exp(-a * r) * envelope(d.age);
                    sum += amp * (float)Math.cos(phase);
                }
                
                float base = 12f;
                float scale = 115f;
                float val = base + scale * sum;
                val *= mask;
                
                if (val < 0f) val = 0f;
                if (val > 255f) val = 255f;
                int iv = (int)(val + 0.5f);
                
                int argb = 0xFF000000 | (iv << 16) | (iv << 8) | iv;
                frameBuf[idx] = argb;
            }
        }
        
        // 25x25のグリッドを画面に表示
        displayMatrix();
    }
    
    private void displayMatrix() {
        // 25x25のグリッドを拡大して表示
        int scale = 20; // 1ピクセルを20x20に拡大
        int displayW = W * scale;
        int displayH = H * scale;
        
        Bitmap bitmap = Bitmap.createBitmap(displayW, displayH, Bitmap.Config.ARGB_8888);
        
        for (int j = 0; j < H; j++) {
            for (int i = 0; i < W; i++) {
                int color = frameBuf[j * W + i];
                
                // このピクセルをscale x scaleの大きさで描画
                for (int dy = 0; dy < scale; dy++) {
                    for (int dx = 0; dx < scale; dx++) {
                        bitmap.setPixel(i * scale + dx, j * scale + dy, color);
                    }
                }
            }
        }
        
        matrixView.setImageBitmap(bitmap);
    }
    
    private static float smoothstep(float edge0, float edge1, float x) {
        float t = clamp01((x - edge0) / (edge1 - edge0));
        return t * t * (3f - 2f * t);
    }
    
    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }
    
    private static float envelope(float age) {
        float a = age;
        float attack = 6f;
        float fade = 0.004f;
        float att = 1f - (float)Math.exp(-a / attack);
        float rel = (float)Math.exp(-fade * a);
        return att * rel;
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTimer();
    }
}
