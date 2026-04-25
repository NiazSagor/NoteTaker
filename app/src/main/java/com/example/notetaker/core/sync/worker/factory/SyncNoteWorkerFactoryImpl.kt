package com.example.notetaker.core.sync.worker.factory

import android.content.Context
import androidx.work.WorkerParameters
import com.example.notetaker.core.data.db.dao.NoteDao
import com.example.notetaker.core.data.sync.SyncNoteWorker
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
//
//class SyncNoteWorkerFactoryImpl @Inject constructor(
//    private val firestore: FirebaseFirestore,
//    private val noteDao: NoteDao
//) : SyncNoteWorkerFactory {
//
//    override fun create(
//        context: Context,
//        params: WorkerParameters
//    ): SyncNoteWorker {
//        return SyncNoteWorker(context, params, firestore, noteDao)
//    }
//}
