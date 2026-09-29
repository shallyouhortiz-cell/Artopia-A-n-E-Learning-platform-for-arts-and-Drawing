package com.example.artopiacontrol.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class User(
    val uid: String = "",
    val email: String = "",
    val nickname: String = "",
    val age: Int = 0,
    val role: String = "student",
    val status: String = "active",
    val presence: String = "offline",
    val gradeLevel: String = "",
    val section: String = "",
    val isVerified: Boolean = false,
    val mustChangePassword: Boolean = false,
    val profileImageUrl: String? = null,
    val createdAt: Timestamp? = null,
    val lastLogin: Timestamp? = null,
    val lastSeen: Timestamp? = null
)

data class Artwork(
    @DocumentId val artworkId: String = "",
    val studentUid: String = "",
    val studentName: String = "",
    val title: String = "",
    val imageUrl: String = "",
    val gradeLevel: String = "",
    val section: String = "",
    val status: String = "pending", // "pending", "evaluated", "approved"
    val score: Int = 0,
    val feedback: String = "",
    val evaluatorUid: String? = null,
    val evaluatorName: String? = null,
    val timestamp: Timestamp? = null
)

data class Evaluation(
    @DocumentId val evaluationId: String = "",
    val artworkId: String = "",
    val studentUid: String = "",
    val section: String = "",
    val gradeLevel: String = "",
    val teacherUid: String = "",
    val teacherName: String = "",
    val score: Int = 0,
    val feedback: String = "",
    val timestamp: Timestamp? = null
)

data class AdminLog(
    @DocumentId val logId: String = "",
    val action: String = "",
    val message: String = "",
    val actorEmail: String? = "",
    val actorUid: String = "",
    val targetUid: String = "",
    val timestamp: Timestamp? = null,
    val source: String = "",
    val details: Map<String, Any>? = null
)
