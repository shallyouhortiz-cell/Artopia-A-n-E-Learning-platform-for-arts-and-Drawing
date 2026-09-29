package com.example.artopiacontrol.data.repository

import com.example.artopiacontrol.data.result.RepoResult
import com.google.firebase.auth.FirebaseUser

interface AuthRepository {
    fun getCurrentUser(): FirebaseUser?
    suspend fun login(email: String, pass: String): RepoResult<FirebaseUser>
    suspend fun logout(): RepoResult<Unit>
    suspend fun mustChangePassword(): RepoResult<Boolean>
    suspend fun updatePassword(newPassword: String): RepoResult<Unit>
    suspend fun sendPasswordResetEmail(email: String): RepoResult<Unit>
}
