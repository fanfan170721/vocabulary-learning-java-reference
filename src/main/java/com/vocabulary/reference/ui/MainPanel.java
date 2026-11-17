package com.vocabulary.reference.ui;

import com.vocabulary.reference.application.DictionaryService;
import com.vocabulary.reference.application.StudyService;
import com.vocabulary.reference.domain.AnswerResult;
import com.vocabulary.reference.domain.HistoryRecord;
import com.vocabulary.reference.domain.MistakeStat;
import com.vocabulary.reference.domain.StudyQuestion;
import com.vocabulary.reference.domain.StudySession;
import com.vocabulary.reference.domain.Word;
import com.vocabulary.reference.application.PronunciationService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MainPanel extends JPanel {
    private static final String HOME = "home";
    private static final String STUDY = "study";
    private static final String RESULT = "result";
    private static final String MISTAKES = "mistakes";
    private static final String HISTORY = "history";
    private static final String DICTIONARY = "dictionary";
    private static final String SETTINGS = "settings";
    private static final String ASSESSMENT = "assessment";

    private final StudyService study;
    private final DictionaryService dictionary;
    private final PronunciationService pronunciation;
    private final Runnable closeWindow;
    private final AssessmentPanel assessmentPanel;
    private final JLabel speechStatus = new JLabel("Select Listen to hear a dictionary recording.");
    private String accent = "en-US";
    private long speechRequest;
    private boolean closed;
    private String visibleCard = HOME;
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
    private final JLabel homeStatus = new JLabel();
    private final JLabel questionLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel progressLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel resultLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JButton reviewButton = new RoundedButton("Review missed words", RoundedButton.Style.PRIMARY);
    private final JButton[] optionButtons = new JButton[4];
    private final CelebrationOverlay overlay = new CelebrationOverlay();
    private final JPanel mistakesPanel = new JPanel(new BorderLayout(8, 8));
    private final JPanel historyPanel = new JPanel(new BorderLayout(8, 8));
    private StudySession current;
    private String resultSessionId;

    public MainPanel(StudyService study, DictionaryService dictionary,
                     PronunciationService pronunciation, Runnable closeWindow) {
        super(new BorderLayout());
        this.study = study;
        this.dictionary = dictionary;
        this.pronunciation = pronunciation;
        this.closeWindow = closeWindow;
        Theme.applyGlobal();
        setBackground(Theme.WHITE);
        assessmentPanel = new AssessmentPanel(study, session -> {
            current = session;
            resultSessionId = null;
            show(STUDY);
            renderQuestion();
        }, () -> show(HOME));
        cards.add(buildHome(), HOME);
        cards.add(buildStudy(), STUDY);
        cards.add(buildResult(), RESULT);
        cards.add(buildMistakes(), MISTAKES);
        cards.add(buildHistory(), HISTORY);
        cards.add(buildDictionary(), DICTIONARY);
        cards.add(buildSettings(), SETTINGS);
        cards.add(assessmentPanel, ASSESSMENT);
        add(cards, BorderLayout.CENTER);
        speechStatus.setName("speech.status");
        speechStatus.setBorder(BorderFactory.createEmptyBorder(6, 16, 10, 16));
        add(speechStatus, BorderLayout.SOUTH);
    }

    public void close() {
        closed = true;
        overlay.stop();
        stopSpeech();
        pronunciation.close();
    }

    private JPanel buildHome() {
        JPanel root = base("Vocabulary Learning");
        JLabel intro = new JLabel("Learn, review and track your vocabulary in short rounds.", SwingConstants.CENTER);
        intro.setFont(Theme.SUBTITLE);
        intro.setForeground(Theme.MUTED);
        JPanel homeCenter = new JPanel(new BorderLayout(8, 8));
        homeCenter.setOpaque(false);
        homeCenter.add(intro, BorderLayout.NORTH);
        JPanel actions = new JPanel(new GridLayout(0, 2, 12, 12));
        actions.setOpaque(false);
        actions.setBorder(BorderFactory.createEmptyBorder(10, 26, 16, 26));
        actions.add(featureButton("Start / continue", "Study a 20-word round", AppIcon.Kind.LEARN, RoundedButton.Style.PRIMARY, e -> startStudy()));
        actions.add(featureButton("Missed words", "See words that need practice", AppIcon.Kind.MISTAKES, RoundedButton.Style.SECONDARY, e -> show(MISTAKES)));
        actions.add(featureButton("Learning history", "See completed rounds", AppIcon.Kind.HISTORY, RoundedButton.Style.SECONDARY, e -> show(HISTORY)));
        actions.add(featureButton("Dictionary", "Look up a word online", AppIcon.Kind.DICTIONARY, RoundedButton.Style.PRIMARY, e -> show(DICTIONARY)));
        actions.add(featureButton("Settings", "Audio and animation preferences", AppIcon.Kind.SETTINGS, RoundedButton.Style.SECONDARY, e -> show(SETTINGS)));
        actions.add(featureButton("Retake placement", "Apply difficulty to a fresh round now", AppIcon.Kind.LEARN,
                RoundedButton.Style.PRIMARY, e -> openAssessment()));
        actions.add(featureButton("Exit", "Close this reference app", AppIcon.Kind.BACK, RoundedButton.Style.NEUTRAL, e -> closeWindow.run()));
        homeCenter.add(actions, BorderLayout.CENTER);
        root.add(homeCenter, BorderLayout.CENTER);
        homeStatus.setOpaque(true);
        homeStatus.setForeground(Theme.INK);
        homeStatus.setBackground(Theme.BLUE);
        homeStatus.setBorder(Theme.cardBorder(Theme.BLUE_EDGE));
        homeStatus.setHorizontalAlignment(SwingConstants.CENTER);
        root.add(homeStatus, BorderLayout.SOUTH);
        refreshHome();
        return root;
    }

    private JPanel buildStudy() {
        JPanel root = base("Study round");
        JPanel center = new JPanel(new BorderLayout(12, 12));
        center.setOpaque(false);
        progressLabel.setFont(progressLabel.getFont().deriveFont(Font.BOLD, 16f));
        progressLabel.setOpaque(true);
        progressLabel.setBackground(Theme.BLUE);
        progressLabel.setForeground(Theme.INK);
        progressLabel.setBorder(Theme.cardBorder(Theme.BLUE_EDGE));
        center.add(progressLabel, BorderLayout.NORTH);
        questionLabel.setFont(questionLabel.getFont().deriveFont(Font.BOLD, 32f));
        questionLabel.setOpaque(true);
        questionLabel.setBackground(Theme.PINK);
        questionLabel.setForeground(Theme.INK);
        questionLabel.setBorder(Theme.cardBorder(Theme.PINK_EDGE));
        center.add(questionLabel, BorderLayout.CENTER);
        JPanel options = new JPanel(new GridLayout(2, 2, 12, 12));
        options.setOpaque(false);
        options.setBorder(BorderFactory.createEmptyBorder(12, 40, 12, 40));
        for (int i = 0; i < optionButtons.length; i++) {
            final int index = i;
            optionButtons[i] = new RoundedButton(" ", i % 2 == 0 ? RoundedButton.Style.PRIMARY : RoundedButton.Style.SECONDARY);
            optionButtons[i].setName("study.option" + i);
            optionButtons[i].addActionListener(e -> {
                if (!closed && STUDY.equals(visibleCard) && current != null && current.currentQuestion() != null) {
                    answer(current.currentQuestion().getOptions().get(index));
                }
            });
            options.add(optionButtons[i]);
        }
        JButton unknown = button("I don't know", e -> answer("I don't know"), RoundedButton.Style.PRIMARY);
        JPanel bottom = new JPanel(new BorderLayout(12, 12));
        bottom.setOpaque(false);
        unknown.setName("study.unknown");
        questionLabel.setName("study.word");
        progressLabel.setName("study.progress");
        JPanel studyActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        studyActions.setOpaque(false);
        studyActions.add(unknown);
        JButton listen = button("Listen", e -> {
            if (!closed && STUDY.equals(visibleCard) && current != null && current.currentQuestion() != null) {
                speak(current.currentQuestion().getLemma());
            }
        }, RoundedButton.Style.SECONDARY);
        listen.setName("study.listen");
        studyActions.add(listen);
        studyActions.add(button("Stop audio", e -> stopSpeech(), RoundedButton.Style.NEUTRAL));
        studyActions.add(button("Retake placement", e -> openAssessment(), RoundedButton.Style.SECONDARY));
        bottom.add(studyActions, BorderLayout.CENTER);
        bottom.add(button("Home", e -> show(HOME), RoundedButton.Style.NEUTRAL), BorderLayout.EAST);
        center.add(options, BorderLayout.SOUTH);
        root.add(center, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);
        return root;
    }

    private JLayeredPane buildResult() {
        JPanel resultContent = base("Round results");
        resultLabel.setFont(resultLabel.getFont().deriveFont(Font.BOLD, 22f));
        resultLabel.setOpaque(true);
        resultLabel.setBackground(Theme.BLUE);
        resultLabel.setForeground(Theme.INK);
        resultLabel.setBorder(Theme.cardBorder(Theme.BLUE_EDGE));
        resultContent.add(resultLabel, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 8));
        buttons.setOpaque(false);
        reviewButton.addActionListener(e -> startReview());
        buttons.add(reviewButton);
        buttons.add(button("Home", e -> show(HOME), RoundedButton.Style.NEUTRAL));
        buttons.add(button("Another round", e -> startStudy(), RoundedButton.Style.SECONDARY));
        buttons.add(button("Skip animation", e -> overlay.stop(), RoundedButton.Style.PRIMARY));
        resultContent.add(buttons, BorderLayout.SOUTH);
        JLayeredPane layered = new JLayeredPane();
        layered.setLayout(null);
        layered.add(resultContent, JLayeredPane.DEFAULT_LAYER);
        layered.add(overlay, JLayeredPane.PALETTE_LAYER);
        layered.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                resultContent.setBounds(0, 0, layered.getWidth(), layered.getHeight());
                overlay.setBounds(0, 0, layered.getWidth(), layered.getHeight());
            }
        });
        return layered;
    }

    private JPanel buildMistakes() {
        JPanel root = base("Missed words");
        mistakesPanel.setOpaque(false);
        root.add(mistakesPanel, BorderLayout.CENTER);
        root.add(button("Home", e -> show(HOME), RoundedButton.Style.NEUTRAL), BorderLayout.SOUTH);
        return root;
    }

    private JPanel buildHistory() {
        JPanel root = base("Learning history");
        historyPanel.setOpaque(false);
        root.add(historyPanel, BorderLayout.CENTER);
        root.add(button("Home", e -> show(HOME), RoundedButton.Style.NEUTRAL), BorderLayout.SOUTH);
        return root;
    }

    private JPanel buildDictionary() {
        JPanel root = base("Online dictionary");
        JPanel search = new JPanel(new BorderLayout(8, 8));
        search.setOpaque(false);
        JTextField input = new JTextField();
        input.setBorder(Theme.cardBorder(Theme.BLUE_EDGE));
        JTextArea output = new JTextArea();
        output.setEditable(false);
        output.setLineWrap(true);
        output.setWrapStyleWord(true);
        output.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        search.add(input, BorderLayout.CENTER);
        JButton lookup = new RoundedButton("Look up", RoundedButton.Style.PRIMARY);
        lookup.addActionListener(e -> {
            String requestedWord = input.getText();
            lookup.setEnabled(false);
            output.setText("Looking up…");
            new SwingWorker<String, Void>() {
                @Override protected String doInBackground() throws Exception { return dictionary.lookup(requestedWord).toString(); }
                @Override protected void done() {
                    lookup.setEnabled(true);
                    try { output.setText(get()); } catch (Exception ex) { output.setText("Lookup failed: " + (ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage())); }
                }
            }.execute();
        });
        JPanel dictionaryActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        dictionaryActions.setOpaque(false);
        dictionaryActions.add(lookup);
        dictionaryActions.add(button("Listen", e -> speak(input.getText()), RoundedButton.Style.SECONDARY));
        dictionaryActions.add(button("Stop audio", e -> stopSpeech(), RoundedButton.Style.NEUTRAL));
        search.add(dictionaryActions, BorderLayout.EAST);
        root.add(search, BorderLayout.NORTH);
        root.add(new JScrollPane(output), BorderLayout.CENTER);
        root.add(button("Home", e -> show(HOME), RoundedButton.Style.NEUTRAL), BorderLayout.SOUTH);
        return root;
    }

    private JPanel buildSettings() {
        JPanel root = base("Settings");
        JCheckBox reduced = new JCheckBox("Reduce animation", study.reducedMotion());
        reduced.setOpaque(true);
        reduced.setBackground(Theme.PINK);
        reduced.setForeground(Theme.INK);
        reduced.setBorder(Theme.cardBorder(Theme.PINK_EDGE));
        reduced.addActionListener(e -> study.setReducedMotion(reduced.isSelected()));
        JPanel settings = new JPanel(new GridLayout(0, 1, 8, 12));
        settings.setOpaque(false);
        settings.add(reduced);
        settings.add(new JLabel("Preferred recording accent"));
        JComboBox<String> accents = new JComboBox<>(new String[]{"American English", "British English"});
        accents.setName("settings.accent");
        accents.addActionListener(e -> {
            stopSpeech();
            accent = accents.getSelectedIndex() == 1 ? "en-GB" : "en-US";
        });
        settings.add(accents);
        settings.add(new JLabel("Dictionary recordings are preferred. Local speech is the fallback."));
        settings.add(new JLabel("The audio status identifies the actual accent and source. Recordings need internet."));
        root.add(settings, BorderLayout.CENTER);
        root.add(button("Home", e -> show(HOME), RoundedButton.Style.NEUTRAL), BorderLayout.SOUTH);
        return root;
    }

    private JPanel base(String title) {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBackground(Theme.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(22, 24, 20, 24));
        JPanel titlePanel = new JPanel(new BorderLayout(4, 4));
        titlePanel.setOpaque(false);
        JLabel heading = new JLabel(title, SwingConstants.CENTER);
        heading.setFont(Theme.TITLE);
        heading.setForeground(Theme.INK);
        titlePanel.add(heading, BorderLayout.CENTER);
        JLabel subheading = new JLabel("Java Swing reference · local-first learning", SwingConstants.CENTER);
        subheading.setFont(Theme.SUBTITLE);
        subheading.setForeground(Theme.MUTED);
        titlePanel.add(subheading, BorderLayout.SOUTH);
        panel.add(titlePanel, BorderLayout.NORTH);
        return panel;
    }

    private JButton button(String text, java.awt.event.ActionListener listener) {
        return button(text, listener, RoundedButton.Style.PRIMARY);
    }

    private JButton button(String text, java.awt.event.ActionListener listener, RoundedButton.Style style) {
        JButton button = new RoundedButton(text, style);
        button.addActionListener(listener);
        return button;
    }

    private JButton featureButton(String title, String subtitle, AppIcon.Kind icon,
                                  RoundedButton.Style style, java.awt.event.ActionListener listener) {
        String text = "<html><b>" + title + "</b><br><span color='#5E6570'>" + subtitle + "</span></html>";
        RoundedButton button = new RoundedButton(text, style);
        button.setIcon(new AppIcon(icon, 42, style == RoundedButton.Style.PRIMARY));
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setHorizontalTextPosition(SwingConstants.RIGHT);
        button.setIconTextGap(14);
        button.setPreferredSize(new Dimension(280, 84));
        button.addActionListener(listener);
        return button;
    }

    private void startStudy() {
        current = study.startOrResume();
        show(STUDY);
        renderQuestion();
    }

    private void startReview() {
        current = study.startReview(resultSessionId);
        show(STUDY);
        renderQuestion();
    }

    private void answer(String value) {
        if (closed || !STUDY.equals(visibleCard) || current == null || current.currentQuestion() == null) return;
        stopSpeech();
        AnswerResult result = study.answer(current.getId(), value);
        current = study.session(current.getId());
        if (!result.isRoundComplete()) {
            renderQuestion();
            return;
        }
        resultSessionId = current.getId();
        reviewButton.setVisible(result.needsReview());
        resultLabel.setText(result.needsReview()
                ? "Round finished. " + current.getWrongIds().size() + " words need review."
                : "Round completed! Goal progress: " + study.goal().getProgress() + " / " + study.goal().denominator());
        show(RESULT);
        boolean reduced = study.reducedMotion();
        SwingUtilities.invokeLater(() -> {
            if (!closed && RESULT.equals(visibleCard)) overlay.start(reduced);
        });
    }

    private void renderQuestion() {
        StudyQuestion question = current.currentQuestion();
        if (question == null) return;
        stopSpeech();
        questionLabel.setText(question.getLemma());
        progressLabel.setText("Question " + (current.getCurrentIndex() + 1) + " / " + current.size()
                + "  |  Difficulty L" + study.difficulty());
        for (int i = 0; i < optionButtons.length; i++) {
            optionButtons[i].setText("<html><div style='text-align:center;width:260px'>"
                    + escape(question.getOptions().get(i)) + "</div></html>");
        }
    }

    private void show(String card) {
        visibleCard = card;
        stopSpeech();
        if (!RESULT.equals(card)) overlay.stop();
        if (MISTAKES.equals(card)) refreshMistakes();
        if (HISTORY.equals(card)) refreshHistory();
        if (HOME.equals(card)) refreshHome();
        cardLayout.show(cards, card);
    }

    private void refreshHome() {
        homeStatus.setText("Difficulty L" + study.difficulty() + "  |  Mastered: " + study.mastered().size() + "  |  New-word goal: " + study.goal().getProgress()
                + " / " + study.goal().denominator() + " (" + study.goal().percent() + "%)");
    }

    private void refreshMistakes() {
        mistakesPanel.removeAll();
        Map<Integer, MistakeStat> stats = study.mistakes();
        String[] rows = stats.entrySet().stream().map(e -> {
            Word word = study.wordById(e.getKey());
            MistakeStat s = e.getValue();
            return word.getLemma() + " — " + word.getMeaning() + " (errors: " + s.getErrors() + "; unknown: " + s.getUnknowns() + "; retries: " + s.getRetries() + ")";
        }).toArray(String[]::new);
        mistakesPanel.add(listScrollPane(new JList<>(rows)), BorderLayout.CENTER);
        mistakesPanel.revalidate(); mistakesPanel.repaint();
    }

    private void refreshHistory() {
        historyPanel.removeAll();
        List<String> rows = new ArrayList<>();
        for (HistoryRecord r : study.history()) rows.add(r.getCompletedAt() + ": completed " + r.getWordIds().size() + " words; newly mastered " + r.getNetNew() + "; errors " + r.getErrorCount());
        historyPanel.add(listScrollPane(new JList<>(rows.toArray(new String[0]))), BorderLayout.CENTER);
        historyPanel.revalidate(); historyPanel.repaint();
    }

    private void openAssessment() {
        assessmentPanel.open();
        show(ASSESSMENT);
    }

    private void speak(String word) {
        final long request = ++speechRequest;
        pronunciation.speak(word, accent, message -> SwingUtilities.invokeLater(() -> {
            if (!closed && request == speechRequest) {
                speechStatus.setText(message);
                speechStatus.setToolTipText(message);
            }
        }));
    }

    private void stopSpeech() {
        speechRequest++;
        pronunciation.stop();
        speechStatus.setText("Select Listen to hear a dictionary recording.");
        speechStatus.setToolTipText(null);
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private JScrollPane listScrollPane(JList<String> list) {
        list.setFont(Theme.BODY);
        list.setForeground(Theme.INK);
        list.setBackground(Theme.WHITE);
        list.setSelectionBackground(Theme.PINK);
        list.setSelectionForeground(Theme.INK);
        list.setFixedCellHeight(44);
        list.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> owner, Object value, int index,
                                                                    boolean selected, boolean focused) {
                JLabel cell = (JLabel) super.getListCellRendererComponent(owner, value, index, selected, focused);
                cell.setFont(Theme.BODY);
                cell.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                        BorderFactory.createEmptyBorder(8, 10, 8, 10)));
                cell.setOpaque(true);
                cell.setBackground(selected ? Theme.PINK : Theme.WHITE);
                cell.setForeground(Theme.INK);
                return cell;
            }
        });
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(Theme.cardBorder(Theme.BORDER));
        scroll.getViewport().setBackground(Theme.WHITE);
        return scroll;
    }
}
