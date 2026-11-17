# Architecture notes

The reference application keeps business rules independent from Swing and SQLite.

```text
Swing MainFrame / MainPanel / AssessmentPanel
    -> StudyService / DictionaryService / PronunciationService
        -> StudyRepository / DictionaryClient / pronunciation ports
            -> SQLite / HTTP / recorded audio / local speech adapters
```

## Study snapshots and completion

`SessionSnapshot` stores the word IDs, definitions, four answer choices, current
index and answers. Closing the program therefore resumes the exact stored
question instead of rebuilding a different quiz. Changing the bundled CSV to
English does not rewrite an existing question snapshot. Newly generated rounds
use the current English content.

A completed initial round with mistakes creates a review session whose
`sourceSessionId` points back to the original round. Mastery and completion
history are written only after the review chain is complete.

## Placement and adoption

The verified `PlacementAssessment`
represents the 20-question reference placement in the domain layer.
`StudyService.startOrResumeAssessment()` and `answerAssessment()` coordinate it;
the Swing `AssessmentPanel` displays questions and the result.

`StudyService.adoptAssessment()` returns a fresh `StudySession` immediately after
the learner adopts the completed result. It archives unfinished study or review
work without completion credit and creates 20 questions at the recommended
sample level. This boundary preserves completed history, mastery and existing
goals. Starting or answering assessment questions does not itself complete a
study round or award mastery.

The content currently has only 20 words per level across L1–L3. A fresh session
at a previously used level can therefore repeat those words. The reference
placement must not be described as a standardized proficiency score or an
estimate of full vocabulary size.

## Pronunciation

The pronunciation path has automated and live playback evidence in [VALIDATION.md](VALIDATION.md).
`PronunciationService` coordinates `DictionaryPronunciationClient`,
`JLayerAudioPlayer` and `WindowsSpeechFallback`. Its `speak(word, accent, callback)`
entry point supports the `en-US` and `en-GB` preferences; `stop()` and `close()`
control playback lifetime.

Dictionary audio is preferred to synthesized speech. The selected accent is a
preference, not a promise that every dictionary entry has a recording in that
accent. Available alternatives must be labeled accurately. Downloading online
clips requires internet access. The Windows SAPI fallback is explicitly labeled
as local synthesized speech when used.

JLayer (`javazoom:jlayer:1.0.1`) decodes MP3 and VorbisSPI
(`com.googlecode.soundlibs:vorbisspi:1.0.3.3`) decodes Ogg Vorbis. If a dictionary
audio proxy fails, the client can resolve its attributed Commons page through
the official media API and use the original recording, retaining creator and
license attribution. Audio is triggered only by a Listen button; navigation,
answers, Stop audio and closing the window cancel the current request. The dictionary
client, audio player and local speech adapter remain outside the domain rules.
Unavailable audio or a failed network request must not modify a study answer,
mastery, goal progress or completed history.

## Animation

`ParticlePhysics` is a pure model. `CelebrationOverlay` schedules a Swing timer
and paints that model. Gravity, collision, finite lifetime and reduced-motion
behavior can be tested without opening a window.

## Course scope

The reference exposes boundaries that a three-person course project could divide
into complete user stories. It is a feasibility reference, not a ready-to-submit
course repository or evidence of team contributions.
