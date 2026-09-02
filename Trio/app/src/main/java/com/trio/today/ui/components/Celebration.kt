package com.trio.today.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.trio.today.ui.theme.CelebrationColors
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class Particle(
    val angleRadians: Float,
    val speed: Float,
    val color: Color,
    val radiusPx: Float,
    val spin: Float,
)

/**
 * A brief burst of confetti over the whole screen when a task is completed.
 *
 * Kept to roughly 900ms and drawn on a single Canvas that ignores touch, so the
 * reward never gets in the way of the next action. The report's warning about
 * gamification (§2, Habitica) is about maintenance burden and punishment, not
 * about celebration -- a burst that costs the user nothing and asks nothing of
 * them is the safe form of this.
 */
@Composable
fun Celebration(
    /** Changes on every completion; a new value replays the burst. */
    trigger: Int,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(1f) }

    val particles = remember(trigger) {
        val random = Random(trigger)
        List(PARTICLE_COUNT) {
            Particle(
                // Biased upward: gravity pulls them back down through the frame.
                angleRadians = (random.nextFloat() * 2f - 1f) * 1.15f - 1.57f,
                speed = 0.55f + random.nextFloat() * 0.85f,
                color = CelebrationColors[random.nextInt(CelebrationColors.size)],
                radiusPx = 5f + random.nextFloat() * 6f,
                spin = random.nextFloat() * 2f - 1f,
            )
        }
    }

    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 900, easing = LinearEasing))
    }

    val t = progress.value
    if (t >= 1f) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val origin = Offset(size.width / 2f, size.height * 0.62f)
        val reach = size.minDimension * 0.9f
        // Fade out over the last third so the burst clears rather than blinks.
        val alpha = if (t < 0.66f) 1f else 1f - (t - 0.66f) / 0.34f

        particles.forEach { particle ->
            val distance = particle.speed * reach * t
            val gravity = GRAVITY * reach * t * t
            val x = origin.x + cos(particle.angleRadians) * distance + particle.spin * 30f * t
            val y = origin.y + sin(particle.angleRadians) * distance + gravity

            drawCircle(
                color = particle.color.copy(alpha = alpha.coerceIn(0f, 1f)),
                radius = particle.radiusPx * (1f - 0.35f * t),
                center = Offset(x, y),
            )
        }
    }
}

private const val PARTICLE_COUNT = 34
private const val GRAVITY = 0.95f
