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
        String info = "RippleWaveToy について\n\n" +
                     "• 中心から同心円の波紋が外へ伝播\n" +
                     "• 3つのプロファイルで波の特性を変更\n" +
                     "• 長押しで新しい水滴追加 + プロファイル切替\n" +
                     "• AOD対応で省電力モード\n" +
                     "• 実機ではGlyph Matrixに表示\n\n" +
                     "※ このシミュレーターは25x25グリッドを画面に表示します";
        infoText.setText(info);
        infoText.setVisibility(View.VISIBLE);
    }
}
