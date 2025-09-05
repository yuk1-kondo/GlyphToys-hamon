package com.example.ripplewavetoy;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/**
 * MainActivity
 * エミュレーター用のメイン画面
 * シミュレーターを起動するためのエントリーポイント
 */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        Button simulatorButton = findViewById(R.id.simulator_button);
        Button toyInfoButton = findViewById(R.id.toy_info_button);
        
        simulatorButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, com.example.ripplewavetoy.simulator.RippleWaveSimulatorActivity.class);
            startActivity(intent);
        });
        
        toyInfoButton.setOnClickListener(v -> {
            // トイの情報を表示
            showToyInfo();
        });
    }
    
    private void showToyInfo() {
        TextView infoText = findViewById(R.id.info_text);
        String info = "Hamon について\n\n" +
                     "• 静寂の水面に広がる波紋を表現\n" +
                     "• モード: 禅 / 波紋 / 雨\n" +
                     "  - 禅: 一滴が静かに広がる\n" +
                     "  - 波紋: 先頭リングに後続リングが追従\n" +
                     "  - 雨: ランダムな多点の雨粒、シェイクでバースト\n" +
                     "• AOD対応で省電力の演出\n" +
                     "• 実機ではGlyph Matrixに表示\n\n" +
                     "※ このシミュレーターは25x25グリッドを画面に表示します";
        infoText.setText(info);
        infoText.setVisibility(View.VISIBLE);
    }
}
