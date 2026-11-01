package com.vocabulary.reference.application;

import com.vocabulary.reference.domain.AnswerResult;
import com.vocabulary.reference.domain.Goal;
import com.vocabulary.reference.domain.HistoryRecord;
import com.vocabulary.reference.domain.MistakeStat;
import com.vocabulary.reference.domain.SessionSnapshot;
import com.vocabulary.reference.domain.StudyQuestion;
import com.vocabulary.reference.domain.StudySession;
import com.vocabulary.reference.domain.Word;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class StudyService {
    public static final int ROUND_SIZE = 20;
    private final List<Word> catalog;
    private final StudyRepository repository;

    public StudyService(List<Word> catalog, StudyRepository repository) {
        if (catalog.size() < ROUND_SIZE) throw new IllegalArgumentException("At least 20 words are required");
        this.catalog = Collections.unmodifiableList(new ArrayList<>(catalog));
        this.repository = repository;
    }

    public StudySession startOrResume() {
        Optional<SessionSnapshot> active = repository.loadActiveSession();
        if (active.isPresent()) return new StudySession(active.get());
        return startNewRound();
    }

    public StudySession startNewRound() {
        Set<Integer> mastered = repository.masteredIds();
        List<Word> words = catalog.stream().filter(w -> !mastered.contains(w.getId())).limit(ROUND_SIZE).collect(Collectors.toList());
        if (words.size() < ROUND_SIZE) {
            words = catalog.stream().limit(ROUND_SIZE).collect(Collectors.toList());
        }
        return createSession(words, null);
    }

    public StudySession startReview(String sessionId) {
        SessionSnapshot source = repository.loadSession(sessionId).orElseThrow(() -> new IllegalArgumentException("Unknown session"));
        if (source.wrongIds.isEmpty()) throw new IllegalStateException("There are no mistakes to review");
        List<Word> words = source.wrongIds.stream().map(this::wordById).collect(Collectors.toList());
        return createSession(words, source.sourceSessionId == null ? source.id : source.sourceSessionId);
    }

    public AnswerResult answer(String sessionId, String answer) {
        StudySession session = new StudySession(repository.loadSession(sessionId).orElseThrow(() -> new IllegalArgumentException("Unknown session")));
        StudyQuestion question = session.currentQuestion();
        boolean correct = session.answer(answer);
        boolean complete = session.isFinished();
        boolean review = complete && !session.getWrongIds().isEmpty();
        session.snapshot().status = complete ? (review ? "NEEDS_REVIEW" : "COMPLETED") : "ACTIVE";
        repository.saveSession(session.snapshot());
        repository.setActiveSession(complete ? "" : session.getId());
        if (complete) repository.recordMistakes(session.getMistakes());
        if (complete && !review) {
            if (session.snapshot().sourceSessionId == null) finalizeCompleted(session);
            else finalizeReviewChain(session);
        }
        return new AnswerResult(correct, complete, review, question.getCorrectMeaning());
    }

    public void finalizeReview(String sessionId) {
        StudySession review = new StudySession(repository.loadSession(sessionId).orElseThrow(() -> new IllegalArgumentException("Unknown session")));
        if (!review.isFinished() || !review.getWrongIds().isEmpty()) throw new IllegalStateException("Review is not complete");
        String sourceId = review.snapshot().sourceSessionId;
        SessionSnapshot source = sourceId == null ? review.snapshot() : repository.loadSession(sourceId).orElse(review.snapshot());
        finalizeCompleted(new StudySession(source));
    }

    public Optional<StudySession> active() {
        return repository.loadActiveSession().map(StudySession::new);
    }
    public StudySession session(String id) {
        return new StudySession(repository.loadSession(id).orElseThrow(() -> new IllegalArgumentException("Unknown session")));
    }
    public Set<Integer> mastered() { return repository.masteredIds(); }
    public Map<Integer, MistakeStat> mistakes() { return repository.mistakeStats(); }
    public List<HistoryRecord> history() { return repository.history(); }
    public Goal goal() { return repository.loadGoal(); }
    public void setGoal(int target) {
        Goal goal = repository.loadGoal();
        repository.saveGoal(new Goal(goal.getBaseline(), target, goal.getProgress()));
    }
    public boolean reducedMotion() { return repository.reducedMotion(); }
    public void setReducedMotion(boolean value) { repository.setReducedMotion(value); }
    public Word wordById(int id) { return catalog.stream().filter(w -> w.getId() == id).findFirst().orElseThrow(); }
    public List<Word> catalog() { return catalog; }

    private StudySession createSession(List<Word> words, String sourceId) {
        SessionSnapshot snapshot = new SessionSnapshot();
        snapshot.id = UUID.randomUUID().toString();
        snapshot.sourceSessionId = sourceId;
        snapshot.status = "ACTIVE";
        snapshot.wordIds = words.stream().map(Word::getId).collect(Collectors.toList());
        for (int i = 0; i < words.size(); i++) {
            Word word = words.get(i);
            List<String> options = new ArrayList<>();
            options.add(word.getMeaning());
            for (Word candidate : catalog) {
                if (candidate.getId() != word.getId() && !options.contains(candidate.getMeaning())) {
                    options.add(candidate.getMeaning());
                }
                if (options.size() == 4) break;
            }
            Collections.rotate(options, i % options.size());
            snapshot.questions.add(new SessionSnapshot.StudyQuestionSnapshot(
                    new StudyQuestion(word.getId(), word.getLemma(), word.getMeaning(), options)));
        }
        repository.saveSession(snapshot);
        repository.setActiveSession(snapshot.id);
        return new StudySession(snapshot);
    }

    private void finalizeCompleted(StudySession session) {
        Set<Integer> before = new HashSet<>(repository.masteredIds());
        Set<Integer> all = new LinkedHashSet<>(session.getWordIds());
        repository.addMastered(all);
        int netNew = 0;
        for (Integer id : all) if (!before.contains(id)) netNew++;
        Goal goal = repository.loadGoal();
        goal.addNetNew(netNew);
        repository.saveGoal(goal);
        int errors = session.getMistakes().values().stream().mapToInt(MistakeStat::getErrors).sum();
        int unknowns = session.getMistakes().values().stream().mapToInt(MistakeStat::getUnknowns).sum();
        repository.addHistory(new HistoryRecord(session.getId(), Instant.now(), new ArrayList<>(all), netNew, errors, unknowns));
        session.snapshot().status = "COMPLETED";
        repository.saveSession(session.snapshot());
    }

    private void finalizeReviewChain(StudySession review) {
        String sourceId = review.snapshot().sourceSessionId;
        SessionSnapshot source = repository.loadSession(sourceId).orElseThrow(() -> new IllegalStateException("Missing source session"));
        for (Map.Entry<Integer, MistakeStat> entry : review.getMistakes().entrySet()) {
            MistakeStat existing = source.mistakes.get(entry.getKey());
            if (existing == null) {
                existing = new MistakeStat();
                source.mistakes.put(entry.getKey(), existing);
            }
            for (int i = 0; i < entry.getValue().getErrors(); i++) existing.error(false);
            for (int i = 0; i < entry.getValue().getUnknowns(); i++) existing.error(true);
            for (int i = 0; i < entry.getValue().getRetries(); i++) existing.retry();
        }
        source.wrongIds.clear();
        source.status = "COMPLETED";
        repository.saveSession(source);
        repository.recordMistakes(source.mistakes);
        finalizeCompleted(new StudySession(source));
    }
}
