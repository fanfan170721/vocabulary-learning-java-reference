package com.vocabulary.reference.infrastructure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.BufferedInputStream;
import java.io.InputStream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import static org.junit.jupiter.api.Assertions.*;

class RecordedAudioDecodeTest {
    @Test @Timeout(5)
    void decodesTheLicensedOggFixtureWithoutNetworkOrAnAudioDevice() throws Exception {
        InputStream resource = getClass().getResourceAsStream("/audio/hello-uk.ogg");
        assertNotNull(resource);
        try (AudioInputStream encoded = AudioSystem.getAudioInputStream(new BufferedInputStream(resource))) {
            AudioFormat source = encoded.getFormat();
            AudioFormat target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, source.getSampleRate(),
                    16, source.getChannels(), source.getChannels() * 2, source.getSampleRate(), false);
            try (AudioInputStream decoded = AudioSystem.getAudioInputStream(target, encoded)) {
                byte[] samples = decoded.readNBytes(512 * 1024);
                assertTrue(samples.length > 1000, "A real recording must decode to PCM samples.");
                assertTrue(samples.length < 512 * 1024, "The short fixture must have a bounded duration.");
                assertEquals(AudioFormat.Encoding.PCM_SIGNED, decoded.getFormat().getEncoding());
                boolean hasSignal = false;
                for (byte sample : samples) hasSignal |= sample != 0;
                assertTrue(hasSignal, "The recorded voice must not be silent.");
            }
        }
    }
}
