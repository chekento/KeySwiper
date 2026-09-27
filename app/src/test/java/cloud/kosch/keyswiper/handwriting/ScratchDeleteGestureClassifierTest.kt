package cloud.kosch.keyswiper.handwriting

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ScratchDeleteGestureClassifierTest {

    @Test
    fun recognizesHorizontalScratchOut() {
        val points = listOf(
            StylusScreenPoint(10f, 50f, 0),
            StylusScreenPoint(100f, 45f, 10),
            StylusScreenPoint(25f, 55f, 20),
            StylusScreenPoint(115f, 46f, 30),
            StylusScreenPoint(30f, 57f, 40),
            StylusScreenPoint(120f, 48f, 50),
            StylusScreenPoint(35f, 54f, 60),
            StylusScreenPoint(125f, 50f, 70)
        )

        assertNotNull(
            ScratchDeleteGestureClassifier.classify(
                points,
                density = 1f
            )
        )
    }

    @Test
    fun doesNotTreatNormalStrokeAsScratchOut() {
        val points = listOf(
            StylusScreenPoint(10f, 10f, 0),
            StylusScreenPoint(20f, 20f, 10),
            StylusScreenPoint(30f, 30f, 20),
            StylusScreenPoint(40f, 40f, 30),
            StylusScreenPoint(50f, 50f, 40),
            StylusScreenPoint(60f, 60f, 50),
            StylusScreenPoint(70f, 70f, 60),
            StylusScreenPoint(80f, 80f, 70)
        )

        assertNull(
            ScratchDeleteGestureClassifier.classify(
                points,
                density = 1f
            )
        )
    }
}
