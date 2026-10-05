package cloud.kosch.keyswiper.ui

/** Text alternatives are strings so multi-code-point symbols stay intact. */
object KeyAlternatives {
    private val letters = mapOf(
        'a' to listOf("ä", "à", "á", "â", "ã", "å", "æ"),
        'e' to listOf("é", "è", "ê", "ë", "€"),
        'i' to listOf("í", "ì", "î", "ï", "ı"),
        'o' to listOf("ö", "ó", "ò", "ô", "õ", "ø", "œ"),
        'u' to listOf("ü", "ú", "ù", "û"),
        's' to listOf("ß", "ś", "š", "$"),
        'c' to listOf("ç", "ć", "č", "©"),
        'n' to listOf("ñ", "ń", "ŋ", "~"),
        'y' to listOf("ý", "ÿ", "¥"), 'z' to listOf("ž", "ź", "ż"),
        'l' to listOf("ł", "£"), 'd' to listOf("ð", "đ", "†"),
        't' to listOf("þ", "ť", "™"), 'r' to listOf("ř", "ŕ", "®"),
        'g' to listOf("ğ", "ģ"), 'h' to listOf("ħ", "#"),
        'j' to listOf("ĵ", "_"), 'k' to listOf("ķ", "("),
        'b' to listOf("β", ")"), 'v' to listOf("✓", "/"),
        'x' to listOf("×", "%"), 'f' to listOf("ƒ", "+"),
        'm' to listOf("µ", "−"), 'p' to listOf("π", "¶"),
        'q' to listOf("@", "¿"), 'w' to listOf("ŵ", "&")
    )
    private val symbols = mapOf(
        '1' to listOf("¹", "½", "⅓", "¼", "⅛"),
        '2' to listOf("²", "⅔", "½"), '3' to listOf("³", "¾", "⅜"),
        '4' to listOf("⁴", "¼"), '5' to listOf("⁵", "⅕", "⅝"),
        '6' to listOf("⁶", "⅙"), '7' to listOf("⁷", "⅞"),
        '8' to listOf("⁸", "⅛", "∞"), '9' to listOf("⁹", "⅑"),
        '0' to listOf("⁰", "°", "∅"),
        '@' to listOf("©", "®", "™"), '#' to listOf("№", "♯", "♭"),
        '€' to listOf("$", "£", "¥", "₹", "₩", "₽", "¢"),
        '$' to listOf("€", "£", "¥", "¢"), '£' to listOf("€", "$", "¥"),
        '_' to listOf("—", "–", "¯"), '-' to listOf("−", "–", "—", "•"),
        '&' to listOf("§", "¶", "⁊"), '+' to listOf("±", "∓", "∑", "†"),
        '(' to listOf("[", "{", "〈", "«"), ')' to listOf("]", "}", "〉", "»"),
        '/' to listOf("\\", "÷", "⁄"), '*' to listOf("×", "⋆", "★", "•"),
        '"' to listOf("„", "“", "”", "«", "»"), '\'' to listOf("’", "‘", "‚", "`"),
        ':' to listOf("∶", ";", "…"), ';' to listOf(":", "·", "•"),
        '!' to listOf("¡", "‼", "⁉"), '?' to listOf("¿", "‽", "⁉"),
        '.' to listOf(",", "?", "!", ":", ";", "…", "–", "·"), ',' to listOf("‚", "„", "،"),
        '[' to listOf("(", "{", "〈"), ']' to listOf(")", "}", "〉"),
        '{' to listOf("[", "(", "«"), '}' to listOf("]", ")", "»"),
        '<' to listOf("≤", "«", "‹", "←"), '>' to listOf("≥", "»", "›", "→"),
        '=' to listOf("≠", "≈", "≡", "≔"), '%' to listOf("‰", "‱", "٪"),
        '^' to listOf("↑", "ˆ", "⌃"), '~' to listOf("≈", "≃", "∼"),
        '|' to listOf("¦", "‖", "∣"), '\\' to listOf("/", "÷", "∖"),
        '°' to listOf("℃", "℉", "′", "″"), '×' to listOf("*", "·", "⊗"),
        '÷' to listOf("/", "∕", "∶"), '±' to listOf("+", "−", "∓"),
        '§' to listOf("¶", "&", "†"), '…' to listOf(".", "⋯", "⋮"),
        '•' to listOf("·", "◦", "●", "○"), '←' to listOf("⇐", "↔", "↖"),
        '→' to listOf("⇒", "↔", "↗"), '↑' to listOf("⇧", "↟", "↕"),
        '↓' to listOf("⇩", "↡", "↕")
    )

    fun forKey(character: Char, layoutId: String, shifted: Boolean, symbolMode: Boolean): List<String> {
        if (symbolMode || !character.isLetter()) return symbols[character].orEmpty()
        val key = character.lowercaseChar()
        val row = KeyboardLayoutProfiles.byId(layoutId).letterRows.first()
        val digit = row.indexOf(key).takeIf { it >= 0 }?.let { "1234567890"[it].toString() }
        val alternatives = (letters[key].orEmpty() + listOfNotNull(digit)).distinct().take(8)
        return if (shifted) alternatives.map { if (it == "ß") "ẞ" else it.uppercase() } else alternatives
    }

    fun hint(character: Char, layoutId: String, symbolMode: Boolean): String? =
        forKey(character, layoutId, false, symbolMode).firstOrNull()
}
