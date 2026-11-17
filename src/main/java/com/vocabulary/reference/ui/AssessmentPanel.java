package com.vocabulary.reference.ui;

import com.vocabulary.reference.application.StudyService;
import com.vocabulary.reference.domain.PlacementAssessment;
import com.vocabulary.reference.domain.StudyQuestion;
import com.vocabulary.reference.domain.StudySession;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/** Resumable placement and explicit adoption; ordinary practice is never scored here. */
final class AssessmentPanel extends JPanel {
    private final StudyService study;
    private final Consumer<StudySession> onAdopted;
    private final JLabel progress = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel word = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel explanation = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel error = new JLabel(" ");
    private final JPanel choices = new JPanel(new GridLayout(2, 2, 12, 12));
    private final JButton[] options = new JButton[4];
    private final JButton unknown = new RoundedButton("I don't know", RoundedButton.Style.SECONDARY);
    private final JButton adopt = new RoundedButton("Apply level and start fresh 20 words", RoundedButton.Style.PRIMARY);
    private PlacementAssessment assessment;

    AssessmentPanel(StudyService study, Consumer<StudySession> onAdopted, Runnable onHome) {
        super(new BorderLayout(12, 16));
        this.study = study;
        this.onAdopted = onAdopted;
        setBackground(Theme.WHITE);
        setBorder(BorderFactory.createEmptyBorder(24, 24, 18, 24));
        progress.setName("assessment.progress");
        word.setName("assessment.word");
        explanation.setName("assessment.explanation");
        error.setName("assessment.error");
        word.setFont(Theme.TITLE);
        progress.setFont(Theme.SUBTITLE);
        JPanel heading = new JPanel(new GridLayout(0, 1, 0, 10));
        heading.setOpaque(false);
        heading.add(new JLabel("Vocabulary placement", SwingConstants.CENTER));
        heading.add(progress);
        heading.add(explanation);
        add(heading, BorderLayout.NORTH);
        JPanel center = new JPanel(new BorderLayout(12, 18));
        center.setOpaque(false);
        word.setOpaque(true);
        word.setBackground(Theme.PINK);
        word.setBorder(Theme.cardBorder(Theme.PINK_EDGE));
        center.add(word, BorderLayout.CENTER);
        choices.setOpaque(false);
        for (int i = 0; i < options.length; i++) {
            final int index = i;
            options[i] = new RoundedButton(" ", i % 2 == 0
                    ? RoundedButton.Style.PRIMARY : RoundedButton.Style.SECONDARY);
            options[i].setName("assessment.option" + i);
            options[i].addActionListener(event -> {
                if (assessment != null && !assessment.isFinished()) {
                    answer(assessment.currentQuestion().getOptions().get(index));
                }
            });
            choices.add(options[i]);
        }
        center.add(choices, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(6, 8));
        bottom.setOpaque(false);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        actions.setOpaque(false);
        unknown.setName("assessment.unknown");
        unknown.addActionListener(event -> answer("I don't know"));
        adopt.setName("assessment.adopt");
        adopt.addActionListener(event -> adopt());
        JButton home = new RoundedButton("Save and return home", RoundedButton.Style.NEUTRAL);
        home.setName("assessment.home");
        home.addActionListener(event -> onHome.run());
        actions.add(unknown);
        actions.add(adopt);
        actions.add(home);
        bottom.add(error, BorderLayout.NORTH);
        bottom.add(actions, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);
    }

    void open() {
        try {
            assessment = study.startOrResumeAssessment();
            error.setText(" ");
            render();
        } catch (RuntimeException failure) {
            showFailure(failure);
        }
    }

    private void answer(String value) {
        if (assessment == null || assessment.isFinished()) {
            return;
        }
        try {
            assessment = study.answerAssessment(assessment.getId(), value);
            error.setText(" ");
            render();
        } catch (RuntimeException failure) {
            showFailure(failure);
        }
    }

    private void adopt() {
        if (assessment == null || !assessment.isFinished()) {
            return;
        }
        try {
            StudySession refreshed = study.adoptAssessment(assessment.getId());
            adopt.setEnabled(false);
            onAdopted.accept(refreshed);
        } catch (RuntimeException failure) {
            showFailure(failure);
        }
    }

    private void render() {
        boolean finished = assessment.isFinished();
        choices.setVisible(!finished);
        unknown.setVisible(!finished);
        adopt.setVisible(finished);
        adopt.setEnabled(finished);
        if (finished) {
            progress.setText("Placement complete: " + assessment.getCorrectCount() + " / " + assessment.size());
            word.setText("Recommended level: L" + assessment.getRecommendedLevel());
            explanation.setText("<html><div style='text-align:center'>A guide for this sample vocabulary, not a standardized score.<br>"
                    + "Apply it to replace the unfinished round immediately. Completed progress is kept.</div></html>");
        } else {
            StudyQuestion question = assessment.currentQuestion();
            progress.setText("Question " + (assessment.getCurrentIndex() + 1) + " / " + assessment.size());
            explanation.setText("Answers save automatically. Your current learning round changes only when you apply the result.");
            word.setText(question.getLemma());
            for (int i = 0; i < options.length; i++) {
                String text = question.getOptions().get(i).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
                options[i].setText("<html><div style='text-align:center;width:280px'>" + text + "</div></html>");
            }
        }
        revalidate();
        repaint();
    }

    private void showFailure(RuntimeException failure) {
        error.setText("Could not save placement: " + failure.getMessage());
    }
}
