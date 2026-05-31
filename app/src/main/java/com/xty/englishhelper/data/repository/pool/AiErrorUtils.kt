package com.xty.englishhelper.data.repository.pool

import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Shared AI error classification utilities.
 * Used by WordPoolRepositoryImpl, EdgeReviewer, EntryTypeClassifier.
 */
internal object AiErrorUtils {

    fun isRetryableError(e: Exception): Boolean {
        val msg = e.message?.lowercase() ?: ""
        // Non-retryable: auth errors
        if (msg.contains("401") || msg.contains("403") || msg.contains("unauthorized") || msg.contains("invalid api key")) {
            return false
        }
        // Retryable: rate limit (handled with longer backoff in caller)
        if (isRateLimitError(e)) return true
        // Retryable: network timeouts and connection errors
        if (e is SocketTimeoutException || e is java.net.ConnectException) return true
        if (e is IOException && (msg.contains("timeout") || msg.contains("connect"))) return true
        if (e is kotlinx.coroutines.TimeoutCancellationException) return true
        // Retryable: server errors (5xx)
        if (msg.contains("500") || msg.contains("502") || msg.contains("503") || msg.contains("504")) return true
        // Default: do not retry unknown errors (programming errors, etc.)
        return false
    }

    fun isRateLimitError(e: Exception): Boolean {
        val msg = e.message?.lowercase() ?: ""
        return msg.contains("429") || msg.contains("rate limit")
    }

    fun retryDelay(e: Exception, attempt: Int): Long {
        val baseDelay = if (isRateLimitError(e)) 2000L else 1000L
        return baseDelay * (attempt + 1)
    }
}
