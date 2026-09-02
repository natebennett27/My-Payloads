package com.trio.today.ui.setup

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.trio.today.reminder.OemBatteryGuidance
import com.trio.today.domain.OemGuidance

/**
 * The reminder-reliability setup wizard.
 *
 * The report calls OEM battery optimisation "the hidden killer" and says an
 * app marketed on reminders must plan an in-app setup wizard that detects the
 * device brand (§6). This is that wizard.
 *
 * It is the one screen that breaks the near-zero-setup rule, and it earns the
 * exception: a reminder that silently never arrives is worse than no app at
 * all, and no API can fix a Samsung "deep sleeping app" from inside the
 * process. It is three taps, it is skippable, and it never appears again.
 */
@Composable
fun ReliabilityScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val guidance = remember { OemBatteryGuidance.forThisDevice() }

    // Re-read on each recomposition after returning from a settings screen.
    var refreshToken by remember { mutableStateOf(0) }
    val exactAlarmsAllowed = remember(refreshToken) {
        com.trio.today.reminder.AlarmScheduler(context).canScheduleExactAlarms()
    }
    val batteryExempt = remember(refreshToken) {
        OemBatteryGuidance.isIgnoringBatteryOptimizations(context)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Make reminders stick",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Android puts apps to sleep to save battery, which can quietly " +
                "stop reminders arriving. Two quick permissions fix that for good.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(28.dp))

        StepCard(
            done = exactAlarmsAllowed,
            title = "Allow alarms & reminders",
            body = "Lets a reminder fire at the time you set, even in battery saver.",
            actionLabel = "Open setting",
            onAction = {
                OemBatteryGuidance.exactAlarmSettingsIntent(context)?.let { launch(context, it) }
                refreshToken++
            },
            actionEnabled = !exactAlarmsAllowed,
        )

        Spacer(Modifier.height(12.dp))

        StepCard(
            done = batteryExempt,
            title = "Turn off battery optimisation",
            body = "Stops the system pausing the app between reminders.",
            actionLabel = "Open setting",
            onAction = {
                launch(context, OemBatteryGuidance.batteryOptimizationIntent(context))
                refreshToken++
            },
            actionEnabled = !batteryExempt,
        )

        if (guidance != null) {
            Spacer(Modifier.height(12.dp))
            OemCard(guidance = guidance, onOpen = {
                OemBatteryGuidance.resolveSettingsIntent(context, guidance)?.let { launch(context, it) }
                refreshToken++
            })
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Done", style = MaterialTheme.typography.labelLarge)
        }
        TextButton(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Skip for now")
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StepCard(
    done: Boolean,
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    actionEnabled: Boolean,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = if (done) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (actionEnabled) {
                    TextButton(
                        onClick = onAction,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 0.dp,
                            vertical = 4.dp,
                        ),
                    ) {
                        Text(actionLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun OemCard(guidance: OemGuidance, onOpen: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "One more on ${guidance.brandLabel}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${guidance.brandLabel} devices have their own power manager that " +
                    "can override the settings above.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(10.dp))
            guidance.steps.forEachIndexed { index, step ->
                Row(modifier = Modifier.padding(vertical = 3.dp)) {
                    Text(
                        text = "${index + 1}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(width = 22.dp, height = 20.dp),
                    )
                    Text(
                        text = step,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onOpen) { Text("Take me there") }
        }
    }
}

/**
 * OEM settings screens are undocumented and frequently renamed between
 * firmware versions, so a missing activity is expected rather than
 * exceptional. Failing silently leaves the written steps on screen, which
 * still get the user there.
 */
private fun launch(context: Context, intent: Intent) {
    runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
