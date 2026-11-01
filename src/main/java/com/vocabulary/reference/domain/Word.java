package com.vocabulary.reference.domain;

import java.util.Objects;

public final class Word {
    private final int id;
    private final String lemma;
    private final String meaning;
    private final String level;

    public Word(int id, String lemma, String meaning, String level) {
        this.id = id;
        this.lemma = Objects.requireNonNull(lemma);
        this.meaning = Objects.requireNonNull(meaning);
        this.level = Objects.requireNonNull(level);
    }

    public int getId() { return id; }
    public String getLemma() { return lemma; }
    public String getMeaning() { return meaning; }
    public String getLevel() { return level; }

    @Override public String toString() { return lemma + " — " + meaning; }
}
