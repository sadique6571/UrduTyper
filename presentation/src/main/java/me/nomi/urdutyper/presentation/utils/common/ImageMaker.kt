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

    fun renderPosterBitmap(
        context: Context,
        posterView: View,
        textView: EditText,
        effects: Set<Int>,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap {
        val scale = if (posterView.width > 0) targetWidth.toFloat() / posterView.width else 1f
        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bgDrawable = posterView.background
        if (bgDrawable is BitmapDrawable && bgDrawable.bitmap != null && bgDrawable.gravity != Gravity.FILL) {
            val bmp = bgDrawable.bitmap!!
            val s = maxOf(targetWidth.toFloat() / bmp.width, targetHeight.toFloat() / bmp.height)
            val sw = bmp.width * s
            val sh = bmp.height * s
            val left = (targetWidth - sw) / 2f
            val top = (targetHeight - sh) / 2f
            canvas.drawBitmap(bmp, null, RectF(left, top, left + sw, top + sh), null)
        } else if (bgDrawable != null) {
            bgDrawable.setBounds(0, 0, targetWidth, targetHeight)
            bgDrawable.draw(canvas)
            bgDrawable.setBounds(0, 0, posterView.width, posterView.height)
        } else {
            canvas.drawColor(Color.WHITE)
        }

        val rawText = textView.text.toString()
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

        val textBoxWidth = if (textView.width > 0) (textView.width * scale).toInt() else targetWidth
        offscreenText.measure(
            View.MeasureSpec.makeMeasureSpec(textBoxWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val textHeight = offscreenText.measuredHeight
        val liveCenterX = (textView.left + textView.width / 2f + textView.translationX) * scale
        val liveCenterY = (textView.top + textView.height / 2f + textView.translationY) * scale
        canvas.save()
        canvas.translate(liveCenterX - textBoxWidth / 2f, liveCenterY - textHeight / 2f)
        if (textView.rotation != 0f) canvas.rotate(textView.rotation, textBoxWidth / 2f, textHeight / 2f)
        if (textView.scaleX != 1f) canvas.scale(textView.scaleX, textView.scaleX, textBoxWidth / 2f, textHeight / 2f)
        offscreenText.layout(0, 0, textBoxWidth, textHeight)
        val layoutPaint = offscreenText.layout.paint
        val mainColor = layoutPaint.color
        val lum = (0.299f * Color.red(mainColor) +
            0.587f * Color.green(mainColor) +
            0.114f * Color.blue(mainColor)) / 255f

        if (9 in effects) {
            layoutPaint.color = Color.argb(100, 0, 0, 0)
            val step = 1f * scale
            for (i in 40 downTo 1) {
                canvas.translate(i * step, i * step)
                offscreenText.draw(canvas)
                canvas.translate(-i * step, -i * step)
            }
            layoutPaint.color = mainColor
        }
        if (6 in effects) {
            layoutPaint.setShadowLayer(8f * scale, 4f * scale, 6f * scale, Color.argb(160, 0, 0, 0))
            offscreenText.draw(canvas)
            layoutPaint.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        }
        if (8 in effects) {
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
        }
        if (3 in effects) {
            layoutPaint.setShadowLayer(24f * scale, 0f, 0f, layoutPaint.color)
            offscreenText.draw(canvas)
            layoutPaint.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        }
        if (7 in effects) {
            layoutPaint.setShadowLayer(36f * scale, 0f, 0f, layoutPaint.color)
            offscreenText.draw(canvas)
            layoutPaint.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        }

        val bevel = if (2 in effects) 2 else if (1 in effects) 1 else 0
        if (bevel != 0) {
            val d = 1.5f * scale
            val hi = if (bevel == 1) -d to -d else d to d
            val sh = if (bevel == 1) d to d else -d to -d
            layoutPaint.color = Color.argb(150, 255, 255, 255)
            canvas.translate(hi.first, hi.second)
            offscreenText.draw(canvas)
            canvas.translate(-hi.first, -hi.second)
            layoutPaint.color = Color.argb(130, 0, 0, 0)
            canvas.translate(sh.first, sh.second)
            offscreenText.draw(canvas)
            canvas.translate(-sh.first, -sh.second)
            layoutPaint.color = mainColor
        }
        if (4 in effects) {
            layoutPaint.style = Paint.Style.STROKE
            layoutPaint.strokeWidth = 3f * scale
            layoutPaint.strokeJoin = Paint.Join.ROUND
            layoutPaint.color = if (lum < 0.5f) Color.WHITE else Color.BLACK
            offscreenText.draw(canvas)
            layoutPaint.style = Paint.Style.FILL
            layoutPaint.color = mainColor
        }

        val fill = listOf(5, 10, 11, 12, 13, 16).lastOrNull { it in effects }
        fun setFillShader() {
            when (fill) {
                5 -> layoutPaint.shader = LinearGradient(0f, 0f, 0f, textHeight.toFloat(),
                    intArrayOf(
                        ContextCompat.getColor(context, R.color.gradient_cyan),
                        ContextCompat.getColor(context, R.color.gradient_purple),
                        ContextCompat.getColor(context, R.color.gradient_indigo)
                    ), null, Shader.TileMode.CLAMP)
                10 -> layoutPaint.shader = LinearGradient(0f, 0f, textBoxWidth.toFloat(), 0f,
                    intArrayOf(
                        ContextCompat.getColor(context, R.color.gradient_cyan),
                        ContextCompat.getColor(context, R.color.gradient_purple),
                        ContextCompat.getColor(context, R.color.gradient_indigo)
                    ), null, Shader.TileMode.CLAMP)
                11 -> layoutPaint.shader = LinearGradient(0f, 0f, textBoxWidth.toFloat(), textHeight.toFloat(),
                    intArrayOf(
                        ContextCompat.getColor(context, R.color.gradient_cyan),
                        ContextCompat.getColor(context, R.color.gradient_purple),
                        ContextCompat.getColor(context, R.color.gradient_indigo)
                    ), null, Shader.TileMode.CLAMP)
                12 -> layoutPaint.shader = LinearGradient(0f, 0f, 0f, textHeight.toFloat(),
                    intArrayOf(
                        Color.parseColor("#FFFBEB"),
                        Color.parseColor("#FDE68A"),
                        Color.parseColor("#D97706"),
                        Color.parseColor("#92400E"),
                        Color.parseColor("#FDE68A")
                    ), null, Shader.TileMode.CLAMP)
                13 -> layoutPaint.shader = LinearGradient(0f, 0f, textBoxWidth.toFloat(), 0f,
                    intArrayOf(
                        Color.parseColor("#EF4444"),
                        Color.parseColor("#F97316"),
                        Color.parseColor("#EAB308"),
                        Color.parseColor("#22C55E"),
                        Color.parseColor("#3B82F6"),
                        Color.parseColor("#A855F7")
                    ), null, Shader.TileMode.CLAMP)
                16 -> layoutPaint.shader = BitmapShader(patternTile(layoutPaint.color, scale), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            }
        }
        setFillShader()
        offscreenText.draw(canvas)
        layoutPaint.shader = null
        layoutPaint.color = mainColor

        if (14 in effects) {
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
            canvas.drawBitmap(block, 0f, 0f, null)
            block.recycle()
        }
        if (15 in effects) {
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
            canvas.drawBitmap(block, 0f, 0f, null)
            block.recycle()
        }
        if (17 in effects) {
            val block = Bitmap.createBitmap(textBoxWidth, textHeight, Bitmap.Config.ARGB_8888)
            setFillShader()
            Canvas(block).also { offscreenText.draw(it) }
            layoutPaint.shader = null
            val fadePaint = Paint().apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                shader = LinearGradient(
                    0f, 0f, 0f, textHeight.toFloat(),
                    intArrayOf(Color.argb(0, 0, 0, 0), Color.argb(140, 0, 0, 0)),
                    null, Shader.TileMode.CLAMP
                )
            }
            Canvas(block).drawRect(0f, 0f, textBoxWidth.toFloat(), textHeight.toFloat(), fadePaint)
            val gap = 12f * scale
            canvas.save()
            canvas.translate(0f, textHeight + gap)
            canvas.scale(1f, -1f)
            canvas.drawBitmap(block, 0f, 0f, null)
            canvas.restore()
            block.recycle()
        }

        canvas.restore()
        return bitmap
    }

    fun savePosterBitmap(
        context: Context,
        posterView: View,
        textView: EditText,
        effects: Set<Int>,
        targetWidth: Int,
        targetHeight: Int,
        prefix: String,
        quality: Int,
        dialogPrefix: String
    ): File? {
        val pictureFileDir = getPictureDir(context) ?: return null
        val bitmap = renderPosterBitmap(context, posterView, textView, effects, targetWidth, targetHeight)
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

    fun saveBitMapHighRes(
        context: Context,
        posterView: View,
        textView: EditText,
        effects: Set<Int> = emptySet()
    ): File? {
        return savePosterBitmap(context, posterView, textView, effects, 1440, 2560, "UT", 95, "Saved (HD): ")
    }

    fun saveBitMapWhatsApp(
        context: Context,
        posterView: View,
        textView: EditText,
        effects: Set<Int> = emptySet()
    ): File? {
        return savePosterBitmap(context, posterView, textView, effects, 1080, 1920, "UTWA", 85, "Saved (WhatsApp size): ")
    }

    fun getListOfImages(context: Context): List<Image> {
        val newDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Nomiii")
        val legacyDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Nomiii")
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
                false
            } else {
                onSuccess.invoke()
                true
            }
        } catch (e: Exception) {
            context.dialog(e.localizedMessage ?: "Something went wrong").show()
            false
        }
    }

    @Throws(IOException::class)
    fun getImageFromImageView(context: Context, iv: ImageView, filename: String): Uri? {
        val draw = iv.drawable
        val bitmap = Bitmap.createBitmap(draw.intrinsicWidth, draw.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        draw.setBounds(0, 0, canvas.width, canvas.height)
        draw.draw(canvas)
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Nomiii")
        if (!dir.exists()) {
            val isDirectoryCreated = dir.mkdirs()
            if (!isDirectoryCreated) context.dialog("Failed to create the directory!").show()
            return null
        }
        val outFile = File(dir.path + File.separator + filename + ".jpg")
        val outStream = FileOutputStream(outFile)
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
