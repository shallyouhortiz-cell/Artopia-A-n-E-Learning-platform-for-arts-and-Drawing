package com.example.artopiacontrol.data.result

sealed class AppError {
    data class Network(val cause: Throwable) : AppError()
    data class PermissionDenied(val message: String) : AppError()
    data class Validation(val message: String) : AppError()
    data class Unknown(val cause: Throwable) : AppError()
}
