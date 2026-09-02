package com.trio.today.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.trio.today.R
import com.trio.today.TrioApp
import com.trio.today.domain.Task
import com.trio.today.ui.capture.QuickCaptureActivity
import kotlinx.coroutines.flow.first

/**
 * The home-screen widget.
 *
 * Two jobs, both straight from the report's Stage 1 (§4, Recommendations):
 *
 *  1. Capture without opening the app. The "+" opens the transparent capture
 *     sheet directly, which is the shortest path a thought can take from
 *     occurring to saved.
 *  2. Show today's short list at a glance, so progress is visible without any
 *     interaction at all -- and so the app stays present on a home screen
 *     instead of being forgotten, which is how the "productivity app
 *     graveyard" starts (§3).
 *
 * Tapping a task completes it in place. The widget shows the same capped list
 * as the app, so it can never become the backlog the app is hiding.
 */
class TrioWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as TrioApp).container
        val open = container.taskRepository.observeToday().first()
        val doneToday = container.taskRepository.observeCompletedToday().first().size

        provideContent {
            GlanceTheme {
                WidgetBody(open = open, doneToday = doneToday)
            }
        }
    }

    @Composable
    private fun WidgetBody(open: List<Task>, doneToday: Int) {
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(20.dp)
                .padding(14.dp),
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Today",
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = GlanceTheme.colors.onSurface,
                    ),
                    modifier = GlanceModifier.defaultWeight(),
                )
                Text(
                    text = "$doneToday/${doneToday + open.size}",
                    style = TextStyle(
                        fontSize = 14.sp,
                        color = GlanceTheme.colors.onSurfaceVariant,
                    ),
                )
                Spacer(GlanceModifier.width(10.dp))
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_add),
                    contentDescription = "Add a task",
                    modifier = GlanceModifier
                        .size(26.dp)
                        .clickable(actionStartActivity<QuickCaptureActivity>()),
                )
            }

            Spacer(GlanceModifier.height(10.dp))

            if (open.isEmpty()) {
                Text(
                    text = if (doneToday > 0) "All done." else "Tap + to add one.",
                    style = TextStyle(
                        fontSize = 14.sp,
                        color = GlanceTheme.colors.onSurfaceVariant,
                    ),
                )
            } else {
                open.take(MAX_ROWS).forEach { task ->
                    WidgetTaskRow(task)
                    Spacer(GlanceModifier.height(6.dp))
                }
            }
        }
    }

    @Composable
    private fun WidgetTaskRow(task: Task) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(GlanceTheme.colors.secondaryContainer)
                .cornerRadius(12.dp)
                .padding(horizontal = 10.dp, vertical = 9.dp)
                .clickable(actionRunCallback<CompleteTaskAction>(CompleteTaskAction.params(task.id))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_circle),
                contentDescription = null,
                modifier = GlanceModifier.size(16.dp),
            )
            Spacer(GlanceModifier.width(10.dp))
            Text(
                text = task.title,
                maxLines = 1,
                style = TextStyle(
                    fontSize = 14.sp,
                    color = GlanceTheme.colors.onSecondaryContainer,
                ),
            )
        }
    }

    companion object {
        private const val MAX_ROWS = 4

        /** Redraws every placed widget. Call after any change to today's list. */
        suspend fun refresh(context: Context) {
            runCatching { TrioWidget().updateAll(context) }
        }
    }
}
