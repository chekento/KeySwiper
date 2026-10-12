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
- Confirm Backspace is right of P and the wider Shift is left of Y on QWERTZ. Double-tap Shift for Caps Lock, type several letters, then tap Shift to unlock.
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
- Verify a Backspace tap removes one grapheme (or reverses an immediate autocorrection); holding progresses to single words and stays there by default. Release, slide off the key and cancel separately; deletion must stop. Undo must restore the held deletion at the original cursor context. Enable advanced deletion separately to test groups/sentences/paragraphs.
- Swipe two words, then tap a letter without pressing Space: both words must remain separated and the letter must start the next word.
- Type a word without a final space, then swipe a new word: the two words must not join.

## 4. Context prediction

Test:
- normal chat text;
- email;
- search field;
- URL field;
- multiline document.

Verify:
- completion while typing;
- accept a completion and immediately type/swipe the next word without pressing Space;
- accept a completion immediately before an existing space: the cursor must end after one separator, with no doubled space;
- replace a selected word with a suggestion: the preceding word must remain intact;
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



## 0.19 layered input and correction checks

- With a finger, swipe a word, briefly leave each edge, return and release. Repeat with the pen, and then release just beyond an edge. Each path should commit once.
- Add/lift a second finger or palm during a finger/pen swipe; the original pointer must keep control.
- Hold a/o/u and slide across the popup to choose ä/ö/ü or another accent; repeat with Shift and Caps Lock. No base letter should also appear.
- Hold digits, punctuation, and symbols on both symbol pages; release outside the popup to cancel.
- Select DE/EN/FR/IT/ES with the separate language key. A normal Space tap must insert one space; a horizontal Space drag moves the cursor.
- Browse all six emoji categories; hold a hand emoji for skin tones, then use Backspace to remove the whole inserted emoji once.
- Type “udn ” and “tastatru ” in a normal text field; check correction and immediate Backspace restoration. Press space again to retain the original.
- Check names, personal words, URLs, passwords, code fields, cursor movement and selected text for unwanted replacements.
- Type “ich mo” and inspect contextual umlaut completions; word labels must remain readable and select normally with the pen.
- Above the open keyboard, verify native editor selection and handwriting independently on the physical OEM device.

## 0.20 interaction checks

- After swiping/accepting “Hallo”, type comma: expect “Hallo, ”. Repeat with `. : ; ! ?` and the cluster `!?`. Manually typed spaces must remain unchanged.
- Put the cursor inside “Tastatru”, accept “Tastatur” and check that no old suffix remains; surrounding words must survive.
- Hold Space for 1, 4 and 10 seconds, then release: exactly one space is inserted and no repetition occurs. Change language with the separate language key.
- With the keyboard open, use the pen to tap the cursor, app controls and menus. No unwanted dot may appear. Circle existing text in supported editors; unsupported circles must not insert “o”. Write a normal small “o” in the manual handwriting pad.
- Test voice permission first granted, denied and later re-enabled. Watch partial text, press Stop, and verify one final insertion. Switch fields or hide the keyboard during recognition; no late text may enter another field. Check offline/missing recognizer errors and real audio on a physical phone.
- Read selected text aloud, stop playback, then switch languages. Check missing TTS language handling.
- Search clipboard with keys and pen; the target app text must remain untouched. Add/edit a snippet, pin it, copy/insert it, delete several cards and undo. Delete the current system clip from history and reopen: it must stay deleted.
- Rotate/open/close panels during a held key and verify no stuck repeat or delayed action.

## 0.21 punctuation and language checks

- Confirm the separate punctuation row is gone. Tap period beside M, then hold it and slide to comma, question mark, exclamation mark, colon and semicolon. No extra period may be inserted.
- Compare the larger letter-key height; swipe paths must still match the displayed letters.
- Type “die tastatur”, “Hallo. ich”, “das ist schon”, “ich mochte”, “fur”, “moglich”, “grosser” and “bucher”. Check capitalization/umlaut alternatives and that ambiguous valid words are not silently changed.
- Confirm both “Tastatur” and the original “tastatur” can remain selectable; check names, acronyms, formal Sie/Ihnen and “morgen” versus “Guten Morgen”.
- Test “Ich kann nicht ”, “Es geht um einen Termin. Ich möchte ” in email context, and “Ich kann nicht |teilnehmen”. The continuation must fit the phrase, form of address and existing right-hand text.
- Hold a long suggestion to read it fully. Set the depth to 2, 8 and 12 and check the maximum inserted length.
- Teach a recurring personal phrase; matching longer context should outrank unrelated frequent bigrams.

## 0.22 cursor, deletion, stable suggestions and numeric keypad

- Swipe Space left/right, reverse direction, start with small jitter and cancel a gesture. Drags move the cursor without changing text; a stationary tap/hold inserts one space on release. Lift the original pointer while another finger remains down and verify no extra space appears.
- Move across family emoji, skin tones, flags and accented combining characters. Collapse a selected range in either direction, move at both text edges, and repeat in clipboard search/snippet editing without moving the target app's cursor.
- Hold Backspace for 15 seconds in ordinary text with the default setting. It must remain at one word per repeat; the spacebar displays the active unit. Test the optional advanced setting independently, and verify number/PIN fields still repeat by character.
- Inspect three fixed word slots and open `⌄` for sentence suggestions/further words. Undo/Redo becoming available must not change slot widths. Hold a word while a newer prediction arrives; releasing must accept the originally pressed word.
- Switch between ordinary text, an integer, a signed number, a decimal, a signed decimal, a numeric PIN, phone, date and time fields. Integer/PIN fields must show only 0–9 plus Backspace/Enter; decimal/minus and other separators follow each field's requirements.
- In numeric mode confirm no alphabet/symbol-page switch, toolbar, word suggestions, swipe words or long-press fractions appear. Enter all ten digits and return to a text field; the normal keyboard must return immediately.
- Compare ordinary, numeric and expanded-suggestion heights in portrait/landscape against the one-third display budget.
