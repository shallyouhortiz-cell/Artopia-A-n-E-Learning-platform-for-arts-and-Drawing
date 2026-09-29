package com.example.artopiacontrol.data.result

sealed class RepoResult<out T> {
    data class Success<T>(val data: T) : RepoResult<T>()
    data class Failure(val error: AppError) : RepoResult<Nothing>()
}
