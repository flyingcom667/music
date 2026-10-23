package com.example.myapplication.util

import android.content.Context
import java.util.Locale

/**
 * The language of the app.
 *
 * The app always follows the language of the device — there is no separate in-app language, so
 * the system translations are used (English, Persian and Arabic). Switching the language of the
 * device is therefore enough to change the language of the app.
 */
object AppLocale {

    /** The language tag of the device, for example "fa" or "en-US". */
    fun currentTag(context: Context): String =
        context.resources.configuration.locales[0].toLanguageTag()

    /** The name of the current language, written in that language, for example "فارسی". */
    fun currentName(context: Context): String {
        val locale: Locale = context.resources.configuration.locales[0]
        return locale.getDisplayName(locale)
    }
}
