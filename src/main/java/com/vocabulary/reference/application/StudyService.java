package com.vocabulary.reference.application;

import com.vocabulary.reference.domain.AnswerResult;
import com.vocabulary.reference.domain.AssessmentSnapshot;
import com.vocabulary.reference.domain.PlacementAssessment;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
        if (repository.loadActiveSession().isPresent()) throw new IllegalStateException("Continue the active round first.");
        return createSession(selectWords(difficulty(), false), null);
    }

    public StudySession startReview(String sessionId) {
        SessionSnapshot source = repository.loadSession(sessionId).orElseThrow(() -> new IllegalArgumentException("Unknown session"));
        if (!"NEEDS_REVIEW".equals(source.status)) throw new IllegalStateException("This round is no longer available for review.");
        if (repository.loadActiveSession().isPresent()) throw new IllegalStateException("Continue the active round first.");
        if (source.wrongIds.isEmpty()) throw new IllegalStateException("There are no mistakes to review");
        List<Word> words = source.wrongIds.stream().map(this::wordById).collect(Collectors.toList());
        return createSession(words, source.sourceSessionId == null ? source.id : source.sourceSessionId);
    }

    public AnswerResult answer(String sessionId, String answer) {
        StudySession session = new StudySession(repository.loadSession(sessionId).orElseThrow(() -> new IllegalArgumentException("Unknown session")));
        if (!"ACTIVE".equals(session.getStatus()) || !repository.loadActiveSession().map(s -> s.id.equals(sessionId)).orElse(false)) {
            throw new IllegalStateException("This round is no longer active. Continue the current round.");
        }
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
        if ("SUPERSEDED".equals(review.getStatus())) throw new IllegalStateException("This round was replaced by a new placement result.");
        if (!review.isFinished() || !review.getWrongIds().isEmpty()) throw new IllegalStateException("Review is not complete");
        String sourceId = review.snapshot().sourceSessionId;
        SessionSnapshot source = sourceId == null ? review.snapshot() : repository.loadSession(sourceId).orElse(review.snapshot());
        if ("SUPERSEDED".equals(source.status)) throw new IllegalStateException("The original round was replaced.");
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

    public int difficulty() {
        int saved = repository.difficulty();
        return availableLevels().stream().min(Comparator.comparingInt(level -> Math.abs(level - saved))).orElse(1);
    }

    public List<Integer> availableLevels() {
        return catalog.stream().map(StudyService::level).distinct().sorted().collect(Collectors.toList());
    }

    public Optional<PlacementAssessment> activeAssessment() {
        return repository.loadActiveAssessment().map(PlacementAssessment::new);
    }

    public PlacementAssessment assessment(String id) {
        return new PlacementAssessment(repository.loadAssessment(id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown placement assessment.")));
    }

    public PlacementAssessment startOrResumeAssessment() {
        Optional<PlacementAssessment> saved = activeAssessment();
        if (saved.isPresent()) return saved.get();
        Map<Integer, List<Word>> groups = new LinkedHashMap<>();
        for (Integer level : availableLevels()) groups.put(level, new ArrayList<>());
        for (Word word : catalog) groups.get(level(word)).add(word);
        for (List<Word> group : groups.values()) Collections.shuffle(group);
        List<Word> selected = new ArrayList<>();
        for (int index = 0; selected.size() < ROUND_SIZE; index++) {
            for (List<Word> group : groups.values()) {
                if (index < group.size() && selected.size() < ROUND_SIZE) selected.add(group.get(index));
            }
        }
        AssessmentSnapshot snapshot = new AssessmentSnapshot();
        snapshot.id = UUID.randomUUID().toString();
        snapshot.availableLevels = availableLevels();
        snapshot.questions = questions(selected);
        repository.saveAssessment(snapshot);
        repository.setActiveAssessment(snapshot.id);
        return new PlacementAssessment(snapshot);
    }

    public PlacementAssessment answerAssessment(String id, String answer) {
        AssessmentSnapshot snapshot = assessment(id).snapshot();
        if (!"ACTIVE".equals(snapshot.status)) throw new IllegalStateException("The placement assessment is already complete.");
        if (!repository.loadActiveAssessment().map(active -> active.id.equals(id)).orElse(false)) {
            throw new IllegalStateException("This placement assessment is no longer active.");
        }
        StudyQuestion question = snapshot.questions.get(snapshot.currentIndex).toQuestion();
        String chosen = answer == null ? "I don't know" : answer;
        if (!question.getOptions().contains(chosen) && !"I don't know".equals(chosen)) {
            throw new IllegalArgumentException("Choose one of the current answers.");
        }
        snapshot.answers.add(chosen);
        if (question.isCorrect(chosen)) snapshot.correctCount++;
        snapshot.currentIndex++;
        if (snapshot.currentIndex == snapshot.questions.size()) {
            int index = Math.min(snapshot.availableLevels.size() - 1,
                    snapshot.correctCount * snapshot.availableLevels.size() / snapshot.questions.size());
            snapshot.recommendedLevel = snapshot.availableLevels.get(index);
            snapshot.status = "COMPLETED";
        }
        repository.saveAssessment(snapshot);
        return new PlacementAssessment(snapshot);
    }

    public StudySession adoptAssessment(String id) {
        AssessmentSnapshot assessment = assessment(id).snapshot();
        if (assessment.adoptedSessionId != null) return session(assessment.adoptedSessionId);
        if (!"COMPLETED".equals(assessment.status)) throw new IllegalStateException("Complete the placement assessment first.");
        int level = assessment.recommendedLevel;
        SessionSnapshot replacement = newSession(selectWords(level, true), null, level);
        List<SessionSnapshot> superseded = repository.unfinishedSessions();
        for (SessionSnapshot previous : superseded) {
            previous.status = "SUPERSEDED";
            previous.supersededBy = replacement.id;
        }
        assessment.adoptedSessionId = replacement.id;
        repository.adoptAssessment(assessment, superseded, replacement, level);
        return new StudySession(replacement);
    }

    private static int level(Word word) { return Integer.parseInt(word.getLevel().substring(1)); }

    private List<Word> selectWords(int selectedLevel, boolean placementRefresh) {
        Set<Integer> mastered = repository.masteredIds();
        List<Word> candidates = new ArrayList<>(catalog);
        Collections.shuffle(candidates);
        Comparator<Word> distance = Comparator.comparingInt(word -> Math.abs(level(word) - selectedLevel));
        Comparator<Word> priorLearning = Comparator.comparingInt(word -> mastered.contains(word.getId()) ? 1 : 0);
        candidates.sort(placementRefresh ? distance.thenComparing(priorLearning) : priorLearning.thenComparing(distance));
        return new ArrayList<>(candidates.subList(0, ROUND_SIZE));
    }

    private StudySession createSession(List<Word> words, String sourceId) {
        SessionSnapshot snapshot = newSession(words, sourceId, difficulty());
        repository.saveSession(snapshot);
        repository.setActiveSession(snapshot.id);
        return new StudySession(snapshot);
    }

    private SessionSnapshot newSession(List<Word> words, String sourceId, int selectedLevel) {
        SessionSnapshot snapshot = new SessionSnapshot();
        snapshot.id = UUID.randomUUID().toString();
        snapshot.sourceSessionId = sourceId;
        snapshot.status = "ACTIVE";
        snapshot.difficulty = selectedLevel;
        snapshot.wordIds = words.stream().map(Word::getId).collect(Collectors.toList());
        snapshot.questions = questions(words);
        return snapshot;
    }

    private List<SessionSnapshot.StudyQuestionSnapshot> questions(List<Word> words) {
        List<SessionSnapshot.StudyQuestionSnapshot> questions = new ArrayList<>();
        for (int i = 0; i < words.size(); i++) {
            Word word = words.get(i);
            List<String> options = new ArrayList<>();
            options.add(word.getMeaning());
            List<Word> alternatives = new ArrayList<>(catalog);
            Collections.shuffle(alternatives);
            alternatives.sort(Comparator.comparingInt(candidate -> Math.abs(level(candidate) - level(word))));
            for (Word candidate : alternatives) {
                if (candidate.getId() != word.getId() && !options.contains(candidate.getMeaning())) {
                    options.add(candidate.getMeaning());
                }
                if (options.size() == 4) break;
            }
            if (options.size() != 4) throw new IllegalStateException("The word bank needs four distinct meanings.");
            Collections.shuffle(options);
            questions.add(new SessionSnapshot.StudyQuestionSnapshot(
                    new StudyQuestion(word.getId(), word.getLemma(), word.getMeaning(), options)));
        }
        return questions;
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
