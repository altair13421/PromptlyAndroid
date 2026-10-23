package com.promptly.app.data.repository

import com.promptly.app.data.db.ReminderDao
import com.promptly.app.data.model.RecurrenceRule
import com.promptly.app.data.model.ReminderEntity
import com.promptly.app.data.model.ReminderStatus
import com.promptly.app.util.TimeUtils
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for reminder CRUD + lifecycle transitions.
 *
 * Scheduling side-effects (arming/canceling AlarmManager) are NOT done here —
 * the ReminderScheduler picks up changes via a Flow watcher in PromptlyApplication.
 * That separation keeps the repository synchronous on DB and lets the scheduler
 * retry / re-arm without coupling to DB writes.
 */
@Singleton
class ReminderRepository @Inject constructor(
    private val dao: ReminderDao,
    private val timeUtils: TimeUtils
) {

    fun observeAll(): Flow<List<ReminderEntity>> = dao.observeAll()

    fun observeActive(): Flow<List<ReminderEntity>> = dao.observeActive()

    fun observeDone(): Flow<List<ReminderEntity>> = dao.observeDone()

    fun observeToday(): Flow<List<ReminderEntity>> {
        val (start, end) = timeUtils.todayRangeUtc()
        return dao.observeDueBetween(start, end)
    }

    fun observeById(id: Long): Flow<ReminderEntity?> = dao.observeById(id)

    suspend fun getById(id: Long): ReminderEntity? = dao.getById(id)

    suspend fun getNextActive(limit: Int = 3): List<ReminderEntity> =
        dao.getNextActive(limit)

    /**
     * Insert a brand-new reminder. Returns the new row id.
     */
    suspend fun create(reminder: ReminderEntity): Long {
        val now = System.currentTimeMillis()
        return dao.insert(reminder.copy(createdAt = now, updatedAt = now))
    }

    /**
     * Update an existing reminder. Refreshes updatedAt.
     */
    suspend fun update(reminder: ReminderEntity) {
        dao.update(reminder.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(id: Long) = dao.deleteById(id)

    /**
     * Mark this occurrence as done. If the reminder is recurring, advance the
     * triggerAt to the next occurrence and reset status to PENDING so it fires
     * again on schedule. Otherwise flip status to DONE.
     */
    suspend fun markDone(id: Long) {
        val r = dao.getById(id) ?: return
        val now = System.currentTimeMillis()

        if (r.recurrence.isRepeating) {
            val next = timeUtils.nextOccurrence(
                fromMillis = r.triggerAt,
                rule = r.recurrence,
                now = now
            )
            if (next == null) {
                dao.update(r.copy(status = ReminderStatus.DONE, updatedAt = now))
            } else {
                dao.update(
                    r.copy(
                        triggerAt = next,
                        status = ReminderStatus.PENDING,
                        lastFiredAt = now,
                        occurrenceCount = r.occurrenceCount + 1,
                        updatedAt = now
                    )
                )
            }
        } else {
            dao.update(r.copy(status = ReminderStatus.DONE, lastFiredAt = now, updatedAt = now))
        }
    }

    /**
     * Snooze a reminder by [minutes] from now. Status becomes SNOOZED.
     */
    suspend fun snooze(id: Long, minutes: Int = 10) {
        val r = dao.getById(id) ?: return
        val newTrigger = System.currentTimeMillis() + minutes * 60_000L
        dao.update(
            r.copy(
                triggerAt = newTrigger,
                status = ReminderStatus.SNOOZED,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * Skip just this occurrence of a recurring reminder, advance to the next.
     */
    suspend fun skipOccurrence(id: Long) {
        val r = dao.getById(id) ?: return
        if (!r.recurrence.isRepeating) {
            markDone(id)
            return
        }
        val next = timeUtils.nextOccurrence(
            fromMillis = r.triggerAt,
            rule = r.recurrence,
            now = System.currentTimeMillis()
        )
        if (next == null) {
            dao.update(r.copy(status = ReminderStatus.DONE, updatedAt = System.currentTimeMillis()))
        } else {
            dao.update(
                r.copy(
                    triggerAt = next,
                    status = ReminderStatus.PENDING,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    /**
     * All reminders whose triggerAt has passed but still PENDING/SNOOZED.
     * Used by the boot receiver to re-arm missed alarms.
     */
    suspend fun getOverdue(): List<ReminderEntity> =
        dao.getOverdue(System.currentTimeMillis())

    suspend fun getAllActive(): List<ReminderEntity> = dao.getAllActive()

    /**
     * Backup / restore entry points.
     */
    suspend fun getAll(): List<ReminderEntity> = dao.getAll()
    suspend fun replaceAll(reminders: List<ReminderEntity>) {
        dao.deleteAll()
        dao.insertAll(reminders)
    }
}
