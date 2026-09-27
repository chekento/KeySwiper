# Swipe Engine

## v0.2 — adaptive multilingual ranking

KeySwiper's second swipe iteration keeps the decoder local and adds four ranking signals:

1. the compact key path captured by the keyboard surface;
2. edit distance, ordered key coverage and tolerant endpoint penalties;
3. likely language hints from Google ML Kit Language Identification;
4. local correction weights learned when the user explicitly selects an alternative candidate.

The language detector can identify far more languages than the starter swipe lexicon currently predicts. Prediction dictionaries are therefore modular: the current embedded starter pack covers German, English, Italian, French and Spanish, while language identification and translation remain broader Google-powered layers.

## Local learning

A correction is stored as a small derived counter keyed by:

- compact swipe signature;
- previous word;
- chosen candidate.

No typed sentence is stored by the swipe-learning component. Password fields and fields marked with Android's no-personalized-learning flag never contribute learning data.

The Settings screen includes a reset button that clears all locally learned swipe weights.

## Undo behavior

Immediately after a swipe, Backspace removes the entire committed swipe word and its trailing space. Once normal typing continues, Backspace returns to single-character deletion.
