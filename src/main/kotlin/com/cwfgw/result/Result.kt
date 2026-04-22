package com.cwfgw.result

/**
 * A typed-error result, used by service-layer functions that can fail in
 * distinct, caller-branchable ways. Prefer nullable returns (`T?`) for pure
 * "absent" cases; prefer [Result] when multiple failure modes need to drive
 * different caller behavior (e.g., state-machine conflicts at the HTTP
 * boundary).
 *
 * This is deliberately not `kotlin.Result<T>`, which is `Throwable`-bound and
 * not suited for typed domain errors, and not Arrow's `Either` (excluded by
 * CLAUDE.md). The companion extensions stay minimal — add only as needed.
 */
sealed interface Result<out T, out E> {
    data class Ok<T>(val value: T) : Result<T, Nothing>

    data class Err<E>(val error: E) : Result<Nothing, E>
}

inline fun <T, E> Result<T, E>.getOrElse(onError: (E) -> T): T =
    when (this) {
        is Result.Ok -> value
        is Result.Err -> onError(error)
    }

inline fun <T, U, E> Result<T, E>.map(transform: (T) -> U): Result<U, E> =
    when (this) {
        is Result.Ok -> Result.Ok(transform(value))
        is Result.Err -> this
    }

inline fun <T, U, E> Result<T, E>.flatMap(transform: (T) -> Result<U, E>): Result<U, E> =
    when (this) {
        is Result.Ok -> transform(value)
        is Result.Err -> this
    }
