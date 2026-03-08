package com.example.germanspreach;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    private SharedPreferences prefs;

    private TextView tvRepCount, tvSpeedValue, btnBack, btnRepMinus, btnRepPlus;
    private CheckBox cbAskQuestions;
    private LinearLayout btnToggleQuestions;
    private SeekBar seekSpeed;

    private int repetitions = 1;
    private boolean askQuestions = false;
    private float speechSpeed = 1.0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("GermanLearen", MODE_PRIVATE);

        // Load settings
        repetitions = prefs.getInt("pref_repetitions", 1);
        askQuestions = prefs.getBoolean("pref_ask_questions", false);
        speechSpeed = prefs.getFloat("pref_speech_speed", 1.0f);

        initViews();
        updateUI();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btn_back);
        tvRepCount = findViewById(R.id.tv_rep_count);
        btnRepMinus = findViewById(R.id.btn_rep_minus);
        btnRepPlus = findViewById(R.id.btn_rep_plus);
        cbAskQuestions = findViewById(R.id.cb_ask_questions);
        btnToggleQuestions = findViewById(R.id.btn_toggle_questions);
        seekSpeed = findViewById(R.id.seek_speed);
        tvSpeedValue = findViewById(R.id.tv_speed_value);

        btnBack.setOnClickListener(v -> finish());

        // Repetitions
        btnRepMinus.setOnClickListener(v -> {
            if (repetitions > 1) {
                repetitions--;
                saveSettings();
                updateUI();
            }
        });

        btnRepPlus.setOnClickListener(v -> {
            if (repetitions < 5) {
                repetitions++;
                saveSettings();
                updateUI();
            }
        });

        // Ask questions toggle
        btnToggleQuestions.setOnClickListener(v -> {
            askQuestions = !askQuestions;
            saveSettings();
            updateUI();
        });

        // Speech speed (slider 0 to 10 mapped to 0.5x to 1.5x)
        int progress = (int) ((speechSpeed - 0.5f) * 10f);
        seekSpeed.setProgress(progress);

        seekSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    speechSpeed = 0.5f + (progress / 10f);
                    updateUI();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                saveSettings();
            }
        });
    }

    private void updateUI() {
        tvRepCount.setText(String.valueOf(repetitions));
        cbAskQuestions.setChecked(askQuestions);
        tvSpeedValue.setText(String.format(java.util.Locale.US, "%.1fx", speechSpeed));
    }

    private void saveSettings() {
        prefs.edit()
                .putInt("pref_repetitions", repetitions)
                .putBoolean("pref_ask_questions", askQuestions)
                .putFloat("pref_speech_speed", speechSpeed)
                .apply();
    }
}
