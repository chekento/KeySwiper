# KeySwiper architecture

KeySwiper is intentionally split into independent input layers.

## Pipeline

```
Touch / Swipe ─┐
Stylus / Ink ──┤
Voice ─────────┼─> Input adapters ─> candidate layer ─> InputConnection
Clipboard ─────┤
Emoji ─────────┤
Translation ───┘
```

## Components

- `KeySwiperImeService` — Android IME lifecycle and text commit boundary.
- `KeyboardSurface` — tap and swipe trace acquisition.
- `SwipeDecoder` — replaceable candidate decoder. The v0.1 implementation is deliberately small.
- `TranslationEngine` — local language detection and translation through Google ML Kit.
- `DigitalInkEngine` — downloadable local handwriting models.
- `VoiceInputController` — Android `SpeechRecognizer`, preferring on-device recognition.
- `ClipboardController` — ephemeral clipboard history while the IME process is active.
- `SecurityPolicy` — disables higher-risk helpers in password/no-learning fields.

## Planned adaptive swipe model

The production decoder should score candidates from geometry, key-center distance, path velocity,
per-user motor offsets, language probability, previous-word context, profile and correction history.
The motor model should remain local by default.
