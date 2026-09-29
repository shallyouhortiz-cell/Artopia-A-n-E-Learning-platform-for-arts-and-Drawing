package com.example.artopiacontrol.data.repository

import com.example.artopiacontrol.data.remote.firestore.FirestoreService
import com.google.firebase.firestore.Query
import javax.inject.Inject

class AuditLogRepositoryImpl @Inject constructor(
    private val firestoreService: FirestoreService
) : AuditLogRepository {

    override fun getAdminLogs(): Query {
        return firestoreService.adminLogsCollection()
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
    }
}
