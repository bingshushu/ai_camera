package com.ai.bb.camera

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.ai.bb.camera.ui.theme.AICameraTheme

class SettingsActivity : AppCompatActivity() {
    private lateinit var settingsManager: SettingsManager
    
    override fun attachBaseContext(newBase: Context) {
        val settingsManager = AICameraApplication.getSettingsManager(newBase)
        val currentLanguage = settingsManager.getCurrentLanguage()
        val updatedContext = LanguageManager.updateLanguage(newBase, currentLanguage)
        super.attachBaseContext(updatedContext)
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        settingsManager = AICameraApplication.getSettingsManager(this)
        
        setContent {
            AICameraTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SettingsScreen(
                        settingsManager = settingsManager,
                        onNavigateBack = { finish() }
                    )
                }
            }
        }
    }
}