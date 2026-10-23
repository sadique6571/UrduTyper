package me.nomi.urdutyper.presentation.utils.common

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import me.nomi.urdutyper.R
import java.io.IOException

object Notification {
    fun create(context: Context, message: String?, uri: Uri?) {
        var image: Bitmap? = null
        try {
            image = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        val mNotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "UrduTyper",
                "UrduTyper",
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = "Your file has been saved!"
            channel.enableVibration(true)
            channel.vibrationPattern = longArrayOf(100, 1000, 200, 340)
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            mNotificationManager.createNotificationChannel(channel)
        }
        val b: Bitmap? = null
        val mBuilder: NotificationCompat.Builder =
            NotificationCompat.Builder(context.applicationContext, "UrduTyper")
            .setSmallIcon(R.mipmap.ic_launcher_icons_custom) // notification icon
            .setLargeIcon(image)
            .setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(image)
                    .bigLargeIcon(b)
            )
            .setContentTitle("Your file has been saved!") // title for notification
            .setContentText(message) // message for notification
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(longArrayOf(100, 1000, 200, 340))
            .setTicker("UrduTyper - " + "Your file has been saved!")
            .setAutoCancel(true) // clear notification after click
        //
        val intent = Intent(Intent.ACTION_VIEW)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        intent.setDataAndType(uri, "image/*")
        val pi = PendingIntent.getActivity(
            context.applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        mBuilder.setContentIntent(pi)
        mNotificationManager.notify(System.currentTimeMillis().toInt() % 10000, mBuilder.build())
    }
}
