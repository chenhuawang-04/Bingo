package com.xty.englishhelper.data.repository

import android.os.Build
import android.util.Base64
import com.squareup.moshi.Moshi
import com.xty.englishhelper.BuildConfig
import com.xty.englishhelper.data.json.ArticleCategoriesExportModel
import com.xty.englishhelper.data.json.ArticleCategoryJsonModel
import com.xty.englishhelper.data.json.ArticleImageJsonModel
import com.xty.englishhelper.data.json.ArticleJsonModel
import com.xty.englishhelper.data.json.ArticleParagraphJsonModel
import com.xty.englishhelper.data.json.ArticlesExportModel
import com.xty.englishhelper.data.json.DictionaryJsonModel
import com.xty.englishhelper.data.json.ExamPaperJson
import com.xty.englishhelper.data.json.InflectionJsonModel
import com.xty.englishhelper.data.json.ParagraphJson
import com.xty.englishhelper.data.json.PracticeRecordJson
import com.xty.englishhelper.data.json.QuestionBankExportModel
import com.xty.englishhelper.data.json.QuestionGroupJson
import com.xty.englishhelper.data.json.QuestionItemJson
import com.xty.englishhelper.data.json.StudyStateJsonModel
import com.xty.englishhelper.data.json.SyncManifest
import com.xty.englishhelper.data.json.UnitJsonModel
import com.xty.englishhelper.data.json.WordExampleJsonModel
import com.xty.englishhelper.data.json.WordExamplesExportModel
import com.xty.englishhelper.data.json.WordPoolJsonModel
import com.xty.englishhelper.data.local.dao.ArticleDao
import com.xty.englishhelper.data.local.dao.QuestionBankDao
import com.xty.englishhelper.data.local.dao.WordPoolDao
import com.xty.englishhelper.data.local.entity.ExamPaperEntity
import com.xty.englishhelper.data.local.entity.PracticeRecordEntity
import com.xty.englishhelper.data.local.entity.QuestionGroupEntity
import com.xty.englishhelper.data.local.entity.QuestionGroupParagraphEntity
import com.xty.englishhelper.data.local.entity.QuestionItemEntity
import com.xty.englishhelper.data.local.entity.QuestionSourceArticleEntity
import com.xty.englishhelper.data.local.entity.WordPoolEntity
import com.xty.englishhelper.data.local.entity.WordPoolMemberEntity
import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.data.remote.GitHubApiService
import com.xty.englishhelper.data.remote.dto.GitHubPutRequest
import com.xty.englishhelper.domain.model.Article
import com.xty.englishhelper.domain.model.ArticleCategory
import com.xty.englishhelper.domain.model.ArticleCategoryDefaults
import com.xty.englishhelper.domain.model.ArticleParseStatus
import com.xty.englishhelper.domain.model.ArticleSourceType
import com.xty.englishhelper.domain.model.ArticleSourceTypeV2
import com.xty.englishhelper.domain.model.Dictionary
import com.xty.englishhelper.domain.model.StudyUnit
import com.xty.englishhelper.domain.model.WordStudyState
import com.xty.englishhelper.domain.repository.ArticleRepository
import com.xty.englishhelper.domain.repository.WordExample
import com.xty.englishhelper.domain.repository.CloudSyncRepository
import com.xty.englishhelper.domain.repository.DictionaryImportExporter
import com.xty.englishhelper.domain.repository.DictionaryRepository
import com.xty.englishhelper.domain.repository.StudyRepository
import com.xty.englishhelper.domain.repository.SyncProgress
import com.xty.englishhelper.domain.repository.TransactionRunner
import com.xty.englishhelper.domain.repository.UnitRepository
import com.xty.englishhelper.domain.repository.WordPoolRepository
import com.xty.englishhelper.domain.repository.WordRepository
import com.xty.englishhelper.domain.usecase.importexport.ExportDictionaryUseCase
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubSyncRepositoryImpl @Inject constructor(
    private val gitHubApi: GitHubApiService,
    private val settingsDataStore: SettingsDataStore,
    private val dictionaryRepository: DictionaryRepository,
    private val wordRepository: WordRepository,
    private val unitRepository: UnitRepository,
    private val studyRepository: StudyRepository,
    private val articleRepository: ArticleRepository,
    private val articleDao: ArticleDao,
    private val wordPoolRepository: WordPoolRepository,
    private val wordPoolDao: WordPoolDao,
    private val questionBankDao: QuestionBankDao,
    private val importExporter: DictionaryImportExporter,
    private val exportDictionary: ExportDictionaryUseCase,
    private val transactionRunner: TransactionRunner,
    private val moshi: Moshi
) : CloudSyncRepository {

    private val dictAdapter = moshi.adapter(DictionaryJsonModel::class.java).indent("  ")
    private val articlesAdapter = moshi.adapter(ArticlesExportModel::class.java).indent("  ")
    private val manifestAdapter = moshi.adapter(SyncManifest::class.java).indent("  ")
    private val questionBankAdapter = moshi.adapter(QuestionBankExportModel::class.java).indent("  ")
    private val articleCategoriesAdapter = moshi.adapter(ArticleCategoriesExportModel::class.java).indent("  ")
    private val wordExamplesAdapter = moshi.adapter(WordExamplesExportModel::class.java).indent("  ")

    private fun authHeader(): String {
        val pat = settingsDataStore.getGitHubPat()
        if (pat.isBlank()) throw IllegalStateException("未配置 GitHub Token")
        return "Bearer $pat"
    }

    private suspend fun owner(): String {
        val o = settingsDataStore.githubOwner.first()
        if (o.isBlank()) throw IllegalStateException("未配置 GitHub 用户名")
        return o
    }

    private suspend fun repo(): String {
        val r = settingsDataStore.githubRepo.first()
        if (r.isBlank()) throw IllegalStateException("未配置仓库名")
        return r
    }

    // ── Public API ──

    override suspend fun testConnection(): Boolean {
        val response = gitHubApi.getRepo(authHeader(), owner(), repo())
        return response.isSuccessful
    }

    override suspend fun getCloudManifest(): SyncManifest? {
        return downloadJson("manifest.json", manifestAdapter)
    }

    override suspend fun sync(onProgress: (SyncProgress) -> Unit) {
        val auth = authHeader()
        val owner = owner()
        val repo = repo()

        // 1. Download cloud data
        onProgress(SyncProgress("下载中", "正在下载云端数据...", 0, 4))
        val cloudManifest = downloadJson("manifest.json", manifestAdapter)
        val cloudDicts = mutableMapOf<String, DictionaryJsonModel>()
        if (cloudManifest != null) {
            cloudManifest.dictionaries.forEachIndexed { i, fileName ->
                onProgress(SyncProgress("下载中", "正在下载 $fileName", i + 1, cloudManifest.dictionaries.size + 1))
                val dict = downloadJson("dictionaries/$fileName", dictAdapter)
                if (dict != null) cloudDicts[dict.name] = dict
            }
        }
        val cloudArticles = downloadJson("articles.json", articlesAdapter)
        val cloudCategories = downloadJson("article_categories.json", articleCategoriesAdapter)
        val cloudWordExamples = downloadJson("word_examples.json", wordExamplesAdapter)

        // 2. Read local data
        onProgress(SyncProgress("读取中", "正在读取本地数据...", 1, 4))
        val localDicts = dictionaryRepository.getAllDictionaries().first()
        applyCloudCategoriesOnSync(cloudCategories)
        val localArticles = articleRepository.getAllArticles().first()

        // 3. Smart merge
        onProgress(SyncProgress("合并中", "正在合并辞书...", 2, 4))
        val mergedDictJsons = mutableListOf<DictionaryJsonModel>()

        for (localDict in localDicts) {
            val localJson = exportLocalDictionary(localDict)
            val cloudJson = cloudDicts.remove(localDict.name)

            if (cloudJson == null) {
                // Only local → upload as-is
                mergedDictJsons.add(localJson)
            } else {
                // Both exist → merge
                val merged = mergeDictionaries(localDict, localJson, cloudJson)
                mergedDictJsons.add(merged)
            }
        }

        // Cloud-only dicts → import to local
        for ((_, cloudDict) in cloudDicts) {
            onProgress(SyncProgress("合并中", "正在导入 ${cloudDict.name}...", 2, 4))
            importCloudDictionary(cloudDict)
            mergedDictJsons.add(cloudDict)
        }

        // Merge articles
        val mergedArticles = mergeArticles(localArticles, cloudArticles)

        // Merge question bank (after articles, so source links can resolve)
        val cloudQuestionBank = downloadJson("questionbank.json", questionBankAdapter)
        val articleUidToId = buildArticleUidToIdMap()
        mergeQuestionBank(cloudQuestionBank, articleUidToId)
        val wordUidToId = buildWordUidToIdMap()
        importWordExamples(cloudWordExamples, wordUidToId, articleUidToId)

        // Re-export local dicts that were merged (to pick up cloud-only words)
        val finalDictJsons = mutableListOf<DictionaryJsonModel>()
        val allLocalDicts = dictionaryRepository.getAllDictionaries().first()
        for (dict in allLocalDicts) {
            finalDictJsons.add(exportLocalDictionary(dict))
        }

        // 4. Upload merged snapshot
        onProgress(SyncProgress("上传中", "正在上传数据...", 3, 4))
        val fileNames = mutableListOf<String>()
        for (dictJson in finalDictJsons) {
            val fileName = dictFileName(dictJson.name)
            fileNames.add(fileName)
            uploadJson("dictionaries/$fileName", dictJson, dictAdapter)
        }

        // Upload articles
        val allArticles = articleRepository.getAllArticles().first()
        val articleJsons = mutableListOf<ArticleJsonModel>()
        for (article in allArticles) {
            articleJsons.add(exportArticleJson(article))
        }
        val articlesExport = ArticlesExportModel(
            schemaVersion = 2,
            articles = articleJsons
        )
        uploadJson("articles.json", articlesExport, articlesAdapter)

        // Upload article categories
        val categoriesExport = exportArticleCategories()
        uploadJson("article_categories.json", categoriesExport, articleCategoriesAdapter)

        // Upload question bank
        val questionBankExport = exportQuestionBank()
        uploadJson("questionbank.json", questionBankExport, questionBankAdapter)

        // Upload word examples
        val wordExamplesExport = exportWordExamples()
        uploadJson("word_examples.json", wordExamplesExport, wordExamplesAdapter)

        // Upload manifest
        val manifest = SyncManifest(
            appVersion = BuildConfig.VERSION_NAME,
            schemaVersion = 5,
            syncedAt = System.currentTimeMillis(),
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
            dictionaries = fileNames,
            hasArticles = allArticles.isNotEmpty(),
            hasQuestionBank = questionBankExport.papers.isNotEmpty(),
            hasWordExamples = wordExamplesExport.examples.isNotEmpty()
        )
        uploadJson("manifest.json", manifest, manifestAdapter)

        settingsDataStore.setLastSyncAt(System.currentTimeMillis())
        onProgress(SyncProgress("完成", "同步完成", 4, 4))
    }

    override suspend fun forceUpload(onProgress: (SyncProgress) -> Unit) {
        onProgress(SyncProgress("上传中", "正在导出本地数据...", 0, 3))

        val localDicts = dictionaryRepository.getAllDictionaries().first()
        val fileNames = mutableListOf<String>()

        localDicts.forEachIndexed { i, dict ->
            onProgress(SyncProgress("上传中", "正在上传 ${dict.name}...", i + 1, localDicts.size + 2))
            val json = exportLocalDictionary(dict)
            val fileName = dictFileName(dict.name)
            fileNames.add(fileName)
            uploadJson("dictionaries/$fileName", json, dictAdapter)
        }

        // Upload articles
        val allArticles = articleRepository.getAllArticles().first()
        val articleJsons = mutableListOf<ArticleJsonModel>()
        for (article in allArticles) {
            articleJsons.add(exportArticleJson(article))
        }
        val articlesExport = ArticlesExportModel(
            schemaVersion = 2,
            articles = articleJsons
        )
        uploadJson("articles.json", articlesExport, articlesAdapter)

        // Upload article categories
        val categoriesExport = exportArticleCategories()
        uploadJson("article_categories.json", categoriesExport, articleCategoriesAdapter)

        // Upload question bank
        val questionBankExport = exportQuestionBank()
        uploadJson("questionbank.json", questionBankExport, questionBankAdapter)

        // Upload word examples
        val wordExamplesExport = exportWordExamples()
        uploadJson("word_examples.json", wordExamplesExport, wordExamplesAdapter)

        // Upload manifest
        val manifest = SyncManifest(
            appVersion = BuildConfig.VERSION_NAME,
            schemaVersion = 5,
            syncedAt = System.currentTimeMillis(),
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
            dictionaries = fileNames,
            hasArticles = allArticles.isNotEmpty(),
            hasQuestionBank = questionBankExport.papers.isNotEmpty(),
            hasWordExamples = wordExamplesExport.examples.isNotEmpty()
        )
        uploadJson("manifest.json", manifest, manifestAdapter)

        settingsDataStore.setLastSyncAt(System.currentTimeMillis())
        onProgress(SyncProgress("完成", "上传完成", 3, 3))
    }

    override suspend fun forceDownload(onProgress: (SyncProgress) -> Unit) {
        onProgress(SyncProgress("下载中", "正在下载云端数据...", 0, 3))

        val cloudManifest = downloadJson("manifest.json", manifestAdapter)
            ?: throw IllegalStateException("云端没有同步数据")

        // Download all dicts
        val cloudDicts = mutableListOf<DictionaryJsonModel>()
        cloudManifest.dictionaries.forEachIndexed { i, fileName ->
            onProgress(SyncProgress("下载中", "正在下载 $fileName", i + 1, cloudManifest.dictionaries.size + 1))
            val dict = downloadJson("dictionaries/$fileName", dictAdapter)
            if (dict != null) cloudDicts.add(dict)
        }

        val cloudArticles = downloadJson("articles.json", articlesAdapter)
        val cloudCategories = downloadJson("article_categories.json", articleCategoriesAdapter)
        val cloudQuestionBank = downloadJson("questionbank.json", questionBankAdapter)
        val cloudWordExamples = downloadJson("word_examples.json", wordExamplesAdapter)

        // Clear local data
        onProgress(SyncProgress("导入中", "正在清空本地数据...", 1, 3))
        val localDicts = dictionaryRepository.getAllDictionaries().first()
        for (dict in localDicts) {
            dictionaryRepository.deleteDictionary(dict.id)
        }
        val localArticles = articleRepository.getAllArticles().first()
        for (article in localArticles) {
            articleRepository.deleteArticle(article.id)
        }
        // Clear local question bank (CASCADE deletes child tables)
        val localPapers = questionBankDao.getAllExamPapers().first()
        for (paper in localPapers) {
            questionBankDao.deleteExamPaper(paper.id)
        }

        replaceCategoriesFromCloud(cloudCategories)

        // Import cloud data
        onProgress(SyncProgress("导入中", "正在导入辞书...", 2, 3))
        for (dict in cloudDicts) {
            importCloudDictionary(dict)
        }

        // Import articles
        if (cloudArticles != null) {
            for (articleJson in cloudArticles.articles) {
                importArticleFromJson(articleJson, null, supportsStructuredContent = (cloudArticles?.schemaVersion ?: 1) >= 2)
            }
        }
        normalizeArticleCategories()

        // Import question bank
        if (cloudQuestionBank != null && cloudQuestionBank.papers.isNotEmpty()) {
            val articleUidToId = buildArticleUidToIdMap()
            importQuestionBank(cloudQuestionBank, articleUidToId)
        }

        val wordUidToId = buildWordUidToIdMap()
        val articleUidToId = buildArticleUidToIdMap()
        importWordExamples(cloudWordExamples, wordUidToId, articleUidToId)

        settingsDataStore.setLastSyncAt(System.currentTimeMillis())
        onProgress(SyncProgress("完成", "下载完成", 3, 3))
    }

    // ── Merge Logic ──

    private suspend fun mergeDictionaries(
        localDict: Dictionary,
        localJson: DictionaryJsonModel,
        cloudJson: DictionaryJsonModel
    ): DictionaryJsonModel {
        val localWordsByUid = localJson.words.filter { it.wordUid.isNotBlank() }.associateBy { it.wordUid }
        val cloudWordsByUid = cloudJson.words.filter { it.wordUid.isNotBlank() }.associateBy { it.wordUid }
        val allUids = localWordsByUid.keys + cloudWordsByUid.keys

        // Find cloud-only words and import them to local DB
        val cloudOnlyWords = allUids.filter { it !in localWordsByUid.keys }.mapNotNull { cloudWordsByUid[it] }
        if (cloudOnlyWords.isNotEmpty()) {
            val importResult = importExporter.importFromJson(
                dictAdapter.toJson(cloudJson.copy(
                    words = cloudOnlyWords,
                    units = emptyList(),
                    studyStates = cloudJson.studyStates.filter { state ->
                        cloudOnlyWords.any { it.wordUid == state.wordUid }
                    },
                    wordPools = emptyList()
                ))
            )
            transactionRunner.runInTransaction {
                importResult.words.forEach { word ->
                    val existing = wordRepository.findByNormalizedSpelling(localDict.id, word.normalizedSpelling)
                    if (existing == null) {
                        val wordWithDict = word.copy(
                            dictionaryId = localDict.id,
                            wordUid = word.wordUid.ifBlank { UUID.randomUUID().toString() }
                        )
                        val wordId = wordRepository.insertWord(wordWithDict)
                        // Import study state if available
                        val stateData = importResult.studyStates.find { it.wordUid == word.wordUid }
                        if (stateData != null) {
                            studyRepository.upsertStudyState(
                                WordStudyState(
                                    wordId = wordId,
                                    state = stateData.state,
                                    step = stateData.step,
                                    stability = stateData.stability,
                                    difficulty = stateData.difficulty,
                                    due = stateData.due,
                                    lastReviewAt = stateData.lastReviewAt,
                                    reps = stateData.reps,
                                    lapses = stateData.lapses
                                )
                            )
                        }
                    }
                }
                dictionaryRepository.updateWordCount(localDict.id)
            }
        }

        // For words that exist on both sides, update local if cloud is newer
        // We use studyStates' lastReviewAt as a proxy for "updatedAt" for study state merging
        val cloudStudyByUid = cloudJson.studyStates.associateBy { it.wordUid }
        val localStudyByUid = localJson.studyStates.associateBy { it.wordUid }
        val allStudyUids = cloudStudyByUid.keys + localStudyByUid.keys

        // Get local word mapping for study state updates
        val localWords = wordRepository.getWordsByDictionary(localDict.id).first()
        val wordUidToId = localWords.associate { it.wordUid to it.id }

        if (cloudJson.wordPools.isNotEmpty()) {
            val localPoolCount = wordPoolRepository.getPoolCount(localDict.id)
            if (localPoolCount == 0) {
                transactionRunner.runInTransaction {
                    importWordPools(localDict.id, cloudJson.wordPools, wordUidToId)
                }
            }
        }

        for (uid in allStudyUids) {
            val cloud = cloudStudyByUid[uid]
            val local = localStudyByUid[uid]
            val wordId = wordUidToId[uid] ?: continue

            if (local == null && cloud != null) {
                // Cloud-only study state
                studyRepository.upsertStudyState(
                    WordStudyState(
                        wordId = wordId,
                        state = cloud.state,
                        step = cloud.step,
                        stability = cloud.stability,
                        difficulty = cloud.difficulty,
                        due = cloud.due,
                        lastReviewAt = cloud.lastReviewAt,
                        reps = cloud.reps,
                        lapses = cloud.lapses
                    )
                )
            } else if (local != null && cloud != null) {
                // Both exist → keep the one with more recent lastReviewAt
                if (cloud.lastReviewAt > local.lastReviewAt) {
                    studyRepository.upsertStudyState(
                        WordStudyState(
                            wordId = wordId,
                            state = cloud.state,
                            step = cloud.step,
                            stability = cloud.stability,
                            difficulty = cloud.difficulty,
                            due = cloud.due,
                            lastReviewAt = cloud.lastReviewAt,
                            reps = cloud.reps,
                            lapses = cloud.lapses
                        )
                    )
                }
            }
        }

        // Merge units by name
        val cloudUnitsByName = cloudJson.units.associateBy { it.name }
        val localUnits = unitRepository.getUnitsByDictionary(localDict.id)
        val localUnitNames = localUnits.map { it.name }.toSet()

        for ((name, cloudUnit) in cloudUnitsByName) {
            if (name !in localUnitNames) {
                // Cloud-only unit → import
                val unitId = unitRepository.insertUnit(
                    StudyUnit(dictionaryId = localDict.id, name = name, defaultRepeatCount = cloudUnit.repeatCount)
                )
                val wordIds = cloudUnit.wordUids.mapNotNull { wordUidToId[it] }
                if (wordIds.isNotEmpty()) {
                    unitRepository.addWordsToUnit(unitId, wordIds)
                }
            } else {
                // Both exist → merge member wordUids (union)
                val localUnit = localUnits.find { it.name == name } ?: continue
                val existingWordIds = unitRepository.getWordIdsInUnit(localUnit.id).toSet()
                val cloudWordIds = cloudUnit.wordUids.mapNotNull { wordUidToId[it] }
                val newWordIds = cloudWordIds.filter { it !in existingWordIds }
                if (newWordIds.isNotEmpty()) {
                    unitRepository.addWordsToUnit(localUnit.id, newWordIds)
                }
            }
        }

        // Return merged JSON (will be re-exported from local DB later)
        return localJson
    }

    private suspend fun mergeArticles(
        localArticles: List<Article>,
        cloudArticlesModel: ArticlesExportModel?
    ): List<Article> {
        if (cloudArticlesModel == null) return localArticles

        val localByUid = localArticles.filter { it.articleUid.isNotBlank() }.associateBy { it.articleUid }
        val cloudByUid = cloudArticlesModel.articles.filter { it.articleUid.isNotBlank() }.associateBy { it.articleUid }
        val allUids = localByUid.keys + cloudByUid.keys

        for (uid in allUids) {
            val local = localByUid[uid]
            val cloud = cloudByUid[uid]

            if (local == null && cloud != null) {
                // Cloud-only → import
                importArticleFromJson(cloud, null, supportsStructuredContent = (cloudArticlesModel.schemaVersion >= 2))
            } else if (local != null && cloud != null) {
                // Both exist → keep newer
                if (cloud.updatedAt > local.updatedAt) {
                    importArticleFromJson(cloud, local, supportsStructuredContent = (cloudArticlesModel.schemaVersion >= 2))
                }
            }
            // local-only → no action needed, will be uploaded
        }

        return articleRepository.getAllArticles().first()
    }

    private suspend fun applyCloudCategoriesOnSync(cloudCategories: ArticleCategoriesExportModel?) {
        if (cloudCategories == null || cloudCategories.categories.isEmpty()) {
            articleRepository.ensureDefaultCategories()
            return
        }

        val localCategories = articleRepository.getArticleCategories().first()
        val localIdToName = localCategories.associate { it.id to it.name }

        articleRepository.replaceCategories(cloudCategories.categories.map { it.toDomain() })
        articleRepository.ensureDefaultCategories()

        val updatedCategories = articleRepository.getArticleCategories().first()
        val nameToId = updatedCategories.associate { it.name to it.id }
        val defaultId = nameToId[ArticleCategoryDefaults.DEFAULT_NAME] ?: ArticleCategoryDefaults.DEFAULT_ID
        val validIds = updatedCategories.map { it.id }.toSet()

        val articles = articleRepository.getAllArticles().first()
        for (article in articles) {
            val oldName = localIdToName[article.categoryId]
            val targetId = when {
                validIds.contains(article.categoryId) -> article.categoryId
                oldName != null -> nameToId[oldName] ?: defaultId
                else -> defaultId
            }
            if (article.categoryId != targetId) {
                updateArticleCategoryPreservingUpdatedAt(article, targetId)
            }
        }
    }

    private suspend fun replaceCategoriesFromCloud(cloudCategories: ArticleCategoriesExportModel?) {
        if (cloudCategories == null || cloudCategories.categories.isEmpty()) {
            articleRepository.replaceCategories(emptyList())
            articleRepository.ensureDefaultCategories()
            return
        }
        articleRepository.replaceCategories(cloudCategories.categories.map { it.toDomain() })
        articleRepository.ensureDefaultCategories()
    }

    private suspend fun normalizeArticleCategories() {
        val categories = articleRepository.getArticleCategories().first()
        if (categories.isEmpty()) return
        val validIds = categories.map { it.id }.toSet()
        val defaultId = categories.firstOrNull { it.id == ArticleCategoryDefaults.DEFAULT_ID }?.id
            ?: categories.first().id
        val articles = articleRepository.getAllArticles().first()
        for (article in articles) {
            if (!validIds.contains(article.categoryId)) {
                updateArticleCategoryPreservingUpdatedAt(article, defaultId)
            }
        }
    }

    private suspend fun updateArticleCategoryPreservingUpdatedAt(article: Article, targetId: Long) {
        if (article.categoryId == targetId) return
        articleRepository.upsertArticle(article.copy(categoryId = targetId, updatedAt = article.updatedAt))
    }

    private fun dictFileName(name: String): String {
        val slug = name.replace(Regex("[^\\w\\u4e00-\\u9fff]+"), "_").trim { it == '_' }
        val safeSlug = if (slug.isBlank()) "dict" else slug
        val hash = shortHash(name)
        return "${safeSlug}__${hash}.json"
    }

    private fun shortHash(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
        return digest.take(4).joinToString("") { "%02x".format(it) }
    }

    // ── Export Helpers ──

    private suspend fun exportLocalDictionary(dict: Dictionary): DictionaryJsonModel {
        val json = exportDictionary(dict.id, dict.name, dict.description)
        val model = dictAdapter.fromJson(json) ?: throw IllegalStateException("Failed to parse exported dict")

        // Enrich with word pools
        val words = wordRepository.getWordsByDictionary(dict.id).first()
        val wordIdToUid = words.associate { it.id to it.wordUid }

        val poolToMembers = wordPoolRepository.getPoolToMembersMap(dict.id)
        val poolEntities = wordPoolDao.getPoolsByDictionary(dict.id).associateBy { it.id }

        val wordPools = mutableListOf<WordPoolJsonModel>()
        for ((poolId, memberIds) in poolToMembers) {
            val memberUids = memberIds.mapNotNull { wordIdToUid[it] }
            val poolEntity = poolEntities[poolId]
            val strategy = poolEntity?.strategy ?: "BALANCED"
            val algorithmVersion = poolEntity?.algorithmVersion ?: "${strategy}_v1"
            val focusUid = poolEntity?.focusWordId?.let { wordIdToUid[it] }
            wordPools.add(
                WordPoolJsonModel(
                    focusWordUid = focusUid,
                    memberWordUids = memberUids,
                    strategy = strategy,
                    algorithmVersion = algorithmVersion
                )
            )
        }

        return model.copy(wordPools = wordPools)
    }

    private suspend fun exportArticleCategories(): ArticleCategoriesExportModel {
        articleRepository.ensureDefaultCategories()
        val categories = articleRepository.getArticleCategories().first()
        val now = System.currentTimeMillis()
        return ArticleCategoriesExportModel(
            schemaVersion = 1,
            categories = categories.map { it.toCategoryJson(now) }
        )
    }

    private suspend fun exportArticleJson(article: Article): ArticleJsonModel {
        val paragraphs = articleRepository.getParagraphs(article.id)
        val paragraphJsons = paragraphs.map { p ->
            ArticleParagraphJsonModel(
                paragraphIndex = p.paragraphIndex,
                text = p.text,
                paragraphType = p.paragraphType.name,
                imageUri = p.imageUri,
                imageUrl = p.imageUrl
            )
        }
        val images = articleRepository.getImages(article.id)
        val imageJsons = images.mapIndexed { index, uri ->
            ArticleImageJsonModel(
                localUri = uri,
                orderIndex = index,
                imageUrl = guessImageUrl(uri)
            )
        }
        return article.toArticleJson(paragraphJsons, imageJsons)
    }

    private suspend fun importArticleFromJson(
        articleJson: ArticleJsonModel,
        existing: Article?,
        supportsStructuredContent: Boolean
    ): Long {
        val sourceType = runCatching { ArticleSourceType.valueOf(articleJson.sourceType) }
            .getOrDefault(existing?.sourceType ?: ArticleSourceType.MANUAL)
        val sourceTypeV2 = runCatching { ArticleSourceTypeV2.valueOf(articleJson.sourceTypeV2) }
            .getOrDefault(existing?.sourceTypeV2 ?: ArticleSourceTypeV2.LOCAL)

        val difficultyLocal = if (articleJson.difficultyLocal != 0f) {
            articleJson.difficultyLocal
        } else {
            existing?.difficultyLocal ?: 0f
        }
        val difficultyFinal = if (articleJson.difficultyFinal != 0f) {
            articleJson.difficultyFinal
        } else {
            existing?.difficultyFinal ?: 0f
        }

        val coverImageUri = articleJson.coverImageUri?.takeIf { it.isNotBlank() }
            ?: existing?.coverImageUri

        val hasStructuredContent = supportsStructuredContent &&
            (articleJson.paragraphs.isNotEmpty() || articleJson.images.isNotEmpty())
        val parseStatus = when {
            supportsStructuredContent -> {
                if (hasStructuredContent) ArticleParseStatus.DONE else ArticleParseStatus.PENDING
            }
            else -> existing?.parseStatus ?: ArticleParseStatus.PENDING
        }

        val article = Article(
            id = existing?.id ?: 0,
            articleUid = articleJson.articleUid.ifBlank { existing?.articleUid ?: UUID.randomUUID().toString() },
            title = articleJson.title,
            content = articleJson.content,
            domain = articleJson.domain,
            difficultyAi = articleJson.difficultyAi,
            difficultyLocal = difficultyLocal,
            difficultyFinal = difficultyFinal,
            sourceType = sourceType,
            sourceTypeV2 = sourceTypeV2,
            parseStatus = parseStatus,
            createdAt = if (articleJson.createdAt > 0) articleJson.createdAt else (existing?.createdAt ?: System.currentTimeMillis()),
            updatedAt = articleJson.updatedAt,
            summary = articleJson.summary,
            author = articleJson.author,
            source = articleJson.source,
            coverImageUri = coverImageUri,
            coverImageUrl = articleJson.coverImageUrl,
            wordCount = articleJson.wordCount,
            isSaved = articleJson.isSaved,
            categoryId = articleJson.categoryId
        )
        val articleId = articleRepository.upsertArticle(article)

        if (supportsStructuredContent || articleJson.paragraphs.isNotEmpty()) {
            articleRepository.deleteParagraphsByArticle(articleId)
            if (articleJson.paragraphs.isNotEmpty()) {
                val paragraphs = articleJson.paragraphs
                    .sortedBy { it.paragraphIndex }
                    .map { p ->
                        com.xty.englishhelper.domain.model.ArticleParagraph(
                            articleId = articleId,
                            paragraphIndex = p.paragraphIndex,
                            text = p.text,
                            imageUri = p.imageUri,
                            imageUrl = p.imageUrl,
                            paragraphType = runCatching { com.xty.englishhelper.domain.model.ParagraphType.valueOf(p.paragraphType) }
                                .getOrDefault(com.xty.englishhelper.domain.model.ParagraphType.TEXT)
                        )
                    }
                articleRepository.insertParagraphs(paragraphs)
            }
        }

        if (supportsStructuredContent || articleJson.images.isNotEmpty()) {
            articleRepository.deleteImagesByArticle(articleId)
            val ordered = articleJson.images.sortedBy { it.orderIndex }
                .mapNotNull { image ->
                    when {
                        image.localUri.isNotBlank() -> image.localUri
                        !image.imageUrl.isNullOrBlank() -> image.imageUrl
                        else -> null
                    }
                }
            if (ordered.isNotEmpty()) {
                articleRepository.insertImages(articleId, ordered)
            }
        }

        return articleId
    }

    private suspend fun exportWordExamples(): WordExamplesExportModel {
        val wordUidToId = buildWordUidToIdMap()
        val wordIdToUid = wordUidToId.entries.associate { it.value to it.key }
        val articles = articleRepository.getAllArticles().first()
        val articleIdToUid = articles.associate { it.id to it.articleUid }

        val examples = articleDao.getAllExamples().mapNotNull { example ->
            val wordUid = wordIdToUid[example.wordId] ?: return@mapNotNull null
            val articleUid = example.sourceArticleId?.let { articleIdToUid[it] }
            val sentenceIndex = example.sourceSentenceId?.let { articleDao.getSentenceIndexById(it) }
            WordExampleJsonModel(
                wordUid = wordUid,
                sentence = example.sentence,
                sourceType = example.sourceType,
                sourceArticleUid = articleUid,
                sourceSentenceIndex = sentenceIndex,
                sourceLabel = example.sourceLabel,
                createdAt = example.createdAt
            )
        }
        return WordExamplesExportModel(schemaVersion = 1, examples = examples)
    }

    private suspend fun importWordExamples(
        model: WordExamplesExportModel?,
        wordUidToId: Map<String, Long>,
        articleUidToId: Map<String, Long>
    ) {
        if (model == null || model.examples.isEmpty()) return

        val existing = articleDao.getAllExamples()
        val existingKeys = existing.map { ex ->
            val articleId = ex.sourceArticleId ?: -1L
            val label = ex.sourceLabel ?: ""
            "${ex.wordId}|${ex.sourceType}|${articleId}|${ex.sentence}|$label"
        }.toHashSet()

        val toInsert = mutableListOf<WordExample>()
        for (example in model.examples) {
            val wordId = wordUidToId[example.wordUid] ?: continue
            val articleId = example.sourceArticleUid?.let { articleUidToId[it] } ?: -1L
            val label = example.sourceLabel ?: ""
            val key = "${wordId}|${example.sourceType}|${articleId}|${example.sentence}|$label"
            if (!existingKeys.add(key)) continue

            val sentenceId = if (articleId > 0 && example.sourceSentenceIndex != null) {
                articleDao.getSentenceIdByIndex(articleId, example.sourceSentenceIndex)
            } else {
                null
            }

            toInsert.add(
                WordExample(
                    wordId = wordId,
                    sentence = example.sentence,
                    sourceType = com.xty.englishhelper.domain.model.WordExampleSourceType.fromValue(example.sourceType),
                    sourceArticleId = if (articleId > 0) articleId else null,
                    sourceSentenceId = sentenceId,
                    sourceLabel = example.sourceLabel,
                    createdAt = example.createdAt
                )
            )
        }

        if (toInsert.isNotEmpty()) {
            articleRepository.insertExamples(toInsert)
        }
    }

    private suspend fun buildWordUidToIdMap(): Map<String, Long> {
        val dicts = dictionaryRepository.getAllDictionaries().first()
        val map = mutableMapOf<String, Long>()
        for (dict in dicts) {
            val words = wordRepository.getWordsByDictionary(dict.id).first()
            for (word in words) {
                if (word.wordUid.isNotBlank()) {
                    map[word.wordUid] = word.id
                }
            }
        }
        return map
    }

    private fun Article.toArticleJson(
        paragraphs: List<ArticleParagraphJsonModel>,
        images: List<ArticleImageJsonModel>
    ) = ArticleJsonModel(
        articleUid = articleUid.ifBlank { UUID.randomUUID().toString() },
        title = title,
        content = content,
        domain = domain,
        difficultyAi = difficultyAi,
        sourceType = sourceType.name,
        createdAt = createdAt,
        updatedAt = updatedAt,
        summary = summary,
        author = author,
        source = source,
        coverImageUri = coverImageUri,
        coverImageUrl = coverImageUrl,
        wordCount = wordCount,
        categoryId = categoryId,
        difficultyLocal = difficultyLocal,
        difficultyFinal = difficultyFinal,
        sourceTypeV2 = sourceTypeV2.name,
        isSaved = isSaved,
        paragraphs = paragraphs,
        images = images
    )

    private fun guessImageUrl(uri: String): String? {
        return if (uri.startsWith("http://") || uri.startsWith("https://")) uri else null
    }

    private fun ArticleCategory.toCategoryJson(now: Long) = ArticleCategoryJsonModel(
        id = id,
        name = name,
        isSystem = isSystem,
        createdAt = now,
        updatedAt = now
    )

    private fun ArticleCategoryJsonModel.toDomain() = ArticleCategory(
        id = id,
        name = name,
        isSystem = isSystem
    )

    private suspend fun importWordPools(
        dictionaryId: Long,
        pools: List<WordPoolJsonModel>,
        wordUidToId: Map<String, Long>
    ) {
        wordPoolDao.deleteByDictionary(dictionaryId)
        if (pools.isEmpty()) return

        pools.forEach { pool ->
            val memberIds = pool.memberWordUids.mapNotNull { wordUidToId[it] }.distinct()
            if (memberIds.size < 2) return@forEach

            val focusId = pool.focusWordUid?.let { wordUidToId[it] }
            val strategy = pool.strategy.ifBlank { "BALANCED" }
            val algorithmVersion = pool.algorithmVersion.ifBlank { "${strategy}_v1" }

            val poolId = wordPoolDao.insertPool(
                WordPoolEntity(
                    dictionaryId = dictionaryId,
                    focusWordId = focusId,
                    strategy = strategy,
                    algorithmVersion = algorithmVersion
                )
            )
            wordPoolDao.insertMembers(
                memberIds.map { WordPoolMemberEntity(wordId = it, poolId = poolId) }
            )
        }
    }

    // ── Import Helper ──

    private suspend fun importCloudDictionary(cloudDict: DictionaryJsonModel) {
        val json = dictAdapter.toJson(cloudDict)
        try {
            importExporter.importFromJson(json).let { result ->
                transactionRunner.runInTransaction {
                    val dictId = dictionaryRepository.insertDictionary(result.dictionary)
                    val wordUidToId = mutableMapOf<String, Long>()

                    result.words.forEach { word ->
                        val wordWithDict = word.copy(
                            dictionaryId = dictId,
                            normalizedSpelling = word.spelling.trim().lowercase(),
                            wordUid = word.wordUid.ifBlank { UUID.randomUUID().toString() }
                        )
                        val wordId = wordRepository.insertWord(wordWithDict)
                        wordUidToId[wordWithDict.wordUid] = wordId
                    }
                    dictionaryRepository.updateWordCount(dictId)

                    result.units.forEach { unitData ->
                        val unitId = unitRepository.insertUnit(
                            StudyUnit(
                                dictionaryId = dictId,
                                name = unitData.name,
                                defaultRepeatCount = unitData.repeatCount
                            )
                        )
                        val wordIds = unitData.wordUids.mapNotNull { wordUidToId[it] }
                        if (wordIds.isNotEmpty()) {
                            unitRepository.addWordsToUnit(unitId, wordIds)
                        }
                    }

                    result.studyStates.forEach { stateData ->
                        val wordId = wordUidToId[stateData.wordUid] ?: return@forEach
                        studyRepository.upsertStudyState(
                            WordStudyState(
                                wordId = wordId,
                                state = stateData.state,
                                step = stateData.step,
                                stability = stateData.stability,
                                difficulty = stateData.difficulty,
                                due = stateData.due,
                                lastReviewAt = stateData.lastReviewAt,
                                reps = stateData.reps,
                                lapses = stateData.lapses
                            )
                        )
                    }

                    importWordPools(dictId, cloudDict.wordPools, wordUidToId)

                    wordRepository.recomputeAllAssociationsForDictionary(dictId)
                }
            }
        } catch (e: Exception) {
            throw IllegalStateException("导入辞书 ${cloudDict.name} 失败: ${e.message}", e)
        }
    }

    // ── Question Bank Export/Import/Merge ──

    private suspend fun buildArticleUidToIdMap(): Map<String, Long> {
        val articles = articleRepository.getAllArticles().first()
        return articles.filter { it.articleUid.isNotBlank() }.associate { it.articleUid to it.id }
    }

    private suspend fun exportQuestionBank(): QuestionBankExportModel {
        val articles = articleRepository.getAllArticles().first()
        val articleIdToUid = articles.associate { it.id to it.articleUid }

        val papers = questionBankDao.getAllExamPapers().first()
        val paperJsons = papers.map { paper ->
            val groups = questionBankDao.getGroupsByPaperOnce(paper.id)
            val groupJsons = groups.map { group ->
                val paragraphs = questionBankDao.getParagraphs(group.id)
                val items = questionBankDao.getItemsByGroup(group.id)
                val sourceArticle = questionBankDao.getSourceArticle(group.id)

                val linkedArticleUid = if (sourceArticle != null) {
                    // Prefer stored UID (survives even if article was deleted)
                    sourceArticle.linkedArticleUid.ifBlank {
                        articleIdToUid[sourceArticle.linkedArticleId] ?: ""
                    }
                } else ""

                val itemJsons = items.map { item ->
                    val records = questionBankDao.getRecordsByItem(item.id)
                    QuestionItemJson(
                        questionNumber = item.questionNumber,
                        questionText = item.questionText,
                        optionA = item.optionA,
                        optionB = item.optionB,
                        optionC = item.optionC,
                        optionD = item.optionD,
                        correctAnswer = item.correctAnswer,
                        answerSource = item.answerSource,
                        explanation = item.explanation,
                        orderInGroup = item.orderInGroup,
                        wordCount = item.wordCount,
                        difficultyLevel = item.difficultyLevel,
                        difficultyScore = item.difficultyScore,
                        wrongCount = item.wrongCount,
                        extraData = item.extraData,
                        sampleSourceTitle = item.sampleSourceTitle,
                        sampleSourceUrl = item.sampleSourceUrl,
                        sampleSourceInfo = item.sampleSourceInfo,
                        practiceRecords = records.map { r ->
                            PracticeRecordJson(
                                userAnswer = r.userAnswer,
                                isCorrect = r.isCorrect,
                                practicedAt = r.practicedAt
                            )
                        }
                    )
                }

                val paragraphJsons = paragraphs.map { p ->
                    ParagraphJson(
                        paragraphIndex = p.paragraphIndex,
                        text = p.text,
                        paragraphType = p.paragraphType,
                        imageUrl = p.imageUrl
                    )
                }

                QuestionGroupJson(
                    uid = group.uid,
                    questionType = group.questionType,
                    sectionLabel = group.sectionLabel,
                    orderInPaper = group.orderInPaper,
                    directions = group.directions,
                    passageText = group.passageText,
                    sourceInfo = group.sourceInfo,
                    sourceUrl = group.sourceUrl,
                    sourceAuthor = group.sourceAuthor,
                    sourceVerified = group.sourceVerified,
                    sourceVerifyError = group.sourceVerifyError,
                    wordCount = group.wordCount,
                    difficultyLevel = group.difficultyLevel,
                    difficultyScore = group.difficultyScore,
                    hasAiAnswer = group.hasAiAnswer,
                    hasScannedAnswer = group.hasScannedAnswer,
                    createdAt = group.createdAt,
                    updatedAt = group.updatedAt,
                    linkedArticleUid = linkedArticleUid,
                    paragraphs = paragraphJsons,
                    items = itemJsons
                )
            }

            ExamPaperJson(
                uid = paper.uid,
                title = paper.title,
                description = paper.description,
                totalQuestions = paper.totalQuestions,
                createdAt = paper.createdAt,
                updatedAt = paper.updatedAt,
                groups = groupJsons
            )
        }

        return QuestionBankExportModel(schemaVersion = 1, papers = paperJsons)
    }

    private suspend fun importQuestionBank(
        model: QuestionBankExportModel,
        articleUidToId: Map<String, Long>
    ) {
        if (model.schemaVersion > 1) {
            throw IllegalStateException("不支持的题库数据版本: ${model.schemaVersion}，请升级应用")
        }
        transactionRunner.runInTransaction {
            for (paperJson in model.papers) {
            val paperId = questionBankDao.insertExamPaper(
                ExamPaperEntity(
                    uid = paperJson.uid.ifBlank { UUID.randomUUID().toString() },
                    title = paperJson.title,
                    description = paperJson.description,
                    totalQuestions = paperJson.totalQuestions,
                    createdAt = paperJson.createdAt,
                    updatedAt = paperJson.updatedAt
                )
            )

            for (groupJson in paperJson.groups) {
                val groupId = questionBankDao.insertQuestionGroup(
                    QuestionGroupEntity(
                        uid = groupJson.uid.ifBlank { UUID.randomUUID().toString() },
                        examPaperId = paperId,
                        questionType = groupJson.questionType,
                        sectionLabel = groupJson.sectionLabel,
                        orderInPaper = groupJson.orderInPaper,
                        directions = groupJson.directions,
                        passageText = groupJson.passageText,
                        sourceInfo = groupJson.sourceInfo,
                        sourceUrl = groupJson.sourceUrl,
                        sourceAuthor = groupJson.sourceAuthor,
                        sourceVerified = groupJson.sourceVerified,
                        sourceVerifyError = groupJson.sourceVerifyError,
                        wordCount = groupJson.wordCount,
                        difficultyLevel = groupJson.difficultyLevel,
                        difficultyScore = groupJson.difficultyScore,
                        hasAiAnswer = groupJson.hasAiAnswer,
                        hasScannedAnswer = groupJson.hasScannedAnswer,
                        createdAt = groupJson.createdAt,
                        updatedAt = groupJson.updatedAt
                    )
                )

                // Insert paragraphs
                if (groupJson.paragraphs.isNotEmpty()) {
                    questionBankDao.insertParagraphs(
                        groupJson.paragraphs.map { p ->
                            QuestionGroupParagraphEntity(
                                questionGroupId = groupId,
                                paragraphIndex = p.paragraphIndex,
                                text = p.text,
                                paragraphType = p.paragraphType,
                                imageUrl = p.imageUrl
                            )
                        }
                    )
                }

                // Insert items one by one (need itemId for practice records)
                for (itemJson in groupJson.items) {
                    val itemId = questionBankDao.insertQuestionItem(
                        QuestionItemEntity(
                            questionGroupId = groupId,
                            questionNumber = itemJson.questionNumber,
                            questionText = itemJson.questionText,
                            optionA = itemJson.optionA,
                            optionB = itemJson.optionB,
                            optionC = itemJson.optionC,
                            optionD = itemJson.optionD,
                            correctAnswer = itemJson.correctAnswer,
                            answerSource = itemJson.answerSource,
                            explanation = itemJson.explanation,
                            orderInGroup = itemJson.orderInGroup,
                            wordCount = itemJson.wordCount,
                            difficultyLevel = itemJson.difficultyLevel,
                            difficultyScore = itemJson.difficultyScore,
                            wrongCount = itemJson.wrongCount,
                            extraData = itemJson.extraData,
                            sampleSourceTitle = itemJson.sampleSourceTitle,
                            sampleSourceUrl = itemJson.sampleSourceUrl,
                            sampleSourceInfo = itemJson.sampleSourceInfo
                        )
                    )

                    // Insert practice records
                    if (itemJson.practiceRecords.isNotEmpty()) {
                        questionBankDao.insertPracticeRecords(
                            itemJson.practiceRecords.map { r ->
                                PracticeRecordEntity(
                                    questionItemId = itemId,
                                    userAnswer = r.userAnswer,
                                    isCorrect = r.isCorrect,
                                    practicedAt = r.practicedAt
                                )
                            }
                        )
                    }
                }

                // Insert source article link (always preserve UID even if article not yet imported)
                if (groupJson.linkedArticleUid.isNotBlank()) {
                    val linkedArticleId = articleUidToId[groupJson.linkedArticleUid] ?: 0L
                    questionBankDao.insertSourceArticle(
                        QuestionSourceArticleEntity(
                            questionGroupId = groupId,
                            linkedArticleId = linkedArticleId,
                            linkedArticleUid = groupJson.linkedArticleUid,
                            verifiedAt = groupJson.updatedAt
                        )
                    )
                }
            }

            // Update total questions count
            var totalItems = 0
            for (groupJson in paperJson.groups) {
                totalItems += groupJson.items.size
            }
            questionBankDao.updateTotalQuestions(paperId, totalItems, paperJson.updatedAt)
            }
        }
    }

    private suspend fun mergeQuestionBank(
        cloudModel: QuestionBankExportModel?,
        articleUidToId: Map<String, Long>
    ) {
        if (cloudModel == null || cloudModel.papers.isEmpty()) return

        val localPapers = questionBankDao.getAllExamPapers().first()
        val localByUid = localPapers.filter { it.uid.isNotBlank() }.associateBy { it.uid }
        val cloudByUid = cloudModel.papers.filter { it.uid.isNotBlank() }.associateBy { it.uid }
        val allUids = localByUid.keys + cloudByUid.keys

        for (uid in allUids) {
            val local = localByUid[uid]
            val cloud = cloudByUid[uid]

            if (local == null && cloud != null) {
                // Cloud-only → import
                importQuestionBank(
                    QuestionBankExportModel(papers = listOf(cloud)),
                    articleUidToId
                )
            } else if (local != null && cloud != null) {
                // Both exist → newer wins (using effective updatedAt that includes child changes)
                val localEffective = questionBankDao.getEffectiveUpdatedAt(local.id) ?: local.updatedAt
                val cloudEffective = cloudEffectiveUpdatedAt(cloud)
                if (cloudEffective > localEffective) {
                    // Delete local (CASCADE clears child tables), then import cloud
                    questionBankDao.deleteExamPaper(local.id)
                    importQuestionBank(
                        QuestionBankExportModel(papers = listOf(cloud)),
                        articleUidToId
                    )
                }
            }
            // local-only → keep (will be uploaded)
        }
    }

    private fun cloudEffectiveUpdatedAt(paper: ExamPaperJson): Long {
        var max = paper.updatedAt
        for (group in paper.groups) {
            if (group.updatedAt > max) max = group.updatedAt
            for (item in group.items) {
                for (record in item.practiceRecords) {
                    if (record.practicedAt > max) max = record.practicedAt
                }
            }
        }
        return max
    }

    // ── GitHub API Helpers ──

    private suspend fun <T> downloadJson(path: String, adapter: com.squareup.moshi.JsonAdapter<T>): T? {
        val response = gitHubApi.getContent(authHeader(), owner(), repo(), path)
        if (!response.isSuccessful) {
            if (response.code() == 404) return null
            throw IllegalStateException("GitHub API 错误: ${response.code()} ${response.message()}")
        }
        val content = response.body() ?: return null
        val decoded = when {
            content.encoding == "base64" && !content.content.isNullOrBlank() -> {
                val cleaned = content.content.replace("\n", "")
                String(Base64.decode(cleaned, Base64.DEFAULT), Charsets.UTF_8)
            }
            !content.content.isNullOrBlank() -> content.content
            !content.downloadUrl.isNullOrBlank() -> {
                val rawResponse = gitHubApi.downloadRaw(authHeader(), content.downloadUrl)
                if (!rawResponse.isSuccessful) {
                    throw IllegalStateException("下载失败: ${rawResponse.code()} ${rawResponse.message()}")
                }
                rawResponse.body()?.string() ?: throw IllegalStateException("下载失败: 空响应")
            }
            else -> return null
        }
        return adapter.fromJson(decoded)
    }

    private suspend fun <T> uploadJson(path: String, data: T, adapter: com.squareup.moshi.JsonAdapter<T>) {
        val json = adapter.toJson(data)
        val encoded = Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

        // Get existing SHA for update (CAS)
        val existingResponse = gitHubApi.getContent(authHeader(), owner(), repo(), path)
        val sha = if (existingResponse.isSuccessful) existingResponse.body()?.sha else null

        val putRequest = GitHubPutRequest(
            message = "sync: update $path",
            content = encoded,
            sha = sha
        )
        val putResponse = gitHubApi.putContent(authHeader(), owner(), repo(), path, putRequest)
        if (!putResponse.isSuccessful) {
            throw IllegalStateException("上传失败: $path (${putResponse.code()} ${putResponse.message()})")
        }
    }
}
