# Swipe Engine

## v0.3 — geometric adaptive swipe

KeySwiper no longer treats a swipe as only a sequence of keys. Each gesture is represented by a `SwipeTrace` with normalized X/Y coordinates and timestamps. The UI keeps at most 96 samples so scoring remains lightweight enough for an IME.

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
