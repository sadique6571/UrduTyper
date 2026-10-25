package me.nomi.urdutyper.presentation.utils.extensions.views

import android.app.Activity
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.nomi.urdutyper.presentation.utils.extensions.common.inputMethodManager

fun <T : View> T.afterMeasured(f: T.() -> Unit) {
    viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
        override fun onPreDraw(): Boolean {
            viewTreeObserver.removeOnPreDrawListener(this)
            f()
            return false
        }
    })
}

fun View.removeFromParent() {
    if (parent is ViewGroup) {
        (parent as ViewGroup).removeView(this)
    }
}

fun View.disableTouch() {
    setOnTouchListener { _, _ -> true }
}

fun View.setVisible(isVisible: Boolean, animate: Boolean = false) {
    if (animate) {
        if (isVisible) {
            alpha = 0f
            setVisible(true)
        }
        ViewCompat.animate(this)
            .alpha(if (isVisible) 1f else 0f)
            .withEndAction {
                if (!isVisible) {
                    setVisible(false)
                }
            }
    } else {
        visibility = if (isVisible) VISIBLE else GONE
    }
}


fun View.show() {
    isVisible = true
}

fun View.hide() {
    isVisible = false
}

inline fun AppCompatActivity.launchAndRepeatWithViewLifecycle(
    lifecycleState: Lifecycle.State = Lifecycle.State.STARTED,
    crossinline block: suspend CoroutineScope.() -> Unit
) {
    lifecycleScope.launch {
        lifecycle.repeatOnLifecycle(lifecycleState) {
            block()
        }
    }
}

inline fun Fragment.launchAndRepeatWithViewLifecycle(
    lifecycleState: Lifecycle.State = Lifecycle.State.STARTED,
    crossinline block: suspend CoroutineScope.() -> Unit
) {
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.lifecycle.repeatOnLifecycle(lifecycleState) {
            block()
        }
    }
}

fun Activity.hideKeyboard(view: View) {
    if (inputMethodManager.isAcceptingText) {
        inputMethodManager.hideSoftInputFromWindow(
            view.windowToken,
            0
        )
    }
}

fun Fragment.hideKeyboard(view: View) = requireActivity().hideKeyboard(view)

fun Activity.showKeyboard(view: View) {
    inputMethodManager.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
}

fun Fragment.showKeyboard(view: View) = requireActivity().showKeyboard(view)