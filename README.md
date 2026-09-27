# KeySwiper

**Adaptive multimodal Android keyboard — swipe, stylus, voice, clipboard, emoji and multilingual input.**

> 🚧 **Early development / experimental build.** KeySwiper is not yet intended for production use or for entering secrets such as passwords, recovery phrases or payment data.

## Vision

KeySwiper is an Android Input Method Editor (IME) designed around **input fusion**:

- finger typing and swipe paths
- stylus / S Pen input
- local handwriting recognition
- Voice2Text
- clipboard actions
- emoji input
- language detection and translation
- adaptive, privacy-first personalization

The goal is not to clone an existing keyboard. KeySwiper is designed so the input system adapts to the user's motor patterns, languages and workflows.

## Current alpha foundation

The first implementation focuses on a buildable architecture with:

- Android `InputMethodService`
- custom QWERTY keyboard view with tap + swipe path capture
- adaptive Swipe v3 ranking with geometric path scoring and local correction learning
- intelligent word completion, next-word and sentence prediction strip
- hybrid instant + semantic beam-search prediction up to 2–6 words ahead
- surrounding-context intelligence using text before/after the cursor, selection, sentence, paragraph and topics
- optional local LiteRT-LM neural refinement with importable `.litertlm` models
- Google ML Kit auto-language hints plus per-user motor offsets for swipe ranking
- personal local 2/3/4-gram prediction learning
- local General / Message / Email / Search / Code context modes
- three simultaneous language lanes for real code-switching
- larger DE/EN/IT/FR/ES core language packs plus local personal vocabulary
- local neural model manager with SHA-256 verification
- suggestion/candidate strip
- clipboard panel
- emoji panel
- Android SpeechRecognizer integration
- Google ML Kit language identification
- Google ML Kit on-device translation
- Google ML Kit Digital Ink Recognition foundation
- stylus-aware touch handling
- configurable S Pen / stylus primary-single, primary-double and secondary-button actions
- local preferences and privacy controls
- CI debug-APK build
- permanent per-build GitHub Release + version-page archive

## Google language stack

KeySwiper deliberately separates three capabilities:

1. **Language identification** — local ML Kit detection for 100+ languages.
2. **Translation** — local ML Kit translation for 50+ languages.
3. **Digital Ink** — local handwriting recognition with models for 300+ languages / variants.

A cloud translation gateway can be added for broader Google Cloud Translation coverage without embedding service-account credentials into the APK.

## Privacy model

Keyboard software handles unusually sensitive data. KeySwiper therefore follows these defaults:

- no keystroke logging
- no network transmission for normal typing
- no learning in password fields
- local-first language and handwriting models
- clipboard access only while the IME is active
- cloud features must be explicit and separately configured

## Build

Requirements:

- JDK 21
- Android SDK 36
- Gradle 9.6+
- Android Gradle Plugin 9.4

From the repository root:

```bash
gradle :app:assembleDebug
```

The GitHub Actions workflow runs unit tests, builds the debug APK, keeps a CI artifact, publishes a permanent GitHub Release asset and generates a dedicated version page with SHA-256 and changelog. See [APK version archive](docs/versions/README.md).

See [Swipe Engine](docs/SWIPE_ENGINE.md), [Prediction Engine](docs/PREDICTION_ENGINE.md), [Code Switching](docs/CODE_SWITCHING.md), [Context Intelligence](docs/CONTEXT_INTELLIGENCE.md), [Neural Model Manager](docs/NEURAL_MODEL_MANAGER.md) and [Neural Prediction Roadmap](docs/NEURAL_PREDICTION.md).

## Roadmap

Next milestones:

- optional Samsung Air Action adapter where foreground/device routing permits it
- direct Android stylus-handwriting IME session support
- editable gesture mappings
- multilingual target-language wheel
- smart clipboard categorization/search
- voice editing commands
- optional Google Cloud Translation gateway
- one-hand geometry adaptation
- autocorrect timeline / undo-redo
- developer layout and programmable profiles

## License

Apache-2.0
