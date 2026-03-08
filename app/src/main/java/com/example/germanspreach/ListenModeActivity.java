package com.example.germanspreach;

import android.content.Context;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.LinearLayout;
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
import java.util.Random;
import java.util.Set;

/**
 * Plays the study queue words (swiped-left "don't know") with their phrases
 * aloud.
 * Flow per word: German word → pause → German phrase → pause → Ukrainian
 * translation → next word.
 */
public class ListenModeActivity extends AppCompatActivity {

    public static final String EXTRA_TOPIC = "topic";

    private List<PhraseItem> items = new ArrayList<>();
    private int currentIndex = 0;

    private TextToSpeech tts;
    private boolean ttsReady = false;
    private Handler handler = new Handler();
    private boolean isPlaying = false;

    private AudioManager audioManager;
    private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener;

    private TextView tvWordDe, tvPhraseDe, tvTranslation, tvPhraseUk;
    private TextView tvCounter, tvStatus, tvTitle;
    private TextView btnPlayPause, btnBack, btnMarkLearned;
    private android.content.SharedPreferences prefs;

    private int prefReps = 1;
    private float prefSpeed = 1.0f;
    private boolean prefAskQuestions = false;
    private int currentRep = 1;

    private final Locale DE = Locale.GERMANY;
    private final Locale UK = new Locale("uk", "UA");
    private final Random rand = new Random();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_listen_mode);

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioFocusChangeListener = focusChange -> {
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                    focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                    focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                if (isPlaying)
                    stopPlayback();
            }
        };

        initViews();
        loadStudyQueueItems();
        initTTS();
    }

    @Override
    protected void onResume() {
        super.onResume();

        prefs = getSharedPreferences("GermanLearen", MODE_PRIVATE);
        prefReps = prefs.getInt("pref_repetitions", 1);
        prefAskQuestions = prefs.getBoolean("pref_ask_questions", false);
        prefSpeed = prefs.getFloat("pref_speech_speed", 1.0f);
        if (tts != null)
            tts.setSpeechRate(prefSpeed);

        // Refresh list when returning from StudyActivity
        stopPlayback();
        loadStudyQueueItems();

        if (ttsReady && !items.isEmpty() && !isPlaying) {
            togglePlay();
        }
    }

    private void loadStudyQueueItems() {
        prefs = getSharedPreferences("GermanLearen", MODE_PRIVATE);
        Set<String> learnedWords = new HashSet<>(prefs.getStringSet("learned_words", new HashSet<>()));
        Map<String, List<PhraseItem>> allData = PhraseProvider.getPhrasesByTopic(this);

        items.clear();
        String specificTopic = getIntent().getStringExtra("topic_to_listen");

        if (specificTopic != null) {
            // Load ALL words for this specific topic (except those already learned)
            List<PhraseItem> topicItems = allData.get(specificTopic);
            if (topicItems != null) {
                for (PhraseItem item : topicItems) {
                    if (!learnedWords.contains(item.getWordDe())) {
                        items.add(item);
                    }
                }
            }
        } else {
            // Load study queue across all topics
            for (Map.Entry<String, List<PhraseItem>> entry : allData.entrySet()) {
                String topic = entry.getKey();
                String studyKey = "study_" + topic;
                Set<String> studyQueue = prefs.getStringSet(studyKey, new HashSet<>());
                if (studyQueue.isEmpty())
                    continue;

                for (PhraseItem item : entry.getValue()) {
                    if (studyQueue.contains(item.getWordDe()) && !learnedWords.contains(item.getWordDe())) {
                        items.add(item);
                    }
                }
            }
        }

        // Shuffle the list to randomize playback as requested
        if (!items.isEmpty()) {
            Collections.shuffle(items, rand);
        }

        if (items.isEmpty()) {
            tvStatus.setText("Немає слів для вивчення.\nСвайпніть ← на картці, щоб додати слова.");
            btnPlayPause.setVisibility(View.GONE);
            tvCounter.setVisibility(View.GONE);
        } else {
            tvTitle.setText("ВИВЧЕННЯ СЛОВА");
            tvCounter.setVisibility(View.VISIBLE);
            tvCounter.setText("0 / " + items.size());
            tvStatus.setText("Завантаження...");
        }
    }

    private void initViews() {
        tvWordDe = findViewById(R.id.tv_listen_word_de);
        tvPhraseDe = findViewById(R.id.tv_listen_phrase_de);
        tvTranslation = findViewById(R.id.tv_listen_translation);
        tvPhraseUk = findViewById(R.id.tv_listen_phrase_uk);
        tvCounter = findViewById(R.id.tv_listen_counter);
        tvStatus = findViewById(R.id.tv_listen_status);
        tvTitle = findViewById(R.id.tv_listen_title);
        btnPlayPause = findViewById(R.id.btn_listen_play_pause);
        btnMarkLearned = findViewById(R.id.btn_mark_learned);
        btnBack = findViewById(R.id.btn_back);

        tvTitle.setText("ВИВЧЕННЯ СЛОВА");
        btnMarkLearned.setVisibility(android.view.View.VISIBLE);

        btnBack.setOnClickListener(v -> finish());
        btnPlayPause.setOnClickListener(v -> togglePlay());

        // Opens the checklist where user ticks which words they've learned
        btnMarkLearned.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(this, StudyActivity.class);
            String currentTopic = getIntent().getStringExtra("topic_to_listen");
            if (currentTopic != null) {
                intent.putExtra("topic_to_study", currentTopic);
            }
            startActivity(intent);
        });
    }

    private void togglePlay() {
        if (isPlaying) {
            stopPlayback();
        } else {
            if (items.isEmpty())
                return;

            int result = audioManager.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN);
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                isPlaying = true;
                btnPlayPause.setText("ПАУЗА");
                btnMarkLearned.setVisibility(View.VISIBLE);
                playCurrentItem();
            }
        }
    }

    private void playCurrentItem() {
        if (!isPlaying)
            return;

        if (currentIndex >= items.size()) {
            // Re-shuffle when the list restarts
            Collections.shuffle(items, rand);
            currentIndex = 0;
            currentRep = 1;
            tvStatus.setText("✓ Завершено! Починаємо знову...");
            handler.postDelayed(() -> {
                if (isPlaying)
                    playCurrentItem();
            }, 2000);
            return;
        }

        PhraseItem item = items.get(currentIndex);
        tvCounter.setText((currentIndex + 1) + " / " + items.size());

        // Pre-determine phrases to use for this word to allocate layout bounds
        String phraseDeText = "";
        String phraseUkText = "";
        if (!item.getPhrasesDe().isEmpty()) {
            int idx = rand.nextInt(item.getPhrasesDe().size());
            phraseDeText = item.getPhrasesDe().get(idx);
            phraseUkText = idx < item.getPhrasesUk().size() ? item.getPhrasesUk().get(idx) : item.getWordUk();
        }

        // Fill texts and set layout bounds, but hide translations and phrases
        tvWordDe.setText(item.getWordDe());
        tvTranslation.setText(item.getWordUk());
        tvPhraseDe.setText(phraseDeText);
        tvPhraseUk.setText(phraseUkText);

        tvTranslation.setVisibility(android.view.View.INVISIBLE);
        tvPhraseDe.setVisibility(android.view.View.INVISIBLE);
        tvPhraseUk.setVisibility(android.view.View.INVISIBLE);

        // Ask question only on the first repetition, if enabled
        boolean shouldAsk = (prefAskQuestions && currentRep == 1);

        if (shouldAsk) {
            tvWordDe.setText("???");
            tvTranslation.setVisibility(android.view.View.VISIBLE);
            tvStatus.setText("Запитання...");

            final String finalPhraseDeText = phraseDeText;
            final String finalPhraseUkText = phraseUkText;
            speakAndThen(UK, "Як буде: " + item.getWordUk() + "?", 1500, () -> {
                if (!isPlaying)
                    return;
                tvWordDe.setText(item.getWordDe());
                playSteps(item, finalPhraseDeText, finalPhraseUkText);
            });
        } else {
            playSteps(item, phraseDeText, phraseUkText);
        }
    }

    private void playSteps(PhraseItem item, String phraseDe, String phraseUk) {
        if (!isPlaying)
            return;

        tvStatus.setText("Слово...");

        // Step 1: speak German word
        speakAndThen(DE, item.getWordDe(), 600, () -> {

            // Step 2: reveal Ukrainian word translation & speak
            handler.postDelayed(() -> {
                if (!isPlaying)
                    return;
                tvTranslation.setVisibility(android.view.View.VISIBLE);
                tvStatus.setText("Переклад слова...");
                speakAndThen(UK, item.getWordUk(), 600, () -> {

                    if (!phraseDe.isEmpty()) {

                        // Step 3: reveal German phrase & speak
                        handler.postDelayed(() -> {
                            if (!isPlaying)
                                return;
                            tvPhraseDe.setVisibility(android.view.View.VISIBLE);
                            tvStatus.setText("Речення...");
                            speakAndThen(DE, phraseDe, 500, () -> {

                                // Step 4: reveal Ukrainian phrase translation & speak
                                handler.postDelayed(() -> {
                                    if (!isPlaying)
                                        return;
                                    tvPhraseUk.setVisibility(android.view.View.VISIBLE);
                                    tvStatus.setText("Переклад речення...");
                                    speakAndThen(UK, phraseUk, 800, this::onSequenceEnd);
                                }, 400);
                            });
                        }, 400);

                    } else {
                        // No phrase
                        handler.postDelayed(this::onSequenceEnd, 400);
                    }
                });
            }, 300);
        });
    }

    private void onSequenceEnd() {
        if (!isPlaying)
            return;
        if (currentRep < prefReps) {
            currentRep++;
            playCurrentItem();
        } else {
            currentRep = 1;
            currentIndex++;
            playCurrentItem();
        }
    }

    /** Speak text, then after TTS finishes + extraDelay ms, call onDone. */
    private void speakAndThen(Locale locale, String text, int extraDelayMs, Runnable onDone) {
        if (tts == null || !ttsReady) {
            handler.postDelayed(() -> speakAndThen(locale, text, extraDelayMs, onDone), 400);
            return;
        }
        tts.setLanguage(locale);
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "u");
        pollUntilDone(extraDelayMs, onDone);
    }

    private void pollUntilDone(int extraDelayMs, Runnable onDone) {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isPlaying)
                    return;
                if (tts != null && tts.isSpeaking()) {
                    handler.postDelayed(this, 200);
                } else {
                    handler.postDelayed(onDone, extraDelayMs);
                }
            }
        }, 300);
    }

    private void stopPlayback() {
        isPlaying = false;
        if (tts != null)
            tts.stop();
        handler.removeCallbacksAndMessages(null);
        if (audioManager != null && audioFocusChangeListener != null) {
            audioManager.abandonAudioFocus(audioFocusChangeListener);
        }
        btnPlayPause.setText("ПРОДОВЖИТИ");
        btnMarkLearned.setVisibility(android.view.View.VISIBLE);
        tvStatus.setText("На паузі");
    }

    private void markCurrentWordAsLearned() {
        if (currentIndex >= items.size())
            return;
        PhraseItem item = items.get(currentIndex);
        String word = item.getWordDe();

        // Add to learned_words
        Set<String> learnedWords = new HashSet<>(prefs.getStringSet("learned_words", new HashSet<>()));
        learnedWords.add(word);
        prefs.edit().putStringSet("learned_words", learnedWords).apply();

        // Remove from all study queues
        Map<String, List<PhraseItem>> allData = PhraseProvider.getPhrasesByTopic(this);
        for (String topic : allData.keySet()) {
            String key = "study_" + topic;
            Set<String> queue = new HashSet<>(prefs.getStringSet(key, new HashSet<>()));
            if (queue.remove(word)) {
                prefs.edit().putStringSet(key, queue).apply();
            }
        }

        // Remove from current in-memory list
        items.remove(currentIndex);
        tvStatus.setText("✅ Слово видалено зі списку!");
        tvTitle.setText("🎧 Слухати (" + items.size() + " слів)");

        // Stop current TTS and jump to next word
        if (tts != null)
            tts.stop();
        handler.removeCallbacksAndMessages(null);

        if (items.isEmpty()) {
            stopPlayback();
            tvStatus.setText("🎉 Список порожній!");
            return;
        }
        // currentIndex stays the same (next word shifted into its place)
        if (currentIndex >= items.size())
            currentIndex = 0;
        handler.postDelayed(this::playCurrentItem, 600);
    }

    private void initTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(DE);
                ttsReady = true;
                if (tts != null)
                    tts.setSpeechRate(prefSpeed);
                if (!items.isEmpty() && !isPlaying) {
                    togglePlay();
                }
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
