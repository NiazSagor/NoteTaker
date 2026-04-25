package com.example.notetaker.core.domain.model

enum class SyncStatus {
    SYNCED,     // local matches remote
    PENDING,    // local change not yet pushed to Firestore
    CONFLICT,   // diverged — needs manual resolution
    UPLOADING,  // asset currently being uploaded to Cloudinary
    ERROR       // last sync attempt failed (will retry)
}
