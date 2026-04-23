package com.example.notetaker.core.domain.conflict

/**
 * Detects the type of conflict between local and remote versions.
 *
 * CASE 1 — No local changes, remote advanced:
 *     L == 0 AND R > LR
 *
 * CASE 2 — Local change, remote unchanged:
 *     L > 0 AND R == LR
 *
 * CASE 3 — Clean fast-forward (remote advanced by exactly 1):
 *     L > 0 AND R == LR + 1
 *
 * CASE 4 — TRUE CONFLICT (both sides diverged):
 *     L > 0 AND R > LR + 1
 */
object ConflictDetector {
    fun detect(
        localVersion: Int,
        remoteVersionAtLocal: Int,
        incomingRemoteVersion: Int
    ): ConflictType {
        val L = localVersion
        val LR = remoteVersionAtLocal
        val R = incomingRemoteVersion

        return when {
            L == 0 && R > LR -> ConflictType.REMOTE_ADVANCED
            L > 0 && R == LR -> ConflictType.LOCAL_ADVANCED
            L > 0 && R == LR + 1 -> ConflictType.CLEAN_FAST_FORWARD
            L > 0 && R > LR + 1 -> ConflictType.TRUE_CONFLICT
            else -> ConflictType.REMOTE_ADVANCED // Default fallback
        }
    }
}
