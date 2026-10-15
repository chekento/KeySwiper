package cloud.kosch.keyswiper.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.util.Log
import cloud.kosch.keyswiper.clipboard.ClipboardController
import cloud.kosch.keyswiper.clipboard.EncryptedClipboardStore

object ClipboardRegressionChecks {
    fun runAsync(context: Context, onComplete: () -> Unit) {
        Thread {
            try { run(context); Handler(Looper.getMainLooper()).post { onComplete() } }
            catch (error: Throwable) { Handler(Looper.getMainLooper()).post { throw error } }
        }.start()
    }
    private fun run(context: Context) {
        val store = EncryptedClipboardStore(context, "clipboard-regression.enc", "keyswiper.clipboard.regression")
        store.file.delete()
        var now = System.currentTimeMillis()
        fun controller() = ClipboardController(context, store, { now }).apply {
            setCaptureAllowed(false); setDefaultExpiryMinutes(10)
        }
        val first = controller()
        first.add("Temporary retention probe")
        val temporary = first.items().single()
        val deadline = checkNotNull(temporary.expiresAtMs)
        now += 9 * 60_000
        val restarted = controller()
        check(restarted.items().single().expiresAtMs == deadline) { "Restart must not renew temporary retention" }
        restarted.togglePin(temporary.id)
        restarted.save(null, "Permanent encrypted probe")
        val saved = restarted.items().first { it.saved }
        restarted.togglePin(saved.id)
        restarted.togglePin(saved.id)
        check(restarted.items().first { it.id == saved.id }.saved)
        check(restarted.items().first { it.id == saved.id }.expiresAtMs == null)
        now += 31L * 24 * 60 * 60_000
        val durable = controller()
        check(durable.items().any { it.id == temporary.id && it.pinned })
        check(durable.items().any { it.id == saved.id && it.saved })
        repeat(90) { durable.add("Retained history item " + it) }
        check(controller().items().size == 92) { "Unexpired history must not disappear after eighty clips or restart" }
        durable.clearUnpinned()
        check(controller().items().size == 2) { "Clearing temporary history must preserve pins and saved snippets" }
        val encoded = store.file.readBytes()
        check(!String(encoded, Charsets.ISO_8859_1).contains("Permanent encrypted probe"))
        store.write(store.read())
        check(!encoded.contentEquals(store.file.readBytes())) { "Each encrypted snapshot must use a fresh nonce" }
        val valid = store.file.readBytes()
        val tampered = valid.copyOf()
        tampered[tampered.lastIndex] = (tampered.last().toInt() xor 1).toByte()
        store.file.writeBytes(tampered)
        check(runCatching { store.read() }.isFailure) { "Authenticated storage must reject tampered ciphertext" }
        check(store.file.readBytes().contentEquals(tampered)) { "A failed read must not overwrite the stored file" }
        store.file.writeBytes(valid)

        now = System.currentTimeMillis()
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText("check", "Deleted copy restart probe"))
        val captures = controller().apply { setCaptureAllowed(true) }
        val captured = captures.items().first { it.text == "Deleted copy restart probe" }
        captures.delete(captured.id)
        val afterDelete = controller().apply { setCaptureAllowed(true) }
        check(afterDelete.items().none { it.id == captured.id }) { "Deleted system clips must stay deleted after restart" }
        check(captures.undoDelete())
        check(captures.items().first { it.id == captured.id }.expiresAtMs == captured.expiresAtMs)
        now += 11 * 60_000
        check(controller().apply { setCaptureAllowed(true) }.items().none { it.id == captured.id }) {
            "An expired current system clip must not be resurrected after restart"
        }
        val sensitive = ClipData.newPlainText("private", "Sensitive clipboard probe")
        sensitive.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        manager.setPrimaryClip(sensitive)
        check(afterDelete.items().none { it.text == "Sensitive clipboard probe" }) {
            "Sensitive clips must not be captured automatically"
        }
        check(afterDelete.importSystemClipboard()) { "Explicit system import must accept the selected clip" }
        check(afterDelete.items().any { it.text == "Sensitive clipboard probe" }) {
            "Imported sensitive text should be available in encrypted history"
        }
        afterDelete.setCaptureAllowed(false)
        check(!afterDelete.copySelection("Blocked private selection"))
        if (android.os.Build.VERSION.SDK_INT >= 28) manager.clearPrimaryClip()
        else manager.setPrimaryClip(ClipData.newPlainText("", ""))
        store.file.delete()
        Log.i("KeySwiperInputChecks", "PASS: encrypted clipboard restart/retention, pins/save, capacity, tamper detection and private capture")
    }
}
