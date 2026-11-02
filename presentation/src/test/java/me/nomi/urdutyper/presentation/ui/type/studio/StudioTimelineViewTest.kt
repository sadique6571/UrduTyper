package me.nomi.urdutyper.presentation.ui.type.studio

import android.app.Application
import android.view.MotionEvent
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class StudioTimelineViewTest {
    @Test fun draggingClipMovesItOnceAndCancelDoesNotCommit() {
        val context: Application = RuntimeEnvironment.getApplication()
        val density = context.resources.displayMetrics.density
        val timeline = StudioTimelineView(context).apply {
            durationMs = 8000
            clips = listOf(StudioTimelineView.Clip("text", "Urdu", 1000, 3000))
        }
        timeline.measure(View.MeasureSpec.makeMeasureSpec((600 * density).toInt(), View.MeasureSpec.EXACTLY), 0)
        timeline.layout(0,0,timeline.measuredWidth,timeline.measuredHeight)
        fun x(ms: Long) = 88 * density + (timeline.width - 104 * density) * ms / 8000
        val y = 63 * density
        var calls = 0; var result = 0L to 0L
        timeline.onRange = { _, start, end -> calls++; result = start to end }
        fun touch(action: Int, ms: Long) { val event = MotionEvent.obtain(0,0,action,x(ms),y,0); timeline.onTouchEvent(event); event.recycle() }
        touch(MotionEvent.ACTION_DOWN,2000); touch(MotionEvent.ACTION_MOVE,3000)
        assertEquals(0,calls)
        touch(MotionEvent.ACTION_UP,3000)
        assertEquals(1,calls); assertEquals(2000L,result.first); assertEquals(4000L,result.second)
        touch(MotionEvent.ACTION_DOWN,2000); touch(MotionEvent.ACTION_MOVE,3500); touch(MotionEvent.ACTION_CANCEL,3500)
        assertEquals(1,calls)
    }
    @Test fun lockedClipNeverChangesRange() {
        val context: Application = RuntimeEnvironment.getApplication()
        val timeline = StudioTimelineView(context).apply { clips = listOf(StudioTimelineView.Clip("a","Locked",0,8000,locked = true)) }
        timeline.measure(View.MeasureSpec.makeMeasureSpec(600,View.MeasureSpec.EXACTLY),0)
        timeline.layout(0,0,600,timeline.measuredHeight)
        var calls = 0; timeline.onRange = { _,_,_ -> calls++ }
        listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP).forEach { action ->
            val event = MotionEvent.obtain(0,0,action,300f,63 * context.resources.displayMetrics.density,0)
            timeline.onTouchEvent(event); event.recycle()
        }
        assertEquals(0,calls)
    }
}
