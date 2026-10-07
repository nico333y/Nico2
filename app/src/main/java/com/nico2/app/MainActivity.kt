package com.nico2.app

import android.os.Bundle
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        val language = UserPreferences.read(newBase).appLanguage
        val locale = Locale.forLanguageTag(language)
        val configuration = Configuration(newBase.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dispatchVoiceNotificationIntent(intent)
        setContent {
            ChatScreen()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        dispatchVoiceNotificationIntent(intent)
    }

    private fun dispatchVoiceNotificationIntent(intent: Intent?) {
        if (intent?.action == VoiceSessionNotification.ACTION_OPEN) {
            VoiceNotificationCommands.dispatch(VoiceSessionNotification.ACTION_OPEN)
        }
    }
}
