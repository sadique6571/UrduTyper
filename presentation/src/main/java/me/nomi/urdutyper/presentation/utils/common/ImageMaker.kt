package me.nomi.urdutyper.presentation.utils.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.BitmapShader
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.text.Layout
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.ColorUtils
import me.nomi.urdutyper.R
import me.nomi.urdutyper.domain.entity.Image
import me.nomi.urdutyper.presentation.utils.extensions.common.dialog
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ImageMaker {
    private fun getBitmapFromView(view: View): Bitmap {
        val returnedBitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(returnedBitmap)
        val bgDrawable = view.background
        if (bgDrawable != null) {
            bgDrawable.draw(canvas)
        } else {
            canvas.drawColor(Color.WHITE)
        }
        view.draw(canvas)
        return returnedBitmap
    }

    private fun getPictureDir(context: Context): File? {
        // Pictures, not DCIM: DCIM is what Google Photos/Drive auto-backup
        // picks up by default, Pictures is not.
        val pictureFileDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "Nomiii"
        )
        if (!pictureFileDir.exists() && !pictureFileDir.mkdirs()) {
            context.dialog("Failed to create the directory!").show()
            return null
        }
        return pictureFileDir
    }

    // Tell the system gallery about a new file right away — without this,
    // MediaStore only discovers it on its own slow scan and the poster
    // takes ages to appear in the gallery.
    private fun scanFile(context: Context, file: File) {
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/jpeg"), null)
    }

    fun saveBitMap(context: Context, drawView: View): File? {
        val pictureFileDir = getPictureDir(context) ?: return null

        val formatter = SimpleDateFormat("yyyyMMdd_hhmmss", Locale.getDefault())
        val now = Date()
        val filename = "${pictureFileDir.path}${File.separator}UT${formatter.format(now)}.jpg"
        val pictureFile = File(filename)
        val bitmap = getBitmapFromView(drawView)
        try {
            val oStream = FileOutputStream(pictureFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, oStream)
            oStream.flush()
            oStream.close()
            scanFile(context, pictureFile)
            context.dialog("Saved: $filename").show()
        } catch (e: IOException) {
            e.printStackTrace()
            context.dialog(e.localizedMessage ?: "There was an error saving the image").show()
        }
        return pictureFile
    }

    // Repeating diagonal-stripe tile for PATTERN_FILL (also used by the
    // live editor, which can shade an EditText paint with a BitmapShader).
    // Generated in code so no asset file is needed.
    fun patternTile(mainColor: Int, scale: Float): Bitmap {
        val size = (48f * scale).toInt().coerceAtLeast(48)
        val tile = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(tile)
        canvas.drawColor(mainColor)
        val lum = (0.299f * Color.red(mainColor) +
            0.587f * Color.green(mainColor) +
            0.114f * Color.blue(mainColor)) / 255f
        val stripe = Paint().apply {
            color = if (lum >= 0.5f)
                ColorUtils.blendARGB(mainColor, Color.BLACK, 0.35f)
            else
                ColorUtils.blendARGB(mainColor, Color.WHITE, 0.35f)
            strokeWidth = size / 6f
            isAntiAlias = true
        }
        val step = size / 3
        var x = -size
        while (x <= 2 * size) {
            canvas.drawLine(x.toFloat(), size.toFloat(), (x + size).toFloat(), 0f, stripe)
            x += step
        }
        return tile
    }

    // Renders the poster off-screen at targetWidth x targetHeight and
    // returns the bitmap; callers save it. NOT an upscale of the screen
    // capture: the background drawable scales losslessly and the text is
    // laid out fresh at the target size, so Nastaliq stays crisp. The
    // on-screen view is never touched: no menu/cursor hiding and no
    // modification of the live text.
    // effect: 0 = none, 1 = emboss, 2 = engrave, 3 = glow, 4 = outline,
    // 5 = gradient, 6 = drop shadow, 7 = neon, 8 = 3D, 9 = long shadow,
    // 10 = gradient horizontal, 11 = gradient diagonal, 12 = gradient gold,
    // 13 = gradient rainbow, 14 = inner shadow, 15 = inner glow,
    // 16 = pattern fill, 17 = reflection — matches TypeFragment's
    // TextEffect ordinals.
    private fun renderPosterBitmap(
        context: Context,
        posterView: View,
        textView: EditText,
        effect: Int,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap {
        val scale = if (posterView.width > 0) targetWidth.toFloat() / posterView.width else 1f

        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bgDrawable = posterView.background
        if (bgDrawable is BitmapDrawable && bgDrawable.bitmap != null &&
            bgDrawable.gravity != Gravity.FILL
        ) {
            // Center-gravity bitmap wrappers (picture templates, background
            // library) draw at intrinsic size: on the phone that overflows
            // and looks full-bleed, but on the wider HD canvas it leaves
            // black bars and the full-width text sticks out past the
            // picture. Draw it centerCrop instead: scale to cover the whole
            // canvas, centred — no bars, text always stays over picture.
            val bmp = bgDrawable.bitmap!!
            val s = maxOf(
                targetWidth.toFloat() / bmp.width,
                targetHeight.toFloat() / bmp.height
            )
            val sw = bmp.width * s
            val sh = bmp.height * s
            val left = (targetWidth - sw) / 2f
            val top = (targetHeight - sh) / 2f
            canvas.drawBitmap(bmp, null, RectF(left, top, left + sw, top + sh), null)
        } else if (bgDrawable != null) {
            bgDrawable.setBounds(0, 0, targetWidth, targetHeight)
            bgDrawable.draw(canvas)
            // Restore the live view's bounds — the drawable is shared with it.
            bgDrawable.setBounds(0, 0, posterView.width, posterView.height)
        } else {
            canvas.drawColor(Color.WHITE)
        }

        val rawText = textView.text.toString()
        // Same last-line trick as the normal save: justificationMode leaves the
        // last line unjustified, so a trailing newline (on the copy only, the
        // live text is never modified) stretches it to full width too.
        val captureText = if (rawText.isNotEmpty() && !rawText.endsWith("\n")) "$rawText\n" else rawText
        val offscreenText = TextView(context).apply {
            if (captureText.isNotEmpty()) {
                text = captureText
            } else {
                hint = textView.hint
                setHintTextColor(textView.hintTextColors)
            }
            typeface = textView.typeface
            setTextColor(textView.currentTextColor)
            gravity = Gravity.CENTER
            justificationMode = Layout.JUSTIFICATION_MODE_INTER_WORD
            setTextSize(TypedValue.COMPLEX_UNIT_PX, textView.textSize * scale)
            setPadding(
                (textView.paddingLeft * scale).toInt(),
                (textView.paddingTop * scale).toInt(),
                (textView.paddingRight * scale).toInt(),
                (textView.paddingBottom * scale).toInt()
            )
            textDirection = textView.textDirection
            layoutDirection = textView.layoutDirection
        }
        // HD text box = the LIVE EditText's box, scaled — NOT the full canvas.
        // Measuring at the full 1440 made a pinch-zoomed text scale past the
        // canvas edges (the editor's narrower EditText absorbed the zoom, so
        // normal save looked fine while HD overflowed both sides).
        val textBoxWidth = if (textView.width > 0) (textView.width * scale).toInt() else targetWidth
        offscreenText.measure(
            View.MeasureSpec.makeMeasureSpec(textBoxWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val textHeight = offscreenText.measuredHeight
        // Reproduce the editor's transforms: MultiTouch lets the user drag
        // (translation), pinch-zoom (scale) and twist (rotation) the text, but
        // the old HD render ignored all three — a tilted text came out
        // straight. draw() also ignores layout() position, so the old code's
        // "centered" text actually landed at the top. Center the block on the
        // live text's center (poster coords -> HD coords), then rotate and
        // zoom about that center, exactly like the live view's matrix.
        val liveCenterX = (textView.left + textView.width / 2f + textView.translationX) * scale
        val liveCenterY = (textView.top + textView.height / 2f + textView.translationY) * scale
        canvas.save()
        canvas.translate(liveCenterX - textBoxWidth / 2f, liveCenterY - textHeight / 2f)
        if (textView.rotation != 0f) canvas.rotate(textView.rotation, textBoxWidth / 2f, textHeight / 2f)
        if (textView.scaleX != 1f) canvas.scale(textView.scaleX, textView.scaleX, textBoxWidth / 2f, textHeight / 2f)
        offscreenText.layout(0, 0, textBoxWidth, textHeight)
        // Drawn with the layout's own paint (the paint it actually draws
        // with — setTextColor alone does not reach it).
        val layoutPaint = offscreenText.layout.paint
        when (effect) {
            0 -> offscreenText.draw(canvas)
            1, 2 -> {
                // Photoshop-style bevel: highlight pass + shadow pass +
                // main pass. EMBOSS: light top-left, dark bottom-right.
                // ENGRAVE: swapped. Offsets rotate with the text because
                // they sit inside the transform above. Kept soft: full
                // strength looked dirty on busy picture backgrounds.
                val mainColor = layoutPaint.color
                val d = 1.5f * scale
                val hi = if (effect == 1) -d to -d else d to d
                val sh = if (effect == 1) d to d else -d to -d
                layoutPaint.color = Color.argb(150, 255, 255, 255)
                canvas.translate(hi.first, hi.second)
                offscreenText.draw(canvas)
                canvas.translate(-hi.first, -hi.second)
                layoutPaint.color = Color.argb(130, 0, 0, 0)
                canvas.translate(sh.first, sh.second)
                offscreenText.draw(canvas)
                canvas.translate(-sh.first, -sh.second)
                layoutPaint.color = mainColor
                offscreenText.draw(canvas)
            }
            3 -> {
                // Outer glow: one pass with a centred shadow in the text's
                // own colour, then the shadow is cleared.
                layoutPaint.setShadowLayer(24f * scale, 0f, 0f, layoutPaint.color)
                offscreenText.draw(canvas)
                layoutPaint.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
            }
            4 -> {
                // Stroke: stroked pass in a contrasting colour, then the
                // filled pass on top — fill + outline, Photoshop-style.
                val mainColor = layoutPaint.color
                val lum = (0.299f * Color.red(mainColor) +
                    0.587f * Color.green(mainColor) +
                    0.114f * Color.blue(mainColor)) / 255f
                layoutPaint.style = Paint.Style.STROKE
                layoutPaint.strokeWidth = 3f * scale
                layoutPaint.strokeJoin = Paint.Join.ROUND
                layoutPaint.color = if (lum < 0.5f) Color.WHITE else Color.BLACK
                offscreenText.draw(canvas)
                layoutPaint.style = Paint.Style.FILL
                layoutPaint.color = mainColor
                offscreenText.draw(canvas)
            }
            5 -> {
                // Gradient overlay: app theme gradient, top to bottom
                // across the text block.
                layoutPaint.shader = LinearGradient(
                    0f, 0f, 0f, textHeight.toFloat(),
                    intArrayOf(
                        ContextCompat.getColor(context, R.color.gradient_cyan),
                        ContextCompat.getColor(context, R.color.gradient_purple),
                        ContextCompat.getColor(context, R.color.gradient_indigo)
                    ),
                    null, Shader.TileMode.CLAMP
                )
                offscreenText.draw(canvas)
                layoutPaint.shader = null
            }
            6 -> {
                // Drop shadow: soft offset shadow, then the shadow is
                // cleared so later draws stay clean.
                layoutPaint.setShadowLayer(
                    8f * scale, 4f * scale, 6f * scale,
                    Color.argb(160, 0, 0, 0)
                )
                offscreenText.draw(canvas)
                layoutPaint.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
            }
            7 -> {
                // Neon: strong halo in the text's own colour, then a crisp
                // main pass on top so the core stays bright.
                layoutPaint.setShadowLayer(36f * scale, 0f, 0f, layoutPaint.color)
                offscreenText.draw(canvas)
                layoutPaint.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
                offscreenText.draw(canvas)
            }
            8 -> {
                // 3D extrude: stacked offset passes, then the filled main
                // pass on top. The extrude is a mid-tone shade of the text
                // colour (light text darkened, dark text lightened) — a
                // solid opposite-colour copy at full depth read as plain
                // doubling, not depth. Depth kept small for the same reason.
                val mainColor = layoutPaint.color
                val lum = (0.299f * Color.red(mainColor) +
                    0.587f * Color.green(mainColor) +
                    0.114f * Color.blue(mainColor)) / 255f
                layoutPaint.color = if (lum >= 0.5f)
                    ColorUtils.blendARGB(mainColor, Color.BLACK, 0.55f)
                else
                    ColorUtils.blendARGB(mainColor, Color.WHITE, 0.55f)
                val step = 1f * scale
                for (i in 6 downTo 1) {
                    canvas.translate(i * step, i * step)
                    offscreenText.draw(canvas)
                    canvas.translate(-i * step, -i * step)
                }
                layoutPaint.color = mainColor
                offscreenText.draw(canvas)
            }
            9 -> {
                // Long shadow: 40 stacked flat offset passes in translucent
                // black, then the filled main pass on top.
                val mainColor = layoutPaint.color
                layoutPaint.color = Color.argb(100, 0, 0, 0)
                val step = 1f * scale
                for (i in 40 downTo 1) {
                    canvas.translate(i * step, i * step)
                    offscreenText.draw(canvas)
                    canvas.translate(-i * step, -i * step)
                }
                layoutPaint.color = mainColor
                offscreenText.draw(canvas)
            }
            10 -> {
                // Theme gradient, left to right.
                layoutPaint.shader = LinearGradient(
                    0f, 0f, textBoxWidth.toFloat(), 0f,
                    intArrayOf(
                        ContextCompat.getColor(context, R.color.gradient_cyan),
                        ContextCompat.getColor(context, R.color.gradient_purple),
                        ContextCompat.getColor(context, R.color.gradient_indigo)
                    ),
                    null, Shader.TileMode.CLAMP
                )
                offscreenText.draw(canvas)
                layoutPaint.shader = null
            }
            11 -> {
                // Theme gradient, top-left to bottom-right.
                layoutPaint.shader = LinearGradient(
                    0f, 0f, textBoxWidth.toFloat(), textHeight.toFloat(),
                    intArrayOf(
                        ContextCompat.getColor(context, R.color.gradient_cyan),
                        ContextCompat.getColor(context, R.color.gradient_purple),
                        ContextCompat.getColor(context, R.color.gradient_indigo)
                    ),
                    null, Shader.TileMode.CLAMP
                )
                offscreenText.draw(canvas)
                layoutPaint.shader = null
            }
            12 -> {
                // Metallic gold gradient, top to bottom.
                layoutPaint.shader = LinearGradient(
                    0f, 0f, 0f, textHeight.toFloat(),
                    intArrayOf(
                        Color.parseColor("#FFFBEB"),
                        Color.parseColor("#FDE68A"),
                        Color.parseColor("#D97706"),
                        Color.parseColor("#92400E"),
                        Color.parseColor("#FDE68A")
                    ),
                    null, Shader.TileMode.CLAMP
                )
                offscreenText.draw(canvas)
                layoutPaint.shader = null
            }
            13 -> {
                // Rainbow gradient, left to right.
                layoutPaint.shader = LinearGradient(
                    0f, 0f, textBoxWidth.toFloat(), 0f,
                    intArrayOf(
                        Color.parseColor("#EF4444"),
                        Color.parseColor("#F97316"),
                        Color.parseColor("#EAB308"),
                        Color.parseColor("#22C55E"),
                        Color.parseColor("#3B82F6"),
                        Color.parseColor("#A855F7")
                    ),
                    null, Shader.TileMode.CLAMP
                )
                offscreenText.draw(canvas)
                layoutPaint.shader = null
            }
            14 -> {
                // Inner shadow: a dark edge clipped to the inside of the
                // glyphs. A stroked pass is masked by the filled glyphs
                // (DST_IN), so only the inner half of the stroke survives,
                // then it is laid over the normal filled pass.
                val mainColor = layoutPaint.color
                val lum = (0.299f * Color.red(mainColor) +
                    0.587f * Color.green(mainColor) +
                    0.114f * Color.blue(mainColor)) / 255f
                val edge = if (lum >= 0.5f)
                    ColorUtils.blendARGB(mainColor, Color.BLACK, 0.5f)
                else
                    Color.argb(170, 0, 0, 0)
                val block = Bitmap.createBitmap(textBoxWidth, textHeight, Bitmap.Config.ARGB_8888)
                val blockCanvas = Canvas(block)
                layoutPaint.style = Paint.Style.STROKE
                layoutPaint.strokeWidth = 6f * scale
                layoutPaint.strokeJoin = Paint.Join.ROUND
                layoutPaint.color = edge
                offscreenText.draw(blockCanvas)
                layoutPaint.style = Paint.Style.FILL
                layoutPaint.color = Color.BLACK
                layoutPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                offscreenText.draw(blockCanvas)
                layoutPaint.xfermode = null
                layoutPaint.color = mainColor
                offscreenText.draw(canvas)
                canvas.drawBitmap(block, 0f, 0f, null)
                block.recycle()
            }
            15 -> {
                // Inner glow: the same inside-clip trick as inner shadow,
                // but with a light edge so the glyph rims catch light.
                val mainColor = layoutPaint.color
                val lum = (0.299f * Color.red(mainColor) +
                    0.587f * Color.green(mainColor) +
                    0.114f * Color.blue(mainColor)) / 255f
                val edge = if (lum < 0.5f)
                    ColorUtils.blendARGB(mainColor, Color.WHITE, 0.6f)
                else
                    Color.argb(200, 255, 255, 255)
                val block = Bitmap.createBitmap(textBoxWidth, textHeight, Bitmap.Config.ARGB_8888)
                val blockCanvas = Canvas(block)
                layoutPaint.style = Paint.Style.STROKE
                layoutPaint.strokeWidth = 5f * scale
                layoutPaint.strokeJoin = Paint.Join.ROUND
                layoutPaint.color = edge
                offscreenText.draw(blockCanvas)
                layoutPaint.style = Paint.Style.FILL
                layoutPaint.color = Color.BLACK
                layoutPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                offscreenText.draw(blockCanvas)
                layoutPaint.xfermode = null
                layoutPaint.color = mainColor
                offscreenText.draw(canvas)
                canvas.drawBitmap(block, 0f, 0f, null)
                block.recycle()
            }
            16 -> {
                // Pattern fill: glyphs filled with the repeating diagonal
                // stripe tile generated in code (no asset files).
                layoutPaint.shader = BitmapShader(
                    patternTile(layoutPaint.color, scale),
                    Shader.TileMode.REPEAT, Shader.TileMode.REPEAT
                )
                offscreenText.draw(canvas)
                layoutPaint.shader = null
            }
            17 -> {
                // Reflection: the filled pass, then a faded mirrored copy
                // below it. The fade is baked into the mirrored bitmap
                // itself (DST_IN gradient), so the background behind the
                // reflection is never touched.
                offscreenText.draw(canvas)
                val block = Bitmap.createBitmap(textBoxWidth, textHeight, Bitmap.Config.ARGB_8888)
                Canvas(block).also { offscreenText.draw(it) }
                val fadePaint = Paint().apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                    shader = LinearGradient(
                        0f, 0f, 0f, textHeight.toFloat(),
                        intArrayOf(Color.argb(0, 0, 0, 0), Color.argb(140, 0, 0, 0)),
                        null, Shader.TileMode.CLAMP
                    )
                }
                Canvas(block).drawRect(
                    0f, 0f, textBoxWidth.toFloat(), textHeight.toFloat(), fadePaint
                )
                val gap = 12f * scale
                canvas.save()
                canvas.translate(0f, textHeight + gap)
                canvas.scale(1f, -1f)
                canvas.drawBitmap(block, 0f, 0f, null)
                canvas.restore()
                block.recycle()
            }
            else -> offscreenText.draw(canvas)
        }
        canvas.restore()
        return bitmap
    }

    // Thin wrappers over the shared render: same Nomiii folder + gallery
    // scan, only the size, quality and filename prefix differ.
    fun saveBitMapHighRes(
        context: Context,
        posterView: View,
        textView: EditText,
        effect: Int = 0
    ): File? {
        val pictureFileDir = getPictureDir(context) ?: return null
        val bitmap = renderPosterBitmap(context, posterView, textView, effect, 1440, 2560)
        return saveRenderedBitmap(context, pictureFileDir, bitmap, "UT", 95, "Saved (HD): ")
    }

    // WhatsApp-size export: the same poster at max width 1080, JPEG 85 —
    // a much smaller file that shares fast over a WhatsApp-only bundle.
    fun saveBitMapWhatsApp(
        context: Context,
        posterView: View,
        textView: EditText,
        effect: Int = 0
    ): File? {
        val pictureFileDir = getPictureDir(context) ?: return null
        val bitmap = renderPosterBitmap(context, posterView, textView, effect, 1080, 1920)
        return saveRenderedBitmap(context, pictureFileDir, bitmap, "UTWA", 85, "Saved (WhatsApp size): ")
    }

    private fun saveRenderedBitmap(
        context: Context,
        pictureFileDir: File,
        bitmap: Bitmap,
        prefix: String,
        quality: Int,
        dialogPrefix: String
    ): File? {
        val formatter = SimpleDateFormat("yyyyMMdd_hhmmss", Locale.getDefault())
        val filename = "${pictureFileDir.path}${File.separator}$prefix${formatter.format(Date())}.jpg"
        val pictureFile = File(filename)
        try {
            val oStream = FileOutputStream(pictureFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, oStream)
            oStream.flush()
            oStream.close()
            scanFile(context, pictureFile)
            context.dialog("$dialogPrefix$filename").show()
        } catch (e: IOException) {
            e.printStackTrace()
            context.dialog(e.localizedMessage ?: "There was an error saving the image").show()
        }
        return pictureFile
    }

    fun getListOfImages(context: Context): List<Image> {
        // New saves go to Pictures/Nomiii; keep showing legacy DCIM/Nomiii
        // posters too so nothing already saved disappears from Local.
        val newDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "Nomiii"
        )
        val legacyDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            "Nomiii"
        )
        // List concat (not Array +): Array.orEmpty() yields an out-projected
        // Array here, and Array.plus does not accept it (run #44: "None of
        // the following functions can be called" at the +). Lists concat
        // cleanly, and newDir stays first so distinctBy keeps new saves.
        val files = (newDir.listFiles().orEmpty().toList() + legacyDir.listFiles().orEmpty().toList())
            .distinctBy { it.name }
        return files.map { file ->
            Image(
                name = file.name,
                url = FileProvider.getUriForFile(
                    context,
                    context.applicationContext.packageName + ".provider",
                    file
                ).toString()
            )
        }
    }

    fun deleteFile(context: Context, contentUri: Uri, onSuccess: (() -> Unit)): Boolean {
        return try {
            val deleteResult = context.contentResolver?.delete(contentUri, null, null)
            if (deleteResult == -1) {
                context.dialog("Error deleting file").show()
                return false
            } else {
                onSuccess.invoke()
                return true
            }
        } catch (e: Exception) {
            context.dialog(e.localizedMessage ?: "Something went wrong").show()
            false
        }
    }


    @Throws(IOException::class)
    fun getImageFromImageView(context: Context, iv: ImageView, filename: String): Uri? {
        //to get the image from the ImageView (say iv)
        val draw = iv.drawable
        val bitmap = Bitmap.createBitmap(
            draw.intrinsicWidth,
            draw.intrinsicHeight, Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        draw.setBounds(0, 0, canvas.width, canvas.height)
        draw.draw(canvas)
        var outStream: FileOutputStream? = null
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "Nomiii"
        )
        if (!dir.exists()) {
            val isDirectoryCreated = dir.mkdirs()
            if (!isDirectoryCreated) context.dialog("Failed to create the directory!").show()
            return null
        }
        val outFile = File(dir.path + File.separator + filename + ".jpg")
        outStream = FileOutputStream(outFile)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outStream)
        outStream.flush()
        outStream.close()
        return FileProvider.getUriForFile(
            context,
            context.applicationContext.packageName + ".provider",
            outFile
        )
    }
}