package com.vocabulary.reference.domain;

public final class MistakeStat {
    private int errors;
    private int unknowns;
    private int retries;

    public MistakeStat() { }
    public MistakeStat(int errors, int unknowns, int retries) {
        this.errors = errors;
        this.unknowns = unknowns;
        this.retries = retries;
    }
    public void error(boolean unknown) {
        if (unknown) unknowns++; else errors++;
    }
    public void retry() { retries++; }
    public int getErrors() { return errors; }
    public int getUnknowns() { return unknowns; }
    public int getRetries() { return retries; }
}
