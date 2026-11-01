package com.vocabulary.reference.application;

import com.vocabulary.reference.domain.Goal;
import com.vocabulary.reference.domain.HistoryRecord;
import com.vocabulary.reference.domain.MistakeStat;
import com.vocabulary.reference.domain.SessionSnapshot;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface StudyRepository {
    void saveSession(SessionSnapshot snapshot);
    Optional<SessionSnapshot> loadSession(String id);
    Optional<SessionSnapshot> loadActiveSession();
    void setActiveSession(String id);
    Set<Integer> masteredIds();
    void addMastered(Set<Integer> ids);
    void recordMistakes(Map<Integer, MistakeStat> stats);
    Map<Integer, MistakeStat> mistakeStats();
    void addHistory(HistoryRecord record);
    List<HistoryRecord> history();
    Goal loadGoal();
    void saveGoal(Goal goal);
    boolean reducedMotion();
    void setReducedMotion(boolean value);
}
