package cloud.kosch.keyswiper.test

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

class ImeSmokeHostActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )

        val input = EditText(this).apply {
            hint = "Type to test KeySwiper"
            setSingleLine(true)
        }

        setContentView(input)
        input.requestFocus()
        input.postDelayed(
            {
                getSystemService(InputMethodManager::class.java)
                    ?.showSoftInput(
                        input,
                        InputMethodManager.SHOW_IMPLICIT
                    )
            },
            350L
        )
    }
}
