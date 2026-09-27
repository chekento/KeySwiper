package cloud.kosch.keyswiper.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import cloud.kosch.keyswiper.input.MotorProfileStore
import cloud.kosch.keyswiper.input.SwipeLearningStore
import cloud.kosch.keyswiper.prediction.NeuralModelManager
import cloud.kosch.keyswiper.prediction.NeuralModelStatus
import cloud.kosch.keyswiper.prediction.PredictionLearningStore

class SettingsActivity : Activity() {

    private lateinit var neuralModelManager: NeuralModelManager
    private lateinit var neuralStatusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        neuralModelManager = NeuralModelManager(this)

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
            text = "Adaptive multimodal Android keyboard — swipe, context-aware hybrid prediction, local neural models, stylus, voice, clipboard and multilingual input."
            textSize = 16f
            setPadding(0, dp(8), 0, dp(22))
        })

        content.addView(Button(this).apply {
            text = "1. Enable KeySwiper"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            }
        })

        content.addView(Button(this).apply {
            text = "2. Choose KeySwiper"
            setOnClickListener {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showInputMethodPicker()
            }
        })

        content.addView(Button(this).apply {
            text = if (hasMicPermission()) {
                "Microphone permission granted"
            } else {
                "Grant microphone permission"
            }

            setOnClickListener {
                if (!hasMicPermission()) {
                    requestPermissions(
                        arrayOf(Manifest.permission.RECORD_AUDIO),
                        70
                    )
                }
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

        content.addView(
            targetLanguage,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

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

        content.addView(
            handwritingLanguage,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        content.addView(TextView(this).apply {
            text = "Semantic prediction depth (2–6 words)"
            textSize = 14f
            setPadding(0, dp(18), 0, dp(6))
        })

        val semanticDepth = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(
                Prefs.semanticPredictionDepth(
                    this@SettingsActivity
                ).toString()
            )
            hint = "5"
            maxLines = 1
        }

        content.addView(
            semanticDepth,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        content.addView(Button(this).apply {
            text = "Save language & prediction settings"
            setOnClickListener {
                Prefs.setTargetLanguage(
                    this@SettingsActivity,
                    targetLanguage.text.toString()
                )
                Prefs.setHandwritingLanguage(
                    this@SettingsActivity,
                    handwritingLanguage.text.toString()
                )
                Prefs.setSemanticPredictionDepth(
                    this@SettingsActivity,
                    semanticDepth.text.toString().toIntOrNull() ?: 5
                )
                semanticDepth.setText(
                    Prefs.semanticPredictionDepth(
                        this@SettingsActivity
                    ).toString()
                )
                text = "Saved ✓"
            }
        })

        content.addView(TextView(this).apply {
            text = "Context Intelligence"
            textSize = 20f
            setTextColor(Color.rgb(22, 24, 30))
            setPadding(0, dp(28), 0, dp(6))
        })

        content.addView(TextView(this).apply {
            text = "Predictions now use surrounding text before and after the cursor, the current and neighbouring sentences, selected text, paragraph topics, detected language, question intent and the editor mode. Sensitive fields bypass this context completely."
            textSize = 14f
            setPadding(0, dp(4), 0, dp(8))
        })

        content.addView(TextView(this).apply {
            text = "Prediction v3 combines instant completion, local 2/3/4-gram learning, contextual beam search and an optional LiteRT-LM neural reranker."
            textSize = 14f
            setPadding(0, dp(6), 0, dp(8))
        })

        content.addView(Button(this).apply {
            text = "Reset word & sentence prediction learning"
            setOnClickListener {
                PredictionLearningStore(this@SettingsActivity).reset()
                text = "Prediction learning reset ✓"
            }
        })

        content.addView(TextView(this).apply {
            text = "Local neural model (LiteRT-LM)"
            textSize = 20f
            setTextColor(Color.rgb(22, 24, 30))
            setPadding(0, dp(28), 0, dp(6))
        })

        neuralStatusText = TextView(this).apply {
            textSize = 14f
            setPadding(0, dp(4), 0, dp(8))
        }
        content.addView(neuralStatusText)
        refreshNeuralStatus()

        content.addView(Button(this).apply {
            text = "Import .litertlm model"
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                startActivityForResult(intent, REQUEST_IMPORT_MODEL)
            }
        })

        content.addView(Button(this).apply {
            text = "Verify neural model SHA-256"
            setOnClickListener {
                neuralStatusText.text = "Verifying model…"
                Thread {
                    val result = neuralModelManager.verify()
                    runOnUiThread {
                        neuralStatusText.text = result.fold(
                            onSuccess = {
                                "Neural model verified ✓\n" +
                                    formatModelInfo(it.sizeBytes, it.sha256)
                            },
                            onFailure = {
                                "Verification failed: ${it.message}"
                            }
                        )
                    }
                }.start()
            }
        })

        content.addView(Button(this).apply {
            text = "Remove neural model"
            setOnClickListener {
                neuralModelManager.remove()
                refreshNeuralStatus()
            }
        })

        content.addView(TextView(this).apply {
            text = "Neural models are optional and stored only inside KeySwiper's private app storage. The keyboard remains fully functional without one."
            textSize = 13f
            setPadding(0, dp(6), 0, dp(6))
        })

        content.addView(TextView(this).apply {
            text = "Swipe v3 uses path geometry, speed, direction and a local per-key motor profile."
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
            text = "Privacy default: surrounding context, prediction learning and neural inference are disabled in sensitive/password fields. Personal models stay local."
            textSize = 14f
            setPadding(0, dp(24), 0, 0)
        })

        setContentView(
            ScrollView(this).apply {
                addView(content)
            }
        )
    }

    @Deprecated("Legacy activity result API is sufficient for this internal file picker.")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (
            requestCode != REQUEST_IMPORT_MODEL ||
            resultCode != RESULT_OK
        ) {
            return
        }

        val uri = data?.data ?: return
        neuralStatusText.text = "Importing and hashing model…"

        Thread {
            val result = neuralModelManager.installFromUri(uri)

            runOnUiThread {
                neuralStatusText.text = result.fold(
                    onSuccess = {
                        "Neural model ready ✓\n" +
                            formatModelInfo(it.sizeBytes, it.sha256)
                    },
                    onFailure = {
                        "Import failed: ${it.message}"
                    }
                )
            }
        }.start()
    }

    private fun refreshNeuralStatus() {
        neuralStatusText.text = when (val status = neuralModelManager.status()) {
            NeuralModelStatus.NotInstalled ->
                "No neural model installed. Instant and semantic local prediction are active."

            is NeuralModelStatus.Invalid ->
                "Neural model invalid: ${status.reason}"

            is NeuralModelStatus.Ready ->
                "Neural model installed ✓\n" +
                    formatModelInfo(
                        status.info.sizeBytes,
                        status.info.sha256
                    )
        }
    }

    private fun formatModelInfo(
        sizeBytes: Long,
        sha256: String
    ): String {
        val sizeGb = sizeBytes / 1_073_741_824.0
        val sizeMb = sizeBytes / 1_048_576.0

        val size = if (sizeGb >= 1.0) {
            "%.2f GB".format(sizeGb)
        } else {
            "%.1f MB".format(sizeMb)
        }

        return "Size: $size\nSHA-256: $sha256"
    }

    private fun hasMicPermission(): Boolean =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        private const val REQUEST_IMPORT_MODEL = 81
    }
}
