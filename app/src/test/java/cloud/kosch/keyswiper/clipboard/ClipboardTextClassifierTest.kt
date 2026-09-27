package cloud.kosch.keyswiper.clipboard

import org.junit.Assert.assertEquals
import org.junit.Test

class ClipboardTextClassifierTest {

    @Test
    fun identifiesCommonClipboardTypes() {
        assertEquals(
            ClipboardCategory.LINK,
            ClipboardTextClassifier.category(
                "https://kosch.cloud/example"
            )
        )

        assertEquals(
            ClipboardCategory.EMAIL,
            ClipboardTextClassifier.category(
                "hello@example.com"
            )
        )

        assertEquals(
            ClipboardCategory.PHONE,
            ClipboardTextClassifier.category(
                "+49 4102 123456"
            )
        )

        assertEquals(
            ClipboardCategory.ADDRESS,
            ClipboardTextClassifier.category(
                "12 Example Straße Hamburg"
            )
        )

        assertEquals(
            ClipboardCategory.CODE,
            ClipboardTextClassifier.category(
                "fun main() { return }"
            )
        )

        assertEquals(
            ClipboardCategory.TEXT,
            ClipboardTextClassifier.category(
                "Ein normaler Text"
            )
        )
    }
}
