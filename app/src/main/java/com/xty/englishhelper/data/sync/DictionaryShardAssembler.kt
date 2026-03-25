package com.xty.englishhelper.data.sync

import com.squareup.moshi.Moshi
import com.xty.englishhelper.data.json.DictionaryChunkRefJsonModel
import com.xty.englishhelper.data.json.DictionaryCloudEntryJsonModel
import com.xty.englishhelper.data.json.DictionaryJsonModel
import com.xty.englishhelper.data.json.DictionaryShardChunkJsonModel
import com.xty.englishhelper.data.json.DictionaryShardIndexJsonModel
import com.xty.englishhelper.data.json.StudyStateJsonModel
import com.xty.englishhelper.data.json.WordJsonModel
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

    private val wordAdapter = moshi.adapter(WordJsonModel::class.java)
    private val studyStateAdapter = moshi.adapter(StudyStateJsonModel::class.java)
    private val chunkAdapter = moshi.adapter(DictionaryShardChunkJsonModel::class.java)

    fun buildFolderPath(dictionaryName: String): String {
        val slug = dictionaryName
            .replace(Regex("[^\\w\\u4e00-\\u9fff]+"), "_")
            .trim { it == '_' }
            .ifBlank { "dict" }
        return "dictionaries/${slug}__${shortHash(dictionaryName)}"
    }

    fun shard(dictionary: DictionaryJsonModel): ShardedDictionary {
        val folderPath = buildFolderPath(dictionary.name)
        val stateBuckets = dictionary.studyStates
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
            var currentEstimatedBytes = emptyChunkEstimatedBytes()

            fun flushCurrent() {
                if (currentWords.isEmpty()) return
                val payload = DictionaryShardChunkJsonModel(
                    schemaVersion = CHUNK_SCHEMA_VERSION,
                    words = currentWords.toList(),
                    studyStates = currentStates.toList()
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
                        contentHash = shortHash(payloadJson)
                    )
                )
                currentWords.clear()
                currentStates.clear()
                currentEstimatedBytes = emptyChunkEstimatedBytes()
                partIndex += 1
            }

            bucketWords.forEach { word ->
                val linkedStates = stateBuckets[word.wordUid].orEmpty()
                val estimatedAddedBytes = estimateWordBytes(word) + linkedStates.sumOf(::estimateStudyStateBytes)
                val wouldOverflow = currentWords.isNotEmpty() &&
                    currentEstimatedBytes + estimatedAddedBytes > TARGET_CHUNK_BYTES

                if (wouldOverflow) {
                    flushCurrent()
                }

                currentWords += word
                currentStates += linkedStates
                currentEstimatedBytes += estimatedAddedBytes
            }

            flushCurrent()
        }

        val indexPath = "$folderPath/index.json"
        val index = DictionaryShardIndexJsonModel(
            name = dictionary.name,
            description = dictionary.description,
            schemaVersion = INDEX_SCHEMA_VERSION,
            dictionarySchemaVersion = dictionary.schemaVersion,
            totalWords = dictionary.words.size,
            totalStudyStates = dictionary.studyStates.size,
            units = dictionary.units,
            wordPools = dictionary.wordPools,
            chunks = chunkFiles.map { it.ref }
        )
        val entry = DictionaryCloudEntryJsonModel(
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
        val words = mutableListOf<WordJsonModel>()
        val studyStates = mutableListOf<StudyStateJsonModel>()

        index.chunks.forEach { ref ->
            val chunk = chunksByPath[ref.file]
                ?: throw IllegalStateException("Missing dictionary chunk: ${ref.file}")
            validateChunk(ref, chunk)
            words += chunk.words
            studyStates += chunk.studyStates
        }

        return DictionaryJsonModel(
            name = index.name,
            description = index.description,
            schemaVersion = index.dictionarySchemaVersion,
            words = words,
            units = index.units,
            studyStates = studyStates,
            wordPools = index.wordPools
        )
    }

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

    private fun shortHash(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
        return digest.take(6).joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val BUCKET_COUNT = 16
        private const val TARGET_CHUNK_BYTES = 350 * 1024
        private const val INDEX_SCHEMA_VERSION = 1
        private const val CHUNK_SCHEMA_VERSION = 1
    }
}
