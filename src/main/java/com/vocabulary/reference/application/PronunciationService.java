package com.vocabulary.reference.application;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Coordinates cancellable recorded audio and an explicitly labelled local fallback. */
public final class PronunciationService implements AutoCloseable {
    private final PronunciationClient client;
    private final PronunciationPlayer player;
    private final SpeechFallback fallback;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "pronunciation");
        thread.setDaemon(true);
        return thread;
    });
    private volatile long generation;
    private Future<?> request;
    private volatile boolean closed;

    public PronunciationService(PronunciationClient client, PronunciationPlayer player, SpeechFallback fallback) {
        this.client = Objects.requireNonNull(client);
        this.player = Objects.requireNonNull(player);
        this.fallback = Objects.requireNonNull(fallback);
    }

    /** Status consumers run on the caller/worker thread; Swing callers must dispatch to the EDT. */
    public synchronized void speak(String word, String accent, Consumer<String> status) {
        Objects.requireNonNull(status);
        stop();
        if (closed) return;
        String clean = word == null ? "" : word.strip();
        if (clean.isEmpty() || clean.length() > 100 || clean.chars().anyMatch(Character::isISOControl)) {
            status.accept("Choose a word to hear its pronunciation.");
            return;
        }
        String selectedAccent = "en-GB".equals(accent) ? "en-GB" : "en-US";
        long current = generation;
        emit(current, status, "Loading recorded pronunciation...");
        request = worker.submit(() -> play(current, clean, selectedAccent, status));
    }

    private void play(long current, String word, String accent, Consumer<String> status) {
        BooleanSupplier cancelled = () -> !isCurrent(current) || Thread.currentThread().isInterrupted();
        try {
            PronunciationClip clip = client.fetch(word.toLowerCase(Locale.ROOT), accent);
            if (cancelled.getAsBoolean()) return;
            String label = "Recorded pronunciation (" + accentName(clip.accent()) + ")";
            if (!accent.equals(clip.accent())) label += "; requested " + accentName(accent) + " is unavailable";
            label += " — " + clip.source();
            if (!clip.license().isBlank()) label += " [" + clip.license() + "]";
            emit(current, status, label);
            player.play(clip.audio(), cancelled);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Exception failure) {
            if (cancelled.getAsBoolean()) return;
            emit(current, status, "Recording unavailable; using local " + accentName(accent)
                    + " speech (less natural). " + reason(failure));
            try {
                String voice = fallback.speak(word, accent, cancelled);
                if (!cancelled.getAsBoolean()) {
                    emit(current, status, "Local " + accentName(accent) + " speech (less natural): " + voice);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (Exception localFailure) {
                emit(current, status, "Pronunciation unavailable. " + reason(localFailure)
                        + " You can continue studying without audio.");
            }
        }
    }

    public synchronized void stop() {
        generation++;
        if (request != null) {
            request.cancel(true);
            request = null;
        }
        player.stop();
        fallback.stop();
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        stop();
        worker.shutdownNow();
        player.close();
        fallback.close();
    }

    private boolean isCurrent(long current) { return !closed && current == generation; }

    private synchronized void emit(long current, Consumer<String> status, String message) {
        if (isCurrent(current)) {
            try { status.accept(message); }
            catch (RuntimeException ignored) { /* A disposed UI must not trigger fallback audio. */ }
        }
    }

    private static String accentName(String accent) {
        if ("en-US".equals(accent)) return "US English";
        if ("en-GB".equals(accent)) return "UK English";
        if ("en-AU".equals(accent)) return "Australian English";
        return "accent unspecified";
    }

    private static String reason(Exception failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? "Audio could not be played." : message;
    }
}
