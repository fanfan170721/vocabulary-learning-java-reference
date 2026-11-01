package com.vocabulary.reference.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StudySession {
    private final SessionSnapshot snapshot;

    public StudySession(SessionSnapshot snapshot) { this.snapshot = snapshot; }
    public SessionSnapshot snapshot() { return snapshot; }
    public String getId() { return snapshot.id; }
    public String getStatus() { return snapshot.status; }
    public int getCurrentIndex() { return snapshot.currentIndex; }
    public int size() { return snapshot.questions.size(); }
    public boolean isFinished() { return snapshot.currentIndex >= size(); }
    public StudyQuestion currentQuestion() {
        if (isFinished()) return null;
        return snapshot.questions.get(snapshot.currentIndex).toQuestion();
    }
    public boolean answer(String value) {
        StudyQuestion q = currentQuestion();
        if (q == null) throw new IllegalStateException("Session is already complete");
        boolean correct = q.isCorrect(value);
        snapshot.answers.put(q.getWordId(), value);
        MistakeStat stat = snapshot.mistakes.computeIfAbsent(q.getWordId(), k -> new MistakeStat());
        if (!correct) stat.error("I don't know".equals(value));
        if (!correct && !snapshot.wrongIds.contains(q.getWordId())) snapshot.wrongIds.add(q.getWordId());
        snapshot.currentIndex++;
        return correct;
    }
    public List<Integer> getWrongIds() { return new ArrayList<>(snapshot.wrongIds); }
    public Map<Integer, MistakeStat> getMistakes() { return new LinkedHashMap<>(snapshot.mistakes); }
    public List<Integer> getWordIds() { return new ArrayList<>(snapshot.wordIds); }
}
