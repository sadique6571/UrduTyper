package me.nomi.urdutyper.presentation.ui.type.studio

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.roundToLong

/** A real time scale: drag the body to move, drag an edge to trim. One undo event per gesture. */
class StudioTimelineView(context: Context) : View(context) {
    data class Clip(val id: String, val title: String, val start: Long, val end: Long,
                    val audio: Boolean = false, val locked: Boolean = false, val peaks: FloatArray? = null)
    var clips: List<Clip> = emptyList()
        set(value) { field = value; requestLayout(); invalidate() }
    var durationMs = 8000L
    var positionMs = 0L
        set(value) { field = value; invalidate() }
    var onRange: (String, Long, Long) -> Unit = { _, _, _ -> }
    var onClip: (String) -> Unit = {}
    var onScrub: (Long) -> Unit = {}
    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rowHeight get() = 42f * density
    private val left get() = 88f * density
    private val right get() = (width - 16f * density).coerceAtLeast(left + 1)
    private fun x(time: Long) = left + (right - left) * time / durationMs.coerceAtLeast(1)
    private fun time(x: Float) = (((x - left) / (right - left)) * durationMs).roundToLong().coerceIn(0, durationMs)
    private var active: Clip? = null
    private var downX = 0f
    private var mode = 0
    private var moved = false
    private var previewStart = 0L
    private var previewEnd = 0L
    init { contentDescription = "Timeline. Drag clip edges to trim, drag its middle to move, tap the ruler to seek."; isClickable = true }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), ((clips.size + 1) * rowHeight).toInt())
    }
    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(LuxeTheme.background)
        paint.textSize = 10f * density
        for (i in 0..4) {
            val t = durationMs * i / 4
            paint.color = LuxeTheme.muted
            canvas.drawText(String.format(java.util.Locale.ROOT, "%.1fs", t / 1000f), x(t) - 10 * density, 20 * density, paint)
            paint.color = 0x15809a88
            canvas.drawLine(x(t), rowHeight, x(t), height.toFloat(), paint)
        }
        clips.forEachIndexed { index, clip ->
            val top = (index + 1) * rowHeight + 4 * density
            val bottom = top + rowHeight - 8 * density
            paint.color = LuxeTheme.text; paint.textSize = 11 * density
            canvas.drawText(clip.title.take(11), 8 * density, top + 19 * density, paint)
            val start = if (active?.id == clip.id) previewStart else clip.start
            val end = if (active?.id == clip.id) previewEnd else clip.end
            val l = x(start); val r = x(end)
            paint.color = if (clip.locked) LuxeTheme.border else if (clip.audio) 0xff245b70.toInt() else 0xff75552c.toInt()
            canvas.drawRoundRect(RectF(l, top, r, bottom), 6 * density, 6 * density, paint)
            clip.peaks?.let { peaks ->
                paint.color = 0xaaffffff.toInt(); paint.strokeWidth = density
                canvas.save(); canvas.clipRect(l + 6 * density, top, r - 6 * density, bottom)
                peaks.forEachIndexed { i, value ->
                    val xx = l + (r - l) * i / peaks.size
                    val amplitude = value.coerceIn(.03f, 1f) * (bottom - top) * .42f
                    canvas.drawLine(xx, (top + bottom) / 2 - amplitude, xx, (top + bottom) / 2 + amplitude, paint)
                }; canvas.restore()
            }
            paint.color = Color.WHITE; paint.strokeWidth = 2 * density
            canvas.drawLine(l + 4 * density, top + 8 * density, l + 4 * density, bottom - 8 * density, paint)
            canvas.drawLine(r - 4 * density, top + 8 * density, r - 4 * density, bottom - 8 * density, paint)
        }
        paint.color = LuxeTheme.gold; paint.strokeWidth = 2 * density
        canvas.drawLine(x(positionMs), rowHeight - 8 * density, x(positionMs), height.toFloat(), paint)
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val index = (event.y / rowHeight).toInt() - 1
                active = clips.getOrNull(index)
                downX = event.x; moved = false
                val clip = active
                if (clip != null && !clip.locked && event.x >= x(clip.start) - 12 * density && event.x <= x(clip.end) + 12 * density) {
                    previewStart = clip.start; previewEnd = clip.end
                    mode = when { abs(event.x - x(clip.start)) < 14 * density -> 1; abs(event.x - x(clip.end)) < 14 * density -> 2; else -> 3 }
                    parent?.requestDisallowInterceptTouchEvent(true)
                } else { active = null; mode = 0; if (index < 0) onScrub(time(event.x)) }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val clip = active ?: return true
                if (abs(event.x - downX) > 4 * density) moved = true
                val delta = ((event.x - downX) * durationMs / (right - left)).roundToLong()
                val end = clip.end.coerceAtMost(durationMs)
                when (mode) {
                    1 -> previewStart = (clip.start + delta).coerceIn(0, (end - 100).coerceAtLeast(0))
                    2 -> previewEnd = (end + delta).coerceIn((clip.start + 100).coerceAtMost(durationMs), durationMs)
                    3 -> { val length = (end - clip.start).coerceAtLeast(100).coerceAtMost(durationMs)
                        previewStart = (clip.start + delta).coerceIn(0, durationMs - length); previewEnd = previewStart + length }
                }
                invalidate(); return true
            }
            MotionEvent.ACTION_UP -> {
                active?.let { if (moved) onRange(it.id, previewStart, previewEnd) else onClip(it.id) }
                if (!moved) performClick()
                active = null; parent?.requestDisallowInterceptTouchEvent(false); invalidate(); return true
            }
            MotionEvent.ACTION_CANCEL -> { active = null; parent?.requestDisallowInterceptTouchEvent(false); invalidate(); return true }
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
