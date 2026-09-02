package com.trio.today.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trio.today.domain.Task
import com.trio.today.ui.TimeFormat
import com.trio.today.ui.components.Celebration
import com.trio.today.ui.components.CompletionFeedback
import com.trio.today.ui.components.ProgressRing
import com.trio.today.ui.components.TaskRow

/**
 * The one surface the app opens to.
 *
 * Everything the report says about at-a-glance progress (priority #2) reduces
 * to a rule this screen obeys strictly: the user sees a handful of tasks and a
 * ring, and nothing else. There is no backlog count in the header, no overdue
 * section, and no badge. The Later bin exists but you have to go and ask for it.
 */
@Composable
fun TodayScreen(
    viewModel: TodayViewModel,
    onOpenCapture: () -> Unit,
    onOpenLater: () -> Unit,
    onOpenTask: (Task) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nudge by viewModel.nudge.collectAsStateWithLifecycle()
    val celebrationTrigger by viewModel.celebrationTrigger.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val feedback = remember { CompletionFeedback(context) }

    // Fire sound and haptics from the screen rather than the ViewModel so the
    // reward lands with the animation, not a frame or two after the write.
    LaunchedEffect(celebrationTrigger) {
        if (celebrationTrigger > 0) {
            feedback.celebrate(
                withSound = state.soundEnabled,
                withHaptics = state.hapticsEnabled,
            )
        }
    }

    LaunchedEffect(nudge?.id) {
        val current = nudge ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = current.text,
            actionLabel = current.undoTaskId?.let { "Undo" },
        )
        if (result == SnackbarResult.ActionPerformed) {
            current.undoTaskId?.let(viewModel::undoComplete)
        }
        viewModel.dismissNudge()
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onOpenCapture,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Add", style = MaterialTheme.typography.labelLarge)
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "header") {
                    TodayHeader(
                        done = state.doneCount,
                        total = state.total,
                        allDone = state.allDone,
                    )
                }

                items(state.open, key = { it.id }) { task ->
                    TaskRow(
                        task = task,
                        timeLabel = task.remindAt?.let { TimeFormat.reminderLabel(context, it) },
                        onComplete = { viewModel.complete(task) },
                        onDefer = { viewModel.defer(task) },
                        onClick = { onOpenTask(task) },
                        onThresholdCrossed = { feedback.tick(state.hapticsEnabled) },
                    )
                }

                if (state.open.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(hasFinished = state.doneCount > 0)
                    }
                }

                item(key = "later") {
                    LaterLink(count = state.laterCount, onClick = onOpenLater)
                }
            }

            Celebration(
                trigger = celebrationTrigger,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun TodayHeader(done: Int, total: Int, allDone: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ProgressRing(done = done, total = total)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Today",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = when {
                    total == 0 -> "A clean slate."
                    allDone -> "That's the lot. Nicely done."
                    done == 0 -> "Pick one and start."
                    else -> "Keep going — you're moving."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun EmptyState(hasFinished: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (hasFinished) "Everything's done." else "Nothing here yet.",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (hasFinished) {
                "Stopping here is a perfectly good option."
            } else {
                "Add one thing. Three is a full day."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LaterLink(count: Int, onClick: () -> Unit) {
    if (count == 0) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onClick) {
            Icon(
                Icons.Outlined.Inbox,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            // Worded as a place, not a number of things owed. "12 waiting" and
            // "12 overdue" describe the same list and feel completely different.
            Text(
                text = if (count == 1) "1 waiting in Later" else "$count waiting in Later",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
