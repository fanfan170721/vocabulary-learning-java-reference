package com.vocabulary.reference.application;

/** A recorded pronunciation with its actual accent and source attribution. */
public final class PronunciationClip {
    private final byte[] audio;
    private final String accent;
    private final String source;
    private final String license;

    public PronunciationClip(byte[] audio, String accent, String source, String license) {
        this.audio = audio.clone();
        this.accent = accent;
        this.source = source;
        this.license = license;
    }

    public byte[] audio() { return audio.clone(); }
    public String accent() { return accent; }
    public String source() { return source; }
    public String license() { return license; }
}
