# ArtopiaControl Architecture & Design

## 1. Repository Segregation (Interface Segregation Principle)
`AdminRepository` has been refactored into domain-specific repositories:
- `AuthRepository`: Handles sign-in, sign-out, session restore, password reset.
- `UserRepository`: Handles user creation, role/status updates (delegating to Cloud Functions).
- `PresenceRepository`: Manages Realtime Database presence and `onDisconnect()`.
- `AuditLogRepository`: Read-only queries for audit logs.

## 2. Sealed Result Wrapper (`RepoResult`)
All repository operations wrap outcomes in a structured sealed type:
```kotlin
sealed class RepoResult<out T> {
    data class Success<T>(val data: T) : RepoResult<T>()
    data class Failure(val error: AppError) : RepoResult<Nothing>()
}

sealed class AppError {
    data class NetworkError(val message: String?) : AppError()
    data class PermissionDenied(val message: String?) : AppError()
    data class ValidationError(val message: String?) : AppError()
    data class Unknown(val throwable: Throwable?) : AppError()
}
```

## 3. Caching & Offline Policy
- **Firestore**: Offline persistence enabled for read queries, but security-sensitive checks (role validation, auth tokens) require fresh network tokens (`getIdToken(true)`).
- **RTDB**: `keepSynced(true)` enabled selectively for active presence and notification channels only to optimize bandwidth and battery life.
