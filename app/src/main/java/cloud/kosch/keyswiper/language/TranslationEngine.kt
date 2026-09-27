package cloud.kosch.keyswiper.language

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentifier
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions

class TranslationEngine {
    private val identifier: LanguageIdentifier = LanguageIdentification.getClient()

    fun identifyLikelyLanguages(
        text: String,
        callback: (List<String>) -> Unit
    ) {
        if (text.trim().length < 8) {
            callback(emptyList())
            return
        }

        identifier.identifyPossibleLanguages(text.takeLast(400))
            .addOnSuccessListener { identified ->
                callback(
                    identified.asSequence()
                        .filter { it.languageTag != "und" && it.confidence >= 0.10f }
                        .sortedByDescending { it.confidence }
                        .map { it.languageTag.substringBefore('-').lowercase() }
                        .distinct()
                        .take(3)
                        .toList()
                )
            }
            .addOnFailureListener { callback(emptyList()) }
    }

    fun translate(
        text: String,
        targetLanguageTag: String,
        callback: (Result<String>) -> Unit
    ) {
        if (text.isBlank()) {
            callback(Result.failure(IllegalArgumentException("Nothing selected to translate.")))
            return
        }

        identifier.identifyLanguage(text)
            .addOnSuccessListener { detected ->
                val sourceTag = detected.takeUnless { it == "und" }
                    ?: run {
                        callback(Result.failure(IllegalStateException("Source language could not be identified.")))
                        return@addOnSuccessListener
                    }

                val source = TranslateLanguage.fromLanguageTag(sourceTag)
                    ?: TranslateLanguage.fromLanguageTag(sourceTag.substringBefore('-'))
                val target = TranslateLanguage.fromLanguageTag(targetLanguageTag)
                    ?: TranslateLanguage.fromLanguageTag(targetLanguageTag.substringBefore('-'))

                if (source == null || target == null) {
                    callback(Result.failure(UnsupportedOperationException(
                        "This language pair is not available in the local ML Kit translator."
                    )))
                    return@addOnSuccessListener
                }

                if (source == target) {
                    callback(Result.success(text))
                    return@addOnSuccessListener
                }

                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(source)
                    .setTargetLanguage(target)
                    .build()
                val translator = Translation.getClient(options)

                translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
                    .addOnSuccessListener {
                        translator.translate(text)
                            .addOnSuccessListener { translated ->
                                translator.close()
                                callback(Result.success(translated))
                            }
                            .addOnFailureListener { error ->
                                translator.close()
                                callback(Result.failure(error))
                            }
                    }
                    .addOnFailureListener { error ->
                        translator.close()
                        callback(Result.failure(error))
                    }
            }
            .addOnFailureListener { callback(Result.failure(it)) }
    }

    fun close() {
        identifier.close()
    }
}
