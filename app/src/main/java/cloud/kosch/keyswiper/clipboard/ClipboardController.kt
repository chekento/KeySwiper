package cloud.kosch.keyswiper.clipboard

import android.content.ClipboardManager
import android.content.Context
import android.util.Base64
import java.security.MessageDigest

enum class ClipboardCategory(
    val label: String
) {
    LINK("Link"),
    EMAIL("Email"),
    PHONE("Phone"),
    ADDRESS("Address"),
    CODE("Code"),
    TEXT("Text")
}

data class ClipboardEntry(
    val id: String,
    val text: String,
    val category: ClipboardCategory,
    val createdAtMs: Long,
    val expiresAtMs: Long?,
    val pinned: Boolean
)

class ClipboardController(
    private val context: Context
) {
    private val clipboard =
        context.getSystemService(
            Context.CLIPBOARD_SERVICE
        ) as ClipboardManager

    private val preferences =
        context.getSharedPreferences(
            "keyswiper_clipboard",
            Context.MODE_PRIVATE
        )

    private val history =
        mutableListOf<ClipboardEntry>()

    private var defaultExpiryMinutes =
        60L

    private val listener =
        ClipboardManager.OnPrimaryClipChangedListener {
            capturePrimary()
        }

    init {
        loadPinned()
    }

    fun start() {
        clipboard.addPrimaryClipChangedListener(
            listener
        )
        capturePrimary()
    }

    fun stop() {
        clipboard.removePrimaryClipChangedListener(
            listener
        )
    }

    fun setDefaultExpiryMinutes(
        minutes: Long
    ) {
        defaultExpiryMinutes =
            minutes.coerceIn(
                10L,
                1440L
            )
    }

    fun items(
        query: String = ""
    ): List<ClipboardEntry> {
        capturePrimary()
        purgeExpired()

        val normalized =
            query.trim().lowercase()

        return history
            .asSequence()
            .filter {
                normalized.isBlank() ||
                    it.text
                        .lowercase()
                        .contains(normalized) ||
                    it.category
                        .label
                        .lowercase()
                        .contains(normalized)
            }
            .sortedWith(
                compareByDescending<ClipboardEntry> {
                    it.pinned
                }.thenByDescending {
                    it.createdAtMs
                }
            )
            .toList()
    }

    fun togglePin(
        id: String
    ): Boolean {
        purgeExpired()

        val index =
            history.indexOfFirst {
                it.id == id
            }

        if (index < 0) return false

        val current =
            history[index]

        history[index] =
            current.copy(
                pinned = !current.pinned,
                expiresAtMs =
                    if (current.pinned) {
                        System.currentTimeMillis() +
                            defaultExpiryMinutes *
                            60_000L
                    } else {
                        null
                    }
            )

        persistPinned()
        return true
    }

    fun delete(
        id: String
    ): Boolean {
        val removed =
            history.removeAll {
                it.id == id
            }

        if (removed) {
            persistPinned()
        }

        return removed
    }

    fun clearUnpinned() {
        history.removeAll {
            !it.pinned
        }
    }

    fun textFor(
        id: String
    ): String? {
        purgeExpired()

        return history
            .firstOrNull {
                it.id == id
            }
            ?.text
    }

    private fun capturePrimary() {
        purgeExpired()

        val clip =
            try {
                clipboard.primaryClip
            } catch (_: SecurityException) {
                null
            } ?: return

        if (clip.itemCount == 0) {
            return
        }

        val value =
            clip.getItemAt(0)
                .coerceToText(context)
                ?.toString()
                ?.trim()
                .orEmpty()

        if (value.isBlank()) return

        val text =
            value.take(
                MAX_ENTRY_CHARS
            )

        val id =
            stableId(text)

        val existing =
            history.firstOrNull {
                it.id == id
            }

        history.removeAll {
            it.id == id
        }

        val now =
            System.currentTimeMillis()

        history.add(
            0,
            ClipboardEntry(
                id = id,
                text = text,
                category =
                    ClipboardTextClassifier
                        .category(text),
                createdAtMs =
                    existing
                        ?.createdAtMs
                        ?: now,
                expiresAtMs =
                    if (
                        existing?.pinned ==
                        true
                    ) {
                        null
                    } else {
                        now +
                            defaultExpiryMinutes *
                            60_000L
                    },
                pinned =
                    existing?.pinned
                        ?: false
            )
        )

        trimHistory()
    }

    private fun purgeExpired() {
        val now =
            System.currentTimeMillis()

        history.removeAll {
            !it.pinned &&
                it.expiresAtMs != null &&
                it.expiresAtMs <= now
        }
    }

    private fun trimHistory() {
        if (
            history.size <=
            MAX_ENTRIES
        ) {
            return
        }

        val removable =
            history
                .withIndex()
                .filter {
                    !it.value.pinned
                }
                .map {
                    it.index
                }
                .sortedDescending()

        var size =
            history.size

        for (index in removable) {
            if (
                size <=
                MAX_ENTRIES
            ) {
                break
            }

            history.removeAt(index)
            size--
        }
    }

    private fun stableId(
        text: String
    ): String {
        val digest =
            MessageDigest
                .getInstance("SHA-256")
                .digest(
                    text.toByteArray()
                )

        return digest
            .take(10)
            .joinToString("") {
                "%02x".format(it)
            }
    }

    private fun loadPinned() {
        val encoded =
            preferences
                .getStringSet(
                    KEY_PINNED,
                    emptySet()
                )
                .orEmpty()

        encoded.forEach {
            decodePinned(it)
                ?.let(history::add)
        }
    }

    private fun persistPinned() {
        val encoded =
            history
                .filter {
                    it.pinned
                }
                .map {
                    encodePinned(it)
                }
                .toSet()

        preferences
            .edit()
            .putStringSet(
                KEY_PINNED,
                encoded
            )
            .apply()
    }

    private fun encodePinned(
        entry: ClipboardEntry
    ): String {
        val payload =
            Base64.encodeToString(
                entry.text.toByteArray(),
                Base64.NO_WRAP or
                    Base64.URL_SAFE
            )

        return listOf(
            entry.id,
            entry.createdAtMs
                .toString(),
            entry.category.name,
            payload
        ).joinToString("|")
    }

    private fun decodePinned(
        raw: String
    ): ClipboardEntry? =
        runCatching {
            val parts =
                raw.split(
                    "|",
                    limit = 4
                )

            require(
                parts.size == 4
            )

            val text =
                String(
                    Base64.decode(
                        parts[3],
                        Base64.NO_WRAP or
                            Base64.URL_SAFE
                    )
                )

            ClipboardEntry(
                id = parts[0],
                text = text,
                category =
                    ClipboardCategory
                        .valueOf(
                            parts[2]
                        ),
                createdAtMs =
                    parts[1]
                        .toLong(),
                expiresAtMs = null,
                pinned = true
            )
        }.getOrNull()

    companion object {
        private const val KEY_PINNED =
            "pinned_entries"

        private const val MAX_ENTRIES =
            40

        private const val MAX_ENTRY_CHARS =
            12_000
    }
}
