package com.tidalmusic.player.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.app.LocaleManager
import android.os.LocaleList
import java.util.Locale

/**
 * In-app language selection (English / Persian / system).
 *
 * Android 13+ routes through the platform per-app language API, which persists the
 * choice at the framework level. Older versions get an application-level override:
 * the preference is applied to the activity's base context on every launch.
 */
object AppLanguage {

    enum class Option(val key: String) {
        SYSTEM("system"),
        ENGLISH("en"),
        PERSIAN("fa");

        fun toLocale(): Locale? = when (this) {
            SYSTEM -> null
            ENGLISH -> Locale.forLanguageTag("en")
            PERSIAN -> Locale.forLanguageTag("fa")
        }

        companion object {
            fun fromKey(key: String?): Option = entries.firstOrNull { it.key == key } ?: SYSTEM
        }
    }

    private const val PREFS = "app_language"
    private const val KEY = "language"

    fun readPref(context: Context): Option {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Option.fromKey(prefs.getString(KEY, Option.SYSTEM.key))
    }

    fun savePref(context: Context, option: Option) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, option.key)
            .apply()
    }

    /** Applies the persisted override to a base context (Android 12 and below). */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val option = readPref(base)
        val locale = option.toLocale() ?: return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    /** Applies the choice live: platform API on Android 13+, recreate on older versions. */
    fun apply(activity: Activity, option: Option) {
        savePref(activity, option)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locale = option.toLocale()
            val manager = activity.getSystemService(LocaleManager::class.java)
            if (manager != null) {
                manager.applicationLocales = if (locale == null) {
                    LocaleList.getEmptyLocaleList()
                } else {
                    LocaleList(locale)
                }
                return
            }
        }
        activity.recreate()
    }
}
