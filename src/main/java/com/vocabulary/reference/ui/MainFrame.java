package com.vocabulary.reference.ui;

import com.vocabulary.reference.application.DictionaryService;
import com.vocabulary.reference.application.PronunciationService;
import com.vocabulary.reference.application.StudyService;
import com.vocabulary.reference.infrastructure.SQLiteStudyRepository;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;

/** Native window lifecycle; the content panel also runs in headless UI tests. */
public final class MainFrame extends JFrame {
    public MainFrame(StudyService study, DictionaryService dictionary,
                     SQLiteStudyRepository repository, PronunciationService pronunciation) {
        super("Vocabulary Learning — Java Reference");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(980, 700));
        setSize(1100, 780);
        setLocationByPlatform(true);
        MainPanel panel = new MainPanel(study, dictionary, pronunciation, this::dispose);
        setContentPane(panel);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent event) {
                panel.close();
                repository.close();
            }
        });
    }
}
