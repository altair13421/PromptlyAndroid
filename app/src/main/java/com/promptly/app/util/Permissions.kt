package com.promptly.app.util

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * Permission helpers — runtime checks + intent-launch helpers for the
 * system-settings pages Android forces us to navigate to.
 *
 * On API 29+ we have to handle three separate flows:
 *
 *  1. POST_NOTIFICATIONS  -- runtime permission only on API 33+
 *  2. SCHEDULE_EXACT_ALARM -- special access API 31+ (must open settings page)
 *  3. Battery optimization -- special access, must open settings page
 */
class Permissions(@ApplicationContext private val context: Context) {

    fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * True if the app can schedule exact alarms. On API < 31 this is always true.
     */
    fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    /**
     * Intent that opens the system "exact alarm" settings page.
     */
    fun exactAlarmSettingsIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }

    /**
     * True if the app is whitelisted from battery optimization.
     */
    fun isBatteryOptimizationWhitelisted(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE)
            as android.os.PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Intent that asks the user to whitelist the app from battery optimization.
     */
    fun batteryOptimizationIntent(): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
}
