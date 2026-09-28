package cloud.kosch.keyswiper.ui

import android.graphics.Color

data class KeyboardThemeProfile(
    val id: String,
    val label: String,
    val background: Int,
    val surface: Int,
    val surfaceRaised: Int,
    val key: Int,
    val keySpecial: Int,
    val keyPressed: Int,
    val accent: Int,
    val accentSoft: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val border: Int,
    val trace: Int,
    val keyCornerDp: Float,
    val panelCornerDp: Float
)

object KeyboardThemes {
    val matrixCyber =
        KeyboardThemeProfile(
            id = "matrix-cyber",
            label = "Matrix Cyber · Default",
            background =
                Color.rgb(
                    3,
                    8,
                    10
                ),
            surface =
                Color.rgb(
                    7,
                    16,
                    18
                ),
            surfaceRaised =
                Color.rgb(
                    10,
                    24,
                    26
                ),
            key =
                Color.rgb(
                    13,
                    30,
                    31
                ),
            keySpecial =
                Color.rgb(
                    9,
                    41,
                    38
                ),
            keyPressed =
                Color.rgb(
                    17,
                    71,
                    58
                ),
            accent =
                Color.rgb(
                    76,
                    255,
                    180
                ),
            accentSoft =
                Color.rgb(
                    33,
                    112,
                    84
                ),
            textPrimary =
                Color.rgb(
                    232,
                    255,
                    247
                ),
            textSecondary =
                Color.rgb(
                    146,
                    194,
                    177
                ),
            border =
                Color.rgb(
                    28,
                    76,
                    63
                ),
            trace =
                Color.rgb(
                    86,
                    255,
                    195
                ),
            keyCornerDp = 12f,
            panelCornerDp = 18f
        )

    val all =
        listOf(
            matrixCyber
        )

    fun byId(
        id: String?
    ): KeyboardThemeProfile =
        all.firstOrNull {
            it.id == id
        } ?: matrixCyber
}
