package com.vocabulary.reference.application;

import java.io.IOException;

public interface DictionaryClient {
    DictionaryEntry lookup(String word) throws IOException, InterruptedException;

    final class DictionaryEntry {
        private final String word;
        private final String phonetic;
        private final String definition;
        public DictionaryEntry(String word, String phonetic, String definition) {
            this.word = word; this.phonetic = phonetic; this.definition = definition;
        }
        public String getWord() { return word; }
        public String getPhonetic() { return phonetic; }
        public String getDefinition() { return definition; }
        @Override public String toString() { return word + (phonetic.isEmpty() ? "" : " " + phonetic) + "\n" + definition; }
    }
}
