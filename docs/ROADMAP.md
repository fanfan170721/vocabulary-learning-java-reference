# Reference roadmap

## Established reference behavior

- Java 11 + Maven + Swing
- local CSV content with 60 sample words across L1–L3
- four-choice session snapshots that preserve the current round's content
- resume, wrong-only review, mastery, net-new goal progress and history
- SQLite profile records
- Free Dictionary API adapter with offline failure handling in the UI
- particle animation with skip and reduced-motion controls

## Verified update — November 17, 2026

- English definitions for all 60 bundled words, preserving their IDs, lemmas
  and sample levels; existing saved questions keep their original snapshots
- a 20-question reference placement with resumable progress and an L1–L3 result
- adopting the result immediately archives unfinished work without completion
  credit and opens a new 20-word round at the recommended difficulty
- preservation of completed history, mastery and existing goals when adopting
  a placement result
- dictionary-recorded pronunciation with US/UK preference, accurate labeling of
  alternative recordings and an explicitly identified Windows speech fallback
- internet access for online audio; local synthesis is not described as a
  dictionary recording
- original Commons recording recovery when dictionary proxy audio is broken;
  MP3 and Ogg Vorbis decoding, with no automatic playback

`mvn verify` passed 33 tests. The [verification record](VALIDATION.md) separates
automated checks, actual live-audio evidence and remaining limits.

This placement uses a small reference vocabulary and is not a standardized
language assessment. Each sample level has 20 words, so a new round at the same
level can repeat previously seen words. Fresh sessions do not imply fresh words.

## Remaining parity work

- broader onboarding and 50/100/200/500-question assessment modes
- versioned 15-level content and multi-sense distractor rules
- achievements, queue, import/export with validation and backup
- additional pronunciation providers and richer audio settings
- broader transactional-completion auditing and explicit content-version metadata
- richer presenters/controllers and PlantUML diagrams

Each slice needs meaningful behavior tests and a complete user story before it is
considered for the separate course repository. Test results must describe what
was actually run; this roadmap is not a completion or course-compliance claim.
