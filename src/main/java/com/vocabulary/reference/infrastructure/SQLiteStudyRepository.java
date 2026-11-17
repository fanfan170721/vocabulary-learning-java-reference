package com.vocabulary.reference.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.vocabulary.reference.application.StudyRepository;
import com.vocabulary.reference.domain.Goal;
import com.vocabulary.reference.domain.HistoryRecord;
import com.vocabulary.reference.domain.MistakeStat;
import com.vocabulary.reference.domain.SessionSnapshot;
import com.vocabulary.reference.domain.AssessmentSnapshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class SQLiteStudyRepository implements StudyRepository, AutoCloseable {
    private final Connection connection;
    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);

    public SQLiteStudyRepository(Path databasePath) throws SQLException, IOException {
        Path parent = databasePath.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS records (kind TEXT NOT NULL, id TEXT NOT NULL, data TEXT NOT NULL, updated_at TEXT NOT NULL, PRIMARY KEY(kind,id))");
        }
    }

    public static SQLiteStudyRepository openDefault() throws SQLException, IOException {
        String configured = System.getenv("VOCAB_DATA_DIR");
        Path dir = configured == null || configured.isBlank()
                ? Paths.get(System.getProperty("user.home"), ".vocabulary-learning-reference")
                : Paths.get(configured);
        return new SQLiteStudyRepository(dir.resolve("profile.sqlite"));
    }

    @Override public void saveSession(SessionSnapshot snapshot) { put("session", snapshot.id, json(snapshot)); }
    @Override public Optional<SessionSnapshot> loadSession(String id) { return get("session", id).map(x -> fromJson(x, SessionSnapshot.class)); }
    @Override public Optional<SessionSnapshot> loadActiveSession() {
        return get("setting", "activeSession").filter(x -> !x.isEmpty()).flatMap(this::loadSession);
    }
    @Override public void setActiveSession(String id) { put("setting", "activeSession", id == null ? "" : id); }
    @Override public List<SessionSnapshot> unfinishedSessions() {
        List<SessionSnapshot> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT data FROM records WHERE kind='session'")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SessionSnapshot session = fromJson(rs.getString(1), SessionSnapshot.class);
                    if ("ACTIVE".equals(session.status) || "NEEDS_REVIEW".equals(session.status)) result.add(session);
                }
            }
        } catch (SQLException e) { throw new IllegalStateException(e); }
        return result;
    }
    @Override public int difficulty() { return get("setting", "difficulty").map(Integer::parseInt).orElse(1); }
    @Override public void saveAssessment(AssessmentSnapshot snapshot) { put("assessment", snapshot.id, json(snapshot)); }
    @Override public Optional<AssessmentSnapshot> loadAssessment(String id) {
        return get("assessment", id).map(value -> fromJson(value, AssessmentSnapshot.class));
    }
    @Override public Optional<AssessmentSnapshot> loadActiveAssessment() {
        return get("setting", "activeAssessment").filter(value -> !value.isEmpty()).flatMap(this::loadAssessment);
    }
    @Override public void setActiveAssessment(String id) { put("setting", "activeAssessment", id == null ? "" : id); }
    @Override public synchronized void adoptAssessment(AssessmentSnapshot assessment, List<SessionSnapshot> superseded,
                                                       SessionSnapshot replacement, int newDifficulty) {
        try {
            connection.setAutoCommit(false);
            try {
                for (SessionSnapshot previous : superseded) saveSession(previous);
                saveSession(replacement);
                saveAssessment(assessment);
                setActiveSession(replacement.id);
                setActiveAssessment("");
                put("setting", "difficulty", String.valueOf(newDifficulty));
                connection.commit();
            } catch (RuntimeException | SQLException failure) {
                connection.rollback();
                throw failure;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException failure) { throw new IllegalStateException("Could not save the new difficulty and round.", failure); }
    }
    @Override public Set<Integer> masteredIds() {
        Set<Integer> ids = new LinkedHashSet<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT id FROM records WHERE kind='mastered'")) {
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) ids.add(Integer.parseInt(rs.getString(1))); }
        } catch (SQLException e) { throw new IllegalStateException(e); }
        return ids;
    }
    @Override public void addMastered(Set<Integer> ids) { for (Integer id : ids) put("mastered", String.valueOf(id), "true"); }
    @Override public void recordMistakes(Map<Integer, MistakeStat> stats) {
        for (Map.Entry<Integer, MistakeStat> e : stats.entrySet()) put("mistake", String.valueOf(e.getKey()), json(e.getValue()));
    }
    @Override public Map<Integer, MistakeStat> mistakeStats() {
        Map<Integer, MistakeStat> result = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT id,data FROM records WHERE kind='mistake'")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.put(Integer.parseInt(rs.getString(1)), fromJson(rs.getString(2), MistakeStat.class));
            }
        } catch (SQLException e) { throw new IllegalStateException(e); }
        return result;
    }
    @Override public void addHistory(HistoryRecord record) { put("history", record.getId(), json(record)); }
    @Override public List<HistoryRecord> history() {
        List<HistoryRecord> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT data FROM records WHERE kind='history' ORDER BY updated_at DESC")) {
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) result.add(fromJson(rs.getString(1), HistoryRecord.class)); }
        } catch (SQLException e) { throw new IllegalStateException(e); }
        return result;
    }
    @Override public Goal loadGoal() { return get("setting", "goal").map(x -> fromJson(x, Goal.class)).orElse(new Goal(0, 100, 0)); }
    @Override public void saveGoal(Goal goal) { put("setting", "goal", json(goal)); }
    @Override public boolean reducedMotion() { return get("setting", "reducedMotion").map(Boolean::parseBoolean).orElse(false); }
    @Override public void setReducedMotion(boolean value) { put("setting", "reducedMotion", String.valueOf(value)); }

    private void put(String kind, String id, String data) {
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO records(kind,id,data,updated_at) VALUES(?,?,?,?) ON CONFLICT(kind,id) DO UPDATE SET data=excluded.data,updated_at=excluded.updated_at")) {
            ps.setString(1, kind); ps.setString(2, id); ps.setString(3, data); ps.setString(4, Instant.now().toString()); ps.executeUpdate();
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }
    private Optional<String> get(String kind, String id) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT data FROM records WHERE kind=? AND id=?")) {
            ps.setString(1, kind); ps.setString(2, id);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? Optional.of(rs.getString(1)) : Optional.empty(); }
        } catch (SQLException e) { throw new IllegalStateException(e); }
    }
    private String json(Object value) { try { return mapper.writeValueAsString(value); } catch (IOException e) { throw new IllegalStateException(e); } }
    private <T> T fromJson(String value, Class<T> type) { try { return mapper.readValue(value, type); } catch (IOException e) { throw new IllegalStateException(e); } }
    @Override public void close() { try { connection.close(); } catch (SQLException e) { throw new IllegalStateException(e); } }
}
