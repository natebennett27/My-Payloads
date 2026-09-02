package com.trio.today.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.trio.today.TrioApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Re-arms every reminder after the conditions that silently wipe alarms.
 *
 * Android drops all scheduled alarms on reboot, and an app update replaces the
 * process without carrying them over. Without this receiver a user who reboots
 * their phone loses every future reminder with no visible sign -- which reads
 * to them as "the app just stopped working", the single most common complaint
 * behind ADHD-app abandonment (§6).
 *
 * Also handles clock and timezone changes, which shift when a wall-clock
 * reminder should fire.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> Unit
            else -> return
        }

        val pending = goAsync()
        val container = (context.applicationContext as TrioApp).container
        CoroutineScope(Dispatchers.IO).launch {
            try {
                container.taskRepository.rescheduleAllReminders()
            } finally {
                pending.finish()
            }
        }
    }
}
