package com.trio.today.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * A deliberately calm palette.
 *
 * Note what is missing: there is no error red anywhere in the task surface.
 * The report is explicit that overdue red badges and red counters are a
 * primary driver of ADHD-app abandonment (§4), so the palette simply does not
 * offer the app a way to shout at someone.
 */

// Light
val Ink = Color(0xFF1B1C1E)
val InkMuted = Color(0xFF5C5F66)
val Paper = Color(0xFFFBFAF7)
val PaperRaised = Color(0xFFFFFFFF)
val Edge = Color(0xFFE6E3DC)

// A single warm accent carries progress, focus and the completion moment.
val Accent = Color(0xFF3F7D6B)
val AccentSoft = Color(0xFFD9EAE3)
val AccentBright = Color(0xFF5EA98F)

// Dark
val InkDark = Color(0xFFEDEDEA)
val InkMutedDark = Color(0xFFA0A3A8)
val PaperDark = Color(0xFF141517)
val PaperRaisedDark = Color(0xFF1E2023)
val EdgeDark = Color(0xFF32353A)
val AccentDark = Color(0xFF7FC3A9)
val AccentSoftDark = Color(0xFF24352F)

/** Celebration confetti. Warm, not alarming. */
val CelebrationColors = listOf(
    Color(0xFF5EA98F),
    Color(0xFFE8C468),
    Color(0xFFE39A6B),
    Color(0xFF8FB8DE),
    Color(0xFFC59BD1),
)

/**
 * A muted clay used only for Material's `error` role, which some components
 * require. The task surface never routes anything through it -- a task being
 * unfinished is not an error.
 */
val MutedError = Color(0xFFA8695C)
