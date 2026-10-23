package me.nomi.urdutyper.presentation.utils.extensions

import kotlin.math.floor

fun Int.toK(): String {
    return if (this < 1000) this.toString() else "%.1fk".format(floor(this / 100.0) / 10)
}

