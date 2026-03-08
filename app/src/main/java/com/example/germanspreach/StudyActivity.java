package com.example.germanspreach;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.example.germanspreach.data.PhraseProvider;
import com.example.germanspreach.models.PhraseItem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StudyActivity extends AppCompatActivity {

    private LinearLayout studyContainer;
    private TextView btnBack, btnSave;
    private SharedPreferences prefs;
    private final List<CheckBox> checkBoxes = new ArrayList<>();
    private final List<PhraseItem> studyItems = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_study);

        prefs = getSharedPreferences("GermanLearen", MODE_PRIVATE);
        studyContainer = findViewById(R.id.study_container);
        btnBack = findViewById(R.id.btn_back);
        btnSave = findViewById(R.id.btn_save);

        btnBack.setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> saveChecked());

        buildChecklist();
    }

    private void buildChecklist() {
        studyContainer.removeAllViews();
        checkBoxes.clear();
        studyItems.clear();

        Map<String, List<PhraseItem>> allData = PhraseProvider.getPhrasesByTopic(this);
        Set<String> learnedWords = new HashSet<>(prefs.getStringSet("learned_words", new HashSet<>()));

        boolean hasAny = false;
        String specificTopic = getIntent().getStringExtra("topic_to_study");

        for (Map.Entry<String, List<PhraseItem>> entry : allData.entrySet()) {
            String topic = entry.getKey();

            if (specificTopic != null && !topic.equals(specificTopic)) {
                continue;
            }

            String studyKey = "study_" + topic;
            Set<String> studyQueue = prefs.getStringSet(studyKey, new HashSet<>());

            // If we're not in a specific topic, skip topics with an empty study queue
            if (specificTopic == null && studyQueue.isEmpty()) {
                continue;
            }

            List<PhraseItem> topicItems = new ArrayList<>();
            for (PhraseItem item : entry.getValue()) {
                boolean shouldInclude = false;
                if (specificTopic != null) {
                    // For topic listen mode: show all unlearned words in the topic
                    shouldInclude = true;
                } else {
                    // For global listen mode: show only words that were swiped left
                    shouldInclude = studyQueue.contains(item.getWordDe());
                }

                if (shouldInclude && !learnedWords.contains(item.getWordDe())) {
                    topicItems.add(item);
                }
            }

            if (topicItems.isEmpty())
                continue;

            hasAny = true;

            // Topic header
            TextView tvHeader = new TextView(this);
            tvHeader.setText(topic + "  (" + topicItems.size() + ")");
            tvHeader.setTextColor(ContextCompat.getColor(this, R.color.primary));
            tvHeader.setTextSize(15f);
            tvHeader.setTypeface(null, android.graphics.Typeface.BOLD);
            tvHeader.setPadding(0, 28, 0, 10);
            tvHeader.setLayoutParams(fullWidthLp());
            studyContainer.addView(tvHeader);

            // Each word: card with checkbox
            for (PhraseItem item : topicItems) {
                // Card row background
                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.HORIZONTAL);
                card.setGravity(Gravity.CENTER_VERTICAL);
                card.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_checklist_item));
                card.setPadding(32, 24, 32, 24);
                LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                cardLp.setMargins(0, 0, 0, 8);
                card.setLayoutParams(cardLp);

                // Word text
                TextView tvWord = new TextView(this);
                tvWord.setText(item.getWordDe() + "  —  " + item.getWordUk());
                tvWord.setTextColor(ContextCompat.getColor(this, R.color.text_main));
                tvWord.setTextSize(15f);
                tvWord.setLayoutParams(new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                card.addView(tvWord);

                // Checkbox — styled for dark theme using a custom TextView toggle
                CheckBox cb = new CheckBox(this);
                cb.setButtonTintList(android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(this, R.color.primary)));
                cb.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                card.addView(cb);

                // Tap whole card toggles checkbox
                card.setOnClickListener(v -> cb.setChecked(!cb.isChecked()));

                checkBoxes.add(cb);
                studyItems.add(item);
                studyContainer.addView(card);
            }
        }

        if (!hasAny) {
            TextView tv = new TextView(this);
            tv.setText("Список порожній.\nСвайпніть ← на картці, щоб додати слова.");
            tv.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
            tv.setTextSize(15f);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(0, 48, 0, 0);
            tv.setLayoutParams(fullWidthLp());
            studyContainer.addView(tv);
            btnSave.setVisibility(View.GONE);
        } else {
            btnSave.setVisibility(View.VISIBLE);
        }
    }

    private void saveChecked() {
        Map<String, List<PhraseItem>> allData = PhraseProvider.getPhrasesByTopic(this);
        Set<String> learnedWords = new HashSet<>(prefs.getStringSet("learned_words", new HashSet<>()));
        int count = 0;

        for (int i = 0; i < checkBoxes.size(); i++) {
            if (!checkBoxes.get(i).isChecked())
                continue;
            String word = studyItems.get(i).getWordDe();
            learnedWords.add(word);
            // Remove from every topic's study queue
            for (String topic : allData.keySet()) {
                String key = "study_" + topic;
                Set<String> queue = new HashSet<>(prefs.getStringSet(key, new HashSet<>()));
                if (queue.remove(word))
                    prefs.edit().putStringSet(key, queue).apply();
            }
            count++;
        }

        prefs.edit().putStringSet("learned_words", learnedWords).apply();

        if (count > 0) {
            Toast.makeText(this, "✅ " + count + " слів позначено як вивчені", Toast.LENGTH_SHORT).show();
            buildChecklist(); // refresh — marked words disappear immediately
        } else {
            Toast.makeText(this, "Не відмічено жодного слова", Toast.LENGTH_SHORT).show();
        }
    }

    private LinearLayout.LayoutParams fullWidthLp() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }
}
