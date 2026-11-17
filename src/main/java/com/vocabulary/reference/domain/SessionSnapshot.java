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
    public int difficulty = 1;
    public String supersededBy;
    public List<Integer> wordIds = new ArrayList<>();
    public List<StudyQuestionSnapshot> questions = new ArrayList<>();
    public Map<Integer, String> answers = new LinkedHashMap<>();
    public Map<Integer, MistakeStat> mistakes = new LinkedHashMap<>();
    public List<Integer> wrongIds = new ArrayList<>();

    public SessionSnapshot() { }

    public SessionSnapshot copy() {
        SessionSnapshot copy = new SessionSnapshot();
        copy.id = id;
        copy.sourceSessionId = sourceSessionId;
        copy.status = status;
        copy.currentIndex = currentIndex;
        copy.difficulty = difficulty;
        copy.supersededBy = supersededBy;
        copy.wordIds = new ArrayList<>(wordIds);
        for (StudyQuestionSnapshot question : questions) {
            copy.questions.add(new StudyQuestionSnapshot(question.toQuestion()));
        }
        copy.answers = new LinkedHashMap<>(answers);
        for (Map.Entry<Integer, MistakeStat> entry : mistakes.entrySet()) {
            MistakeStat stat = new MistakeStat(entry.getValue().getErrors(),
                    entry.getValue().getUnknowns(), entry.getValue().getRetries());
            copy.mistakes.put(entry.getKey(), stat);
        }
        copy.wrongIds = new ArrayList<>(wrongIds);
        return copy;
    }

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
