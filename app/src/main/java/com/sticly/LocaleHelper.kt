package com.sticly

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

object LocaleHelper {

    fun onAttach(context: Context): Context {
        val lang = getPersistedData(context)
        return setLocale(context, lang)
    }

    private fun getPersistedData(context: Context): String {
        val savedLang = PreferencesHelper.getLanguage(context)
        if (savedLang.isNotEmpty()) {
            android.util.Log.d("LocaleHelper", "Using saved lang: $savedLang")
            return savedLang
        }

        // Get system locale
        val locale = Locale.getDefault()
        val systemLang = locale.language
        android.util.Log.d("LocaleHelper", "System lang detected via Locale.getDefault: $systemLang")

        val supportedLangs = listOf("en", "tr", "zh", "es", "ar", "hi", "pt")
        val lang = if (supportedLangs.contains(systemLang)) systemLang else "en"
        
        android.util.Log.d("LocaleHelper", "Final resolved lang: $lang")
        
        // Save for next time
        PreferencesHelper.setLanguage(context, lang)
        return lang
    }

    fun setLocale(context: Context, language: String): Context {
        val locale = Locale(language)
        Locale.setDefault(locale)

        val configuration = Configuration(context.resources.configuration)
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.setLocale(locale)
            configuration.setLayoutDirection(locale)
            context.createConfigurationContext(configuration)
        } else {
            @Suppress("DEPRECATION")
            configuration.locale = locale
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                configuration.setLayoutDirection(locale)
            }
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
            context
        }
    }
}
