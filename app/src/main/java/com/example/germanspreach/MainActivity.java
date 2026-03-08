package com.example.germanspreach;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.germanspreach.data.PhraseProvider;
import com.example.germanspreach.models.PhraseItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private LinearLayout topicsList;
    private TextView tvTotalLearned;
    private TextView btnViewLearned, btnNavLearned;
    private SharedPreferences prefs;
    private Set<String> learnedWords;
    private Map<String, List<PhraseItem>> phraseData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("GermanLearen", MODE_PRIVATE);
        phraseData = PhraseProvider.getPhrasesByTopic(this);

        topicsList = findViewById(R.id.topics_list);
        tvTotalLearned = findViewById(R.id.tv_total_learned);
        btnViewLearned = findViewById(R.id.btn_view_learned);
        btnNavLearned = findViewById(R.id.btn_nav_learned);
        TextView btnSettings = findViewById(R.id.btn_settings);

        btnViewLearned.setOnClickListener(v -> startActivity(new Intent(this, LearnedActivity.class)));

        // "🎧 Слухати" → plays study queue words with phrases (words swiped left)
        btnNavLearned.setOnClickListener(v -> startActivity(new Intent(this, ListenModeActivity.class)));

        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        learnedWords = new HashSet<>(prefs.getStringSet("learned_words", new HashSet<>()));
        tvTotalLearned.setText("Вивчено слів: " + learnedWords.size());
        renderTopics();
    }

    private void renderTopics() {
        topicsList.removeAllViews();

        for (Map.Entry<String, List<PhraseItem>> entry : phraseData.entrySet()) {
            String topic = entry.getKey();
            List<PhraseItem> items = entry.getValue();

            View card = getLayoutInflater().inflate(R.layout.item_topic, topicsList, false);

            TextView tvName = card.findViewById(R.id.tv_topic_name);
            TextView tvWordCount = card.findViewById(R.id.tv_word_count);
            TextView tvProgressPct = card.findViewById(R.id.tv_progress_pct);
            ProgressBar pb = card.findViewById(R.id.pb_topic);

            tvName.setText(topic);
            tvWordCount.setText(items.size() + " слів");

            int learned = 0;
            for (PhraseItem item : items) {
                if (learnedWords.contains(item.getWordDe()))
                    learned++;
            }
            int pct = items.isEmpty() ? 0 : (learned * 100) / items.size();

            if (pct == 100) {
                tvProgressPct.setText("✓ Вивчено");
                tvProgressPct.setTextColor(getResources().getColor(R.color.accent_green));
            } else {
                tvProgressPct.setText(pct + "%");
                tvProgressPct.setTextColor(getResources().getColor(R.color.primary));
            }
            pb.setProgress(pct);

            // Tap card → open flashcard mode
            card.setOnClickListener(v -> {
                Intent intent = new Intent(this, FlashcardActivity.class);
                intent.putExtra(FlashcardActivity.EXTRA_TOPIC, topic);
                startActivity(intent);
            });

            TextView btnTopicListen = card.findViewById(R.id.btn_topic_listen);
            btnTopicListen.setOnClickListener(v -> {
                Intent intent = new Intent(this, ListenModeActivity.class);
                intent.putExtra("topic_to_listen", topic);
                startActivity(intent);
            });

            topicsList.addView(card);
        }
    }
}
