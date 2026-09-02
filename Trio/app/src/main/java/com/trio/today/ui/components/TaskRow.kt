package com.trio.today.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trio.today.domain.Task
import kotlinx.coroutines.launch

/** Fraction of the row width a swipe must cross to count as done. */
private const val COMMIT_FRACTION = 0.4f

/**
 * A single task, completed by swiping right.
 *
 * Swipe-to-complete plus a haptic is called out in the report as the minimum
 * viable delight (§4), and it has a second virtue: it is a large, imprecise
 * gesture. A small checkbox demands aim; a swipe across the whole row does not,
 * which matters when someone is moving fast or distracted.
 *
 * The row commits at 40% of its width. Below that it springs back with no
 * penalty and no dialog -- an accidental half-swipe should cost nothing.
 *
 * @param timeLabel already-formatted reminder time, or null for anytime tasks.
 * @param onThresholdCrossed fired once per swipe as the row crosses the commit
 *   point, so the caller can play the light haptic tick that tells a user the
 *   gesture has taken without them having to look.
 */
@Composable
fun TaskRow(
    task: Task,
    timeLabel: String?,
    onComplete: () -> Unit,
    onDefer: () -> Unit,
    onClick: () -> Unit,
    onThresholdCrossed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val offsetX = remember(task.id) { Animatable(0f) }
    var rowWidth by remember(task.id) { mutableIntStateOf(1) }
    var hasCrossed by remember(task.id) { mutableStateOf(false) }

    val threshold = rowWidth * COMMIT_FRACTION
    val swipeProgress = if (threshold <= 0f) 0f else (offsetX.value / threshold).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            // The track behind the row carries the accent, so the gesture shows
            // its own progress before it commits.
            .background(MaterialTheme.colorScheme.primaryContainer)
            .onSizeChanged { rowWidth = it.width.coerceAtLeast(1) },
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 22.dp)
                .size(26.dp)
                .alpha(swipeProgress)
                .graphicsLayer {
                    // The tick swells as the swipe commits.
                    val scale = 0.7f + 0.5f * swipeProgress
                    scaleX = scale
                    scaleY = scale
                },
        )

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = offsetX.value }
                .pointerInput(task.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX.value >= threshold) {
                                scope.launch {
                                    // Finish the sweep before the row leaves the
                                    // list, so completion looks deliberate
                                    // rather than abrupt.
                                    offsetX.animateTo(rowWidth.toFloat(), tween(180))
                                    onComplete()
                                }
                            } else {
                                hasCrossed = false
                                scope.launch { offsetX.animateTo(0f, tween(220)) }
                            }
                        },
                        onDragCancel = {
                            hasCrossed = false
                            scope.launch { offsetX.animateTo(0f, tween(220)) }
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            // Right-swipe only, and never past the row width.
                            val next = (offsetX.value + dragAmount).coerceIn(0f, rowWidth.toFloat())
                            offsetX.snapTo(next)
                            if (!hasCrossed && next >= threshold) {
                                hasCrossed = true
                                onThresholdCrossed()
                            } else if (hasCrossed && next < threshold) {
                                hasCrossed = false
                            }
                        }
                    }
                }
                .semantics {
                    contentDescription = buildString {
                        append(task.title)
                        if (timeLabel != null) append(", at $timeLabel")
                    }
                },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(start = 18.dp, end = 8.dp, top = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (timeLabel != null) {
                        Text(
                            text = timeLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            // Time is informational here, never a warning colour.
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }

                NotRightNowButton(onClick = onDefer)
            }
        }
    }

    // A recycled row must never inherit the previous task's swipe offset.
    LaunchedEffect(task.id) {
        offsetX.snapTo(0f)
        hasCrossed = false
    }
}
