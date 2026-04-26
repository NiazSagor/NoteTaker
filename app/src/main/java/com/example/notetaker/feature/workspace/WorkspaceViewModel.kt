package com.example.notetaker.feature.workspace

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.notetaker.core.domain.base.Result
import com.example.notetaker.core.domain.model.GridElementWithContent
import com.example.notetaker.core.domain.usecase.auth.ObserveUserIdUseCase
import com.example.notetaker.core.domain.usecase.auth.SignInAnonymouslyUseCase
import com.example.notetaker.core.domain.usecase.note.CreateNoteParams
import com.example.notetaker.core.domain.usecase.note.CreateNoteUseCase
import com.example.notetaker.core.domain.usecase.workspace.GetGridElementsUseCase
import com.example.notetaker.core.domain.usecase.workspace.ReorderGridElementUseCase
import com.example.notetaker.core.domain.usecase.workspace.ReorderParams
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Collections
import javax.inject.Inject

private const val TAG = "WorkspaceViewModel"

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

    private val itemBounds = mutableMapOf<String, Rect>()
    private var lastHoveredId: String? = null

    private val _navigateToNoteEditor = MutableSharedFlow<String>()
    val navigateToNoteEditor: SharedFlow<String> = _navigateToNoteEditor

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

    fun updateItemBounds(id: String, rect: Rect) {
        itemBounds[id] = rect
    }

    fun onEvent(event: WorkspaceEvent) {
        when (event) {
            is WorkspaceEvent.OnCreateNote -> createNote()
            is WorkspaceEvent.OnReorder -> {}
            is WorkspaceEvent.OnAddImage -> { /* TODO */
            }
        }
    }

    fun getItemBounds(id: String): Rect? = itemBounds[id]

    fun onDragMove(draggedId: String, fingerPosition: Offset) {
        val hoveredId = itemBounds.entries
            .firstOrNull { (id, rect) ->
                id != draggedId && rect.contains(fingerPosition)
            }?.key

        if (hoveredId != null && hoveredId != lastHoveredId) {
            lastHoveredId = hoveredId
            swapGridElements(draggedId, hoveredId)
        }
    }

    private fun swapGridElements(fromId: String, toId: String) {
        val currentList = _uiState.value.gridElements.toMutableList()
        val fromIndex = currentList.indexOfFirst { it.element.id == fromId }
        val toIndex = currentList.indexOfFirst { it.element.id == toId }

        if (fromIndex != -1 && toIndex != -1) {
            Collections.swap(currentList, fromIndex, toIndex)
            // Update the UI state with the newly ordered list
            _uiState.update { it.copy(gridElements = currentList) }
        }
    }

    fun onDragEnd(draggedId: String) {
        lastHoveredId = null
        val currentList = _uiState.value.gridElements
        val droppedIndex = currentList.indexOfFirst { it.element.id == draggedId }

        if (droppedIndex == -1) return

        // 1. Get the indices of the items above and below the drop target
        val prevIndex = currentList.getOrNull(droppedIndex - 1)?.element?.orderIndex
        val nextIndex = currentList.getOrNull(droppedIndex + 1)?.element?.orderIndex

        // 2. Calculate the new midpoint
        val newOrderIndex = when {
            // Dropped at the very top
            prevIndex == null && nextIndex != null -> nextIndex - 1.0
            // Dropped at the very bottom
            prevIndex != null && nextIndex == null -> prevIndex + 1.0
            // Dropped between two items
            prevIndex != null && nextIndex != null -> (prevIndex + nextIndex) / 2.0
            // List was empty or only had one item
            else -> 0.0
        }

        viewModelScope.launch {
            val movedElement = currentList[droppedIndex].element.copy(
                orderIndex = newOrderIndex,
                updatedAt = System.currentTimeMillis()
            )

            // 3. PERSISTENCE: Only update this ONE record in Room & Firestore
            reorderGridElementUseCase(
                ReorderParams(
                    elementId = movedElement.id,
                    newOrderIndex = movedElement.orderIndex
                )
            )
        }
    }

    private fun createNote() {
        val userId = uiState.value.userId ?: return
        val nextOrderIndex =
            (uiState.value.gridElements.lastOrNull()?.element?.orderIndex ?: 0.0) + 1.0

        viewModelScope.launch {
            try {
                val result =
                    createNoteUseCase(CreateNoteParams(workspaceId, userId, nextOrderIndex))
                if (result is Result.Success) {
                    _navigateToNoteEditor.emit(result.data)
                }
            } catch (e: Exception) {
                // Handle error, e.g., show a toast or update error state
            }
        }

    }
}
