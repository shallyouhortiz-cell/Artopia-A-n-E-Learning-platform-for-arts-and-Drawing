package com.example.artopiacontrol.data.repository

import com.google.firebase.firestore.Query

interface AuditLogRepository {
    fun getAdminLogs(): Query
}
