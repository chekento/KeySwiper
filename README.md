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

## v0.1 foundation

The first implementation focuses on a buildable architecture with:

- Android `InputMethodService`
- custom QWERTY keyboard view with tap + swipe path capture
- suggestion/candidate strip
- clipboard panel
- emoji panel
- Android SpeechRecognizer integration
- Google ML Kit language identification
- Google ML Kit on-device translation
- Google ML Kit Digital Ink Recognition foundation
- stylus-aware touch handling
- S Pen / stylus button mapping abstraction
- local preferences and privacy controls
- CI debug-APK build

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

- JDK 17
- Android SDK 37
- Gradle 9.6+
- Android Gradle Plugin 9.4

From the repository root:

```bash
gradle :app:assembleDebug
```

The GitHub Actions workflow also builds and uploads the debug APK as a workflow artifact.

## Roadmap

Next milestones:

- adaptive swipe decoder with per-user motor model
- multilingual dictionaries and next-word prediction
- richer S Pen button / Air Action support on compatible Samsung devices
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
