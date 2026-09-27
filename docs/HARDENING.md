# Daily-driver Hardening

KeySwiper 0.14 is a stabilization release rather than a feature showcase.

## Editor-aware keyboard modes

The focused Android editor is classified into one of four local modes:

- Text
- Email
- URL
- Number / Phone

Email fields expose an @ key.

URL fields expose a / key.

Number and phone fields start directly on the number/symbol surface and suppress normal word/sentence prediction.

## Backspace safety

Backspace first checks whether the target editor currently has selected text. If so, the selection is replaced with an empty string.

Without a selection, normal deletion uses a Unicode character boundary instead of deleting one UTF-16 code unit blindly. This prevents common surrogate-pair emoji corruption and handles combining sequences according to the runtime BreakIterator.

The existing whole-word Swipe undo remains the higher-level Backspace behavior immediately after a swipe.

## Cursor-aware context

Android apps can move the cursor or change a selection without KeySwiper causing the change.

KeySwiper now listens to InputMethodService selection updates and performs a short debounced refresh of:

- surrounding context;
- language hints;
- prediction candidates;
- sentence-start auto capitalization.

## Input-session reset

When focus moves to a new input field, KeySwiper closes any old auxiliary panel and returns to the main keyboard in the appropriate editor mode.

This prevents an old Clipboard, Emoji or Translation panel from leaking visually into a newly focused field.

## CI hardening

Every main-branch build now runs:

1. unit tests;
2. Android lint;
3. debug APK assembly;
4. permanent release/archive publishing.

The release steps only run if the earlier quality gates succeed.
