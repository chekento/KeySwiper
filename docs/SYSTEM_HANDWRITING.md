# Direct Stylus Handwriting

KeySwiper 0.9 implements Android's native stylus-handwriting IME lifecycle.

## System integration

The IME advertises:

`android:supportsStylusHandwriting="true"`

On supported Android text fields, the framework can request a handwriting session. KeySwiper then uses:

- `onPrepareStylusHandwriting()` to pre-warm the configured ML Kit handwriting model;
- `onStartStylusHandwriting()` to attach a transparent inking view to the system handwriting window;
- `onStylusHandwritingMotionEvent()` to consume stylus strokes immediately;
- `onFinishStylusHandwriting()` to clear transient ink/session state.

## Recognition pipeline

```
Stylus stroke over target editor
        ↓
Android handwriting session
        ↓
KeySwiper system ink overlay
        ↓
650 ms stroke-group debounce
        ↓
Google ML Kit Digital Ink
        ↓
InputConnection.commitText()
        ↓
Prediction + local vocabulary learning
```

The keyboard's normal input view does not need to cover the target editor during system handwriting.

## Privacy

Direct handwriting never starts in fields treated as sensitive by KeySwiper.

Ink batches are transient. The handwriting recognizer receives the current batch plus a short pre-context for recognition quality; KeySwiper does not persist raw handwriting strokes.

## Compatibility

The underlying IME handwriting lifecycle APIs exist from Android API 33. Android 14+ text fields can start stylus handwriting automatically when a compatible stylus and handwriting-capable IME are available.

The existing KeySwiper handwriting panel remains available on devices or apps where direct system handwriting is not started.

## Current limitations

- connectionless handwriting sessions are not yet implemented;
- editor handwriting gestures such as select/delete/insert are not yet mapped;
- handwriting-session UI is intentionally minimal;
- real-device validation is still required across Samsung S Pen and other Android stylus hardware.
