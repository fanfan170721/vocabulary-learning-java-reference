package com.vocabulary.reference.application;

import java.io.IOException;

public final class DictionaryService {
    private final DictionaryClient client;
    public DictionaryService(DictionaryClient client) { this.client = client; }
    public DictionaryClient.DictionaryEntry lookup(String word) throws IOException, InterruptedException {
        if (word == null || word.trim().isEmpty()) throw new IllegalArgumentException("Enter a word");
        return client.lookup(word.trim());
    }
}
