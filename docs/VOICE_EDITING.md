# Voice Editing

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
