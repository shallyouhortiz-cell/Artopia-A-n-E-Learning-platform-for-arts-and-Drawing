package com.example.artopiacontrol.data.repository

import com.example.artopiacontrol.data.result.RepoResult
import com.google.firebase.database.DatabaseReference
import com.google.firebase.firestore.DocumentReference

interface UserRepository {
    fun getAllUsers(): DatabaseReference
    fun getUser(uid: String): DocumentReference
    suspend fun createUser(userData: Map<String, Any>): RepoResult<String>
    suspend fun updateUserRole(targetUid: String, newRole: String): RepoResult<Unit>
    suspend fun toggleUserStatus(targetUid: String, status: String): RepoResult<Unit>
    suspend fun deleteUser(targetUid: String): RepoResult<Unit>
    suspend fun resetPassword(email: String): RepoResult<Unit>
    suspend fun updateUserLastLogin(): RepoResult<Unit>
}
