package me.nomi.urdutyper.presentation.ui.type.studio

import android.content.Context
import java.io.File
import java.io.OutputStream

/** GIF89a, 256-color RGB332 palette. Dictionary resets keep every LZW code at 9 bits. */
object StudioGifExporter {
    fun export(context: Context, document: StudioDocument, target: File, progress: (Int) -> Unit = {}, cancelled: () -> Boolean = { false }) {
        require(document.video.durationMs in 1000..10000) { "GIF is limited to 10 seconds; use MP4 for longer scenes" }
        val width = minOf(360, document.width)
        val renderer = StudioAnimationRenderer(StudioRenderer(context, StudioFonts(context)))
        val height = (width.toFloat() / document.width * document.height).toInt().coerceAtLeast(1)
        require(height <= 640) { "For GIF use square, post or story size" }
        try {
            target.outputStream().buffered().use { output ->
                fun word(value: Int) { output.write(value and 255); output.write(value shr 8 and 255) }
                output.write("GIF89a".toByteArray(Charsets.US_ASCII)); word(width); word(height)
                output.write(0xf7); output.write(0); output.write(0)
                for (index in 0..255) {
                    output.write((index shr 5 and 7) * 255 / 7)
                    output.write((index shr 2 and 7) * 255 / 7)
                    output.write((index and 3) * 255 / 3)
                }
                output.write(byteArrayOf(0x21,0xff.toByte(),11)); output.write("NETSCAPE2.0".toByteArray())
                output.write(byteArrayOf(3,1,0,0,0))
                val frames = ((document.video.durationMs + 124) / 125).toInt()
                for (frame in 0 until frames) {
                    if (cancelled()) throw java.util.concurrent.CancellationException("Export cancelled")
                    val bitmap = renderer.bitmap(document, width, frame * 125L)
                    val pixels = IntArray(width * height)
                    try { bitmap.getPixels(pixels,0,width,0,0,width,height) } finally { bitmap.recycle() }
                    output.write(byteArrayOf(0x21,0xf9.toByte(),4,8)); word(if (frame % 2 == 0) 12 else 13); output.write(0); output.write(0)
                    output.write(0x2c); word(0); word(0); word(width); word(height); output.write(0)
                    output.write(8)
                    codes(output, pixels)
                    progress((frame + 1) * 100 / frames)
                }
                output.write(0x3b)
            }
        } catch (error: Throwable) { target.delete(); throw error }
    }
    private fun codes(output: OutputStream, pixels: IntArray) {
        val block = ByteArray(255); var size = 0; var bits = 0; var count = 0
        fun byte(value: Int) {
            block[size++] = value.toByte()
            if (size == 255) { output.write(size); output.write(block,0,size); size = 0 }
        }
        fun code(value: Int) {
            bits = bits or (value shl count); count += 9
            while (count >= 8) { byte(bits and 255); bits = bits ushr 8; count -= 8 }
        }
        code(256)
        pixels.forEachIndexed { index, color ->
            if (index > 0 && index % 200 == 0) code(256)
            code((color shr 16 and 0xe0) or (color shr 11 and 0x1c) or (color shr 6 and 3))
        }
        code(257); if (count > 0) byte(bits and 255)
        if (size > 0) { output.write(size); output.write(block,0,size) }
        output.write(0)
    }
}
