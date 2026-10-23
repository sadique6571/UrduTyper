package me.nomi.urdutyper.presentation.utils.extensions.common

import android.os.Handler
import android.os.Looper
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

private val mainThreadHandler by lazy { Handler(Looper.getMainLooper()) }

fun runAsync(action: () -> Unit) {
    Thread(Runnable(action)).start()
}

fun runOnUiThread(action: () -> Unit) {
    mainThreadHandler.post(action)
}

fun runDelayed(delayMillis: Long, action: () -> Unit) {
    mainThreadHandler.postDelayed(action, delayMillis)
}

fun scheduleTaskWithFixedDelay(
    seconds: Int,
    action: () -> Unit
) {
    val executor = ScheduledThreadPoolExecutor(1)
    val task = Runnable { action() }
    executor.scheduleAtFixedRate(task, 0, seconds.toLong(), TimeUnit.SECONDS)
}
