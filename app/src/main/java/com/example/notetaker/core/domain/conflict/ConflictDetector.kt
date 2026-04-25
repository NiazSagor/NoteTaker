package com.example.notetaker.core.domain.conflict

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
            R < LR -> ConflictType.STALE
            R == LR -> if (L == 0) ConflictType.NO_CHANGE else ConflictType.LOCAL_ADVANCED
            L == 0 && R > LR -> ConflictType.REMOTE_ADVANCED
            L > 0 && R == LR + 1 -> ConflictType.CLEAN_FAST_FORWARD
            L > 0 && R > LR + 1 -> ConflictType.TRUE_CONFLICT
            else -> ConflictType.STALE 
        }
    }
}
