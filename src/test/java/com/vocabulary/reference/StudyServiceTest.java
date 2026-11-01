package com.vocabulary.reference;

import com.vocabulary.reference.application.StudyService;
import com.vocabulary.reference.domain.AnswerResult;
import com.vocabulary.reference.domain.StudySession;
import com.vocabulary.reference.infrastructure.ContentRepository;
import com.vocabulary.reference.infrastructure.InMemoryStudyRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StudyServiceTest {
    @Test
    void interruptedSessionResumesAtExactQuestion() throws Exception {
        InMemoryStudyRepository repository = new InMemoryStudyRepository();
        StudyService service = new StudyService(new ContentRepository().load(), repository);
        StudySession first = service.startNewRound();
        String firstLemma = first.currentQuestion().getLemma();
        service.answer(first.getId(), first.currentQuestion().getOptions().get(0));
        StudySession resumed = service.active().orElseThrow();
        assertEquals(1, resumed.getCurrentIndex());
        assertNotEquals(firstLemma, resumed.currentQuestion().getLemma());
    }

    @Test
    void wrongWordsAreRetriedAndOnlyFinalCompletionAddsMastery() throws Exception {
        InMemoryStudyRepository repository = new InMemoryStudyRepository();
        StudyService service = new StudyService(new ContentRepository().load(), repository);
        StudySession first = service.startNewRound();
        for (int i = 0; i < first.size(); i++) {
            String answer = i == 0 ? "I don't know" : first.snapshot().questions.get(i).correctMeaning;
            AnswerResult result = service.answer(first.getId(), answer);
            assertEquals(i == first.size() - 1, result.isRoundComplete());
        }
        assertEquals(0, service.mastered().size());
        StudySession review = service.startReview(first.getId());
        assertEquals(1, review.size());
        service.answer(review.getId(), review.currentQuestion().getCorrectMeaning());
        assertEquals(20, service.mastered().size());
        assertEquals(1, service.history().size());
        assertEquals(20, service.goal().getProgress());
    }

    @Test
    void fourOptionsRemainAvailableForTheSingleReviewWord() throws Exception {
        InMemoryStudyRepository repository = new InMemoryStudyRepository();
        StudyService service = new StudyService(new ContentRepository().load(), repository);
        StudySession session = service.startNewRound();
        assertEquals(4, session.currentQuestion().getOptions().size());
        assertTrue(session.currentQuestion().getOptions().contains(session.currentQuestion().getCorrectMeaning()));
    }
}
