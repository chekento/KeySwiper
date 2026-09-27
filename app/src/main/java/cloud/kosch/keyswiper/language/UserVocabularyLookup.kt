package cloud.kosch.keyswiper.language

interface UserVocabularyLookup {
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
