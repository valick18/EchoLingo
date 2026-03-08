package com.example.germanspreach;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.germanspreach.data.PhraseProvider;
import com.example.germanspreach.models.PhraseItem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class StudyActivity extends AppCompatActivity {

    private LinearLayout studyContainer;
    private TextView btnBack, btnListenStudy;
    private TextToSpeech tts;
    private Handler handler = new Handler();
    private List<PhraseItem> studyItems = new ArrayList<>();
    private boolean isPlaying = false;
    private int playIndex = 0;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_study);

        prefs = getSharedPreferences("GermanLearen", MODE_PRIVATE);

        studyContainer = findViewById(R.id.study_container);
        btnBack = findViewById(R.id.btn_back);
        btnListenStudy = findViewById(R.id.btn_listen_study);

        btnBack.setOnClickListener(v -> finish());
        btnListenStudy.setOnClickListener(v -> togglePlay());

        initTTS();
        buildUI();
    }

    private void buildUI() {
        studyContainer.removeAllViews();
        studyItems.clear();

        Map<String, List<PhraseItem>> allData = PhraseProvider.getPhrasesByTopic(this);
        Set<String> learnedWords = new HashSet<>(prefs.getStringSet("learned_words", new HashSet<>()));

        boolean hasAny = false;
        for (Map.Entry<String, List<PhraseItem>> entry : allData.entrySet()) {
            String topic = entry.getKey();
            String studyKey = "study_" + topic;
            Set<String> studyQueue = prefs.getStringSet(studyKey, new HashSet<>());

            if (studyQueue.isEmpty())
                continue;

            // Filter out already learned
            List<PhraseItem> topicStudy = new ArrayList<>();
            for (PhraseItem item : entry.getValue()) {
                if (studyQueue.contains(item.getWordDe()) && !learnedWords.contains(item.getWordDe())) {
                    topicStudy.add(item);
                    studyItems.add(item);
                }
            }

            if (topicStudy.isEmpty())
                continue;
            hasAny = true;

            // Topic header
            TextView tvHeader = new TextView(this);
            tvHeader.setText(topic + " (" + topicStudy.size() + ")");
            tvHeader.setTextColor(getResources().getColor(R.color.primary));
            tvHeader.setTextSize(15f);
            tvHeader.setPadding(0, 24, 0, 8);
            tvHeader.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            studyContainer.addView(tvHeader);

            for (PhraseItem item : topicStudy) {
                TextView tvWord = new TextView(this);
                tvWord.setText("  • " + item.getWordDe() + " — " + item.getWordUk());
                tvWord.setTextColor(getResources().getColor(R.color.text_secondary));
                tvWord.setTextSize(15f);
                tvWord.setPadding(0, 4, 0, 4);
                tvWord.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                tvWord.setOnClickListener(v -> {
                    if (tts != null) {
                        tts.setLanguage(Locale.GERMANY);
                        tts.speak(item.getWordDe(), TextToSpeech.QUEUE_FLUSH, null, null);
                    }
                });
                studyContainer.addView(tvWord);
            }
        }

        if (!hasAny) {
            TextView tv = new TextView(this);
            tv.setText(getString(R.string.msg_no_study));
            tv.setTextColor(getResources().getColor(R.color.text_muted));
            tv.setTextSize(16f);
            tv.setPadding(0, 32, 0, 0);
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            studyContainer.addView(tv);
        }
    }

    private void togglePlay() {
        if (isPlaying) {
            stopPlayback();
        } else {
            if (studyItems.isEmpty()) {
                Toast.makeText(this, getString(R.string.msg_no_study), Toast.LENGTH_SHORT).show();
                return;
            }
            isPlaying = true;
            playIndex = 0;
            btnListenStudy.setText("⏹");
            playNext();
        }
    }

    private void playNext() {
        if (!isPlaying || playIndex >= studyItems.size()) {
            stopPlayback();
            return;
        }
        PhraseItem item = studyItems.get(playIndex);
        if (tts != null) {
            tts.setLanguage(Locale.GERMANY);
            tts.speak(item.getWordDe(), TextToSpeech.QUEUE_FLUSH, null, "de" + playIndex);
            handler.postDelayed(() -> {
                if (isPlaying && tts != null) {
                    tts.setLanguage(new Locale("uk", "UA"));
                    tts.speak(item.getWordUk(), TextToSpeech.QUEUE_FLUSH, null, "uk" + playIndex);
                    handler.postDelayed(() -> {
                        playIndex++;
                        playNext();
                    }, 2000);
                }
            }, 1500);
        }
    }

    private void stopPlayback() {
        isPlaying = false;
        if (tts != null)
            tts.stop();
        handler.removeCallbacksAndMessages(null);
        btnListenStudy.setText("🎧");
    }

    private void initTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.GERMANY);
            }
        });
    }

    @Override
    protected void onDestroy() {
        stopPlayback();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
