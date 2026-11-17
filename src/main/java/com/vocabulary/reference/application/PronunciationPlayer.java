package com.vocabulary.reference.application;

import java.util.function.BooleanSupplier;

/** Blocking playback runs on the pronunciation worker, never the Swing EDT. */
public interface PronunciationPlayer extends AutoCloseable {
    void play(byte[] audio, BooleanSupplier cancelled) throws Exception;
    void stop();
    @Override default void close() { stop(); }
}
