# Voice Editing

## 0.24 continuous dictation

Ordinary dictation now aggregates successive Android SpeechRecognizer results in one session. The recognizer may stop after a short sentence on some devices; KeySwiper re-arms it to capture subsequent sentences. The panel previews the entire accumulated text and commits it once at the end of the session, not after each short recognition.

- Tap 🎙 to dictate multiple sentences. Continue after natural short breaks.
- Tap **Beenden** to finalize manually; if the recognizer is still processing the last phrase, KeySwiper waits briefly for that result.
- With collected text, an approximately 9-second pause finishes automatically. Recognition is limited to 5 minutes / 12,000 characters; Android system backends can impose their own limitations.
- Leaving the keyboard or changing input fields cancels the current audio session; this deliberately avoids inserting speech into the wrong editor.
- Command mode remains one-shot, so instructions execute only after a single recognized command.
- The microphone is accessed using Android SpeechRecognizer. KeySwiper does not store audio recordings. On-device speech remains the default; system/network recognizers require explicit permission in Settings.


## 0.22 dictation and privacy

The recording panel now includes a measured microphone-level envelope, elapsed recording time, live partial transcript and a separate processing state. Returning to the keyboard cancels microphone ownership. The panel fits the normal keyboard height budget and is screenshot-protected by default.

Dictation uses strict on-device recognition by default. Settings can explicitly allow the Android system recognizer, which may use network services. Regional language tags and quality formatting are requested where supported; explicit personal vocabulary is provided only to a local backend, and device-context biasing is disabled. Recognition accuracy and available languages remain dependent on the installed Android recognizer and its models.

Command mode requests unformatted speech and retains session checks. Unknown commands do not insert their raw text. KeySwiper does not save audio buffers; offline Android voices are used for reading text aloud.

KeySwiper 0.13 separates ordinary dictation from deterministic voice commands.

## Controls

- Tap 🎙: normal Voice2Text dictation.
- Long-press 🎙: Voice Editing command mode.

The long-press is consumed by the toolbar button, so command mode does not also trigger a normal microphone click.

## Commands

Initial German and English command set:

### Delete

- Lösche letztes Wort
- Letztes Wort löschen
- Delete last word
- Lösche letzten Satz
- Delete last sentence

### Structure

- Neue Zeile
- New line

### Selection / clipboard

- Alles auswählen
- Select all
- Kopieren / Copy
- Ausschneiden / Cut
- Einfügen / Paste

These use the target editor's Android context-menu actions.

### Replace

Examples:

- Ersetze Peter durch Markus
- Replace Peter with Markus

KeySwiper replaces the most recent matching text before the cursor and keeps any suffix between that match and the cursor.

### Translation

Examples:

- Übersetze ins Englische
- Übersetze ins Deutsche
- Translate to French
- Translate to Spanish

A translation voice command requires selected text. It uses the existing KeySwiper on-device ML Kit translation flow; no unselected surrounding text is sent for translation.

### Swipe undo

- Letzten Swipe rückgängig
- Undo last swipe

This uses KeySwiper's existing whole-word swipe undo state.

## Safety and predictability

Voice command mode is intentionally deterministic.

If speech does not match a supported command, KeySwiper reports that the command was not recognized and does not insert the spoken phrase.

Voice Editing is disabled in fields KeySwiper treats as sensitive.

## Future Voice Editing

Planned additions:

- move cursor by word/sentence;
- change capitalization;
- punctuation commands;
- richer replacement ranges;
- “make friendlier / shorter / professional” through an explicit local or configured AI transformation provider;
- user-defined voice macros.
