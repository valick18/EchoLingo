package com.example.germanspreach;

import android.animation.AnimatorInflater;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
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

public class FlashcardActivity extends AppCompatActivity {

    public static final String EXTRA_TOPIC = "topic";
    public static final String EXTRA_STUDY_MODE = "study_mode";

    private String topicName;
    private boolean studyMode;
    private List<PhraseItem> cards;
    private int cardIndex = 0;
    private boolean isFlipped = false;
    private boolean isSwiping = false;

    private TextToSpeech tts;
    private SharedPreferences prefs;
    private Set<String> learnedWords;

    // Views - Card
    private View cardFront, cardBack;
    private View cardContainer;
    private TextView tvWordDe, tvWordDeBack, tvWordUk;
    private TextView tvSoundIcon;

    // Views - UI
    private TextView tvTopicTitle, tvCardCount;
    private ProgressBar pbFlashcard;
    private TextView overlayLeft, overlayRight;

    // Completion
    private View completionView;
    private View flashcardContent;
    private TextView tvCompletionTopic, tvCompletionPct, tvCompletionCount;
    private TextView btnBackTopics, btnRepeatUnknown;
    private TextView btnBack;

    private GestureDetector gestureDetector;
    private Handler handler = new Handler();

    // Swipe tracking
    private float startX, startY;
    private static final int SWIPE_THRESHOLD = 120;
    private static final int SWIPE_VELOCITY_THRESHOLD = 100;
    private int totalCardsInTopic = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flashcard);

        topicName = getIntent().getStringExtra(EXTRA_TOPIC);
        studyMode = getIntent().getBooleanExtra(EXTRA_STUDY_MODE, false);

        prefs = getSharedPreferences("GermanLearen", MODE_PRIVATE);
        learnedWords = new HashSet<>(prefs.getStringSet("learned_words", new HashSet<>()));

        initViews();
        initTTS();
        loadCards();
    }

    private void initViews() {
        flashcardContent = findViewById(R.id.flashcard_content);
        completionView = findViewById(R.id.completion_view);

        cardContainer = findViewById(R.id.card_container);
        cardFront = findViewById(R.id.card_front);
        cardBack = findViewById(R.id.card_back);
        tvWordDe = findViewById(R.id.tv_word_de);
        tvWordDeBack = findViewById(R.id.tv_word_de_back);
        tvWordUk = findViewById(R.id.tv_word_uk);
        tvSoundIcon = findViewById(R.id.tv_sound_icon);

        tvTopicTitle = findViewById(R.id.tv_topic_title);
        tvCardCount = findViewById(R.id.tv_card_count);
        pbFlashcard = findViewById(R.id.pb_flashcard);
        overlayLeft = findViewById(R.id.overlay_left);
        overlayRight = findViewById(R.id.overlay_right);

        tvCompletionTopic = findViewById(R.id.tv_completion_topic);
        tvCompletionPct = findViewById(R.id.tv_completion_pct);
        tvCompletionCount = findViewById(R.id.tv_completion_count);
        btnBackTopics = findViewById(R.id.btn_back_topics);
        btnRepeatUnknown = findViewById(R.id.btn_repeat_unknown);
        btnBack = findViewById(R.id.btn_back);

        tvTopicTitle.setText(topicName);

        btnBack.setOnClickListener(v -> finish());
        tvSoundIcon.setOnClickListener(v -> speakCurrentWord());

        btnBackTopics.setOnClickListener(v -> finish());
        btnRepeatUnknown.setOnClickListener(v -> {
            // Restart with only unknown words
            loadCardsFromStudyQueue();
        });

        setupSwipeGesture();
        setupCardFlip();
    }

    private void setupCardFlip() {
        // Enable hardware layer for smooth animation
        cardFront.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        cardBack.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        cardContainer.setOnClickListener(v -> {
            if (!isSwiping) {
                flipCard();
            }
        });
    }

    private void setupSwipeGesture() {
        cardContainer.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX = event.getRawX();
                    startY = event.getRawY();
                    isSwiping = false;
                    break;

                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - startX;
                    float dy = event.getRawY() - startY;

                    if (Math.abs(dx) > 10) {
                        isSwiping = true;
                        // Move card
                        cardContainer.setTranslationX(dx * 0.7f);
                        cardContainer.setRotation(dx * 0.03f);

                        // Show overlays
                        if (dx > 0) {
                            overlayRight.setAlpha(Math.min(1f, dx / 300f));
                            overlayLeft.setAlpha(0f);
                        } else {
                            overlayLeft.setAlpha(Math.min(1f, -dx / 300f));
                            overlayRight.setAlpha(0f);
                        }
                    }
                    break;

                case MotionEvent.ACTION_UP:
                    float finalDx = event.getRawX() - startX;

                    if (Math.abs(finalDx) > SWIPE_THRESHOLD && isSwiping) {
                        if (finalDx > 0) {
                            swipeRight(); // Know it
                        } else {
                            swipeLeft(); // Don't know
                        }
                    } else {
                        // Snap back
                        cardContainer.animate()
                                .translationX(0)
                                .rotation(0)
                                .setDuration(200)
                                .start();
                        overlayLeft.setAlpha(0f);
                        overlayRight.setAlpha(0f);

                        if (!isSwiping) {
                            // It was a click - flip card
                            flipCard();
                        }
                    }
                    isSwiping = false;
                    break;
            }
            return true;
        });
    }

    private void loadCards() {
        Map<String, List<PhraseItem>> allData = PhraseProvider.getPhrasesByTopic(this);
        List<PhraseItem> allTopicCards = allData.get(topicName);
        totalCardsInTopic = allTopicCards != null ? allTopicCards.size() : 0;

        cards = new ArrayList<>();
        if (allTopicCards != null) {
            for (PhraseItem item : allTopicCards) {
                if (!learnedWords.contains(item.getWordDe())) {
                    cards.add(item);
                }
            }
        }

        if (cards.isEmpty()) {
            showCompletionScreen();
            return;
        }

        cardIndex = 0;
        showCard(cardIndex);
    }

    private void loadCardsFromStudyQueue() {
        // Reload only non-learned words
        loadCards();
        completionView.setVisibility(View.GONE);
        flashcardContent.setVisibility(View.VISIBLE);
    }

    private void showCard(int index) {
        if (index >= cards.size()) {
            showCompletionScreen();
            return;
        }

        PhraseItem item = cards.get(index);
        isFlipped = false;

        // Reset card to front
        cardFront.setVisibility(View.VISIBLE);
        cardBack.setVisibility(View.GONE);
        cardFront.setAlpha(1f);
        cardBack.setAlpha(0f);

        // Reset position
        cardContainer.setTranslationX(0);
        cardContainer.setRotation(0);
        overlayLeft.setAlpha(0f);
        overlayRight.setAlpha(0f);

        // Set content
        tvWordDe.setText(item.getWordDe());
        tvWordDeBack.setText(item.getWordDe());
        tvWordUk.setText(item.getWordUk());

        // Update progress
        int shown = index + 1;
        int total = cards.size();
        tvCardCount.setText(shown + " / " + total);
        pbFlashcard.setProgress((shown * 100) / Math.max(total, 1));

        // Auto-speak when card appears
        handler.postDelayed(this::speakCurrentWord, 400);
    }

    private void speakCurrentWord() {
        if (tts != null && cardIndex < cards.size()) {
            tts.setLanguage(Locale.GERMANY);
            tts.speak(cards.get(cardIndex).getWordDe(), TextToSpeech.QUEUE_FLUSH, null, "word");
        }
    }

    private void flipCard() {
        if (isFlipped) {
            // Flip back to front
            animateFlip(cardBack, cardFront);
            isFlipped = false;
        } else {
            // Flip to back
            animateFlip(cardFront, cardBack);
            isFlipped = true;
        }
    }

    private void animateFlip(View fromView, View toView) {
        final float scaleX = getResources().getDisplayMetrics().widthPixels;

        // Out anim
        ObjectAnimator flipOut = ObjectAnimator.ofFloat(fromView, "rotationY", 0f, 90f);
        flipOut.setDuration(180);
        flipOut.setInterpolator(new AccelerateDecelerateInterpolator());

        flipOut.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                fromView.setVisibility(View.GONE);
                toView.setVisibility(View.VISIBLE);
                toView.setRotationY(-90f);

                // In anim
                ObjectAnimator flipIn = ObjectAnimator.ofFloat(toView, "rotationY", -90f, 0f);
                flipIn.setDuration(180);
                flipIn.setInterpolator(new AccelerateDecelerateInterpolator());
                flipIn.start();
            }
        });
        flipOut.start();
    }

    private void swipeRight() {
        // User knows this word → mark as learned
        PhraseItem item = cards.get(cardIndex);
        learnedWords.add(item.getWordDe());
        prefs.edit().putStringSet("learned_words", learnedWords).apply();

        animateSwipeOut(true);
    }

    private void swipeLeft() {
        // User doesn't know → add to study queue for this topic
        PhraseItem item = cards.get(cardIndex);
        String studyKey = "study_" + topicName;
        Set<String> studyQueue = new HashSet<>(prefs.getStringSet(studyKey, new HashSet<>()));
        studyQueue.add(item.getWordDe());
        prefs.edit().putStringSet(studyKey, studyQueue).apply();

        animateSwipeOut(false);
    }

    private void animateSwipeOut(boolean toRight) {
        float targetX = toRight ? 2000f : -2000f;

        cardContainer.animate()
                .translationX(targetX)
                .rotation(toRight ? 30f : -30f)
                .alpha(0f)
                .setDuration(300)
                .withEndAction(() -> {
                    cardContainer.setAlpha(1f);
                    overlayLeft.setAlpha(0f);
                    overlayRight.setAlpha(0f);
                    cardIndex++;
                    if (cardIndex < cards.size()) {
                        showCard(cardIndex);
                    } else {
                        showCompletionScreen();
                    }
                })
                .start();
    }

    private void showCompletionScreen() {
        flashcardContent.setVisibility(View.GONE);
        completionView.setVisibility(View.VISIBLE);

        // Calculate progress
        int learnedInTopic = 0;
        Map<String, List<PhraseItem>> allData = PhraseProvider.getPhrasesByTopic(this);
        List<PhraseItem> allTopicCards = allData.get(topicName);
        if (allTopicCards != null) {
            for (PhraseItem item : allTopicCards) {
                if (learnedWords.contains(item.getWordDe()))
                    learnedInTopic++;
            }
            totalCardsInTopic = allTopicCards.size();
        }

        int pct = totalCardsInTopic > 0 ? (learnedInTopic * 100) / totalCardsInTopic : 100;

        tvCompletionTopic.setText(topicName);
        tvCompletionPct.setText(pct + "%");
        tvCompletionCount.setText(learnedInTopic + " з " + totalCardsInTopic + " слів вивчено");

        // Check if there are unknown words to repeat
        String studyKey = "study_" + topicName;
        Set<String> studyQueue = prefs.getStringSet(studyKey, new HashSet<>());
        if (studyQueue.isEmpty() || cards.isEmpty()) {
            btnRepeatUnknown.setVisibility(View.GONE);
        } else {
            btnRepeatUnknown.setVisibility(View.VISIBLE);
        }
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
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
