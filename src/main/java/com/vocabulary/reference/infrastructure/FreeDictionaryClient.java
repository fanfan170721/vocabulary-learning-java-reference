package com.vocabulary.reference.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vocabulary.reference.application.DictionaryClient;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class FreeDictionaryClient implements DictionaryClient {
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public FreeDictionaryClient() {
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @Override public DictionaryEntry lookup(String word) throws IOException, InterruptedException {
        String encoded = word.trim().replace(" ", "%20");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.dictionaryapi.dev/api/v2/entries/en/" + encoded))
                .timeout(Duration.ofSeconds(8)).header("Accept", "application/json").GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) throw new IOException("Dictionary API returned HTTP " + response.statusCode());
        JsonNode root = mapper.readTree(response.body());
        JsonNode first = root.isArray() && root.size() > 0 ? root.get(0) : root;
        String phonetic = first.path("phonetic").asText("");
        String definition = "No definition returned";
        JsonNode meanings = first.path("meanings");
        if (meanings.isArray() && meanings.size() > 0) {
            JsonNode definitions = meanings.get(0).path("definitions");
            if (definitions.isArray() && definitions.size() > 0) definition = definitions.get(0).path("definition").asText(definition);
        }
        return new DictionaryEntry(word, phonetic, definition);
    }
}
