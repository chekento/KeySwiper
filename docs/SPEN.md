# S Pen / stylus plan

## Implemented in v0.1

KeySwiper treats Android stylus events as first-class input:

- stylus handwriting pad
- time-preserving stroke capture through ML Kit Ink points
- primary stylus button event support while interacting with the IME
- secondary button candidate cycling where Android exposes the event

Default mapping:

- primary button → toggle Voice2Text
- secondary button → next swipe candidate

## Samsung remote / Air Actions

Remote S Pen events require Samsung-specific APIs and vary by device and S Pen generation.
KeySwiper keeps this capability behind an adapter boundary so the core keyboard does not depend
on Samsung SDK availability.

Planned configurable actions include single/double click, directional Air Actions,
clockwise/counter-clockwise gestures, translation, clipboard, emoji, undo/redo,
voice toggle and profile switching.

The Android-generic stylus path remains functional on non-Samsung devices.
