package com.vocabulary.reference.infrastructure;

import com.sun.net.httpserver.HttpServer;
import com.vocabulary.reference.application.PronunciationClip;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class DictionaryPronunciationClientTest {
    private HttpServer server;
    private ExecutorService requests;
    private String base;
    private final Map<String, byte[]> responses = new ConcurrentHashMap<>();
    private final Map<String, Integer> codes = new ConcurrentHashMap<>();
    private final AtomicInteger hits = new AtomicInteger();
    private volatile int code = 200;
    private volatile long delay;

    @BeforeEach void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        requests = Executors.newCachedThreadPool(task -> { Thread thread = new Thread(task); thread.setDaemon(true); return thread; });
        server.setExecutor(requests);
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/", exchange -> {
            hits.incrementAndGet();
            try {
                if (delay > 0) Thread.sleep(delay);
                byte[] body = responses.getOrDefault(exchange.getRequestURI().getPath(), new byte[0]);
                exchange.sendResponseHeaders(codes.getOrDefault(exchange.getRequestURI().getPath(), code), body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
    }

    @AfterEach void stop() { server.stop(0); requests.shutdownNow(); }

    @Test void selectsRequestedAccentPreservesAttributionAndCachesTheRecording() throws Exception {
        responses.put("/entries/hello", json("/hello-uk.mp3", "/hello-us.mp3"));
        responses.put("/hello-us.mp3", mp3());
        responses.put("/hello-uk.mp3", mp3());
        DictionaryPronunciationClient client = client(Duration.ofSeconds(2));
        PronunciationClip us = client.fetch("HELLO", "en-US");
        assertEquals("en-US", us.accent());
        assertEquals("Source page", us.source());
        assertTrue(us.license().contains("BY 3.0"));
        assertTrue(us.license().contains("https://creativecommons.org/licenses/by/3.0/"));
        assertArrayEquals(mp3(), us.audio());
        assertSame(us, client.fetch("hello", "en-US"));
        assertEquals(2, hits.get(), "The cached lookup must not contact the service again.");
        assertEquals("en-GB", client.fetch("hello", "en-GB").accent());
        assertEquals(4, hits.get());
    }

    @Test void availableAlternateAccentIsNotMislabelledAsRequestedAccent() throws Exception {
        responses.put("/entries/hello", json("/hello-au.mp3", "/hello-uk.mp3"));
        responses.put("/hello-uk.mp3", mp3());
        PronunciationClip clip = client(Duration.ofSeconds(2)).fetch("hello", "en-US");
        assertEquals("en-GB", clip.accent());
    }

    @Test void invalidOrUntrustedLinksAreSkippedWithoutOpeningThem() throws Exception {
        responses.put("/entries/hello", ("[{\"phonetics\":[{\"audio\":\"https://example.invalid/hello-us.mp3\"},"
                + "{\"audio\":\"file:///C:/secret.mp3\"},{\"audio\":\"not a URI\"}]}]").getBytes(StandardCharsets.UTF_8));
        IOException failure = assertThrows(IOException.class, () -> client(Duration.ofSeconds(2)).fetch("hello", "en-US"));
        assertTrue(failure.getMessage().contains("No recorded"));
        assertEquals(1, hits.get());
    }

    @Test void brokenDictionaryProxyRecoversTheOriginalCommonsRecordingWithAttribution() throws Exception {
        responses.put("/entries/hello", ("[{\"phonetics\":[{\"audio\":\"" + base + "/hello-uk.mp3\","
                + "\"sourceUrl\":\"https://commons.wikimedia.org/w/index.php?curid=9021983\"}]}]").getBytes(StandardCharsets.UTF_8));
        codes.put("/hello-uk.mp3", 404);
        responses.put("/commons", ("{\"query\":{\"pages\":{\"9021983\":{\"videoinfo\":[{\"url\":\""
                + base + "/original.ogg\",\"extmetadata\":{\"Artist\":{\"value\":\"<b>Test reader</b>\"},"
                + "\"LicenseShortName\":{\"value\":\"CC BY 3.0\"},\"LicenseUrl\":{\"value\":\"https://creativecommons.org/licenses/by/3.0/\"}}}]}}}}")
                .getBytes(StandardCharsets.UTF_8));
        byte[] recording = {'O', 'g', 'g', 'S', 0, 1};
        responses.put("/original.ogg", recording);
        DictionaryPronunciationClient client = new DictionaryPronunciationClient(URI.create(base + "/entries/"),
                HttpClient.newHttpClient(), Duration.ofSeconds(2), URI.create(base + "/commons"));
        PronunciationClip clip = client.fetch("hello", "en-US");
        assertArrayEquals(recording, clip.audio());
        assertEquals("en-GB", clip.accent());
        assertTrue(clip.source().startsWith("Test reader — https://commons.wikimedia.org/"));
        assertTrue(clip.license().startsWith("CC BY 3.0"));
        assertEquals(4, hits.get());
        assertSame(clip, client.fetch("hello", "en-US"));
        assertEquals(4, hits.get());
    }

    @Test void sourceRecoveryRejectsUntrustedSourcePagesAndReturnedAudioHosts() {
        responses.put("/entries/hello", ("[{\"phonetics\":[{\"audio\":\"" + base + "/hello-uk.mp3\","
                + "\"sourceUrl\":\"https://commons.wikimedia.org.evil.invalid/w/index.php?curid=9021983\"}]}]").getBytes(StandardCharsets.UTF_8));
        codes.put("/hello-uk.mp3", 404);
        DictionaryPronunciationClient client = new DictionaryPronunciationClient(URI.create(base + "/entries/"),
                HttpClient.newHttpClient(), Duration.ofSeconds(2), URI.create(base + "/commons"));
        assertThrows(IOException.class, () -> client.fetch("hello", "en-US"));
        assertEquals(2, hits.get());
        responses.put("/entries/hello", ("[{\"phonetics\":[{\"audio\":\"" + base + "/hello-uk.mp3\","
                + "\"sourceUrl\":\"https://commons.wikimedia.org/w/index.php?curid=9021983\"}]}]").getBytes(StandardCharsets.UTF_8));
        responses.put("/commons", "{\"query\":{\"pages\":{\"9021983\":{\"videoinfo\":[{\"url\":\"http://localhost/secret.ogg\"}]}}}}"
                .getBytes(StandardCharsets.UTF_8));
        assertTrue(assertThrows(IOException.class, () -> client.fetch("hello", "en-US")).getMessage().contains("allowed audio link"));
        assertEquals(5, hits.get());
    }

    @Test void rejectsNonAudioAndOversizedBodies() {
        responses.put("/entries/hello", json("/hello-us.mp3"));
        responses.put("/hello-us.mp3", "<html>Error</html>".getBytes(StandardCharsets.UTF_8));
        IOException format = assertThrows(IOException.class, () -> client(Duration.ofSeconds(2)).fetch("hello", "en-US"));
        assertTrue(format.getMessage().contains("not an MP3"));
        responses.put("/hello-us.mp3", new byte[2 * 1024 * 1024 + 1]);
        IOException size = assertThrows(IOException.class, () -> client(Duration.ofSeconds(2)).fetch("hello", "en-US"));
        assertTrue(size.getMessage().contains("size limit"));
    }

    @Test void reportsHttpFailureAndTimesOutAcrossTheWholeResponse() {
        code = 503;
        responses.put("/entries/hello", "unavailable".getBytes(StandardCharsets.UTF_8));
        assertTrue(assertThrows(IOException.class, () -> client(Duration.ofSeconds(2)).fetch("hello", "en-US"))
                .getMessage().contains("503"));
        code = 200;
        delay = 700;
        IOException timeout = assertThrows(IOException.class, () -> client(Duration.ofMillis(100)).fetch("hello", "en-US"));
        assertTrue(timeout.getMessage().toLowerCase().contains("timed out"));
    }

    @Test void emptyProviderResultsCannotBeReportedAsPlayableAudio() {
        responses.put("/entries/hello", "[]".getBytes(StandardCharsets.UTF_8));
        assertThrows(IOException.class, () -> client(Duration.ofSeconds(2)).fetch("hello", "en-US"));
        assertEquals(1, hits.get());
    }

    private DictionaryPronunciationClient client(Duration timeout) {
        return new DictionaryPronunciationClient(URI.create(base + "/entries/"),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build(), timeout);
    }

    private byte[] json(String... paths) {
        StringBuilder text = new StringBuilder("[{\"phonetics\":[");
        for (int i = 0; i < paths.length; i++) {
            if (i > 0) text.append(',');
            text.append("{\"audio\":\"").append(base).append(paths[i])
                    .append("\",\"sourceUrl\":\"Source page\",\"license\":{\"name\":\"BY 3.0\",\"url\":\"https://creativecommons.org/licenses/by/3.0/\"}}");
        }
        return text.append("]}]").toString().getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] mp3() { return new byte[]{'I', 'D', '3', 4, 0, 0}; }
}
