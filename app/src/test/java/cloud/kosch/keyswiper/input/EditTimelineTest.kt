package cloud.kosch.keyswiper.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditTimelineTest {

    @Test
    fun undoAndRedoRequireMatchingCursorTail() {
        val timeline =
            EditTimeline()

        timeline.record(
            deletedText =
                "teh",
            insertedText =
                "the ",
            source =
                "Prediction",
            timestampMs = 1L
        )

        assertNull(
            timeline.planUndo(
                "not matching"
            )
        )

        val undo =
            timeline.planUndo(
                "hello the "
            )

        assertNotNull(undo)
        assertEquals(
            4,
            undo?.deleteUtf16Count
        )
        assertEquals(
            "teh",
            undo?.insertText
        )

        assertTrue(
            timeline.completeUndo(
                undo!!.entry.id
            )
        )
        assertFalse(
            timeline.canUndo
        )
        assertTrue(
            timeline.canRedo
        )

        val redo =
            timeline.planRedo(
                "hello teh"
            )

        assertNotNull(redo)
        assertEquals(
            "the ",
            redo?.insertText
        )
    }

    @Test
    fun newRecordClearsRedoHistory() {
        val timeline =
            EditTimeline()

        timeline.record(
            "a",
            "b",
            "test",
            1L
        )

        val undo =
            timeline.planUndo("b")!!

        timeline.completeUndo(
            undo.entry.id
        )

        timeline.record(
            "x",
            "y",
            "test",
            2L
        )

        assertFalse(
            timeline.canRedo
        )
    }
    @Test fun deletingUndoRequiresBothCursorAnchors() {
        val timeline = EditTimeline()
        timeline.record("drei Worte weg", "", "Gedrückt löschen", anchorBefore = "Davor ", anchorAfter = "danach")
        assertNull(timeline.planUndo("Anders ", "danach"))
        assertNull(timeline.planUndo("Davor ", "anderer Text"))
        val undo = checkNotNull(timeline.planUndo("Davor ", "danach"))
        assertEquals("drei Worte weg", undo.insertText)
        timeline.completeUndo(undo.entry.id)
        assertNotNull(timeline.planRedo("Davor drei Worte weg", "danach"))
        assertNull(timeline.planRedo("Woanders drei Worte weg", "danach"))
    }
}

