package com.trio.today.ui.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/**
 * The capture surface (priority #1).
 *
 * Everything here serves sub-three-second entry:
 *  - the field is focused and the keyboard is up the moment it appears, so the
 *    first frame is already ready for typing;
 *  - the mic is one tap away and equally prominent, because speech "sidesteps
 *    the blank-page freeze" (§3);
 *  - submitting keeps the sheet open, so a brain-dump of six thoughts is six
 *    taps of the same key rather than six round trips;
 *  - there is no date picker, no priority, no project. What the parser
 *    understands, it takes; what it does not, it keeps as the title.
 *
 * Nothing here can fail in a way that loses text.
 */
@Composable
fun CaptureSheet(
    onSubmit: (String) -> Unit,
    onVoice: () -> Unit,
    onDone: () -> Unit,
    /** Text handed back from the speech recogniser, if any. */
    prefill: String? = null,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    var savedCount by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(prefill) {
        if (!prefill.isNullOrBlank()) text = prefill
    }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    fun submit() {
        val value = text.trim()
        if (value.isEmpty()) {
            onDone()
            return
        }
        onSubmit(value)
        text = ""
        savedCount += 1
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp),
    ) {
        Text(
            text = "What's on your mind?",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Try \"call the dentist tomorrow at 3pm\"",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            placeholder = { Text("Add a task") },
            singleLine = false,
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onVoice) {
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = "Capture by voice",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (savedCount > 0) {
                    Text(
                        text = if (savedCount == 1) "1 added" else "$savedCount added",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
                IconButton(onClick = { submit() }) {
                    Icon(
                        Icons.Filled.Send,
                        contentDescription = "Save task",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}
