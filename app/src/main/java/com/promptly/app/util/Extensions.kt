package com.promptly.app.util

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.promptly.app.ui.theme.PromptlyGreen
import com.promptly.app.ui.theme.PromptlyGreenDark
import com.promptly.app.ui.theme.PromptlyGreenLight

/**
 * Small helpers used by multiple screens.
 *  - format duration for snooze chips
 *  - compute a "due-soon" tint for reminder cards
 */
object Extensions {

    fun snoozeLabel(minutes: Int): String = when {
        minutes < 60 -> "${minutes}m"
        minutes % 60 == 0 -> "${minutes / 60}h"
        else -> "${minutes / 60}h ${minutes % 60}m"
    }

    /**
     * Tint for a reminder card based on how soon it fires.
     *  - < 1h  : warm orange (urgent)
     *  - < 24h : brand green (active)
     *  - else  : muted gray (calm)
     */
    fun dueTint(millisUntilDue: Long): Color = when {
        millisUntilDue < 60 * 60 * 1000 -> Color(0xFFE65100)
        millisUntilDue < 24 * 60 * 60 * 1000 -> PromptlyGreen
        else -> Color(0xFF757575)
    }
}
