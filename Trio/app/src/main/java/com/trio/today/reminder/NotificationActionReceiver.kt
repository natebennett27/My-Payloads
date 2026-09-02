package com.trio.today.reminder

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.trio.today.TrioApp
import com.trio.today.widget.TrioWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles the notification's two buttons so the user never has to open the app
 * to act on a nudge.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(AlarmScheduler.EXTRA_TASK_ID, -1L)
        if (taskId < 0) return

        val pending = goAsync()
        val container = (context.applicationContext as TrioApp).container
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_COMPLETE -> container.taskRepository.complete(taskId)
                    ACTION_DEFER -> container.taskRepository.defer(taskId)
                }
                NotificationHelper.cancel(appContext, taskId)
                TrioWidget.refresh(appContext)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE = "com.trio.today.action.COMPLETE"
        const val ACTION_DEFER = "com.trio.today.action.DEFER"

        fun pendingIntent(context: Context, taskId: Long, action: String): PendingIntent {
            val intent = Intent(context, NotificationActionReceiver::class.java).apply {
                this.action = action
                data = Uri.parse("trio://task/$taskId/$action")
                putExtra(AlarmScheduler.EXTRA_TASK_ID, taskId)
            }
            return PendingIntent.getBroadcast(
                context,
                taskId.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
