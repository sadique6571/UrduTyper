package me.nomi.urdutyper.presentation.utils.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
        val pictureFileDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            "Nomiii"
        )
        if (!pictureFileDir.exists() && !pictureFileDir.mkdirs()) {
            context.dialog("Failed to create the directory!").show()
            return null
        }
        return pictureFileDir
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
        offscreenText.measure(
            View.MeasureSpec.makeMeasureSpec(targetWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val textHeight = offscreenText.measuredHeight
        val top = (targetHeight - textHeight) / 2
        offscreenText.layout(0, top, targetWidth, top + textHeight)
        offscreenText.draw(canvas)

        val formatter = SimpleDateFormat("yyyyMMdd_hhmmss", Locale.getDefault())
        val filename = "${pictureFileDir.path}${File.separator}UT${formatter.format(Date())}.jpg"
        val pictureFile = File(filename)
        try {
            val oStream = FileOutputStream(pictureFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, oStream)
            oStream.flush()
            oStream.close()
            context.dialog("Saved (HD): $filename").show()
        } catch (e: IOException) {
            e.printStackTrace()
            context.dialog(e.localizedMessage ?: "There was an error saving the image").show()
        }
        return pictureFile
    }

    fun getListOfImages(context: Context): List<Image> {
        val pictureFileDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            "Nomiii"
        )
        return pictureFileDir.listFiles()?.map { file ->
            Image(
                name = file.name,
                url = FileProvider.getUriForFile(
                    context,
                    context.applicationContext.packageName + ".provider",
                    file
                ).toString()
            )
        } ?: emptyList()
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
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
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