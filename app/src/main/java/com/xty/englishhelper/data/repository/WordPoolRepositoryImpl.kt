package com.xty.englishhelper.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.xty.englishhelper.data.local.AppDatabase
import com.xty.englishhelper.data.local.dao.WordDao
import com.xty.englishhelper.data.local.dao.WordPoolDao
import com.xty.englishhelper.data.local.entity.WordPoolEntity
import com.xty.englishhelper.data.local.entity.WordPoolMemberEntity
import com.xty.englishhelper.data.local.relation.WordWithDetails
import com.xty.englishhelper.data.mapper.toDomain
import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.data.remote.AiApiClientProvider
import com.xty.englishhelper.data.remote.ChatMessage
import com.xty.englishhelper.domain.model.AiSettingsScope
import com.xty.englishhelper.domain.model.PoolStrategy
import com.xty.englishhelper.domain.model.WordPool
import com.xty.englishhelper.domain.pool.BuiltPool
import com.xty.englishhelper.domain.pool.PoolCandidate
import com.xty.englishhelper.domain.pool.WordPoolEngine
import com.xty.englishhelper.domain.repository.WordPoolRepository
import kotlinx.coroutines.ensureActive
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

private data class BuiltPoolWithWordIds(
    val focusWordId: Long?,
    val memberWordIds: List<Long>
)

@Singleton
class WordPoolRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val wordPoolDao: WordPoolDao,
    private val wordDao: WordDao,
    private val aiApiClientProvider: AiApiClientProvider,
    private val settingsDataStore: SettingsDataStore
) : WordPoolRepository {

    private val engine = WordPoolEngine()

    override suspend fun getPoolsForWord(wordId: Long): List<WordPool> {
        val poolIds = wordPoolDao.getPoolIdsForWord(wordId)
        if (poolIds.isEmpty()) return emptyList()

        val poolEntities = wordPoolDao.getPoolsByIds(poolIds)
        val allWords = wordPoolDao.getWordWithDetailsByPoolIds(poolIds)

        // Group members by pool
        val memberships = wordPoolDao.getMembershipsByPoolIds(poolIds)
        val poolToWordIds = mutableMapOf<Long, MutableSet<Long>>()
        memberships.forEach { m ->
            poolToWordIds.getOrPut(m.poolId) { mutableSetOf() }.add(m.wordId)
        }

        val wordMap = allWords.associateBy { it.word.id }

        return poolEntities.map { entity ->
            val memberWordIds = poolToWordIds[entity.id] ?: emptySet()
            val memberDetails = memberWordIds
                .filter { it != wordId }
                .mapNotNull { wid -> wordMap[wid]?.toDomain() }
            WordPool(
                poolId = entity.id,
                focusWordId = entity.focusWordId,
                strategy = entity.strategy,
                algorithmVersion = entity.algorithmVersion,
                members = memberDetails
            )
        }.filter { it.members.isNotEmpty() }
    }

    override suspend fun rebuildPools(
        dictionaryId: Long,
        strategy: PoolStrategy,
        onProgress: (Int, Int) -> Unit
    ) {
        val words = wordDao.getWordsByDictionaryOnce(dictionaryId)
        val total = words.size
        onProgress(0, total)

        val pools: List<BuiltPoolWithWordIds> = when (strategy) {
            PoolStrategy.BALANCED, PoolStrategy.BALANCED_WITH_AI ->
                buildBalanced(words, dictionaryId, strategy, onProgress, total)
            PoolStrategy.QUALITY_FIRST ->
                buildQualityFirst(words, dictionaryId, onProgress, total)
        }

        // Ensure not cancelled before writing
        coroutineContext.ensureActive()

        // Single transaction write
        db.withTransaction {
            wordPoolDao.deleteByDictionaryAndStrategy(dictionaryId, strategy.dbValue)
            pools.forEach { builtPool ->
                val poolId = wordPoolDao.insertPool(
                    WordPoolEntity(
                        dictionaryId = dictionaryId,
                        focusWordId = builtPool.focusWordId,
                        strategy = strategy.dbValue,
                        algorithmVersion = strategy.algorithmVersion
                    )
                )
                wordPoolDao.insertMembers(
                    builtPool.memberWordIds.map {
                        WordPoolMemberEntity(wordId = it, poolId = poolId)
                    }
                )
            }
        }
    }

    override suspend fun getPoolCount(dictionaryId: Long): Int =
        wordPoolDao.countPools(dictionaryId)

    override suspend fun getWordToPoolsMap(dictionaryId: Long): Map<Long, Set<Long>> {
        val memberships = wordPoolDao.getAllMemberships(dictionaryId)
        val result = mutableMapOf<Long, MutableSet<Long>>()
        memberships.forEach { m ->
            result.getOrPut(m.wordId) { mutableSetOf() }.add(m.poolId)
        }
        return result
    }

    override suspend fun getPoolToMembersMap(dictionaryId: Long): Map<Long, Set<Long>> {
        val memberships = wordPoolDao.getAllMemberships(dictionaryId)
        val result = mutableMapOf<Long, MutableSet<Long>>()
        memberships.forEach { m ->
            result.getOrPut(m.poolId) { mutableSetOf() }.add(m.wordId)
        }
        return result
    }

    override suspend fun getPoolVersionInfo(dictionaryId: Long): List<Pair<String, String>> {
        return wordPoolDao.getPoolVersionInfo(dictionaryId).map { it.strategy to it.algorithmVersion }
    }

    // ── BALANCED build ──

    private suspend fun buildBalanced(
        words: List<WordWithDetails>,
        dictionaryId: Long,
        strategy: PoolStrategy,
        onProgress: (Int, Int) -> Unit,
        total: Int
    ): List<BuiltPoolWithWordIds> {
        // Get associations filtered by same dictionary
        val associations = wordPoolDao.getAssociationsInDictionary(dictionaryId)
        val assocMap = mutableMapOf<Long, MutableList<Long>>()
        associations.forEach { pair ->
            assocMap.getOrPut(pair.wordId) { mutableListOf() }.add(pair.associatedWordId)
            assocMap.getOrPut(pair.associatedWordId) { mutableListOf() }.add(pair.wordId)
        }

        val candidates = words.mapIndexed { index, wwd ->
            coroutineContext.ensureActive()
            onProgress(index, total)
            val domain = wwd.toDomain()
            PoolCandidate(
                index = index,
                wordId = domain.id,
                spelling = domain.spelling,
                meanings = domain.meanings.map { it.definition },
                synonymSpellings = domain.synonyms.map { it.word },
                similarSpellings = domain.similarWords.map { it.word },
                cognateSpellings = domain.cognates.map { it.word },
                associatedWordIds = assocMap[domain.id] ?: emptyList()
            )
        }

        var pools = engine.buildPools(candidates)
        onProgress(total, total)

        // Optional AI batch completion for orphaned words
        if (strategy == PoolStrategy.BALANCED_WITH_AI) {
            coroutineContext.ensureActive()
            pools = tryAiBatchCompletion(candidates, pools)
        }

        return pools.map { pool ->
            BuiltPoolWithWordIds(
                focusWordId = null,
                memberWordIds = pool.memberIndices.map { candidates[it].wordId }
            )
        }
    }

    // ── QUALITY_FIRST build ──

    private suspend fun buildQualityFirst(
        words: List<WordWithDetails>,
        dictionaryId: Long,
        onProgress: (Int, Int) -> Unit,
        total: Int
    ): List<BuiltPoolWithWordIds> {
        val result = mutableListOf<BuiltPoolWithWordIds>()
        val domains = words.map { it.toDomain() }

        for ((index, targetWord) in domains.withIndex()) {
            coroutineContext.ensureActive()
            onProgress(index, total)

            // Pick 49 random other words with fixed seed for reproducibility
            val others = domains.filter { it.id != targetWord.id }
            val rng = Random(targetWord.id)
            val sampled = if (others.size <= 49) others else others.shuffled(rng).take(49)

            val candidateList = listOf(targetWord) + sampled

            // Build prompt
            val prompt = buildString {
                appendLine("你是词汇学习助手。下面是词库中${candidateList.size}个单词（index. spelling: 首条中文释义）。")
                appendLine("目标词是 #0（${targetWord.spelling}）。请找出其中与 #0 形近（拼写相似）或意近（语义相关）的词。")
                appendLine("只列出相关词的序号，不相关的忽略，不含 #0 自身。")
                appendLine("返回严格JSON数组，如 [1,5,12]，若无相关词返回 []")
                appendLine()
                candidateList.forEachIndexed { i, w ->
                    val firstMeaning = w.meanings.firstOrNull()?.definition ?: ""
                    appendLine("$i. ${w.spelling}: $firstMeaning")
                }
            }

            val relatedIndices = callAiForIndices(prompt, candidateList.size)

            if (relatedIndices.isNotEmpty()) {
                val memberIds = mutableListOf(targetWord.id)
                relatedIndices.forEach { idx ->
                    if (idx in candidateList.indices && idx != 0) {
                        memberIds.add(candidateList[idx].id)
                    }
                }
                if (memberIds.size >= 2) {
                    result.add(
                        BuiltPoolWithWordIds(
                            focusWordId = targetWord.id,
                            memberWordIds = memberIds.distinct()
                        )
                    )
                }
            }
        }

        onProgress(total, total)
        return result
    }

    // ── AI helpers ──

    private suspend fun tryAiBatchCompletion(
        candidates: List<PoolCandidate>,
        existingPools: List<BuiltPool>
    ): List<BuiltPool> {
        val assignedIndices = existingPools.flatMap { it.memberIndices }.toSet()
        val orphans = candidates.filter { it.index !in assignedIndices }

        if (orphans.isEmpty()) return existingPools

        // Process in batches of 40
        val allAiGroups = mutableListOf<List<Int>>()
        orphans.chunked(40).forEach { batch ->
            coroutineContext.ensureActive()
            val prompt = buildString {
                appendLine("以下单词来自同一词库。找出其中形近（拼写相似、易混淆）或意近（语义相关）的分组，每组至少2词。")
                appendLine("返回严格JSON: [[0,3],[1,5,12]]（数字为列表序号，不含无关词，不要解释）")
                appendLine()
                batch.forEachIndexed { i, c ->
                    val firstMeaning = c.meanings.firstOrNull() ?: ""
                    appendLine("$i. ${c.spelling}: $firstMeaning")
                }
            }

            val groups = callAiForGroups(prompt, batch.size)
            // Map batch-local indices back to global indices
            groups.forEach { group ->
                val globalGroup = group.mapNotNull { localIdx ->
                    if (localIdx in batch.indices) batch[localIdx].index else null
                }
                if (globalGroup.size >= 2) {
                    allAiGroups.add(globalGroup)
                }
            }
        }

        return if (allAiGroups.isEmpty()) existingPools
        else engine.mergeAiGroups(candidates, existingPools, allAiGroups)
    }

    private suspend fun callAiForIndices(prompt: String, listSize: Int, retryCount: Int = 0): List<Int> {
        return try {
            val response = callAi(prompt)
            parseJsonIntArray(response, listSize)
        } catch (e: Exception) {
            if (retryCount < 1) {
                Log.w("WordPoolRepo", "AI call failed, retrying", e)
                callAiForIndices(prompt, listSize, retryCount + 1)
            } else {
                Log.w("WordPoolRepo", "AI call failed after retry", e)
                emptyList()
            }
        }
    }

    private suspend fun callAiForGroups(prompt: String, listSize: Int, retryCount: Int = 0): List<List<Int>> {
        return try {
            val response = callAi(prompt)
            parseJsonIntArrayOfArrays(response, listSize)
        } catch (e: Exception) {
            if (retryCount < 1) {
                Log.w("WordPoolRepo", "AI batch call failed, retrying", e)
                callAiForGroups(prompt, listSize, retryCount + 1)
            } else {
                Log.w("WordPoolRepo", "AI batch call failed after retry", e)
                emptyList()
            }
        }
    }

    private suspend fun callAi(prompt: String): String {
        val config = settingsDataStore.getAiConfig(AiSettingsScope.POOL)
        val client = aiApiClientProvider.getClient(config.provider)

        return client.sendMessage(
            url = config.baseUrl,
            apiKey = config.apiKey,
            model = config.model,
            systemPrompt = null,
            messages = listOf(ChatMessage(role = "user", content = prompt)),
            maxTokens = 1024
        )
    }

    private fun parseJsonIntArray(text: String, maxValue: Int): List<Int> {
        // Try JSON parse first
        val trimmed = text.trim()
        val arrayMatch = Regex("\\[([\\d,\\s]*)\\]").find(trimmed)
        if (arrayMatch != null) {
            return arrayMatch.groupValues[1]
                .split(",")
                .mapNotNull { it.trim().toIntOrNull() }
                .filter { it in 0 until maxValue }
        }
        return emptyList()
    }

    private fun parseJsonIntArrayOfArrays(text: String, maxValue: Int): List<List<Int>> {
        val result = mutableListOf<List<Int>>()
        // Match each inner array
        val pattern = Regex("\\[\\s*(\\d+(?:\\s*,\\s*\\d+)*)\\s*\\]")
        pattern.findAll(text).forEach { match ->
            val indices = match.groupValues[1]
                .split(",")
                .mapNotNull { it.trim().toIntOrNull() }
                .filter { it in 0 until maxValue }
            if (indices.size >= 2) {
                result.add(indices)
            }
        }
        return result
    }
}
