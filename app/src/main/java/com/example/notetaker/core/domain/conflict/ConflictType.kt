package com.example.notetaker.core.domain.conflict

enum class ConflictType {
    REMOTE_ADVANCED,     // L == 0 AND R > LR
    LOCAL_ADVANCED,      // L > 0 AND R == LR
    CLEAN_FAST_FORWARD,  // L > 0 AND R == LR + 1
    TRUE_CONFLICT,       // L > 0 AND R > LR + 1
    STALE,               // R < LR
    NO_CHANGE            // R == LR AND L == 0
}
