package com.crispyburger.admin.data

import kotlinx.coroutines.CancellationException

/** Like runCatching, but never swallows coroutine cancellation. */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
