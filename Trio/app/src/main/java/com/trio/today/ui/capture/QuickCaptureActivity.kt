package com.trio.today.ui.capture

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.trio.today.TrioApp
import com.trio.today.ui.theme.TrioTheme
import com.trio.today.widget.TrioWidget
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * A capture surface that opens over whatever the user was doing.
 *
 * This activity is the whole point of the capture priority. It is transparent,
 * excluded from recents, and reachable from the home-screen widget, a launcher
 * long-press shortcut, the Assistant's "create note" action and the Android
 * share sheet. A thought can go from occurring to saved without the app ever
 * really "opening", which is what sub-three-second capture actually requires.
 */
class QuickCaptureActivity : ComponentActivity() {

    private val speech = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return@registerForActivityResult
        pendingVoiceText = spoken
    }

    private var pendingVoiceText: String? by mutableStateOf(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Text arriving from the share sheet or the Assistant is captured
        // immediately -- there is nothing to ask the user about.
        val shared = intent?.getStringExtra(Intent.EXTRA_TEXT)
            ?: intent?.getStringExtra(Intent.EXTRA_SUBJECT)

        setContent {
            TrioTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .imePadding()
                            .navigationBarsPadding(),
                    ) {
                        CaptureSheet(
                            prefill = pendingVoiceText ?: shared,
                            onSubmit = ::save,
                            onVoice = ::startVoiceCapture,
                            onDone = { finish() },
                        )
                    }
                }
            }
        }
    }

    private fun save(raw: String) {
        val container = (application as TrioApp).container
        lifecycleScope.launch {
            container.taskRepository.capture(raw)
            TrioWidget.refresh(applicationContext)
        }
    }

    private fun startVoiceCapture() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "What needs doing?")
        }
        runCatching { speech.launch(intent) }.onFailure {
            // No recogniser installed. Say so once and leave the keyboard --
            // never block capture on a missing optional path.
            Toast.makeText(this, "No voice input available on this device", Toast.LENGTH_SHORT).show()
        }
    }
}
