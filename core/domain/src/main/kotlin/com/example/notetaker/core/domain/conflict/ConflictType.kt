package com.example.notetaker.core.domain.conflict

enum class ConflictType {
    REMOTE_ADVANCED,     // Case 1: Safe to apply remote
    LOCAL_ADVANCED,      // Case 2: Safe, keep local
    CLEAN_FAST_FORWARD,  // Case 3: Merge silently or snackbar
    TRUE_CONFLICT        // Case 4: Needs manual resolution
}
