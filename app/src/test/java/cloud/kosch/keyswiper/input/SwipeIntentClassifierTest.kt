package cloud.kosch.keyswiper.input

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeIntentClassifierTest {
    @Test
    fun stylusJitterRemainsATap() {
        val sample = GestureIntentSample(
            PointerKind.STYLUS,14f,22f,2,90L
        )
        assertFalse(
            SwipeIntentClassifier.shouldStartDrag(
                sample.pointerKind,
                sample.displacementDp
            )
        )
        assertFalse(SwipeIntentClassifier.shouldCommitSwipe(sample))
    }

    @Test
    fun deliberateStylusPathNeverBecomesAWordSwipe() {
        assertFalse(
            SwipeIntentClassifier.shouldCommitSwipe(
                GestureIntentSample(
                    PointerKind.STYLUS,64f,91f,4,120L
                )
            )
        )
    }

    @Test
    fun normalFingerSwipeKeepsLowerThreshold() {
        assertTrue(
            SwipeIntentClassifier.shouldCommitSwipe(
                GestureIntentSample(
                    PointerKind.TOUCH,24f,31f,2,28L
                )
            )
        )
    }

    @Test
    fun fingerLoopCanReturnToItsStartingKey() {
        assertTrue(SwipeIntentClassifier.shouldCommitSwipe(
            GestureIntentSample(PointerKind.TOUCH, 0f, 110f, 3, 180L)
        ))
    }

    @Test
    fun stationaryFingerJitterIsNotAWord() {
        assertFalse(SwipeIntentClassifier.shouldCommitSwipe(
            GestureIntentSample(PointerKind.TOUCH, 2f, 8f, 2, 180L)
        ))
    }
}
