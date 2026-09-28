package cloud.kosch.keyswiper.handwriting

import org.junit.Assert.assertTrue
import org.junit.Test

class ExtendedHandwritingGestureClassifierTest {

    @Test
    fun recognizesCircleAsSelection() {
        val points =
            listOf(
                StylusScreenPoint(20f, 50f, 0),
                StylusScreenPoint(30f, 20f, 10),
                StylusScreenPoint(70f, 10f, 20),
                StylusScreenPoint(105f, 25f, 30),
                StylusScreenPoint(115f, 55f, 40),
                StylusScreenPoint(100f, 85f, 50),
                StylusScreenPoint(60f, 95f, 60),
                StylusScreenPoint(25f, 80f, 70),
                StylusScreenPoint(20f, 50f, 80)
            )

        assertTrue(
            ExtendedHandwritingGestureClassifier
                .classify(
                    points,
                    density = 1f
                ) is
                ExtendedHandwritingGesture
                    .SelectArea
        )
    }

    @Test
    fun recognizesLongHorizontalRemoveSpace() {
        val points =
            listOf(
                StylusScreenPoint(10f, 50f, 0),
                StylusScreenPoint(45f, 51f, 10),
                StylusScreenPoint(85f, 49f, 20),
                StylusScreenPoint(125f, 50f, 30)
            )

        assertTrue(
            ExtendedHandwritingGestureClassifier
                .classify(
                    points,
                    density = 1f
                ) is
                ExtendedHandwritingGesture
                    .RemoveSpace
        )
    }

    @Test
    fun recognizesLongVerticalJoinOrSplit() {
        val points =
            listOf(
                StylusScreenPoint(60f, 10f, 0),
                StylusScreenPoint(61f, 35f, 10),
                StylusScreenPoint(59f, 65f, 20),
                StylusScreenPoint(60f, 90f, 30)
            )

        assertTrue(
            ExtendedHandwritingGestureClassifier
                .classify(
                    points,
                    density = 1f
                ) is
                ExtendedHandwritingGesture
                    .JoinOrSplit
        )
    }
}
