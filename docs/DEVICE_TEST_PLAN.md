# KeySwiper Real-device Daily-driver Test Plan

CI also runs a shell-driven Android 15 AOSP ATD emulator smoke test. It installs the debug APK, enables and selects KeySwiper from outside the app process, opens a debug-only focused text field, verifies the KeySwiper package stays alive and confirms Android reports the IME as current and visible. Failures preserve dumpsys and logcat diagnostics. It also runs debug input checks with synthetic Android pen/finger events, menu selection and upper-pad layout assertions. This catches basic startup, input-routing and service-lifecycle regressions, but it does not prove touch feel, Samsung OEM switching, stylus hardware behavior or third-party editor compatibility; those still need the real-device checks below.

## 1. Setup and system integration

- Install the latest APK.
- Open KeySwiper Settings.
- Confirm status says KeySwiper is registered.
- Tap Enable KeySwiper.
- On Samsung/Android keyboard settings, turn the KeySwiper switch on.
- Return to KeySwiper and confirm the status changes to enabled.
- Tap Choose KeySwiper and select it.
- Confirm Settings shows selected ✓.
- Open at least three apps with ordinary text fields.

## 2. Keyboard geometry and UI

- Confirm no KeySwiper status strip remains above the functional keyboard controls; status feedback should appear transiently.
- Confirm the complete normal keyboard UI uses at most one third of the display height.
- Confirm the IME reserves the Android navigation-bar inset instead of drawing keys underneath it.
- Confirm the IME sits above and visually joins the Android navigation bar.
- Confirm Matrix Cyber is the default theme.
- Confirm all three character rows use the available keyboard width.
- Confirm rounded key surfaces and pressed-state feedback.
- Confirm Backspace is the rightmost key on the third character row, after M on DE/EN layouts.
- Confirm Emoji is immediately left of Space and is no longer in the toolbar.
- Confirm toolbar remains compact and does not dominate the keyboard.
- Rotate portrait/landscape and verify no clipped keys.

## 3. Tap and swipe

- Tap at least 20 keys with S Pen/stylus and verify normal pen jitter never starts a swipe.
- Swipe several words with the pen and verify it commits words, just like finger swiping. Verify small pen jitter still types only the starting key.
- Rest a hand on the screen before/during a pen swipe and lift it before/after the pen; verify the pen word commits only once.
- Perform fast pen swipes and closed-loop pen swipes such as “test”; neither should be rejected by speed or final displacement.
- Tap menus, predictions, clipboard search and Settings fields with the pen; they must select/focus normally without starting handwriting.
- Open the handwriting panel and verify it stays above the visible letter keys; its buttons select normally.
- Start a stroke in the writing area and cross onto the keyboard; it must cancel rather than type a key or continue ink.
- With both finger and pen, swipe “hallo”, “danke”, “morgen”, “tastatur” on QWERTZ and “hello”, “world”, “thanks” on QWERTY.
- Swipe “test” and “dad” back to the starting key and verify they remain word swipes.
- With the keyboard open, write directly in a compatible editor above it and circle existing text to check handwriting/selection.
- Write multiple strokes with short pauses and one long stroke; recognition must wait for pen-up and preserve all strokes.
- On Android 16 verify native handwriting does not capture keyboard/menu touches. On Android 13–15 verify intercepted keyboard taps reach the original control.
- Type German umlauts and ß.
- Type English and German mixed text.
- Swipe at least 50 common words.
- Correct deliberately wrong swipe candidates.
- Verify corrected candidates train the local swipe model.
- Verify swipe path still follows the visible full-width key geometry.
- Verify Backspace immediately after a swipe removes the whole swiped word.

## 4. Context prediction

Test:
- normal chat text;
- email;
- search field;
- URL field;
- multiline document.

Verify:
- completion while typing;
- next-word candidates;
- 2–6 word semantic continuations;
- context refresh after moving the cursor;
- numeric/phone editors suppress word prediction.

## 5. Autocorrect Timeline

- Trigger an explicit prediction replacement.
- Trigger a swipe-candidate correction.
- Trigger a Voice Editing replace command.
- Press ↶ and verify exact restoration.
- Press ↷ and verify reapplication.
- Move the cursor elsewhere and verify stale Undo refuses to edit unrelated text.
- Long-press ↶ and inspect the session timeline.
- Start a new field/input session and verify the timeline is reset.

## 6. One-hand mode

- Long-press ⌨ repeatedly: full → left → right → full.
- Verify keyboard width and anchoring on each side.
- Verify swipe/tap coordinates remain correct.
- Change one-hand mode in Settings and reopen the IME.

## 7. Developer layout

Open </> and test in a code editor or terminal-capable app:
- Esc and Tab;
- arrows;
- Home/End;
- braces/brackets/parentheses;
- slash/backslash/pipe/backtick/quotes;
- Ctrl+A/C/V/X/Z/Y.

Record apps that intentionally ignore hardware-style key events.

## 8. Clipboard, Voice and Translation

- Capture text/link/email/code clipboard entries.
- Search and pin/unpin entries.
- Verify expiry presets.
- Test normal dictation.
- Long-press microphone and test deterministic Voice Editing commands.
- Translate selected text locally with ML Kit.

## 9. Handwriting / S Pen

On compatible hardware:
- write directly over a supported text field;
- use the in-keyboard handwriting panel;
- scratch-out a word;
- circle text for selection;
- draw a long horizontal line through whitespace for Remove Space;
- draw a long vertical gesture at a boundary for Join/Split;
- verify unsupported target editors keep the stroke as handwriting instead of silently losing it.

## 10. Sensitive fields

In password/PIN/private fields verify:
- surrounding context off;
- prediction learning off;
- neural prediction off;
- clipboard history hidden;
- voice input/editing blocked;
- translation blocked;
- handwriting content actions blocked;
- correction timeline unavailable.

## Test report

For each failure capture:
- device/model;
- Android version/API;
- target app;
- field type;
- exact steps;
- screenshot/video if visual;
- expected behavior;
- actual behavior.

