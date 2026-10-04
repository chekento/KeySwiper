# Swipe Engine

## v0.3 — geometric adaptive swipe

KeySwiper no longer treats a swipe as only a sequence of keys. Each gesture is represented by a `SwipeTrace` with normalized X/Y coordinates and timestamps. The UI keeps at most 192 samples, evenly thinning long paths while retaining their beginning and end, so scoring remains lightweight enough for an IME.

## Candidate score

The v0.3 decoder combines:

1. compact touched-key signature;
2. edit distance and ordered key coverage;
3. tolerant start/end-key penalties;
4. Google ML Kit language hints from surrounding text;
5. local correction-frequency weights;
6. geometric similarity to an ideal QWERTY word path;
7. total path-length similarity;
8. local direction-change similarity;
9. timestamp-derived velocity around expected key positions;
10. a locally learned motor offset for each letter.

The geometric comparison resamples both the observed gesture and candidate word path and applies Dynamic Time Warping (DTW). This tolerates different swipe speeds and uneven sampling while still comparing the shape of the gesture.

## Personal motor profile

When the user explicitly taps an alternative candidate, KeySwiper treats that selection as confirmed feedback.

The corrected word is aligned against the swipe trace and produces a small X/Y offset estimate for its letters. Each letter has a bounded exponential moving average:

- early samples adapt quickly;
- established letters adapt more slowly;
- offsets are clamped to avoid runaway drift.

This means a user who consistently passes slightly left of **R**, above **E**, or cuts a specific corner can gradually receive better ranking without changing keyboard geometry on screen.

## Privacy

The motor model stores only derived per-letter offsets and sample counts. It does not store complete sentences. Sensitive/password fields and fields using Android's no-personalized-learning flag do not use or update adaptive models.

Both lexical learning and the motor profile can be reset from KeySwiper Settings.

## Next geometry steps

Planned extensions:

- pressure / touch-major signals when a device exposes reliable values;
- better monotonic letter-to-path alignment instead of equal-distance sampling;
- separate thumb profiles for one-hand mode;
- per-orientation and per-device geometry profiles;
- confidence-aware candidate UI;
- larger modular multilingual dictionaries.


## Finger and pen handling (0.18)

- both finger and pen word swipes must start on the letter surface;
- touch enters swipe mode after 12 dp displacement and commits after an 18 dp path across at least two keys;
- either tool may end a swipe on its starting key, supporting words such as “test”;
- pen taps commit the pen-down key; 16 dp deliberate displacement starts a swipe and a 24 dp path across two keys commits it, without a minimum duration;
- pen paths on keys never become handwriting, and menu/symbol controls remain normal buttons;
- extra palm/finger contacts do not replace an active pen pointer, and Android canceled contacts never commit input;
- as of 0.19, a swipe that starts on the letter surface retains ownership beyond its edges; off-surface samples are ignored and release outside finishes the last valid path;
- the separate handwriting pad is displayed above the letter keys.

## Continuous path ranking (0.18)

The observed path is resampled once per decode. A tolerant endpoint shortlist limits expensive DTW scoring. Geometry, endpoint distance, smoothed path length, direction and velocity dominate ranking; touched-letter edit distance, context, language and correction history break closer ties. Crossing intermediate keys no longer eliminates a candidate through a hard signature-length filter. Consecutive repeated letters share a compact key signature.

All five built-in packs include more everyday vocabulary. The decoder also considers frequent/pinned local user words, unless the editor is sensitive or disables personalized learning. On an empty editor the active keyboard layout supplies the primary language hint.

JVM tests cover continuous sampled paths with transit keys, repeated letters, closed paths, small offsets, personal vocabulary and context interference. Debug Android checks exercise actual pen/finger events on the rendered view.


## Gesture ownership and key alternatives (0.19)

An additional finger/hand contact does not reset a finger swipe. The initial active pointer owns the path; a pen can take over from an existing palm contact. Android cancellation flags still cancel the gesture. A gesture starting outside the keyboard is never adopted as a word swipe. Native handwriting remains a separate upper surface.

Holding a stationary character key opens alternatives; movement before the hold timeout chooses normal swipe intent instead. Once the popup opens, sliding selects a variant and releasing inserts it once. Leaving the popup selection area or canceling the touch inserts nothing. Surface detach/layout changes cancel timers and dismiss windows. Letters, both number/symbol pages, bottom-row punctuation and emoji families share this interaction.

The wider Shift key sits left of Y on QWERTZ; Backspace sits right of P. Tap toggles one-shot case and double-tap enables Caps Lock. A separate language key opens language selection; holding Space repeats spaces. Umlauts are first alternatives on a/o/u, and ß/ẞ is on s/S.

Real Android debug checks cover finger/pen edge re-entry, release beyond the boundary, extra hand contacts and attached popup insertion. Physical stylus/OEM palm-rejection behavior still needs device validation.

## Accelerated editing and punctuation

Backspace repeats after 420 ms. At 1.5 s it removes words, at 3.5 s groups of three words, at 6 s sentences, and at 9.5 s paragraphs. It operates on the selection or text before the cursor. Space repeats with group sizes 1/1/2/4/8 at the same stages. Both stop on release, cancellation, leaving the command key, hiding or detaching. Held deletion is one undo entry guarded by the surrounding cursor context.

Automatic separators keep a before/after snapshot. Punctuation moves that separator behind itself only when the snapshot and empty selection still match. Manual spaces are not altered.
