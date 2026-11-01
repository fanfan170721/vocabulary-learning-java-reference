package com.vocabulary.reference.domain;

public final class Goal {
    private int baseline;
    private int target;
    private int progress;

    public Goal() { }
    public Goal(int baseline, int target, int progress) {
        if (target < baseline) throw new IllegalArgumentException("Target must be at least baseline");
        this.baseline = baseline;
        this.target = target;
        this.progress = Math.max(0, progress);
    }
    public int getBaseline() { return baseline; }
    public int getTarget() { return target; }
    public int getProgress() { return progress; }
    public void addNetNew(int count) { progress += Math.max(0, count); }
    public int denominator() { return Math.max(1, target - baseline); }
    public int percent() { return Math.min(100, (progress * 100) / denominator()); }
}
