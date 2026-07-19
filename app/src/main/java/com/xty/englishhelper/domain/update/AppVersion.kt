package com.xty.englishhelper.domain.update

data class AppVersion(
    val numbers: List<Int>,
    val prerelease: List<String>
) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int {
        val width = maxOf(numbers.size, other.numbers.size)
        repeat(width) { index ->
            val comparison = (numbers.getOrNull(index) ?: 0)
                .compareTo(other.numbers.getOrNull(index) ?: 0)
            if (comparison != 0) return comparison
        }
        if (prerelease.isEmpty() && other.prerelease.isNotEmpty()) return 1
        if (prerelease.isNotEmpty() && other.prerelease.isEmpty()) return -1
        val prereleaseWidth = maxOf(prerelease.size, other.prerelease.size)
        repeat(prereleaseWidth) { index ->
            val left = prerelease.getOrNull(index) ?: return -1
            val right = other.prerelease.getOrNull(index) ?: return 1
            val leftNumber = left.toIntOrNull()
            val rightNumber = right.toIntOrNull()
            val comparison = when {
                leftNumber != null && rightNumber != null -> leftNumber.compareTo(rightNumber)
                leftNumber != null -> -1
                rightNumber != null -> 1
                else -> left.compareTo(right, ignoreCase = true)
            }
            if (comparison != 0) return comparison
        }
        return 0
    }

    companion object {
        fun parse(raw: String): AppVersion? {
            val normalized = raw.trim().removePrefix("v").removePrefix("V")
                .substringBefore('+')
            val core = normalized.substringBefore('-')
            val numbers = core.split('.').map { part ->
                part.takeWhile(Char::isDigit).toIntOrNull() ?: return null
            }
            if (numbers.isEmpty()) return null
            val prerelease = normalized.substringAfter('-', "")
                .split('.', '-')
                .filter(String::isNotBlank)
            return AppVersion(numbers, prerelease)
        }
    }
}

fun isNewerVersion(candidate: String, current: String): Boolean {
    val candidateVersion = AppVersion.parse(candidate) ?: return false
    val currentVersion = AppVersion.parse(current) ?: return false
    return candidateVersion > currentVersion
}
