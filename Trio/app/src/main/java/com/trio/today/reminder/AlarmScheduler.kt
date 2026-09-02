package com.trio.today.reminder

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService
import com.trio.today.MainActivity
import com.trio.today.domain.Task

/**
 * Schedules reminders so they actually arrive.
 *
 * The report calls Android reminder reliability "the hidden killer" and a P0
 * requirement (§6): OEM battery managers on Samsung, Xiaomi, OnePlus and Oppo
 * silently delay or drop anything scheduled through ordinary background work.
 *
 * So this class does the one thing that survives Doze, app standby and
 * aggressive OEM power saving: [AlarmManager.setAlarmClock]. It is the same
 * mechanism a real alarm clock uses, the system exempts it from batching, and
 * it is the only tier Android guarantees will fire at the stated time.
 *
 * The cost is honest: the user sees a pending-alarm indicator, and on
 * Android 12+ we need the "Alarms & reminders" permission. That is the trade
 * the report asks for -- a reminder that does not arrive is worse than no app.
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager: AlarmManager? = context.getSystemService()

    /** Whether the system will currently let us set an exact alarm. */
    fun canScheduleExactAlarms(): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            alarmManager?.canScheduleExactAlarms() == true
        else -> true
    }

    @SuppressLint("MissingPermission")
    fun schedule(task: Task) {
        val remindAt = task.remindAt ?: return
        val manager = alarmManager ?: return

        // A reminder in the past would fire immediately and read as a glitch.
        if (remindAt <= System.currentTimeMillis()) return

        val pending = reminderIntent(task.id, task.title)

        if (canScheduleExactAlarms()) {
            // setAlarmClock is the strongest tier available: it is exempt from
            // Doze batching and, unlike setExactAndAllowWhileIdle, is not
            // rate-limited to roughly once every nine minutes per app.
            val showIntent = PendingIntent.getActivity(
                context,
                task.id.toInt(),
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            manager.setAlarmClock(AlarmManager.AlarmClockInfo(remindAt, showIntent), pending)
        } else {
            // Permission was withdrawn. Degrade rather than crash, and let the
            // setup screen tell the user why reminders may drift.
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, remindAt, pending)
        }
    }

    fun cancel(taskId: Long) {
        val manager = alarmManager ?: return
        manager.cancel(reminderIntent(taskId, title = null))
    }

    private fun reminderIntent(taskId: Long, title: String?): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMIND
            // A distinct data URI keeps PendingIntents for different tasks from
            // being treated as equal and overwriting one another.
            data = android.net.Uri.parse("trio://task/$taskId")
            putExtra(EXTRA_TASK_ID, taskId)
            title?.let { putExtra(EXTRA_TASK_TITLE, it) }
        }
        return PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_REMIND = "com.trio.today.action.REMIND"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TASK_TITLE = "task_title"
    }
}
