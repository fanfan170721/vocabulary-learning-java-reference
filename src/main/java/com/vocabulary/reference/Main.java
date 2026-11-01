package com.vocabulary.reference;

import com.vocabulary.reference.application.DictionaryClient;
import com.vocabulary.reference.application.DictionaryService;
import com.vocabulary.reference.application.StudyService;
import com.vocabulary.reference.infrastructure.ContentRepository;
import com.vocabulary.reference.infrastructure.FreeDictionaryClient;
import com.vocabulary.reference.infrastructure.SQLiteStudyRepository;
import com.vocabulary.reference.ui.MainFrame;

import javax.swing.SwingUtilities;

public final class Main {
    private Main() { }
    public static void main(String[] args) throws Exception {
        ContentRepository content = new ContentRepository();
        SQLiteStudyRepository repository = SQLiteStudyRepository.openDefault();
        StudyService study = new StudyService(content.load(), repository);
        DictionaryClient dictionaryClient = new FreeDictionaryClient();
        DictionaryService dictionary = new DictionaryService(dictionaryClient);
        SwingUtilities.invokeLater(() -> new MainFrame(study, dictionary, repository).setVisible(true));
    }
}
