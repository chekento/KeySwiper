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
    private var imeRequestGeneration = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.getBooleanExtra("run_input_checks", false)) {
            InputRegressionChecks.run(this)
        }

        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        )

        input = EditText(this).apply {
            id = View.generateViewId()
            hint = "KeySwiper IME smoke test"
            setSingleLine(false)
            minLines = 4
            isFocusable = true
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
        if (intent.getBooleanExtra("run_input_checks", false)) {
            root.post { InputRegressionChecks.runAttached(root) { input.requestFocus(); requestImeRepeatedly() } }
        }
    }

    override fun onResume() {
        super.onResume()
        requestImeRepeatedly()
    }

    override fun onPause() {
        imeRequestGeneration++
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (hasFocus) {
            requestImeRepeatedly()
        } else {
            imeRequestGeneration++
        }
    }

    private fun requestImeRepeatedly() {
        val generation = ++imeRequestGeneration

        repeat(8) { attempt ->
            input.postDelayed(
                {
                    if (
                        generation != imeRequestGeneration ||
                        !hasWindowFocus()
                    ) {
                        return@postDelayed
                    }

                    input.requestFocus()
                    getSystemService(InputMethodManager::class.java)
                        ?.showSoftInput(
                            input,
                            InputMethodManager.SHOW_IMPLICIT
                        )
                },
                250L + attempt * 500L
            )
        }
    }
}
