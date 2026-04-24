package com.example.notetaker.core.data.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun syncNote(noteId: String) {
        SyncNoteWorker.enqueue(context, noteId)
    }
}