package com.vocabulary.reference.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class StudyQuestion {
    private final int wordId;
    private final String lemma;
    private final String correctMeaning;
    private final List<String> options;

    public StudyQuestion(int wordId, String lemma, String correctMeaning, List<String> options) {
        this.wordId = wordId;
        this.lemma = lemma;
        this.correctMeaning = correctMeaning;
        this.options = Collections.unmodifiableList(new ArrayList<>(options));
        if (this.options.size() != 4) throw new IllegalArgumentException("A quiz must have four options");
    }

    public int getWordId() { return wordId; }
    public String getLemma() { return lemma; }
    public String getCorrectMeaning() { return correctMeaning; }
    public List<String> getOptions() { return options; }

    public boolean isCorrect(String answer) { return correctMeaning.equals(answer); }
}
