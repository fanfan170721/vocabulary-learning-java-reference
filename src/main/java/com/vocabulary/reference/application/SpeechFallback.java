package com.vocabulary.reference.application;

import java.util.function.BooleanSupplier;

/** Optional local speech for words without a playable dictionary recording. */
public interface SpeechFallback extends AutoCloseable {
    String speak(String word, String accent, BooleanSupplier cancelled) throws Exception;
    void stop();
    @Override default void close() { stop(); }
}
