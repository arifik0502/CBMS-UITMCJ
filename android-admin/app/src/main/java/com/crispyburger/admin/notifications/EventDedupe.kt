package com.crispyburger.admin.notifications

/**
 * Pure de-duplication of notification events. State is a newline-separated list of
 * "key|timestamp" (oldest first), so eviction order is well defined (unlike a StringSet).
 */
object EventDedupe {
    const val WINDOW_MS = 20_000L
    const val MAX_ENTRIES = 100

    data class Result(val shouldPost: Boolean, val updatedState: String)

    fun check(
        storedState: String,
        eventKey: String,
        nowMs: Long,
        windowMs: Long = WINDOW_MS,
        maxEntries: Int = MAX_ENTRIES
    ): Result {
        val key = eventKey.replace('\n', ' ').replace('\r', ' ')

        val entries = storedState.split('\n')
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val index = line.lastIndexOf('|')
                if (index <= 0) null else line.substring(0, index) to (line.substring(index + 1).toLongOrNull() ?: 0L)
            }
            .toMutableList()

        val existing = entries.firstOrNull { it.first == key }
        if (existing != null && nowMs - existing.second < windowMs) {
            return Result(shouldPost = false, updatedState = storedState)
        }

        entries.removeAll { it.first == key }
        entries.add(key to nowMs)
        while (entries.size > maxEntries) entries.removeAt(0)

        return Result(
            shouldPost = true,
            updatedState = entries.joinToString("\n") { "${it.first}|${it.second}" }
        )
    }
}
