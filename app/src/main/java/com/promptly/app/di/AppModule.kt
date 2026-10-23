package com.promptly.app.di

import android.content.Context
import com.promptly.app.data.backup.BackupManager
import com.promptly.app.data.db.PromptlyDatabase
import com.promptly.app.data.db.ReminderDao
import com.promptly.app.data.repository.ReminderRepository
import com.promptly.app.data.repository.SettingsRepository
import com.promptly.app.data.ringtone.RingtoneHelper
import com.promptly.app.data.storage.PictureStorage
import com.promptly.app.notifications.NotificationHelper
import com.promptly.app.notifications.ReminderScheduler
import com.promptly.app.util.Permissions
import com.promptly.app.util.TimeUtils
import com.promptly.app.widget.WidgetUpdater
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module wiring up the singletons of the app.
 *
 * Note: ViewModels are NOT here — they use @HiltViewModel + @Inject constructor
 * and Hilt auto-provides them.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): PromptlyDatabase =
        PromptlyDatabase.build(ctx)

    @Provides
    fun provideReminderDao(db: PromptlyDatabase): ReminderDao = db.reminderDao()

    @Provides
    @Singleton
    fun provideTimeUtils(@ApplicationContext ctx: Context): TimeUtils = TimeUtils(ctx)

    @Provides
    @Singleton
    fun providePictureStorage(@ApplicationContext ctx: Context): PictureStorage =
        PictureStorage(ctx)

    @Provides
    @Singleton
    fun provideRingtoneHelper(@ApplicationContext ctx: Context): RingtoneHelper =
        RingtoneHelper(ctx)

    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext ctx: Context): SettingsRepository =
        SettingsRepository(ctx)

    @Provides
    @Singleton
    fun provideReminderRepository(
        dao: ReminderDao,
        timeUtils: TimeUtils
    ): ReminderRepository = ReminderRepository(dao, timeUtils)

    @Provides
    @Singleton
    fun provideNotificationHelper(
        @ApplicationContext ctx: Context,
        ringtoneHelper: RingtoneHelper,
        settings: SettingsRepository
    ): NotificationHelper = NotificationHelper(ctx, ringtoneHelper, settings)

    @Provides
    @Singleton
    fun provideReminderScheduler(
        @ApplicationContext ctx: Context,
        repository: ReminderRepository,
        notificationHelper: NotificationHelper
    ): ReminderScheduler = ReminderScheduler(ctx, repository, notificationHelper)

    @Provides
    @Singleton
    fun provideWidgetUpdater(@ApplicationContext ctx: Context): WidgetUpdater =
        WidgetUpdater(ctx)

    @Provides
    @Singleton
    fun provideBackupManager(
        @ApplicationContext ctx: Context,
        repository: ReminderRepository,
        pictureStorage: PictureStorage,
        ringtoneHelper: RingtoneHelper
    ): BackupManager = BackupManager(ctx, repository, pictureStorage, ringtoneHelper)

    @Provides
    @Singleton
    fun providePermissions(@ApplicationContext ctx: Context): Permissions = Permissions(ctx)
}
