package com.example.notetaker.core.data.repository

import com.example.notetaker.core.data.db.dao.ConflictDao
import com.example.notetaker.core.data.db.dao.NoteImageDao
import com.example.notetaker.core.data.db.entity.NoteImageEntity
import com.example.notetaker.core.domain.di.IoDispatcher
import com.example.notetaker.core.domain.repository.NoteImageRepository
import com.example.notetaker.core.network.firebase.FirestoreSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteImageRepositoryImpl @Inject constructor(
    private val noteImageDao: NoteImageDao,
    private val conflictDao: ConflictDao,
    private val firestoreSource: FirestoreSource,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : NoteImageRepository {

    private val workspaceId = "global_workspace" // Assuming a single global workspace

    init {
        // This needs to observe images for all notes or a specific note context.
        // For now, let's assume we observe images for a given noteId contextually.
        // A more global observer might be needed if images can be shared or displayed outside a note context.
        // For simplicity, we'll assume observeNoteImages is called with a noteId, and we need to adapt.
        // Let's create a separate observation flow if needed here, or assume it's handled by higher layers.
        // For now, we won't auto-observe all images here, but rely on specific note observation.
    }

    override fun observeNoteImages(noteId: String): Flow<List<NoteImageEntity>> {
        // Observes local Room data. Remote changes are synced to Room.
        return noteImageDao.observeNoteImages(noteId)
    }

    override suspend fun getNoteImage(id: String): NoteImageEntity? = withContext(ioDispatcher) {
        noteImageDao.getById(id)
    }

    override suspend fun saveNoteImage(image: NoteImageEntity) = withContext(ioDispatcher) {
        // Local save first (optimistic update)
        noteImageDao.upsert(image)
        // Remote save handled by sync mechanism
    }

    override suspend fun saveNoteImages(images: List<NoteImageEntity>) = withContext(ioDispatcher) {
        // Local save first
        noteImageDao.upsertAll(images)
        // Remote save handled by sync mechanism
    }

    override suspend fun softDeleteNoteImage(id: String) = withContext(ioDispatcher) {
        noteImageDao.softDelete(id)
    }

    // --- Remote Synchronization Logic ---
    // This is a placeholder and would need to be integrated with FirestoreSource and Conflict logic
    // similar to NoteRepositoryImpl. It's more complex as noteImages are sub-collections.
    // For now, we rely on the local-first save and the sync worker to push changes.
    // Conflict detection for note images might be simpler (e.g., last write wins for rotation).
    // The SKILL.md mentions "Conflict for Non-Text Fields: For `orderIndex`, `rotationDegrees`, `uploadStatus` — use LAST WRITE WINS."
    // This implies TRUE_CONFLICT might not occur for NoteImageEntity itself, but rather the NoteEntity it belongs to.
    // However, if noteImage itself has versions and can diverge, we'd need similar detection.
    // For now, we assume conflicts on NoteImage are rare or handled differently (e.g., via parent Note conflict).
    // If needed, a similar observeRemoteNoteImages method would be implemented here.
}
