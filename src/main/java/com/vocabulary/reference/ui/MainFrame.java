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
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JFrame;
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
    private final JButton reviewButton = new RoundedButton("重测错词", RoundedButton.Style.PRIMARY);
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
        Theme.applyGlobal();
        getContentPane().setBackground(Theme.WHITE);
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
        JLabel intro = new JLabel("用短轮次学习、复习并记录你的词汇进度", SwingConstants.CENTER);
        intro.setFont(Theme.SUBTITLE);
        intro.setForeground(Theme.MUTED);
        JPanel homeCenter = new JPanel(new BorderLayout(8, 8));
        homeCenter.setOpaque(false);
        homeCenter.add(intro, BorderLayout.NORTH);
        JPanel actions = new JPanel(new GridLayout(0, 2, 12, 12));
        actions.setOpaque(false);
        actions.setBorder(BorderFactory.createEmptyBorder(10, 26, 16, 26));
        actions.add(featureButton("开始 / 继续学习", "进入 20 词学习轮次", AppIcon.Kind.LEARN, RoundedButton.Style.PRIMARY, e -> startStudy()));
        actions.add(featureButton("错词本", "只复习需要巩固的词", AppIcon.Kind.MISTAKES, RoundedButton.Style.SECONDARY, e -> show(MISTAKES)));
        actions.add(featureButton("学习历史", "查看已经完成的轮次", AppIcon.Kind.HISTORY, RoundedButton.Style.SECONDARY, e -> show(HISTORY)));
        actions.add(featureButton("词典 API", "查询外部词典释义", AppIcon.Kind.DICTIONARY, RoundedButton.Style.PRIMARY, e -> show(DICTIONARY)));
        actions.add(featureButton("设置", "减少动画等偏好", AppIcon.Kind.SETTINGS, RoundedButton.Style.SECONDARY, e -> show(SETTINGS)));
        actions.add(featureButton("退出", "关闭参考程序", AppIcon.Kind.BACK, RoundedButton.Style.NEUTRAL, e -> dispose()));
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
        JPanel root = base("学习一轮");
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
            optionButtons[i].addActionListener(e -> answer(optionButtons[index].getText()));
            options.add(optionButtons[i]);
        }
        JButton unknown = button("我不会", e -> answer("I don't know"), RoundedButton.Style.PRIMARY);
        JPanel bottom = new JPanel(new BorderLayout(12, 12));
        bottom.setOpaque(false);
        bottom.add(unknown, BorderLayout.WEST);
        bottom.add(button("返回首页", e -> show(HOME), RoundedButton.Style.NEUTRAL), BorderLayout.EAST);
        center.add(options, BorderLayout.SOUTH);
        root.add(center, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);
        return root;
    }

    private JLayeredPane buildResult() {
        JPanel resultContent = base("本轮结果");
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
        buttons.add(button("返回首页", e -> show(HOME), RoundedButton.Style.NEUTRAL));
        buttons.add(button("再来一轮", e -> startStudy(), RoundedButton.Style.SECONDARY));
        buttons.add(button("跳过动画", e -> overlay.stop(), RoundedButton.Style.PRIMARY));
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
        mistakesPanel.setOpaque(false);
        root.add(mistakesPanel, BorderLayout.CENTER);
        root.add(button("返回首页", e -> show(HOME), RoundedButton.Style.NEUTRAL), BorderLayout.SOUTH);
        return root;
    }

    private JPanel buildHistory() {
        JPanel root = base("学习历史");
        historyPanel.setOpaque(false);
        root.add(historyPanel, BorderLayout.CENTER);
        root.add(button("返回首页", e -> show(HOME), RoundedButton.Style.NEUTRAL), BorderLayout.SOUTH);
        return root;
    }

    private JPanel buildDictionary() {
        JPanel root = base("外部词典 API");
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
        JButton lookup = new RoundedButton("查询", RoundedButton.Style.PRIMARY);
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
        root.add(button("返回首页", e -> show(HOME), RoundedButton.Style.NEUTRAL), BorderLayout.SOUTH);
        return root;
    }

    private JPanel buildSettings() {
        JPanel root = base("设置");
        JCheckBox reduced = new JCheckBox("减少动画", study.reducedMotion());
        reduced.setOpaque(true);
        reduced.setBackground(Theme.PINK);
        reduced.setForeground(Theme.INK);
        reduced.setBorder(Theme.cardBorder(Theme.PINK_EDGE));
        reduced.addActionListener(e -> study.setReducedMotion(reduced.isSelected()));
        root.add(reduced, BorderLayout.NORTH);
        root.add(button("返回首页", e -> show(HOME), RoundedButton.Style.NEUTRAL), BorderLayout.SOUTH);
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
        mistakesPanel.add(listScrollPane(new JList<>(rows)), BorderLayout.CENTER);
        mistakesPanel.revalidate(); mistakesPanel.repaint();
    }

    private void refreshHistory() {
        historyPanel.removeAll();
        List<String> rows = new ArrayList<>();
        for (HistoryRecord r : study.history()) rows.add(r.getCompletedAt() + "：完成 " + r.getWordIds().size() + " 词，净新增 " + r.getNetNew() + "，错误 " + r.getErrorCount());
        historyPanel.add(listScrollPane(new JList<>(rows.toArray(new String[0]))), BorderLayout.CENTER);
        historyPanel.revalidate(); historyPanel.repaint();
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
