package com.trio.today.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.trio.today.TrioApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Fires when a task's exact alarm goes off. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmScheduler.ACTION_REMIND) return
        val taskId = intent.getLongExtra(AlarmScheduler.EXTRA_TASK_ID, -1L)
        if (taskId < 0) return

        val titleFromIntent = intent.getStringExtra(AlarmScheduler.EXTRA_TASK_TITLE)
        if (titleFromIntent != null) {
            NotificationHelper.postReminder(context, taskId, titleFromIntent)
            return
        }

        // The extra can be missing if the alarm was re-armed after a reboot from
        // a cancel-shaped intent. Fall back to the database.
        val pending = goAsync()
        val container = (context.applicationContext as TrioApp).container
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val task = container.taskRepository.taskById(taskId)
                if (task != null && !task.isDone) {
                    NotificationHelper.postReminder(context, taskId, task.title)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
