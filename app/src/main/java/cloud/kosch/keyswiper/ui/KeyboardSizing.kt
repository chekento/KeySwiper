package cloud.kosch.keyswiper.ui

import kotlin.math.roundToInt

data class KeyboardSizeBudget(
    val maxImeHeightPx: Int,
    val toolbarHeightPx: Int,
    val predictionHeightPx: Int,
    val surfaceHeightPx: Int,
    val accentRowHeightPx: Int,
    val bottomRowHeightPx: Int,
    val bottomInsetPx: Int,
    val contentVerticalPaddingPx: Int
) {
    val totalHeightPx: Int
        get() =
            toolbarHeightPx +
                predictionHeightPx +
                surfaceHeightPx +
                accentRowHeightPx +
                bottomRowHeightPx +
                bottomInsetPx +
                contentVerticalPaddingPx
}

object KeyboardSizing {
    fun calculate(
        screenHeightPx: Int,
        density: Float,
        symbolMode: Boolean,
        bottomInsetPx: Int,
        contentVerticalPaddingPx: Int
    ): KeyboardSizeBudget {
        val safeScreenHeight = screenHeightPx.coerceAtLeast(3)
        val safeDensity = density.coerceAtLeast(0.5f)
        val maxImeHeight = (safeScreenHeight / 3).coerceAtLeast(1)
        val inset = bottomInsetPx.coerceIn(0, maxImeHeight / 5)
        val contentPadding =
            contentVerticalPaddingPx.coerceIn(
                0,
                (maxImeHeight - inset).coerceAtLeast(0)
            )
        val usable =
            (maxImeHeight - inset - contentPadding)
                .coerceAtLeast(1)

        fun dp(value: Float): Int =
            (value * safeDensity).roundToInt()

        fun fraction(
            ratio: Float,
            capDp: Float
        ): Int =
            (usable * ratio)
                .roundToInt()
                .coerceAtMost(dp(capDp))
                .coerceAtLeast(1)

        val toolbar = fraction(0.10f, 26f)
        val prediction = fraction(0.14f, 36f)
        val bottom = fraction(0.17f, 42f)
        val accent =
            if (symbolMode) 0
            else fraction(0.09f, 24f)

        val surface =
            (usable - toolbar - prediction - bottom - accent)
                .coerceAtLeast(1)

        return KeyboardSizeBudget(
            maxImeHeight,
            toolbar,
            prediction,
            surface,
            accent,
            bottom,
            inset,
            contentPadding
        )
    }
}
