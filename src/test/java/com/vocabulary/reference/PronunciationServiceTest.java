package com.vocabulary.reference;

import com.vocabulary.reference.application.PronunciationClient;
import com.vocabulary.reference.application.PronunciationClip;
import com.vocabulary.reference.application.PronunciationPlayer;
import com.vocabulary.reference.application.PronunciationService;
import com.vocabulary.reference.application.SpeechFallback;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class PronunciationServiceTest {
    @Test void recordedAudioIsPreferredAndAlternateAccentIsLabelledHonestly() throws Exception {
        FakePlayer player = new FakePlayer();
        FakeFallback fallback = new FakeFallback();
        List<String> messages = new CopyOnWriteArrayList<>();
        try (PronunciationService service = new PronunciationService((word, accent) -> {
            assertEquals("hello", word);
            assertEquals("en-US", accent);
            return new PronunciationClip(new byte[]{1, 2, 3}, "en-GB", "Dictionary source", "CC BY 3.0");
        }, player, fallback)) {
            service.speak(" Hello ", "en-US", messages::add);
            assertTrue(player.played.await(3, TimeUnit.SECONDS));
            assertArrayEquals(new byte[]{1, 2, 3}, player.audio);
            assertEquals(0, fallback.calls.get());
            assertTrue(messages.stream().anyMatch(text -> text.contains("UK English")
                    && text.contains("requested US English is unavailable") && text.contains("CC BY 3.0")));
        }
    }

    @Test void downloadFailureUsesExplicitlyLabelledLocalSpeech() throws Exception {
        FakeFallback fallback = new FakeFallback();
        List<String> messages = new CopyOnWriteArrayList<>();
        CountDownLatch done = new CountDownLatch(1);
        try (PronunciationService service = new PronunciationService((word, accent) -> {
            throw new IOException("Network unavailable.");
        }, new FakePlayer(), fallback)) {
            service.speak("hello", "en-GB", text -> {
                messages.add(text);
                if (text.endsWith("Test voice")) done.countDown();
            });
            assertTrue(done.await(3, TimeUnit.SECONDS));
            assertEquals(1, fallback.calls.get());
            assertEquals("en-GB", fallback.accent);
            assertTrue(messages.stream().anyMatch(text -> text.contains("less natural") && text.contains("Network unavailable")));
        }
    }

    @Test void playbackFailureAlsoFallsBackWithoutPretendingTheRecordingSucceeded() throws Exception {
        FakeFallback fallback = new FakeFallback();
        FakePlayer player = new FakePlayer();
        player.failure = new IOException("No audio output device.");
        CountDownLatch done = new CountDownLatch(1);
        try (PronunciationService service = new PronunciationService((word, accent) -> clip(), player, fallback)) {
            service.speak("hello", "en-US", text -> { if (text.endsWith("Test voice")) done.countDown(); });
            assertTrue(done.await(3, TimeUnit.SECONDS));
            assertEquals(1, fallback.calls.get());
        }
    }

    @Test void unavailableLocalVoiceLeavesAnActionableStatus() throws Exception {
        FakeFallback fallback = new FakeFallback();
        fallback.failure = new IOException("No UK voice installed.");
        List<String> messages = new CopyOnWriteArrayList<>();
        CountDownLatch done = new CountDownLatch(1);
        try (PronunciationService service = new PronunciationService((word, accent) -> {
            throw new IOException("No recording.");
        }, new FakePlayer(), fallback)) {
            service.speak("hello", "en-GB", text -> {
                messages.add(text);
                if (text.contains("continue studying")) done.countDown();
            });
            assertTrue(done.await(3, TimeUnit.SECONDS));
            assertTrue(messages.get(messages.size() - 1).contains("No UK voice installed"));
        }
    }

    @Test void aLateCancelledLookupCannotPlayOrOverwriteTheNewWord() throws Exception {
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        FakePlayer player = new FakePlayer();
        FakeFallback fallback = new FakeFallback();
        List<String> oldMessages = new CopyOnWriteArrayList<>();
        PronunciationClient client = (word, accent) -> {
            if (word.equals("old")) {
                firstEntered.countDown();
                boolean released = false;
                while (!released) {
                    try { released = releaseFirst.await(3, TimeUnit.SECONDS); }
                    catch (InterruptedException ignored) { /* Simulate a provider that ignores cancellation. */ }
                }
                return new PronunciationClip(new byte[]{1}, accent, "Old source", "");
            }
            return new PronunciationClip(new byte[]{2}, accent, "New source", "");
        };
        try (PronunciationService service = new PronunciationService(client, player, fallback)) {
            service.speak("old", "en-US", oldMessages::add);
            assertTrue(firstEntered.await(3, TimeUnit.SECONDS));
            service.speak("new", "en-US", ignored -> { });
            releaseFirst.countDown();
            assertTrue(player.played.await(3, TimeUnit.SECONDS));
            assertArrayEquals(new byte[]{2}, player.audio);
            assertEquals(1, player.calls.get());
            assertEquals(1, oldMessages.size(), "Only the initial loading message may reach the old view.");
            assertEquals(0, fallback.calls.get());
        } finally { releaseFirst.countDown(); }
    }

    @Test void stopCancelsActivePlaybackAndCloseRejectsLaterRequests() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch stopped = new CountDownLatch(1);
        AtomicInteger fetches = new AtomicInteger();
        PronunciationPlayer player = new PronunciationPlayer() {
            @Override public void play(byte[] bytes, BooleanSupplier cancelled) throws InterruptedException {
                started.countDown();
                stopped.await(3, TimeUnit.SECONDS);
            }
            @Override public void stop() { if (started.getCount() == 0) stopped.countDown(); }
        };
        PronunciationService service = new PronunciationService((word, accent) -> {
            fetches.incrementAndGet(); return clip();
        }, player, new FakeFallback());
        try {
            service.speak("hello", "en-US", ignored -> { });
            assertTrue(started.await(3, TimeUnit.SECONDS));
            service.stop();
            assertTrue(stopped.await(3, TimeUnit.SECONDS));
            service.close();
            service.speak("new", "en-US", ignored -> fail("Closed service must not emit status."));
            assertEquals(1, fetches.get());
        } finally { service.close(); }
    }

    @Test void invalidTextNeverReachesAProviderAndClipBytesAreImmutable() {
        AtomicInteger fetches = new AtomicInteger();
        List<String> messages = new CopyOnWriteArrayList<>();
        try (PronunciationService service = new PronunciationService((word, accent) -> {
            fetches.incrementAndGet(); return clip();
        }, new FakePlayer(), new FakeFallback())) {
            service.speak(" ", "en-US", messages::add);
            service.speak("word\ncommand", "en-US", messages::add);
            service.speak("w".repeat(101), "en-US", messages::add);
            assertEquals(0, fetches.get());
            assertEquals(3, messages.size());
        }
        byte[] original = {1};
        PronunciationClip clip = new PronunciationClip(original, "en-US", "source", "license");
        original[0] = 2;
        clip.audio()[0] = 3;
        assertArrayEquals(new byte[]{1}, clip.audio());
    }

    private static PronunciationClip clip() { return new PronunciationClip(new byte[]{1}, "en-US", "source", ""); }

    private static final class FakePlayer implements PronunciationPlayer {
        private final CountDownLatch played = new CountDownLatch(1);
        private final AtomicInteger calls = new AtomicInteger();
        private volatile byte[] audio;
        private Exception failure;
        @Override public void play(byte[] bytes, BooleanSupplier cancelled) throws Exception {
            if (failure != null) throw failure;
            audio = bytes;
            calls.incrementAndGet();
            played.countDown();
        }
        @Override public void stop() { }
    }

    private static final class FakeFallback implements SpeechFallback {
        private final AtomicInteger calls = new AtomicInteger();
        private volatile String accent;
        private Exception failure;
        @Override public String speak(String word, String selectedAccent, BooleanSupplier cancelled) throws Exception {
            if (failure != null) throw failure;
            calls.incrementAndGet();
            accent = selectedAccent;
            return "Test voice";
        }
        @Override public void stop() { }
    }
}
