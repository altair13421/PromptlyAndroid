package com.promptly.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.promptly.app.data.model.ReminderEntity
import com.promptly.app.data.model.ReminderStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: ReminderEntity): Long

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: Long): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE id = :id")
    fun observeById(id: Long): Flow<ReminderEntity?>

    /**
     * All reminders, ordered by next trigger time ascending.
     */
    @Query("SELECT * FROM reminders ORDER BY triggerAt ASC")
    fun observeAll(): Flow<List<ReminderEntity>>

    /**
     * Only pending / snoozed reminders, ordered by next fire time.
     * These are what shows up on the home screen "Upcoming" tab and on the widget.
     */
    @Query(
        """
        SELECT * FROM reminders
        WHERE status IN ('PENDING', 'SNOOZED')
        ORDER BY triggerAt ASC
        """
    )
    fun observeActive(): Flow<List<ReminderEntity>>

    /**
     * Reminders due today (between start-of-day and end-of-day in user's tz).
     * Caller is responsible for computing the day boundaries in UTC millis.
     */
    @Query(
        """
        SELECT * FROM reminders
        WHERE status IN ('PENDING', 'SNOOZED')
          AND triggerAt BETWEEN :startOfDay AND :endOfDay
        ORDER BY triggerAt ASC
        """
    )
    fun observeDueBetween(startOfDay: Long, endOfDay: Long): Flow<List<ReminderEntity>>

    @Query(
        """
        SELECT * FROM reminders
        WHERE status IN ('PENDING', 'SNOOZED')
        ORDER BY triggerAt ASC
        LIMIT :limit
        """
    )
    suspend fun getNextActive(limit: Int): List<ReminderEntity>

    /**
     * Reminders already marked done — used by the "Done" filter tab.
     */
    @Query(
        """
        SELECT * FROM reminders
        WHERE status = 'DONE'
        ORDER BY updatedAt DESC
        """
    )
    fun observeDone(): Flow<List<ReminderEntity>>

    /**
     * All PENDING/SNOOZED reminders whose trigger time is in the past — i.e. the
     * scheduler missed them (reboot, Doze, etc.). The boot receiver pulls these
     * and re-arms them.
     */
    @Query(
        """
        SELECT * FROM reminders
        WHERE status IN ('PENDING', 'SNOOZED')
          AND triggerAt <= :now
        ORDER BY triggerAt ASC
        """
    )
    suspend fun getOverdue(now: Long): List<ReminderEntity>

    /**
     * Bulk update — used by the boot receiver to re-arm alarms efficiently.
     */
    @Query("SELECT * FROM reminders WHERE status IN ('PENDING', 'SNOOZED')")
    suspend fun getAllActive(): List<ReminderEntity>

    /**
     * Used by backup export — pulls every row regardless of status.
     */
    @Query("SELECT * FROM reminders ORDER BY createdAt ASC")
    suspend fun getAll(): List<ReminderEntity>

    /**
     * Wipes the table — used by restore-from-backup before re-inserting.
     */
    @Query("DELETE FROM reminders")
    suspend fun deleteAll()

    /**
     * Bulk insert for restore-from-backup.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(reminders: List<ReminderEntity>)
}
