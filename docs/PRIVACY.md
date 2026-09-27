# Privacy and security model

An IME can observe highly sensitive text. KeySwiper therefore defaults to minimum collection.

## Current defaults

- no telemetry
- no analytics SDK
- no keystroke logging
- no persistent clipboard database
- no normal typing sent to a server
- local ML Kit models for language identification, translation and handwriting
- sensitive/password fields disable voice, clipboard history, translation and handwriting helpers
- Android's `IME_FLAG_NO_PERSONALIZED_LEARNING` is respected

## Cloud translation

Broader Google Cloud Translation support should be implemented through an explicit,
separately configured gateway. Service-account credentials must never be shipped inside the APK.

## Future personalization

Adaptive swipe/motor learning should store only derived local model data, expose reset/export
controls and never train from password fields.
