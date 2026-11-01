package com.vocabulary.reference.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class HistoryRecord {
    private String id;
    private Instant completedAt;
    private List<Integer> wordIds;
    private int netNew;
    private int errorCount;
    private int unknownCount;

    public HistoryRecord() { }

    public HistoryRecord(String id, Instant completedAt, List<Integer> wordIds,
                         int netNew, int errorCount, int unknownCount) {
        this.id = id;
        this.completedAt = completedAt;
        this.wordIds = Collections.unmodifiableList(new ArrayList<>(wordIds));
        this.netNew = netNew;
        this.errorCount = errorCount;
        this.unknownCount = unknownCount;
    }
    public String getId() { return id; }
    public Instant getCompletedAt() { return completedAt; }
    public List<Integer> getWordIds() { return wordIds; }
    public int getNetNew() { return netNew; }
    public int getErrorCount() { return errorCount; }
    public int getUnknownCount() { return unknownCount; }
}
