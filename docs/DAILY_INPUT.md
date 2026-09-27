# Daily Input Controls

KeySwiper 0.11 focuses on making the keyboard practical for normal daily typing.

## Keyboard layouts

The keyboard can use:

- German QWERTZ
- English QWERTY
- French AZERTY
- Italian QWERTY
- Spanish QWERTY

The active layout can be changed in Settings or cycled directly from the compact language key below the letter rows.

Swipe traces carry the active layout ID, so geometric swipe scoring and the local motor profile use the correct physical key positions.

## Accents and umlauts

The compact language row exposes common characters without requiring a long-press popup:

- DE: ä ö ü ß
- FR: é è à ç ù
- IT: à è é ì ò ù
- ES: ñ á é í ó ú

English keeps a compact case-control key instead.

## Numbers and symbols

?123 switches the main surface to a dedicated number/symbol layout.

ABC returns to letters.

Swipe recognition is disabled on the symbol surface to avoid accidental word decoding.

## Translation target picker

The 🌐 toolbar control opens a local target-language panel.

The panel shows:

- the current translation target;
- recently used targets first;
- the detected source-language hints;
- common quick targets;
- an explicit Translate selection action.

Target selection is persisted locally. Translation still happens only after an explicit user action and only on selected text.

## Editor action key

The enter key now respects the Android editor action when available:

- Search
- Send
- Go
- Done
- Next

If the editor exposes no action, KeySwiper falls back to a normal Enter key event.
