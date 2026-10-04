package cloud.kosch.keyswiper.input

data class CursorWord(val prefix: String, val suffix: String) {
    val whole: String get() = prefix + suffix
    companion object {
        fun isWord(c: Char) = c.isLetterOrDigit() || c in "'-’" || Character.getType(c) == Character.NON_SPACING_MARK.toInt()
        fun at(before: String, after: String) = CursorWord(before.takeLastWhile(::isWord), after.takeWhile(::isWord))
    }
}
