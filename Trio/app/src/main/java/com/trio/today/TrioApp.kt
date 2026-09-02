package com.trio.today

import android.app.Application
import android.content.Context
import com.trio.today.data.SettingsStore
import com.trio.today.data.TaskRepository
import com.trio.today.data.db.TrioDatabase
import com.trio.today.reminder.AlarmScheduler
import com.trio.today.reminder.NotificationHelper
import com.trio.today.work.DailyRolloverWorker

/**
 * Hand-rolled dependency container.
 *
 * A DI framework would earn its keep in a larger app; here it would add a
 * plugin, an annotation processor and a build-time cost for four objects.
 */
class AppContainer(context: Context) {
    private val database = TrioDatabase.get(context)

    val alarmScheduler = AlarmScheduler(context)
    val settingsStore = SettingsStore(context)
    val taskRepository = TaskRepository(database.taskDao(), alarmScheduler)
}

class TrioApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.ensureChannel(this)
        DailyRolloverWorker.enqueue(this)
    }
}
