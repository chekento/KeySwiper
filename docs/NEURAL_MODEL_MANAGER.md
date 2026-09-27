# Neural Model Manager

## Scope

KeySwiper can optionally use a local LiteRT-LM model for neural phrase refinement.

The neural model is **not bundled into the APK**. This keeps normal installs smaller and allows model versions to evolve independently from keyboard releases.

## Runtime

KeySwiper pins:

`com.google.ai.edge.litertlm:litertlm-android:0.17.1`

The app minimum is Android API 24.

## Import

Settings contains an **Import .litertlm model** action using Android's system document picker.

During import KeySwiper:

1. streams the chosen file into private app storage;
2. computes SHA-256 while copying;
3. enforces a 12 GB safety ceiling;
4. rejects implausibly small files;
5. atomically replaces the previous active model;
6. stores model size, hash and import timestamp.

The original external file is never modified.

## Verification

The stored SHA-256 can be recalculated from Settings. If bytes no longer match the recorded hash, the model is reported invalid.

## Inference lifecycle

The LiteRT-LM `Engine` is initialized lazily, only when:

- a model is installed;
- the current input field is non-sensitive;
- prediction is at a word/sentence boundary.

Initialization and inference run on a dedicated background executor.

The queue drops older waiting prediction jobs when a newer context arrives. The IME also attaches a monotonically increasing generation ID to visible prediction state so a late neural result cannot overwrite newer suggestions.

## Prediction contract

The model is prompted to return a small number of short continuations rather than open-ended chat. Neural results are marked with `✦` in the prediction strip and merged with fast local candidates.

KeySwiper remains usable if:

- no model is installed;
- model loading fails;
- the model is too slow;
- the device lacks an accelerator.

## Future model delivery

The next model-manager stage can add a curated remote catalog with:

- model license;
- languages;
- RAM/storage recommendations;
- expected latency;
- SHA-256 supplied by the catalog;
- resumable download;
- CPU/GPU/NPU capability hints.
