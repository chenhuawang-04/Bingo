package com.xty.englishhelper.domain.article

import java.net.URI

object OnlineArticleSourceUrl {

    fun normalize(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return ""
        return runCatching {
            val uri = URI(trimmed)
            val normalizedPath = uri.path
                ?.takeIf { it.isNotEmpty() }
                ?.let { path -> if (path.length > 1) path.trimEnd('/') else path }
                .orEmpty()
            URI(
                uri.scheme?.lowercase(),
                uri.authority?.lowercase(),
                normalizedPath,
                null,
                null
            ).toString()
        }.getOrDefault(trimmed)
    }

    fun variants(url: String?): List<String> {
        val exact = url?.trim().orEmpty()
        if (exact.isBlank()) return emptyList()
        val normalized = normalize(exact)
        val trailingSlash = withTrailingSlash(normalized)
        return buildList {
            appendIfAbsent(exact)
            appendIfAbsent(normalized)
            appendIfAbsent(trailingSlash)
        }
    }

    private fun withTrailingSlash(url: String): String {
        if (url.isBlank()) return ""
        return runCatching {
            val uri = URI(url)
            val path = uri.path.orEmpty()
            if (path.isBlank() || path == "/" || path.endsWith("/")) {
                url
            } else {
                URI(
                    uri.scheme?.lowercase(),
                    uri.authority?.lowercase(),
                    "$path/",
                    null,
                    null
                ).toString()
            }
        }.getOrDefault(url)
    }

    private fun MutableList<String>.appendIfAbsent(candidate: String) {
        if (candidate.isNotBlank() && candidate !in this) {
            add(candidate)
        }
    }
}
