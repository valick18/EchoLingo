package com.example.germanspreach;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
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

public class LearnedActivity extends AppCompatActivity {

    private LinearLayout learnedContainer;
    private TextView btnBack, btnListenAll;
    private TextToSpeech tts;
    private Handler handler = new Handler();
    private List<PhraseItem> learnedItems = new ArrayList<>();
    private boolean isPlaying = false;
    private int playIndex = 0;
    private SharedPreferences prefs;
    private Set<String> learnedWords;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_learned);

        prefs = getSharedPreferences("GermanLearen", MODE_PRIVATE);

        learnedContainer = findViewById(R.id.learned_container);
        btnBack = findViewById(R.id.btn_back);
        btnListenAll = findViewById(R.id.btn_listen_all);

        btnBack.setOnClickListener(v -> finish());
        btnListenAll.setOnClickListener(v -> togglePlayAll());

        initTTS();
        buildUI();
    }

    private void buildUI() {
        learnedContainer.removeAllViews();
        learnedItems.clear();
        learnedWords = new HashSet<>(prefs.getStringSet("learned_words", new HashSet<>()));

        if (learnedWords.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("Немає вивчених слів.");
            tv.setTextColor(getResources().getColor(R.color.text_muted));
            tv.setTextSize(16f);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(0, 48, 0, 0);
            tv.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            learnedContainer.addView(tv);
            btnListenAll.setVisibility(View.GONE);
            return;
        }

        Map<String, List<PhraseItem>> allData = PhraseProvider.getPhrasesByTopic(this);

        for (Map.Entry<String, List<PhraseItem>> entry : allData.entrySet()) {
            String topic = entry.getKey();
            List<PhraseItem> topicLearned = new ArrayList<>();

            for (PhraseItem item : entry.getValue()) {
                if (learnedWords.contains(item.getWordDe())) {
                    topicLearned.add(item);
                    learnedItems.add(item);
                }
            }
            if (topicLearned.isEmpty())
                continue;

            // Topic header
            TextView tvHeader = new TextView(this);
            tvHeader.setText(topic);
            tvHeader.setTextColor(getResources().getColor(R.color.primary));
            tvHeader.setTextSize(15f);
            tvHeader.setTypeface(null, android.graphics.Typeface.BOLD);
            tvHeader.setPadding(0, 28, 0, 8);
            tvHeader.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            learnedContainer.addView(tvHeader);

            // Each word row
            for (PhraseItem item : topicLearned) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rowLp.setMargins(0, 4, 0, 4);
                row.setLayoutParams(rowLp);

                // Word text (tap to hear)
                TextView tvWord = new TextView(this);
                tvWord.setText("• " + item.getWordDe() + " — " + item.getWordUk());
                tvWord.setTextColor(getResources().getColor(R.color.text_secondary));
                tvWord.setTextSize(15f);
                tvWord.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                tvWord.setOnClickListener(v -> {
                    if (tts != null) {
                        tts.setLanguage(Locale.GERMANY);
                        tts.speak(item.getWordDe(), TextToSpeech.QUEUE_FLUSH, null, null);
                    }
                });
                row.addView(tvWord);

                // ↩ Re-add to study queue button
                TextView btnRestudy = new TextView(this);
                btnRestudy.setText("↩");
                btnRestudy.setTextColor(getResources().getColor(R.color.accent));
                btnRestudy.setTextSize(18f);
                btnRestudy.setPadding(16, 8, 8, 8);
                btnRestudy.setOnClickListener(v -> {
                    restudyWord(item, topic);
                    buildUI(); // refresh list
                    Toast.makeText(this, "↩ Додано на повторення: " + item.getWordDe(), Toast.LENGTH_SHORT).show();
                });
                row.addView(btnRestudy);

                learnedContainer.addView(row);
            }
        }
    }

    /** Moves a word from learned back to the study queue for its topic. */
    private void restudyWord(PhraseItem item, String topic) {
        String word = item.getWordDe();

        // Remove from learned_words
        Set<String> learned = new HashSet<>(prefs.getStringSet("learned_words", new HashSet<>()));
        learned.remove(word);
        prefs.edit().putStringSet("learned_words", learned).apply();

        // Add back to study queue for that topic
        String studyKey = "study_" + topic;
        Set<String> queue = new HashSet<>(prefs.getStringSet(studyKey, new HashSet<>()));
        queue.add(word);
        prefs.edit().putStringSet(studyKey, queue).apply();
    }

    private void togglePlayAll() {
        if (isPlaying) {
            stopPlayback();
        } else {
            if (learnedItems.isEmpty())
                return;
            isPlaying = true;
            playIndex = 0;
            btnListenAll.setText("⏹");
            playNext();
        }
    }

    private void playNext() {
        if (!isPlaying || playIndex >= learnedItems.size()) {
            stopPlayback();
            return;
        }
        PhraseItem item = learnedItems.get(playIndex);
        if (tts != null) {
            tts.setLanguage(Locale.GERMANY);
            tts.speak(item.getWordDe(), TextToSpeech.QUEUE_FLUSH, null, "w" + playIndex);
            handler.postDelayed(() -> {
                if (!isPlaying)
                    return;
                tts.setLanguage(new Locale("uk", "UA"));
                tts.speak(item.getWordUk(), TextToSpeech.QUEUE_FLUSH, null, "u" + playIndex);
                handler.postDelayed(() -> {
                    playIndex++;
                    playNext();
                }, 1800);
            }, 1500);
        }
    }

    private void stopPlayback() {
        isPlaying = false;
        if (tts != null)
            tts.stop();
        handler.removeCallbacksAndMessages(null);
        btnListenAll.setText("🎧");
    }

    private void initTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS)
                tts.setLanguage(Locale.GERMANY);
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
