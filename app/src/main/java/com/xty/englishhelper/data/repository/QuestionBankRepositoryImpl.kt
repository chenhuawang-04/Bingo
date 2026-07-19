package com.xty.englishhelper.data.repository

import com.xty.englishhelper.data.local.dao.QuestionBankDao
import com.xty.englishhelper.data.local.AppDatabase
import com.xty.englishhelper.data.local.entity.ExamPaperEntity
import com.xty.englishhelper.data.local.entity.ExamPaperSourceEntity
import com.xty.englishhelper.data.local.entity.ExamPaperSlotSelectionEntity
import com.xty.englishhelper.data.local.entity.ExamPaperAnswerDraftEntity
import com.xty.englishhelper.data.local.entity.ExamPaperPracticeProgressEntity
import com.xty.englishhelper.data.local.entity.PracticeRecordEntity
import com.xty.englishhelper.data.local.entity.QuestionGroupEntity
import com.xty.englishhelper.data.local.entity.QuestionGroupParagraphEntity
import com.xty.englishhelper.data.local.entity.QuestionItemEntity
import com.xty.englishhelper.data.local.entity.QuestionSourceArticleEntity
import com.xty.englishhelper.domain.model.AnswerSource
import com.xty.englishhelper.domain.model.ArticleParagraph
import com.xty.englishhelper.domain.model.DifficultyLevel
import com.xty.englishhelper.domain.model.ExamPaper
import com.xty.englishhelper.domain.model.ExamPaperBlueprint
import com.xty.englishhelper.domain.model.ExamPaperCollectionResult
import com.xty.englishhelper.domain.model.ExamPaperProfile
import com.xty.englishhelper.domain.model.ExamPaperSource
import com.xty.englishhelper.domain.model.ExamPaperSourceStatus
import com.xty.englishhelper.domain.model.ExamPaperStatus
import com.xty.englishhelper.domain.model.ExamPaperSummary
import com.xty.englishhelper.domain.model.ExamPaperType
import com.xty.englishhelper.domain.model.ExamPaperCompositionMode
import com.xty.englishhelper.domain.model.AutoPaperSelectionStatus
import com.xty.englishhelper.domain.model.ExamPaperSlotSelection
import com.xty.englishhelper.domain.model.ExamPaperSlotSelectionStatus
import com.xty.englishhelper.domain.model.ArticleAdvancedScoringTargets
import com.xty.englishhelper.domain.model.ParagraphType
import com.xty.englishhelper.domain.model.PracticeRecord
import com.xty.englishhelper.domain.model.QuestionGroup
import com.xty.englishhelper.domain.model.QuestionItem
import com.xty.englishhelper.domain.model.QuestionType
import com.xty.englishhelper.domain.model.SourceVerifyStatus
import com.xty.englishhelper.domain.repository.QuestionBankRepository
import com.xty.englishhelper.domain.repository.ArticleRepository
import com.xty.englishhelper.domain.model.ArticleCategoryDefaults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.room.withTransaction
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuestionBankRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val dao: QuestionBankDao,
    private val articleRepository: ArticleRepository
) : QuestionBankRepository {

    // ── ExamPaper ──

    override fun getAllExamPapers(): Flow<List<ExamPaper>> =
        dao.getAllExamPapers().map { list -> list.map { it.toDomain() } }

    override fun getAllExamPaperSummaries(): Flow<List<ExamPaperSummary>> =
        dao.getAllExamPaperSummaries().map { rows ->
            rows.map { row ->
                ExamPaperSummary(
                    paper = row.toDomain(),
                    collectedSourceCount = row.collected_source_count,
                    generatedGroupCount = row.generated_group_count
                )
            }
        }

    override suspend fun getExamPaperById(id: Long): ExamPaper? =
        dao.getExamPaperById(id)?.toDomain()

    override suspend fun insertExamPaper(paper: ExamPaper): Long {
        val now = System.currentTimeMillis()
        return dao.insertExamPaper(paper.toEntity(now))
    }

    override suspend fun deleteExamPaper(id: Long) = dao.deleteExamPaper(id)

    override suspend fun createAutoExamPaper(
        dayKey: String,
        profile: ExamPaperProfile,
        specialQuestionType: QuestionType
    ): ExamPaper = database.withTransaction {
        val year = dayKey.take(4).toIntOrNull()
            ?: throw IllegalArgumentException("组卷日期无效")
        require(specialQuestionType in ArticleAdvancedScoringTargets.selectableSpecialTypes) {
            "不支持的新题型：${specialQuestionType.displayName}"
        }
        val now = System.currentTimeMillis()
        val blueprint = ExamPaperBlueprint.forYear(year, profile, specialQuestionType)
        val sequence = dao.getMaxComposedPaperSequenceByDay(dayKey) + 1
        val paper = ExamPaper(
            uid = UUID.randomUUID().toString(),
            title = ExamPaperBlueprint.dailyPaperTitle(dayKey, sequence),
            description = "考研${if (profile == ExamPaperProfile.ENGLISH_ONE) "英语一" else "英语二"}自动组卷",
            createdAt = now,
            updatedAt = now,
            paperType = ExamPaperType.COMPOSED,
            status = ExamPaperStatus.COLLECTING,
            dayKey = dayKey,
            dailySequence = sequence,
            profile = profile,
            blueprintVersion = blueprint.version,
            specialQuestionType = specialQuestionType,
            compositionMode = ExamPaperCompositionMode.AUTOMATIC,
            selectionStatus = AutoPaperSelectionStatus.NOT_STARTED
        )
        val paperId = dao.insertExamPaper(paper.toEntity(now))
        if (paperId <= 0L) throw IllegalStateException("自动组卷创建失败")
        blueprint.slots.forEach { slot ->
            dao.upsertExamPaperSlotSelection(
                ExamPaperSlotSelectionEntity(
                    examPaperId = paperId,
                    slotKey = slot.key,
                    questionType = slot.questionType.name,
                    variant = slot.variant.orEmpty(),
                    status = ExamPaperSlotSelectionStatus.PENDING.name,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
        dao.getExamPaperById(paperId)?.toDomain()
            ?: throw IllegalStateException("自动组卷创建后无法读取")
    }

    override suspend fun getLatestAutoPaperByDay(dayKey: String): ExamPaper? =
        dao.getLatestAutoPaperByDay(dayKey)?.toDomain()

    override suspend fun collectArticleForPaper(
        articleId: Long,
        dayKey: String,
        profile: ExamPaperProfile,
        specialQuestionType: QuestionType?,
        questionType: QuestionType,
        variant: String?
    ): ExamPaperCollectionResult {
        val article = articleRepository.getArticleByIdOnce(articleId)
            ?: throw IllegalStateException("文章不存在")
        val year = dayKey.take(4).toIntOrNull()
            ?: throw IllegalArgumentException("组卷日期无效")

        return database.withTransaction {
            val now = System.currentTimeMillis()
            var paperEntity = dao.getCollectingPaperByDay(dayKey, profile.name)
            if (paperEntity == null) {
                val blueprint = ExamPaperBlueprint.forYear(
                    year = year,
                    profile = profile,
                    specialQuestionType = specialQuestionType ?: ExamPaperBlueprint.rotatingSpecialType(year)
                )
                val sequence = dao.getMaxComposedPaperSequenceByDay(dayKey) + 1
                val paper = ExamPaper(
                    uid = UUID.randomUUID().toString(),
                    title = ExamPaperBlueprint.dailyPaperTitle(dayKey, sequence),
                    description = "考研${if (profile == ExamPaperProfile.ENGLISH_ONE) "英语一" else "英语二"}智能组卷",
                    createdAt = now,
                    updatedAt = now,
                    paperType = ExamPaperType.COMPOSED,
                    status = ExamPaperStatus.COLLECTING,
                    dayKey = dayKey,
                    dailySequence = sequence,
                    profile = profile,
                    blueprintVersion = blueprint.version,
                    specialQuestionType = blueprint.specialQuestionType
                )
                val paperId = dao.insertExamPaper(paper.toEntity(now))
                paperEntity = dao.getExamPaperById(paperId)
                    ?: throw IllegalStateException("试卷创建失败")
            }

            if (
                specialQuestionType != null &&
                paperEntity.specialQuestionType != specialQuestionType.name &&
                dao.getExamPaperSourcesOnce(paperEntity.id).none { it.slotKey == "special" }
            ) {
                dao.updateExamPaperSpecialQuestionType(paperEntity.id, specialQuestionType.name, now)
                paperEntity = dao.getExamPaperById(paperEntity.id)
                    ?: throw IllegalStateException("试卷配置更新失败")
            }
            val paper = paperEntity.toDomain()
            val blueprint = ExamPaperBlueprint.forPaper(paper)
            val duplicate = dao.findExamPaperSource(
                paperId = paper.id,
                articleId = articleId,
                questionType = questionType.name,
                variant = variant
            )
            if (duplicate != null) {
                return@withTransaction ExamPaperCollectionResult.Duplicate(paper, duplicate.toDomain())
            }

            val occupied = dao.getExamPaperSourcesOnce(paper.id).mapTo(mutableSetOf()) { it.slotKey }
            val slot = blueprint.nextAvailableSlot(questionType, variant, occupied)
                ?: return@withTransaction ExamPaperCollectionResult.TargetFull(paper, questionType, variant)
            val sourceEntity = ExamPaperSourceEntity(
                uid = UUID.randomUUID().toString(),
                examPaperId = paper.id,
                articleId = article.id,
                articleUid = article.articleUid,
                slotKey = slot.key,
                questionType = slot.questionType.name,
                variant = slot.variant,
                orderInPaper = slot.orderInPaper,
                startQuestionNumber = slot.startQuestionNumber,
                status = ExamPaperSourceStatus.COLLECTED.name,
                createdAt = now,
                updatedAt = now
            )
            val sourceId = dao.insertExamPaperSource(sourceEntity)
            if (sourceId <= 0L) {
                throw IllegalStateException("文章槽位保存冲突，请重试")
            }
            if (paper.compositionMode == ExamPaperCompositionMode.AUTOMATIC) {
                val score = articleRepository.getAdvancedScoresForArticle(article.id)
                    .firstOrNull {
                        it.questionType == slot.questionType && it.variant.orEmpty() == slot.variant.orEmpty()
                    }
                val existingSelection = dao.getExamPaperSlotSelection(paper.id, slot.key)
                dao.upsertExamPaperSlotSelection(
                    ExamPaperSlotSelectionEntity(
                        id = existingSelection?.id ?: 0L,
                        examPaperId = paper.id,
                        slotKey = slot.key,
                        questionType = slot.questionType.name,
                        variant = slot.variant.orEmpty(),
                        status = ExamPaperSlotSelectionStatus.SELECTED.name,
                        articleId = article.id,
                        articleUid = article.articleUid,
                        articleTitle = article.title,
                        selectedScore = score?.score,
                        candidateCount = existingSelection?.candidateCount ?: 0,
                        reason = existingSelection?.reason,
                        createdAt = existingSelection?.createdAt ?: now,
                        updatedAt = now
                    )
                )
            }
            val sources = dao.getExamPaperSourcesOnce(paper.id)
            val ready = blueprint.isReady(sources.mapTo(mutableSetOf()) { it.slotKey })
            if (ready) {
                dao.updateExamPaperStatus(
                    paperId = paper.id,
                    status = ExamPaperStatus.READY.name,
                    error = null,
                    startedAt = null,
                    completedAt = null,
                    updatedAt = now
                )
            }
            val updatedPaper = dao.getExamPaperById(paper.id)?.toDomain() ?: paper
            ExamPaperCollectionResult.Added(
                paper = updatedPaper,
                source = sourceEntity.copy(id = sourceId).toDomain(),
                collectedCount = sources.size,
                requiredCount = blueprint.slots.size,
                becameReady = ready
            )
        }
    }

    override fun getExamPaperSources(paperId: Long): Flow<List<ExamPaperSource>> =
        dao.getExamPaperSources(paperId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getExamPaperSourcesOnce(paperId: Long): List<ExamPaperSource> =
        dao.getExamPaperSourcesOnce(paperId).map { it.toDomain() }

    override suspend fun updateExamPaperStatus(
        paperId: Long,
        status: ExamPaperStatus,
        error: String?,
        startedAt: Long?,
        completedAt: Long?
    ) {
        dao.updateExamPaperStatus(
            paperId = paperId,
            status = status.name,
            error = error,
            startedAt = startedAt,
            completedAt = completedAt,
            updatedAt = System.currentTimeMillis()
        )
    }

    override suspend fun updateExamPaperSourceStatus(
        sourceId: Long,
        status: ExamPaperSourceStatus,
        groupId: Long?,
        error: String?
    ) {
        dao.updateExamPaperSourceStatus(sourceId, status.name, groupId, error, System.currentTimeMillis())
    }

    override fun getExamPaperSlotSelections(paperId: Long): Flow<List<ExamPaperSlotSelection>> =
        dao.getExamPaperSlotSelections(paperId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getExamPaperSlotSelectionsOnce(paperId: Long): List<ExamPaperSlotSelection> =
        dao.getExamPaperSlotSelectionsOnce(paperId).map { it.toDomain() }

    override suspend fun upsertExamPaperSlotSelection(selection: ExamPaperSlotSelection): Long =
        dao.upsertExamPaperSlotSelection(selection.toEntity())

    override suspend fun updateAutoPaperSelectionStatus(
        paperId: Long,
        status: AutoPaperSelectionStatus,
        error: String?,
        startedAt: Long?,
        completedAt: Long?
    ) = dao.updateAutoPaperSelectionStatus(
        paperId = paperId,
        status = status.name,
        error = error,
        startedAt = startedAt,
        completedAt = completedAt,
        updatedAt = System.currentTimeMillis()
    )

    // ── QuestionGroup ──

    override fun getAllGroupsWithPaperTitle(): Flow<List<QuestionGroup>> =
        dao.getAllGroupsWithPaperTitle().map { list -> list.map { it.toDomain() } }

    override fun getGroupsByPaper(paperId: Long): Flow<List<QuestionGroup>> =
        dao.getGroupsByPaper(paperId).map { list -> list.map { it.toDomain() } }

    override suspend fun getGroupById(groupId: Long): QuestionGroup? {
        val entity = dao.getGroupById(groupId) ?: return null
        val paragraphs = getParagraphs(groupId)
        val items = getItemsByGroup(groupId)
        val linkedArticleId = dao.getLinkedArticleId(groupId)
        val paper = dao.getExamPaperById(entity.examPaperId)
        return entity.toDomain().copy(
            paragraphs = paragraphs,
            items = items,
            linkedArticleId = linkedArticleId,
            examPaperTitle = paper?.title
        )
    }

    override suspend fun insertQuestionGroup(group: QuestionGroup): Long {
        val now = System.currentTimeMillis()
        return dao.insertQuestionGroup(group.toEntity(now))
    }

    override suspend fun deleteQuestionGroup(groupId: Long) = dao.deleteQuestionGroup(groupId)

    override suspend fun updateSourceVerification(groupId: Long, status: Int, error: String?) {
        dao.updateSourceVerification(groupId, status, error, System.currentTimeMillis())
    }

    override suspend fun updateSourceUrl(groupId: Long, url: String) {
        dao.updateSourceUrl(groupId, url, System.currentTimeMillis())
    }

    override suspend fun updateSourceMeta(groupId: Long, url: String?, info: String?) {
        dao.updateSourceMeta(groupId, url, info, System.currentTimeMillis())
    }

    override suspend fun markHasAiAnswer(groupId: Long) {
        dao.markHasAiAnswer(groupId, System.currentTimeMillis())
    }

    override suspend fun markHasScannedAnswer(groupId: Long) {
        dao.markHasScannedAnswer(groupId, System.currentTimeMillis())
    }

    // ── Paragraphs ──

    override suspend fun getParagraphs(groupId: Long): List<ArticleParagraph> =
        dao.getParagraphs(groupId).map { it.toDomain(groupId) }

    override suspend fun insertParagraphs(groupId: Long, paragraphs: List<ArticleParagraph>) {
        dao.deleteParagraphs(groupId)
        dao.insertParagraphs(paragraphs.mapIndexed { index, p ->
            QuestionGroupParagraphEntity(
                questionGroupId = groupId,
                paragraphIndex = index,
                text = p.text,
                paragraphType = p.paragraphType.name,
                imageUri = p.imageUri,
                imageUrl = p.imageUrl
            )
        })
    }

    // ── QuestionItem ──

    override suspend fun getItemsByGroup(groupId: Long): List<QuestionItem> =
        dao.getItemsByGroup(groupId).map { it.toDomain() }

    override suspend fun insertQuestionItems(items: List<QuestionItem>) {
        dao.insertQuestionItems(items.mapIndexed { index, item ->
            item.toEntity(index)
        })
    }

    override suspend fun updateAnswer(
        itemId: Long, answer: String, source: String,
        explanation: String?, difficultyLevel: String?, difficultyScore: Float?
    ) {
        dao.updateAnswer(itemId, answer, source, explanation, difficultyLevel, difficultyScore)
    }

    override suspend fun updateWritingSample(
        itemId: Long,
        sampleText: String,
        source: String,
        sampleTitle: String?,
        sampleUrl: String?,
        sampleInfo: String?
    ) {
        dao.updateWritingSample(itemId, sampleText, source, sampleTitle, sampleUrl, sampleInfo)
    }

    override suspend fun incrementWrongCount(itemId: Long) = dao.incrementWrongCount(itemId)

    override suspend fun getWrongItemIds(groupId: Long): List<Long> = dao.getWrongItemIds(groupId)

    override suspend fun updateItemsExtraDataByGroup(groupId: Long, extraData: String?) {
        dao.updateItemsExtraDataByGroup(groupId, extraData)
    }

    // ── PracticeRecord ──

    override suspend fun insertPracticeRecords(records: List<PracticeRecord>) {
        dao.insertPracticeRecords(records.map { it.toEntity() })
    }

    override suspend fun getCorrectCountByGroup(groupId: Long): Int =
        dao.getCorrectCountByGroup(groupId)

    override suspend fun getTotalPracticeCountByGroup(groupId: Long): Int =
        dao.getTotalPracticeCountByGroup(groupId)

    override suspend fun saveExamPaperAnswerDraft(paperId: Long, itemId: Long, answer: String) {
        dao.upsertExamPaperAnswerDraft(
            ExamPaperAnswerDraftEntity(
                examPaperId = paperId,
                questionItemId = itemId,
                userAnswer = answer,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun getExamPaperAnswerDrafts(paperId: Long, groupId: Long): Map<Long, String> =
        dao.getExamPaperAnswerDrafts(paperId, groupId).associate { it.questionItemId to it.userAnswer }

    override suspend fun markExamPaperGroupCompleted(paperId: Long, groupId: Long) {
        val now = System.currentTimeMillis()
        dao.upsertExamPaperPracticeProgress(
            ExamPaperPracticeProgressEntity(paperId, groupId, submittedAt = now, updatedAt = now)
        )
    }

    override suspend fun resetExamPaperGroupProgress(paperId: Long, groupId: Long) {
        database.withTransaction {
            dao.clearExamPaperPracticeProgress(paperId, groupId)
            dao.clearExamPaperAnswerDrafts(paperId, groupId)
        }
    }

    override fun observeCompletedExamPaperGroupIds(paperId: Long): Flow<List<Long>> =
        dao.observeCompletedExamPaperGroupIds(paperId)

    override suspend fun isExamPaperGroupCompleted(paperId: Long, groupId: Long): Boolean =
        dao.isExamPaperGroupCompleted(paperId, groupId)

    // ── SourceArticle ──

    override suspend fun linkSourceArticle(groupId: Long, articleId: Long) {
        dao.deleteSourceArticle(groupId)
        dao.insertSourceArticle(
            QuestionSourceArticleEntity(
                questionGroupId = groupId,
                linkedArticleId = articleId,
                verifiedAt = System.currentTimeMillis()
            )
        )
        articleRepository.updateArticleCategory(articleId, ArticleCategoryDefaults.SOURCE_ID)
    }

    override suspend fun getLinkedArticleId(groupId: Long): Long? =
        dao.getLinkedArticleId(groupId)

    // ── Composite save ──

    override suspend fun saveScannedPaper(
        paper: ExamPaper,
        groups: List<QuestionGroup>
    ): Long = database.withTransaction {
        val now = System.currentTimeMillis()
        val paperId = dao.insertExamPaper(paper.toEntity(now))

        var totalQuestions = 0
        groups.forEachIndexed { groupIndex, group ->
            val groupEntity = QuestionGroupEntity(
                uid = group.uid.ifBlank { UUID.randomUUID().toString() },
                examPaperId = paperId,
                questionType = group.questionType.name,
                sectionLabel = group.sectionLabel,
                orderInPaper = groupIndex,
                directions = group.directions,
                passageText = group.passageText,
                sourceInfo = group.sourceInfo,
                sourceUrl = group.sourceUrl,
                sourceAuthor = group.sourceAuthor,
                sourceVerified = 0,
                wordCount = group.wordCount,
                difficultyLevel = group.difficultyLevel?.name,
                difficultyScore = group.difficultyScore,
                createdAt = now,
                updatedAt = now
            )
            val groupId = dao.insertQuestionGroup(groupEntity)

            // Insert paragraphs
            if (group.paragraphs.isNotEmpty()) {
                dao.insertParagraphs(group.paragraphs.mapIndexed { i, p ->
                    QuestionGroupParagraphEntity(
                        questionGroupId = groupId,
                        paragraphIndex = i,
                        text = p.text,
                        paragraphType = p.paragraphType.name
                    )
                })
            }

            // Insert question items
            if (group.items.isNotEmpty()) {
                dao.insertQuestionItems(group.items.mapIndexed { i, item ->
                    item.copy(questionGroupId = groupId).toEntity(i)
                })
                totalQuestions += group.items.size
            }
        }

        dao.updateTotalQuestions(paperId, totalQuestions, now)
        paperId
    }

    override suspend fun saveGeneratedGroup(
        paperId: Long,
        source: ExamPaperSource,
        group: QuestionGroup
    ): Long {
        val groupId = database.withTransaction {
            val currentSource = dao.getExamPaperSourceById(source.id)
                ?: throw IllegalStateException("组卷来源不存在")
            currentSource.questionGroupId?.let { existingId ->
                if (dao.getGroupById(existingId) != null) return@withTransaction existingId
            }

            val now = System.currentTimeMillis()
            val insertedGroupId = dao.insertQuestionGroup(
                group.copy(examPaperId = paperId, orderInPaper = source.orderInPaper).toEntity(now)
            )
            if (group.paragraphs.isNotEmpty()) {
                dao.insertParagraphs(group.paragraphs.mapIndexed { index, paragraph ->
                    QuestionGroupParagraphEntity(
                        questionGroupId = insertedGroupId,
                        paragraphIndex = index,
                        text = paragraph.text,
                        paragraphType = paragraph.paragraphType.name,
                        imageUri = paragraph.imageUri,
                        imageUrl = paragraph.imageUrl
                    )
                })
            }
            if (group.items.isNotEmpty()) {
                dao.insertQuestionItems(group.items.mapIndexed { index, item ->
                    item.copy(questionGroupId = insertedGroupId).toEntity(index)
                })
            }
            dao.insertSourceArticle(
                QuestionSourceArticleEntity(
                    questionGroupId = insertedGroupId,
                    linkedArticleId = source.articleId,
                    linkedArticleUid = source.articleUid,
                    verifiedAt = now
                )
            )
            dao.updateExamPaperSourceStatus(
                sourceId = source.id,
                status = ExamPaperSourceStatus.GENERATED.name,
                groupId = insertedGroupId,
                error = null,
                updatedAt = now
            )
            dao.updateTotalQuestions(paperId, dao.getItemCountByPaper(paperId), now)
            insertedGroupId
        }
        articleRepository.updateArticleCategory(source.articleId, ArticleCategoryDefaults.SOURCE_ID)
        return groupId
    }

    // ── Mappers ──

    private fun ExamPaperEntity.toDomain() = ExamPaper(
        id = id, uid = uid, title = title, description = description,
        totalQuestions = totalQuestions, createdAt = createdAt, updatedAt = updatedAt,
        paperType = ExamPaperType.entries.find { it.name == paperType } ?: ExamPaperType.IMPORTED,
        status = ExamPaperStatus.entries.find { it.name == status } ?: ExamPaperStatus.READY_TO_PRACTICE,
        dayKey = dayKey,
        dailySequence = dailySequence,
        profile = ExamPaperProfile.entries.find { it.name == profile } ?: ExamPaperProfile.ENGLISH_ONE,
        blueprintVersion = blueprintVersion,
        specialQuestionType = specialQuestionType?.let { name -> QuestionType.entries.find { it.name == name } },
        compositionMode = ExamPaperCompositionMode.entries.find { it.name == compositionMode }
            ?: ExamPaperCompositionMode.MANUAL,
        selectionStatus = AutoPaperSelectionStatus.entries.find { it.name == selectionStatus }
            ?: AutoPaperSelectionStatus.NOT_STARTED,
        selectionError = selectionError,
        selectionStartedAt = selectionStartedAt,
        selectionCompletedAt = selectionCompletedAt,
        generationError = generationError,
        generationStartedAt = generationStartedAt,
        generationCompletedAt = generationCompletedAt
    )

    private fun ExamPaper.toEntity(now: Long) = ExamPaperEntity(
        id = id, uid = uid.ifBlank { UUID.randomUUID().toString() },
        title = title, description = description, totalQuestions = totalQuestions,
        createdAt = if (createdAt == 0L) now else createdAt,
        updatedAt = now,
        paperType = paperType.name,
        status = status.name,
        dayKey = dayKey,
        dailySequence = dailySequence,
        profile = profile.name,
        blueprintVersion = blueprintVersion,
        specialQuestionType = specialQuestionType?.name,
        compositionMode = compositionMode.name,
        selectionStatus = selectionStatus.name,
        selectionError = selectionError,
        selectionStartedAt = selectionStartedAt,
        selectionCompletedAt = selectionCompletedAt,
        generationError = generationError,
        generationStartedAt = generationStartedAt,
        generationCompletedAt = generationCompletedAt
    )

    private fun com.xty.englishhelper.data.local.dao.ExamPaperWithProgress.toDomain() = ExamPaper(
        id = id,
        uid = uid,
        title = title,
        description = description,
        totalQuestions = total_questions,
        createdAt = created_at,
        updatedAt = updated_at,
        paperType = ExamPaperType.entries.find { it.name == paper_type } ?: ExamPaperType.IMPORTED,
        status = ExamPaperStatus.entries.find { it.name == status } ?: ExamPaperStatus.READY_TO_PRACTICE,
        dayKey = day_key,
        dailySequence = daily_sequence,
        profile = ExamPaperProfile.entries.find { it.name == profile } ?: ExamPaperProfile.ENGLISH_ONE,
        blueprintVersion = blueprint_version,
        specialQuestionType = special_question_type?.let { name -> QuestionType.entries.find { it.name == name } },
        compositionMode = ExamPaperCompositionMode.entries.find { it.name == composition_mode }
            ?: ExamPaperCompositionMode.MANUAL,
        selectionStatus = AutoPaperSelectionStatus.entries.find { it.name == selection_status }
            ?: AutoPaperSelectionStatus.NOT_STARTED,
        selectionError = selection_error,
        selectionStartedAt = selection_started_at,
        selectionCompletedAt = selection_completed_at,
        generationError = generation_error,
        generationStartedAt = generation_started_at,
        generationCompletedAt = generation_completed_at
    )

    private fun ExamPaperSourceEntity.toDomain() = ExamPaperSource(
        id = id,
        uid = uid,
        examPaperId = examPaperId,
        articleId = articleId,
        articleUid = articleUid,
        slotKey = slotKey,
        questionType = QuestionType.entries.find { it.name == questionType } ?: QuestionType.NEW_TYPE,
        variant = variant,
        orderInPaper = orderInPaper,
        startQuestionNumber = startQuestionNumber,
        status = ExamPaperSourceStatus.entries.find { it.name == status } ?: ExamPaperSourceStatus.COLLECTED,
        questionGroupId = questionGroupId,
        errorMessage = errorMessage,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun ExamPaperSlotSelectionEntity.toDomain() = ExamPaperSlotSelection(
        id = id,
        examPaperId = examPaperId,
        slotKey = slotKey,
        questionType = QuestionType.entries.find { it.name == questionType } ?: QuestionType.NEW_TYPE,
        variant = variant.takeIf { it.isNotBlank() },
        status = ExamPaperSlotSelectionStatus.entries.find { it.name == status }
            ?: ExamPaperSlotSelectionStatus.PENDING,
        articleId = articleId,
        articleUid = articleUid,
        articleTitle = articleTitle,
        selectedScore = selectedScore,
        candidateCount = candidateCount,
        reason = reason,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun ExamPaperSlotSelection.toEntity() = ExamPaperSlotSelectionEntity(
        id = id,
        examPaperId = examPaperId,
        slotKey = slotKey,
        questionType = questionType.name,
        variant = variant.orEmpty(),
        status = status.name,
        articleId = articleId,
        articleUid = articleUid,
        articleTitle = articleTitle,
        selectedScore = selectedScore,
        candidateCount = candidateCount,
        reason = reason,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun QuestionGroupEntity.toDomain() = QuestionGroup(
        id = id, uid = uid, examPaperId = examPaperId,
        questionType = QuestionType.entries.find { it.name == questionType } ?: QuestionType.READING_COMPREHENSION,
        sectionLabel = sectionLabel, orderInPaper = orderInPaper,
        directions = directions, passageText = passageText,
        sourceInfo = sourceInfo, sourceUrl = sourceUrl, sourceAuthor = sourceAuthor,
        sourceVerified = when (sourceVerified) { 1 -> SourceVerifyStatus.VERIFIED; -1 -> SourceVerifyStatus.FAILED; else -> SourceVerifyStatus.UNVERIFIED },
        sourceVerifyError = sourceVerifyError,
        wordCount = wordCount,
        difficultyLevel = DifficultyLevel.entries.find { it.name == difficultyLevel },
        difficultyScore = difficultyScore,
        hasAiAnswer = hasAiAnswer != 0, hasScannedAnswer = hasScannedAnswer != 0,
        createdAt = createdAt, updatedAt = updatedAt
    )

    private fun com.xty.englishhelper.data.local.dao.QuestionGroupWithPaperTitle.toDomain() = QuestionGroup(
        id = id, uid = uid, examPaperId = exam_paper_id,
        questionType = QuestionType.entries.find { it.name == question_type } ?: QuestionType.READING_COMPREHENSION,
        sectionLabel = section_label, orderInPaper = order_in_paper,
        directions = directions, passageText = passage_text,
        sourceInfo = source_info, sourceUrl = source_url, sourceAuthor = source_author,
        sourceVerified = when (source_verified) { 1 -> SourceVerifyStatus.VERIFIED; -1 -> SourceVerifyStatus.FAILED; else -> SourceVerifyStatus.UNVERIFIED },
        sourceVerifyError = source_verify_error,
        wordCount = word_count,
        difficultyLevel = DifficultyLevel.entries.find { it.name == difficulty_level },
        difficultyScore = difficulty_score,
        hasAiAnswer = has_ai_answer != 0, hasScannedAnswer = has_scanned_answer != 0,
        createdAt = created_at, updatedAt = updated_at,
        examPaperTitle = exam_paper_title
    )

    private fun QuestionGroup.toEntity(now: Long) = QuestionGroupEntity(
        id = id, uid = uid.ifBlank { UUID.randomUUID().toString() },
        examPaperId = examPaperId, questionType = questionType.name,
        sectionLabel = sectionLabel, orderInPaper = orderInPaper,
        directions = directions, passageText = passageText,
        sourceInfo = sourceInfo, sourceUrl = sourceUrl, sourceAuthor = sourceAuthor,
        sourceVerified = when (sourceVerified) { SourceVerifyStatus.VERIFIED -> 1; SourceVerifyStatus.FAILED -> -1; else -> 0 },
        sourceVerifyError = sourceVerifyError,
        wordCount = wordCount,
        difficultyLevel = difficultyLevel?.name, difficultyScore = difficultyScore,
        hasAiAnswer = if (hasAiAnswer) 1 else 0, hasScannedAnswer = if (hasScannedAnswer) 1 else 0,
        createdAt = if (createdAt == 0L) now else createdAt, updatedAt = now
    )

    private fun QuestionGroupParagraphEntity.toDomain(groupId: Long) = ArticleParagraph(
        id = id, articleId = groupId, paragraphIndex = paragraphIndex,
        text = text,
        paragraphType = ParagraphType.entries.find { it.name == paragraphType } ?: ParagraphType.TEXT,
        imageUri = imageUri, imageUrl = imageUrl
    )

    private fun QuestionItemEntity.toDomain() = QuestionItem(
        id = id, questionGroupId = questionGroupId,
        questionNumber = questionNumber, questionText = questionText,
        optionA = optionA, optionB = optionB, optionC = optionC, optionD = optionD,
        correctAnswer = correctAnswer,
        answerSource = AnswerSource.entries.find { it.name == answerSource } ?: AnswerSource.NONE,
        explanation = explanation, orderInGroup = orderInGroup,
        wordCount = wordCount,
        difficultyLevel = DifficultyLevel.entries.find { it.name == difficultyLevel },
        difficultyScore = difficultyScore,
        wrongCount = wrongCount,
        extraData = extraData,
        sampleSourceTitle = sampleSourceTitle,
        sampleSourceUrl = sampleSourceUrl,
        sampleSourceInfo = sampleSourceInfo
    )

    private fun QuestionItem.toEntity(index: Int) = QuestionItemEntity(
        id = id, questionGroupId = questionGroupId,
        questionNumber = questionNumber, questionText = questionText,
        optionA = optionA, optionB = optionB, optionC = optionC, optionD = optionD,
        correctAnswer = correctAnswer, answerSource = answerSource.name,
        explanation = explanation, orderInGroup = index,
        wordCount = wordCount,
        difficultyLevel = difficultyLevel?.name, difficultyScore = difficultyScore,
        wrongCount = wrongCount,
        extraData = extraData,
        sampleSourceTitle = sampleSourceTitle,
        sampleSourceUrl = sampleSourceUrl,
        sampleSourceInfo = sampleSourceInfo
    )

    private fun PracticeRecord.toEntity() = PracticeRecordEntity(
        id = id, questionItemId = questionItemId,
        userAnswer = userAnswer, isCorrect = if (isCorrect) 1 else 0,
        practicedAt = practicedAt
    )
}
