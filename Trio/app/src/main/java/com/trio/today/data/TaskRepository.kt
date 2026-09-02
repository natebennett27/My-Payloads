package com.trio.today.data

import com.trio.today.data.db.TaskDao
import com.trio.today.data.db.toDomain
import com.trio.today.data.db.toEntity
import com.trio.today.domain.Bucket
import com.trio.today.domain.CaptureParser
import com.trio.today.domain.DailyRollover
import com.trio.today.domain.ParsedCapture
import com.trio.today.domain.RolloverAction
import com.trio.today.domain.Task
import com.trio.today.reminder.AlarmScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The single place tasks are read and written.
 *
 * Also owns the side effects that must stay in lockstep with the data: alarms
 * are scheduled and cancelled here so a task and its reminder can never drift
 * apart, which is how "the reminder never came" bugs start.
 */
class TaskRepository(
    private val dao: TaskDao,
    private val alarms: AlarmScheduler,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    /**
     * The Rule of 3 cap.
     *
     * The report is blunt about this (§3, §4): a list of 40 items with 3 done
     * is net-negative dopamine. Today holds a handful of real commitments;
     * everything else waits in Later, out of sight.
     */
    val todayCap: Int get() = TODAY_CAP

    fun observeToday(): Flow<List<Task>> =
        dao.observeToday().map { rows -> rows.map { it.toDomain() } }

    fun observeLater(): Flow<List<Task>> =
        dao.observeLater().map { rows -> rows.map { it.toDomain() } }

    fun observeCompletedToday(): Flow<List<Task>> =
        dao.observeCompletedSince(startOfToday()).map { rows -> rows.map { it.toDomain() } }

    /**
     * Captures a raw phrase.
     *
     * Capture never rejects input. If Today is already full the task still
     * saves -- it just lands in Later, and the caller is told so it can say so
     * gently. Losing a thought because a list was full would be the worst
     * possible outcome for this audience.
     */
    suspend fun capture(raw: String, now: LocalDateTime = LocalDateTime.now(zone)): CaptureResult {
        val parsed = CaptureParser.parse(raw, now)
        if (parsed.title.isBlank()) return CaptureResult.Empty

        val todayIsFull = dao.countOpenToday() >= TODAY_CAP
        val bucket = when {
            parsed.bucket == Bucket.LATER -> Bucket.LATER
            todayIsFull -> Bucket.LATER
            else -> Bucket.TODAY
        }

        val task = Task(
            title = parsed.title,
            bucket = bucket,
            createdAt = now.atZone(zone).toInstant().toEpochMilli(),
            remindAt = parsed.remindAt?.atZone(zone)?.toInstant()?.toEpochMilli(),
            timeAnchor = parsed.timeAnchor,
            recurrence = parsed.recurrence,
            sortOrder = nextSortOrder(bucket),
        )

        val id = dao.insert(task.toEntity())
        val saved = task.copy(id = id)
        saved.remindAt?.let { alarms.schedule(saved) }

        return CaptureResult.Saved(
            task = saved,
            parsed = parsed,
            divertedBecauseTodayIsFull = todayIsFull && parsed.bucket != Bucket.LATER,
        )
    }

    /** Marks a task done and, if it repeats, queues the next instance. */
    suspend fun complete(taskId: Long, nowMillis: Long = System.currentTimeMillis()) {
        val entity = dao.byId(taskId) ?: return
        val task = entity.toDomain()
        alarms.cancel(task.id)
        dao.update(task.copy(completedAt = nowMillis, bucket = Bucket.DONE).toEntity())

        val recurrence = task.recurrence ?: return
        val nextDate = recurrence.nextAfter(LocalDate.now(zone))
        val nextRemind = task.remindAt
            ?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalTime() }
            ?.let { nextDate.atTime(it).atZone(zone).toInstant().toEpochMilli() }

        val next = task.copy(
            id = 0L,
            bucket = if (nextDate == LocalDate.now(zone)) Bucket.TODAY else Bucket.LATER,
            completedAt = null,
            createdAt = nowMillis,
            remindAt = nextRemind,
            deferCount = 0,
        )
        val newId = dao.insert(next.toEntity())
        next.copy(id = newId).takeIf { it.remindAt != null }?.let { alarms.schedule(it) }
    }

    /** Undoes a completion. Always available -- a mis-swipe must never cost anything. */
    suspend fun uncomplete(taskId: Long) {
        val task = dao.byId(taskId)?.toDomain() ?: return
        val restored = task.copy(completedAt = null, bucket = Bucket.TODAY)
        dao.update(restored.toEntity())
        restored.remindAt?.let { alarms.schedule(restored) }
    }

    /**
     * "Not right now."
     *
     * Moves a task off Today without comment or penalty. This is the forgiving
     * alternative to letting it sit there accruing shame (§3).
     */
    suspend fun defer(taskId: Long) {
        val task = dao.byId(taskId)?.toDomain() ?: return
        alarms.cancel(task.id)
        dao.update(
            task.copy(
                bucket = Bucket.LATER,
                remindAt = null,
                deferCount = task.deferCount + 1,
                sortOrder = nextSortOrder(Bucket.LATER),
            ).toEntity()
        )
    }

    /** Pulls a task from Later onto Today. */
    suspend fun promote(taskId: Long) {
        val task = dao.byId(taskId)?.toDomain() ?: return
        dao.update(
            task.copy(bucket = Bucket.TODAY, sortOrder = nextSortOrder(Bucket.TODAY)).toEntity()
        )
    }

    suspend fun rename(taskId: Long, title: String) {
        val task = dao.byId(taskId)?.toDomain() ?: return
        if (title.isBlank()) return
        dao.update(task.copy(title = title.trim()).toEntity())
    }

    suspend fun setReminder(taskId: Long, remindAtMillis: Long?) {
        val task = dao.byId(taskId)?.toDomain() ?: return
        val updated = task.copy(remindAt = remindAtMillis)
        dao.update(updated.toEntity())
        if (remindAtMillis == null) alarms.cancel(task.id) else alarms.schedule(updated)
    }

    suspend fun delete(taskId: Long) {
        alarms.cancel(taskId)
        dao.delete(taskId)
    }

    suspend fun taskById(id: Long): Task? = dao.byId(id)?.toDomain()

    /** Re-arms every live reminder. Called after a reboot, an update, or a clock change. */
    suspend fun rescheduleAllReminders() {
        dao.allWithReminders().forEach { alarms.schedule(it.toDomain()) }
    }

    /**
     * Applies the gentle overnight reset. See [DailyRollover] for the rules.
     */
    suspend fun runDailyRollover(
        newDay: LocalDate = LocalDate.now(zone),
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val tasks = dao.allToday().map { it.toDomain() }
        DailyRollover.plan(tasks, newDay, nowMillis).forEach { action ->
            when (action) {
                is RolloverAction.Keep -> Unit

                is RolloverAction.Archive ->
                    dao.update(action.task.copy(bucket = Bucket.DONE).toEntity())

                is RolloverAction.MoveToLater -> {
                    alarms.cancel(action.task.id)
                    dao.update(
                        action.task.copy(
                            bucket = Bucket.LATER,
                            remindAt = null,
                            sortOrder = nextSortOrder(Bucket.LATER),
                        ).toEntity()
                    )
                }

                is RolloverAction.Reschedule -> {
                    val time = action.task.remindAt
                        ?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalTime() }
                    val nextRemind = time
                        ?.let { action.nextDate.atTime(it).atZone(zone).toInstant().toEpochMilli() }
                    val updated = action.task.copy(
                        remindAt = nextRemind,
                        bucket = if (action.nextDate == newDay) Bucket.TODAY else Bucket.LATER,
                    )
                    dao.update(updated.toEntity())
                    if (nextRemind != null) alarms.schedule(updated) else alarms.cancel(updated.id)
                }
            }
        }
        // Keep completed history to a week; it feeds the "what you did" strip.
        dao.purgeCompletedBefore(startOfToday() - SEVEN_DAYS_MILLIS)
    }

    private suspend fun nextSortOrder(bucket: Bucket): Int =
        (dao.minSortOrder(bucket.name) ?: 0) - 1

    private fun startOfToday(): Long =
        LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()

    companion object {
        private const val TODAY_CAP = 5
        private const val SEVEN_DAYS_MILLIS = 7L * 24 * 60 * 60 * 1000
    }
}

sealed interface CaptureResult {
    data object Empty : CaptureResult

    data class Saved(
        val task: Task,
        val parsed: ParsedCapture,
        /** True when Today was already full, so the task waits in Later instead. */
        val divertedBecauseTodayIsFull: Boolean,
    ) : CaptureResult
}
