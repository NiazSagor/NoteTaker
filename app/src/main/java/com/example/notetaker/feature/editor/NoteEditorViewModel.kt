package com.example.notetaker.feature.editor

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerInputChange
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
import kotlin.math.abs
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
        val lastAngle: Float,
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

    fun handlePointerEvent(
        imageId: String,
        imageBounds: Rect,
        activePointers: List<PointerInputChange>,
        currentRotation: Float
    ) {
        val state = _touchState.value

        when (activePointers.size) {

            0 -> {
                // All fingers lifted
                if (state is ImageTouchState.Rotating) {
                    persistRotation(state.imageId, state.currentDegrees)
                }
                _touchState.value = ImageTouchState.Idle
            }

            1 -> {
                // Only one finger — select or return to selected
                when (state) {
                    is ImageTouchState.Rotating -> {
                        // Finger lifted mid rotation — persist and step back
                        persistRotation(state.imageId, state.currentDegrees)
                        _touchState.value = ImageTouchState.Selected(imageId)
                    }

                    is ImageTouchState.RotationReady -> {
                        _touchState.value = ImageTouchState.Selected(imageId)
                    }

                    is ImageTouchState.Idle -> {
                        _touchState.value = ImageTouchState.Selected(imageId)
                    }

                    else -> { /* already selected */
                    }
                }
            }

            2 -> {
                // Two fingers — move to RotationReady regardless of previous state
                when (state) {
                    is ImageTouchState.Rotating -> {
                        // Dropped from 3 to 2 fingers — persist and step back
                        persistRotation(state.imageId, state.currentDegrees)
                        _touchState.value = ImageTouchState.RotationReady(
                            imageId = imageId,
                            pointer0Id = activePointers[0].id.value,
                            pointer1Id = activePointers[1].id.value
                        )
                    }

                    else -> {
                        _touchState.value = ImageTouchState.RotationReady(
                            imageId = imageId,
                            pointer0Id = activePointers[0].id.value,
                            pointer1Id = activePointers[1].id.value
                        )
                    }
                }
            }

            // 3 or more fingers — enter or update rotation
            else -> {
                when (state) {

                    // Jump straight into rotating even if we skipped RotationReady
                    // This handles the case where 2-3 fingers land in same event
                    is ImageTouchState.Selected,
                    is ImageTouchState.RotationReady,
                    is ImageTouchState.Idle -> {
                        enterRotatingState(
                            imageId = imageId,
                            imageBounds = imageBounds,
                            activePointers = activePointers,
                            currentRotation = currentRotation
                        )
                    }

                    is ImageTouchState.Rotating -> {
                        if (state.imageId == imageId) {
                            updateRotation(state, imageBounds, activePointers)
                        }
                    }
                }
            }
        }
    }

    private fun updateRotation(
        state: ImageTouchState.Rotating,
        imageBounds: Rect,
        activePointers: List<PointerInputChange>
    ) {
        val pivot = Offset(imageBounds.center.x, imageBounds.center.y)

        // Find Finger 2 by its stored pointer ID
        val pointer1 = activePointers
            .firstOrNull { it.id.value == state.pointer1Id }
            ?: return

        val currentAngle = angleBetween(pivot, pointer1.position)

        // Calculate delta from LAST frame, not from base angle
        var delta = currentAngle - state.lastAngle

        // ── Fix angle wrapping ──────────────────────────────────
        // If delta jumps more than 180° it means we crossed the
        // -180/+180 boundary. Correct it back to a small delta.
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f

        // Only apply rotation if delta is meaningful
        // Filters out micro-jitter from finger placement
        if (abs(delta) < 0.5f) return

        val newDegrees = state.currentDegrees + delta

        _touchState.value = state.copy(
            currentDegrees = newDegrees,
            lastAngle = currentAngle    // update lastAngle for next frame
        )
    }

    private fun enterRotatingState(
        imageId: String,
        imageBounds: Rect,
        activePointers: List<PointerInputChange>,
        currentRotation: Float
    ) {
        val pivot = Offset(imageBounds.center.x, imageBounds.center.y)
        val pointer1 = activePointers[1]   // Finger 2 is rotation reference
        val baseAngle = angleBetween(pivot, pointer1.position)

        _touchState.value = ImageTouchState.Rotating(
            imageId = imageId,
            pointer0Id = activePointers[0].id.value,
            pointer1Id = activePointers[1].id.value,
            pointer2Id = activePointers[2].id.value,
            currentDegrees = currentRotation,
            baseAngle = baseAngle,
            committedDegrees = currentRotation,
            lastAngle = baseAngle
        )
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

    private fun addImage(uriString: String) {
        val userId = uiState.value.userId ?: return
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
