package com.example.artopiacontrol.data.remote.firestore

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class FirestoreService @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    fun usersCollection() = firestore.collection("users")
    fun adminLogsCollection() = firestore.collection("adminLogs")
    fun mailCollection() = firestore.collection("mail")

    suspend fun getUserDoc(uid: String) = usersCollection().document(uid).get().await()
    suspend fun updateUserDoc(uid: String, data: Map<String, Any>) = usersCollection().document(uid).update(data).await()
    suspend fun setUserDoc(uid: String, data: Map<String, Any>) = usersCollection().document(uid).set(data).await()
    suspend fun deleteUserDoc(uid: String) = usersCollection().document(uid).delete().await()
}
