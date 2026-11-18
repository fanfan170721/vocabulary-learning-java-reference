# Vocabulary Learning — Java Swing Reference

An independent Java 11 and Swing reference implementation based on the Python
behavior specification in `Vocabulary-learning-software`. This is separate from
the course team submission: it does not modify `team-project` or reuse the course
repository's commit history.

**Switching computers:** [Current progress and continuation instructions](docs/HANDOFF.md).

## Reference behavior

- Study rounds of 20 words with four choices and an **I don't know** answer.
- Resume the same round and stored question choices after closing the program.
- Review only incorrect and unknown words in the next review round.
- Finalize completed work with deduplicated mastery and net-new goal progress.
- Store sessions, answers, mastery, mistakes, history and settings in local SQLite.
- Swing screens for home, study, mistakes, history, dictionary lookup and settings.
- Look up definitions through Free Dictionary API; network failures do not stop
  offline study.
- Celebrate completed results with an animation that can be skipped or reduced.

The bundled content contains **60 sample words across L1–L3**, with 20 words in
each reference level. New question snapshots use English definitions. Existing
saved questions retain their original definitions and choices so that resuming
an old round does not change its answers midway.

## Reassessment and pronunciation update

Implemented and verified on November 17, 2026; see the [verification record](docs/VALIDATION.md).

- A **20-question placement assessment** recommends one of the sample content
  levels L1–L3. This is a reference-level check, not a standardized language test,
  CEFR certification or a measurement of a learner's full vocabulary size.
- Adopting the completed result immediately archives unfinished study or review
  work without awarding completion credit, then opens a fresh 20-word round at
  the recommended difficulty. Completed history, existing mastery and goals
  remain intact. With only 20 words per level, a fresh round at the same level
  may contain words seen previously; it is a new session, not a guarantee of
  20 previously unseen words.
- Pronunciation prefers dictionary-recorded audio. The selected US or UK accent
  is preferred when available; any alternative accent is labeled according to
  the available recording. Online clips require internet access. When no usable
  recording is available, a local Windows speech fallback is identified as
  synthesized speech rather than presented as a dictionary recording.
  Broken dictionary proxy links can be recovered from their attributed Wikimedia
  Commons source. Audio plays only after clicking **Listen**; it never starts
  automatically on launch, navigation or a new question. **Stop audio** stops playback.

The full 15-level vocabulary, additional assessment lengths, achievements,
validated import/export and remaining Python content rules are future work.
See [the roadmap](docs/ROADMAP.md) for the remaining scope.

## Visual design

The Swing interface uses a white background and dark text, with pale pink
`#FFF4FE` and pale blue `#E4F5FF` for buttons, cards, status indicators and icons.
Java2D draws the rounded controls and line icons without extra image assets.
The palette does not change the learning rules or persistence behavior.

## Run

```sh
mvn verify
mvn compile exec:java
```

Use JDK 11 or later and Maven. Data defaults to `.vocabulary-learning-reference`
under the current user's home directory. Set `VOCAB_DATA_DIR` to use a separate
demonstration directory. Do not commit personal databases or API credentials.

On Windows, double-click `run.cmd`, or run `./run.ps1`. Both compile the current
source before opening the app. To change the active round, choose **Retake
placement**, answer the 20 questions, then choose **Apply level and start fresh
20 words**. The study screen immediately opens question 1 of the new round.

Choose **Listen** in Study or Dictionary for pronunciation. Settings offers a
US/UK accent preference; the audio status reports the accent actually available.
Automated checks use silent audio doubles or decode a fixture without playing it.

## Layers

`domain` contains business objects and rules. `application` defines use cases and
ports. `infrastructure` implements SQLite storage, content loading and HTTP
services. `ui` handles Swing interaction. `DictionaryClient` isolates the external
dictionary API, and tests use a fake client. See [architecture notes](docs/ARCHITECTURE.md)
for the assessment, snapshot and pronunciation boundaries.

## Relationship to the course project

This reference demonstrates Java Swing, an external API, persistence, use-case
separation, unit tests and an animation model. The course team must still design
and implement its own user stories, interfaces and reviewed pull requests under
its TeamContract, README/MVP and individual-contribution requirements. Do not
copy this repository's history as evidence of team collaboration.
