package com.vocabulary.reference.ui;

import com.vocabulary.reference.application.DictionaryService;
import com.vocabulary.reference.application.StudyService;
import com.vocabulary.reference.domain.AnswerResult;
import com.vocabulary.reference.domain.HistoryRecord;
import com.vocabulary.reference.domain.MistakeStat;
import com.vocabulary.reference.domain.StudyQuestion;
import com.vocabulary.reference.domain.StudySession;
import com.vocabulary.reference.domain.Word;
import com.vocabulary.reference.infrastructure.SQLiteStudyRepository;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MainFrame extends JFrame {
    private static final String HOME = "home";
    private static final String STUDY = "study";
    private static final String RESULT = "result";
    private static final String MISTAKES = "mistakes";
    private static final String HISTORY = "history";
    private static final String DICTIONARY = "dictionary";
    private static final String SETTINGS = "settings";

    private final StudyService study;
    private final DictionaryService dictionary;
    private final SQLiteStudyRepository repository;
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);
    private final JLabel homeStatus = new JLabel();
    private final JLabel questionLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel progressLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel resultLabel = new JLabel(" ", SwingConstants.CENTER);
    private final JButton reviewButton = new JButton("重测错词");
    private final JButton[] optionButtons = new JButton[4];
    private final CelebrationOverlay overlay = new CelebrationOverlay();
    private final JPanel mistakesPanel = new JPanel(new BorderLayout(8, 8));
    private final JPanel historyPanel = new JPanel(new BorderLayout(8, 8));
    private StudySession current;
    private String resultSessionId;

    public MainFrame(StudyService study, DictionaryService dictionary, SQLiteStudyRepository repository) {
        super("Vocabulary Learning — Java Reference");
        this.study = study;
        this.dictionary = dictionary;
        this.repository = repository;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(820, 560));
        setSize(920, 620);
        setLocationByPlatform(true);
        cards.add(buildHome(), HOME);
        cards.add(buildStudy(), STUDY);
        cards.add(buildResult(), RESULT);
        cards.add(buildMistakes(), MISTAKES);
        cards.add(buildHistory(), HISTORY);
        cards.add(buildDictionary(), DICTIONARY);
        cards.add(buildSettings(), SETTINGS);
        add(cards);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) { repository.close(); }
        });
    }

    private JPanel buildHome() {
        JPanel root = base("Vocabulary Learning");
        JPanel actions = new JPanel(new GridLayout(0, 2, 12, 12));
        actions.setBorder(BorderFactory.createEmptyBorder(16, 40, 16, 40));
        JButton learn = button("开始 / 继续学习", e -> startStudy());
        actions.add(learn);
        actions.add(button("错词本", e -> show(MISTAKES)));
        actions.add(button("学习历史", e -> show(HISTORY)));
        actions.add(button("词典 API", e -> show(DICTIONARY)));
        actions.add(button("设置", e -> show(SETTINGS)));
        actions.add(button("退出", e -> dispose()));
        root.add(actions, BorderLayout.CENTER);
        homeStatus.setBorder(BorderFactory.createEmptyBorder(0, 20, 15, 20));
        root.add(homeStatus, BorderLayout.SOUTH);
        refreshHome();
        return root;
    }

    private JPanel buildStudy() {
        JPanel root = base("学习一轮");
        JPanel center = new JPanel(new BorderLayout(12, 12));
        progressLabel.setFont(progressLabel.getFont().deriveFont(Font.BOLD, 16f));
        center.add(progressLabel, BorderLayout.NORTH);
        questionLabel.setFont(questionLabel.getFont().deriveFont(Font.BOLD, 32f));
        questionLabel.setBorder(BorderFactory.createEmptyBorder(20, 10, 20, 10));
        center.add(questionLabel, BorderLayout.CENTER);
        JPanel options = new JPanel(new GridLayout(2, 2, 12, 12));
        options.setBorder(BorderFactory.createEmptyBorder(12, 40, 12, 40));
        for (int i = 0; i < optionButtons.length; i++) {
            final int index = i;
            optionButtons[i] = button(" ", e -> answer(optionButtons[index].getText()));
            options.add(optionButtons[i]);
        }
        JButton unknown = button("我不会", e -> answer("I don't know"));
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(unknown, BorderLayout.WEST);
        bottom.add(button("返回首页", e -> show(HOME)), BorderLayout.EAST);
        center.add(options, BorderLayout.SOUTH);
        root.add(center, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);
        return root;
    }

    private JLayeredPane buildResult() {
        JPanel resultContent = base("本轮结果");
        resultLabel.setFont(resultLabel.getFont().deriveFont(Font.BOLD, 22f));
        resultContent.add(resultLabel, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 8));
        reviewButton.addActionListener(e -> startReview());
        buttons.add(reviewButton);
        buttons.add(button("返回首页", e -> show(HOME)));
        buttons.add(button("再来一轮", e -> startStudy()));
        buttons.add(button("跳过动画", e -> overlay.stop()));
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
        JPanel root = base("错词本");
        root.add(mistakesPanel, BorderLayout.CENTER);
        root.add(button("返回首页", e -> show(HOME)), BorderLayout.SOUTH);
        return root;
    }

    private JPanel buildHistory() {
        JPanel root = base("学习历史");
        root.add(historyPanel, BorderLayout.CENTER);
        root.add(button("返回首页", e -> show(HOME)), BorderLayout.SOUTH);
        return root;
    }

    private JPanel buildDictionary() {
        JPanel root = base("外部词典 API");
        JPanel search = new JPanel(new BorderLayout(8, 8));
        JTextField input = new JTextField();
        JTextArea output = new JTextArea();
        output.setEditable(false);
        output.setLineWrap(true);
        output.setWrapStyleWord(true);
        search.add(input, BorderLayout.CENTER);
        JButton lookup = new JButton("查询");
        lookup.addActionListener(e -> {
            lookup.setEnabled(false);
            output.setText("正在查询…");
            new SwingWorker<String, Void>() {
                @Override protected String doInBackground() throws Exception { return dictionary.lookup(input.getText()).toString(); }
                @Override protected void done() {
                    lookup.setEnabled(true);
                    try { output.setText(get()); } catch (Exception ex) { output.setText("查询失败：" + ex.getCause().getMessage()); }
                }
            }.execute();
        });
        search.add(lookup, BorderLayout.EAST);
        root.add(search, BorderLayout.NORTH);
        root.add(new JScrollPane(output), BorderLayout.CENTER);
        root.add(button("返回首页", e -> show(HOME)), BorderLayout.SOUTH);
        return root;
    }

    private JPanel buildSettings() {
        JPanel root = base("设置");
        JCheckBox reduced = new JCheckBox("减少动画", study.reducedMotion());
        reduced.addActionListener(e -> study.setReducedMotion(reduced.isSelected()));
        root.add(reduced, BorderLayout.NORTH);
        root.add(button("返回首页", e -> show(HOME)), BorderLayout.SOUTH);
        return root;
    }

    private JPanel base(String title) {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        JLabel heading = new JLabel(title, SwingConstants.CENTER);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 24f));
        panel.add(heading, BorderLayout.NORTH);
        return panel;
    }

    private JButton button(String text, java.awt.event.ActionListener listener) {
        JButton button = new JButton(text);
        button.setMargin(new Insets(10, 12, 10, 12));
        button.addActionListener(listener);
        return button;
    }

    private void startStudy() {
        current = study.startOrResume();
        renderQuestion();
        show(STUDY);
    }

    private void startReview() {
        current = study.startReview(resultSessionId);
        renderQuestion();
        show(STUDY);
    }

    private void answer(String value) {
        if (current == null || current.currentQuestion() == null) return;
        AnswerResult result = study.answer(current.getId(), value);
        current = study.session(current.getId());
        if (!result.isRoundComplete()) {
            renderQuestion();
            return;
        }
        resultSessionId = current.getId();
        reviewButton.setVisible(result.needsReview());
        resultLabel.setText(result.needsReview()
                ? "本轮完成，但有 " + current.getWrongIds().size() + " 个词需要重测。"
                : "本轮完成！目标进度：" + study.goal().getProgress() + " / " + study.goal().denominator());
        show(RESULT);
        SwingUtilities.invokeLater(() -> overlay.start(study.reducedMotion()));
    }

    private void renderQuestion() {
        StudyQuestion question = current.currentQuestion();
        if (question == null) return;
        questionLabel.setText("英文：" + question.getLemma());
        progressLabel.setText("第 " + (current.getCurrentIndex() + 1) + " / " + current.size() + " 题");
        for (int i = 0; i < optionButtons.length; i++) optionButtons[i].setText(question.getOptions().get(i));
    }

    private void show(String card) {
        if (!RESULT.equals(card)) overlay.stop();
        if (MISTAKES.equals(card)) refreshMistakes();
        if (HISTORY.equals(card)) refreshHistory();
        if (HOME.equals(card)) refreshHome();
        cardLayout.show(cards, card);
    }

    private void refreshHome() {
        homeStatus.setText("已掌握：" + study.mastered().size() + "  | 目标新增：" + study.goal().getProgress()
                + " / " + study.goal().denominator() + " (" + study.goal().percent() + "%)");
    }

    private void refreshMistakes() {
        mistakesPanel.removeAll();
        Map<Integer, MistakeStat> stats = study.mistakes();
        String[] rows = stats.entrySet().stream().map(e -> {
            Word word = study.wordById(e.getKey());
            MistakeStat s = e.getValue();
            return word.getLemma() + " — " + word.getMeaning() + "（错误 " + s.getErrors() + "，不会 " + s.getUnknowns() + "，重试 " + s.getRetries() + "）";
        }).toArray(String[]::new);
        mistakesPanel.add(new JScrollPane(new JList<>(rows)), BorderLayout.CENTER);
        mistakesPanel.revalidate(); mistakesPanel.repaint();
    }

    private void refreshHistory() {
        historyPanel.removeAll();
        List<String> rows = new ArrayList<>();
        for (HistoryRecord r : study.history()) rows.add(r.getCompletedAt() + "：完成 " + r.getWordIds().size() + " 词，净新增 " + r.getNetNew() + "，错误 " + r.getErrorCount());
        historyPanel.add(new JScrollPane(new JList<>(rows.toArray(new String[0]))), BorderLayout.CENTER);
        historyPanel.revalidate(); historyPanel.repaint();
    }
}
