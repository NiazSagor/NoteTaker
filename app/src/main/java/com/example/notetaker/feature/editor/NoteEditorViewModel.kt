package com.example.notetaker.feature.editor

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.notetaker.core.domain.base.Result
import com.example.notetaker.core.data.db.entity.NoteEntity
import com.example.notetaker.core.data.db.entity.NoteImageEntity
import com.example.notetaker.core.data.sync.CloudinaryUploadWorker
import com.example.notetaker.core.domain.usecase.auth.ObserveUserIdUseCase
import com.example.notetaker.core.domain.usecase.image.AddNoteImageParams
import com.example.notetaker.core.domain.usecase.image.AddNoteImageUseCase
import com.example.notetaker.core.domain.usecase.image.ObserveNoteImagesUseCase
import com.example.notetaker.core.domain.usecase.image.UpdateNoteImageRotationUseCase
import com.example.notetaker.core.domain.usecase.image.UpdateRotationParams
import com.example.notetaker.core.domain.usecase.note.ObserveNoteUseCase
import com.example.notetaker.core.domain.usecase.note.UpdateNoteParams
import com.example.notetaker.core.domain.usecase.note.UpdateNoteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.onSuccess

data class NoteEditorUiState(
    val note: NoteEntity? = null,
    val images: List<NoteImageEntity> = emptyList(),
    val draftTitle: String = "",
    val draftContent: String = "",
    val userId: String? = null,
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
    private val observeNoteUseCase: ObserveNoteUseCase,
    private val observeNoteImagesUseCase: ObserveNoteImagesUseCase,
    private val updateNoteUseCase: UpdateNoteUseCase,
    private val addNoteImageUseCase: AddNoteImageUseCase,
    private val updateNoteImageRotationUseCase: UpdateNoteImageRotationUseCase,
    private val observeUserIdUseCase: ObserveUserIdUseCase,
    private val savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context // Inject context to enqueue worker
) : ViewModel() {

    private val noteId: String = checkNotNull(savedStateHandle["noteId"])
    private val workspaceId: String = "global_workspace"

    private val _uiState = MutableStateFlow(NoteEditorUiState())
    val uiState: StateFlow<NoteEditorUiState> = _uiState.asStateFlow()

    init {
        observeAuth()
        observeNote()
        observeImages()
        setupAutoSave()
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

    private fun observeNote() {
        observeNoteUseCase(noteId)
            .onEach { result ->
                if (result is Result.Success && result.data != null) {
                    val note = result.data
                    _uiState.update {
                        it.copy(
                            note = note,
                            draftTitle = savedStateHandle["draftTitle"] ?: note.title,
                            draftContent = savedStateHandle["draftContent"] ?: note.content
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
        // Auto-save title
        _uiState.map { it.draftTitle }
            .distinctUntilChanged()
            .debounce(500)
            .onEach { saveNote() }
            .launchIn(viewModelScope)

        // Auto-save content
        _uiState.map { it.draftContent }
            .distinctUntilChanged()
            .debounce(500)
            .onEach { saveNote() }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: NoteEditorEvent) {
        when (event) {
            is NoteEditorEvent.OnTitleChange -> {
                _uiState.update { it.copy(draftTitle = event.newTitle) }
                savedStateHandle["draftTitle"] = event.newTitle
            }
            is NoteEditorEvent.OnContentChange -> {
                _uiState.update { it.copy(draftContent = event.newContent) }
                savedStateHandle["draftContent"] = event.newContent
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
        val userId = uiState.value.userId ?: return
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

    private fun addImage(uri: String) {
        val userId = uiState.value.userId ?: return
        viewModelScope.launch {
            addNoteImageUseCase(
                AddNoteImageParams(
                    noteId = noteId,
                    workspaceId = workspaceId,
                    localUri = uri,
                    userId = userId,
                    orderInNote = uiState.value.images.size // Append to the end
                )
            )

//                .onSuccess {
//                // Once the NoteImageEntity is saved locally with PENDING status,
//                // enqueue the CloudinaryUploadWorker to handle the actual file upload.
//                //CloudinaryUploadWorker.enqueue(context)
//            }.onFailure { error ->
//                // Handle potential errors during local save of image metadata
//                Log.e("NoteEditorViewModel", "Failed to add image locally: ${error.message}", error)
//            }
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
