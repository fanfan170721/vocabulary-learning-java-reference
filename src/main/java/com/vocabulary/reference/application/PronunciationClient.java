package com.vocabulary.reference.application;

import java.io.IOException;

public interface PronunciationClient {
    PronunciationClip fetch(String word, String preferredAccent) throws IOException, InterruptedException;
}
