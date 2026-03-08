package com.example.germanspreach;

import android.animation.ObjectAnimator;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ProgressBar;
import android.widget.TextView;
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

    private String topicName;
    private List<PhraseItem> cards;
    private int cardIndex = 0;
    private boolean isFlipped = false;
    private boolean isAnimating = false;

    private TextToSpeech tts;
    private boolean ttsReady = false;
    private SharedPreferences prefs;
    private Set<String> learnedWords;

    // Card views
    private View cardFront, cardBack;
    private View cardContainer;
    private TextView tvWordDe, tvWordDeBack, tvWordUk;
    private TextView tvSoundIcon;

    // UI
    private TextView tvTopicTitle, tvCardCount;
    private ProgressBar pbFlashcard;
    private TextView overlayLeft, overlayRight;

    // Completion
    private View completionView;
    private View flashcardContent;
    private TextView tvCompletionTopic, tvCompletionPct, tvCompletionCount;
    private TextView btnBackTopics, btnRepeatUnknown;
    private TextView btnBack;

    private Handler handler = new Handler();

    // Swipe state
    private float startX;
    private boolean isSwiping = false;
    private static final int SWIPE_THRESHOLD = 100;
    private int totalCardsInTopic = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flashcard);

        topicName = getIntent().getStringExtra(EXTRA_TOPIC);
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

        // Setup camera distance to prevent clipping during 3D flip
        float distance = 8000 * getResources().getDisplayMetrics().density;
        cardFront.setCameraDistance(distance);
        cardBack.setCameraDistance(distance);

        // Hardware layer for smooth animation
        cardFront.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        cardBack.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        // Start with back invisible (not GONE - that breaks FrameLayout sizing)
        cardBack.setVisibility(View.INVISIBLE);

        btnBack.setOnClickListener(v -> finish());
        tvSoundIcon.setOnClickListener(v -> speakCurrentWord());
        btnBackTopics.setOnClickListener(v -> finish());
        btnRepeatUnknown.setOnClickListener(v -> restartWithUnknown());

        setupTouchHandler();
    }

    private void setupTouchHandler() {
        cardContainer.setOnTouchListener((v, event) -> {
            if (isAnimating)
                return true;

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX = event.getRawX();
                    isSwiping = false;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - startX;
                    if (Math.abs(dx) > 8) {
                        isSwiping = true;
                        cardContainer.setTranslationX(dx * 0.75f);
                        cardContainer.setRotation(dx * 0.04f);
                        if (dx > 0) {
                            overlayRight.setAlpha(Math.min(1f, dx / 250f));
                            overlayLeft.setAlpha(0f);
                        } else {
                            overlayLeft.setAlpha(Math.min(1f, -dx / 250f));
                            overlayRight.setAlpha(0f);
                        }
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    float finalDx = event.getRawX() - startX;
                    if (isSwiping && Math.abs(finalDx) > SWIPE_THRESHOLD) {
                        // Swipe gesture
                        if (finalDx > 0)
                            swipeRight();
                        else
                            swipeLeft();
                    } else if (!isSwiping) {
                        // Tap: flip card
                        snapBack();
                        flipCard();
                    } else {
                        // Small swipe, snap back
                        snapBack();
                    }
                    isSwiping = false;
                    return true;

                case MotionEvent.ACTION_CANCEL:
                    snapBack();
                    isSwiping = false;
                    return true;
            }
            return false;
        });
    }

    private void snapBack() {
        cardContainer.animate()
                .translationX(0).rotation(0).setDuration(200).start();
        overlayLeft.setAlpha(0f);
        overlayRight.setAlpha(0f);
    }

    // ──────────────────────────────────────────────
    // FLIP ANIMATION
    // ──────────────────────────────────────────────
    private void flipCard() {
        if (isAnimating)
            return;
        if (!isFlipped) {
            flipFrontToBack();
        } else {
            flipBackToFront();
        }
        isFlipped = !isFlipped;
    }

    private void flipFrontToBack() {
        isAnimating = true;
        // Phase 1: rotate front OUT (0 → 90)
        ObjectAnimator outAnim = ObjectAnimator.ofFloat(cardFront, "rotationY", 0f, 90f);
        outAnim.setDuration(200);
        outAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        outAnim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator anim) {
                cardFront.setVisibility(View.INVISIBLE);
                cardFront.setRotationY(0f);
                cardBack.setVisibility(View.VISIBLE);
                cardBack.setRotationY(-90f);
                // Phase 2: rotate back IN (-90 → 0)
                ObjectAnimator inAnim = ObjectAnimator.ofFloat(cardBack, "rotationY", -90f, 0f);
                inAnim.setDuration(200);
                inAnim.setInterpolator(new AccelerateDecelerateInterpolator());
                inAnim.addListener(new android.animation.AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(android.animation.Animator a) {
                        isAnimating = false;
                    }
                });
                inAnim.start();
            }
        });
        outAnim.start();
    }

    private void flipBackToFront() {
        isAnimating = true;
        // Phase 1: rotate back OUT (0 → 90)
        ObjectAnimator outAnim = ObjectAnimator.ofFloat(cardBack, "rotationY", 0f, 90f);
        outAnim.setDuration(200);
        outAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        outAnim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator anim) {
                cardBack.setVisibility(View.INVISIBLE);
                cardBack.setRotationY(0f);
                cardFront.setVisibility(View.VISIBLE);
                cardFront.setRotationY(-90f);
                // Phase 2: rotate front IN (-90 → 0)
                ObjectAnimator inAnim = ObjectAnimator.ofFloat(cardFront, "rotationY", -90f, 0f);
                inAnim.setDuration(200);
                inAnim.setInterpolator(new AccelerateDecelerateInterpolator());
                inAnim.addListener(new android.animation.AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(android.animation.Animator a) {
                        isAnimating = false;
                    }
                });
                inAnim.start();
            }
        });
        outAnim.start();
    }

    // ──────────────────────────────────────────────
    // SWIPE
    // ──────────────────────────────────────────────
    private void swipeRight() {
        PhraseItem item = cards.get(cardIndex);
        learnedWords.add(item.getWordDe());
        prefs.edit().putStringSet("learned_words", learnedWords).apply();
        animateSwipeOut(true);
    }

    private void swipeLeft() {
        PhraseItem item = cards.get(cardIndex);
        String studyKey = "study_" + topicName;
        Set<String> studyQueue = new HashSet<>(prefs.getStringSet(studyKey, new HashSet<>()));
        studyQueue.add(item.getWordDe());
        prefs.edit().putStringSet(studyKey, studyQueue).apply();
        animateSwipeOut(false);
    }

    private void animateSwipeOut(boolean right) {
        isAnimating = true;
        float targetX = right ? 1600f : -1600f;
        cardContainer.animate()
                .translationX(targetX)
                .rotation(right ? 25f : -25f)
                .alpha(0f)
                .setDuration(280)
                .withEndAction(() -> {
                    cardContainer.setAlpha(1f);
                    overlayLeft.setAlpha(0f);
                    overlayRight.setAlpha(0f);
                    isAnimating = false;
                    cardIndex++;
                    if (cardIndex < cards.size())
                        showCard(cardIndex);
                    else
                        showCompletionScreen();
                })
                .start();
    }

    // ──────────────────────────────────────────────
    // CARD DATA
    // ──────────────────────────────────────────────
    private void loadCards() {
        Map<String, List<PhraseItem>> allData = PhraseProvider.getPhrasesByTopic(this);
        List<PhraseItem> all = allData.get(topicName);
        totalCardsInTopic = all != null ? all.size() : 0;
        cards = new ArrayList<>();
        if (all != null) {
            for (PhraseItem item : all) {
                if (!learnedWords.contains(item.getWordDe()))
                    cards.add(item);
            }
        }
        if (cards.isEmpty()) {
            showCompletionScreen();
            return;
        }
        cardIndex = 0;
        showCard(0);
    }

    private void showCard(int index) {
        PhraseItem item = cards.get(index);
        isFlipped = false;
        isAnimating = false;

        // Reset card state
        cardFront.setVisibility(View.VISIBLE);
        cardFront.setRotationY(0f);
        cardBack.setVisibility(View.INVISIBLE);
        cardBack.setRotationY(0f);
        cardContainer.setTranslationX(0);
        cardContainer.setRotation(0);
        cardContainer.setAlpha(1f);
        overlayLeft.setAlpha(0f);
        overlayRight.setAlpha(0f);

        tvWordDe.setText(item.getWordDe());
        tvWordDeBack.setText(item.getWordDe());
        tvWordUk.setText(item.getWordUk());

        int shown = index + 1;
        int total = cards.size();
        tvCardCount.setText(shown + " / " + total);
        pbFlashcard.setProgress((shown * 100) / Math.max(total, 1));

        handler.postDelayed(this::speakCurrentWord, 500);
    }

    private void restartWithUnknown() {
        learnedWords = new HashSet<>(prefs.getStringSet("learned_words", new HashSet<>()));
        completionView.setVisibility(View.GONE);
        flashcardContent.setVisibility(View.VISIBLE);
        loadCards();
    }

    // ──────────────────────────────────────────────
    // TTS
    // ──────────────────────────────────────────────
    private void speakCurrentWord() {
        if (tts != null && ttsReady && cardIndex < cards.size()) {
            tts.setLanguage(Locale.GERMANY);
            tts.speak(cards.get(cardIndex).getWordDe(), TextToSpeech.QUEUE_FLUSH, null, "w");
        }
    }

    private void initTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.GERMANY);
                ttsReady = true;
            }
        });
    }

    // ──────────────────────────────────────────────
    // COMPLETION
    // ──────────────────────────────────────────────
    private void showCompletionScreen() {
        flashcardContent.setVisibility(View.GONE);
        completionView.setVisibility(View.VISIBLE);

        int learnedInTopic = 0;
        Map<String, List<PhraseItem>> allData = PhraseProvider.getPhrasesByTopic(this);
        List<PhraseItem> allTopicCards = allData.get(topicName);
        if (allTopicCards != null) {
            totalCardsInTopic = allTopicCards.size();
            for (PhraseItem item : allTopicCards) {
                if (learnedWords.contains(item.getWordDe()))
                    learnedInTopic++;
            }
        }
        int pct = totalCardsInTopic > 0 ? (learnedInTopic * 100) / totalCardsInTopic : 100;

        tvCompletionTopic.setText(topicName);
        tvCompletionPct.setText(pct + "%");
        tvCompletionCount.setText(learnedInTopic + " з " + totalCardsInTopic + " слів вивчено");

        Set<String> studyQueue = prefs.getStringSet("study_" + topicName, new HashSet<>());
        btnRepeatUnknown.setVisibility(studyQueue.isEmpty() ? View.GONE : View.VISIBLE);
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
