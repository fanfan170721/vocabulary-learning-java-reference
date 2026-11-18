# Continue the Java reference on another computer

Snapshot: November 17, 2026, America/Toronto.

Latest work is on **`Emilia/reference-reassessment-audio`**, implementation commit
`946e04f`. The default `main` branch still points to the older reference. This
repository is independent of the CSC207 team project.

With Git, JDK 11 or 17 and Maven 3.9+ installed:

```sh
git clone --branch Emilia/reference-reassessment-audio https://github.com/fanfan170721/vocabulary-learning-java-reference.git vocabulary-java-reference
cd vocabulary-java-reference
mvn verify
mvn compile exec:java
```

On Windows, `run.cmd` or `run.ps1` compiles the latest source and opens the app.
For IntelliJ, open `pom.xml` as a Maven project. Authenticate with Allen's own
GitHub account if access to this repository requires it.

## Completed update

- A resumable 20-question placement assessment for the 60 sample words at L1–L3.
- Applying a result immediately archives unfinished work and creates a fresh
  20-word round at the recommended level. Completed history, mastery and goals
  remain intact. A same-level refresh can contain the same vocabulary.
- Natural recorded pronunciation from dictionary sources, with recovery from
  the attributed Commons source when the dictionary audio proxy is broken.
- MP3 and Ogg playback, accurate actual-accent/creator/license labels and an
  explicitly identified local Windows speech fallback.
- English UI, source documentation and the 60 example definitions. Existing
  saved question snapshots retain their original text and answers.
- **33 tests passed**. Core behavior, SQLite rollback/restart, real Swing controls,
  audio cancellation/fallback and silent decoding are covered. See [VALIDATION.md](VALIDATION.md).

## Important continuation rules

**Never run audible playback tests without an explicit request.**
Automated tests must remain silent. App startup, new
questions and navigation do not play audio; only the user clicking **Listen** does.

This is a reference, not the full Python application's 15-level feature set.
See [ROADMAP.md](ROADMAP.md) before claiming parity. Do not copy its authorship
history into the course repository as evidence of team collaboration.

Personal data defaults to `~/.vocabulary-learning-reference/profile.sqlite`, or
the folder selected by `VOCAB_DATA_DIR`. Git does not upload this profile. If it
needs migration, close the app and transfer it privately. Maven rebuilds ignored
dependencies and `target/` outputs on the new machine.

For team work, use the separate
[team continuation note](https://github.com/fanfan170721/team-project/blob/Emilia/allen-personal-vocabulary/docs/HANDOFF.md).
