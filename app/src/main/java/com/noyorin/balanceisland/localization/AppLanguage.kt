package com.noyorin.balanceisland.localization

import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import java.util.Locale

enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    SIMPLIFIED_CHINESE("zh-Hans-CN"),
    TRADITIONAL_CHINESE("zh-Hant-TW"),
    ENGLISH("en"),
    JAPANESE("ja"),
    KOREAN("ko")
}

object AppLanguagePreferences {
    private const val PREFS_NAME = "language_settings"
    private const val KEY_LANGUAGE = "app_language"

    fun current(context: Context): AppLanguage = runCatching {
        val saved = AppLanguage.valueOf(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE, AppLanguage.SYSTEM.name)
                ?: AppLanguage.SYSTEM.name
        )
        if (saved != AppLanguage.SYSTEM || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            saved
        } else {
            val frameworkTag = context.getSystemService(LocaleManager::class.java)
                .applicationLocales.toLanguageTags()
            when {
                frameworkTag.startsWith("zh-Hant", ignoreCase = true) ||
                    frameworkTag.startsWith("zh-TW", ignoreCase = true) ->
                    AppLanguage.TRADITIONAL_CHINESE
                frameworkTag.startsWith("zh", ignoreCase = true) -> AppLanguage.SIMPLIFIED_CHINESE
                frameworkTag.startsWith("en", ignoreCase = true) -> AppLanguage.ENGLISH
                frameworkTag.startsWith("ja", ignoreCase = true) -> AppLanguage.JAPANESE
                frameworkTag.startsWith("ko", ignoreCase = true) -> AppLanguage.KOREAN
                else -> AppLanguage.SYSTEM
            }
        }
    }.getOrDefault(AppLanguage.SYSTEM)

    fun set(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LANGUAGE, language.name).apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                if (language == AppLanguage.SYSTEM) LocaleList.getEmptyLocaleList()
                else LocaleList.forLanguageTags(language.tag)
        }
        context.applicationContext.sendBroadcast(
            Intent(ACTION_LANGUAGE_CHANGED).setPackage(context.packageName)
        )
    }

    fun wrap(base: Context): Context {
        val language = current(base)
        if (language == AppLanguage.SYSTEM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return base
        }
        val locale = if (language == AppLanguage.SYSTEM) {
            Resources.getSystem().configuration.locales[0]
        } else {
            Locale.forLanguageTag(language.tag)
        }
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLocales(LocaleList(locale))
        return base.createConfigurationContext(configuration)
    }

    const val ACTION_LANGUAGE_CHANGED = "com.noyorin.balanceisland.LANGUAGE_CHANGED"
}
