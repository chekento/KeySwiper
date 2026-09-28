# Autocorrect Timeline

KeySwiper keeps a small in-memory edit timeline for the active input session.

The timeline is deliberately narrower than a full document undo stack: it records KeySwiper-controlled replacements such as prediction corrections, swipe-candidate changes and Voice Editing replacements.

## Safety model

Before Undo or Redo, KeySwiper verifies that the exact text expected by the timeline still exists immediately before the cursor.

If the cursor moved or the target app changed the text, the operation stops instead of deleting unrelated content.

## UI

- ↶: Undo latest KeySwiper replacement.
- ↷: Redo latest undone replacement.
- Long-press ↶: inspect recent correction history.

## Privacy

The timeline is:
- session-local;
- memory-only;
- cleared when input finishes;
- disabled in sensitive/password fields.
