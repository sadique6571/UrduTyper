package me.nomi.urdutyper.presentation.utils.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
import androidx.core.content.FileProvider
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

    // Fixed 1440x2560 off-screen render — NOT an upscale of the screen capture.
    // The background drawable scales losslessly and the text is laid out fresh at
    // the target size, so Nastaliq stays crisp. The on-screen view is never
    // touched: no menu/cursor hiding and no modification of the live text.
    fun saveBitMapHighRes(context: Context, posterView: View, textView: EditText): File? {
        val pictureFileDir = getPictureDir(context) ?: return null
        val targetWidth = 1440
        val targetHeight = 2560
        val scale = if (posterView.width > 0) targetWidth.toFloat() / posterView.width else 1f

        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bgDrawable = posterView.background
        if (bgDrawable != null) {
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
        offscreenText.draw(canvas)
        canvas.restore()

        val formatter = SimpleDateFormat("yyyyMMdd_hhmmss", Locale.getDefault())
        val filename = "${pictureFileDir.path}${File.separator}UT${formatter.format(Date())}.jpg"
        val pictureFile = File(filename)
        try {
            val oStream = FileOutputStream(pictureFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, oStream)
            oStream.flush()
            oStream.close()
            scanFile(context, pictureFile)
            context.dialog("Saved (HD): $filename").show()
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