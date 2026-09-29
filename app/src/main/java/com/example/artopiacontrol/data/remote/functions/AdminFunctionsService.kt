package com.example.artopiacontrol.data.remote.functions

import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class AdminFunctionsService @Inject constructor(
    private val functions: FirebaseFunctions
) {
    suspend fun callFunction(name: String, data: Map<String, Any?>): Map<String, Any?> {
        return functions.getHttpsCallable(name)
            .call(data)
            .await()
            .data as? Map<String, Any?> ?: emptyMap()
    }
}
