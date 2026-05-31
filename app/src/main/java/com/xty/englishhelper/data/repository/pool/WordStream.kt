package com.xty.englishhelper.data.repository.pool

import com.xty.englishhelper.data.local.dao.WordDao
import com.xty.englishhelper.data.mapper.toDomain
import com.xty.englishhelper.domain.model.WordDetails

/**
 * Cursor-based word stream for memory-efficient iteration.
 * Loads words in batches of [batchSize] from the database.
 *
 * Usage:
 * ```
 * val stream = WordStream(wordDao, dictionaryId)
 * while (stream.hasNext()) {
 *     val word = stream.next()
 *     // process word
 * }
 * ```
 */
class WordStream(
    private val wordDao: WordDao,
    private val dictionaryId: Long,
    private val batchSize: Int = BATCH_SIZE
) {
    companion object {
        private const val BATCH_SIZE = 500
    }

    private val buffer = ArrayDeque<WordDetails>(batchSize)
    private var lastId = 0L
    private var exhausted = false
    private var _loaded = 0

    /** Total words loaded so far (for progress reporting). */
    val loaded: Int get() = _loaded

    /**
     * Returns true if there are more words to read.
     * May prefetch the next batch if the buffer is empty.
     */
    suspend fun hasNext(): Boolean {
        if (exhausted && buffer.isEmpty()) return false
        if (buffer.isNotEmpty()) return true
        prefetch()
        return buffer.isNotEmpty()
    }

    /**
     * Returns the next word in the stream.
     * @throws NoSuchElementException if the stream is exhausted.
     */
    suspend fun next(): WordDetails {
        if (!hasNext()) throw NoSuchElementException("WordStream exhausted")
        _loaded++
        return buffer.removeFirst()
    }

    /**
     * Returns the next [n] words (or fewer if near end).
     * Useful for building the initial window.
     */
    suspend fun take(n: Int): List<WordDetails> {
        val result = mutableListOf<WordDetails>()
        repeat(n) {
            if (!hasNext()) return@repeat
            result.add(next())
        }
        return result
    }

    private suspend fun prefetch() {
        if (exhausted) return
        val rows = wordDao.getWordsByDictionaryPaginated(dictionaryId, lastId, batchSize)
        if (rows.isEmpty()) {
            exhausted = true
            return
        }
        rows.forEach { buffer.addLast(it.toDomain()) }
        lastId = rows.last().word.id
        if (rows.size < batchSize) exhausted = true
    }
}
