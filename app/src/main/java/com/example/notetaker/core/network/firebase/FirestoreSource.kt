package com.example.notetaker.core.network.firebase

import android.util.Log
import com.example.notetaker.core.data.db.entity.GridElementEntity
import com.example.notetaker.core.data.db.entity.NoteEntity
import com.example.notetaker.core.data.db.entity.NoteImageEntity
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
    fun observeNotes(workspaceId: String): Flow<List<NoteEntity>> = callbackFlow {
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
                    // Firestore's toObjects can directly map to Room entities if field names match exactly.
                    // We assume NoteEntity fields match Firestore document fields for simplicity.
                    val notes = snapshot.toObjects(NoteEntity::class.java)
                    Log.e(TAG, "observeNotes: notes $notes", )
                    trySend(notes)
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun upsertNote(workspaceId: String, note: NoteEntity) {
        firestore.collection("workspaces")
            .document(workspaceId)
            .collection("notes")
            .document(note.id)
            .set(note.toFirestoreMap())
            .await()
    }

    // --- GridElement Firestore Operations ---
    fun observeGridElements(workspaceId: String): Flow<List<GridElementEntity>> = callbackFlow {
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
                    val elements = snapshot.toObjects(GridElementEntity::class.java)
                    trySend(elements)
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun upsertGridElement(workspaceId: String, element: GridElementEntity) {
        // Similar to notes, filter out local-only fields if any.
        firestore.collection("workspaces")
            .document(workspaceId)
            .collection("gridElements")
            .document(element.id)
            .set(element.toFirestoreMap())
            .await()
    }

    // --- NoteImage Firestore Operations ---
    fun observeNoteImages(workspaceId: String, noteId: String): Flow<List<NoteImageEntity>> =
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
                        val images = snapshot.toObjects(NoteImageEntity::class.java)
                        trySend(images)
                    }
                }
            awaitClose { listener.remove() }
        }

    suspend fun upsertNoteImage(workspaceId: String, noteId: String, image: NoteImageEntity) {
        // Filter out local-only fields if any.
        firestore.collection("workspaces")
            .document(workspaceId)
            .collection("notes")
            .document(noteId)
            .collection("noteImages")
            .document(image.id)
            .set(image.toFirestoreMap())
            .await()
    }

    // Note: Firestore rules will need to be configured to allow these operations.
    // For assignment purposes, a broad rule `allow read, write: if request.auth != null;` is sufficient (Section 14.1).
}
