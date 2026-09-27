package cloud.kosch.keyswiper.stylus

enum class StylusTrigger(
    val storageKey: String,
    val label: String
) {
    PRIMARY_SINGLE(
        "primary_single",
        "Primary button · single click"
    ),
    PRIMARY_DOUBLE(
        "primary_double",
        "Primary button · double click"
    ),
    SECONDARY_SINGLE(
        "secondary_single",
        "Secondary button"
    )
}

enum class StylusAction(
    val label: String
) {
    VOICE_TOGGLE("Voice2Text on/off"),
    ACCEPT_TOP_PREDICTION("Accept top prediction"),
    NEXT_CANDIDATE("Next swipe candidate"),
    PREVIOUS_CANDIDATE("Previous swipe candidate"),
    TRANSLATE_SELECTION("Translate selected text"),
    CLIPBOARD("Open clipboard"),
    EMOJI("Open emoji panel"),
    HANDWRITING("Open handwriting"),
    UNDO_LAST_SWIPE("Undo last swipe word"),
    SETTINGS("Open KeySwiper settings"),
    NONE("No action");

    companion object {
        fun fromStored(
            raw: String?,
            fallback: StylusAction
        ): StylusAction =
            entries.firstOrNull {
                it.name == raw
            } ?: fallback
    }
}
