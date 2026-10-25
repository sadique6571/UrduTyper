package me.nomi.urdutyper.presentation.utils.common

/**
 * Bounded undo/redo history for editor text.
 *
 * The editor records a snapshot after each text change. Restoring a snapshot
 * must be performed by the caller without recording that restoration again.
 */
class TextHistory(private val maxEntries: Int = 100) {
    private val undoStates = mutableListOf<String>()
    private val redoStates = mutableListOf<String>()

    init {
        require(maxEntries >= 2) { "History must keep at least two states" }
    }

    fun reset(text: String) {
        undoStates.clear()
        redoStates.clear()
        undoStates.add(text)
    }

    fun record(text: String) {
        if (undoStates.lastOrNull() == text) return
        undoStates.add(text)
        while (undoStates.size > maxEntries) undoStates.removeAt(0)
        redoStates.clear()
    }

    fun undo(currentText: String): String? {
        if (undoStates.size <= 1) return null
        // Keep the current state in redo even if a caller did not just record it.
        if (redoStates.lastOrNull() != currentText) redoStates.add(currentText)
        undoStates.removeAt(undoStates.lastIndex)
        return undoStates.lastOrNull()
    }

    fun redo(): String? {
        if (redoStates.isEmpty()) return null
        val next = redoStates.removeAt(redoStates.lastIndex)
        undoStates.add(next)
        while (undoStates.size > maxEntries) undoStates.removeAt(0)
        return next
    }

    fun canUndo(): Boolean = undoStates.size > 1
    fun canRedo(): Boolean = redoStates.isNotEmpty()
}
