package cloud.kosch.keyswiper.handwriting

import android.annotation.TargetApi
import android.graphics.Matrix
import android.graphics.RectF
import android.text.Layout
import android.view.inputmethod.InputConnection
import java.util.concurrent.Executor

/** Uses only editor-provided geometry; never injects touches into other apps. */
@TargetApi(34)
object StylusEditorBridge {
    fun select(connection: InputConnection, area: RectF, cursor: Boolean,
        executor: Executor, stillCurrent: () -> Boolean, onResult: (Boolean) -> Unit) {
        connection.requestTextBoundsInfo(area, executor) { result ->
            if (!stillCurrent()) return@requestTextBoundsInfo
            val info = result.textBoundsInfo
            if (info == null) { onResult(false); return@requestTextBoundsInfo }
            val matrix = Matrix().also { info.getMatrix(it) }
            val inverse = Matrix()
            if (!matrix.invert(inverse)) { onResult(false); return@requestTextBoundsInfo }
            val local = RectF(area).also { inverse.mapRect(it) }
            if (cursor) {
                val offset = info.getOffsetForPosition(local.centerX(), local.centerY())
                onResult(offset >= 0 && connection.setSelection(offset, offset))
            } else {
                val range = info.getRangeForRect(local, info.wordSegmentFinder, Layout.INCLUSION_STRATEGY_CONTAINS_CENTER)
                onResult(range != null && connection.setSelection(range[0], range[1]))
            }
        }
    }
}
