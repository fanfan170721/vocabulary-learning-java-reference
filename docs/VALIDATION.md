# Verification — November 17, 2026

Environment: Windows, Java 11 and Maven 3.9.16.

`mvn -B --no-transfer-progress verify dependency:build-classpath` completed with
**33 tests, zero failures, zero errors and zero skipped tests**. The application
JAR was built; the launchers use Maven to include runtime dependencies.

## Reassessment

- An unfinished round remains unchanged while placement questions are answered.
- Applying the result immediately archives unfinished work and creates a new
  20-word round at the recommended level, including a same-level retake.
- Previous answers remain stored. Replaced sessions cannot accept stale answers
  or award completion credit. Duplicate adoption does not reset the new round.
- Completed history, mastery and goals survive adoption; normal later rounds
  still prioritize unmastered vocabulary.
- SQLite restart preserves the exact assessment question and adopted study
  round. An injected transaction failure rolls back all adoption writes.
- A real Swing button workflow resumes placement at question 2, completes it,
  applies level L3 and immediately displays question 1/20 of the replacement.
  Offscreen component renders were inspected in `target/ui-snapshots/`.

## Pronunciation

Automated tests use local HTTP fixtures, fake playback and an attributed Ogg
decoder fixture. They do not send words to public services or play sound through
the user's speakers. Tests cover cancellation, stale responses, accurate accent
labels, fallback, cache limits, download limits, timeouts and source recovery.

A separate live check of `hello` requested US English. The dictionary had no US
recording; its UK/Australian proxy links returned HTTP 404. Source recovery used
the UK recording attributed to Association Shtooka and Judith Franck on
[Wikimedia Commons](https://commons.wikimedia.org/wiki/File:En-uk-hello.ogg).
The original Ogg file downloaded successfully (14,145 bytes), decoded to PCM and
completed playback through the machine's audio output. Its license is
[CC BY 3.0 US](https://creativecommons.org/licenses/by/3.0/us/).
This check establishes one actual recording path, not universal provider uptime.

Live playback is not part of the automatic test suite and must not be initiated
without the user's explicit request. In the app, only pressing **Listen** starts
audio. Startup, new questions, placement results and navigation do not play sound.

Source/codec documentation: [Dictionary API](https://dictionaryapi.dev/),
[Commons media API](https://www.mediawiki.org/wiki/Extension:TimedMediaHandler/API),
[JLayer](https://central.sonatype.com/artifact/javazoom/jlayer/1.0.1) and
[VorbisSPI](https://central.sonatype.com/artifact/com.googlecode.soundlibs/vorbisspi/1.0.3.3).

## Limits

- The placement uses 60 example words across L1–L3 and is not a standardized
  language assessment. A same-level replacement can contain the same words.
- An internet connection and an available recording are needed for natural
  dictionary audio. Otherwise the application clearly identifies local synthesis
  or reports that audio is unavailable. Local voice availability varies by machine.
- Existing saved question snapshots keep their original text and choices; only
  new rounds use the translated English definitions.
- Native window behavior on other machines has not been manually verified.
  The separate course team repository was not changed by this update.
