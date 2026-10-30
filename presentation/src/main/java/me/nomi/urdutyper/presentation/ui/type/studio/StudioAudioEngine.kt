package me.nomi.urdutyper.presentation.ui.type.studio

import android.media.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/** Decode only the selected range, mix two tracks at 44.1 kHz stereo, then encode AAC. */
object StudioAudioEngine {
    const val RATE = 44100
    private fun checkCancelled(cancelled: () -> Boolean) {
        if (cancelled()) throw java.util.concurrent.CancellationException("Export cancelled")
    }
    private fun decode(track: StudioAudio, maxMs: Long, cancelled: () -> Boolean): FloatArray {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            extractor.setDataSource(track.path)
            val index = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?: error("No audio found in ${track.name}")
            extractor.selectTrack(index)
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var encoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) format.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
            val lengthMs = minOf(track.durationMs(), maxMs).coerceAtLeast(0)
            val output = FloatArray((lengthMs * RATE / 1000).toInt() * 2)
            extractor.seekTo(track.trimStartMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            fun accept(buffer: ByteBuffer, ptsUs: Long) {
                require(channels in 1..8 && rate > 0) { "Unsupported audio format" }
                require(encoding == AudioFormat.ENCODING_PCM_16BIT || encoding == AudioFormat.ENCODING_PCM_FLOAT) { "Audio must decode to 16-bit or float PCM" }
                val bytes = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
                buffer.order(ByteOrder.LITTLE_ENDIAN)
                val frames = buffer.remaining() / (bytes * channels)
                val samples = FloatArray(frames * 2)
                for (frame in 0 until frames) {
                    var left = 0f; var right = 0f
                    for (channel in 0 until channels) {
                        val value = if (bytes == 4) buffer.float else buffer.short / 32768f
                        if (channel == 0) left = value
                        if (channel == 1) right = value
                    }
                    samples[frame * 2] = left; samples[frame * 2 + 1] = if (channels == 1) left else right
                }
                val startFrame = ((ptsUs - track.trimStartMs * 1000) * RATE / 1_000_000.0)
                val first = maxOf(0, ceil(startFrame).toInt())
                val end = minOf(output.size / 2, floor(startFrame + frames.toDouble() * RATE / rate).toInt())
                for (dest in first until end) {
                    val source = ((dest - startFrame) * rate / RATE).coerceIn(0.0, (frames - 1).coerceAtLeast(0).toDouble())
                    val a = source.toInt(); val b = minOf(a + 1, frames - 1); val fraction = (source - a).toFloat()
                    if (frames > 0) for (channel in 0..1) output[dest * 2 + channel] = samples[a * 2 + channel] * (1 - fraction) + samples[b * 2 + channel] * fraction
                }
            }
            val endUs = (track.trimStartMs + lengthMs) * 1000
            if (mime == "audio/raw") {
                val buffer = ByteBuffer.allocateDirect(262144)
                while (extractor.sampleTime >= 0 && extractor.sampleTime < endUs) {
                    checkCancelled(cancelled); buffer.clear()
                    val count = extractor.readSampleData(buffer, 0)
                    if (count < 0) break
                    buffer.position(0); buffer.limit(count); accept(buffer, extractor.sampleTime); extractor.advance()
                }
            } else {
                val codec = MediaCodec.createDecoderByType(mime); decoder = codec
                codec.configure(format, null, null, 0); codec.start()
                var inputDone = false; var outputDone = false; var idle = 0
                val info = MediaCodec.BufferInfo()
                while (!outputDone) {
                    checkCancelled(cancelled)
                    var progressed = false
                    if (!inputDone) {
                        val input = codec.dequeueInputBuffer(10000)
                        if (input >= 0) {
                            val buffer = codec.getInputBuffer(input)!!; buffer.clear()
                            val pts = extractor.sampleTime
                            val count = if (pts < 0 || pts >= endUs) -1 else extractor.readSampleData(buffer, 0)
                            if (count < 0) { codec.queueInputBuffer(input, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                            else { codec.queueInputBuffer(input, 0, count, pts, 0); extractor.advance() }
                            progressed = true
                        }
                    }
                    when (val out = codec.dequeueOutputBuffer(info, 10000)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val decoded = codec.outputFormat
                            rate = decoded.getInteger(MediaFormat.KEY_SAMPLE_RATE); channels = decoded.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            encoding = if (decoded.containsKey(MediaFormat.KEY_PCM_ENCODING)) decoded.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                            progressed = true
                        }
                        else -> if (out >= 0) {
                            if (info.size > 0) codec.getOutputBuffer(out)?.let { buffer -> buffer.position(info.offset); buffer.limit(info.offset + info.size); accept(buffer.slice(), info.presentationTimeUs) }
                            outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            codec.releaseOutputBuffer(out, false); progressed = true
                        }
                    }
                    idle = if (progressed) 0 else idle + 1
                    check(idle < 1000) { "Audio decoder timed out" }
                }
            }
            return output
        } finally { decoder?.let { runCatching { it.stop() }; it.release() }; extractor.release() }
    }
    fun encode(document: StudioDocument, target: File, cancelled: () -> Boolean) {
        val tracks = document.audio.take(2).filter { it.startMs < document.video.durationMs && it.volume > 0 }
        val decoded = tracks.map { decode(it, document.video.durationMs - it.startMs, cancelled) }
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, RATE, 2).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 160000); setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val muxer = MediaMuxer(target.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var started = false; var trackIndex = -1
        var firstPts: Long? = null
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); codec.start()
            val frames = (document.video.durationMs * RATE / 1000).toInt()
            var cursor = 0; var inputDone = false; var outputDone = false; var idle = 0
            val info = MediaCodec.BufferInfo()
            while (!outputDone) {
                checkCancelled(cancelled); var progressed = false
                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(10000)
                    if (index >= 0) {
                        val buffer = codec.getInputBuffer(index)!!.apply { clear(); order(ByteOrder.LITTLE_ENDIAN) }
                        val count = minOf(buffer.remaining() / 4, frames - cursor)
                        for (i in 0 until count) {
                            val frame = cursor + i; val ms = frame * 1000L / RATE
                            for (channel in 0..1) {
                                var mixed = 0f
                                tracks.forEachIndexed { n, audio ->
                                    val source = frame - (audio.startMs * RATE / 1000).toInt()
                                    if (source >= 0 && source * 2 + channel < decoded[n].size) mixed += decoded[n][source * 2 + channel] * audio.gainAt(ms,document.video.durationMs)
                                }
                                buffer.putShort((mixed.coerceIn(-1f, 1f) * 32767).roundToInt().toShort())
                            }
                        }
                        val flags = if (cursor + count >= frames) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                        codec.queueInputBuffer(index, 0, count * 4, cursor * 1_000_000L / RATE, flags)
                        cursor += count; inputDone = flags != 0; progressed = true
                    }
                }
                when (val out = codec.dequeueOutputBuffer(info, 10000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> { trackIndex = muxer.addTrack(codec.outputFormat); muxer.start(); started = true; progressed = true }
                    else -> if (out >= 0) {
                        if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            val buffer = codec.getOutputBuffer(out)!!; buffer.position(info.offset); buffer.limit(info.offset + info.size)
                            if (firstPts == null) firstPts = info.presentationTimeUs
                            info.presentationTimeUs = (info.presentationTimeUs - firstPts!!).coerceAtLeast(0)
                            check(started); muxer.writeSampleData(trackIndex, buffer, info)
                        }
                        outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(out, false); progressed = true
                    }
                }
                idle = if (progressed) 0 else idle + 1; check(idle < 1000) { "Audio encoder timed out" }
            }
            check(started) { "Audio encoder produced no stream" }
            muxer.stop(); started = false
        } finally {
            runCatching { codec.stop() }; codec.release()
            if (started) runCatching { muxer.stop() }; muxer.release()
        }
    }
}
