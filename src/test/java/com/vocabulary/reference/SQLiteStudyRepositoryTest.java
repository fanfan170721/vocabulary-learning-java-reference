package com.vocabulary.reference;

import com.vocabulary.reference.domain.SessionSnapshot;
import com.vocabulary.reference.infrastructure.SQLiteStudyRepository;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SQLiteStudyRepositoryTest {
    @Test
    void sessionAndSettingsSurviveReopen() throws Exception {
        Path directory = Files.createTempDirectory("vocab-reference");
        Path database = directory.resolve("profile.sqlite");
        String id;
        try (SQLiteStudyRepository repository = new SQLiteStudyRepository(database)) {
            SessionSnapshot snapshot = new SessionSnapshot();
            snapshot.id = "session-1";
            snapshot.status = "ACTIVE";
            repository.saveSession(snapshot);
            repository.setActiveSession(snapshot.id);
            repository.setReducedMotion(true);
            id = repository.loadActiveSession().orElseThrow().id;
        }
        try (SQLiteStudyRepository reopened = new SQLiteStudyRepository(database)) {
            assertEquals(id, reopened.loadActiveSession().orElseThrow().id);
            assertTrue(reopened.reducedMotion());
        }
    }
}
