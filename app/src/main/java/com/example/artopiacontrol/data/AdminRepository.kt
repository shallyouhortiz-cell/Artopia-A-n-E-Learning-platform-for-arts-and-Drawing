package com.example.artopiacontrol.data

import android.util.Log
import com.example.artopiacontrol.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdminRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val database: FirebaseDatabase,
    private val functions: FirebaseFunctions
) {
    companion object {
        private const val TAG = "AdminRepository"
        private var emulatorsInitialized = false
    }

    init {
        if (BuildConfig.DEBUG && !emulatorsInitialized) {
            try {
                emulatorsInitialized = true
            } catch (e: Exception) {
                Log.e(TAG, "Error configuring Firebase Emulators", e)
            }
        }
    }

    fun getCurrentUser() = auth.currentUser

    suspend fun getUserRole(): String? {
        val user = auth.currentUser ?: return null
        return try {
            val tokenResult = user.getIdToken(false).await()
            val claimRole = tokenResult.claims["role"] as? String
            if (!claimRole.isNullOrEmpty()) {
                return claimRole
            }

            // Fallback 1: Private Meta Doc in Firestore
            try {
                val metaDoc = firestore.collection("users")
                    .document(user.uid)
                    .collection("private")
                    .document("meta")
                    .get()
                    .await()
                val metaRole = metaDoc.getString("role")
                if (!metaRole.isNullOrEmpty()) {
                    return metaRole
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read meta doc for role", e)
            }

            // Fallback 2: Main User Doc in Firestore
            try {
                val userDoc = firestore.collection("users")
                    .document(user.uid)
                    .get()
                    .await()
                val userRole = userDoc.getString("role")
                if (!userRole.isNullOrEmpty()) {
                    return userRole
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read user doc for role", e)
            }

            // Fallback 3: Authenticated control panel user defaults to admin role
            "admin"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve user role, defaulting to admin for authenticated user", e)
            "admin"
        }
    }

    suspend fun isAdmin(): Boolean {
        return getUserRole() == "admin"
    }

    suspend fun canAccessControlPanel(): Boolean {
        val role = getUserRole()
        return role == "admin" || role == "teacher"
    }

    suspend fun mustChangePassword(): Boolean {
        val user = auth.currentUser ?: return false
        return try {
            val doc = firestore.collection("users")
                .document(user.uid)
                .collection("private")
                .document("meta")
                .get()
                .await()
            doc.getBoolean("mustChangePassword") ?: false
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read mustChangePassword state, defaulting to false", e)
            false
        }
    }

    suspend fun isAccountActive(): Boolean {
        val user = auth.currentUser ?: return false
        return try {
            val doc = firestore.collection("users")
                .document(user.uid)
                .collection("private")
                .document("meta")
                .get()
                .await()
            val status = doc.getString("status")
            if (status != null) {
                return status == "active"
            }

            val userDoc = firestore.collection("users")
                .document(user.uid)
                .get()
                .await()
            val userStatus = userDoc.getString("status") ?: "active"
            userStatus == "active"
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check account status, defaulting to active for signed in user", e)
            true
        }
    }

    fun getAllUsers() = database.getReference("users")

    /**
     * Scoped User Query Helper.
     * Note: Firestore security rules enforce that teachers can ONLY read documents where role == 'student'.
     * Therefore, issuing an unfiltered query as a teacher will be rejected by Firestore Security Rules.
     * Adding `.whereEqualTo("role", "student")` for teachers is mandatory for query authorization.
     */
    suspend fun ScopedUsersQuery(): Query {
        val role = getUserRole()
        return if (role == "teacher") {
            firestore.collectionGroup("private").whereEqualTo("role", "student")
        } else {
            firestore.collection("users")
        }
    }

    fun getUser(uid: String): DocumentReference = firestore.collection("users").document(uid)

    fun getUserMeta(uid: String): DocumentReference =
        firestore.collection("users").document(uid).collection("private").document("meta")

    fun setUserPresence(uid: String) {
        val presenceRef = database.getReference("users/$uid/presence")
        val connectedRef = database.getReference(".info/connected")

        connectedRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected) {
                    presenceRef.onDisconnect().setValue("offline")
                    presenceRef.setValue("online")
                }
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                Log.w(TAG, "Presence listener cancelled", error.toException())
            }
        })
    }

    suspend fun clearPresence(uid: String) {
        try {
            database.getReference("users/$uid/presence").setValue("offline").await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear presence for $uid", e)
        }
    }

    fun getAdminLogs(): Query {
        return firestore.collection("adminLogs")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
    }

    suspend fun createUser(
        email: String,
        password: String? = null,
        nickname: String,
        role: String,
        gradeLevel: String = "",
        section: String = "",
        age: Int? = null
    ) {
        val otpPassword = password?.takeIf { it.isNotEmpty() } ?: generateRandomPassword()

        val payload = mutableMapOf<String, Any>(
            "email" to email,
            "password" to otpPassword,
            "nickname" to nickname,
            "role" to role,
            "gradeLevel" to gradeLevel,
            "section" to section
        )
        age?.let { payload["age"] = it }

        try {
            functions.getHttpsCallable("createUser").call(payload).await()
        } catch (e: Exception) {
            if (isNotFoundError(e)) {
                try {
                    FirebaseFunctions.getInstance("us-central1")
                        .getHttpsCallable("createUser")
                        .call(payload)
                        .await()
                } catch (e2: Exception) {
                    Log.w(TAG, "us-central1 function createUser also failed, falling back to direct Auth/Firestore creation", e2)
                    fallbackCreateUser(email, otpPassword, nickname, role, gradeLevel, section, age)
                }
            } else {
                fallbackCreateUser(email, otpPassword, nickname, role, gradeLevel, section, age)
            }
        }

        // Write to 'mail' collection to trigger 'Trigger Email from Firestore' extension
        sendTeacherInviteEmail(email, otpPassword, nickname, role)
    }

    private suspend fun fallbackCreateUser(
        email: String,
        password: String,
        nickname: String,
        role: String,
        gradeLevel: String = "",
        section: String = "",
        age: Int? = null
    ): com.google.firebase.auth.FirebaseUser? {
        return try {
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val user = authResult.user

            if (user != null) {
                val userDoc = mutableMapOf<String, Any>(
                    "email" to email,
                    "nickname" to nickname,
                    "uid" to user.uid,
                    "role" to role,
                    "status" to "active",
                    "gradeLevel" to gradeLevel,
                    "section" to section
                )
                age?.let { userDoc["age"] = it }
                firestore.collection("users").document(user.uid).set(userDoc).await()

                val metaDoc = mapOf(
                    "role" to role,
                    "status" to "active",
                    "gradeLevel" to gradeLevel,
                    "section" to section,
                    "isVerified" to false,
                    "mustChangePassword" to true,
                    "createdAt" to com.google.firebase.Timestamp.now()
                )
                firestore.collection("users").document(user.uid).collection("private").document("meta").set(metaDoc).await()

                // 3. Create Realtime Database record for live user list
                val rtdbRecord = mapOf(
                    "email" to email,
                    "nickname" to nickname,
                    "role" to role,
                    "status" to "active",
                    "presence" to "offline",
                    "gradeLevel" to gradeLevel,
                    "section" to section,
                    "isVerified" to false
                )
                try {
                    database.getReference("users/${user.uid}").setValue(rtdbRecord).await()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to write RTDB record for $email", e)
                }
            }
            user
        } catch (e: Exception) {
            Log.w(TAG, "fallbackCreateUser exception", e)
            null
        }
    }

    private suspend fun sendTeacherInviteEmail(
        email: String,
        otpPassword: String,
        nickname: String,
        role: String
    ) {
        val roleTitle = role.replaceFirstChar { it.uppercase() }
        val portalUrl = "https://artopia-54e07.firebaseapp.com"

        val mailPayload = mapOf(
            "to" to listOf(email),
            "message" to mapOf(
                "subject" to "Welcome to Artopia - Your $roleTitle Account Credentials",
                "text" to """
                    Hello $nickname,

                    An account has been created for you on the Artopia Control Panel with the role of $roleTitle.

                    ACCOUNT CREDENTIALS:
                    Email: $email
                    One-Time Password: $otpPassword

                    Sign in to activate your account:
                    $portalUrl
                """.trimIndent(),
                "html" to """
                    <div style="font-family: Arial, sans-serif; padding: 24px; background-color: #F5F7FB; border-radius: 12px; max-width: 600px; margin: 0 auto; border: 1px solid #E6E9F0;">
                        <h2 style="color: #3E5C9A; margin-bottom: 16px;">Welcome to Artopia</h2>
                        <p style="color: #1C2025; font-size: 16px; line-height: 1.5;">Hello <strong>$nickname</strong>,</p>
                        <p style="color: #5C6573; font-size: 14px; line-height: 1.5;">An account has been created for you on the Artopia Control Panel with the role of <strong>$roleTitle</strong>.</p>
                        
                        <div style="background-color: #FFFFFF; border: 1px solid #E6E9F0; padding: 20px; border-radius: 8px; margin: 20px 0;">
                            <p style="margin: 0 0 12px 0; color: #5C6573; font-size: 12px; font-weight: bold; letter-spacing: 0.5px; text-transform: uppercase;">ACCOUNT CREDENTIALS</p>
                            <p style="margin: 0 0 8px 0; color: #1C2025; font-size: 14px;"><strong>Email:</strong> $email</p>
                            <p style="margin: 0; color: #1C2025; font-size: 14px;"><strong>One-Time Password:</strong> <code style="background-color: #F5F7FB; padding: 4px 8px; border-radius: 4px; font-weight: bold; color: #3E5C9A;">$otpPassword</code></p>
                        </div>

                        <div style="margin: 28px 0; text-align: center;">
                            <a href="$portalUrl" style="background-color: #3E5C9A; color: #ffffff; padding: 14px 28px; text-decoration: none; border-radius: 8px; font-weight: bold; display: inline-block; font-size: 15px;">Activate & Sign In to Artopia</a>
                        </div>

                        <p style="color: #5C6573; font-size: 13px; line-height: 1.5;">Sign in using your credentials above. You will be prompted to set your personal password upon initial login.</p>
                    </div>
                """.trimIndent()
            )
        )

        try {
            firestore.collection("mail").add(mailPayload).await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write to mail collection for $roleTitle invite email", e)
        }
    }

    suspend fun registerAdmin(userData: Map<String, String>) {
        val email = userData["email"] ?: ""
        val password = userData["password"] ?: ""
        val nickname = userData["nickname"] ?: email.split("@").firstOrNull() ?: "Admin"

        try {
            functions.getHttpsCallable("registerAdmin").call(userData).await()
        } catch (e: Exception) {
            if (isNotFoundError(e)) {
                try {
                    FirebaseFunctions.getInstance("us-central1")
                        .getHttpsCallable("registerAdmin")
                        .call(userData)
                        .await()
                    return
                } catch (e2: Exception) {
                    Log.w(TAG, "us-central1 function registerAdmin also failed, falling back to client-side Auth", e2)
                }

                fallbackRegisterAdmin(email, password, nickname)
            } else {
                throw e
            }
        }
    }

    private suspend fun fallbackRegisterAdmin(email: String, password: String, nickname: String) {
        val authResult = auth.createUserWithEmailAndPassword(email, password).await()
        val user = authResult.user ?: throw Exception("Failed to create user account.")
        val portalUrl = "https://artopia-54e07.firebaseapp.com"

        // 1. Create main user document with 'admin' role
        val userDoc = mapOf(
            "nickname" to nickname,
            "email" to email,
            "uid" to user.uid,
            "role" to "admin",
            "status" to "active"
        )
        firestore.collection("users").document(user.uid).set(userDoc).await()

        // 2. Create private metadata document with 'admin' role
        val metaDoc = mapOf(
            "role" to "admin",
            "status" to "active",
            "isVerified" to false,
            "createdAt" to com.google.firebase.Timestamp.now()
        )
        firestore.collection("users").document(user.uid).collection("private").document("meta").set(metaDoc).await()

        // 3. Create Realtime Database record for live user list
        val rtdbRecord = mapOf(
            "email" to email,
            "nickname" to nickname,
            "role" to "admin",
            "status" to "active",
            "presence" to "offline",
            "isVerified" to false
        )
        try {
            database.getReference("users/${user.uid}").setValue(rtdbRecord).await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write RTDB record for admin $email", e)
        }

        // 3. Write document to 'mail' collection for Trigger Email extension notification
        val mailPayload = mapOf(
            "to" to listOf(email),
            "message" to mapOf(
                "subject" to "Verify Your Artopia Admin Account",
                "text" to "Hello $nickname,\n\nWelcome to Artopia! Sign in to activate your account:\n$portalUrl",
                "html" to """
                    <div style="font-family: Arial, sans-serif; padding: 24px; background-color: #F5F7FB; border-radius: 12px; max-width: 600px; margin: 0 auto; border: 1px solid #E6E9F0;">
                        <h2 style="color: #3E5C9A; margin-bottom: 16px;">Verify Your Artopia Admin Account</h2>
                        <p style="color: #1C2025; font-size: 16px; line-height: 1.5;">Hello <strong>$nickname</strong>,</p>
                        <p style="color: #5C6573; font-size: 14px; line-height: 1.5;">Thank you for registering. Please click the button below to sign in and activate your account:</p>
                        <div style="margin: 28px 0; text-align: center;">
                            <a href="$portalUrl" style="background-color: #3E5C9A; color: #ffffff; padding: 14px 28px; text-decoration: none; border-radius: 8px; font-weight: bold; display: inline-block; font-size: 15px;">Activate Account & Sign In</a>
                        </div>
                    </div>
                """.trimIndent()
            )
        )
        try {
            firestore.collection("mail").add(mailPayload).await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write to mail collection for Firestore Trigger Email extension", e)
        }

        // 4. Send official Firebase Auth Email Verification Link
        try {
            user.sendEmailVerification().await()
        } catch (e: Exception) {
            Log.w(TAG, "Native sendEmailVerification exception", e)
        }
        
        // 5. Sign out until user verifies email
        auth.signOut()
    }

    private fun generateRandomPassword(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$"
        return (1..12).map { chars.random() }.joinToString("")
    }

    private fun isNotFoundError(e: Exception): Boolean {
        if (e is FirebaseFunctionsException && e.code == FirebaseFunctionsException.Code.NOT_FOUND) {
            return true
        }
        return e.message?.contains("NOT_FOUND", ignoreCase = true) == true
    }

    suspend fun updateUserRole(uid: String, newRole: String) {
        functions.getHttpsCallable("updateUserRole").call(
            mapOf("uid" to uid, "newRole" to newRole)
        ).await()
    }

    suspend fun toggleUserStatus(uid: String, status: String) {
        functions.getHttpsCallable("toggleUserStatus").call(
            mapOf("uid" to uid, "status" to status)
        ).await()
    }

    suspend fun updateUserProfile(
        uid: String,
        nickname: String? = null,
        age: Int? = null,
        gradeLevel: String? = null,
        section: String? = null,
        profileImageUrl: String? = null
    ) {
        val payload = mutableMapOf<String, Any>("uid" to uid)
        nickname?.let { payload["nickname"] = it }
        age?.let { payload["age"] = it }
        gradeLevel?.let { payload["gradeLevel"] = it }
        section?.let { payload["section"] = it }
        profileImageUrl?.let { payload["profileImageUrl"] = it }

        try {
            functions.getHttpsCallable("updateUserProfile").call(payload).await()
        } catch (e: Exception) {
            try {
                firestore.collection("users").document(uid).update(payload as Map<String, Any>).await()
                val metaUpdate = mutableMapOf<String, Any>()
                gradeLevel?.let { metaUpdate["gradeLevel"] = it }
                section?.let { metaUpdate["section"] = it }
                if (metaUpdate.isNotEmpty()) {
                    firestore.collection("users").document(uid)
                        .collection("private").document("meta")
                        .update(metaUpdate).await()
                }
            } catch (e2: Exception) {
                Log.w(TAG, "Failed to update profile directly", e2)
            }
        }
    }

    suspend fun getTeacherAssignedSection(): Pair<String, String> {
        val user = auth.currentUser ?: return Pair("", "")
        return try {
            val doc = firestore.collection("users").document(user.uid).get().await()
            val grade = doc.getString("gradeLevel") ?: ""
            val section = doc.getString("section") ?: ""
            Pair(grade, section)
        } catch (e: Exception) {
            Pair("", "")
        }
    }

    suspend fun getSectionArtworks(): List<Artwork> {
        val user = auth.currentUser ?: return emptyList()
        val role = getUserRole()
        return try {
            if (role == "admin") {
                val snapshot = firestore.collection("artworks")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .get()
                    .await()
                snapshot.toObjects(Artwork::class.java)
            } else {
                val (_, assignedSection) = getTeacherAssignedSection()
                if (assignedSection.isEmpty()) return emptyList()

                val snapshot = firestore.collection("artworks")
                    .whereEqualTo("section", assignedSection)
                    .get()
                    .await()
                snapshot.toObjects(Artwork::class.java)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch section artworks", e)
            emptyList()
        }
    }

    suspend fun evaluateStudentArtwork(
        artworkId: String,
        studentUid: String,
        score: Int,
        feedback: String
    ) {
        val user = auth.currentUser ?: throw Exception("User not authenticated.")
        val teacherName = user.displayName ?: user.email ?: "Teacher"
        val (grade, section) = getTeacherAssignedSection()

        val updatePayload = mapOf(
            "status" to "evaluated",
            "score" to score,
            "feedback" to feedback,
            "evaluatorUid" to user.uid,
            "evaluatorName" to teacherName,
            "timestamp" to com.google.firebase.Timestamp.now()
        )

        firestore.collection("artworks").document(artworkId).update(updatePayload).await()

        val evaluation = mapOf(
            "artworkId" to artworkId,
            "studentUid" to studentUid,
            "section" to section,
            "gradeLevel" to grade,
            "teacherUid" to user.uid,
            "teacherName" to teacherName,
            "score" to score,
            "feedback" to feedback,
            "timestamp" to com.google.firebase.Timestamp.now()
        )
        firestore.collection("evaluations").add(evaluation).await()
    }

    suspend fun deleteUser(uid: String) {
        functions.getHttpsCallable("deleteUserAccount").call(mapOf("uid" to uid)).await()
    }

    suspend fun syncLoginState() {
        auth.currentUser?.uid?.let { uid ->
            markUserVerified(uid)
        }
        try {
            functions.getHttpsCallable("syncLoginState").call().await()
        } catch (e: Exception) {
            Log.w(TAG, "syncLoginState failed or function NOT_FOUND, continuing...", e)
        }
    }

    suspend fun markUserVerified(uid: String) {
        try {
            firestore.collection("users").document(uid)
                .collection("private").document("meta")
                .update("isVerified", true)
                .await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update Firestore isVerified", e)
        }

        try {
            database.getReference("users/$uid/isVerified").setValue(true).await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update RTDB isVerified", e)
        }
    }

    suspend fun completePasswordChange() {
        try {
            functions.getHttpsCallable("completePasswordChange").call().await()
        } catch (e: Exception) {
            Log.w(TAG, "completePasswordChange failed or function NOT_FOUND, continuing...", e)
        }
    }

    suspend fun resetPasswordClientSide(email: String) {
        auth.sendPasswordResetEmail(email).await()
    }

    suspend fun logActivity(action: String, message: String, targetUid: String = "", details: Map<String, Any>? = null) {
        try {
            val logData = mutableMapOf<String, Any>(
                "action" to action,
                "message" to message,
                "targetUid" to targetUid,
                "source" to "android-app"
            )
            details?.let { logData["details"] = it }
            functions.getHttpsCallable("logActivity").call(logData).await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record log: $action", e)
        }
    }

    suspend fun logout() {
        auth.currentUser?.uid?.let { clearPresence(it) }
        auth.signOut()
    }
}
