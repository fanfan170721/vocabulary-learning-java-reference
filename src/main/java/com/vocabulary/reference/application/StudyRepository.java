package com.vocabulary.reference.application;

import com.vocabulary.reference.domain.Goal;
import com.vocabulary.reference.domain.HistoryRecord;
import com.vocabulary.reference.domain.MistakeStat;
import com.vocabulary.reference.domain.SessionSnapshot;
import com.vocabulary.reference.domain.AssessmentSnapshot;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface StudyRepository {
    void saveSession(SessionSnapshot snapshot);
    Optional<SessionSnapshot> loadSession(String id);
    Optional<SessionSnapshot> loadActiveSession();
    void setActiveSession(String id);
    List<SessionSnapshot> unfinishedSessions();
    int difficulty();
    void saveAssessment(AssessmentSnapshot snapshot);
    Optional<AssessmentSnapshot> loadAssessment(String id);
    Optional<AssessmentSnapshot> loadActiveAssessment();
    void setActiveAssessment(String id);
    /** Persists the adoption and session replacement as one atomic operation. */
    void adoptAssessment(AssessmentSnapshot assessment, List<SessionSnapshot> superseded,
                         SessionSnapshot replacement, int difficulty);
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
