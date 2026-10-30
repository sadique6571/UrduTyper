package me.nomi.urdutyper.presentation.ui.type.studio

import android.app.Application
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import me.nomi.urdutyper.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StudioControlsTest {
    private val context get(): ContextThemeWrapper {
        val application: Application = RuntimeEnvironment.getApplication()
        return ContextThemeWrapper(application, R.style.Theme_UrduTyper)
    }

    private fun attach(view: View) {
        val parent = FrameLayout(view.context)
        parent.addView(view)
        parent.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY))
        parent.layout(0, 0, 1080, 1080)
    }

    @Test fun selectionSyncNeverCreatesEditsButPlusAndMinusCommitImmediately() {
        val control = FontSizeControl(context)
        val commits = mutableListOf<Float>()
        control.onCommit = { commits.add(it) }
        control.showSelection(76f, true)
        assertTrue(commits.isEmpty())
        control.increaseButton.performClick()
        control.decreaseButton.performClick()
        assertEquals(listOf(77f, 76f), commits)
        assertEquals("76", control.valueButton.text.toString())
        control.showSelection(120f, true)
        assertEquals(112, control.seekBar.progress)
        assertEquals(2, commits.size)
    }

    @Test fun draggingPreviewsContinuouslyAndCreatesOneCommitOnRelease() {
        val control = FontSizeControl(context)
        attach(control)
        val previews = mutableListOf<Float>()
        val commits = mutableListOf<Float>()
        control.onPreview = { previews.add(it) }; control.onCommit = { commits.add(it) }
        control.showSelection(76f, true)
        val bar = control.seekBar
        val start = SystemClock.uptimeMillis()
        fun touch(action: Int, fraction: Float, elapsed: Long) {
            val event = MotionEvent.obtain(start, start + elapsed, action, bar.width * fraction, bar.height / 2f, 0)
            try { bar.dispatchTouchEvent(event) } finally { event.recycle() }
        }
        touch(MotionEvent.ACTION_DOWN, .2f, 0)
        touch(MotionEvent.ACTION_MOVE, .5f, 50)
        touch(MotionEvent.ACTION_MOVE, .8f, 100)
        assertTrue(previews.size >= 2)
        assertTrue("A drag must not fill the undo stack with intermediate sizes", commits.isEmpty())
        touch(MotionEvent.ACTION_UP, .8f, 150)
        assertEquals(1, commits.size)
        assertEquals(previews.last(), commits.single())
    }

    @Test fun keyboardSliderChangesCommitWithoutWaitingForTouchRelease() {
        val control = FontSizeControl(context)
        val commits = mutableListOf<Float>()
        control.onCommit = { commits.add(it) }; control.showSelection(76f, true)
        control.seekBar.onKeyDown(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(listOf(77f), commits)
    }

    @Test fun imageOrLockedSelectionCannotResizeText() {
        val control = FontSizeControl(context)
        var changed = false
        control.onCommit = { changed = true }
        control.showSelection(null, false)
        assertFalse(control.seekBar.isEnabled)
        control.increaseButton.performClick()
        control.showSelection(60f, false)
        control.decreaseButton.performClick()
        assertFalse(changed)
        assertFalse(control.valueButton.isEnabled)
    }

    @Test fun centerSnapCanBeEscapedWithSmallDragStepsAndCommitsOnce() {
        val renderer = StudioRenderer(context, StudioFonts(context))
        val canvas = StudioCanvas(context, renderer)
        val layer = StudioLayer(x = 420f, y = 420f)
        canvas.document = StudioDocument(layers = listOf(layer)); canvas.selectedId = layer.id
        attach(canvas)
        val scale = (canvas.width - 40 * context.resources.displayMetrics.density) / 1080f
        val inset = (canvas.width - 1080 * scale) / 2
        val start = SystemClock.uptimeMillis()
        var commits = 0
        canvas.onChange = { commits++ }
        fun touch(action: Int, position: Float, elapsed: Long) {
            val point = inset + position * scale
            val event = MotionEvent.obtain(start, start + elapsed, action, point, point, 0)
            try { canvas.onTouchEvent(event) } finally { event.recycle() }
        }
        touch(MotionEvent.ACTION_DOWN, 420f, 0)
        touch(MotionEvent.ACTION_MOVE, 535f, 30)
        assertEquals(540f, canvas.document.layers.single().x, .01f)
        for (position in 540..580 step 5) touch(MotionEvent.ACTION_MOVE, position.toFloat(), (position - 500).toLong())
        assertEquals(580f, canvas.document.layers.single().x, .01f)
        touch(MotionEvent.ACTION_UP, 580f, 100)
        assertEquals(1, commits)
    }

    @Test fun viewportZoomAndGridNeverModifyExportedDocument() {
        val canvas = StudioCanvas(context, StudioRenderer(context, StudioFonts(context)))
        attach(canvas)
        val original = canvas.document
        var percent = 100
        canvas.onZoom = { percent = it }
        canvas.zoomBy(1.25f)
        assertEquals(125, percent)
        canvas.showGrid = true
        assertEquals(original, canvas.document)
        canvas.fitToScreen()
        assertEquals(100, percent)
        assertEquals(original, canvas.document)
    }
}
