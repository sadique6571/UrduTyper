package me.nomi.urdutyper.presentation.ui.type.studio

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.util.LruCache
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import me.nomi.urdutyper.R
import me.nomi.urdutyper.presentation.ui.type.ui.BACKGROUND_ENTRIES
import kotlin.math.*

val STUDIO_EFFECTS = listOf("Emboss", "Engrave", "Glow", "Outline", "Gradient", "Drop shadow",
    "Neon", "3D", "Long shadow", "Horizontal gradient", "Diagonal gradient", "Gold",
    "Rainbow", "Inner shadow", "Inner glow", "Pattern", "Reflection")

/** The same document renderer drives the canvas, draft thumbnails and every export format. */
class StudioRenderer(private val context: Context, private val fonts: StudioFonts) {
    private val images = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val layouts = LruCache<StudioLayer, StaticLayout>(32)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun image(path: String): Bitmap? {
        images.get(path)?.let { return it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 2048) inSampleSize *= 2
        }
        return BitmapFactory.decodeFile(path, options)?.also { images.put(path, it) }
    }

    fun layout(layer: StudioLayer): StaticLayout {
        val key = layer.copy(x = 0f, y = 0f, rotation = 0f, visible = true, locked = false)
        layouts.get(key)?.let { return it }
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = fonts.resolve(layer.fontId)
            // Synthetic styles preserve the chosen font instead of substituting a system face.
            isFakeBoldText = layer.bold
            textSkewX = if (layer.italic) -0.18f else 0f
            textSize = layer.size
            color = layer.color
        }
        val rtl = TextDirectionHeuristics.FIRSTSTRONG_LTR.isRtl(layer.text, 0, layer.text.length)
        return StaticLayout.Builder.obtain(layer.text, 0, layer.text.length, textPaint, layer.width.toInt().coerceAtLeast(1))
            .setAlignment(when (layer.alignment) {
                "left" -> if (rtl) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL
                "right" -> if (rtl) Layout.Alignment.ALIGN_NORMAL else Layout.Alignment.ALIGN_OPPOSITE
                else -> Layout.Alignment.ALIGN_CENTER
            })
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .setIncludePad(true).setLineSpacing(0f, layer.lineSpacing).setMaxLines(80)
            .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE).build().also { layouts.put(key, it) }
    }

    fun height(layer: StudioLayer): Float = if (layer.kind == "image") layer.height else layout(layer).height.toFloat()

    fun draw(canvas: Canvas, document: StudioDocument, transparent: Boolean = false) {
        val width = document.width.toFloat()
        val height = document.height.toFloat()
        canvas.save()
        canvas.clipRect(0f, 0f, width, height)
        if (!transparent && document.backgroundVisible) {
            paint.shader = LinearGradient(0f, 0f, width, height, document.backgroundColor, document.gradientColor, Shader.TileMode.CLAMP)
            paint.alpha = 255
            canvas.drawRect(0f, 0f, width, height, paint)
            paint.shader = null
            if (document.backgroundImage.isNotBlank()) {
                image(document.backgroundImage)?.let { crop(canvas, it, width, height) }
            } else if (document.backgroundStyle.isNotBlank()) {
                val res = BACKGROUND_ENTRIES.firstOrNull { it.styleName == document.backgroundStyle }?.drawableRes
                    ?: when (document.backgroundStyle) {
                        "INK_SPLATTER" -> R.drawable.template_bg_splatter
                        "STUDIO_GREEN" -> R.drawable.studio_hero
                        else -> R.drawable.gradient_card
                    }
                val drawable = ContextCompat.getDrawable(context, res)
                if (drawable is BitmapDrawable) crop(canvas, drawable.bitmap, width, height)
                else drawable?.apply { setBounds(0, 0, document.width, document.height); draw(canvas) }
            }
        }
        document.layers.filter { it.visible }.forEach { layer ->
            canvas.save()
            canvas.translate(layer.x, layer.y)
            canvas.rotate(layer.rotation)
            val h = height(layer)
            canvas.translate(-layer.width / 2, -h / 2)
            if (layer.kind == "image") {
                paint.alpha = (layer.opacity * 255).toInt()
                image(layer.imagePath)?.let { canvas.drawBitmap(it, null, RectF(0f, 0f, layer.width, h), paint) }
                paint.alpha = 255
            } else {
                val save = canvas.saveLayerAlpha(-layer.size, -layer.size, layer.width + layer.size,
                    h * 2 + layer.size, (layer.opacity * 255).toInt())
                drawText(canvas, layout(layer), layer)
                canvas.restoreToCount(save)
            }
            canvas.restore()
        }
        canvas.restore()
    }

    private fun crop(canvas: Canvas, bitmap: Bitmap, w: Float, h: Float) {
        val scale = maxOf(w / bitmap.width, h / bitmap.height)
        val dw = bitmap.width * scale
        val dh = bitmap.height * scale
        paint.alpha = 255
        canvas.drawBitmap(bitmap, null, RectF((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2), paint)
    }

    private fun drawText(canvas: Canvas, layout: StaticLayout, layer: StudioLayer) {
        val p = layout.paint
        p.shader = null; p.clearShadowLayer(); p.color = layer.color; p.style = Paint.Style.FILL
        val effects = layer.effects
        val unit = layer.size / 40f
        fun offset(dx: Float, dy: Float, color: Int) {
            canvas.save(); canvas.translate(dx * unit, dy * unit); p.color = color; layout.draw(canvas); canvas.restore()
            p.color = layer.color
        }
        if ("Long shadow" in effects) {
            for (i in 24 downTo 1) offset(i.toFloat(), i.toFloat(), 0x28000000)
        }
        if ("3D" in effects) {
            for (i in 6 downTo 1) offset(i.toFloat(), i.toFloat(), ColorUtils.blendARGB(layer.color, Color.BLACK, .5f))
        }
        if ("Emboss" in effects || "Engrave" in effects) {
            val sign = if ("Engrave" in effects) -1f else 1f
            offset(-sign, -sign, 0x99ffffff.toInt()); offset(sign, sign, 0x99000000.toInt())
        }
        if ("Drop shadow" in effects) { p.setShadowLayer(4 * unit, 3 * unit, 4 * unit, 0x99000000.toInt()); layout.draw(canvas); p.clearShadowLayer() }
        if ("Glow" in effects || "Neon" in effects) {
            p.setShadowLayer((if ("Neon" in effects) 18 else 10) * unit, 0f, 0f, layer.color)
            layout.draw(canvas); p.clearShadowLayer()
        }
        if ("Outline" in effects) {
            p.style = Paint.Style.STROKE; p.strokeWidth = 2 * unit; p.strokeJoin = Paint.Join.ROUND
            p.color = if (ColorUtils.calculateLuminance(layer.color) > .5) Color.BLACK else Color.WHITE
            layout.draw(canvas); p.style = Paint.Style.FILL; p.color = layer.color
        }
        val fill = effects.lastOrNull { it in listOf("Gradient", "Horizontal gradient", "Diagonal gradient", "Gold", "Rainbow", "Pattern") }
        if (fill != null) {
            val colors = when (fill) {
                "Gold" -> intArrayOf(0xfffff4c0.toInt(), 0xffd6a43b.toInt(), 0xff805316.toInt(), 0xfff7df8c.toInt())
                "Rainbow" -> intArrayOf(Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA)
                "Pattern" -> intArrayOf(layer.color, ColorUtils.blendARGB(layer.color, Color.WHITE, .7f), layer.color)
                else -> intArrayOf(0xff25b8ae.toInt(), 0xff287c60.toInt(), 0xffd5b35d.toInt())
            }
            p.shader = LinearGradient(0f, 0f,
                if (fill in listOf("Horizontal gradient", "Diagonal gradient", "Rainbow")) layer.width else 0f,
                if (fill == "Horizontal gradient" || fill == "Rainbow") 0f else if (fill == "Pattern") 18 * unit else layout.height.toFloat(),
                colors, null, if (fill == "Pattern") Shader.TileMode.REPEAT else Shader.TileMode.CLAMP)
        }
        layout.draw(canvas)
        p.shader = null
        for (inner in listOf("Inner shadow", "Inner glow")) if (inner in effects) {
            val saved = canvas.saveLayer(0f, 0f, layer.width, layout.height.toFloat(), null)
            p.style = Paint.Style.STROKE; p.strokeWidth = 3 * unit
            p.color = if (inner == "Inner glow") 0xaaffffff.toInt() else 0x88000000.toInt()
            layout.draw(canvas)
            p.style = Paint.Style.FILL; p.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            layout.draw(canvas); p.xfermode = null
            canvas.restoreToCount(saved); p.color = layer.color
        }
        if ("Reflection" in effects) {
            canvas.save(); canvas.translate(0f, layout.height * 2f + 8 * unit); canvas.scale(1f, -1f)
            p.alpha = 65; layout.draw(canvas); canvas.restore(); p.color = layer.color
        }
    }

    fun bitmap(document: StudioDocument, width: Int, transparent: Boolean): Bitmap {
        val height = (width.toFloat() / document.width * document.height).roundToInt().coerceAtLeast(1)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            val canvas = Canvas(it)
            canvas.scale(width.toFloat() / document.width, height.toFloat() / document.height)
            draw(canvas, document, transparent)
        }
    }
}

class StudioCanvas(context: Context, val renderer: StudioRenderer) : View(context) {
    var document = StudioDocument()
    var selectedId: String? = document.layers.firstOrNull()?.id
    var onChange: (StudioDocument) -> Unit = {}
    var onSelect: () -> Unit = {}
    var onEdit: () -> Unit = {}
    var onZoom: (Int) -> Unit = {}
    var moveCanvas = false
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    private val matrix = Matrix()
    private var lastX = 0f
    private var lastY = 0f
    private var lastSpan = 0f
    private var lastAngle = 0f
    private var changed = false
    private var handle = false
    private var downTime = 0L
    private var lastTap = 0L
    private var moved = false
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xff14785f.toInt(); style = Paint.Style.STROKE }
    init { setLayerType(LAYER_TYPE_SOFTWARE, null); contentDescription = "Design canvas. Tap a layer to select, double tap text to edit. Use two fingers to resize and rotate a selected layer, or Move canvas to zoom and pan."; isFocusable = true }
    private fun fit() = minOf((width - 40f) / document.width, (height - 40f) / document.height).coerceAtLeast(.01f)
    private fun updateMatrix() {
        val scale = fit() * zoom
        matrix.reset()
        matrix.postScale(scale, scale)
        matrix.postTranslate((width - document.width * scale) / 2 + panX, (height - document.height * scale) / 2 + panY)
    }
    private fun point(x: Float, y: Float): FloatArray {
        updateMatrix()
        val inverse = Matrix(); matrix.invert(inverse)
        return floatArrayOf(x, y).also { inverse.mapPoints(it) }
    }
    private fun local(layer: StudioLayer, x: Float, y: Float): FloatArray {
        val point = point(x, y)
        val rad = Math.toRadians(-layer.rotation.toDouble())
        val dx = point[0] - layer.x; val dy = point[1] - layer.y
        return floatArrayOf((dx * cos(rad) - dy * sin(rad)).toFloat(), (dx * sin(rad) + dy * cos(rad)).toFloat())
    }
    fun fitToScreen() { zoom = 1f; panX = 0f; panY = 0f; onZoom(100); invalidate() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(0xffe7e9e5.toInt())
        updateMatrix()
        canvas.save(); canvas.concat(matrix)
        val tile = 30f
        val checker = Paint().apply { color = 0xffdddddd.toInt() }
        canvas.drawRect(0f, 0f, document.width.toFloat(), document.height.toFloat(), Paint().apply { color = Color.WHITE })
        for (x in 0..(document.width / 30)) for (y in 0..(document.height / 30)) {
            if ((x + y) % 2 == 0) canvas.drawRect(x * tile, y * tile, minOf((x + 1) * tile, document.width.toFloat()), minOf((y + 1) * tile, document.height.toFloat()), checker)
        }
        renderer.draw(canvas, document)
        document.layers.firstOrNull { it.id == selectedId && it.visible }?.let {
            canvas.save(); canvas.translate(it.x, it.y); canvas.rotate(it.rotation)
            val h = renderer.height(it)
            val s = fit() * zoom
            border.strokeWidth = 1.5f / s
            canvas.drawRect(-it.width / 2, -h / 2, it.width / 2, h / 2, border)
            border.style = Paint.Style.FILL
            for (x in listOf(-it.width / 2, it.width / 2)) for (y in listOf(-h / 2, h / 2)) canvas.drawCircle(x, y, 5 / s, border)
            border.style = Paint.Style.STROKE
            canvas.restore()
        }
        canvas.restore()
    }
    private fun updateLayer(layer: StudioLayer) {
        document = document.copy(layers = document.layers.map { if (it.id == layer.id) layer else it })
        changed = true; invalidate()
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent.requestDisallowInterceptTouchEvent(true)
                lastX = event.x; lastY = event.y; changed = false; moved = false
                downTime = event.eventTime
                val current = document.layers.firstOrNull { it.id == selectedId && !it.locked && it.visible }
                handle = current?.let {
                    val p = local(it, event.x, event.y)
                    abs(abs(p[0]) - it.width / 2) < 24 / (fit() * zoom) && abs(abs(p[1]) - renderer.height(it) / 2) < 24 / (fit() * zoom)
                } ?: false
                if (!moveCanvas && !handle) {
                    selectedId = document.layers.asReversed().firstOrNull {
                        if (!it.visible) false else {
                            val p = local(it, event.x, event.y)
                            abs(p[0]) <= it.width / 2 && abs(p[1]) <= renderer.height(it) / 2
                        }
                    }?.id
                    onSelect(); invalidate()
                }
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> if (event.pointerCount >= 2) {
                lastSpan = hypot(event.getX(1) - event.getX(0), event.getY(1) - event.getY(0))
                lastAngle = atan2(event.getY(1) - event.getY(0), event.getX(1) - event.getX(0))
                lastX = (event.getX(0) + event.getX(1)) / 2; lastY = (event.getY(0) + event.getY(1)) / 2
                moved = true
            }
            MotionEvent.ACTION_MOVE -> {
                val layer = document.layers.firstOrNull { it.id == selectedId }
                if (event.pointerCount >= 2) {
                    val x = (event.getX(0) + event.getX(1)) / 2; val y = (event.getY(0) + event.getY(1)) / 2
                    val span = hypot(event.getX(1) - event.getX(0), event.getY(1) - event.getY(0))
                    val angle = atan2(event.getY(1) - event.getY(0), event.getX(1) - event.getX(0))
                    val ratio = if (lastSpan > 8) (span / lastSpan).coerceIn(.8f, 1.25f) else 1f
                    if (!moveCanvas && layer != null && !layer.locked) {
                        val scale = fit() * zoom
                        val factor = ratio.coerceIn(maxOf(24f / layer.width, 8f / layer.size), minOf(8192f / layer.width, 600f / layer.size))
                        updateLayer(layer.copy(x = (layer.x + (x - lastX) / scale).coerceIn(0f, document.width.toFloat()),
                            y = (layer.y + (y - lastY) / scale).coerceIn(0f, document.height.toFloat()),
                            width = layer.width * factor, height = layer.height * factor, size = layer.size * factor,
                            rotation = ((layer.rotation + Math.toDegrees((angle - lastAngle).toDouble()).toFloat()) % 360 + 360) % 360))
                    } else {
                        val anchor = point(lastX, lastY)
                        zoom = (zoom * ratio).coerceIn(.4f, 5f)
                        val scale = fit() * zoom
                        panX = x - (width - document.width * scale) / 2 - anchor[0] * scale
                        panY = y - (height - document.height * scale) / 2 - anchor[1] * scale
                        onZoom((zoom * 100).toInt()); invalidate()
                    }
                    lastSpan = span; lastAngle = angle; lastX = x; lastY = y; moved = true
                } else {
                    val dx = event.x - lastX; val dy = event.y - lastY
                    if (abs(dx) + abs(dy) > 2) moved = true
                    if (moveCanvas) { panX += dx; panY += dy; invalidate() }
                    else if (layer != null && !layer.locked) {
                        val scale = fit() * zoom
                        if (handle) {
                            val p = local(layer, event.x, event.y)
                            val factor = (hypot(p[0], p[1]) / hypot(layer.width / 2, renderer.height(layer) / 2)).coerceIn(.8f, 1.25f)
                                .coerceIn(maxOf(24f / layer.width, 8f / layer.size), minOf(8192f / layer.width, 600f / layer.size))
                            updateLayer(layer.copy(width = layer.width * factor, height = layer.height * factor, size = layer.size * factor))
                        } else if (moved) updateLayer(layer.copy(x = (layer.x + dx / scale).coerceIn(0f, document.width.toFloat()), y = (layer.y + dy / scale).coerceIn(0f, document.height.toFloat())))
                    }
                    lastX = event.x; lastY = event.y
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val index = if (event.actionIndex == 0) 1 else 0
                lastX = event.getX(index); lastY = event.getY(index); lastSpan = 0f
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (changed) onChange(document)
                if (!moved && event.actionMasked == MotionEvent.ACTION_UP) {
                    performClick()
                    if (event.eventTime - lastTap < 350 && event.eventTime - downTime < 300) onEdit()
                    lastTap = event.eventTime
                }
                parent.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }
}
