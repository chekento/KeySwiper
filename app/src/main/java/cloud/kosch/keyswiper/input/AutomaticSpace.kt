package cloud.kosch.keyswiper.input

data class AutomaticSpace(val before: String, val after: String) {
    fun matches(left: String, right: String, selected: String): Boolean =
        selected.isEmpty() && left.endsWith(" ") && left.takeLast(256) == before && right.take(64) == after
    companion object {
        fun capture(before: String, after: String): AutomaticSpace? =
            if (before.endsWith(" ")) AutomaticSpace(before.takeLast(256), after.take(64)) else null
        fun isPunctuation(value: String): Boolean = value.isNotEmpty() && value.all { it in ".,;:!?…，。！？：；" }
    }
}
