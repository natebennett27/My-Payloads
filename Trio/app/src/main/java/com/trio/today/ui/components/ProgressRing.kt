package com.trio.today.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The at-a-glance progress surface (priority #2).
 *
 * A ring rather than a bar or a count. The report's finding on list guilt (§3)
 * is that seeing what remains is demoralising, so this shows only the fraction
 * of a small, achievable set that is done -- it fills up, and an empty ring at
 * the start of the day is a blank slate rather than a debt.
 *
 * The ring never turns red and has no "behind schedule" state.
 */
@Composable
fun ProgressRing(
    done: Int,
    total: Int,
    modifier: Modifier = Modifier,
    size: Dp = 108.dp,
    strokeWidth: Dp = 10.dp,
) {
    val target = if (total <= 0) 0f else done.toFloat() / total.toFloat()
    val progress by animateFloatAsState(
        targetValue = target.coerceIn(0f, 1f),
        // Slow enough to be seen filling; the movement is part of the reward.
        animationSpec = tween(durationMillis = 620),
        label = "progress",
    )

    val trackColor = MaterialTheme.colorScheme.primaryContainer
    val fillColor = MaterialTheme.colorScheme.primary

    val description = when {
        total <= 0 -> "Nothing on today's list yet"
        done == total -> "All $total done"
        else -> "$done of $total done"
    }

    Box(
        modifier = modifier
            .size(size)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            val inset = strokeWidth.toPx() / 2f
            val arcSize = androidx.compose.ui.geometry.Size(
                this.size.width - strokeWidth.toPx(),
                this.size.height - strokeWidth.toPx(),
            )
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = stroke,
            )
            if (progress > 0f) {
                drawArc(
                    color = fillColor,
                    // Start at twelve o'clock, fill clockwise.
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = arcSize,
                    style = stroke,
                )
            }
        }

        Text(
            text = if (total <= 0) "–" else "$done/$total",
            style = MaterialTheme.typography.headlineSmall,
            color = if (total > 0 && done == total) fillColor else MaterialTheme.colorScheme.onSurface,
        )
    }
}
