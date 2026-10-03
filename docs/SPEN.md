# S Pen / stylus support

## Input areas in 0.18

On letter keys, both fingers and S Pen can type and swipe words. A short pen tap types the selected key; small jitter remains a tap, while deliberate movement starts a word swipe. Fast strokes and closed paths are accepted. Additional palm/finger contacts do not interrupt an active pen path. Android canceled contacts never commit a key or word.

Menus, predictions, settings and other keyboard controls retain normal selection behavior and opt out of Android automatic handwriting. With the keyboard open, native handwriting and selection remain available in compatible editors above it, without opening a separate keyboard panel. The optional manual handwriting panel also sits above the still-visible keyboard. Native handwriting and editor gestures are restricted to the area above the keyboard; circles select text where the target editor supports selection gestures. A stroke crossing an input-area boundary is canceled rather than changing modes halfway through.

Android 16 uses an explicit handwriting region so outside strokes pass through to their original target. Earlier versions route intercepted keyboard strokes back to the keyboard controls. Physical S Pen and third-party editor behavior still require device testing.

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

