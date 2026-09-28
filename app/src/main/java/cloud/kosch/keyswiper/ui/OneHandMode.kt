package cloud.kosch.keyswiper.ui

enum class OneHandMode(
    val label: String
) {
    OFF("Off · full width"),
    LEFT("Left hand"),
    RIGHT("Right hand");

    fun next(): OneHandMode =
        when (this) {
            OFF -> LEFT
            LEFT -> RIGHT
            RIGHT -> OFF
        }
}
