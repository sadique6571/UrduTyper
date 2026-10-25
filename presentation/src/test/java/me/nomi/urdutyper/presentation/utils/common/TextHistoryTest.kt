package me.nomi.urdutyper.presentation.utils.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextHistoryTest {
    @Test
    fun undoWalksBackThroughRecordedStates() {
        val history = TextHistory()
        history.reset("a")
        history.record("ab")
        history.record("abc")

        assertEquals("ab", history.undo("abc"))
        assertEquals("a", history.undo("ab"))
        assertNull(history.undo("a"))
    }

    @Test
    fun redoRestoresStatesInOriginalOrder() {
        val history = TextHistory()
        history.reset("a")
        history.record("ab")
        history.record("abc")
        history.undo("abc")
        history.undo("ab")

        assertEquals("ab", history.redo())
        assertEquals("abc", history.redo())
        assertNull(history.redo())
    }

    @Test
    fun recordingAfterUndoClearsRedoBranch() {
        val history = TextHistory()
        history.reset("a")
        history.record("ab")
        history.record("abc")
        history.undo("abc")
        history.record("ax")

        assertFalse(history.canRedo())
        // The branch starts from "ab", the state immediately before the edit.
        assertEquals("ab", history.undo("ax"))
    }

    @Test
    fun historyIsBoundedAndIgnoresDuplicateSnapshots() {
        val history = TextHistory(maxEntries = 3)
        history.reset("a")
        history.record("a")
        history.record("ab")
        history.record("abc")
        history.record("abcd")

        assertTrue(history.canUndo())
        assertEquals("abc", history.undo("abcd"))
        assertEquals("ab", history.undo("abc"))
        assertNull(history.undo("ab"))
    }
}
