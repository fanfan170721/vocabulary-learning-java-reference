package com.vocabulary.reference.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vocabulary.reference.application.PronunciationClient;
import com.vocabulary.reference.application.PronunciationClip;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Downloads the audio links actually returned by the dictionary, with bounded memory and time. */
public final class DictionaryPronunciationClient implements PronunciationClient {
    private static final URI DEFAULT_ENDPOINT = URI.create("https://api.dictionaryapi.dev/api/v2/entries/en/");
    private static final Set<String> AUDIO_HOSTS = Set.of("api.dictionaryapi.dev", "ssl.gstatic.com", "upload.wikimedia.org");
    private static final int JSON_LIMIT = 512 * 1024;
    private static final int AUDIO_LIMIT = 2 * 1024 * 1024;
    private static final int CACHE_LIMIT = 8 * 1024 * 1024;
    private final URI endpoint;
    private final URI commonsApi;
    private final HttpClient http;
    private final Duration timeout;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, PronunciationClip> cache = new LinkedHashMap<>(16, 0.75f, true);
    private int cacheBytes;

    public DictionaryPronunciationClient() {
        this(DEFAULT_ENDPOINT, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER).build(), Duration.ofSeconds(5));
    }

    /** Package-level injection keeps automated tests on a loopback HTTP server. */
    DictionaryPronunciationClient(URI endpoint, HttpClient http, Duration timeout) {
        this(endpoint, http, timeout, URI.create("https://commons.wikimedia.org/w/api.php"));
    }

    DictionaryPronunciationClient(URI endpoint, HttpClient http, Duration timeout, URI commonsApi) {
        this.endpoint = endpoint;
        this.http = http;
        this.timeout = timeout;
        this.commonsApi = commonsApi;
    }

    @Override public PronunciationClip fetch(String word, String preferredAccent) throws IOException, InterruptedException {
        String normalized = word.strip().toLowerCase(Locale.ROOT);
        String key = normalized + ":" + preferredAccent;
        synchronized (cache) {
            if (cache.containsKey(key)) return cache.get(key);
        }
        URI lookup = endpoint.resolve(URLEncoder.encode(normalized, StandardCharsets.UTF_8).replace("+", "%20"));
        byte[] json = download(lookup, JSON_LIMIT, "application/json");
        List<Candidate> candidates = candidates(mapper.readTree(json));
        candidates.sort(Comparator.comparingInt(candidate -> preference(candidate.accent, preferredAccent)));
        IOException failure = new IOException("No recorded pronunciation was returned for this word.");
        for (int i = 0; i < Math.min(3, candidates.size()); i++) {
            Candidate candidate = candidates.get(i);
            try {
                PronunciationClip clip;
                try {
                    byte[] audio = audio(candidate.uri);
                    clip = new PronunciationClip(audio, candidate.accent, candidate.source, candidate.license);
                } catch (IOException unavailable) {
                    clip = originalRecording(candidate, unavailable);
                }
                remember(key, clip, clip.audio().length);
                return clip;
            } catch (IOException unavailable) {
                failure = unavailable;
            }
        }
        throw failure;
    }

    private byte[] audio(URI uri) throws IOException, InterruptedException {
        byte[] bytes = download(uri, AUDIO_LIMIT, "audio/mpeg, audio/ogg, application/ogg");
        if (!looksLikeAudio(bytes)) throw new IOException("The pronunciation response was not an MP3 or Ogg recording.");
        return bytes;
    }

    /** Recover a broken dictionary proxy only through its stated Commons source page. */
    private PronunciationClip originalRecording(Candidate candidate, IOException failure)
            throws IOException, InterruptedException {
        String page = commonsPageId(candidate.source);
        if (page == null) throw failure;
        URI query = URI.create(commonsApi + "?action=query&format=json&pageids=" + page
                + "&prop=videoinfo&viprop=url%7Cextmetadata&viextmetadatafilter=Artist%7CLicenseShortName%7CLicenseUrl");
        JsonNode pages = mapper.readTree(download(query, JSON_LIMIT, "application/json")).path("query").path("pages");
        JsonNode information = pages.path(page).path("videoinfo").path(0);
        String address = information.path("url").asText("");
        URI source;
        try { source = URI.create(address); }
        catch (IllegalArgumentException invalid) { throw new IOException("The recording source returned an invalid audio link."); }
        if (!allowedAudio(source)) throw new IOException("The recording source did not return an allowed audio link.");
        JsonNode metadata = information.path("extmetadata");
        String artist = plainText(metadata.path("Artist").path("value").asText(""));
        String attribution = (artist.isBlank() ? "" : artist + " — ") + candidate.source;
        String license = metadata.path("LicenseShortName").path("value").asText(candidate.license);
        String licenseUrl = metadata.path("LicenseUrl").path("value").asText("");
        if (!licenseUrl.isBlank()) license += " " + licenseUrl;
        return new PronunciationClip(audio(source), candidate.accent, attribution, license);
    }

    private static String commonsPageId(String link) {
        try {
            URI source = URI.create(link);
            if (!"https".equals(source.getScheme()) || !"commons.wikimedia.org".equals(source.getHost())
                    || source.getUserInfo() != null || (source.getPort() != -1 && source.getPort() != 443)
                    || !"/w/index.php".equals(source.getPath()) || source.getRawQuery() == null) return null;
            for (String parameter : source.getRawQuery().split("&")) {
                if (parameter.matches("curid=[1-9][0-9]{0,12}")) return parameter.substring(6);
            }
        } catch (IllegalArgumentException invalid) { return null; }
        return null;
    }

    private static String plainText(String value) {
        return value.replaceAll("<[^>]*>", "").replace("&amp;", "&").replace("&quot;", "\"")
                .replace("&#39;", "'").strip();
    }

    private List<Candidate> candidates(JsonNode root) {
        List<Candidate> results = new ArrayList<>();
        if (!root.isArray()) return results;
        for (JsonNode entry : root) {
            for (JsonNode phonetic : entry.path("phonetics")) {
                String link = phonetic.path("audio").asText("");
                if (link.isBlank()) continue;
                if (link.startsWith("//")) link = "https:" + link;
                try {
                    URI uri = URI.create(link);
                    if (!allowedAudio(uri) || results.stream().anyMatch(item -> item.uri.equals(uri))) continue;
                    String source = phonetic.path("sourceUrl").asText(uri.toString());
                    String license = phonetic.path("license").path("name").asText("");
                    String licenseUrl = phonetic.path("license").path("url").asText("");
                    if (!licenseUrl.isBlank()) license += " " + licenseUrl;
                    results.add(new Candidate(uri, accent(uri), source, license));
                } catch (IllegalArgumentException ignored) {
                    // A malformed provider link is not an instruction to open an arbitrary address.
                }
            }
        }
        return results;
    }

    private boolean allowedAudio(URI uri) {
        if (uri.getUserInfo() != null || uri.getFragment() != null) return false;
        if (!endpoint.equals(DEFAULT_ENDPOINT)) {
            return "http".equals(uri.getScheme()) && "127.0.0.1".equals(uri.getHost())
                    && uri.getHost().equals(endpoint.getHost()) && uri.getPort() == endpoint.getPort();
        }
        return "https".equals(uri.getScheme()) && AUDIO_HOSTS.contains(uri.getHost())
                && (uri.getPort() == -1 || uri.getPort() == 443);
    }

    private byte[] download(URI uri, int limit, String accept) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(timeout).header("Accept", accept)
                .header("User-Agent", "VocabularyLearningReference/0.1 (https://github.com/fanfan170721)").GET().build();
        CompletableFuture<HttpResponse<byte[]>> pending = http.sendAsync(request, info -> new LimitedBody(limit));
        try {
            HttpResponse<byte[]> response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (response.statusCode() / 100 != 2) throw new IOException("Pronunciation service returned HTTP " + response.statusCode() + ".");
            return response.body();
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            throw new IOException(cause instanceof IOException ? cause.getMessage() : "The pronunciation download failed.", cause);
        } catch (TimeoutException failure) {
            throw new IOException("The pronunciation service timed out.", failure);
        } finally {
            if (!pending.isDone()) pending.cancel(true);
        }
    }

    private void remember(String key, PronunciationClip clip, int bytes) {
        synchronized (cache) {
            PronunciationClip previous = cache.put(key, clip);
            if (previous != null) cacheBytes -= previous.audio().length;
            cacheBytes += bytes;
            while (cache.size() > 64 || cacheBytes > CACHE_LIMIT) {
                String oldest = cache.keySet().iterator().next();
                cacheBytes -= cache.remove(oldest).audio().length;
            }
        }
    }

    private static int preference(String actual, String requested) {
        if (actual.equals(requested)) return 0;
        if (actual.equals("en-US") || actual.equals("en-GB")) return 1;
        if (actual.equals("en-AU")) return 2;
        return 3;
    }

    private static String accent(URI uri) {
        String path = uri.getPath().toLowerCase(Locale.ROOT);
        if (path.contains("-us.") || path.contains("_us_")) return "en-US";
        if (path.contains("-uk.") || path.contains("-gb.") || path.contains("_gb_")) return "en-GB";
        if (path.contains("-au.") || path.contains("_au_")) return "en-AU";
        return "unknown";
    }

    private static boolean looksLikeAudio(byte[] bytes) {
        if (bytes.length < 4) return false;
        if (bytes[0] == 'O' && bytes[1] == 'g' && bytes[2] == 'g' && bytes[3] == 'S') return true;
        if (bytes[0] == 'I' && bytes[1] == 'D' && bytes[2] == '3') return true;
        return (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xe0) == 0xe0;
    }

    private static final class Candidate {
        private final URI uri;
        private final String accent;
        private final String source;
        private final String license;
        private Candidate(URI uri, String accent, String source, String license) {
            this.uri = uri; this.accent = accent; this.source = source; this.license = license;
        }
    }

    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int limit;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private Flow.Subscription subscription;

        private LimitedBody(int limit) { this.limit = limit; }
        @Override public CompletionStage<byte[]> getBody() { return result; }
        @Override public void onSubscribe(Flow.Subscription value) {
            subscription = value;
            value.request(1);
        }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > limit - bytes.size()) {
                    subscription.cancel();
                    result.completeExceptionally(new IOException("The pronunciation response exceeded the size limit."));
                    return;
                }
                byte[] part = new byte[buffer.remaining()];
                buffer.get(part);
                bytes.write(part, 0, part.length);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable failure) { result.completeExceptionally(failure); }
        @Override public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
