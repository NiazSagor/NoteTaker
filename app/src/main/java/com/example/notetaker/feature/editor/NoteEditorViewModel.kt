package com.example.notetaker.feature.editor

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.notetaker.core.domain.base.Result
import com.example.notetaker.core.domain.model.Note
import com.example.notetaker.core.domain.model.NoteImage
import com.example.notetaker.core.domain.usecase.auth.ObserveUserIdUseCase
import com.example.notetaker.core.domain.usecase.conflict.ObserveConflictsForNoteUseCase
import com.example.notetaker.core.domain.usecase.image.AddNoteImageParams
import com.example.notetaker.core.domain.usecase.image.AddNoteImageUseCase
import com.example.notetaker.core.domain.usecase.image.ObserveNoteImagesUseCase
import com.example.notetaker.core.domain.usecase.image.UpdateNoteImageRotationUseCase
import com.example.notetaker.core.domain.usecase.image.UpdateRotationParams
import com.example.notetaker.core.domain.usecase.note.ObserveNoteUseCase
import com.example.notetaker.core.domain.usecase.note.UpdateNoteParams
import com.example.notetaker.core.domain.usecase.note.UpdateNoteUseCase
import com.example.notetaker.core.util.FileUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.atan2

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

sealed class ImageTouchState {
    object Idle : ImageTouchState()

    data class Selected(
        val imageId: String
    ) : ImageTouchState()

    data class RotationReady(
        val imageId: String,
        val pointer0Id: Long,    // Finger 1's pointer ID
        val pointer1Id: Long     // Finger 2's pointer ID
    ) : ImageTouchState()

    data class Rotating(
        val imageId: String,
        val pointer0Id: Long,    // Finger 1
        val pointer1Id: Long,    // Finger 2 — rotation reference
        val pointer2Id: Long,    // Finger 3 — HUD trigger
        val currentDegrees: Float,
        val baseAngle: Float,    // angle when Finger 3 first landed
        val committedDegrees: Float  // rotation before this gesture started
    ) : ImageTouchState()
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

    private val _touchState = MutableStateFlow<ImageTouchState>(ImageTouchState.Idle)
    val touchState: StateFlow<ImageTouchState> = _touchState.asStateFlow()

    init {
        setupAutoSave()
        observeAuth()
        observeImages()
        observeNote()
        observeConflicts()
    }

    fun onImagePointerEvent(
        imageId: String,
        imageBounds: Rect,          // bounds of the image on screen
        event: PointerEvent,
        currentRotation: Float      // current saved rotation of this image
    ) {
        val activePointers = event.changes.filter { it.pressed }
        val state = _touchState.value

        when {

            // ── Finger 1 lands ──────────────────────────────
            activePointers.size == 1 && state is ImageTouchState.Idle -> {
                _touchState.value = ImageTouchState.Selected(imageId)
            }

            // ── Finger 2 lands ──────────────────────────────
            activePointers.size == 2 && state is ImageTouchState.Selected -> {
                _touchState.value = ImageTouchState.RotationReady(
                    imageId = imageId,
                    pointer0Id = activePointers[0].id.value,
                    pointer1Id = activePointers[1].id.value
                )
            }

            // Finger 3 lands branch — AFTER (safe)
            activePointers.size == 3 && state is ImageTouchState.RotationReady -> {
                val pivot = imageBounds.center

                // Safe lookup — if pointer1 vanished between events, abort
                val pointer1 = activePointers
                    .firstOrNull { it.id.value == state.pointer1Id }
                    ?: run {
                        // Finger 2 already gone — reset to selected
                        _touchState.value = ImageTouchState.Selected(imageId)
                        return
                    }

                // Safe lookup for the new third pointer
                val thirdPointer = activePointers
                    .firstOrNull { it.id.value != state.pointer0Id
                            && it.id.value != state.pointer1Id }
                    ?: run {
                        // Cannot identify third finger — stay in RotationReady
                        return
                    }

                val baseAngle = angleBetween(pivot, pointer1.position)

                _touchState.value = ImageTouchState.Rotating(
                    imageId = imageId,
                    pointer0Id = state.pointer0Id,
                    pointer1Id = state.pointer1Id,
                    pointer2Id = thirdPointer.id.value,
                    currentDegrees = currentRotation,
                    baseAngle = baseAngle,
                    committedDegrees = currentRotation
                )
            }

            // Rotating update branch — BEFORE (can also crash)
            activePointers.size == 3 && state is ImageTouchState.Rotating -> {
                val pivot = imageBounds.center
                val pointer1 = activePointers
                    .firstOrNull { it.id.value == state.pointer1Id }
                    ?: return   // ← was already firstOrNull but double check

                val currentAngle = angleBetween(pivot, pointer1.position)
                val delta = currentAngle - state.baseAngle
                val newDegrees = state.committedDegrees + delta

                _touchState.value = state.copy(currentDegrees = newDegrees)
            }

            // ── A finger lifted while rotating ──────────────
            activePointers.size < 3 && state is ImageTouchState.Rotating -> {
                // Persist final rotation — ONLY written here, never during gesture
                persistRotation(state.imageId, state.currentDegrees)

                _touchState.value = if (activePointers.isEmpty()) {
                    ImageTouchState.Idle
                } else {
                    ImageTouchState.Selected(state.imageId)
                }
            }

            // ── All fingers lifted from Selected ────────────
            activePointers.isEmpty() && state is ImageTouchState.Selected -> {
                _touchState.value = ImageTouchState.Idle
            }
        }
    }

    private fun persistRotation(imageId: String, degrees: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            //noteImageRepository.updateRotation(imageId, degrees)
            // Room write → triggers Firestore sync via syncStatus = PENDING
            updateNoteImageRotationUseCase(
                UpdateRotationParams(imageId, degrees)
            )
        }
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

    private fun angleBetween(pivot: Offset, point: Offset): Float {
        return Math.toDegrees(
            atan2(
                (point.y - pivot.y).toDouble(),
                (point.x - pivot.x).toDouble()
            )
        ).toFloat()
    }

    private fun observeConflicts() {
        observeConflictsForNoteUseCase(noteId)
            .onEach { result ->
                when (result) {
                    is Result.Success -> {
                        _uiState.update { it.copy(hasConflict = result.data.isNotEmpty()) }
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
                    it.copy(
                        interactionState = ImageInteractionState.Rotating(
                            event.imageId,
                            event.initialDegrees,
                            true
                        )
                    )
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
