package cloud.kosch.keyswiper.prediction

object LocalContextAnalyzer {

    private val sentenceBoundary = Regex("(?<=[.!?])\\s+|\\n+")
    private val wordRegex = Regex("[\\p{L}\\p{N}'-]+")

    private val stopWords = setOf(
        // German
        "aber","auch","das","dass","der","die","ein","eine","einer","einen","einem",
        "eines","es","für","hat","ich","ist","mit","nicht","oder","sich","sie","sind",
        "und","von","war","was","wie","wir","zu","zum","zur",
        // English
        "about","and","are","because","for","from","have","into","not","that","the",
        "their","there","they","this","was","were","what","when","with","would","you",
        // Romance common
        "avec","dans","des","est","les","mais","pour","que","une",
        "anche","che","con","non","per","una",
        "como","con","del","los","para","pero","por","que","una"
    )

    private val questionStarts = setOf(
        "wer","wie","was","wann","wo","warum","wieso","weshalb","welche","welcher",
        "who","how","what","when","where","why","which",
        "chi","come","cosa","quando","dove","perché",
        "qui","comment","quoi","quand","où","pourquoi",
        "quién","cómo","qué","cuándo","dónde","porqué","por qué"
    )

    fun analyze(
        beforeCursor: String,
        selectedText: String,
        afterCursor: String
    ): SurroundingContextSnapshot {
        val safeBefore = beforeCursor.takeLast(1600)
        val safeAfter = afterCursor.take(600)
        val safeSelection = selectedText.take(400)

        val beforeSentences = sentenceBoundary
            .split(safeBefore)
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val afterSentences = sentenceBoundary
            .split(safeAfter)
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val currentBefore = currentSentenceBefore(safeBefore)
        val currentAfter = currentSentenceAfter(safeAfter)

        val previous = beforeSentences
            .dropLast(if (currentBefore.isBlank()) 0 else 1)
            .lastOrNull()
            .orEmpty()

        val next = afterSentences
            .drop(if (currentAfter.isBlank()) 0 else 1)
            .firstOrNull()
            .orEmpty()

        val paragraphBefore = safeBefore
            .substringAfterLast("\n\n")
            .substringAfterLast("\r\n\r\n")
        val paragraphAfter = safeAfter
            .substringBefore("\n\n")
            .substringBefore("\r\n\r\n")

        val paragraph = (paragraphBefore + safeSelection + paragraphAfter)
            .trim()
            .take(1800)

        val topicTerms = extractTopics(
            listOf(
                previous,
                currentBefore,
                safeSelection,
                currentAfter,
                next,
                paragraph
            ).joinToString(" ")
        )

        val sentenceStart = currentBefore
            .trimStart()
            .lowercase()
            .let { text ->
                questionStarts.any { start ->
                    text == start || text.startsWith("$start ")
                }
            }

        return SurroundingContextSnapshot(
            beforeCursor = safeBefore,
            selectedText = safeSelection,
            afterCursor = safeAfter,
            previousSentence = previous.takeLast(500),
            currentSentenceBefore = currentBefore.takeLast(700),
            currentSentenceAfter = currentAfter.take(400),
            nextSentence = next.take(400),
            currentParagraph = paragraph,
            topicTerms = topicTerms,
            isQuestion = safeBefore.trimEnd().endsWith("?") ||
                safeAfter.trimStart().startsWith("?") ||
                sentenceStart
        )
    }

    private fun currentSentenceBefore(text: String): String {
        val index = maxOf(
            text.lastIndexOf('.'),
            text.lastIndexOf('!'),
            text.lastIndexOf('?'),
            text.lastIndexOf('\n')
        )
        return text.substring((index + 1).coerceAtMost(text.length)).trim()
    }

    private fun currentSentenceAfter(text: String): String {
        val candidates = listOf(
            text.indexOf('.'),
            text.indexOf('!'),
            text.indexOf('?'),
            text.indexOf('\n')
        ).filter { it >= 0 }

        val end = candidates.minOrNull() ?: text.length
        return text.substring(0, end).trim()
    }

    private fun extractTopics(text: String): List<String> {
        val counts = linkedMapOf<String, Int>()

        wordRegex.findAll(text.lowercase()).forEach { match ->
            val word = match.value.trim('-', '\'')
            if (
                word.length >= 4 &&
                word !in stopWords &&
                !word.all { it.isDigit() }
            ) {
                counts[word] = (counts[word] ?: 0) + 1
            }
        }

        return counts.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.value }
                    .thenByDescending { it.key.length }
            )
            .take(10)
            .map { it.key }
    }
}
