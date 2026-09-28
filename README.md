<p align="center">
  <img src="docs/assets/marketing/keyswiper-app-icon.webp" alt="KeySwiper app icon" width="150">
</p>

<h1 align="center">KeySwiper</h1>

<p align="center">
  <strong>Adaptive AI Keyboard for Android</strong><br>
  Swipe · Context Intelligence · Local AI · Voice · Handwriting · Translation · Stylus · Privacy
</p>

<p align="center">
  <strong>Current build: 0.17.0-alpha17 · Build 17 · Run 36</strong><br>
  Unit Tests ✓ · Android Lint ✓ · APK Build ✓ · Android 15 AOSP IME Smoke ✓ · Permanent Release ✓
</p>

<p align="center">
  <a href="https://github.com/chekento/KeySwiper/releases/latest/download/KeySwiper-latest.apk">
    <img alt="Download latest KeySwiper APK" src="https://img.shields.io/badge/%E2%AC%87%20DOWNLOAD-LATEST%20KEYSWIPER%20APK-2563EB?style=for-the-badge&logo=android&logoColor=white">
  </a>
</p>

<p align="center">
  <a href="https://github.com/chekento/KeySwiper/releases/latest">
    <img alt="Latest release" src="https://img.shields.io/badge/Release-0.17.0--alpha17-16A085?style=for-the-badge">
  </a>
  <a href="docs/versions/README.md">
    <img alt="APK archive" src="https://img.shields.io/badge/APK-ARCHIVE-111827?style=for-the-badge">
  </a>
  <a href="CHANGELOG.md">
    <img alt="Changelog" src="https://img.shields.io/badge/FULL-CHANGELOG-0F766E?style=for-the-badge">
  </a>
</p>

<p align="center">
  <a href="docs/versions/README.md">APK Archive</a> ·
  <a href="CHANGELOG.md">Changelog</a> ·
  <a href="docs/PREDICTION_ENGINE.md">Prediction Engine</a> ·
  <a href="docs/SWIPE_ENGINE.md">Swipe Engine</a> ·
  <a href="docs/DEVICE_TEST_PLAN.md">Real-device Test Plan</a>
</p>

> 🚧 **Experimental alpha — current validated release: 0.17.0-alpha17 / Build 17 / Run 36.** CI runs unit tests, an Android 15 emulator smoke test that opens the IME, Android Lint and APK compilation. OEM keyboard switching, touch behavior, swipe feel and stylus hardware still need physical-device checks. Do not rely on alpha builds for secrets such as passwords, recovery phrases or payment data.

## KeySwiper in action

<p align="center">
  <img src="docs/assets/marketing/keyswiper-neon-keyboard-future.webp" alt="KeySwiper — Neon keyboard of the future" width="100%">
</p>

<p align="center">
  <img src="docs/assets/marketing/keyswiper-beyond-boundaries.webp" alt="KeySwiper — Typing beyond boundaries" width="49%">
  <img src="docs/assets/marketing/keyswiper-local-ai-neon.webp" alt="KeySwiper — Local AI keyboard" width="49%">
</p>

<p align="center">
  <strong>One keyboard. Multiple input modes. Local intelligence where possible.</strong><br>
  Adaptive swipe · contextual prediction · multilingual input · Voice Editing · Smart Clipboard · handwriting · S Pen/stylus · local neural models
</p>

## What KeySwiper is

KeySwiper is an Android Input Method Editor built as an **adaptive multimodal input system**, not a clone of a conventional keyboard. Finger typing, geometric swipe input, context-aware prediction, local neural models, voice, handwriting, stylus actions, clipboard, emoji, language detection and translation are designed to cooperate inside one input session.

## Current feature set

### 🧪 Runtime smoke coverage

- CI installs the real debug APK on a lean Android 15 AOSP ATD emulator.
- A shell-driven harness enables and selects KeySwiper from outside the app process, then opens a debug-only text host and confirms Android reports KeySwiper as the current visible IME.
- The harness verifies the package stays alive and captures input-method, window, activity and logcat diagnostics on failure.
- Unit tests, Android Lint and APK compilation run before the emulator gate, so infrastructure failures cannot hide whether the core project still builds.
- The host exists only in the `debug` source set and is never included in the release APK.


### 🎨 Six keyboard themes

- Matrix Cyber remains the default
- OLED Obsidian for true-black OLED surfaces
- Neon Tokyo with magenta/cyan nightlife accents
- Aurora Glass with cool teal/blue glass-like tones
- Ember Copper with warm copper/orange contrast
- Kawaii Cyber with softer rounded pink/cyan cyber styling
- all themes share the same geometry, touch logic and prediction behavior

### 📐 Compact ≤ 1/3-height keyboard

- the normal keyboard, toolbar, prediction strip and navigation inset are budgeted to at most one third of the display height
- system navigation insets are reserved instead of drawing keyboard content underneath them
- status feedback uses transient toasts instead of adding a persistent bar above the keys
- controls scale proportionally on shorter displays

### 🖊 Tap-first stylus intent

- S Pen/stylus input uses a larger motion threshold than finger input
- small pen jitter remains a tap
- deliberate stylus paths can still swipe
- non-swipe taps commit the pen-down key for stability

### 🌐 Input-language-locked prediction

- predictions follow the current/recent input language instead of mixing all active language lanes
- active keyboard layout is used as a fallback when the context is still empty
- learned, semantic and optional neural suggestions are filtered against the resolved input language
- swipe decoding still permits intentional multilingual code-switching

### 🟢 Matrix Cyber UI

- default Matrix/Cyber/Techno theme with Android-compatible readability
- near-black surfaces with restrained neon-mint accents
- rounded full-width character keys with pressed states and keyboard haptics
- compact low-profile toolbar instead of large default Android buttons
- Backspace integrated directly after M on the third character row
- Emoji moved out of the toolbar to immediately left of Space
- prediction chips, auxiliary panels and Android navigation bar visually match the keyboard
- reusable theme-profile architecture prepared for additional selectable themes

See [Theme Architecture](docs/THEMES.md).

### ↶ Autocorrect Timeline / Undo–Redo

- session-local correction timeline for explicit prediction, swipe and Voice Editing replacements
- dedicated ↶ / ↷ controls beside the prediction strip
- long-press ↶ opens recent correction history
- context verification prevents stale undo from deleting unrelated text
- history is memory-only, per input session and disabled in sensitive fields

See [Autocorrect Timeline](docs/EDIT_TIMELINE.md).

### ↔ One-hand mode

- full-width, left-hand and right-hand modes
- long-press ⌨ cycles modes directly from the keyboard
- mode persists locally and can also be selected in Settings

### </> Developer layout

- Esc, Tab, arrows, Home/End
- braces, brackets, parentheses, angle brackets
- slash, backslash, pipe, tilde, backtick and quotes
- Ctrl+A/C/V/X/Z/Y through Android key events

### 🛠 Daily-driver hardening

- live IME setup-state detection: registered / enabled / selected
- Samsung/Android setup flow refreshes automatically after returning to KeySwiper
- in-app daily-driver readiness summary and concrete real-device checklist
- editor-aware Text / Email / URL / Number keyboard modes
- direct @ for email and / for URL fields
- numeric fields open on numbers/symbols and suppress word prediction
- grapheme-aware Backspace for emoji/combining characters
- selected text is deleted correctly before normal Backspace
- cursor/selection movement refreshes contextual predictions
- sentence-start auto capitalization
- stale auxiliary panels close when a new field receives focus

### ⌨️ Daily typing layouts

- German QWERTZ, English QWERTY, French AZERTY, Italian and Spanish QWERTY
- direct umlaut/accent row for DE/FR/IT/ES
- in-keyboard language/layout cycling
- dedicated ?123 numbers and symbols page
- layout-aware geometric swipe recognition
- Android Search/Send/Go/Done/Next editor actions
- 🌐 translation target picker with recent languages and source-language hints


### 🧠 Intelligent word & sentence prediction

- dedicated prediction strip between toolbar and keyboard
- live word completion while typing
- next-word prediction
- sentence continuations 2–6 words ahead
- local 2/3/4-gram personal learning
- contextual semantic beam search
- optional local LiteRT-LM neural refinement
- stale neural results are discarded instead of overwriting newer context
- General / Message / Email / Search / Code context modes
- personal vocabulary with remember / forget / reset controls
- prediction learning disabled in sensitive fields

### 🧩 Surrounding Context Intelligence

KeySwiper can locally reason over more than the immediately preceding word:

- text before **and after** the cursor
- selected text
- current sentence before/after the cursor
- previous and next sentence
- current paragraph
- topic terms
- question intent
- detected languages
- Android editor/input mode

This lets suggestions fit the current thought and avoid blindly duplicating text already present after the cursor.

### 🌍 Multilingual input & code-switching

- larger embedded German, English, Italian, French and Spanish language packs
- strict single-language lane for word/sentence predictions, resolved from current/recent input
- broader multilingual lanes remain available for intentional swipe code-switching
- mixed-language typing can switch prediction language from the newest identifiable input without a manual layout change
- language scoring from ML Kit hints, recent words, prefixes, diacritics and technical vocabulary
- accented/umlaut words preserve correct spelling while using normalized swipe geometry
- personal words can carry an optional language tag
- Google ML Kit language identification
- Google ML Kit on-device translation
- Google ML Kit Digital Ink models for broad handwriting coverage

### ✨ Adaptive Swipe v3

- real X/Y/time swipe traces instead of touched-letter sequences only
- Dynamic Time Warping against ideal keyboard paths
- path-length similarity
- direction-change scoring
- velocity-aware scoring
- per-letter personal motor offsets
- local learning from explicit corrections
- language-lane-aware swipe ranking
- whole-word Backspace undo immediately after a swipe
- candidate cycling through the suggestion strip or mapped stylus actions

### ✍️ Direct Android stylus handwriting

- native IME stylus-handwriting support
- Android 15+ connectionless handwriting for delegated/search-style flows
- scratch-out handwriting deletion through Android DeleteGesture when supported
- circle-selection through Android SelectGesture when supported
- horizontal remove-space gesture when supported
- vertical join/split gesture when supported
- system handwriting window
- transparent live ink overlay
- local ML Kit Digital Ink recognition
- direct commit into the focused app via InputConnection
- configured handwriting language shared with the manual handwriting panel
- short idle debounce for multi-stroke words
- eraser-tool clears the current uncommitted batch
- handwriting recognition feeds local vocabulary/prediction learning
- disabled in sensitive fields
- manual in-keyboard handwriting pad remains available

See [Direct Stylus Handwriting](docs/SYSTEM_HANDWRITING.md).

### 🖊️ Stylus / S Pen controls

Generic Android stylus-button mappings are configurable independently:

- primary single-click
- primary double-click
- secondary button

Available actions include:

- Voice2Text
- accept top prediction
- next / previous swipe candidate
- translate selected text
- clipboard
- emoji panel
- handwriting
- undo last swipe
- Settings
- None

Default mapping:

- primary single → Voice2Text
- primary double → translate selection
- secondary → next swipe candidate

Samsung remote Air Actions remain a device/foreground-specific optional adapter rather than a dependency for system-wide IME behavior.

### 🎙 Voice2Text + Voice Editing

- short tap 🎙 for normal dictation
- long-press 🎙 for deterministic Voice Editing commands
- Android SpeechRecognizer integration
- prefers the on-device recognizer when available
- partial recognition status
- dictation feeds prediction and personal vocabulary
- delete last word / delete last sentence
- new line
- select all / copy / cut / paste
- replace X with Y
- translate selected text to a named language
- undo last swipe
- unknown voice commands are not inserted as text
- disabled in sensitive fields

### 🌐 Translation

- explicit translation of selected text
- local language identification
- Google ML Kit on-device translation when the pair is supported
- configurable target language
- fails closed rather than silently uploading text when a local pair is unavailable

### 📋 Smart Clipboard 2.0

- local categories: Link, Email, Phone, Address, Code and Text
- search across clipboard text and categories
- Pin / Unpin / Delete per entry
- 10 min / 1 h / 1 day expiry presets for temporary history
- Clear removes temporary items while preserving pins
- only explicitly pinned entries persist in private app storage
- normal clipboard history remains ephemeral
- up to 40 local entries
- hidden in sensitive fields

### 😀 Emoji

- built-in emoji panel
- direct insertion into the target editor

### 🤖 Optional local neural models

KeySwiper can import a compatible `.litertlm` model into private app storage.

- model is separate from the APK
- SHA-256 calculated during import
- manual hash re-verification
- model can be removed from Settings
- lazy initialization
- neural work runs off the UI thread
- fast local prediction remains available without a model
- neural suggestions are marked with ✦

See [Neural Model Manager](docs/NEURAL_MODEL_MANAGER.md).

### 🔐 Privacy model

Keyboard software handles unusually sensitive input. KeySwiper therefore defaults to:

- no keystroke logging
- no analytics/telemetry layer
- no network transmission for ordinary typing
- no personalization in password / sensitive / no-learning fields
- no surrounding-context prediction in sensitive fields
- no neural inference in sensitive fields
- no clipboard panel in sensitive fields
- no Voice2Text in sensitive fields
- no handwriting helper/direct handwriting in sensitive fields
- local swipe, prediction and vocabulary models
- `allowBackup=false`

## App identity

The KeySwiper K/swipe logo is included as the Android launcher and IME icon.

## Install

1. Download the current APK using the large button above.
2. Install the APK on Android.
3. Open **KeySwiper**.
4. Tap **Enable KeySwiper**.
5. Enable it in Android's input-method settings.
6. Return and tap **Choose KeySwiper**.
7. Select KeySwiper in the system IME picker.

For stylus handwriting, use a compatible Android stylus and a text field/device that supports Android system handwriting.

## Build archive

Every successful `main` APK build is permanently preserved as a GitHub Release with:

- semantic version
- Android versionCode
- CI run number
- exact Git commit
- SHA-256 checksum
- permanent version-specific APK
- stable `KeySwiper-latest.apk` alias
- complete per-version changelog
- dedicated version page

➡️ [Browse every archived APK](docs/versions/README.md)

## Architecture

```
Touch / Swipe ───────────────┐
Stylus / Handwriting ────────┤
Voice ───────────────────────┤
Clipboard / Emoji ───────────┤
                             ▼
                    KeySwiper IME Core
                             │
        ┌────────────────────┼────────────────────┐
        ▼                    ▼                    ▼
 Adaptive Swipe       Context Prediction    Language Lanes
 Motor Model          2/3/4-gram Memory     Code Switching
        │                    │                    │
        └──────────────┬─────┴──────────────┬─────┘
                       ▼                    ▼
              Semantic Beam Search    Optional LiteRT-LM
                       │                    │
                       └─────────┬──────────┘
                                 ▼
                         Prediction Strip
                                 │
                                 ▼
                         InputConnection
```

## Technical baseline

- Android `InputMethodService`
- package: `cloud.kosch.keyswiper`
- minSdk 24
- target / compile SDK 36
- JDK 21 for CI
- Java bytecode target 17
- Gradle 9.6
- Android Gradle Plugin 9.4
- Kotlin Gradle Plugin 2.4.10
- Google ML Kit Language ID / Translation / Digital Ink
- LiteRT-LM Android 0.17.1

## Documentation

- [Daily-driver Hardening](docs/HARDENING.md)
- [Daily Input Controls](docs/DAILY_INPUT.md)
- [Smart Clipboard](docs/SMART_CLIPBOARD.md)
- [Voice Editing](docs/VOICE_EDITING.md)
- [Swipe Engine](docs/SWIPE_ENGINE.md)
- [Prediction Engine](docs/PREDICTION_ENGINE.md)
- [Code Switching](docs/CODE_SWITCHING.md)
- [Context Intelligence](docs/CONTEXT_INTELLIGENCE.md)
- [Direct Stylus Handwriting](docs/SYSTEM_HANDWRITING.md)
- [S Pen / Stylus](docs/SPEN.md)
- [Neural Model Manager](docs/NEURAL_MODEL_MANAGER.md)
- [Neural Prediction Roadmap](docs/NEURAL_PREDICTION.md)
- [Privacy](docs/PRIVACY.md)
- [Cloud Translation architecture](docs/CLOUD_TRANSLATION.md)
- [APK Version Archive](docs/versions/README.md)

## Roadmap

Next milestones:

- broaden emulator coverage to typing, editing and input-method lifecycle changes
- more handwriting edit gestures: select, range select, insert, join/split and preview
- multilingual translation-target wheel
- optional Google Cloud Translation gateway
- one-hand thumb geometry adaptation
- programmable input profiles
- optional Samsung Air Action adapter where device/foreground routing permits it

## License

Apache-2.0
