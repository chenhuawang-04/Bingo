package com.xty.englishhelper.domain.usecase.study

import com.xty.englishhelper.domain.background.BackgroundTaskEnqueueResult
import com.xty.englishhelper.domain.background.BackgroundTaskManager
import com.xty.englishhelper.domain.model.BackgroundTaskStatus
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.repository.BackgroundTaskRepository
import com.xty.englishhelper.domain.repository.WordPoolRepository
import com.xty.englishhelper.domain.repository.WordRepository
import com.xty.englishhelper.domain.repository.WordEdgeNeighborPreview
import com.xty.englishhelper.domain.usecase.unit.AddWordsToUnitUseCase
import com.xty.englishhelper.domain.usecase.unit.GetUnitIdsForWordUseCase
import com.xty.englishhelper.domain.usecase.word.SaveWordUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class StudyWordNoteOutcome {
    PROMOTED,
    QUEUED,
    ALREADY_QUEUED
}

data class SubmitStudyWordNoteResult(
    val relatedWordId: Long,
    val relatedSpelling: String,
    val createdWord: Boolean,
    val outcome: StudyWordNoteOutcome,
    val message: String
)

class GetStudyWordEdgePreviewsUseCase @Inject constructor(
    private val wordPoolRepository: WordPoolRepository
) {
    suspend operator fun invoke(
        dictionaryId: Long,
        wordId: Long,
        minConfidence: Double
    ): List<WordEdgeNeighborPreview> = wordPoolRepository.getWordEdgePreviews(
        dictionaryId = dictionaryId,
        wordId = wordId,
        minConfidence = minConfidence
    )
}

class SearchStudyWordNoteSuggestionsUseCase @Inject constructor(
    private val wordRepository: WordRepository
) {
    private companion object {
        const val MIN_QUERY_LENGTH = 2
        const val MIN_FETCH_LIMIT = 12
        const val FETCH_MULTIPLIER = 4
    }

    suspend operator fun invoke(
        currentWord: WordDetails,
        rawInput: String,
        limit: Int = 8
    ): List<String> {
        val normalizedQuery = rawInput.trim().lowercase()
        if (normalizedQuery.length < MIN_QUERY_LENGTH || limit <= 0) return emptyList()
        val currentNormalized = currentWord.normalizedSpelling.ifBlank {
            currentWord.spelling.trim().lowercase()
        }
        val fetchLimit = maxOf(limit * FETCH_MULTIPLIER, MIN_FETCH_LIMIT)
        return wordRepository.suggestWordSpellings(
            dictionaryId = currentWord.dictionaryId,
            query = normalizedQuery,
            excludeWordId = currentWord.id,
            limit = fetchLimit
        ).asSequence()
            .map(String::trim)
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .filter { it.lowercase() != currentNormalized }
            .sortedWith(
                compareBy<String>(
                    { it.lowercase() != normalizedQuery },
                    { it.length },
                    { it.lowercase() }
                )
            )
            .take(limit)
            .toList()
    }
}

class SubmitStudyWordNoteUseCase @Inject constructor(
    private val wordRepository: WordRepository,
    private val wordPoolRepository: WordPoolRepository,
    private val saveWord: SaveWordUseCase,
    private val getUnitIdsForWord: GetUnitIdsForWordUseCase,
    private val addWordsToUnit: AddWordsToUnitUseCase,
    private val backgroundTaskRepository: BackgroundTaskRepository,
    private val backgroundTaskManager: BackgroundTaskManager
) {
    suspend operator fun invoke(
        currentWord: WordDetails,
        rawInput: String,
        fallbackUnitIds: List<Long> = emptyList()
    ): SubmitStudyWordNoteResult = withContext(Dispatchers.IO) {
        val spelling = rawInput.trim()
        if (spelling.isBlank()) {
            throw IllegalStateException("请输入关联单词")
        }

        val normalized = spelling.lowercase()
        val currentNormalized = currentWord.normalizedSpelling.ifBlank { currentWord.spelling.trim().lowercase() }
        if (normalized == currentNormalized) {
            throw IllegalStateException("便签不能填写当前单词本身")
        }

        val existing = wordRepository.findByNormalizedSpelling(currentWord.dictionaryId, normalized)
        val relatedWord = if (existing != null) {
            existing
        } else {
            val newWordId = saveWord(
                WordDetails(
                    dictionaryId = currentWord.dictionaryId,
                    spelling = spelling
                )
            )
            val targetUnitId = getUnitIdsForWord(currentWord.id).ifEmpty { fallbackUnitIds }.firstOrNull()
            if (targetUnitId != null) {
                addWordsToUnit(targetUnitId, listOf(newWordId))
            }
            wordRepository.getWordById(newWordId)
                ?: throw IllegalStateException("新建单词后无法重新读取")
        }

        val confirmed = wordPoolRepository.confirmWordRelation(
            dictionaryId = currentWord.dictionaryId,
            wordId = currentWord.id,
            relatedWordId = relatedWord.id
        )
        if (confirmed) {
            SubmitStudyWordNoteResult(
                relatedWordId = relatedWord.id,
                relatedSpelling = relatedWord.spelling,
                createdWord = existing == null,
                outcome = StudyWordNoteOutcome.PROMOTED,
                message = "已将 ${relatedWord.spelling} 标记为强关联"
            )
        } else {
            if (existing != null) {
                val existingTask = backgroundTaskRepository.getTaskByDedupeKey(
                    wordNoteTaskDedupeKey(
                        dictionaryId = currentWord.dictionaryId,
                        sourceWordId = currentWord.id,
                        targetWordId = relatedWord.id
                    )
                )
                if (existingTask?.status == BackgroundTaskStatus.PENDING ||
                    existingTask?.status == BackgroundTaskStatus.RUNNING
                ) {
                    SubmitStudyWordNoteResult(
                        relatedWordId = relatedWord.id,
                        relatedSpelling = relatedWord.spelling,
                        createdWord = false,
                        outcome = StudyWordNoteOutcome.ALREADY_QUEUED,
                        message = "${relatedWord.spelling} 的关联整理已在后台队列中"
                    )
                } else {
                wordPoolRepository.organizeWordNoteRelation(
                    relatedWordId = relatedWord.id,
                    dictionaryId = currentWord.dictionaryId,
                    wordId = currentWord.id
                )
                SubmitStudyWordNoteResult(
                    relatedWordId = relatedWord.id,
                    relatedSpelling = relatedWord.spelling,
                    createdWord = false,
                    outcome = StudyWordNoteOutcome.PROMOTED,
                    message = "已将 ${relatedWord.spelling} 连接为强关联"
                )
                }
            } else {
                val enqueueResult = backgroundTaskManager.enqueueWordNoteOrganize(
                    dictionaryId = currentWord.dictionaryId,
                    sourceWordId = currentWord.id,
                    sourceSpelling = currentWord.spelling,
                    targetWordId = relatedWord.id,
                    targetSpelling = relatedWord.spelling,
                    organizeTargetWordFirst = true,
                    targetReferenceHints = listOf(currentWord.spelling),
                    force = true
                )

                when (enqueueResult) {
                    BackgroundTaskEnqueueResult.ENQUEUED,
                    BackgroundTaskEnqueueResult.RESTARTED -> SubmitStudyWordNoteResult(
                        relatedWordId = relatedWord.id,
                        relatedSpelling = relatedWord.spelling,
                        createdWord = true,
                        outcome = StudyWordNoteOutcome.QUEUED,
                        message = "已创建 ${relatedWord.spelling}，并加入后台整理"
                    )

                    BackgroundTaskEnqueueResult.ALREADY_PENDING,
                    BackgroundTaskEnqueueResult.ALREADY_RUNNING -> SubmitStudyWordNoteResult(
                        relatedWordId = relatedWord.id,
                        relatedSpelling = relatedWord.spelling,
                        createdWord = true,
                        outcome = StudyWordNoteOutcome.ALREADY_QUEUED,
                        message = "${relatedWord.spelling} 的关联整理已在后台队列中"
                    )

                    BackgroundTaskEnqueueResult.SKIPPED_SUCCESS -> {
                        val promotedAfterSuccess = wordPoolRepository.confirmWordRelation(
                            dictionaryId = currentWord.dictionaryId,
                            wordId = currentWord.id,
                            relatedWordId = relatedWord.id
                        )
                        if (promotedAfterSuccess) {
                            SubmitStudyWordNoteResult(
                                relatedWordId = relatedWord.id,
                                relatedSpelling = relatedWord.spelling,
                                createdWord = true,
                                outcome = StudyWordNoteOutcome.PROMOTED,
                                message = "已将 ${relatedWord.spelling} 标记为强关联"
                            )
                        } else {
                            SubmitStudyWordNoteResult(
                                relatedWordId = relatedWord.id,
                                relatedSpelling = relatedWord.spelling,
                                createdWord = true,
                                outcome = StudyWordNoteOutcome.ALREADY_QUEUED,
                                message = "${relatedWord.spelling} 的关联整理已在后台队列中"
                            )
                        }
                    }
                }
            }
        }
    }

    private fun wordNoteTaskDedupeKey(
        dictionaryId: Long,
        sourceWordId: Long,
        targetWordId: Long
    ): String {
        val minId = minOf(sourceWordId, targetWordId)
        val maxId = maxOf(sourceWordId, targetWordId)
        return "word_note:$dictionaryId:$minId:$maxId"
    }
}
