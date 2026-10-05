package cloud.kosch.keyswiper.language

interface UserVocabularyLookup {
    fun contains(word: String): Boolean = false
    fun isExplicit(word: String): Boolean = false

    fun prefixMatches(
        prefix: String,
        lanes: List<LanguageLane>,
        limit: Int = 8
    ): List<Pair<String, Int>>

    fun frequentWords(
        lanes: List<LanguageLane>,
        limit: Int = 10
    ): List<Pair<String, Int>>
}

