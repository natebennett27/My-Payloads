package com.trio.today.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * "Not right now."
 *
 * The escape hatch the report's newer ADHD apps all ship in some form -- Focus
 * One's "Too Hard Right Now" is the clearest example (§2). It moves a task off
 * Today with no penalty, no confirmation and no record the user can see.
 *
 * Giving people a graceful way out is what stops the list becoming a monument
 * to things they did not do, which is the mechanism behind list guilt (§3).
 */
@Composable
fun NotRightNowButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.WatchLater,
            contentDescription = "Not right now",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
    }
}
