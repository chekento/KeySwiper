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
    fun deliberateStylusPathCanSwipe() {
        assertTrue(
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
}
