package com.hamon.yukknd;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button simulatorButton = findViewById(R.id.simulator_button);
        Button toyInfoButton = findViewById(R.id.toy_info_button);
        simulatorButton.setOnClickListener(v -> {
            Intent intent = new Intent(this, com.hamon.yukknd.simulator.RippleWaveSimulatorActivity.class);
            startActivity(intent);
        });
        toyInfoButton.setOnClickListener(v -> {
            showToyInfo();
        });
    }

    private void showToyInfo() {
        TextView infoText = findViewById(R.id.info_text);
        infoText.setText(R.string.toy_info_text);
        infoText.setVisibility(View.VISIBLE);
    }
}
