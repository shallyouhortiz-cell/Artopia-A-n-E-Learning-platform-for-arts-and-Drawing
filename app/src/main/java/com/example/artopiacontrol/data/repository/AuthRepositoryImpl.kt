package com.example.artopiacontrol.data.repository

import com.example.artopiacontrol.data.remote.firestore.FirestoreService
import com.example.artopiacontrol.data.result.AppError
import com.example.artopiacontrol.data.result.RepoResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestoreService: FirestoreService
) : AuthRepository {

    override fun getCurrentUser(): FirebaseUser? = auth.currentUser

    override suspend fun login(email: String, pass: String): RepoResult<FirebaseUser> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, pass).await()
            val user = result.user ?: return RepoResult.Failure(AppError.Validation("User not found"))
            RepoResult.Success(user)
        } catch (e: Exception) {
            RepoResult.Failure(AppError.Unknown(e))
        }
    }

    override suspend fun logout(): RepoResult<Unit> {
        return try {
            auth.signOut()
            RepoResult.Success(Unit)
        } catch (e: Exception) {
            RepoResult.Failure(AppError.Unknown(e))
        }
    }

    override suspend fun mustChangePassword(): RepoResult<Boolean> {
        return try {
            val user = auth.currentUser ?: return RepoResult.Success(false)
            val doc = firestoreService.getUserDoc(user.uid)
            val mustChange = doc.getBoolean("mustChangePassword") ?: false
            RepoResult.Success(mustChange)
        } catch (e: Exception) {
            RepoResult.Failure(AppError.Unknown(e))
        }
    }

    override suspend fun updatePassword(newPassword: String): RepoResult<Unit> {
        return try {
            val user = auth.currentUser ?: return RepoResult.Failure(AppError.PermissionDenied("Not logged in"))
            user.updatePassword(newPassword).await()
            firestoreService.updateUserDoc(user.uid, mapOf("mustChangePassword" to false))
            RepoResult.Success(Unit)
        } catch (e: Exception) {
            RepoResult.Failure(AppError.Unknown(e))
        }
    }

    override suspend fun sendPasswordResetEmail(email: String): RepoResult<Unit> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            RepoResult.Success(Unit)
        } catch (e: Exception) {
            RepoResult.Failure(AppError.Unknown(e))
        }
    }
}
