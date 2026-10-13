# Smart Clipboard 0.22

The clipboard opens as compact, recycled text cards inside the normal one-third-height keyboard budget. Tap a card to paste once. A pin is directly accessible; Copy, Save permanently, Edit and Delete are in its overflow menu. History, Pins and Saved are separate views. Search and editing temporarily expose the letter keys and route typing/cursor gestures to the local clipboard editor.

## Durable retention

All accepted entries are stored as atomic AES-256-GCM snapshots in the application's no-backup directory. The encryption key lives in Android Keystore. Encryption and file operations run on a dedicated worker; a successful save is displayed after the atomic write finishes. Storage failures preserve the prior snapshot rather than falling back to plaintext.

New temporary copies can retain 10 minutes, 1 hour, 1 day, 7 days or 30 days. The default for new installations is one day. Each card shows its own remaining time. Changing the default applies to new copies; it does not silently change existing deadlines.

Absolute expiry and the system-copy identity survive process restarts. Opening/searching the panel and Undo do not renew expiry. A system clip that was deleted or expired cannot return merely because Android still holds the same copy. The Android copy timestamp is used when available.

Pins have no expiry. Permanently saved snippets have no expiry independently of pinning; unpinning a saved snippet does not remove its saved status. Clear temporary history preserves both categories.

History supports up to 2,000 entries and 12,000 characters per entry, with a 64 MiB encrypted-snapshot limit. At capacity, new content is rejected with a storage message instead of evicting unexpired older content. Legacy pinned entries migrate only after a successful encrypted write.

## Selected text and privacy

When the focused app has selected text, the header's copy-selection action adds it to encrypted history and also copies it to Android. KeySwiper-generated system clips are marked sensitive to suppress system preview. Clips already marked sensitive by their source are excluded from automatic capture.

Automatic capture is enabled only during ordinary keyboard sessions. Password fields, no-personalized-learning fields and the manual private mode block capture and history access. No cloud clipboard synchronization is implemented.

Native checks verify restart survival, exact deadlines, independent pins/save, more than eighty retained entries, deleted-copy suppression, authenticated tamper rejection and sensitive/private capture.
