package cloud.kosch.keyswiper.prediction

import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class LiteRtLmPredictionBackend(
    private val modelManager: NeuralModelManager
) : NeuralPredictionBackend, AutoCloseable {

    override val id: String = "litert-lm-local"

    private val executor = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        LinkedBlockingQueue()
    ) { runnable ->
        Thread(runnable, "KeySwiper-LiteRT-LM").apply {
            priority = Thread.NORM_PRIORITY - 1
        }
    }

    private val lock = Any()
    private var engine: Engine? = null
    private var enginePath: String? = null

    override fun isReady(): Boolean =
        modelManager.activeModelPath() != null

    override fun predict(
        context: PredictionContext,
        maxSuggestions: Int,
        callback: (List<PredictionSuggestion>) -> Unit
    ) {
        val path = modelManager.activeModelPath()
        if (path == null) {
            callback(emptyList())
            return
        }

        // Keep at most the current running request plus the newest queued request.
        executor.queue.clear()

        executor.execute {
            val result = runCatching {
                val activeEngine = ensureEngine(path)
                val prompt = buildPrompt(context, maxSuggestions)

                synchronized(lock) {
                    activeEngine.createConversation().use { conversation ->
                        val response = conversation.sendMessage(prompt)
                        parseSuggestions(response.toString(), maxSuggestions)
                    }
                }
            }.getOrElse {
                emptyList()
            }

            callback(result)
        }
    }

    private fun ensureEngine(path: String): Engine =
        synchronized(lock) {
            val existing = engine

            if (existing != null && enginePath == path) {
                existing
            } else {
                existing?.close()

                val created = Engine(
                    EngineConfig(
                        modelPath = path
                    )
                )
                created.initialize()

                engine = created
                enginePath = path
                created
            }
        }

    private fun buildPrompt(
        context: PredictionContext,
        maxSuggestions: Int
    ): String {
        val snapshot = context.surrounding

        return buildString {
            appendLine("Task: predict short text continuations for an Android keyboard.")
            appendLine("Return exactly ${maxSuggestions.coerceIn(1, 4)} alternatives, one per line.")
            appendLine("No numbering, no explanations, no quotation marks.")
            appendLine("Each alternative must be at most ${context.maxSemanticTokens.coerceIn(2, 6)} words.")
            appendLine("Match the user's language, current sentence, topic and tone.")
            appendLine("Use text after the cursor only to maintain coherence; never repeat it.")
            appendLine("Input mode: ${context.inputMode}")
            appendLine("Detected languages: ${context.languageHints.joinToString(",")}")
            appendLine("Previous sentence: ${snapshot.previousSentence}")
            appendLine("Current sentence before cursor: ${snapshot.currentSentenceBefore}")
            appendLine("Selected text: ${snapshot.selectedText}")
            appendLine("Current sentence after cursor: ${snapshot.currentSentenceAfter}")
            appendLine("Next sentence: ${snapshot.nextSentence}")
            appendLine("Current paragraph: ${snapshot.currentParagraph.take(900)}")
            appendLine("Topic terms: ${snapshot.topicTerms.joinToString(", ")}")
            appendLine("Question context: ${snapshot.isQuestion}")
            append("Continue at the cursor:")
        }
    }

    private fun parseSuggestions(
        raw: String,
        maxSuggestions: Int
    ): List<PredictionSuggestion> =
        raw.lines()
            .asSequence()
            .map { line ->
                line.trim()
                    .removePrefix("-")
                    .trim()
                    .replace(Regex("^\\d+[.)]\\s*"), "")
                    .trim('"', '\'', ' ')
            }
            .filter { it.isNotBlank() }
            .map { phrase ->
                phrase
                    .split(Regex("\\s+"))
                    .take(6)
                    .joinToString(" ")
                    .trimEnd('.', ',', ';', ':')
            }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .take(maxSuggestions)
            .mapIndexed { index, phrase ->
                PredictionSuggestion(
                    display = "✦ $phrase",
                    commitText = phrase,
                    kind = PredictionKind.NEURAL,
                    confidence = (0.94f - index * 0.08f)
                        .coerceAtLeast(0.55f)
                )
            }
            .toList()

    override fun close() {
        executor.queue.clear()
        executor.shutdownNow()

        synchronized(lock) {
            engine?.close()
            engine = null
            enginePath = null
        }
    }
}
