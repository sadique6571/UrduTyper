package me.nomi.urdutyper.presentation.utils.extensions.common

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.IdRes
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment


fun Activity.toast(text: CharSequence, duration: Int = Toast.LENGTH_SHORT): Unit =
    Toast.makeText(this, text, duration).show()

inline fun <reified T : Any> Activity.start(extras: Intent.() -> Unit) {
    val intent = intentFor<T>(this)
    intent.apply(extras)
    startActivity(intent)
}

inline fun <reified T : Any> Activity.start() {
    val intent = intentFor<T>(this)
    startActivity(intent)
}

inline fun <reified T : Any> Activity.startActivityForResult(
    requestCode: Int,
    options: Bundle? = null,
    action: String? = null
) {
    startActivityForResult(intentFor<T>(this).setAction(action), requestCode, options)
}

fun Activity.getNavController(@IdRes navHostFragmentId: Int): NavController {
    val navHostFragment =
        (this as FragmentActivity).supportFragmentManager.findFragmentById(navHostFragmentId) as NavHostFragment
    return navHostFragment.navController
}
