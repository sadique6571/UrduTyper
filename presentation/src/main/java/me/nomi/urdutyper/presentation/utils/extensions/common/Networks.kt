package me.nomi.urdutyper.presentation.utils.extensions.common

import android.content.Context
import android.net.NetworkCapabilities

fun Context.isNetworkConnected(): Boolean {
    return connectivityManager.run {
        getNetworkCapabilities(activeNetwork)
            ?.hasCapability((NetworkCapabilities.NET_CAPABILITY_INTERNET)) == true
    }
}