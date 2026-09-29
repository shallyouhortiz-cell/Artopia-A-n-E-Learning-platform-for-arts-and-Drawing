package com.example.artopiacontrol.data.remote.rtdb

import com.google.firebase.database.FirebaseDatabase
import javax.inject.Inject

class RealtimeDbService @Inject constructor(
    private val database: FirebaseDatabase
) {
    fun usersRef() = database.getReference("users")
    fun userRef(uid: String) = database.getReference("users/$uid")
    fun infoConnectedRef() = database.getReference(".info/connected")
}
