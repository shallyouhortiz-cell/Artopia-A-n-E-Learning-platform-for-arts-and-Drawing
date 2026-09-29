package com.example.artopiacontrol.data.repository

import com.example.artopiacontrol.data.remote.firestore.FirestoreService
import com.example.artopiacontrol.data.remote.functions.AdminFunctionsService
import com.example.artopiacontrol.data.remote.rtdb.RealtimeDbService
import com.example.artopiacontrol.data.result.AppError
import com.example.artopiacontrol.data.result.RepoResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class UserRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val functionsService: AdminFunctionsService,
    private val firestoreService: FirestoreService,
    private val rtdbService: RealtimeDbService
) : UserRepository {

    override fun getAllUsers(): DatabaseReference = rtdbService.usersRef()

    override fun getUser(uid: String): DocumentReference = firestoreService.usersCollection().document(uid)

    override suspend fun createUser(userData: Map<String, Any>): RepoResult<String> {
        return try {
            val response = functionsService.callFunction("createUser", userData)
            val uid = response["uid"] as? String ?: throw Exception("UID not returned from Cloud Function")
            
            functionsService.callFunction("logActivity", mapOf(
                "action" to "CREATE_USER",
                "message" to "Created account for ${userData["email"]}",
                "targetUid" to uid
            ))

            RepoResult.Success(uid)
        } catch (e: FirebaseFunctionsException) {
            val error = when (e.code) {
                FirebaseFunctionsException.Code.PERMISSION_DENIED -> AppError.PermissionDenied(e.message ?: "Unauthorized")
                FirebaseFunctionsException.Code.INVALID_ARGUMENT -> AppError.Validation(e.message ?: "Invalid arguments")
                FirebaseFunctionsException.Code.UNAVAILABLE -> AppError.Network(e)
                else -> AppError.Unknown(e)
            }
            RepoResult.Failure(error)
        } catch (e: Exception) {
            RepoResult.Failure(AppError.Unknown(e))
        }
    }

    override suspend fun updateUserRole(targetUid: String, newRole: String): RepoResult<Unit> {
        return try {
            functionsService.callFunction("updateUserRole", mapOf("uid" to targetUid, "newRole" to newRole))
            
            functionsService.callFunction("logActivity", mapOf(
                "action" to "UPDATE_USER_ROLE",
                "message" to "Updated role to $newRole for user $targetUid",
                "targetUid" to targetUid
            ))

            RepoResult.Success(Unit)
        } catch (e: FirebaseFunctionsException) {
            val error = when (e.code) {
                FirebaseFunctionsException.Code.PERMISSION_DENIED -> AppError.PermissionDenied(e.message ?: "Unauthorized")
                FirebaseFunctionsException.Code.INVALID_ARGUMENT -> AppError.Validation(e.message ?: "Invalid role")
                FirebaseFunctionsException.Code.UNAVAILABLE -> AppError.Network(e)
                else -> AppError.Unknown(e)
            }
            RepoResult.Failure(error)
        } catch (e: Exception) {
            RepoResult.Failure(AppError.Unknown(e))
        }
    }

    override suspend fun toggleUserStatus(targetUid: String, status: String): RepoResult<Unit> {
        return try {
            functionsService.callFunction("toggleUserStatus", mapOf("uid" to targetUid, "status" to status))
            
            functionsService.callFunction("logActivity", mapOf(
                "action" to "TOGGLE_USER_STATUS",
                "message" to "Changed status to $status for user $targetUid",
                "targetUid" to targetUid
            ))

            RepoResult.Success(Unit)
        } catch (e: FirebaseFunctionsException) {
            val error = when (e.code) {
                FirebaseFunctionsException.Code.PERMISSION_DENIED -> AppError.PermissionDenied(e.message ?: "Unauthorized")
                else -> AppError.Unknown(e)
            }
            RepoResult.Failure(error)
        } catch (e: Exception) {
            RepoResult.Failure(AppError.Unknown(e))
        }
    }

    override suspend fun deleteUser(targetUid: String): RepoResult<Unit> {
        return try {
            functionsService.callFunction("deleteUserAccount", mapOf("uid" to targetUid))
            
            functionsService.callFunction("logActivity", mapOf(
                "action" to "DELETE_USER",
                "message" to "Deleted user account $targetUid",
                "targetUid" to targetUid
            ))

            RepoResult.Success(Unit)
        } catch (e: FirebaseFunctionsException) {
            val error = when (e.code) {
                FirebaseFunctionsException.Code.PERMISSION_DENIED -> AppError.PermissionDenied(e.message ?: "Unauthorized")
                else -> AppError.Unknown(e)
            }
            RepoResult.Failure(error)
        } catch (e: Exception) {
            RepoResult.Failure(AppError.Unknown(e))
        }
    }

    override suspend fun resetPassword(email: String): RepoResult<Unit> {
        return try {
            functionsService.callFunction("resetUserPassword", mapOf("email" to email))
            RepoResult.Success(Unit)
        } catch (e: Exception) {
            RepoResult.Failure(AppError.Unknown(e))
        }
    }

    override suspend fun updateUserLastLogin(): RepoResult<Unit> {
        return try {
            val user = auth.currentUser ?: return RepoResult.Failure(AppError.PermissionDenied("Not logged in"))
            val docRef = firestoreService.usersCollection().document(user.uid)
            docRef.update(
                mapOf(
                    "lastLogin" to FieldValue.serverTimestamp(),
                    "isVerified" to user.isEmailVerified
                )
            ).await()
            RepoResult.Success(Unit)
        } catch (e: Exception) {
            RepoResult.Failure(AppError.Unknown(e))
        }
    }
}
