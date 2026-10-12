# Privacy and security model

KeySwiper implements concrete protections inside its own Android IME. It does not claim complete protection against third-party keyloggers, malicious accessibility tools, rooted devices or a compromised operating system.

## Clipboard and local data

- Accepted clipboard history is authenticated-encrypted with AES-256-GCM and atomic file replacement.
- Clipboard keys remain in Android Keystore; history is stored in the application's no-backup directory.
- There is no plaintext clipboard fallback. Failed writes restore the last durable in-memory state, and failed reads do not overwrite the stored file.
- Absolute temporary deadlines survive restarts. Pins and saved snippets persist independently.
- Sensitive system clips are not captured, and KeySwiper-generated copies suppress the Android system preview.
- Automatic capture is suspended when the keyboard closes or a private/password field is active.
- Normal prediction personalization remains local application data with reset controls. Clipboard encryption does not imply that every prediction preference or learned model uses the clipboard encryption format.
- The application disables Android backup and cleartext network traffic.

## Typing and private views

Password variations and Android's no-personalized-learning flag are respected. The additional private mode disables contextual prediction, learning, swipe suggestions, clipboard, dictation and transformations even in ordinary fields. Held private deletion is character-based and is not added to the correction timeline.

Obscured touches are rejected. Clipboard and dictation views are marked sensitive for accessibility on supported Android versions. Their screenshots/screen recordings are blocked by default; users may turn off that panel protection in Settings. Password/private-field protection remains enabled. This protects the IME window; the focused application's own text field remains that application's responsibility.

The app has no key-event logging pipeline or app-owned text-upload endpoint. Third-party SDKs and installed Android services have their own behavior; local model use is distinct from model downloads over the network.

## Speech

Strict on-device recognition is the default. If unavailable, dictation reports the missing offline service/model rather than silently using a network-backed recognizer. The user can explicitly allow the system recognizer, whose network behavior depends on the installed service.

Personal bias words are supplied only to the local recognizer. Device-context biasing is disabled. Audio buffers are not saved by KeySwiper. Stop waits for the final result; cancel, field changes and leaving the panel invalidate late callbacks. Text-to-speech selects offline voices only.

## Development diagnostics

The debug-only smoke host requires the privileged Android DUMP permission, so ordinary third-party apps cannot invoke it. CI diagnostic examples use synthetic test text. Alpha APKs and emulator checks do not constitute an independent security audit or a real-device recognition-quality benchmark.
