package com.vocabulary.reference.domain;

import java.util.ArrayList;
import java.util.List;

/** A local placement attempt, kept separate from credited learning sessions. */
public final class AssessmentSnapshot {
    public String id;
    public String status = "ACTIVE";
    public int currentIndex;
    public int correctCount;
    public int recommendedLevel;
    public String adoptedSessionId;
    public List<Integer> availableLevels = new ArrayList<>();
    public List<SessionSnapshot.StudyQuestionSnapshot> questions = new ArrayList<>();
    public List<String> answers = new ArrayList<>();

    public AssessmentSnapshot() { }

    public AssessmentSnapshot copy() {
        AssessmentSnapshot copy = new AssessmentSnapshot();
        copy.id = id;
        copy.status = status;
        copy.currentIndex = currentIndex;
        copy.correctCount = correctCount;
        copy.recommendedLevel = recommendedLevel;
        copy.adoptedSessionId = adoptedSessionId;
        copy.availableLevels = new ArrayList<>(availableLevels);
        copy.answers = new ArrayList<>(answers);
        for (SessionSnapshot.StudyQuestionSnapshot question : questions) {
            copy.questions.add(new SessionSnapshot.StudyQuestionSnapshot(question.toQuestion()));
        }
        return copy;
    }
}
