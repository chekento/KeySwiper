package cloud.kosch.keyswiper.debug

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout

class ImeSmokeHostActivity : Activity() {

    private lateinit var input: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)

        input = EditText(this).apply {
            id = View.generateViewId()
            hint = "KeySwiper IME smoke test"
            setSingleLine(false)
            minLines = 4
            isFocusableInTouchMode = true
        }

        val density = resources.displayMetrics.density
        val padding = (24 * density).toInt()
        val root = FrameLayout(this).apply {
            setPadding(padding, padding, padding, padding)
            addView(
                input,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        setContentView(root)
        input.requestFocus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return

        input.postDelayed(
            {
                getSystemService(InputMethodManager::class.java)
                    ?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            },
            450L
        )
    }
}
