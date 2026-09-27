package cloud.kosch.keyswiper.input

import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeDecoderTest {
    @Test fun singleKeyIsPreserved() {
        assertTrue(SwipeDecoder().decode(listOf('a')).first() == "a")
    }

    @Test fun traceProducesCandidates() {
        assertTrue(SwipeDecoder().decode(listOf('h', 'e', 'l', 'l', 'o')).contains("hello"))
    }
}
