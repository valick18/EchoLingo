package com.example.germanspreach.models;

import java.util.Arrays;
import java.util.List;

public class PhraseItem {
    private final String wordDe;
    private final String wordUk;
    private final List<String> phrasesDe;
    private final List<String> phrasesUk;

    public PhraseItem(String wordDe, String wordUk, String phraseDe, String phraseUk) {
        this(wordDe, wordUk, Arrays.asList(phraseDe), Arrays.asList(phraseUk));
    }

    public PhraseItem(String wordDe, String wordUk, List<String> phrasesDe, List<String> phrasesUk) {
        this.wordDe = wordDe;
        this.wordUk = wordUk;
        this.phrasesDe = phrasesDe;
        this.phrasesUk = phrasesUk;
    }

    public String getWordDe() {
        return wordDe;
    }

    public String getWordUk() {
        return wordUk;
    }

    public List<String> getPhrasesDe() {
        return phrasesDe;
    }

    public List<String> getPhrasesUk() {
        return phrasesUk;
    }
}
