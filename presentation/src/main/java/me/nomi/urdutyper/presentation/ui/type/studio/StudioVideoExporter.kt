package me.nomi.urdutyper.presentation.ui.type.studio

import android.content.Context
import android.graphics.Bitmap
import android.media.*
import android.opengl.*
import android.view.Surface
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

object StudioVideoExporter {
    fun dimensions(document: StudioDocument, quality: Int): Pair<Int, Int> {
        val edge = if (quality <= 720) 1280 else 1920
        val scale = minOf(quality.toFloat() / minOf(document.width, document.height), edge.toFloat() / maxOf(document.width, document.height))
        return ((document.width * scale).toInt() / 2 * 2).coerceAtLeast(64) to ((document.height * scale).toInt() / 2 * 2).coerceAtLeast(64)
    }
    fun export(context: Context, document: StudioDocument, target: File, quality: Int = 720,
               progress: (Int) -> Unit = {}, cancelled: () -> Boolean = { false }) {
        require(document.video.durationMs in 1000..60000) { "Video duration must be 1–60 seconds" }
        val (width, height) = dimensions(document, quality)
        val video = File.createTempFile("studio-video-", ".mp4", context.cacheDir)
        val audio = File.createTempFile("studio-audio-", ".m4a", context.cacheDir)
        try {
            encodeVideo(context, document, video, width, height, { progress(it * 85 / 100) }, cancelled)
            if (document.audio.any { it.volume > 0 && it.startMs < document.video.durationMs }) {
                progress(87); StudioAudioEngine.encode(document, audio, cancelled)
                progress(96); mux(video, audio, target, cancelled)
            } else { video.copyTo(target, overwrite = true) }
            progress(100)
        } catch (error: Throwable) { target.delete(); throw error }
        finally { video.delete(); audio.delete() }
    }
    private fun encodeVideo(context: Context, document: StudioDocument, target: File, width: Int, height: Int,
                            progress: (Int) -> Unit, cancelled: () -> Boolean) {
        val fps = 24
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, width * height * 4)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps); setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val muxer = MediaMuxer(target.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var egl: EncoderSurface? = null; var surface: Surface? = null
        var started = false; var track = -1; var eos = false
        val info = MediaCodec.BufferInfo()
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            surface = codec.createInputSurface(); codec.start()
            egl = EncoderSurface(surface, width, height)
            fun drain(final: Boolean) {
                var idle = 0
                while (true) {
                    if (cancelled()) throw java.util.concurrent.CancellationException("Export cancelled")
                    when (val out = codec.dequeueOutputBuffer(info, if (final) 10000 else 0)) {
                        MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (!final) return
                            check(++idle < 1500) { "Video encoder timed out" }
                        }
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            check(!started); track = muxer.addTrack(codec.outputFormat); muxer.start(); started = true
                        }
                        else -> if (out >= 0) {
                            idle = 0
                            if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                check(started); val buffer = codec.getOutputBuffer(out)!!
                                buffer.position(info.offset); buffer.limit(info.offset + info.size)
                                muxer.writeSampleData(track, buffer, info)
                            }
                            eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            codec.releaseOutputBuffer(out, false)
                            if (eos) return
                        }
                    }
                }
            }
            val renderer = StudioAnimationRenderer(StudioRenderer(context, StudioFonts(context)))
            val frames = ((document.video.durationMs * fps + 999) / 1000).toInt()
            for (frame in 0 until frames) {
                if (cancelled()) throw java.util.concurrent.CancellationException("Export cancelled")
                val time = frame * 1000L / fps
                val bitmap = renderer.bitmap(document, width, time)
                try { egl.draw(bitmap, frame * 1_000_000_000L / fps) } finally { bitmap.recycle() }
                drain(false); progress((frame + 1) * 100 / frames)
            }
            codec.signalEndOfInputStream(); drain(true)
            check(started && eos) { "Video did not finish encoding" }
            muxer.stop(); started = false
        } finally {
            egl?.release(); surface?.release()
            runCatching { codec.stop() }; codec.release()
            if (started) runCatching { muxer.stop() }; muxer.release()
        }
    }
    private fun mux(video: File, audio: File, target: File, cancelled: () -> Boolean) {
        val muxer = MediaMuxer(target.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val sources = listOf(MediaExtractor(), MediaExtractor())
        var started = false
        try {
            val mappings = sources.mapIndexed { index, extractor ->
                extractor.setDataSource(if (index == 0) video.path else audio.path)
                val type = if (index == 0) "video/" else "audio/"
                val source = (0 until extractor.trackCount).first { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!!.startsWith(type) }
                val track = muxer.addTrack(extractor.getTrackFormat(source)); extractor.selectTrack(source)
                track
            }
            muxer.start(); started = true
            val buffer = ByteBuffer.allocateDirect(4 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            while (true) {
                if (cancelled()) throw java.util.concurrent.CancellationException("Export cancelled")
                val index = sources.indices.filter { sources[it].sampleTime >= 0 }.minByOrNull { sources[it].sampleTime } ?: break
                val extractor = sources[index]; buffer.clear()
                val count = extractor.readSampleData(buffer, 0)
                if (count < 0) break
                info.set(0, count, extractor.sampleTime, if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                buffer.position(0); buffer.limit(count); muxer.writeSampleData(mappings[index], buffer, info); extractor.advance()
            }
            muxer.stop(); started = false
        } finally { sources.forEach { it.release() }; if (started) runCatching { muxer.stop() }; muxer.release() }
    }
}

/** A codec input Surface requires EGL/GLES; Canvas.lockCanvas is not supported there. */
private class EncoderSurface(surface: Surface, private val width: Int, private val height: Int) {
    private val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    private var context = EGL14.EGL_NO_CONTEXT
    private var window = EGL14.EGL_NO_SURFACE
    private var program = 0
    private var texture = 0
    private val vertices = ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f,-1f,0f,1f, 1f,-1f,1f,1f, -1f,1f,0f,0f, 1f,1f,1f,0f)); position(0)
    }
    init {
        try {
            check(display != EGL14.EGL_NO_DISPLAY && EGL14.eglInitialize(display, IntArray(2), 0, IntArray(2), 0)) { "Cannot initialize video graphics" }
            val attributes = intArrayOf(EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,
                EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,0x3142,1,EGL14.EGL_NONE)
            val configs = arrayOfNulls<EGLConfig>(1); val count = IntArray(1)
            check(EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) && count[0] > 0) { "No recording graphics configuration" }
            context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE),0)
            window = EGL14.eglCreateWindowSurface(display, configs[0], surface, intArrayOf(EGL14.EGL_NONE),0)
            check(EGL14.eglMakeCurrent(display, window, window, context))
            fun shader(type: Int, source: String): Int {
                val id = GLES20.glCreateShader(type); GLES20.glShaderSource(id, source); GLES20.glCompileShader(id)
                val status = IntArray(1); GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, status, 0)
                check(status[0] != 0) { GLES20.glGetShaderInfoLog(id) }; return id
            }
            val vertex = shader(GLES20.GL_VERTEX_SHADER,"attribute vec2 position; attribute vec2 uv; varying vec2 coord; void main(){gl_Position=vec4(position,0.,1.);coord=uv;}")
            val fragment = shader(GLES20.GL_FRAGMENT_SHADER,"precision mediump float; varying vec2 coord; uniform sampler2D image; void main(){gl_FragColor=texture2D(image,coord);}")
            program = GLES20.glCreateProgram(); GLES20.glAttachShader(program, vertex); GLES20.glAttachShader(program, fragment); GLES20.glLinkProgram(program)
            GLES20.glDeleteShader(vertex); GLES20.glDeleteShader(fragment)
            val linked = IntArray(1); GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0); check(linked[0] != 0) { GLES20.glGetProgramInfoLog(program) }
            val ids = IntArray(1); GLES20.glGenTextures(1, ids, 0); texture = ids[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        } catch (error: Throwable) { release(); throw error }
    }
    fun draw(bitmap: Bitmap, ptsNs: Long) {
        GLES20.glViewport(0,0,width,height); GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bitmap,0)
        val position = GLES20.glGetAttribLocation(program,"position"); val uv = GLES20.glGetAttribLocation(program,"uv")
        vertices.position(0); GLES20.glVertexAttribPointer(position,2,GLES20.GL_FLOAT,false,16,vertices); GLES20.glEnableVertexAttribArray(position)
        vertices.position(2); GLES20.glVertexAttribPointer(uv,2,GLES20.GL_FLOAT,false,16,vertices); GLES20.glEnableVertexAttribArray(uv)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"image"),0); GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4)
        check(GLES20.glGetError() == GLES20.GL_NO_ERROR) { "Video graphics failed" }
        EGLExt.eglPresentationTimeANDROID(display,window,ptsNs); check(EGL14.eglSwapBuffers(display,window)) { "Cannot submit video frame" }
    }
    fun release() {
        if (display != EGL14.EGL_NO_DISPLAY) {
            if (texture != 0) GLES20.glDeleteTextures(1,intArrayOf(texture),0)
            if (program != 0) GLES20.glDeleteProgram(program)
            EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT)
            if (window != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display,window)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display,context)
            EGL14.eglReleaseThread(); EGL14.eglTerminate(display)
        }
    }
}
