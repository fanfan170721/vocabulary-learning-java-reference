package com.vocabulary.reference.domain;

public final class AnswerResult {
    private final boolean correct;
    private final boolean roundComplete;
    private final boolean needsReview;
    private final String expected;

    public AnswerResult(boolean correct, boolean roundComplete, boolean needsReview, String expected) {
        this.correct = correct; this.roundComplete = roundComplete; this.needsReview = needsReview; this.expected = expected;
    }
    public boolean isCorrect() { return correct; }
    public boolean isRoundComplete() { return roundComplete; }
    public boolean needsReview() { return needsReview; }
    public String getExpected() { return expected; }
}
