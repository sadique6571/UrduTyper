package me.nomi.urdutyper.presentation.ui.type.studio

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.Executors

/** One worker, one pending snapshot: scrubbing never queues hundreds of stale render jobs. */
internal class PreviewFrames(private val context: Context) {
    private data class Request(val document: StudioDocument, val time: Long?, val width: Int, val ready: (Bitmap) -> Unit)
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor { runnable -> Thread(runnable,"StudioPreview").apply { priority=Thread.NORM_PRIORITY-1 } }
    private val renderer=StudioRenderer(context.applicationContext,StudioFonts(context.applicationContext))
    private val animation=StudioAnimationRenderer(renderer)
    private var pending: Request?=null
    private var running=false; private var closed=false
    private var previous: Request?=null
    var slow=false; private set
    private var frames=0; private var totalMs=0L
    @Synchronized fun request(document: StudioDocument, time: Long?, width: Int, ready: (Bitmap) -> Unit) {
        if(closed) return
        val next=Request(document,time,width,ready)
        if(previous?.let { it.document==document && it.time==time && it.width==width }==true) return
        previous=next; pending=next
        if(!running) { running=true; worker.execute { drain() } }
    }
    private fun drain() {
        while(true) {
            val next=synchronized(this) { if(closed || pending==null) { running=false; null } else pending.also { pending=null } } ?: return
            val start=SystemClock.elapsedRealtime()
            val result=runCatching { if(next.time==null) renderer.bitmap(next.document,next.width,false) else animation.bitmap(next.document,next.width,next.time) }.getOrNull()
            val elapsed=SystemClock.elapsedRealtime()-start
            synchronized(this) { frames++; totalMs+=elapsed; slow=frames>=3 && totalMs/frames>55 }
            if(result!=null) main.post { synchronized(this) { if(closed) result.recycle() else next.ready(result) } }
        }
    }
    @Synchronized fun close() {
        closed=true; pending=null; worker.shutdown()
        android.util.Log.d("StudioPerformance","Preview frames=$frames meanMs=${if(frames==0) 0 else totalMs/frames} ${renderer.cacheStats()}")
    }
}
