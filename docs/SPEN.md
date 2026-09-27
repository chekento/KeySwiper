# S Pen / stylus support

## v0.8 — configurable local stylus actions

KeySwiper treats Android stylus events as first-class input and does not require Samsung-specific APIs for its core pen workflow.

Implemented triggers:

- primary stylus button · single click
- primary stylus button · double click
- secondary stylus button

Primary-button events are debounced at the view layer so Android does not accidentally turn one physical event reported through multiple MotionEvent paths into a false double-click.

A primary single-click is delayed by a short 280 ms window so a real double-click can be recognized.

## Configurable actions

Each supported trigger can independently map to:

- Voice2Text on/off
- accept the top prediction
- next swipe candidate
- previous swipe candidate
- translate selected text
- open clipboard
- open emoji panel
- open handwriting
- undo the most recent swipe word
- open KeySwiper Settings
- no action

Defaults:

- primary single → Voice2Text
- primary double → translate selected text
- secondary → next swipe candidate

Mappings are stored locally and can be reset from Settings.

## Candidate safety

Cycling through swipe candidates with the stylus does **not** train every intermediate candidate as a correction.

Explicit candidate taps still train the adaptive swipe model. This distinction prevents browsing candidates from poisoning the personal correction model.

## Handwriting surface

Both primary and secondary Android stylus buttons are handled consistently on the normal keyboard surface and the handwriting surface.

## Sensitive fields

Content-affecting stylus actions are blocked in sensitive/password fields. Opening Settings and mapping an action to “None” remain available.

## Samsung Remote / Air Actions

Samsung documents Air Actions as remote actions delivered to the foreground application, and currently permits a single RemoteAction definition per app. The S Pen Remote SDK is likewise foreground-app oriented.

A system IME normally serves a *different* foreground application, so KeySwiper does not claim that Samsung remote Air gestures can be captured reliably across arbitrary apps.

For that reason:

1. the system-wide keyboard relies on Android-generic stylus events;
2. Samsung Remote/Air Actions remain behind an optional adapter boundary;
3. no Samsung SDK dependency is required for normal KeySwiper operation;
4. device-specific support can be enabled later only where runtime tests prove that the event is actually delivered to the IME.

This keeps KeySwiper functional on Samsung and non-Samsung stylus devices without making unsupported assumptions about remote gesture routing.
