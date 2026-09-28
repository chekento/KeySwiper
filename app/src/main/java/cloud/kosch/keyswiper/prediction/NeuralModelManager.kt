package cloud.kosch.keyswiper.prediction

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

data class NeuralModelInfo(
    val file: File,
    val sizeBytes: Long,
    val sha256: String,
    val importedAtMs: Long
)

sealed class NeuralModelStatus {
    data object NotInstalled : NeuralModelStatus()
    data class Ready(val info: NeuralModelInfo) : NeuralModelStatus()
    data class Invalid(val reason: String) : NeuralModelStatus()
}

class NeuralModelManager(private val context: Context) {

    private val prefs =
        context.getSharedPreferences("keyswiper_neural_model", Context.MODE_PRIVATE)

    private val modelDir: File
        get() = File(context.filesDir, "neural_models")

    private val activeFile: File
        get() = File(modelDir, "active-model.litertlm")

    fun status(verifyHash: Boolean = false): NeuralModelStatus {
        val file = activeFile
        if (!file.exists()) return NeuralModelStatus.NotInstalled
        if (file.length() < 1_000_000L) {
            return NeuralModelStatus.Invalid("Model file is unexpectedly small.")
        }

        val expected = prefs.getString(KEY_SHA256, null)
        val sha = when {
            expected.isNullOrBlank() -> sha256(file)
            verifyHash -> sha256(file)
            else -> expected
        }

        if (!expected.isNullOrBlank() && verifyHash && sha != expected) {
            return NeuralModelStatus.Invalid("SHA-256 verification failed.")
        }

        val info = NeuralModelInfo(
            file = file,
            sizeBytes = file.length(),
            sha256 = sha,
            importedAtMs = prefs.getLong(KEY_IMPORTED_AT, file.lastModified())
        )

        return NeuralModelStatus.Ready(info)
    }

    fun activeModelPath(): String? =
        (status(verifyHash = false) as? NeuralModelStatus.Ready)
            ?.info
            ?.file
            ?.absolutePath

    fun installFromUri(uri: Uri): Result<NeuralModelInfo> =
        runCatching {
            modelDir.mkdirs()
            val temp = File(modelDir, "import-${System.currentTimeMillis()}.tmp")
            val digest = MessageDigest.getInstance("SHA-256")

            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Could not open selected model." }

                temp.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
                    var total = 0L

                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue

                        total += count
                        require(total <= MAX_MODEL_BYTES) {
                            "Model exceeds the 12 GB safety limit."
                        }

                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            }

            require(temp.length() >= 1_000_000L) {
                "Selected file is too small to be a usable LiteRT-LM model."
            }

            val sha = digest.digest().joinToString("") { "%02x".format(it) }

            if (activeFile.exists() && !activeFile.delete()) {
                temp.delete()
                error("Could not replace the existing model.")
            }

            if (!temp.renameTo(activeFile)) {
                temp.copyTo(activeFile, overwrite = true)
                temp.delete()
            }

            prefs.edit()
                .putString(KEY_SHA256, sha)
                .putLong(KEY_SIZE, activeFile.length())
                .putLong(KEY_IMPORTED_AT, System.currentTimeMillis())
                .apply()

            NeuralModelInfo(
                file = activeFile,
                sizeBytes = activeFile.length(),
                sha256 = sha,
                importedAtMs = prefs.getLong(KEY_IMPORTED_AT, 0L)
            )
        }

    fun verify(): Result<NeuralModelInfo> =
        runCatching {
            when (val state = status(verifyHash = true)) {
                is NeuralModelStatus.Ready -> state.info
                is NeuralModelStatus.Invalid -> error(state.reason)
                NeuralModelStatus.NotInstalled -> error("No neural model is installed.")
            }
        }

    fun remove(): Boolean {
        val deleted = !activeFile.exists() || activeFile.delete()
        if (deleted) {
            prefs.edit().clear().apply()
        }
        return deleted
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")

        FileInputStream(file).buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }

        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val KEY_SHA256 = "sha256"
        private const val KEY_SIZE = "size"
        private const val KEY_IMPORTED_AT = "imported_at"
        private const val MAX_MODEL_BYTES = 12L * 1024L * 1024L * 1024L
    }
}
