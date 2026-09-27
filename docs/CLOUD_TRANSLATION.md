# Google language stack

KeySwiper uses Google language capabilities in layers.

## Local-first

- ML Kit Language Identification: source-language detection on device.
- ML Kit Translation: local translation for supported downloadable language models.
- ML Kit Digital Ink Recognition: on-device handwriting recognition for hundreds of language models/variants.

## Broader cloud coverage

Google Cloud Translation supports a much broader language catalog than the local ML Kit
translation models. KeySwiper will expose this as an explicit opt-in cloud path.

The APK must not contain a Google service-account private key. Production cloud translation
should use either a user-owned restricted credential flow or a minimal authenticated gateway.
Only text explicitly selected for translation should be transmitted.

The `TranslationEngine` therefore remains local-first and fails closed when a language pair is
not present locally rather than silently uploading typed text.
