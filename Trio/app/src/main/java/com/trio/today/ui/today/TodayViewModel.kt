package com.trio.today.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.trio.today.data.CaptureResult
import com.trio.today.data.SettingsStore
import com.trio.today.data.TaskRepository
import com.trio.today.domain.Task
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TodayUiState(
    val open: List<Task> = emptyList(),
    val completedToday: List<Task> = emptyList(),
    val laterCount: Int = 0,
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
) {
    val doneCount: Int get() = completedToday.size
    val total: Int get() = open.size + completedToday.size
    val allDone: Boolean get() = total > 0 && open.isEmpty()
}

/** A short-lived message shown in a snackbar. Never an error, never a scold. */
data class Nudge(val id: Long, val text: String, val undoTaskId: Long? = null)

class TodayViewModel(
    private val repository: TaskRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    private val _nudge = MutableStateFlow<Nudge?>(null)
    val nudge: StateFlow<Nudge?> = _nudge.asStateFlow()

    /** Incremented on every completion so the celebration replays. */
    private val _celebrationTrigger = MutableStateFlow(0)
    val celebrationTrigger: StateFlow<Int> = _celebrationTrigger.asStateFlow()

    val state: StateFlow<TodayUiState> = combine(
        repository.observeToday(),
        repository.observeCompletedToday(),
        repository.observeLater(),
        settings.soundEnabled,
        settings.hapticsEnabled,
    ) { open, completed, later, sound, haptics ->
        TodayUiState(
            open = open,
            completedToday = completed,
            laterCount = later.size,
            soundEnabled = sound,
            hapticsEnabled = haptics,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    val todayCap: Int get() = repository.todayCap

    init {
        // If the app was closed across a day boundary the scheduled worker may
        // not have run (or an OEM killed it). Catching up on launch means the
        // list is always correct by the time the user sees it.
        viewModelScope.launch {
            runCatching { repository.runDailyRollover() }
            settings.setLastRolloverDay(LocalDate.now().toEpochDay())
        }
    }

    fun capture(raw: String, onSaved: (CaptureResult.Saved) -> Unit = {}) {
        if (raw.isBlank()) return
        viewModelScope.launch {
            when (val result = repository.capture(raw)) {
                is CaptureResult.Empty -> Unit
                is CaptureResult.Saved -> {
                    if (result.divertedBecauseTodayIsFull) {
                        // Say where it went, and say why in a way that frames the
                        // cap as protection rather than rejection.
                        show("Saved to Later — today's list is full, and that's fine")
                    }
                    onSaved(result)
                }
            }
        }
    }

    fun complete(task: Task) {
        viewModelScope.launch {
            repository.complete(task.id)
            _celebrationTrigger.value = _celebrationTrigger.value + 1
            show("Done", undoTaskId = task.id)
        }
    }

    fun undoComplete(taskId: Long) {
        viewModelScope.launch { repository.uncomplete(taskId) }
    }

    fun defer(task: Task) {
        viewModelScope.launch {
            repository.defer(task.id)
            show("Moved to Later", undoTaskId = null)
        }
    }

    fun promote(taskId: Long) {
        viewModelScope.launch { repository.promote(taskId) }
    }

    fun rename(taskId: Long, title: String) {
        viewModelScope.launch { repository.rename(taskId, title) }
    }

    fun delete(taskId: Long) {
        viewModelScope.launch { repository.delete(taskId) }
    }

    fun clearReminder(taskId: Long) {
        viewModelScope.launch { repository.setReminder(taskId, null) }
    }

    fun dismissNudge() {
        _nudge.value = null
    }

    private fun show(text: String, undoTaskId: Long? = null) {
        _nudge.value = Nudge(id = System.currentTimeMillis(), text = text, undoTaskId = undoTaskId)
    }

    class Factory(
        private val repository: TaskRepository,
        private val settings: SettingsStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TodayViewModel(repository, settings) as T
    }
}
