package com.example.germanspreach;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
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
        learnedWords = new HashSet<>(prefs.getStringSet("learned_words", new HashSet<>()));

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

        if (learnedWords.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText(getString(R.string.msg_no_learned));
            tv.setTextColor(getResources().getColor(R.color.text_muted));
            tv.setTextSize(16f);
            tv.setPadding(0, 32, 0, 0);
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            learnedContainer.addView(tv);
            btnListenAll.setVisibility(View.GONE);
            return;
        }

        Map<String, List<PhraseItem>> allData = PhraseProvider.getPhrasesByTopic(this);
        learnedItems.clear();

        for (Map.Entry<String, List<PhraseItem>> entry : allData.entrySet()) {
            String topic = entry.getKey();
            List<PhraseItem> topicItems = entry.getValue();
            List<PhraseItem> topicLearned = new ArrayList<>();

            for (PhraseItem item : topicItems) {
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
            tvHeader.setPadding(0, 24, 0, 8);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            tvHeader.setLayoutParams(lp);
            learnedContainer.addView(tvHeader);

            // Play topic button
            TextView btnPlayTopic = new TextView(this);
            btnPlayTopic.setText("🎧 Прослухати " + topic);
            btnPlayTopic.setTextColor(getResources().getColor(R.color.accent));
            btnPlayTopic.setTextSize(13f);
            btnPlayTopic.setPadding(16, 10, 16, 10);
            btnPlayTopic.setBackground(getResources().getDrawable(R.drawable.btn_glass));
            LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lpBtn.setMargins(0, 0, 0, 16);
            btnPlayTopic.setLayoutParams(lpBtn);
            List<PhraseItem> finalTopicLearned = topicLearned;
            btnPlayTopic.setOnClickListener(v -> playList(finalTopicLearned));
            learnedContainer.addView(btnPlayTopic);

            // Words
            for (PhraseItem item : topicLearned) {
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
                learnedContainer.addView(tvWord);
            }
        }
    }

    private void togglePlayAll() {
        if (isPlaying) {
            stopPlayback();
        } else {
            playList(learnedItems);
        }
    }

    private void playList(List<PhraseItem> items) {
        if (items.isEmpty()) {
            Toast.makeText(this, "Немає слів", Toast.LENGTH_SHORT).show();
            return;
        }
        isPlaying = true;
        playIndex = 0;
        btnListenAll.setText("⏹");
        playNextWord(items);
    }

    private void playNextWord(List<PhraseItem> items) {
        if (!isPlaying || playIndex >= items.size()) {
            stopPlayback();
            return;
        }
        PhraseItem item = items.get(playIndex);
        if (tts != null) {
            tts.setLanguage(Locale.GERMANY);
            tts.speak(item.getWordDe(), TextToSpeech.QUEUE_FLUSH, null, "w" + playIndex);

            handler.postDelayed(() -> {
                if (tts != null && isPlaying) {
                    tts.setLanguage(new Locale("uk", "UA"));
                    tts.speak(item.getWordUk(), TextToSpeech.QUEUE_FLUSH, null, "u" + playIndex);
                    handler.postDelayed(() -> {
                        playIndex++;
                        playNextWord(items);
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
        btnListenAll.setText("🎧");
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
