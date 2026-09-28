# KeySwiper Real-device Daily-driver Test Plan

This checklist is for real Android hardware. Passing CI proves unit tests, Android lint and APK compilation; it does not prove touch feel, Samsung IME switching, stylus hardware behavior or third-party editor compatibility.

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

- Confirm the IME sits above and visually joins the Android navigation bar.
- Confirm Matrix Cyber is the default theme.
- Confirm all three character rows use the available keyboard width.
- Confirm rounded key surfaces and pressed-state feedback.
- Confirm Backspace is the rightmost key on the third character row, after M on DE/EN layouts.
- Confirm Emoji is immediately left of Space and is no longer in the toolbar.
- Confirm toolbar remains compact and does not dominate the keyboard.
- Rotate portrait/landscape and verify no clipped keys.

## 3. Tap and swipe

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
