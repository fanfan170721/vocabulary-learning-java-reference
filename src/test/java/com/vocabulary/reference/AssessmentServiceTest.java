package com.vocabulary.reference;

import com.vocabulary.reference.application.StudyService;
import com.vocabulary.reference.domain.PlacementAssessment;
import com.vocabulary.reference.domain.SessionSnapshot;
import com.vocabulary.reference.domain.StudySession;
import com.vocabulary.reference.domain.Word;
import com.vocabulary.reference.infrastructure.ContentRepository;
import com.vocabulary.reference.infrastructure.InMemoryStudyRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class AssessmentServiceTest {
    @Test void placementSamplesDistinctWordsAcrossLevelsAndDoesNotGrantLearningCredit() throws Exception {
        StudyService service = service();
        PlacementAssessment assessment = service.startOrResumeAssessment();
        assertEquals(20, assessment.size());
        assertEquals(20, assessment.snapshot().questions.stream().map(q -> q.wordId).distinct().count());
        assertEquals(Set.of(1, 2, 3), assessment.snapshot().questions.stream()
                .map(q -> Integer.parseInt(service.wordById(q.wordId).getLevel().substring(1))).collect(Collectors.toSet()));
        assertEquals(assessment.getId(), service.startOrResumeAssessment().getId());
        assertThrows(IllegalStateException.class, () -> service.adoptAssessment(assessment.getId()));
        assertThrows(IllegalArgumentException.class, () -> service.answerAssessment(assessment.getId(), "not an option"));
        assertEquals(0, service.assessment(assessment.getId()).getCurrentIndex());
        PlacementAssessment completed = finish(service, assessment, true);
        assertEquals(3, completed.getRecommendedLevel());
        assertEquals(1, service.difficulty());
        assertTrue(service.mastered().isEmpty());
        assertTrue(service.history().isEmpty());
        assertEquals(0, service.goal().getProgress());
        assertEquals(completed.getId(), service.startOrResumeAssessment().getId());
    }

    @Test void adoptingImmediatelyReplacesPartialRoundAndPreservesItsAnswersWithoutCredit() throws Exception {
        StudyService service = service();
        StudySession old = service.startOrResume();
        service.answer(old.getId(), "I don't know");
        service.answer(old.getId(), service.session(old.getId()).currentQuestion().getCorrectMeaning());
        SessionSnapshot before = service.session(old.getId()).snapshot().copy();
        PlacementAssessment assessment = finish(service, service.startOrResumeAssessment(), true);
        StudySession replacement = service.adoptAssessment(assessment.getId());
        assertNotEquals(old.getId(), replacement.getId());
        assertEquals(20, replacement.size());
        assertEquals(0, replacement.getCurrentIndex());
        assertTrue(replacement.getWordIds().stream().allMatch(id -> service.wordById(id).getLevel().equals("L3")));
        SessionSnapshot archived = service.session(old.getId()).snapshot();
        assertEquals("SUPERSEDED", archived.status);
        assertEquals(replacement.getId(), archived.supersededBy);
        assertEquals(before.answers, archived.answers);
        assertEquals(before.currentIndex, archived.currentIndex);
        assertEquals(1, archived.mistakes.get(before.wordIds.get(0)).getUnknowns());
        assertTrue(service.mastered().isEmpty());
        assertTrue(service.history().isEmpty());
        assertEquals(0, service.goal().getProgress());
        assertEquals(replacement.getId(), service.startOrResume().getId());
        assertThrows(IllegalStateException.class, () -> service.answer(old.getId(), "I don't know"));
        assertThrows(IllegalStateException.class, () -> service.startReview(old.getId()));
    }

    @Test void sameLevelRetakeRefreshesImmediatelyAndDuplicateAdoptionDoesNotResetAgain() throws Exception {
        StudyService service = service();
        StudySession old = service.startOrResume();
        service.answer(old.getId(), old.currentQuestion().getCorrectMeaning());
        PlacementAssessment assessment = finish(service, service.startOrResumeAssessment(), false);
        assertEquals(1, assessment.getRecommendedLevel());
        StudySession replacement = service.adoptAssessment(assessment.getId());
        assertNotEquals(old.getId(), replacement.getId());
        assertEquals(0, replacement.getCurrentIndex());
        service.answer(replacement.getId(), replacement.currentQuestion().getCorrectMeaning());
        StudySession duplicate = service.adoptAssessment(assessment.getId());
        assertEquals(replacement.getId(), duplicate.getId());
        assertEquals(1, duplicate.getCurrentIndex());
        assertEquals(duplicate.getId(), service.active().orElseThrow().getId());
        assertTrue(service.assessment(assessment.getId()).isAdopted());
        assertTrue(service.activeAssessment().isEmpty());
        PlacementAssessment next = service.startOrResumeAssessment();
        assertNotEquals(assessment.getId(), next.getId());
        assertEquals(0, next.getCurrentIndex());
    }

    @Test void completedMasteryHistoryAndGoalSurviveLaterReassessment() throws Exception {
        StudyService service = service();
        StudySession completed = service.startOrResume();
        for (int i = 0; i < completed.size(); i++) {
            service.answer(completed.getId(), service.session(completed.getId()).currentQuestion().getCorrectMeaning());
        }
        Set<Integer> mastered = service.mastered();
        String historyId = service.history().get(0).getId();
        service.startOrResume();
        PlacementAssessment assessment = finish(service, service.startOrResumeAssessment(), true);
        service.adoptAssessment(assessment.getId());
        assertEquals(mastered, service.mastered());
        assertEquals(20, service.goal().getProgress());
        assertEquals(1, service.history().size());
        assertEquals(historyId, service.history().get(0).getId());
        assertEquals("COMPLETED", service.session(completed.getId()).getStatus());
    }

    @Test void ordinaryNextRoundPrioritizesUnmasteredWordsBeforeRepeatingTheSelectedLevel() throws Exception {
        StudyService service = service();
        StudySession first = service.startOrResume();
        assertTrue(first.getWordIds().stream().allMatch(id -> service.wordById(id).getLevel().equals("L1")));
        for (int i = 0; i < first.size(); i++) {
            service.answer(first.getId(), service.session(first.getId()).currentQuestion().getCorrectMeaning());
        }
        StudySession next = service.startOrResume();
        assertEquals(20, next.size());
        assertTrue(next.getWordIds().stream().noneMatch(service.mastered()::contains));
        assertTrue(next.getWordIds().stream().allMatch(id -> service.wordById(id).getLevel().equals("L2")));
        assertEquals(1, service.difficulty());
    }

    @Test void detachedAssessmentCannotAcceptAStaleAnswer() throws Exception {
        InMemoryStudyRepository repository = new InMemoryStudyRepository();
        StudyService service = new StudyService(new ContentRepository().load(), repository);
        PlacementAssessment old = service.startOrResumeAssessment();
        repository.setActiveAssessment("");
        PlacementAssessment current = service.startOrResumeAssessment();
        assertThrows(IllegalStateException.class, () -> service.answerAssessment(old.getId(), "I don't know"));
        assertEquals(0, service.assessment(old.getId()).getCurrentIndex());
        assertEquals(current.getId(), service.activeAssessment().orElseThrow().getId());
    }

    @Test void replacingAReviewArchivesItsUnfinishedSourceAndRejectsLateReviewFinalization() throws Exception {
        StudyService service = service();
        StudySession source = service.startOrResume();
        for (int i = 0; i < source.size(); i++) {
            service.answer(source.getId(), i == 0 ? "I don't know"
                    : service.session(source.getId()).currentQuestion().getCorrectMeaning());
        }
        StudySession review = service.startReview(source.getId());
        PlacementAssessment assessment = finish(service, service.startOrResumeAssessment(), true);
        StudySession replacement = service.adoptAssessment(assessment.getId());
        assertEquals("SUPERSEDED", service.session(source.getId()).getStatus());
        assertEquals("SUPERSEDED", service.session(review.getId()).getStatus());
        assertThrows(IllegalStateException.class, () -> service.answer(review.getId(), review.currentQuestion().getCorrectMeaning()));
        assertThrows(IllegalStateException.class, () -> service.finalizeReview(review.getId()));
        assertTrue(service.mastered().isEmpty());
        assertTrue(service.history().isEmpty());
        assertEquals(replacement.getId(), service.active().orElseThrow().getId());
    }

    @Test void nearestLevelsFillASmallTargetLevelWithoutRepeatedWords() {
        List<Word> words = new ArrayList<>();
        for (int level : List.of(1, 3, 5)) {
            for (int n = 0; n < 10; n++) {
                int id = level * 100 + n;
                words.add(new Word(id, "word" + id, "meaning" + id, "L" + level));
            }
        }
        StudyService service = new StudyService(words, new InMemoryStudyRepository());
        PlacementAssessment assessment = finish(service, service.startOrResumeAssessment(), true);
        assertEquals(5, assessment.getRecommendedLevel());
        StudySession replacement = service.adoptAssessment(assessment.getId());
        assertEquals(20, new HashSet<>(replacement.getWordIds()).size());
        assertEquals(10, replacement.getWordIds().stream().filter(id -> service.wordById(id).getLevel().equals("L5")).count());
        assertEquals(10, replacement.getWordIds().stream().filter(id -> service.wordById(id).getLevel().equals("L3")).count());
    }

    static PlacementAssessment finish(StudyService service, PlacementAssessment assessment, boolean correct) {
        while (!assessment.isFinished()) {
            assessment = service.answerAssessment(assessment.getId(),
                    correct ? assessment.currentQuestion().getCorrectMeaning() : "I don't know");
        }
        return assessment;
    }

    private StudyService service() throws Exception {
        return new StudyService(new ContentRepository().load(), new InMemoryStudyRepository());
    }
}
