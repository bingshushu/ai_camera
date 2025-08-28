package com.ai.bb.camera

import android.app.Application
import android.content.Context
import android.content.res.Configuration

class AICameraApplication : Application() {
    
    companion object {
        private var settingsManager: SettingsManager? = null
        
        fun getSettingsManager(context: Context): SettingsManager {
            if (settingsManager == null) {
                settingsManager = SettingsManager(context.applicationContext)
            }
            return settingsManager!!
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        // Initialize settings manager
        settingsManager = SettingsManager(this)
    }
    
    override fun attachBaseContext(base: Context) {
        val settingsManager = SettingsManager(base)
        val currentLanguage = settingsManager.getCurrentLanguage()
        val updatedContext = LanguageManager.updateLanguage(base, currentLanguage)
        super.attachBaseContext(updatedContext)
    }
    
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val settingsManager = getSettingsManager(this)
        val currentLanguage = settingsManager.getCurrentLanguage()
        LanguageManager.updateLanguage(this, currentLanguage)
    }
}