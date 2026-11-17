package com.vocabulary.reference.infrastructure;

import com.vocabulary.reference.application.StudyRepository;
import com.vocabulary.reference.domain.Goal;
import com.vocabulary.reference.domain.HistoryRecord;
import com.vocabulary.reference.domain.MistakeStat;
import com.vocabulary.reference.domain.SessionSnapshot;
import com.vocabulary.reference.domain.AssessmentSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class InMemoryStudyRepository implements StudyRepository {
    private final Map<String, SessionSnapshot> sessions = new LinkedHashMap<>();
    private final Set<Integer> mastered = new LinkedHashSet<>();
    private final Map<Integer, MistakeStat> mistakes = new LinkedHashMap<>();
    private final List<HistoryRecord> history = new ArrayList<>();
    private String activeId = "";
    private Goal goal = new Goal(0, 100, 0);
    private boolean reducedMotion;
    private final Map<String, AssessmentSnapshot> assessments = new LinkedHashMap<>();
    private String activeAssessmentId = "";
    private int difficulty = 1;

    @Override public void saveSession(SessionSnapshot snapshot) { sessions.put(snapshot.id, snapshot.copy()); }
    @Override public Optional<SessionSnapshot> loadSession(String id) { return Optional.ofNullable(sessions.get(id)).map(SessionSnapshot::copy); }
    @Override public Optional<SessionSnapshot> loadActiveSession() { return activeId.isEmpty() ? Optional.empty() : loadSession(activeId); }
    @Override public void setActiveSession(String id) { activeId = id == null ? "" : id; }
    @Override public List<SessionSnapshot> unfinishedSessions() {
        List<SessionSnapshot> result = new ArrayList<>();
        for (SessionSnapshot session : sessions.values()) {
            if ("ACTIVE".equals(session.status) || "NEEDS_REVIEW".equals(session.status)) result.add(session.copy());
        }
        return result;
    }
    @Override public int difficulty() { return difficulty; }
    @Override public void saveAssessment(AssessmentSnapshot snapshot) { assessments.put(snapshot.id, snapshot.copy()); }
    @Override public Optional<AssessmentSnapshot> loadAssessment(String id) { return Optional.ofNullable(assessments.get(id)).map(AssessmentSnapshot::copy); }
    @Override public Optional<AssessmentSnapshot> loadActiveAssessment() { return loadAssessment(activeAssessmentId); }
    @Override public void setActiveAssessment(String id) { activeAssessmentId = id == null ? "" : id; }
    @Override public void adoptAssessment(AssessmentSnapshot assessment, List<SessionSnapshot> superseded,
                                          SessionSnapshot replacement, int newDifficulty) {
        for (SessionSnapshot previous : superseded) saveSession(previous);
        saveSession(replacement);
        saveAssessment(assessment);
        difficulty = newDifficulty;
        activeId = replacement.id;
        activeAssessmentId = "";
    }
    @Override public Set<Integer> masteredIds() { return new LinkedHashSet<>(mastered); }
    @Override public void addMastered(Set<Integer> ids) { mastered.addAll(ids); }
    @Override public void recordMistakes(Map<Integer, MistakeStat> stats) { mistakes.putAll(stats); }
    @Override public Map<Integer, MistakeStat> mistakeStats() { return new LinkedHashMap<>(mistakes); }
    @Override public void addHistory(HistoryRecord record) { if (history.stream().noneMatch(x -> x.getId().equals(record.getId()))) history.add(record); }
    @Override public List<HistoryRecord> history() { return new ArrayList<>(history); }
    @Override public Goal loadGoal() { return goal; }
    @Override public void saveGoal(Goal goal) { this.goal = goal; }
    @Override public boolean reducedMotion() { return reducedMotion; }
    @Override public void setReducedMotion(boolean value) { reducedMotion = value; }
}
