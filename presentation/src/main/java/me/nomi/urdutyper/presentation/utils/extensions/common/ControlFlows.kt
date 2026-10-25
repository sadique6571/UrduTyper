package me.nomi.urdutyper.presentation.utils.extensions.common

fun Int.loop(func: (Int) -> Unit) {
    for (i in 0 until this) {
        func(i)
    }
}

fun <T> unsafeLazy(initializer: () -> T) = lazy(LazyThreadSafetyMode.NONE, initializer)
