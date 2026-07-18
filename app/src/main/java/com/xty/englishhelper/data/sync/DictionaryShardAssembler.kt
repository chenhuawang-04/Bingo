package com.xty.englishhelper.data.sync

import com.squareup.moshi.Moshi
import com.xty.englishhelper.data.json.DictionaryChunkRefJsonModel
import com.xty.englishhelper.data.json.DictionaryCloudEntryJsonModel
import com.xty.englishhelper.data.json.DictionaryJsonModel
import com.xty.englishhelper.data.json.DictionaryShardChunkJsonModel
import com.xty.englishhelper.data.json.DictionaryShardIndexJsonModel
import com.xty.englishhelper.data.json.StudyStateJsonModel
import com.xty.englishhelper.data.json.WordJsonModel
import com.xty.englishhelper.data.json.WordEdgeJsonModel
import com.xty.englishhelper.data.json.WordPhraseJsonModel
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DictionaryShardAssembler @Inject constructor(
    moshi: Moshi
) {

    data class ChunkFile(
        val path: String,
        val payload: DictionaryShardChunkJsonModel,
        val ref: DictionaryChunkRefJsonModel
    )

    data class ShardedDictionary(
        val entry: DictionaryCloudEntryJsonModel,
        val index: DictionaryShardIndexJsonModel,
        val chunks: List<ChunkFile>
    )

    inner class Accumulator internal constructor(
        private val index: DictionaryShardIndexJsonModel
    ) {
        private val words = mutableListOf<WordJsonModel>()
        private val studyStates = mutableListOf<StudyStateJsonModel>()
        private val wordPhrases = mutableListOf<WordPhraseJsonModel>()
        private val wordEdges = mutableListOf<WordEdgeJsonModel>()
        private var acceptedChunks = 0

        fun accept(ref: DictionaryChunkRefJsonModel, chunk: DictionaryShardChunkJsonModel) {
            validateChunk(ref, chunk)
            words += chunk.words
            studyStates += chunk.studyStates
            wordPhrases += chunk.wordPhrases
            wordEdges += chunk.wordEdges
            acceptedChunks++
        }

        fun build(): DictionaryJsonModel {
            check(acceptedChunks == index.chunks.size) {
                "Dictionary chunk count mismatch: expected=${index.chunks.size} actual=$acceptedChunks"
            }
            return DictionaryJsonModel(
                dictionaryUid = index.dictionaryUid,
                name = index.name,
                description = index.description,
                color = index.color,
                schemaVersion = index.dictionarySchemaVersion,
                createdAt = index.createdAt,
                updatedAt = index.updatedAt,
                words = words,
                units = index.units,
                studyStates = studyStates,
                wordPools = index.wordPools,
                wordPoolStrategies = index.wordPoolStrategies,
                wordEdges = index.wordEdges + wordEdges,
                phraseTags = index.phraseTags,
                wordPhrases = wordPhrases
            )
        }
    }

    private val wordAdapter = moshi.adapter(WordJsonModel::class.java)
    private val wordEdgeAdapter = moshi.adapter(WordEdgeJsonModel::class.java)
    private val studyStateAdapter = moshi.adapter(StudyStateJsonModel::class.java)
    private val wordPhraseAdapter = moshi.adapter(WordPhraseJsonModel::class.java)
    private val chunkAdapter = moshi.adapter(DictionaryShardChunkJsonModel::class.java)

    fun buildFolderPath(dictionaryUid: String, dictionaryName: String): String {
        val safeUid = dictionaryUid
            .trim()
            .replace(Regex("[^\\w-]+"), "_")
            .ifBlank { "" }
        if (safeUid.isNotBlank()) {
            return "dictionaries/$safeUid"
        }
        val slug = dictionaryName
            .replace(Regex("[^\\w\\u4e00-\\u9fff]+"), "_")
            .trim { it == '_' }
            .ifBlank { "dict" }
        return "dictionaries/${slug}__${shortHash(dictionaryName)}"
    }

    fun shard(dictionary: DictionaryJsonModel): ShardedDictionary {
        val folderPath = buildFolderPath(dictionary.dictionaryUid, dictionary.name)
        val stateBuckets = dictionary.studyStates
            .filter { it.wordUid.isNotBlank() }
            .groupBy { it.wordUid }
        val phraseBuckets = dictionary.wordPhrases
            .filter { it.wordUid.isNotBlank() }
            .groupBy { it.wordUid }
        val wordsByBucket = Array(BUCKET_COUNT) { mutableListOf<WordJsonModel>() }
        dictionary.words
            .sortedBy { stableWordKey(it) }
            .forEach { word ->
                wordsByBucket[bucketIndexFor(stableWordKey(word))].add(word)
            }

        val chunkFiles = mutableListOf<ChunkFile>()
        wordsByBucket.forEachIndexed { bucketId, bucketWords ->
            var partIndex = 0
            val currentWords = mutableListOf<WordJsonModel>()
            val currentStates = mutableListOf<StudyStateJsonModel>()
            val currentPhrases = mutableListOf<WordPhraseJsonModel>()
            var currentEstimatedBytes = emptyChunkEstimatedBytes()

            fun flushCurrent() {
                if (currentWords.isEmpty()) return
                val payload = DictionaryShardChunkJsonModel(
                    schemaVersion = CHUNK_SCHEMA_VERSION,
                    words = currentWords.toList(),
                    studyStates = currentStates.toList(),
                    wordPhrases = currentPhrases.toList()
                )
                val payloadJson = chunkAdapter.toJson(payload)
                val chunkPath = "$folderPath/chunks/b${bucketId.toString().padStart(2, '0')}_part_${partIndex.toString().padStart(3, '0')}.json"
                chunkFiles += ChunkFile(
                    path = chunkPath,
                    payload = payload,
                    ref = DictionaryChunkRefJsonModel(
                        file = chunkPath,
                        wordCount = currentWords.size,
                        stateCount = currentStates.size,
                        phraseCount = currentPhrases.size,
                        contentHash = shortHash(payloadJson)
                    )
                )
                currentWords.clear()
                currentStates.clear()
                currentPhrases.clear()
                currentEstimatedBytes = emptyChunkEstimatedBytes()
                partIndex += 1
            }

            bucketWords.forEach { word ->
                val linkedStates = stateBuckets[word.wordUid].orEmpty()
                val linkedPhrases = phraseBuckets[word.wordUid].orEmpty()
                val estimatedAddedBytes = estimateWordBytes(word) +
                    linkedStates.sumOf(::estimateStudyStateBytes) +
                    linkedPhrases.sumOf(::estimateWordPhraseBytes)
                val wouldOverflow = currentWords.isNotEmpty() &&
                    currentEstimatedBytes + estimatedAddedBytes > TARGET_CHUNK_BYTES

                if (wouldOverflow) {
                    flushCurrent()
                }

                currentWords += word
                currentStates += linkedStates
                currentPhrases += linkedPhrases
                currentEstimatedBytes += estimatedAddedBytes
            }

            flushCurrent()
        }

        var edgePartIndex = 0
        val currentEdges = mutableListOf<WordEdgeJsonModel>()
        var currentEdgeBytes = emptyChunkEstimatedBytes()

        fun flushEdges() {
            if (currentEdges.isEmpty()) return
            val payload = DictionaryShardChunkJsonModel(
                schemaVersion = CHUNK_SCHEMA_VERSION,
                wordEdges = currentEdges.toList()
            )
            val payloadJson = chunkAdapter.toJson(payload)
            val chunkPath = "$folderPath/chunks/edges_part_${edgePartIndex.toString().padStart(3, '0')}.json"
            chunkFiles += ChunkFile(
                path = chunkPath,
                payload = payload,
                ref = DictionaryChunkRefJsonModel(
                    file = chunkPath,
                    edgeCount = currentEdges.size,
                    contentHash = shortHash(payloadJson)
                )
            )
            currentEdges.clear()
            currentEdgeBytes = emptyChunkEstimatedBytes()
            edgePartIndex++
        }

        dictionary.wordEdges
            .sortedWith(compareBy<WordEdgeJsonModel> { minOf(it.wordUidA, it.wordUidB) }
                .thenBy { maxOf(it.wordUidA, it.wordUidB) }
                .thenBy { it.edgeType })
            .forEach { edge ->
                val estimatedAddedBytes = estimateWordEdgeBytes(edge)
                if (currentEdges.isNotEmpty() && currentEdgeBytes + estimatedAddedBytes > TARGET_CHUNK_BYTES) {
                    flushEdges()
                }
                currentEdges += edge
                currentEdgeBytes += estimatedAddedBytes
            }
        flushEdges()

        val indexPath = "$folderPath/index.json"
        val index = DictionaryShardIndexJsonModel(
            dictionaryUid = dictionary.dictionaryUid,
            name = dictionary.name,
            description = dictionary.description,
            color = dictionary.color,
            schemaVersion = INDEX_SCHEMA_VERSION,
            dictionarySchemaVersion = dictionary.schemaVersion,
            createdAt = dictionary.createdAt,
            updatedAt = dictionary.updatedAt,
            totalWords = dictionary.words.size,
            totalStudyStates = dictionary.studyStates.size,
            totalWordPhrases = dictionary.wordPhrases.size,
            totalWordEdges = dictionary.wordEdges.size,
            units = dictionary.units,
            wordPools = dictionary.wordPools,
            wordPoolStrategies = dictionary.wordPoolStrategies,
            wordEdges = emptyList(),
            phraseTags = dictionary.phraseTags,
            chunks = chunkFiles.map { it.ref }
        )
        val entry = DictionaryCloudEntryJsonModel(
            dictionaryUid = dictionary.dictionaryUid,
            name = dictionary.name,
            format = DictionaryCloudEntryJsonModel.FORMAT_SHARDED,
            path = indexPath,
            totalWords = dictionary.words.size,
            chunkCount = chunkFiles.size
        )
        return ShardedDictionary(
            entry = entry,
            index = index,
            chunks = chunkFiles
        )
    }

    fun assemble(
        index: DictionaryShardIndexJsonModel,
        chunksByPath: Map<String, DictionaryShardChunkJsonModel>
    ): DictionaryJsonModel {
        val accumulator = newAccumulator(index)
        index.chunks.forEach { ref ->
            val chunk = chunksByPath[ref.file]
                ?: throw IllegalStateException("Missing dictionary chunk: ${ref.file}")
            accumulator.accept(ref, chunk)
        }
        return accumulator.build()
    }

    fun newAccumulator(index: DictionaryShardIndexJsonModel): Accumulator = Accumulator(index)

    fun validateChunk(
        ref: DictionaryChunkRefJsonModel,
        chunk: DictionaryShardChunkJsonModel
    ) {
        if (ref.wordCount != chunk.words.size) {
            throw IllegalStateException(
                "Dictionary chunk word count mismatch: ${ref.file} expected=${ref.wordCount} actual=${chunk.words.size}"
            )
        }
        if (ref.stateCount != chunk.studyStates.size) {
            throw IllegalStateException(
                "Dictionary chunk study state count mismatch: ${ref.file} expected=${ref.stateCount} actual=${chunk.studyStates.size}"
            )
        }
        if (ref.phraseCount != chunk.wordPhrases.size) {
            throw IllegalStateException(
                "Dictionary chunk phrase count mismatch: ${ref.file} expected=${ref.phraseCount} actual=${chunk.wordPhrases.size}"
            )
        }
        if (ref.edgeCount != chunk.wordEdges.size) {
            throw IllegalStateException(
                "Dictionary chunk edge count mismatch: ${ref.file} expected=${ref.edgeCount} actual=${chunk.wordEdges.size}"
            )
        }
        val actualHash = chunkContentHash(chunk)
        if (ref.contentHash.isNotBlank() && ref.contentHash != actualHash) {
            throw IllegalStateException(
                "Dictionary chunk hash mismatch: ${ref.file} expected=${ref.contentHash} actual=${actualHash}"
            )
        }
    }

    fun chunkContentHash(chunk: DictionaryShardChunkJsonModel): String {
        return shortHash(chunkAdapter.toJson(chunk))
    }

    private fun stableWordKey(word: WordJsonModel): String {
        return word.wordUid.ifBlank { word.spelling.trim().lowercase() }
    }

    private fun bucketIndexFor(stableKey: String): Int {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(stableKey.toByteArray(Charsets.UTF_8))
        return (digest[0].toInt() and 0xFF) % BUCKET_COUNT
    }

    private fun emptyChunkEstimatedBytes(): Int = 64

    private fun estimateWordBytes(word: WordJsonModel): Int {
        return wordAdapter.toJson(word).toByteArray(Charsets.UTF_8).size + 2
    }

    private fun estimateStudyStateBytes(state: StudyStateJsonModel): Int {
        return studyStateAdapter.toJson(state).toByteArray(Charsets.UTF_8).size + 2
    }

    private fun estimateWordPhraseBytes(phrase: WordPhraseJsonModel): Int {
        return wordPhraseAdapter.toJson(phrase).toByteArray(Charsets.UTF_8).size + 2
    }

    private fun estimateWordEdgeBytes(edge: WordEdgeJsonModel): Int {
        return wordEdgeAdapter.toJson(edge).toByteArray(Charsets.UTF_8).size + 2
    }

    private fun shortHash(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
        return digest.take(6).joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val BUCKET_COUNT = 16
        private const val TARGET_CHUNK_BYTES = 350 * 1024
        private const val INDEX_SCHEMA_VERSION = 4
        private const val CHUNK_SCHEMA_VERSION = 3
    }
}
