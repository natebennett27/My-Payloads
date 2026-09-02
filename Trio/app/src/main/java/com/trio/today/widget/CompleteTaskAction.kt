package com.trio.today.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import com.trio.today.TrioApp

/**
 * Completes a task straight from the widget.
 *
 * Deliberately has no confirmation step. A mis-tap is recoverable from the
 * app's undo, and asking "are you sure?" on the one genuinely rewarding action
 * in the app would be exactly the wrong trade.
 */
class CompleteTaskAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val taskId = parameters[TASK_ID] ?: return
        val container = (context.applicationContext as TrioApp).container
        container.taskRepository.complete(taskId)
        TrioWidget.refresh(context)
    }

    companion object {
        private val TASK_ID = ActionParameters.Key<Long>("task_id")

        fun params(taskId: Long): ActionParameters = actionParametersOf(TASK_ID to taskId)
    }
}
