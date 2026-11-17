package com.vocabulary.reference;

import com.vocabulary.reference.application.StudyService;
import com.vocabulary.reference.domain.PlacementAssessment;
import com.vocabulary.reference.domain.StudySession;
import com.vocabulary.reference.infrastructure.ContentRepository;
import com.vocabulary.reference.infrastructure.SQLiteStudyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AssessmentPersistenceTest {
    @TempDir Path directory;

    @Test void assessmentAndReplacedRoundSurviveRestartWithExactQuestionOptions() throws Exception {
        Path database = directory.resolve("profile.sqlite");
        String oldId;
        String assessmentId;
        String pendingLemma;
        List<String> pendingOptions;
        try (SQLiteStudyRepository repository = new SQLiteStudyRepository(database)) {
            StudyService service = service(repository);
            assertEquals(1, service.difficulty());
            StudySession old = service.startOrResume();
            oldId = old.getId();
            service.answer(oldId, "I don't know");
            PlacementAssessment assessment = service.startOrResumeAssessment();
            assessmentId = assessment.getId();
            for (int n = 0; n < 5; n++) {
                assessment = service.answerAssessment(assessmentId, assessment.currentQuestion().getCorrectMeaning());
            }
            pendingLemma = assessment.currentQuestion().getLemma();
            pendingOptions = assessment.currentQuestion().getOptions();
        }
        String replacementId;
        try (SQLiteStudyRepository repository = new SQLiteStudyRepository(database)) {
            StudyService service = service(repository);
            PlacementAssessment assessment = service.startOrResumeAssessment();
            assertEquals(assessmentId, assessment.getId());
            assertEquals(5, assessment.getCurrentIndex());
            assertEquals(pendingLemma, assessment.currentQuestion().getLemma());
            assertEquals(pendingOptions, assessment.currentQuestion().getOptions());
            assessment = AssessmentServiceTest.finish(service, assessment, true);
            replacementId = service.adoptAssessment(assessmentId).getId();
        }
        try (SQLiteStudyRepository repository = new SQLiteStudyRepository(database)) {
            StudyService service = service(repository);
            assertEquals(3, service.difficulty());
            assertEquals(replacementId, service.startOrResume().getId());
            assertEquals(replacementId, service.adoptAssessment(assessmentId).getId());
            assertTrue(service.activeAssessment().isEmpty());
            assertEquals("SUPERSEDED", service.session(oldId).getStatus());
            assertEquals(1, service.session(oldId).getCurrentIndex());
            assertEquals(1, service.session(oldId).snapshot().answers.size());
            assertTrue(service.mastered().isEmpty());
            assertTrue(service.history().isEmpty());
            assertThrows(IllegalStateException.class, () -> service.answer(oldId, "I don't know"));
        }
    }

    @Test void failedAdoptionRollsBackDifficultyReplacementAndAssessmentTogether() throws Exception {
        Path database = directory.resolve("profile.sqlite");
        try (SQLiteStudyRepository repository = new SQLiteStudyRepository(database)) {
            StudyService service = service(repository);
            StudySession old = service.startOrResume();
            service.answer(old.getId(), "I don't know");
            PlacementAssessment assessment = AssessmentServiceTest.finish(service, service.startOrResumeAssessment(), true);
            try (Connection databaseConnection = DriverManager.getConnection("jdbc:sqlite:" + database);
                 Statement statement = databaseConnection.createStatement()) {
                statement.executeUpdate("CREATE TRIGGER reject_difficulty BEFORE INSERT ON records "
                        + "WHEN NEW.kind='setting' AND NEW.id='difficulty' "
                        + "BEGIN SELECT RAISE(ABORT, 'Simulated write failure'); END");
            }
            assertThrows(IllegalStateException.class, () -> service.adoptAssessment(assessment.getId()));
            assertEquals(1, service.difficulty());
            assertEquals(old.getId(), service.startOrResume().getId());
            assertEquals("ACTIVE", service.session(old.getId()).getStatus());
            assertEquals(1, service.session(old.getId()).getCurrentIndex());
            assertFalse(service.assessment(assessment.getId()).isAdopted());
            assertEquals(assessment.getId(), service.activeAssessment().orElseThrow().getId());
            assertEquals(1, repository.unfinishedSessions().size());
            assertTrue(service.history().isEmpty());
            try (Connection databaseConnection = DriverManager.getConnection("jdbc:sqlite:" + database);
                 Statement statement = databaseConnection.createStatement()) {
                statement.executeUpdate("DROP TRIGGER reject_difficulty");
            }
            assertNotEquals(old.getId(), service.adoptAssessment(assessment.getId()).getId());
        }
    }

    private StudyService service(SQLiteStudyRepository repository) throws Exception {
        return new StudyService(new ContentRepository().load(), repository);
    }
}
