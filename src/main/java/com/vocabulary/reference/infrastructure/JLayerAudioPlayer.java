package com.vocabulary.reference.infrastructure;

import com.vocabulary.reference.application.PronunciationPlayer;
import javazoom.jl.player.Player;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.function.BooleanSupplier;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

/** MP3 and Ogg Vorbis playback; close interrupts sound when the user changes words. */
public final class JLayerAudioPlayer implements PronunciationPlayer {
    private Player current;
    private SourceDataLine currentPcm;

    @Override public void play(byte[] audio, BooleanSupplier cancelled) throws Exception {
        if (cancelled.getAsBoolean()) return;
        if (audio.length >= 4 && audio[0] == 'O' && audio[1] == 'g' && audio[2] == 'g' && audio[3] == 'S') {
            playOgg(audio, cancelled);
            return;
        }
        Player next = new Player(new ByteArrayInputStream(audio));
        synchronized (this) {
            if (cancelled.getAsBoolean()) { next.close(); return; }
            current = next;
        }
        try { next.play(); }
        finally {
            synchronized (this) { if (current == next) current = null; }
            next.close();
        }
    }

    @Override public void stop() {
        Player previous;
        SourceDataLine pcm;
        synchronized (this) { previous = current; current = null; pcm = currentPcm; currentPcm = null; }
        if (previous != null) previous.close();
        if (pcm != null) { pcm.stop(); pcm.flush(); pcm.close(); }
    }

    private void playOgg(byte[] audio, BooleanSupplier cancelled) throws Exception {
        try (AudioInputStream encoded = AudioSystem.getAudioInputStream(new ByteArrayInputStream(audio))) {
            AudioFormat source = encoded.getFormat();
            if (source.getChannels() < 1 || source.getChannels() > 2
                    || source.getSampleRate() < 8000 || source.getSampleRate() > 192000) {
                throw new IOException("The recording has an unsupported audio format.");
            }
            AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, source.getSampleRate(),
                    16, source.getChannels(), source.getChannels() * 2, source.getSampleRate(), false);
            try (AudioInputStream decoded = AudioSystem.getAudioInputStream(format, encoded)) {
                SourceDataLine line = AudioSystem.getSourceDataLine(format);
                try {
                    line.open(format);
                    synchronized (this) {
                        if (cancelled.getAsBoolean()) return;
                        currentPcm = line;
                    }
                    line.start();
                    byte[] buffer = new byte[4096];
                    int total = 0;
                    int limit = (int) (format.getFrameRate() * format.getFrameSize() * 15);
                    int count;
                    while (!cancelled.getAsBoolean() && (count = decoded.read(buffer)) >= 0) {
                        total += count;
                        if (total > limit) throw new IOException("The pronunciation recording exceeds 15 seconds.");
                        line.write(buffer, 0, count);
                    }
                    if (!cancelled.getAsBoolean()) line.drain();
                } finally {
                    synchronized (this) { if (currentPcm == line) currentPcm = null; }
                    line.stop(); line.flush(); line.close();
                }
            }
        }
    }
}
