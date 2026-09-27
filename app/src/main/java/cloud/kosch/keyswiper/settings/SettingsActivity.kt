package cloud.kosch.keyswiper.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import cloud.kosch.keyswiper.input.MotorProfileStore
import cloud.kosch.keyswiper.input.SwipeLearningStore

class SettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(28), dp(24), dp(32))
        }

        content.addView(TextView(this).apply {
            text = "KeySwiper"
            textSize = 30f
            setTextColor(Color.rgb(22, 24, 30))
        })
        content.addView(TextView(this).apply {
            text = "Adaptive multimodal Android keyboard — swipe, stylus, voice, clipboard, emoji and multilingual input."
            textSize = 16f
            setPadding(0, dp(8), 0, dp(22))
        })

        content.addView(Button(this).apply {
            text = "1. Enable KeySwiper"
            setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        })
        content.addView(Button(this).apply {
            text = "2. Choose KeySwiper"
            setOnClickListener {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showInputMethodPicker()
            }
        })
        content.addView(Button(this).apply {
            text = if (hasMicPermission()) "Microphone permission granted" else "Grant microphone permission"
            setOnClickListener {
                if (!hasMicPermission()) requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 70)
            }
        })

        content.addView(TextView(this).apply {
            text = "Translation target (BCP-47 / ISO language tag)"
            textSize = 14f
            setPadding(0, dp(24), 0, dp(6))
        })
        val targetLanguage = EditText(this).apply {
            setText(Prefs.targetLanguage(this@SettingsActivity))
            hint = "de, en, it, fr, es …"
            maxLines = 1
        }
        content.addView(targetLanguage, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        content.addView(TextView(this).apply {
            text = "Handwriting model language"
            textSize = 14f
            setPadding(0, dp(18), 0, dp(6))
        })
        val handwritingLanguage = EditText(this).apply {
            setText(Prefs.handwritingLanguage(this@SettingsActivity))
            hint = "de-DE, en-US, ja-JP …"
            maxLines = 1
        }
        content.addView(handwritingLanguage, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        content.addView(Button(this).apply {
            text = "Save language settings"
            setOnClickListener {
                Prefs.setTargetLanguage(this@SettingsActivity, targetLanguage.text.toString())
                Prefs.setHandwritingLanguage(this@SettingsActivity, handwritingLanguage.text.toString())
                text = "Saved ✓"
            }
        })

        content.addView(TextView(this).apply {
            text = "Swipe v3 uses Google ML Kit language identification plus swipe geometry, speed, direction and a local per-key motor profile."
            textSize = 14f
            setPadding(0, dp(24), 0, dp(8))
        })

        content.addView(Button(this).apply {
            text = "Reset adaptive swipe profile"
            setOnClickListener {
                SwipeLearningStore(this@SettingsActivity).reset()
                MotorProfileStore(this@SettingsActivity).reset()
                text = "Adaptive swipe profile reset ✓"
            }
        })

        content.addView(TextView(this).apply {
            text = "Privacy default: normal typing is local. Voice, clipboard and translation actions are explicit. Learning and transformation tools are disabled for sensitive/password fields."
            textSize = 14f
            setPadding(0, dp(24), 0, 0)
        })

        setContentView(ScrollView(this).apply { addView(content) })
    }

    private fun hasMicPermission(): Boolean =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
}
