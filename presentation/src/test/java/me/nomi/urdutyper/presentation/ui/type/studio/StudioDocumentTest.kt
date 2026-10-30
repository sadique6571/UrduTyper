package me.nomi.urdutyper.presentation.ui.type.studio

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StudioDocumentTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun resizeScalesPositionsAndPreservesIndependentFontChoices() {
        val first = StudioLayer(fontId = "alvi", x = 270f, y = 270f)
        val second = StudioLayer(fontId = "makki", x = 810f, y = 810f, rotation = 45f)
        val resized = StudioDocument(layers = listOf(first, second)).resized(2160, 1080)
        assertEquals(540f, resized.layers[0].x, .01f)
        assertEquals(270f, resized.layers[0].y, .01f)
        assertEquals(first.size, resized.layers[0].size, .01f)
        assertEquals(listOf("alvi", "makki"), resized.layers.map { it.fontId })
        assertEquals(45f, resized.layers[1].rotation, .01f)
    }

    @Test fun saveReloadPreservesGeometryEffectsImagesAndLayerOrder() {
        val store = StudioStore(folder.newFolder())
        val text = StudioLayer(text = "خوابوں کو حقیقت بناؤ", fontId = "almas", rotation = 37f,
            opacity = .6f, bold = true, italic = true, alignment = "right", lineSpacing = 1.6f,
            locked = true, effects = listOf("Outline", "Gold"))
        val image = StudioLayer(kind = "image", imagePath = "/files/picture.png", visible = false)
        val document = StudioDocument(backgroundImage = "/files/background.png", backgroundVisible = false,
            layers = listOf(image, text))
        store.save(document)
        assertEquals(document, store.load(document.id))
        assertEquals(listOf(document), store.list())
    }

    @Test fun delayedAutosaveCannotOverwriteNewerManualSave() {
        val store = StudioStore(folder.newFolder())
        val original = StudioDocument(updatedAt = 100)
        val newer = original.copy(title = "Latest", updatedAt = 200)
        store.save(newer)
        store.save(original)
        assertEquals(newer, store.load(original.id))
    }

    @Test fun undoRestoresFontBackgroundCanvasGeometryAndVisibilityTogether() {
        val history = StudioHistory()
        val initial = StudioDocument()
        history.reset(initial)
        val changed = initial.resized(1080, 1920).copy(backgroundVisible = false,
            layers = initial.layers.map { it.copy(fontId = "makki", locked = true, rotation = 90f, effects = listOf("Glow")) })
        history.record(changed)
        assertEquals(initial, history.undo())
        assertEquals(changed, history.redo())
    }

    @Test fun editingAfterUndoDropsRedoWithoutMutatingPreviousSnapshots() {
        val history = StudioHistory()
        val original = StudioDocument()
        history.reset(original)
        history.record(original.copy(title = "Second"))
        history.undo()
        val edited = original.copy(layers = original.layers.map { it.copy(fontId = "pencil") })
        history.record(edited)
        assertFalse(history.canRedo())
        assertEquals(original, history.undo())
        assertEquals("nastaliq", original.layers.first().fontId)
    }

    @Test fun historyKeepsBoundedSnapshotsAndIgnoresDuplicates() {
        val history = StudioHistory(3)
        val first = StudioDocument()
        history.reset(first)
        history.record(first)
        assertFalse(history.canUndo())
        history.record(first.copy(title = "Two"))
        history.record(first.copy(title = "Three"))
        history.record(first.copy(title = "Four"))
        assertEquals("Three", history.undo()?.title)
        assertEquals("Two", history.undo()?.title)
        assertNull(history.undo())
    }

    @Test fun corruptDraftDoesNotHideOtherProjects() {
        val root = folder.newFolder()
        val store = StudioStore(root)
        val valid = StudioDocument()
        store.save(valid)
        java.io.File(root, "broken.json").writeText("invalid json")
        assertEquals(listOf(valid), store.list())
        assertNull(store.load("broken"))
    }

    @Test fun rejectsPathsOutsideDraftDirectory() {
        val root = folder.newFolder()
        val outside = java.io.File(root.parentFile, "outside.json").apply { writeText("keep") }
        assertNull(StudioStore(root).load("../outside"))
        assertEquals("keep", outside.readText())
    }

    @Test fun deleteRemovesOnlyRequestedDraft() {
        val store = StudioStore(folder.newFolder())
        val first = StudioDocument()
        val other = StudioDocument()
        store.save(first); store.save(other)
        assertTrue(store.delete(first.id))
        assertEquals(listOf(other), store.list())
    }
}
