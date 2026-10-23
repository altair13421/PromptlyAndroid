package com.promptly.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.promptly.app.notifications.NotificationHelper
import com.promptly.app.ui.navigation.PromptlyNavHost
import com.promptly.app.ui.theme.PromptlyTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var settings: com.promptly.app.data.repository.SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install splash screen before super.onCreate.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Ensure notification channels exist on every launch (idempotent).
        notificationHelper.ensureChannels()

        setContent {
            val darkMode = settings.darkMode.collectAsState(initial = com.promptly.app.data.repository.SettingsRepository.DarkMode.SYSTEM)
            val dynamic = settings.dynamicColor.collectAsState(initial = true)

            val effectiveDark = when (darkMode.value) {
                com.promptly.app.data.repository.SettingsRepository.DarkMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                com.promptly.app.data.repository.SettingsRepository.DarkMode.ON -> true
                com.promptly.app.data.repository.SettingsRepository.DarkMode.OFF -> false
            }

            PromptlyTheme(
                darkMode = effectiveDark,
                dynamicColor = dynamic.value
            ) {
                PromptlyNavHost()
            }
        }
    }
}
