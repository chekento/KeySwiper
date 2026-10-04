package cloud.kosch.keyswiper.ui

data class EmojiCategory(val label: String, val groups: List<List<String>>) {
    val keys: List<String> get() = groups.map { it.first() }
}

object EmojiCatalog {
    val categories = listOf(
        EmojiCategory("😀", listOf(
            listOf("😀", "😃", "😄", "😁", "😊", "🙂", "☺️"),
            listOf("😂", "🤣", "😆", "😅", "🥲", "🥹"),
            listOf("😍", "🥰", "😘", "😚", "😻"),
            listOf("😎", "🤓", "🧐", "🥸", "🤩", "🥳"),
            listOf("🤔", "🤨", "😐", "😑", "🙄", "😏"),
            listOf("😢", "😭", "😔", "😞", "🥺", "😿"),
            listOf("😡", "😠", "🤬", "😤", "😾"),
            listOf("🤯", "😲", "😮", "😱", "😵", "🫨"),
            listOf("😴", "🥱", "😪", "💤"),
            listOf("😇", "😉", "🙃", "😜", "😋", "🤪"),
            listOf("🤗", "🤭", "🫣", "🫡", "🤫"),
            listOf("🤒", "🤕", "🤧", "😷", "🤢", "🤮")
        )),
        EmojiCategory("❤️", listOf(
            listOf("❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍"),
            listOf("💕", "💞", "💓", "💗", "💖", "💘", "💝"),
            listOf("❤️‍🔥", "❤️‍🩹", "💔", "💟", "❣️"),
            listOf("✨", "🌟", "⭐", "💫", "🌠", "✴️"),
            listOf("🔥", "💥", "⚡", "☄️", "🌋"),
            listOf("🎉", "🎊", "🎈", "🎁", "🎀", "🎂"),
            listOf("✅", "☑️", "✔️", "❌", "❎", "⭕"),
            listOf("💯", "❗", "❓", "⁉️", "‼️", "🔔")
        )),
        EmojiCategory("👍", listOf("👍", "👎", "👋", "👌", "✌️", "🤞", "🤟", "🙌", "👏", "🙏", "🫶", "💪").map { base ->
            listOf(base) + listOf("🏻", "🏼", "🏽", "🏾", "🏿").map { base.replace("\uFE0F", "") + it }
        }),
        EmojiCategory("🐾", listOf(
            listOf("🐶", "🐕", "🦮", "🐕‍🦺", "🐩", "🐺"),
            listOf("🐱", "🐈", "🐈‍⬛", "🦁", "🐯", "🐆"),
            listOf("🐦", "🦜", "🦉", "🦅", "🦆", "🦢", "🐧"),
            listOf("🦋", "🐝", "🐞", "🐛", "🐌", "🪲"),
            listOf("🌳", "🌲", "🌴", "🌵", "🌿", "🍀"),
            listOf("🌷", "🌹", "🌻", "🌸", "🌺", "💐"),
            listOf("☀️", "🌤️", "⛅", "☁️", "🌧️", "⛈️", "🌨️"),
            listOf("🌙", "🌑", "🌓", "🌕", "🌌", "🌈")
        )),
        EmojiCategory("🍕", listOf(
            listOf("🍕", "🍔", "🍟", "🌭", "🥪", "🌮"),
            listOf("🍝", "🍜", "🍲", "🍛", "🍣", "🥗"),
            listOf("☕", "🍵", "🧋", "🥛", "🧃", "🥤"),
            listOf("🍎", "🍏", "🍐", "🍊", "🍋", "🍌", "🍉"),
            listOf("🍓", "🍒", "🍇", "🫐", "🥝", "🍍"),
            listOf("🍰", "🎂", "🧁", "🍩", "🍪", "🍫", "🍦"),
            listOf("🚗", "🚕", "🚌", "🚆", "🚲", "✈️", "🚀"),
            listOf("🏠", "🏡", "🏢", "🏰", "🏖️", "⛰️", "🏕️")
        )),
        EmojiCategory("💡", listOf(
            listOf("💡", "🔦", "🕯️", "🔌", "🔋"),
            listOf("💻", "🖥️", "⌨️", "🖱️", "📱", "🤖"),
            listOf("🎮", "🕹️", "🎲", "🧩", "♟️", "🎯"),
            listOf("🎵", "🎶", "🎧", "🎤", "🎸", "🎹"),
            listOf("📷", "📸", "🎥", "🎬", "🎨", "🖼️"),
            listOf("📚", "📖", "📝", "✏️", "🖊️", "📌"),
            listOf("🛠️", "🔧", "🔨", "⚙️", "🧰"),
            listOf("⏰", "⌚", "⏱️", "📅", "🗓️", "⌛")
        ))
    )

    fun alternatives(emoji: String): List<String> =
        categories.asSequence().flatMap { it.groups.asSequence() }
            .firstOrNull { emoji in it }?.filterNot { it == emoji }.orEmpty()
}
