package com.example.notetaker.feature.editor

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.notetaker.core.data.sync.ImageKitUploadWorker
import com.example.notetaker.core.domain.base.Result
import com.example.notetaker.core.domain.model.Conflict
import com.example.notetaker.core.domain.model.Note
import com.example.notetaker.core.domain.model.NoteImage
import com.example.notetaker.core.domain.usecase.auth.ObserveUserIdUseCase
import com.example.notetaker.core.domain.usecase.conflict.ObserveConflictsForNoteUseCase
import com.example.notetaker.core.domain.usecase.image.*
import com.example.notetaker.core.domain.usecase.note.ObserveNoteUseCase
import com.example.notetaker.core.domain.usecase.note.UpdateNoteParams
import com.example.notetaker.core.domain.usecase.note.UpdateNoteUseCase
import com.example.notetaker.core.util.FileUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NoteEditorUiState(
    val note: Note? = null,
    val images: List<NoteImage> = emptyList(),
    val draftTitle: String = "",
    val draftContent: String = "",
    val userId: String? = null,
    val hasConflict: Boolean? = false,
    val interactionState: ImageInteractionState = ImageInteractionState.Idle
)

sealed class ImageInteractionState {
    object Idle : ImageInteractionState()
    data class Selected(val imageId: String) : ImageInteractionState()
    data class Rotating(
        val imageId: String,
        val currentDegrees: Float,
        val showHud: Boolean
    ) : ImageInteractionState()
}

sealed class NoteEditorEvent {
    data class OnTitleChange(val newTitle: String) : NoteEditorEvent()
    data class OnContentChange(val newContent: String) : NoteEditorEvent()
    data class OnAddImage(val uri: String) : NoteEditorEvent()
    data class OnImageSelected(val imageId: String) : NoteEditorEvent()
    data class OnRotationStarted(val imageId: String, val initialDegrees: Float) : NoteEditorEvent()
    data class OnRotationChanged(val degrees: Float) : NoteEditorEvent()
    object OnRotationEnded : NoteEditorEvent()
}

@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    private val observeConflictsForNoteUseCase: ObserveConflictsForNoteUseCase,
    private val observeNoteUseCase: ObserveNoteUseCase,
    private val observeNoteImagesUseCase: ObserveNoteImagesUseCase,
    private val updateNoteUseCase: UpdateNoteUseCase,
    private val addNoteImageUseCase: AddNoteImageUseCase,
    private val updateNoteImageRotationUseCase: UpdateNoteImageRotationUseCase,
    private val observeUserIdUseCase: ObserveUserIdUseCase,
    private val savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context // TODO: remove context: android.content.Context
) : ViewModel() {
    private val TAG = "NoteEditorViewModel"
    private val userEditTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val noteId: String = checkNotNull(savedStateHandle["noteId"])
    private val workspaceId: String = "global_workspace"

    private val _uiState = MutableStateFlow(NoteEditorUiState())
    val uiState: StateFlow<NoteEditorUiState> = _uiState.asStateFlow()

    init {
        setupAutoSave()
        observeAuth()
        observeImages()
        observeNote()
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
                        _uiState.update { it.copy(hasConflict = result.data.isNotEmpty()) }
//                        _uiState.update { it.copy(hasConflict = true) }
                    }
                    is Result.Error -> {
                        Log.e(TAG, "Error observing conflicts: ${result.exception.message}")
                    }
                    Result.Loading -> {
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeNote() {
        observeNoteUseCase(noteId)
            .onEach { result ->
                if (result is Result.Success && result.data != null) {
                    val note = result.data
                    _uiState.update {
                        it.copy(
                            note = note,
                            draftTitle = note.title,
                            draftContent = note.content
                        )
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeImages() {
        observeNoteImagesUseCase(noteId)
            .onEach { result ->
                if (result is Result.Success) {
                    _uiState.update { it.copy(images = result.data) }
                }
            }
            .launchIn(viewModelScope)
    }

    private fun setupAutoSave() {
        userEditTrigger
            .debounce(500)
            .onEach { saveNote() }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: NoteEditorEvent) {
        when (event) {
            is NoteEditorEvent.OnTitleChange -> {
                _uiState.update { it.copy(draftTitle = event.newTitle) }
                userEditTrigger.tryEmit(Unit)
            }
            is NoteEditorEvent.OnContentChange -> {
                _uiState.update { it.copy(draftContent = event.newContent) }
                userEditTrigger.tryEmit(Unit)
            }
            is NoteEditorEvent.OnAddImage -> addImage(event.uri)
            is NoteEditorEvent.OnImageSelected -> {
                _uiState.update { it.copy(interactionState = ImageInteractionState.Selected(event.imageId)) }
            }
            is NoteEditorEvent.OnRotationStarted -> {
                _uiState.update {
                    it.copy(interactionState = ImageInteractionState.Rotating(event.imageId, event.initialDegrees, true))
                }
            }
            is NoteEditorEvent.OnRotationChanged -> {
                val current = _uiState.value.interactionState
                if (current is ImageInteractionState.Rotating) {
                    _uiState.update { it.copy(interactionState = current.copy(currentDegrees = event.degrees)) }
                }
            }
            is NoteEditorEvent.OnRotationEnded -> finalizeRotation()
        }
    }

    private fun saveNote() {
        val userId = "NIAZ" // uiState.value.userId ?: "ANONYMOUS"
        viewModelScope.launch {
            updateNoteUseCase(
                UpdateNoteParams(
                    noteId = noteId,
                    title = uiState.value.draftTitle,
                    content = uiState.value.draftContent,
                    userId = userId
                )
            )
        }
    }

    private fun addImage(uriString: String) {
        val userId = "NIAZ" // uiState.value.userId ?: "ANONYMOUS"
        viewModelScope.launch {
            val sourceUri = Uri.parse(uriString)
            val internalUri = FileUtils.copyUriToInternalStorage(context, sourceUri, "note_images")
            
            if (internalUri != null) {
                addNoteImageUseCase(
                    AddNoteImageParams(
                        noteId = noteId,
                        workspaceId = workspaceId,
                        localUri = internalUri.toString(),
                        userId = userId,
                        orderInNote = uiState.value.images.size
                    )
                )
            } else {
                Log.e(TAG, "Failed to copy image to internal storage")
            }
        }
    }

    private fun finalizeRotation() {
        val current = _uiState.value.interactionState
        if (current is ImageInteractionState.Rotating) {
            viewModelScope.launch {
                updateNoteImageRotationUseCase(
                    UpdateRotationParams(current.imageId, current.currentDegrees)
                )
                _uiState.update { it.copy(interactionState = ImageInteractionState.Selected(current.imageId)) }
            }
        }
    }
}
