package me.nomi.urdutyper.presentation.utils.common

import android.graphics.Typeface
import android.graphics.drawable.Drawable

/**
 * One editor snapshot: everything undo/redo must restore. The old
 * TextHistory kept text only, so pressing undo after a template/background
 * change reverted the words but never brought the previous template back.
 *
 * Background is stored as the live Drawable reference (re-setting the same
 * instance on the same view is safe); effects are TextEffect ordinals so
 * this class does not depend on the editor fragment.
 */
data class EditorSnapshot(
    val text: String,
    val background: Drawable?,
    val backgroundLabel: String,
    val textColor: Int,
    val font: Typeface?,
    val typefaceStyle: Int,
    val textSize: Float,
    val effects: Set<Int>
)

/**
 * Bounded undo/redo history over EditorSnapshot. Same rules as TextHistory:
 * restoring a snapshot must be performed by the caller without recording
 * that restoration again, and identical consecutive states are dropped.
 */
class EditorHistory(private val maxEntries: Int = 100) {
    private val undoStates = mutableListOf<EditorSnapshot>()
    private val redoStates = mutableListOf<EditorSnapshot>()

    init {
        require(maxEntries >= 2) { "History must keep at least two states" }
    }

    fun reset(state: EditorSnapshot) {
        undoStates.clear()
        redoStates.clear()
        undoStates.add(state)
    }

    fun record(state: EditorSnapshot) {
        if (undoStates.lastOrNull() == state) return
        undoStates.add(state)
        while (undoStates.size > maxEntries) undoStates.removeAt(0)
        redoStates.clear()
    }

    fun undo(current: EditorSnapshot): EditorSnapshot? {
        if (undoStates.size <= 1) return null
        // Keep the current state in redo even if a caller did not just record it.
        if (redoStates.lastOrNull() != current) redoStates.add(current)
        undoStates.removeAt(undoStates.lastIndex)
        return undoStates.lastOrNull()
    }

    fun redo(): EditorSnapshot? {
        if (redoStates.isEmpty()) return null
        val next = redoStates.removeAt(redoStates.lastIndex)
        undoStates.add(next)
        while (undoStates.size > maxEntries) undoStates.removeAt(0)
        return next
    }

    fun canUndo(): Boolean = undoStates.size > 1
    fun canRedo(): Boolean = redoStates.isNotEmpty()
}
