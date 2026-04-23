package com.example.notetaker.core.network.firebase

import com.example.notetaker.core.data.db.entity.NoteEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SnapshotListenOptions
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
    fun observeNotes(workspaceId: String): Flow<List<NoteEntity>> = callbackFlow {
        val listener = firestore.collection("workspaces")
            .document(workspaceId)
            .collection("notes")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val notes = snapshot.toObjects(NoteEntity::class.java)
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
            .set(note)
            .await()
    }

    // Similar methods for GridElements and NoteImages...
}
