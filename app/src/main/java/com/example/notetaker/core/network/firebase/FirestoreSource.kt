package com.example.notetaker.core.network.firebase

import android.util.Log
import com.example.notetaker.core.data.db.entity.GridElementEntity
import com.example.notetaker.core.data.db.entity.NoteEntity
import com.example.notetaker.core.data.db.entity.NoteImageEntity
import com.example.notetaker.core.network.firebase.model.GridElementDto
import com.example.notetaker.core.network.firebase.model.NoteDto
import com.example.notetaker.core.network.firebase.model.NoteImageDto
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val TAG = "FirestoreSource"
    // --- Note Firestore Operations ---
    fun observeNotes(workspaceId: String): Flow<List<NoteDto>> = callbackFlow {
        val listener = firestore.collection("workspaces")
            .document(workspaceId)
            .collection("notes")
            .orderBy("updatedAt", Query.Direction.ASCENDING) // Order by update time for consistency
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot?.metadata?.hasPendingWrites() == true) return@addSnapshotListener
                if (snapshot != null) {
                    // Map to DTOs directly from Firestore
                    val notes = snapshot.toObjects(NoteDto::class.java)
                    Log.e(TAG, "observeNotes: notes $notes", )
                    trySend(notes)
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun upsertNote(workspaceId: String, noteDto: NoteDto) {
        firestore.collection("workspaces")
            .document(workspaceId)
            .collection("notes")
            .document(noteDto.id)
            .set(noteDto) // Set the DTO directly
            .await()
    }

    // --- GridElement Firestore Operations ---
    fun observeGridElements(workspaceId: String): Flow<List<GridElementDto>> = callbackFlow {
        val listener = firestore.collection("workspaces")
            .document(workspaceId)
            .collection("gridElements")
            .orderBy("orderIndex", Query.Direction.ASCENDING) // Order by orderIndex
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot?.metadata?.hasPendingWrites() == true) return@addSnapshotListener
                if (snapshot != null) {
                    val elements = snapshot.toObjects(GridElementDto::class.java)
                    trySend(elements)
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun upsertGridElement(workspaceId: String, elementDto: GridElementDto) {
        // Similar to notes, filter out local-only fields if any.
        firestore.collection("workspaces")
            .document(workspaceId)
            .collection("gridElements")
            .document(elementDto.id)
            .set(elementDto) // Set the DTO directly
            .await()
    }

    // --- NoteImage Firestore Operations ---
    fun observeNoteImages(workspaceId: String, noteId: String): Flow<List<NoteImageDto>> =
        callbackFlow {
            val listener = firestore.collection("workspaces")
                .document(workspaceId)
                .collection("notes")
                .document(noteId)
                .collection("noteImages")
                .orderBy("orderInNote", Query.Direction.ASCENDING) // Order within the note
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    if (snapshot?.metadata?.hasPendingWrites() == true) return@addSnapshotListener
                    if (snapshot != null) {
                        val images = snapshot.toObjects(NoteImageDto::class.java)
                        trySend(images)
                    }
                }
            awaitClose { listener.remove() }
        }

    suspend fun upsertNoteImage(workspaceId: String, noteId: String, imageDto: NoteImageDto) {
        // Filter out local-only fields if any.
        firestore.collection("workspaces")
            .document(workspaceId)
            .collection("notes")
            .document(noteId)
            .collection("noteImages")
            .document(imageDto.id)
            .set(imageDto) // Set the DTO directly
            .await()
    }

    // Note: Firestore rules will need to be configured to allow these operations.
    // For assignment purposes, a broad rule `allow read, write: if request.auth != null;` is sufficient (Section 14.1).
}
