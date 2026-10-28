package com.incidex.backend.common

sealed class FirebaseResult<out T> {
    data class Success<T>(val data: T) : FirebaseResult<T>()
    data class Failure(val message: String, val cause: Throwable? = null) : FirebaseResult<Nothing>()
}
