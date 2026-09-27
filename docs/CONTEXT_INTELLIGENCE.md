# Context Intelligence

## Purpose

Prediction quality improves when the keyboard understands where the cursor sits inside the current thought, not only the last few tokens.

KeySwiper 0.6 introduces a local **Surrounding Context Snapshot**.

## Signals

For non-sensitive fields the prediction engine can use:

- up to 1600 characters before the cursor;
- up to 600 characters after the cursor;
- selected text;
- previous sentence;
- current sentence before the cursor;
- current sentence after the cursor;
- next sentence;
- current paragraph;
- up to ten locally extracted topic terms;
- question intent;
- detected language;
- Android editor/input mode.

All of these signals are derived transiently for prediction.

## Android retrieval

On Android API 31+ KeySwiper uses `InputConnection.getSurroundingText()` so text before the cursor, selection and text after the cursor can be obtained as one surrounding-text snapshot.

Older supported devices fall back to:

- `getTextBeforeCursor()`;
- `getSelectedText()`;
- `getTextAfterCursor()`.

Editors are allowed to return less text than requested, so all context processing is defensive.

## Semantic use

Local beam search uses the current sentence as its primary history.

Paragraph topic terms add a ranking prior for related continuations. Existing text after the cursor is used as a bridge signal so a completion can fit into text that already exists instead of blindly appending a duplicate phrase.

The neural provider receives the same structured snapshot and is explicitly instructed not to repeat text already present after the cursor.

## Privacy

Sensitive/password fields never create a surrounding-context snapshot for prediction. They receive an empty snapshot, and local/neural prediction is bypassed.

Context snapshots are not persisted as documents. Personal learning stores only derived short word-transition counts.
