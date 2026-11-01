package com.vocabulary.reference.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Serializable session snapshot. It deliberately stores question options. */
public final class SessionSnapshot {
    public String id;
    public String sourceSessionId;
    public String status;
    public int currentIndex;
    public List<Integer> wordIds = new ArrayList<>();
    public List<StudyQuestionSnapshot> questions = new ArrayList<>();
    public Map<Integer, String> answers = new LinkedHashMap<>();
    public Map<Integer, MistakeStat> mistakes = new LinkedHashMap<>();
    public List<Integer> wrongIds = new ArrayList<>();

    public SessionSnapshot() { }

    public static final class StudyQuestionSnapshot {
        public int wordId;
        public String lemma;
        public String correctMeaning;
        public List<String> options = new ArrayList<>();
        public StudyQuestionSnapshot() { }
        public StudyQuestionSnapshot(StudyQuestion q) {
            wordId = q.getWordId(); lemma = q.getLemma(); correctMeaning = q.getCorrectMeaning();
            options = new ArrayList<>(q.getOptions());
        }
        public StudyQuestion toQuestion() { return new StudyQuestion(wordId, lemma, correctMeaning, options); }
    }
}
