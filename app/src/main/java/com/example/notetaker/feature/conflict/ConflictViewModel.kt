package com.example.notetaker.feature.conflict

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.notetaker.core.domain.base.Result
import com.example.notetaker.core.domain.model.Conflict
import com.example.notetaker.core.domain.model.ResolutionStrategy
import com.example.notetaker.core.domain.usecase.auth.ObserveUserIdUseCase
import com.example.notetaker.core.domain.usecase.conflict.ObserveConflictsForNoteUseCase
import com.example.notetaker.core.domain.usecase.conflict.ResolveConflictParams
import com.example.notetaker.core.domain.usecase.conflict.ResolveConflictUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConflictUiState(
    val conflicts: List<Conflict> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val userId: String? = null
)

sealed class ConflictEvent {
    data class Resolve(val conflictId: String, val strategy: ResolutionStrategy) : ConflictEvent()
}

@HiltViewModel
class ConflictViewModel @Inject constructor(
    private val observeConflictsForNoteUseCase: ObserveConflictsForNoteUseCase,
    private val resolveConflictUseCase: ResolveConflictUseCase,
    private val observeUserIdUseCase: ObserveUserIdUseCase,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val noteId: String =
        checkNotNull(savedStateHandle["noteId"]) // Assuming noteId is passed via navigation

    private val _uiState = MutableStateFlow(ConflictUiState())
    val uiState: StateFlow<ConflictUiState> = _uiState.asStateFlow()

    init {
        observeAuth()
        observeConflicts()
    }

    private fun observeAuth() {
        observeUserIdUseCase(Unit)
            .onEach { result ->
                if (result is Result.Success) {
                    _uiState.update { it.copy(userId = result.data) }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeConflicts() {
        observeConflictsForNoteUseCase(noteId)
            .onEach { result ->
                when (result) {
                    is Result.Success -> {
                        _uiState.update { it.copy(conflicts = result.data, isLoading = false) }
                    }

                    is Result.Error -> {
                        _uiState.update {
                            it.copy(
                                error = result.exception.message,
                                isLoading = false
                            )
                        }
                    }

                    Result.Loading -> {
                        _uiState.update { it.copy(isLoading = true) }
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: ConflictEvent) {
        when (event) {
            is ConflictEvent.Resolve -> resolveConflict(event.conflictId, event.strategy)
        }
    }

    private fun resolveConflict(conflictId: String, strategy: ResolutionStrategy) {
        val userId = uiState.value.userId ?: return
        viewModelScope.launch {
            resolveConflictUseCase(ResolveConflictParams(conflictId, noteId, strategy, userId))
        }
    }
}
