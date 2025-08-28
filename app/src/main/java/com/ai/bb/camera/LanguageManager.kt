package com.ai.bb.camera

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

object LanguageManager {
    
    fun updateLanguage(context: Context, language: AppLanguage): Context {
        val locale = when (language) {
            AppLanguage.SYSTEM -> getCurrentSystemLocale()
            AppLanguage.CHINESE -> Locale("zh", "CN")
            AppLanguage.ENGLISH -> Locale("en", "US")
            AppLanguage.SPANISH -> Locale("es", "ES")
            AppLanguage.FRENCH -> Locale("fr", "FR")
            AppLanguage.ARABIC -> Locale("ar", "SA")
            AppLanguage.RUSSIAN -> Locale("ru", "RU")
            AppLanguage.GERMAN -> Locale("de", "DE")
            AppLanguage.PORTUGUESE -> Locale("pt", "PT")
            AppLanguage.ITALIAN -> Locale("it", "IT")
        }
        
        return updateContextLocale(context, locale)
    }
    
    private fun getCurrentSystemLocale(): Locale {
        return Resources.getSystem().configuration.locales[0]
    }
    
    private fun updateContextLocale(context: Context, locale: Locale): Context {
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        
        return context.createConfigurationContext(config)
    }
    
    fun getDisplayLanguage(language: AppLanguage): String {
        return when (language) {
            AppLanguage.SYSTEM -> "跟随系统"
            AppLanguage.CHINESE -> "简体中文"
            AppLanguage.ENGLISH -> "English"
            AppLanguage.SPANISH -> "Español"
            AppLanguage.FRENCH -> "Français"
            AppLanguage.ARABIC -> "العربية"
            AppLanguage.RUSSIAN -> "Русский"
            AppLanguage.GERMAN -> "Deutsch"
            AppLanguage.PORTUGUESE -> "Português"
            AppLanguage.ITALIAN -> "Italiano"
        }
    }
}