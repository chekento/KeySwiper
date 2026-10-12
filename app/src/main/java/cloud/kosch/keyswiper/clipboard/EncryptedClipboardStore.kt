package cloud.kosch.keyswiper.clipboard

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Authenticated encrypted snapshots; no plaintext fallback or cloud backup. */
class EncryptedClipboardStore(context: Context, fileName: String = "clipboard-history-v1.enc",
    private val alias: String = "keyswiper.clipboard.v1") {
    val file: File = File(context.noBackupFilesDir, fileName)
    private val atomic = AtomicFile(file)
    val exists: Boolean get() = file.exists() || File(file.path + ".bak").exists()
    fun read(): ClipboardSnapshot {
        if (!exists) return ClipboardSnapshot()
        require(file.length() <= 64L * 1024 * 1024) { "Clipboard file exceeds the storage limit" }
        val bytes = atomic.readFully()
        require(bytes.size >= 30 && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) { "Invalid clipboard file" }
        val ivLength = bytes[MAGIC.size].toInt() and 0xff
        require(ivLength == 12 && bytes.size > MAGIC.size + 1 + ivLength) { "Invalid clipboard nonce" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128,
            bytes.copyOfRange(MAGIC.size + 1, MAGIC.size + 1 + ivLength)))
        cipher.updateAAD(MAGIC)
        val json = JSONObject(String(cipher.doFinal(bytes.copyOfRange(MAGIC.size + 1 + ivLength, bytes.size)), Charsets.UTF_8))
        require(json.getInt("version") == 1)
        val entries = json.getJSONArray("entries")
        val decoded = (0 until entries.length()).map { index ->
            val item = entries.getJSONObject(index)
            ClipboardEntry(item.getString("id"), item.getString("text"),
                ClipboardCategory.valueOf(item.getString("category")), item.getLong("created"),
                if (item.isNull("expires")) null else item.getLong("expires"),
                item.getBoolean("pinned"), item.optBoolean("saved", false))
        }
        return ClipboardSnapshot(decoded, if (json.isNull("lastId")) null else json.getString("lastId"),
            json.optLong("lastTimestamp", 0))
    }
    fun write(snapshot: ClipboardSnapshot) {
        val entries = JSONArray()
        snapshot.entries.forEach { entry ->
            entries.put(JSONObject().put("id", entry.id).put("text", entry.text)
                .put("category", entry.category.name).put("created", entry.createdAtMs)
                .put("expires", entry.expiresAtMs ?: JSONObject.NULL)
                .put("pinned", entry.pinned).put("saved", entry.saved))
        }
        val json = JSONObject().put("version", 1).put("entries", entries)
            .put("lastId", snapshot.lastCapturedId ?: JSONObject.NULL).put("lastTimestamp", snapshot.lastClipTimestamp)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(MAGIC)
        val plaintext = json.toString().toByteArray(Charsets.UTF_8)
        require(plaintext.size < 64 * 1024 * 1024 - 64) { "Speicherlimit erreicht. Bestehende Texte bleiben erhalten." }
        val bytes = MAGIC + byteArrayOf(cipher.iv.size.toByte()) + cipher.iv + cipher.doFinal(plaintext)
        file.parentFile?.mkdirs()
        val stream = atomic.startWrite()
        try { stream.write(bytes); atomic.finishWrite(stream) }
        catch (error: Exception) { atomic.failWrite(stream); throw error }
    }
    private fun key(): SecretKey = synchronized(KEY_LOCK) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).setKeySize(256).build())
        }.generateKey()
    }
    companion object {
        private val KEY_LOCK = Any()
        private val MAGIC = "KSCB1".toByteArray(Charsets.US_ASCII)
    }
}
