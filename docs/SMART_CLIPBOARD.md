# Smart Clipboard

KeySwiper 0.12 turns the clipboard panel into a local structured workspace rather than a flat list.

## Categories

Captured text is classified locally as:

- Link
- Email
- Phone
- Address
- Code
- Text

Classification is deterministic and does not call an online service.

## Search

The clipboard panel contains a search field.

Search matches:

- the clipboard text;
- the category label.

## Pinning

Temporary clipboard history remains memory-only.

Only entries the user explicitly pins are persisted in KeySwiper private app storage. Pinned text is encoded for storage and remains local to KeySwiper.

Unpinning an item returns it to normal expiry behavior.

## Expiry

Unpinned clipboard items use one of three quick expiry presets:

- 10 minutes
- 1 hour
- 1 day

The selected default is stored locally and applies to newly captured unpinned clipboard text.

## Delete and clear

Delete removes one chosen item.

Clear removes all unpinned clipboard items while retaining explicitly pinned entries.

## Privacy

The clipboard panel remains disabled for fields treated as sensitive by KeySwiper.

There is no cloud clipboard sync and no clipboard analytics.

Normal history is not persisted. Only explicit pins survive process restarts.
