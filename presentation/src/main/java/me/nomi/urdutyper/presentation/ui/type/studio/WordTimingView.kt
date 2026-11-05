package me.nomi.urdutyper.presentation.ui.type.studio

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.abs
import kotlin.math.roundToLong

/** Timeline uses global movie milliseconds, including a voice track's offset and trim. */
class WordTimingView(context: Context) : View(context) {
    var words: List<ReviewWord> = emptyList()
    var peaks = FloatArray(0)
    var minimum = 0L; var maximum = 60000L
    var selected = 0
    var onSelect: (Int) -> Unit = {}
    var onEdit: (Int,Long,Long,Boolean) -> Unit = { _,_,_,_ -> }
    var zoom = 1f; private set
    private var left = 0.0
    val viewportLeft get()=left
    fun restoreViewport(value: Float, offset: Double) { zoom=value.coerceIn(1f,12f); left=offset.coerceIn(0.0,((maximum-minimum)-span()).coerceAtLeast(0.0)); invalidate() }
    private var downX=0f; private var originalLeft=0.0
    private var originalStart=0L; private var originalEnd=0L
    private var drag=0; private var moved=false
    private val density get()=resources.displayMetrics.density
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val scale=ScaleGestureDetector(context,object: ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean { setZoom(zoom*detector.scaleFactor); return true }
    })
    private fun span()=(maximum-minimum).coerceAtLeast(1).toDouble()/zoom
    private fun x(time: Long)=((time-minimum-left)/span()*width).toFloat()
    private fun time(x: Float)=(minimum+left+x/width.coerceAtLeast(1)*span()).roundToLong()
    fun setZoom(value: Float) { val center=left+span()/2; zoom=value.coerceIn(1f,12f); left=(center-span()/2).coerceIn(0.0,((maximum-minimum)-span()).coerceAtLeast(0.0)); invalidate() }
    fun focus(index: Int) { selected=index; words.getOrNull(index)?.startMs?.let { left=(it-minimum-span()*.3).coerceIn(0.0,((maximum-minimum)-span()).coerceAtLeast(0.0)) }; invalidate() }
    init { contentDescription="Word timing waveform. Pinch to zoom, drag empty space to scroll, tap a word and drag its gold edges to adjust timing."; setBackgroundColor(LuxeTheme.surface); isFocusable=true }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.textSize=11*density; paint.color=LuxeTheme.muted
        for(i in 0..4) { val t=time(width*i/4f); canvas.drawText("%.1fs".format(java.util.Locale.ROOT,t/1000f),width*i/4f,16*density,paint) }
        paint.color=0xff62c7ba.toInt(); paint.strokeWidth=2*density
        peaks.forEachIndexed { index,peak -> val xx=x(minimum+(maximum-minimum)*index/peaks.size.coerceAtLeast(1)); if(xx in 0f..width.toFloat()) { val h=peak.coerceIn(.02f,1f)*22*density; canvas.drawLine(xx,48*density-h,xx,48*density+h,paint) } }
        words.forEachIndexed { index,word ->
            val start=word.startMs ?: return@forEachIndexed; val end=word.endMs ?: return@forEachIndexed
            val rect=RectF(x(start),80*density,x(end).coerceAtLeast(x(start)+5*density),128*density)
            paint.color=if(index==selected) LuxeTheme.gold else LuxeTheme.border; canvas.drawRoundRect(rect,6*density,6*density,paint)
            canvas.save(); canvas.clipRect(rect); paint.color=if(index==selected) LuxeTheme.background else LuxeTheme.text; paint.textSize=14*density
            canvas.drawText(word.text,rect.left+8*density,rect.centerY()+5*density,paint); canvas.restore()
            if(index==selected) { paint.color=LuxeTheme.text; paint.strokeWidth=3*density; canvas.drawLine(rect.left+3*density,rect.top+10*density,rect.left+3*density,rect.bottom-10*density,paint); canvas.drawLine(rect.right-3*density,rect.top+10*density,rect.right-3*density,rect.bottom-10*density,paint) }
        }
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scale.onTouchEvent(event)
        if(event.pointerCount>1 || scale.isInProgress) { drag=0; parent?.requestDisallowInterceptTouchEvent(true); return true }
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true); downX=event.x; originalLeft=left; moved=false; drag=0
                val hit=if(event.y>72*density) words.indices.lastOrNull { val w=words[it]; w.startMs!=null && w.endMs!=null && event.x>=x(w.startMs!!)-12*density && event.x<=x(w.endMs!!)+12*density } else null
                if(hit!=null) { selected=hit; val w=words[hit]; originalStart=w.startMs!!; originalEnd=w.endMs!!; drag=when { abs(event.x-x(originalStart))<16*density -> 1; abs(event.x-x(originalEnd))<16*density -> 2; else -> 3 }; onSelect(hit); invalidate() }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val delta=((event.x-downX)/width.coerceAtLeast(1)*span()).roundToLong(); moved=moved || abs(event.x-downX)>4*density
                if(drag==0) { left=(originalLeft-delta).coerceIn(0.0,((maximum-minimum)-span()).coerceAtLeast(0.0)); invalidate() }
                else if(moved) {
                    val bounds=adjusted(selected,drag,delta,originalStart,originalEnd,words,minimum,maximum)
                    words=words.mapIndexed { i,w -> if(i==selected) w.copy(startMs=bounds.first,endMs=bounds.second,manual=true) else w }
                    onEdit(selected,bounds.first,bounds.second,false); invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if(drag!=0 && moved) { val w=words[selected]; onEdit(selected,w.startMs!!,w.endMs!!,true) }
                if(!moved) performClick(); parent?.requestDisallowInterceptTouchEvent(false); drag=0; return true
            }
        }
        return true
    }
    companion object {
        internal fun adjusted(index: Int, handle: Int, delta: Long, start: Long, end: Long, words: List<ReviewWord>, min: Long, max: Long): Pair<Long,Long> {
            val previous=words.take(index).mapNotNull { it.startMs }.maxOrNull() ?: min
            val next=words.drop(index+1).mapNotNull { it.startMs }.minOrNull() ?: max-20
            val lower=maxOf(min,previous); val upper=minOf(next,max-20).coerceAtLeast(lower)
            return when(handle) {
                1 -> (start+delta).coerceIn(lower,minOf(end-20,upper).coerceAtLeast(lower)) to end
                2 -> start to (end+delta).coerceIn(start+20,max.coerceAtLeast(start+20))
                else -> { val length=(end-start).coerceAtLeast(20); val s=(start+delta).coerceIn(lower,minOf(upper,max-length).coerceAtLeast(lower)); s to (s+length).coerceAtMost(max) }
            }
        }
    }
}
