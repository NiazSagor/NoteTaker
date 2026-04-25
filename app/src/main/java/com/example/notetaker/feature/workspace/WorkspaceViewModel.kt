package com.example.notetaker.feature.workspace

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.notetaker.core.data.db.entity.GridElementEntity
import com.example.notetaker.core.data.db.entity.GridElementWithContent
import com.example.notetaker.core.domain.base.Result
import com.example.notetaker.core.domain.usecase.auth.ObserveUserIdUseCase
import com.example.notetaker.core.domain.usecase.auth.SignInAnonymouslyUseCase
import com.example.notetaker.core.domain.usecase.note.CreateNoteParams
import com.example.notetaker.core.domain.usecase.note.CreateNoteUseCase
import com.example.notetaker.core.domain.usecase.workspace.GetGridElementsUseCase
import com.example.notetaker.core.domain.usecase.workspace.ReorderGridElementUseCase
import com.example.notetaker.core.domain.usecase.workspace.ReorderParams
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WorkspaceUiState(
    val gridElements: List<GridElementWithContent> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val userId: String? = null
)

sealed class WorkspaceEvent {
    data class OnReorder(val elementId: String, val newOrderIndex: Double) : WorkspaceEvent()
    object OnCreateNote : WorkspaceEvent()
    data class OnAddImage(val uri: String) : WorkspaceEvent()
}

@HiltViewModel
class WorkspaceViewModel @Inject constructor(
    private val getGridElementsUseCase: GetGridElementsUseCase,
    private val createNoteUseCase: CreateNoteUseCase,
    private val reorderGridElementUseCase: ReorderGridElementUseCase,
    private val observeUserIdUseCase: ObserveUserIdUseCase,
    private val signInAnonymouslyUseCase: SignInAnonymouslyUseCase,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val workspaceId = "global_workspace"

    private val _uiState = MutableStateFlow(WorkspaceUiState())
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    init {
        observeAuth()
        observeGridElements()
    }

    private fun observeAuth() {
        observeUserIdUseCase(Unit)
            .onEach { result ->
                if (result is Result.Success) {
                    val userId = result.data
                    _uiState.update { it.copy(userId = userId) }
                    if (userId == null) {
                        signInAnonymously()
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun signInAnonymously() {
        viewModelScope.launch {
            signInAnonymouslyUseCase(Unit)
        }
    }

    private fun observeGridElements() {
        getGridElementsUseCase(workspaceId)
            .onEach { result ->
                when (result) {
                    is Result.Success -> {
                        _uiState.update { it.copy(gridElements = result.data, isLoading = false) }
                    }
                    is Result.Error -> {
                        _uiState.update { it.copy(error = result.exception.message, isLoading = false) }
                    }
                    Result.Loading -> {
                        _uiState.update { it.copy(isLoading = true) }
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: WorkspaceEvent) {
        when (event) {
            is WorkspaceEvent.OnCreateNote -> createNote()
            is WorkspaceEvent.OnReorder -> reorder(event.elementId, event.newOrderIndex)
            is WorkspaceEvent.OnAddImage -> { /* TODO */ }
        }
    }

    private fun createNote() {
        val userId = "NIAZ" /*uiState.value.userId ?: return*/
        val nextOrderIndex = (uiState.value.gridElements.lastOrNull()?.element?.orderIndex ?: 0.0) + 1.0

        viewModelScope.launch {
            createNoteUseCase(CreateNoteParams(workspaceId, userId, nextOrderIndex))
        }
    }

    private fun reorder(elementId: String, newOrderIndex: Double) {
        viewModelScope.launch {
            reorderGridElementUseCase(ReorderParams(elementId, newOrderIndex))
        }
    }
}
