package me.nomi.urdutyper.data.source

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import me.nomi.urdutyper.domain.entity.User
import me.nomi.urdutyper.domain.repository.SharedPreferenceRepository
import me.nomi.urdutyper.domain.utils.Constants

class SharedPreference(context: Context) : SharedPreferenceRepository {
    private val prefs: SharedPreferences = context.getSharedPreferences(Constants.PREFS_FILENAME, 0)
    override var user: User?
        get() {
            val jsonStr = prefs.getString(Constants.USER, "") ?: ""
            return Gson().fromJson(jsonStr, User::class.java)
        }
        set(value) {
            val jsonStr = Gson().toJson(value) ?: ""
            prefs.edit().putString(Constants.USER, jsonStr).apply()
        }
    override var email: String
        get() = prefs.getString(Constants.EMAIL, "") ?: ""
        set(value) {
            prefs.edit().putString(Constants.EMAIL, value).apply()
        }
    override var password: String
        get() = prefs.getString(Constants.PASSWORD, "") ?: ""
        set(value) {
            prefs.edit().putString(Constants.PASSWORD, value).apply()
        }
    override var tokenId: String
        get() = prefs.getString(Constants.TOKEN_ID, "") ?: ""
        set(value) {
            prefs.edit().putString(Constants.TOKEN_ID, value).apply()
        }


    override fun clear() {
        prefs.edit().clear().apply()
    }
}