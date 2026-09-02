package com.trio.today.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.trio.today.TrioApp
import com.trio.today.widget.TrioWidget
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Runs the gentle overnight reset.
 *
 * Unlike reminders, this is not time-critical to the minute -- nothing is shown
 * to the user when it runs -- so WorkManager is the right tool and its
 * deferrals are harmless. The app also runs the same reset on launch if the day
 * changed while it was closed, which means a device whose OEM killed the worker
 * still gets a correct Today list.
 */
class DailyRolloverWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as TrioApp).container
        return runCatching {
            container.taskRepository.runDailyRollover()
            container.settingsStore.setLastRolloverDay(LocalDate.now().toEpochDay())
            TrioWidget.refresh(applicationContext)
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }

    companion object {
        private const val WORK_NAME = "daily_rollover"

        /** Schedules the reset for just after 04:00, which is past almost everyone's "day". */
        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailyRolloverWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(initialDelayMinutes(), TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        private fun initialDelayMinutes(): Long {
            val now = java.time.LocalDateTime.now()
            var next = now.toLocalDate().atTime(ROLLOVER_TIME)
            if (!next.isAfter(now)) next = next.plusDays(1)
            return Duration.between(now, next).toMinutes().coerceAtLeast(1)
        }

        private val ROLLOVER_TIME: LocalTime = LocalTime.of(4, 0)
    }
}
