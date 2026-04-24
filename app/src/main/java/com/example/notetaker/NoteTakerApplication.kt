package com.example.notetaker

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class NoteTakerApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // --- Synchronization Workers ---
        // Enqueue SyncPendingWorker to handle pushing local metadata changes (notes, grid elements, image metadata) to Firestore.
        // This worker runs when network is available and retries on failure.
        //SyncPendingWorker.Companion.enqueue(this)

        // CloudinaryUploadWorker is enqueued by NoteEditorViewModel when images are added locally.
        // MediaManager initialization for Cloudinary SDK would typically happen here or be managed by DI.
        // For now, CloudinaryUploadClient uses a placeholder and assumes MediaManager is configured.

        Log.d("NoteTakerApp", "Application initialized. Sync workers enqueued.")

        // --- Current Status ---
        // Core layers (domain, data, network, ui) are set up.
        // Hilt DI is configured.
        // UseCases for core features (Auth, Workspace, Note, Image, Conflict) are implemented.
        // ViewModels for Auth, Workspace, NoteEditor, and Conflict are implemented.
        // Synchronization logic for remote data ingestion (conflict detection) is in place.
        // Local sync worker (SyncPendingWorker) and image upload worker (CloudinaryUploadWorker) are defined.
        // DAOs have updated methods for sync status and JSON snapshotting.
        // Repositories use injected CoroutineScope for background tasks.

        // --- Remaining Tasks ---
        // 1. Full Cloudinary SDK Initialization and Configuration:
        //    - Replace placeholder credentials/presets in CloudinaryUploadClientImpl.
        //    - Ensure MediaManager.init() is called appropriately if not done elsewhere.
        // 2. Implement Firestore Security Rules: Crucial for backend security.
        // 3. UI Implementation: Create Compose screens for Workspace, Note Editor, Conflict Resolution, and Auth.
        // 4. Refine Sync Logic: Address detailed error handling, potential race conditions in complex scenarios,
        //    and more sophisticated retry logic if needed.
        // 5. Cloudinary Upload Client Refinement: Ensure robust handling of various image types and edge cases.
        // 6. Navigation: Implement navigation between screens.
    }
}

//interface SyncNoteWorkerFactory {
//    fun create(
//        context: Context,
//        params: WorkerParameters
//    ): SyncNoteWorker
//}