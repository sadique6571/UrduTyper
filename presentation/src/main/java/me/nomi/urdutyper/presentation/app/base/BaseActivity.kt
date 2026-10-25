package me.nomi.urdutyper.presentation.app.base

import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.ComponentActivity
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelLazy
import androidx.lifecycle.ViewModelProvider
import androidx.viewbinding.ViewBinding
import me.nomi.urdutyper.domain.repository.SharedPreferenceRepository
import javax.inject.Inject
import kotlin.properties.ReadOnlyProperty

abstract class BaseActivity<VB : ViewBinding> : AppCompatActivity() {

    @Inject
    lateinit var prefs: SharedPreferenceRepository

    protected val binding: VB by lazy { inflateViewBinding(layoutInflater) }

    protected abstract fun inflateViewBinding(inflater: LayoutInflater): VB

//    fun isDarkModeEnabled() = prefs.getBoolean(DARK_MODE, false)

//    fun enableDarkMode(enable: Boolean) = appSettings.edit().putBoolean(DARK_MODE, enable).commit()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        setTheme(if (isDarkModeEnabled()) R.style.DarkTheme else R.style.LightTheme)
        setContentView(binding.root)
    }

    protected fun <T> LiveData<T>.observe(observer: Observer<in T>) =
        observe(this@BaseActivity, observer)

    companion object {
        const val DARK_MODE = "dark_mode"
    }
}