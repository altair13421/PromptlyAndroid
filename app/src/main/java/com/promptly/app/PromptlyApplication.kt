package com.promptly.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.promptly.app.notifications.ReminderScheduler
import com.promptly.app.widget.WidgetUpdater
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application entry point — Hilt graph anchor + scheduler live-arm.
 *
 * Watches the active-reminders Flow and re-arms AlarmManager whenever the set
 * of PENDING/SNOOZED reminders changes. This means DB writes anywhere in the
 * app automatically flow through to the alarm scheduler without each callsite
 * having to remember to call scheduler.schedule(...).
 */
@HiltAndroidApp
class PromptlyApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var widgetUpdater: WidgetUpdater

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Live-arm: any change to active reminders triggers a re-schedule pass.
        scheduler.observeAndArm()
            .onEach { widgetUpdater.refresh() }
            .launchIn(appScope)

        // Refresh widget on app start.
        appScope.launch { widgetUpdater.refresh() }
    }
}
