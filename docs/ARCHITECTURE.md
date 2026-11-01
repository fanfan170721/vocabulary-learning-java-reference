# Architecture notes

The reference application keeps the business rules independent from Swing and SQLite.

```text
Swing MainFrame
    -> StudyService / DictionaryService
        -> StudyRepository / DictionaryClient (ports)
            -> SQLiteStudyRepository / FreeDictionaryClient (adapters)
```

`SessionSnapshot` stores the word IDs, four answer choices, current index and answers. This is why closing the program can resume the exact question instead of rebuilding a different quiz. A completed initial round with mistakes creates a review session whose `sourceSessionId` points back to the original round. Mastery and history are written only after the review chain is complete.

`ParticlePhysics` is a pure model. `CelebrationOverlay` only schedules a Swing timer and paints the model. This keeps gravity, collision, finite lifetime and reduced-motion behavior testable without opening a window.

The reference version intentionally exposes the seams that a three-person course project would divide into end-to-end User Stories. It is a feasibility reference, not a ready-to-submit course repository.
