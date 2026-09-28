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
    val matrixCyber = KeyboardThemeProfile(
        "matrix-cyber","Matrix Cyber · Default",
        Color.rgb(3,8,10),Color.rgb(7,16,18),Color.rgb(10,24,26),
        Color.rgb(13,30,31),Color.rgb(9,41,38),Color.rgb(17,71,58),
        Color.rgb(76,255,180),Color.rgb(33,112,84),
        Color.rgb(232,255,247),Color.rgb(146,194,177),
        Color.rgb(28,76,63),Color.rgb(86,255,195),12f,18f
    )

    val oledObsidian = KeyboardThemeProfile(
        "oled-obsidian","OLED Obsidian",
        Color.rgb(0,0,0),Color.rgb(8,8,10),Color.rgb(16,16,20),
        Color.rgb(20,20,24),Color.rgb(28,28,35),Color.rgb(50,50,62),
        Color.rgb(190,195,255),Color.rgb(72,75,120),
        Color.rgb(248,248,252),Color.rgb(178,180,194),
        Color.rgb(47,48,58),Color.rgb(215,218,255),11f,17f
    )

    val neonTokyo = KeyboardThemeProfile(
        "neon-tokyo","Neon Tokyo",
        Color.rgb(16,5,24),Color.rgb(29,9,40),Color.rgb(46,12,58),
        Color.rgb(38,15,52),Color.rgb(57,14,72),Color.rgb(91,20,101),
        Color.rgb(255,84,196),Color.rgb(119,35,103),
        Color.rgb(255,240,253),Color.rgb(218,169,213),
        Color.rgb(95,42,112),Color.rgb(69,244,255),13f,20f
    )

    val auroraGlass = KeyboardThemeProfile(
        "aurora-glass","Aurora Glass",
        Color.rgb(9,18,29),Color.rgb(15,31,45),Color.rgb(23,44,60),
        Color.rgb(25,49,63),Color.rgb(21,61,68),Color.rgb(37,88,92),
        Color.rgb(116,255,214),Color.rgb(52,122,117),
        Color.rgb(239,255,252),Color.rgb(166,211,211),
        Color.rgb(56,100,116),Color.rgb(128,214,255),15f,22f
    )

    val emberCopper = KeyboardThemeProfile(
        "ember-copper","Ember Copper",
        Color.rgb(20,11,8),Color.rgb(34,19,14),Color.rgb(49,28,20),
        Color.rgb(55,31,22),Color.rgb(69,35,21),Color.rgb(100,50,27),
        Color.rgb(255,166,92),Color.rgb(122,72,39),
        Color.rgb(255,246,237),Color.rgb(218,181,157),
        Color.rgb(104,61,41),Color.rgb(255,193,116),10f,16f
    )

    val kawaiiCyber = KeyboardThemeProfile(
        "kawaii-cyber","Kawaii Cyber",
        Color.rgb(23,18,34),Color.rgb(38,29,52),Color.rgb(55,39,70),
        Color.rgb(63,45,80),Color.rgb(75,49,91),Color.rgb(106,67,119),
        Color.rgb(255,151,213),Color.rgb(133,79,124),
        Color.rgb(255,244,253),Color.rgb(222,188,222),
        Color.rgb(103,75,119),Color.rgb(157,236,255),17f,24f
    )

    val all = listOf(
        matrixCyber,
        oledObsidian,
        neonTokyo,
        auroraGlass,
        emberCopper,
        kawaiiCyber
    )

    fun byId(id: String?): KeyboardThemeProfile =
        all.firstOrNull { it.id == id } ?: matrixCyber
}
