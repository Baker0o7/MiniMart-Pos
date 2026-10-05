package com.minimart.pos.util

/**
 * Sealed wrapper for ViewModel UI state — enforces consistent
 * Loading / Success / Error pattern across all screens.
 */
sealed class UiResult<out T> {
    object Loading : UiResult<Nothing>()
    data class Success<T>(val data: T) : UiResult<T>()
    data class Error(val message: String, val cause: Throwable? = null) : UiResult<Nothing>()

    val isLoading get() = this is Loading
    val isSuccess get() = this is Success
    val isError   get() = this is Error

    fun getOrNull(): T? = (this as? Success)?.data
    fun errorOrNull(): String? = (this as? Error)?.message
}

/** Convenience: wrap a suspend call in UiResult */
suspend fun <T> runUiResult(block: suspend () -> T): UiResult<T> =
    try { UiResult.Success(block()) }
    catch (e: Exception) { UiResult.Error(e.localizedMessage ?: "Unknown error", e) }
