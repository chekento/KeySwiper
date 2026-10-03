# Direct Stylus Handwriting

KeySwiper 0.10 supports both regular Android stylus handwriting and Android 15+ connectionless handwriting.

## Writing and selection areas (0.18)

The area above the visible keyboard is the native writing/selection area. Strokes starting on keyboard controls are routed to those controls on older Android releases, with a screen-to-keyboard coordinate conversion. Strokes starting above the keyboard stay latched to the writing area; crossing its boundary cancels that stroke, including historical samples.

On Android 16+, `setStylusHandwritingRegion` excludes the keyboard/system bars and narrows the session to existing ink plus a margin after a stroke. Android passes strokes outside that region back to the target app. Rotation finishes the session so old coordinates cannot affect the new layout. Ink is transformed from screen coordinates into the overlay's local coordinates.

Both fingers and pens can type/swipe on letter keys. With the keyboard open, Android can start handwriting directly in compatible editors above it; no manual handwriting panel is required. The optional manual handwriting pad opens above the visible keyboard. Recognition is paused during an active stroke, and draining completed ink preserves any new active stroke. KeySwiper menus and Settings fields opt out of automatic handwriting. Native circle selection, scratch-out and whitespace gestures still depend on the editor advertising the corresponding Android gesture support.

## Regular system handwriting

The IME advertises:

`android:supportsStylusHandwriting="true"`

On supported Android text fields, the framework can request a handwriting session. KeySwiper uses:

- `onPrepareStylusHandwriting()` to pre-warm the configured ML Kit handwriting model;
- `onStartStylusHandwriting()` to attach a transparent inking view to the system handwriting window;
- `onStylusHandwritingMotionEvent()` to consume stylus strokes immediately;
- `onFinishStylusHandwriting()` to clear transient ink/session state.

Recognized text is committed through the current `InputConnection`.

## Connectionless handwriting — Android 15+

Some UI flows request handwriting before a normal editor connection exists, including delegated/search-style interfaces.

KeySwiper implements:

`onStartConnectionlessStylusHandwriting(inputType, cursorAnchorInfo)`

A connectionless session:

1. validates the requested input type and refuses password input;
2. attaches the same transparent KeySwiper ink view;
3. recognizes stroke groups locally with Google ML Kit Digital Ink;
4. accumulates the recognized phrase without using an `InputConnection`;
5. waits briefly for another stroke;
6. delivers the final phrase using `finishConnectionlessStylusHandwriting(text)`.

A new stroke cancels a pending finish, allowing short multi-word input.

## Scratch-out deletion gesture

Android API 34 introduced editor handwriting gestures. KeySwiper 0.10 adds the first gesture recognizer: **scratch-out deletion**.

A stroke is treated as scratch-out only when it has:

- sufficient horizontal width;
- a relatively low height/width ratio;
- multiple horizontal direction reversals;
- a path length substantially longer than its bounding width.

If the target editor declares support for Android `DeleteGesture`, KeySwiper removes only that scratch stroke from the recognition ink and calls:

`InputConnection.performHandwritingGesture(DeleteGesture, ...)`

The gesture uses **word granularity** and a deletion rectangle in screen coordinates. The target editor remains responsible for deciding which text intersects the gesture.

If the editor does **not** declare `DeleteGesture` support, KeySwiper leaves the stroke untouched so it can still be interpreted as normal handwriting.

## Recognition pipeline

```
Stylus stroke over target editor
        ↓
Android handwriting session
        ↓
KeySwiper transparent ink overlay
        ↓
Gesture classifier ── scratch-out ──> Android DeleteGesture
        │
        └── normal ink
               ↓
        650 ms stroke-group debounce
               ↓
        Google ML Kit Digital Ink
               ↓
     ┌─────────┴──────────┐
     ▼                    ▼
InputConnection     Connectionless result
regular session     Android 15+
```

## Privacy

- password/sensitive regular editors do not start KeySwiper handwriting;
- password input types are rejected for connectionless sessions;
- raw stroke data is transient;
- handwritten text recognition is local;
- the existing manual handwriting pad remains available.

## Current gesture roadmap

Next handwriting gestures:

- select / select range;
- character-granularity scratch-out;
- insert / cursor placement;
- join or split whitespace;
- gesture preview where the editor advertises preview support.

