package com.trio.today.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    /**
     * Open tasks on Today.
     *
     * Ordering puts timed tasks first (they have a real deadline), then capture
     * order. Completed tasks are excluded here and surfaced separately so the
     * list shrinks as the day goes on.
     */
    @Query(
        """
        SELECT * FROM tasks
        WHERE bucket = 'TODAY' AND completedAt IS NULL
        ORDER BY CASE WHEN remindAt IS NULL THEN 1 ELSE 0 END, remindAt ASC, sortOrder ASC, id ASC
        """
    )
    fun observeToday(): Flow<List<TaskEntity>>

    /** Tasks completed since [since], newest first, for the day's "what you did" strip. */
    @Query(
        "SELECT * FROM tasks WHERE completedAt IS NOT NULL AND completedAt >= :since ORDER BY completedAt DESC"
    )
    fun observeCompletedSince(since: Long): Flow<List<TaskEntity>>

    @Query(
        """
        SELECT * FROM tasks
        WHERE bucket = 'LATER' AND completedAt IS NULL
        ORDER BY sortOrder ASC, createdAt DESC
        """
    )
    fun observeLater(): Flow<List<TaskEntity>>

    @Query("SELECT COUNT(*) FROM tasks WHERE bucket = 'TODAY' AND completedAt IS NULL")
    suspend fun countOpenToday(): Int

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun byId(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks WHERE bucket = 'TODAY'")
    suspend fun allToday(): List<TaskEntity>

    /** Every task with a live reminder, used to re-arm alarms after a reboot. */
    @Query("SELECT * FROM tasks WHERE remindAt IS NOT NULL AND completedAt IS NULL")
    suspend fun allWithReminders(): List<TaskEntity>

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: Long)

    /** Archives tasks completed before [before] so the table does not grow without bound. */
    @Query("DELETE FROM tasks WHERE completedAt IS NOT NULL AND completedAt < :before")
    suspend fun purgeCompletedBefore(before: Long)

    @Query("SELECT MIN(sortOrder) FROM tasks WHERE bucket = :bucket")
    suspend fun minSortOrder(bucket: String): Int?
}
