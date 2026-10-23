package me.nomi.urdutyper.presentation.utils.extensions.common

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment


fun Fragment.getColor(@ColorRes colorId: Int): Int {
    return ContextCompat.getColor(requireActivity(), colorId)
}


fun Fragment.getDrawable(@DrawableRes drawableId: Int) =
    ContextCompat.getDrawable(requireContext(), drawableId)