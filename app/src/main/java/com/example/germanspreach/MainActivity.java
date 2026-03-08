package com.example.germanspreach;

import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.germanspreach.data.PhraseProvider;
import com.example.germanspreach.models.PhraseItem;
import com.example.germanspreach.R;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import android.content.SharedPreferences;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private Map<String, List<PhraseItem>> phraseData;
    private List<PhraseItem> currentSet;
    private List<PhraseItem> wordPool = new ArrayList<>();
    private String poolTopic = "";
    private int setIndex = 0;
    private int step = 0; // 0: Word, 1-2: Phrase Rep, 3: Translation
    private boolean isPaused = false;
    private boolean isInQuiz = false;
    private TextToSpeech tts;
    private Locale germanLocale = Locale.GERMANY;
    private Locale ukrainianLocale = new Locale("uk", "UA");
    private java.util.Random random = new java.util.Random();
    private int currentPhraseIndex = -1;

    private View topicView, learningView, learnedDashboardView, selectionView;
    private LinearLayout topicsList, quizSection, learnedWordsContainer, selectionContainer;
    private TextView textDe, textUk, subPhrase, modeLabel, quizFeedback, tvTotalLearned;
    private EditText quizInput;
    private CheckBox cbShowQuiz;
    private Button btnAction, btnBack, btnLearned, btnViewLearned, btnDashboardBack, btnDashboardRepeat;
    private Button btnSelectWords, btnSelectionBack, btnPlaySelected, btnResetSelection;
    private SharedPreferences prefs;
    private Set<String> learnedWords;
    private Set<String> selectedWords;

    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        phraseData = PhraseProvider.getPhrasesByTopic(this);
        initUI();
        initTTS();
        updateLearnedCount();
    }

    private void initUI() {
        topicView = findViewById(R.id.topic_view);
        learningView = findViewById(R.id.learning_view);
        learnedDashboardView = findViewById(R.id.learned_dashboard_view);
        topicsList = findViewById(R.id.topics_list);
        quizSection = findViewById(R.id.quiz_section);
        learnedWordsContainer = findViewById(R.id.learned_words_container);
        
        selectionView = findViewById(R.id.selection_view);
        selectionContainer = findViewById(R.id.selection_container);
        btnSelectWords = findViewById(R.id.btn_select_words);
        btnSelectionBack = findViewById(R.id.btn_selection_back);
        btnPlaySelected = findViewById(R.id.btn_play_selected);
        btnResetSelection = findViewById(R.id.btn_reset_selection);

        textDe = findViewById(R.id.text_de);
        textUk = findViewById(R.id.text_uk);
        subPhrase = findViewById(R.id.sub_phrase);
        modeLabel = findViewById(R.id.mode_label);
        quizFeedback = findViewById(R.id.quiz_feedback);
        quizInput = findViewById(R.id.quiz_input);
        cbShowQuiz = findViewById(R.id.cb_show_quiz);
        
        btnAction = findViewById(R.id.btn_action);
        btnBack = findViewById(R.id.btn_back);
        btnLearned = findViewById(R.id.btn_learned);
        tvTotalLearned = findViewById(R.id.tv_total_learned);
        btnViewLearned = findViewById(R.id.btn_view_learned);
        btnDashboardBack = findViewById(R.id.btn_dashboard_back);
        btnDashboardRepeat = findViewById(R.id.btn_dashboard_repeat);

        prefs = getSharedPreferences("GermanLearen", MODE_PRIVATE);
        learnedWords = new HashSet<>(prefs.getStringSet("learned_words", new HashSet<>()));
        selectedWords = new HashSet<>(prefs.getStringSet("selected_words", new HashSet<>()));
        cbShowQuiz.setChecked(prefs.getBoolean("show_quiz", true));

        renderTopics();

        btnAction.setOnClickListener(v -> togglePause());
        btnBack.setOnClickListener(v -> stopLearning());
        btnLearned.setOnClickListener(v -> markCurrentAsLearned());
        btnViewLearned.setOnClickListener(v -> showLearnedWordsDashboard());
        btnSelectWords.setOnClickListener(v -> showSelectionDashboard());
        cbShowQuiz.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("show_quiz", isChecked).apply();
        });
        btnDashboardBack.setOnClickListener(v -> {
            learnedDashboardView.setVisibility(View.GONE);
            topicView.setVisibility(View.VISIBLE);
        });
        btnDashboardRepeat.setOnClickListener(v -> startRepeatingLearned());
        btnSelectionBack.setOnClickListener(v -> {
            selectionView.setVisibility(View.GONE);
            topicView.setVisibility(View.VISIBLE);
        });
        btnPlaySelected.setOnClickListener(v -> startSelectedPlayback());
        btnResetSelection.setOnClickListener(v -> resetAllSelections());
    }

    private void updateLearnedCount() {
        if (tvTotalLearned != null) {
            tvTotalLearned.setText(getString(R.string.label_total_learned, learnedWords.size()));
        }
    }

    private void showLearnedWordsDashboard() {
        if (learnedWords.isEmpty()) {
            Toast.makeText(this, R.string.msg_no_learned, Toast.LENGTH_SHORT).show();
            return;
        }

        topicView.setVisibility(View.GONE);
        learnedDashboardView.setVisibility(View.VISIBLE);
        learnedWordsContainer.removeAllViews();

        for (Map.Entry<String, List<PhraseItem>> entry : phraseData.entrySet()) {
            String topicName = entry.getKey();
            List<PhraseItem> items = entry.getValue();
            boolean hasLearned = false;

            for (PhraseItem item : items) {
                if (learnedWords.contains(item.getWordDe())) {
                    hasLearned = true;
                    break;
                }
            }

            if (hasLearned) {
                Button btnTopic = new Button(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, 0, 0, 16);
                btnTopic.setLayoutParams(lp);
                btnTopic.setText(topicName);
                btnTopic.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.glass_bg)));
                btnTopic.setTextColor(getResources().getColor(R.color.text_main));
                btnTopic.setAllCaps(false);
                btnTopic.setOnClickListener(v -> showWordsForLearnedTopic(topicName));
                learnedWordsContainer.addView(btnTopic);
            }
        }
    }

    private void showWordsForLearnedTopic(String topicName) {
        learnedWordsContainer.removeAllViews();

        TextView header = new TextView(this);
        header.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        header.setText("--- " + topicName + " ---");
        header.setTextColor(getResources().getColor(R.color.white));
        header.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18);
        header.setPadding(0, 16, 0, 16);
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        learnedWordsContainer.addView(header);

        List<PhraseItem> topicPhrases = phraseData.get(topicName);
        List<String> learnedInTopic = new ArrayList<>();
        if (topicPhrases != null) {
            for (PhraseItem item : topicPhrases) {
                if (learnedWords.contains(item.getWordDe())) {
                    learnedInTopic.add(item.getWordDe() + " - " + item.getWordUk());
                }
            }
        }

        Collections.sort(learnedInTopic);
        for (String entry : learnedInTopic) {
            TextView tv = new TextView(this);
            tv.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            tv.setText("• " + entry);
            tv.setTextColor(getResources().getColor(R.color.text_main));
            tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16);
            tv.setPadding(24, 8, 0, 8);
            learnedWordsContainer.addView(tv);
        }

        Button btnBackToTopics = new Button(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        lp.setMargins(0, 32, 0, 0);
        btnBackToTopics.setLayoutParams(lp);
        btnBackToTopics.setText(R.string.btn_back);
        btnBackToTopics.setOnClickListener(v -> showLearnedWordsDashboard());
        learnedWordsContainer.addView(btnBackToTopics);
    }

    private void showSelectionDashboard() {
        topicView.setVisibility(View.GONE);
        selectionView.setVisibility(View.VISIBLE);
        selectionContainer.removeAllViews();

        for (String topicName : phraseData.keySet()) {
            Button btnTopic = new Button(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, 16);
            btnTopic.setLayoutParams(lp);
            btnTopic.setText(topicName);
            btnTopic.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.glass_bg)));
            btnTopic.setTextColor(getResources().getColor(R.color.text_main));
            btnTopic.setAllCaps(false);
            btnTopic.setOnClickListener(v -> showWordsForSelectionTopic(topicName));
            selectionContainer.addView(btnTopic);
        }
    }

    private void showWordsForSelectionTopic(String topicName) {
        selectionContainer.removeAllViews();

        TextView header = new TextView(this);
        header.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        header.setText("--- " + topicName + " ---");
        header.setTextColor(getResources().getColor(R.color.white));
        header.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18);
        header.setPadding(0, 16, 0, 16);
        header.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        selectionContainer.addView(header);

        List<PhraseItem> items = phraseData.get(topicName);
        if (items != null) {
            for (PhraseItem item : items) {
                CheckBox cb = new CheckBox(this);
                cb.setText(item.getWordDe() + " - " + item.getWordUk());
                cb.setTextColor(getResources().getColor(R.color.text_main));
                cb.setButtonTintList(android.content.res.ColorStateList.valueOf(getResources().getColor(R.color.primary)));
                cb.setChecked(selectedWords.contains(item.getWordDe()));
                cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) selectedWords.add(item.getWordDe());
                    else selectedWords.remove(item.getWordDe());
                    prefs.edit().putStringSet("selected_words", selectedWords).apply();
                });
                selectionContainer.addView(cb);
            }
        }

        Button btnBackToTopics = new Button(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        lp.setMargins(0, 32, 0, 0);
        btnBackToTopics.setLayoutParams(lp);
        btnBackToTopics.setText(R.string.btn_back);
        btnBackToTopics.setOnClickListener(v -> showSelectionDashboard());
        selectionContainer.addView(btnBackToTopics);
    }

    private void resetAllSelections() {
        selectedWords.clear();
        prefs.edit().putStringSet("selected_words", selectedWords).apply();
        Toast.makeText(this, "Виділення скинуто", Toast.LENGTH_SHORT).show();
        showSelectionDashboard();
    }

    private void startSelectedPlayback() {
        List<PhraseItem> selection = new ArrayList<>();
        for (List<PhraseItem> list : phraseData.values()) {
            for (PhraseItem item : list) {
                if (selectedWords.contains(item.getWordDe())) {
                    selection.add(item);
                }
            }
        }

        if (selection.isEmpty()) {
            Toast.makeText(this, R.string.msg_no_selected, Toast.LENGTH_SHORT).show();
            return;
        }

        currentTopic = "Вибрані слова";
        currentSet = new ArrayList<>(selection);
        Collections.shuffle(currentSet);

        setIndex = 0;
        step = 0;
        isPaused = false;
        isInQuiz = false;

        selectionView.setVisibility(View.GONE);
        learningView.setVisibility(View.VISIBLE);
        quizSection.setVisibility(View.GONE);
        btnLearned.setVisibility(View.VISIBLE);

        nextStep();
    }

    private void startRepeatingLearned() {
        List<PhraseItem> learnedItems = new ArrayList<>();
        for (List<PhraseItem> list : phraseData.values()) {
            for (PhraseItem item : list) {
                if (learnedWords.contains(item.getWordDe())) {
                    learnedItems.add(item);
                }
            }
        }

        if (learnedItems.isEmpty()) return;

        currentTopic = "Повторення вивченого";
        currentSet = new ArrayList<>(learnedItems);
        Collections.shuffle(currentSet);
        currentSet = currentSet.subList(0, Math.min(10, currentSet.size()));

        setIndex = 0;
        step = 0;
        isPaused = false;
        isInQuiz = false;

        topicView.setVisibility(View.GONE);
        learnedDashboardView.setVisibility(View.GONE);
        learningView.setVisibility(View.VISIBLE);
        quizSection.setVisibility(View.GONE);
        btnLearned.setVisibility(View.GONE);

        nextStep();
    }

    private void renderTopics() {
        for (String topic : phraseData.keySet()) {
            Button topicBtn = new Button(this);
            topicBtn.setText(topic);
            topicBtn.setOnClickListener(v -> startLearning(topic));
            topicsList.addView(topicBtn);
        }
    }

    private void initTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(germanLocale);
            }
        });
    }

    private String currentTopic;

    private void startLearning(String topic) {
        currentTopic = topic;
        
        if (!topic.equals(poolTopic) || wordPool.isEmpty()) {
            poolTopic = topic;
            List<PhraseItem> all = phraseData.get(topic);
            wordPool = new ArrayList<>();
            for (PhraseItem item : all) {
                if (!learnedWords.contains(item.getWordDe())) {
                    wordPool.add(item);
                }
            }
            Collections.shuffle(wordPool);
        }

        if (wordPool.isEmpty()) {
            Toast.makeText(this, R.string.msg_all_learned, Toast.LENGTH_LONG).show();
            return;
        }

        int batchSize = Math.min(3, wordPool.size());
        currentSet = new ArrayList<>(wordPool.subList(0, batchSize));
        for (int i = 0; i < batchSize; i++) {
            wordPool.remove(0);
        }

        setIndex = 0;
        step = 0;
        isPaused = false;
        isInQuiz = false;

        topicView.setVisibility(View.GONE);
        learningView.setVisibility(View.VISIBLE);
        quizSection.setVisibility(View.GONE);
        btnLearned.setVisibility(View.VISIBLE);

        nextStep();
    }

    private void nextStep() {
        if (isPaused || isInQuiz)
            return;

        PhraseItem item = currentSet.get(setIndex);
        
        if (step > 0 && !item.getPhrasesDe().isEmpty()) {
            currentPhraseIndex = random.nextInt(item.getPhrasesDe().size());
        } else {
            currentPhraseIndex = -1;
        }

        updateUIForStep(item);
        speakStep(item);
    }

    private void updateUIForStep(PhraseItem item) {
        if (step == 0) {
            modeLabel.setText(R.string.label_learn_word);
            textDe.setText(item.getWordDe());
            textUk.setText(item.getWordUk());
            subPhrase.setVisibility(View.GONE);
        } else {
            modeLabel.setText(R.string.label_repeat_phrase);
            if (currentPhraseIndex != -1) {
                String randomPhrase = item.getPhrasesDe().get(currentPhraseIndex);
                subPhrase.setText(randomPhrase);
                subPhrase.setVisibility(View.VISIBLE);
            } else {
                subPhrase.setVisibility(View.GONE);
            }
        }
    }

    private void speakStep(PhraseItem item) {
        if (step == 0) {
            tts.setLanguage(germanLocale);
            tts.speak(item.getWordDe(), TextToSpeech.QUEUE_FLUSH, null, "w1");
            handler.postDelayed(() -> {
                tts.setLanguage(ukrainianLocale);
                tts.speak(item.getWordUk(), TextToSpeech.QUEUE_ADD, null, "w2");
                observeTTS();
            }, 1000);
        } else if (currentPhraseIndex != -1) {
            String phraseDe = item.getPhrasesDe().get(currentPhraseIndex);
            String phraseUk = item.getPhrasesUk().get(currentPhraseIndex);

            tts.setLanguage(germanLocale);
            tts.speak(phraseDe, TextToSpeech.QUEUE_FLUSH, null, "p1");
            handler.postDelayed(() -> {
                tts.setLanguage(ukrainianLocale);
                tts.speak(phraseUk, TextToSpeech.QUEUE_ADD, null, "p2");
                observeTTS();
            }, 2000);
        } else {
            observeTTS();
        }
    }

    private void observeTTS() {
        handler.removeCallbacksAndMessages(null); 
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!tts.isSpeaking()) {
                    advanceStep();
                } else {
                    handler.postDelayed(this, 500);
                }
            }
        }, 1000);
    }

    private void advanceStep() {
        step++;
        if (step > 1) { 
            step = 0;
            setIndex++;
        }

        if (setIndex >= currentSet.size()) {
            if (cbShowQuiz.isChecked()) {
                startQuiz();
            } else {
                finishSession();
            }
        } else {
            nextStep();
        }
    }

    private void startQuiz() {
        isInQuiz = true;
        setIndex = 0;
        quizSection.setVisibility(View.GONE);
        btnLearned.setVisibility(View.GONE); 
        modeLabel.setText(R.string.label_oral_quiz);
        nextQuizItem();
    }

    private void nextQuizItem() {
        if (isPaused) return;
        
        if (setIndex >= currentSet.size()) {
            finishSession();
            return;
        }
        
        PhraseItem item = currentSet.get(setIndex);
        textUk.setText(getString(R.string.quiz_question, item.getWordUk()));
        textDe.setText("???");
        
        tts.setLanguage(ukrainianLocale);
        tts.speak(textUk.getText().toString(), TextToSpeech.QUEUE_FLUSH, null, "q1");
        
        handler.postDelayed(() -> {
            if (isPaused) return;
            textDe.setText(item.getWordDe());
            tts.setLanguage(germanLocale);
            tts.speak(item.getWordDe(), TextToSpeech.QUEUE_FLUSH, null, "q2");
            
            handler.postDelayed(() -> {
                if (!isPaused) {
                    setIndex++;
                    nextQuizItem();
                }
            }, 3000);
        }, 4000);
    }

    private void finishSession() {
        if ("Вибрані слова".equals(currentTopic)) {
            startSelectedPlayback();
            return;
        }

        if (cbShowQuiz.isChecked()) {
            textUk.setText(R.string.msg_well_done);
            textDe.setText("");
            handler.postDelayed(() -> startLearning(currentTopic), 3000);
        } else {
            startLearning(currentTopic);
        }
    }

    private void togglePause() {
        isPaused = !isPaused;
        btnAction.setText(isPaused ? R.string.btn_resume : R.string.btn_pause);
        if (!isPaused) {
            if (isInQuiz) nextQuizItem();
            else nextStep();
        }
    }

    private void markCurrentAsLearned() {
        if (currentSet == null || setIndex >= currentSet.size()) return;
        
        PhraseItem item = currentSet.get(setIndex);
        learnedWords.add(item.getWordDe());
        prefs.edit().putStringSet("learned_words", learnedWords).apply();
        
        Toast.makeText(this, R.string.msg_learned_success, Toast.LENGTH_SHORT).show();
        updateLearnedCount();
        
        tts.stop();
        handler.removeCallbacksAndMessages(null);
        advanceStep();
    }

    private void stopLearning() {
        if (tts != null)
            tts.stop();
        handler.removeCallbacksAndMessages(null);
        topicView.setVisibility(View.VISIBLE);
        learningView.setVisibility(View.GONE);
        learnedDashboardView.setVisibility(View.GONE);
        selectionView.setVisibility(View.GONE);
        isPaused = false;
        updateLearnedCount();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
