package com.robo.medicalmonitors.sdkdemo.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object DemoLocaleHelper {
    private const val PREFERENCES = "demo_settings"
    private const val LANGUAGE_KEY = "language"

    fun wrap(context: Context): Context {
        val locale = localeFor(language(context))
        Locale.setDefault(locale)
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(locale)
        }
        return DisplayScaleHelper.wrap(context.createConfigurationContext(configuration))
    }

    fun language(context: Context): String {
        val stored = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(LANGUAGE_KEY, null)
        return stored?.takeIf { it == "zh" || it == "en" }
            ?: if (context.resources.configuration.locales[0].language == "zh") "zh" else "en"
    }

    fun setLanguage(context: Context, language: String) {
        require(language == "zh" || language == "en")
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(LANGUAGE_KEY, language)
            .apply()
        Locale.setDefault(localeFor(language))
    }

    private fun localeFor(language: String): Locale =
        if (language == "zh") Locale.SIMPLIFIED_CHINESE else Locale.ENGLISH
}
