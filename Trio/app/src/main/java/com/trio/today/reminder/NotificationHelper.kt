package com.trio.today.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import com.trio.today.MainActivity
import com.trio.today.R

object NotificationHelper {

    const val CHANNEL_REMINDERS = "reminders"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService<NotificationManager>() ?: return
        if (manager.getNotificationChannel(CHANNEL_REMINDERS) != null) return

        val channel = NotificationChannel(
            CHANNEL_REMINDERS,
            context.getString(R.string.channel_reminders_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_reminders_description)
            enableVibration(true)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Posts a reminder.
     *
     * The actions matter as much as the text. "Done" completes without opening
     * the app, and "Not right now" moves the task on with no penalty -- both
     * are one tap, because making the user open an app to dismiss a nudge is
     * how nudges start getting swiped away unread.
     */
    fun postReminder(context: Context, taskId: Long, taskTitle: String) {
        ensureChannel(context)

        val (title, body) = ReminderCopy.forTask(taskTitle, seed = taskId)

        val open = PendingIntent.getActivity(
            context,
            taskId.toInt(),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(
                0,
                context.getString(R.string.action_done),
                NotificationActionReceiver.pendingIntent(
                    context, taskId, NotificationActionReceiver.ACTION_COMPLETE
                ),
            )
            .addAction(
                0,
                context.getString(R.string.action_not_right_now),
                NotificationActionReceiver.pendingIntent(
                    context, taskId, NotificationActionReceiver.ACTION_DEFER
                ),
            )
            .build()

        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            runCatching {
                NotificationManagerCompat.from(context).notify(taskId.toInt(), notification)
            }
        }
    }

    fun cancel(context: Context, taskId: Long) {
        NotificationManagerCompat.from(context).cancel(taskId.toInt())
    }
}
