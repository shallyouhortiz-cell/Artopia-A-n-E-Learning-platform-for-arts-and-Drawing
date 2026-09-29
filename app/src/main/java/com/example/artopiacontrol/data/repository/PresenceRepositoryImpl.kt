package com.example.artopiacontrol.data.repository

import android.util.Log
import com.example.artopiacontrol.data.remote.rtdb.RealtimeDbService
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import javax.inject.Inject

class PresenceRepositoryImpl @Inject constructor(
    private val rtdbService: RealtimeDbService
) : PresenceRepository {

    override fun setUserPresence(uid: String) {
        val presenceRef = rtdbService.userRef(uid).child("presence")
        val connectedRef = rtdbService.infoConnectedRef()

        connectedRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected) {
                    presenceRef.onDisconnect().setValue("offline")
                    presenceRef.setValue("online")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w("PresenceRepository", "Presence listener cancelled", error.toException())
            }
        })
    }
}
