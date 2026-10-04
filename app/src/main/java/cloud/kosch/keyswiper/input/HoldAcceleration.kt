package cloud.kosch.keyswiper.input

enum class DeleteUnit { CHARACTER, WORD, WORDS, SENTENCE, PARAGRAPH }
data class HoldStep(val unit: DeleteUnit, val spaces: Int, val intervalMs: Long)

object HoldAcceleration {
    fun step(elapsedMs: Long): HoldStep = when {
        elapsedMs < 1500 -> HoldStep(DeleteUnit.CHARACTER, 1, 170)
        elapsedMs < 3500 -> HoldStep(DeleteUnit.WORD, 1, 240)
        elapsedMs < 6000 -> HoldStep(DeleteUnit.WORDS, 2, 200)
        elapsedMs < 9500 -> HoldStep(DeleteUnit.SENTENCE, 4, 240)
        else -> HoldStep(DeleteUnit.PARAGRAPH, 8, 200)
    }

    fun deleteLength(before: String, unit: DeleteUnit): Int {
        if (before.isEmpty()) return 0
        if (unit == DeleteUnit.CHARACTER) return TextBoundaryUtils.lastGraphemeUtf16Length(before)
        var end = before.length
        fun word() {
            while (end > 0 && before[end - 1].isWhitespace()) end--
            while (end > 0 && !before[end - 1].isLetterOrDigit() && !before[end - 1].isWhitespace()) end--
            while (end > 0 && (before[end - 1].isLetterOrDigit() || before[end - 1] in "'-’")) end--
        }
        when (unit) {
            DeleteUnit.WORD -> word()
            DeleteUnit.WORDS -> repeat(3) { word() }
            DeleteUnit.SENTENCE -> {
                while (end > 0 && (before[end - 1].isWhitespace() || before[end - 1] in ".!?…")) end--
                while (end > 0 && before[end - 1] !in ".!?…\n") end--
            }
            DeleteUnit.PARAGRAPH -> {
                while (end > 0 && before[end - 1].isWhitespace()) end--
                while (end > 0 && before[end - 1] != '\n') end--
            }
            else -> Unit
        }
        return (before.length - end).coerceAtLeast(TextBoundaryUtils.lastGraphemeUtf16Length(before))
    }
}
