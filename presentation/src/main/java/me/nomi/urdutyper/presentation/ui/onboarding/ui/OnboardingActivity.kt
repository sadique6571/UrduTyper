package me.nomi.urdutyper.presentation.ui.onboarding.ui

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import dagger.hilt.android.AndroidEntryPoint
import me.nomi.urdutyper.databinding.ActivityOnboardingBinding
import me.nomi.urdutyper.presentation.app.base.BaseActivity
import me.nomi.urdutyper.presentation.utils.extensions.views.hideKeyboard

@AndroidEntryPoint
class OnboardingActivity : BaseActivity<ActivityOnboardingBinding>() {
    override fun inflateViewBinding(inflater: LayoutInflater) =
        ActivityOnboardingBinding.inflate(inflater)

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (currentFocus != null) {
            hideKeyboard(currentFocus!!)
        }
        return super.dispatchTouchEvent(ev)
    }
}