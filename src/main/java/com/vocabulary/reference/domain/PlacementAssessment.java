package com.vocabulary.reference.domain;

/** A 20-question reference placement, not a standardized vocabulary estimate. */
public final class PlacementAssessment {
    private final AssessmentSnapshot snapshot;

    public PlacementAssessment(AssessmentSnapshot snapshot) { this.snapshot = snapshot.copy(); }
    public AssessmentSnapshot snapshot() { return snapshot.copy(); }
    public String getId() { return snapshot.id; }
    public String getStatus() { return snapshot.status; }
    public int getCurrentIndex() { return snapshot.currentIndex; }
    public int size() { return snapshot.questions.size(); }
    public int getCorrectCount() { return snapshot.correctCount; }
    public int getRecommendedLevel() { return snapshot.recommendedLevel; }
    public boolean isFinished() { return snapshot.currentIndex >= size(); }
    public boolean isAdopted() { return snapshot.adoptedSessionId != null; }
    public StudyQuestion currentQuestion() {
        return isFinished() ? null : snapshot.questions.get(snapshot.currentIndex).toQuestion();
    }
}
