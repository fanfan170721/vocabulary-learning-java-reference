package com.vocabulary.reference.ui;

import com.vocabulary.reference.application.DictionaryClient;
import com.vocabulary.reference.application.DictionaryService;
import com.vocabulary.reference.application.PronunciationClip;
import com.vocabulary.reference.application.PronunciationPlayer;
import com.vocabulary.reference.application.PronunciationService;
import com.vocabulary.reference.application.SpeechFallback;
import com.vocabulary.reference.application.StudyService;
import com.vocabulary.reference.domain.PlacementAssessment;
import com.vocabulary.reference.domain.StudyQuestion;
import com.vocabulary.reference.domain.StudySession;
import com.vocabulary.reference.infrastructure.ContentRepository;
import com.vocabulary.reference.infrastructure.InMemoryStudyRepository;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(30)
class ReferenceWorkflowTest {
    @Test void adoptionThroughRealButtonsReplacesTheDisplayedUnfinishedRoundImmediately() throws Exception {
        StudyService study = new StudyService(new ContentRepository().load(), new InMemoryStudyRepository());
        StudySession old = study.startNewRound();
        study.answer(old.getId(), old.currentQuestion().getCorrectMeaning());
        AtomicReference<String> playedWord = new AtomicReference<>();
        CountDownLatch played = new CountDownLatch(1);
        PronunciationService speech = silentSpeech((word, accent) -> {
            playedWord.set(word);
            return new PronunciationClip(new byte[]{1}, accent, "Test dictionary", "");
        }, played);
        MainPanel panel = edt(() -> {
            MainPanel root = new MainPanel(study, dictionary(), speech, () -> { });
            root.setSize(1100, 780);
            layout(root);
            return root;
        });
        try {
            clickText(panel, "Start / continue");
            assertTrue(edt(() -> label(panel, "study.progress").getText()).contains("Question 2 / 20"));
            clickText(panel, "Retake placement");
            PlacementAssessment assessment = study.activeAssessment().orElseThrow();
            answerCorrect(panel, study, assessment.getId());
            click(panel, "assessment.home");
            clickText(panel, "Retake placement");
            assertTrue(edt(() -> label(panel, "assessment.progress").getText()).contains("Question 2 / 20"));
            while (!study.assessment(assessment.getId()).isFinished()) {
                answerCorrect(panel, study, assessment.getId());
            }
            assertEquals(old.getId(), study.active().orElseThrow().getId());
            snapshot(panel, "placement-result");
            click(panel, "assessment.adopt");
            StudySession fresh = study.active().orElseThrow();
            assertNotEquals(old.getId(), fresh.getId());
            assertEquals("SUPERSEDED", study.session(old.getId()).getStatus());
            assertEquals(1, study.session(old.getId()).getCurrentIndex());
            assertEquals(3, study.difficulty());
            assertEquals(0, fresh.getCurrentIndex());
            assertEquals(20, fresh.size());
            assertEquals(fresh.currentQuestion().getLemma(), edt(() -> label(panel, "study.word").getText()));
            assertEquals("Question 1 / 20  |  Difficulty L3", edt(() -> label(panel, "study.progress").getText()));
            assertEquals(0, study.mastered().size());
            assertEquals(0, study.history().size());
            click(panel, "study.listen");
            assertTrue(played.await(3, TimeUnit.SECONDS));
            assertEquals(fresh.currentQuestion().getLemma(), playedWord.get());
            edt(() -> null);
            snapshot(panel, "refreshed-round");
            clickText(panel, "Home");
            assertEquals("Select Listen to hear a dictionary recording.", edt(() -> label(panel, "speech.status").getText()));
        } finally {
            edt(() -> { panel.close(); return null; });
        }
    }

    private static DictionaryService dictionary() {
        return new DictionaryService(word -> new DictionaryClient.DictionaryEntry(word, "", "Test definition"));
    }

    private static PronunciationService silentSpeech(com.vocabulary.reference.application.PronunciationClient client,
                                                      CountDownLatch played) {
        return new PronunciationService(client, new PronunciationPlayer() {
            public void play(byte[] audio, BooleanSupplier cancelled) { if (!cancelled.getAsBoolean()) played.countDown(); }
            public void stop() { }
        }, new SpeechFallback() {
            public String speak(String word, String accent, BooleanSupplier cancelled) { fail("Unexpected fallback"); return ""; }
            public void stop() { }
        });
    }

    private static void answerCorrect(MainPanel panel, StudyService study, String id) throws Exception {
        StudyQuestion question = study.assessment(id).currentQuestion();
        click(panel, "assessment.option" + question.getOptions().indexOf(question.getCorrectMeaning()));
    }

    private static void click(MainPanel panel, String name) throws Exception {
        edt(() -> {
            Component found = find(panel, name, false);
            assertNotNull(found, name);
            AbstractButton button = (AbstractButton) found;
            assertTrue(button.isEnabled());
            button.doClick(0);
            layout(panel);
            return null;
        });
    }

    private static void clickText(MainPanel panel, String text) throws Exception {
        edt(() -> {
            Component found = find(panel, text, true);
            assertNotNull(found, text);
            ((AbstractButton) found).doClick(0);
            layout(panel);
            return null;
        });
    }

    private static JLabel label(MainPanel panel, String name) { return (JLabel) find(panel, name, false); }

    private static Component find(Component node, String value, boolean byText) {
        if (!node.isVisible()) return null;
        if (byText && node instanceof JButton && ((JButton) node).getText().contains(value)) return node;
        if (!byText && value.equals(node.getName())) return node;
        if (node instanceof Container) {
            for (Component child : ((Container) node).getComponents()) {
                Component result = find(child, value, byText);
                if (result != null) return result;
            }
        }
        return null;
    }

    private static void layout(Container root) {
        root.doLayout();
        for (Component child : root.getComponents()) if (child instanceof Container) layout((Container) child);
    }

    private static void snapshot(MainPanel panel, String name) throws Exception {
        Path path = Path.of("target", "ui-snapshots", name + ".png");
        Files.createDirectories(path.getParent());
        edt(() -> {
            layout(panel);
            BufferedImage image = new BufferedImage(panel.getWidth(), panel.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            panel.printAll(graphics);
            graphics.dispose();
            ImageIO.write(image, "png", path.toFile());
            return null;
        });
    }

    private static <T> T edt(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        SwingUtilities.invokeAndWait(task);
        return task.get();
    }
}
