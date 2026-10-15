package com.example.merrygoround.core.common

/**
 * Uniform result type for repository / backend operations.
 * Failures carry a stable [AppErrorType] so the UI can react to categories
 * (validation vs auth vs network vs conflict) plus a human-readable message.
 */
sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Failure(
        val type: AppErrorType,
        val message: String,
        val fieldErrors: Map<String, String> = emptyMap()
    ) : AppResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    fun getOrNull(): T? = (this as? Success)?.data
    fun errorOrNull(): Failure? = this as? Failure

    inline fun <R> map(transform: (T) -> R): AppResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Failure -> this
    }

    inline fun onSuccess(block: (T) -> Unit): AppResult<T> {
        if (this is Success) block(data)
        return this
    }

    inline fun onFailure(block: (Failure) -> Unit): AppResult<T> {
        if (this is Failure) block(this)
        return this
    }
}

enum class AppErrorType {
    VALIDATION,
    AUTHENTICATION,
    PERMISSION_DENIED,
    NOT_FOUND,
    CONFLICT,
    NETWORK,
    RATE_LIMITED,
    UNKNOWN
}

inline fun <T> runAppCatching(
    fallback: AppErrorType = AppErrorType.UNKNOWN,
    block: () -> T
): AppResult<T> = try {
    AppResult.Success(block())
} catch (e: AppException) {
    AppResult.Failure(e.type, e.message ?: "Something went wrong", e.fieldErrors)
} catch (e: Exception) {
    AppResult.Failure(fallback, e.message ?: "Something went wrong")
}

/** Thrown by backend implementations to signal a typed failure. */
class AppException(
    val type: AppErrorType,
    override val message: String,
    val fieldErrors: Map<String, String> = emptyMap(),
    cause: Throwable? = null
) : Exception(message, cause)
