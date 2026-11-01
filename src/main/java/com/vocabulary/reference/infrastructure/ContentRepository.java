package com.vocabulary.reference.infrastructure;

import com.vocabulary.reference.domain.Word;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class ContentRepository {
    public List<Word> load() throws IOException {
        InputStream stream = getClass().getResourceAsStream("/content.csv");
        if (stream == null) throw new IOException("content.csv is missing");
        List<Word> words = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            boolean header = true;
            while ((line = reader.readLine()) != null) {
                if (header) { header = false; continue; }
                String[] p = line.split(",", -1);
                if (p.length == 4) words.add(new Word(Integer.parseInt(p[0]), p[1], p[2], p[3]));
            }
        }
        return words;
    }
}
