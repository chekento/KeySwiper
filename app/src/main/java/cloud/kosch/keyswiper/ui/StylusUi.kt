package cloud.kosch.keyswiper.ui

import android.os.Build
import android.view.View
import android.view.ViewGroup

/** Keyboard controls must never ask Android to convert pen taps into handwriting. */
object StylusUi {
    fun usePointerInput(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            view.setAutoHandwritingEnabled(false)
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                usePointerInput(view.getChildAt(index))
            }
        }
    }
}
