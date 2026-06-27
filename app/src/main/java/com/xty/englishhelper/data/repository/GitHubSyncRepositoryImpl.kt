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
import com.xty.englishhelper.data.json.DictionaryCloudEntryJsonModel
import com.xty.englishhelper.data.json.DictionaryJsonModel
import com.xty.englishhelper.data.json.DictionaryShardChunkJsonModel
import com.xty.englishhelper.data.json.DictionaryShardIndexJsonModel
import com.xty.englishhelper.data.json.ExamPaperJson
import com.xty.englishhelper.data.json.InflectionJsonModel
import com.xty.englishhelper.data.json.ParagraphJson
import com.xty.englishhelper.data.json.PlanDayRecordJsonModel
import com.xty.englishhelper.data.json.PlanEventLogJsonModel
import com.xty.englishhelper.data.json.PlanExportJsonModel
import com.xty.englishhelper.data.json.PlanItemJsonModel
import com.xty.englishhelper.data.json.PlanTemplateJsonModel
import com.xty.englishhelper.data.json.PracticeRecordJson
import com.xty.englishhelper.data.json.QuestionBankExportModel
import com.xty.englishhelper.data.json.QuestionGroupJson
import com.xty.englishhelper.data.json.QuestionItemJson
import com.xty.englishhelper.data.json.StudyStateJsonModel
import com.xty.englishhelper.data.json.SyncManifest
import com.xty.englishhelper.data.json.SettingsSyncJsonModel
import com.xty.englishhelper.data.json.UnitJsonModel
import com.xty.englishhelper.data.json.WordJsonModel
import com.xty.englishhelper.data.json.WordExampleJsonModel
import com.xty.englishhelper.data.json.WordExamplesExportModel
import com.xty.englishhelper.data.json.WordPoolJsonModel
import com.xty.englishhelper.data.local.dao.ArticleDao
import com.xty.englishhelper.data.local.dao.QuestionBankDao
import com.xty.englishhelper.data.local.dao.WordEdgeDao
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
import com.xty.englishhelper.data.sync.DictionaryShardAssembler
import com.xty.englishhelper.data.sync.DictionaryWordMergePlanner
import com.xty.englishhelper.data.sync.DictionaryWordPoolMergePlanner
import com.xty.englishhelper.data.sync.DictionaryWordUidNormalizer
import com.xty.englishhelper.data.sync.DictionaryWordUpdatePlanner
import com.xty.englishhelper.domain.model.Article
import com.xty.englishhelper.domain.model.ArticleCategory
import com.xty.englishhelper.domain.model.ArticleCategoryDefaults
import com.xty.englishhelper.domain.model.ArticleParseStatus
import com.xty.englishhelper.domain.model.ArticleSourceType
import com.xty.englishhelper.domain.model.ArticleSourceTypeV2
import com.xty.englishhelper.domain.model.CognateInfo
import com.xty.englishhelper.domain.model.DecompositionPart
import com.xty.englishhelper.domain.model.Dictionary
import com.xty.englishhelper.domain.model.Inflection
import com.xty.englishhelper.domain.model.Meaning
import com.xty.englishhelper.domain.model.MorphemeRole
import com.xty.englishhelper.domain.model.SimilarWordInfo
import com.xty.englishhelper.domain.model.StudyMode
import com.xty.englishhelper.domain.model.StudyUnit
import com.xty.englishhelper.domain.model.SynonymInfo
import com.xty.englishhelper.domain.model.WordDetails
import com.xty.englishhelper.domain.model.WordStudyState
import com.xty.englishhelper.domain.model.PlanBackup
import com.xty.englishhelper.domain.model.PlanDayRecordBackup
import com.xty.englishhelper.domain.model.PlanEventLogBackup
import com.xty.englishhelper.domain.model.PlanItemBackup
import com.xty.englishhelper.domain.model.PlanTemplateBackup
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
import com.xty.englishhelper.domain.repository.PlanRepository
import com.xty.englishhelper.domain.usecase.importexport.ExportDictionaryUseCase
import com.xty.englishhelper.domain.usecase.word.EnsureDictionaryWordUidsUseCase
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

internal data class GitHubRepoTarget(
    val owner: String,
    val repo: String
)

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
    private val wordEdgeDao: WordEdgeDao,
    private val questionBankDao: QuestionBankDao,
    private val planRepository: PlanRepository,
    private val importExporter: DictionaryImportExporter,
    private val exportDictionary: ExportDictionaryUseCase,
    private val transactionRunner: TransactionRunner,
    private val moshi: Moshi,
    private val dictionaryShardAssembler: DictionaryShardAssembler,
    private val dictionaryWordMergePlanner: DictionaryWordMergePlanner,
    private val dictionaryWordUpdatePlanner: DictionaryWordUpdatePlanner,
    private val dictionaryWordPoolMergePlanner: DictionaryWordPoolMergePlanner,
    private val dictionaryWordUidNormalizer: DictionaryWordUidNormalizer,
    private val ensureDictionaryWordUids: EnsureDictionaryWordUidsUseCase
) : CloudSyncRepository {

    private val dictAdapter = moshi.adapter(DictionaryJsonModel::class.java).indent("  ")
    private val dictionaryShardIndexAdapter = moshi.adapter(DictionaryShardIndexJsonModel::class.java).indent("  ")
    private val dictionaryShardChunkAdapter = moshi.adapter(DictionaryShardChunkJsonModel::class.java).indent("  ")
    private val articlesAdapter = moshi.adapter(ArticlesExportModel::class.java).indent("  ")
    private val manifestAdapter = moshi.adapter(SyncManifest::class.java).indent("  ")
    private val questionBankAdapter = moshi.adapter(QuestionBankExportModel::class.java).indent("  ")
    private val articleCategoriesAdapter = moshi.adapter(ArticleCategoriesExportModel::class.java).indent("  ")
    private val wordExamplesAdapter = moshi.adapter(WordExamplesExportModel::class.java).indent("  ")
    private val planAdapter = moshi.adapter(PlanExportJsonModel::class.java).indent("  ")
    private val settingsSyncAdapter = moshi.adapter(SettingsSyncJsonModel::class.java).indent("  ")

    private fun authHeader(): String {
        val pat = settingsDataStore.getGitHubPat()
        if (pat.isBlank()) throw IllegalStateException("GitHub Token not configured")
        return "Bearer $pat"
    }

    private suspend fun owner(): String {
        val o = settingsDataStore.githubOwner.first()
        if (o.isBlank()) throw IllegalStateException("GitHub username not configured")
        return o
    }

    private suspend fun repo(): String {
        val r = settingsDataStore.githubRepo.first()
        if (r.isBlank()) throw IllegalStateException("Repository name not configured")
        return r
    }

    private suspend fun mainRepoTarget(): GitHubRepoTarget =
        GitHubRepoTarget(owner = owner(), repo = repo())

    private suspend fun configRepoTargetOrNull(): GitHubRepoTarget? {
        val enabled = settingsDataStore.githubConfigSyncEnabled.first()
        if (!enabled) return null
        val configRepo = settingsDataStore.githubConfigRepo.first().trim()
        if (configRepo.isBlank()) {
            throw IllegalStateException("配置同步已开启，但配置仓库未填写")
        }
        val mainRepo = repo().trim()
        if (configRepo.equals(mainRepo, ignoreCase = true)) {
            throw IllegalStateException("配置同步仓库必须与数据同步仓库不同")
        }
        return GitHubRepoTarget(owner = owner(), repo = configRepo)
    }

    private suspend fun assertRepoAccessible(target: GitHubRepoTarget) {
        val response = gitHubApi.getRepo(authHeader(), target.owner, target.repo)
        if (!response.isSuccessful) {
            throw IllegalStateException("GitHub 仓库不可用: ${target.owner}/${target.repo}")
        }
    }

    private suspend fun exportSettingsSnapshot(): SettingsSyncJsonModel {
        return settingsDataStore.exportSyncSnapshot(
            appVersion = BuildConfig.VERSION_NAME,
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
        )
    }

    private suspend fun prepareUploadableSettingsSnapshot(
        existingSnapshot: SettingsSyncJsonModel? = null
    ): SettingsSyncJsonModel {
        val snapshot = existingSnapshot ?: exportSettingsSnapshot()
        if (snapshot.exportedAt > 0L) return snapshot
        settingsDataStore.markSettingsSyncUpdatedAt()
        return exportSettingsSnapshot()
    }

    // Public API

    override suspend fun testConnection(): Boolean {
        val mainTarget = mainRepoTarget()
        val configTarget = configRepoTargetOrNull()
        val mainResponse = gitHubApi.getRepo(authHeader(), mainTarget.owner, mainTarget.repo)
        if (!mainResponse.isSuccessful) return false
        if (configTarget == null) return true
        val configResponse = gitHubApi.getRepo(authHeader(), configTarget.owner, configTarget.repo)
        return configResponse.isSuccessful
    }

    override suspend fun getCloudManifest(): SyncManifest? {
        return downloadJson("manifest.json", manifestAdapter)
    }

    override suspend fun sync(onProgress: suspend (SyncProgress) -> Unit) {
        authHeader()
        val mainTarget = mainRepoTarget()
        val configTarget = configRepoTargetOrNull()
        assertRepoAccessible(mainTarget)
        configTarget?.let { assertRepoAccessible(it) }
        val totalSteps = if (configTarget != null) 5 else 4

        // 1. Download cloud data
        onProgress(SyncProgress("Downloading", "Downloading cloud data...", 0, totalSteps))
        val cloudManifest = downloadJson("manifest.json", manifestAdapter)
        val cloudDicts = downloadCloudDictionaries(cloudManifest) { current, total, entry ->
            onProgress(
                SyncProgress(
                    "Downloading",
                    "Downloading ${entry.name.ifBlank { entry.path }}",
                    current,
                    total.coerceAtLeast(1)
                )
            )
        }.toMutableList()
        val cloudArticles = downloadJson("articles.json", articlesAdapter)
        val cloudCategories = downloadJson("article_categories.json", articleCategoriesAdapter)
        val cloudWordExamples = downloadJson("word_examples.json", wordExamplesAdapter)
        val cloudPlan = downloadJson("plan.json", planAdapter)

        // 2. Read local data
        onProgress(SyncProgress("Reading", "Reading local data...", 1, totalSteps))
        val localDicts = dictionaryRepository.getAllDictionaries().first()
        applyCloudCategoriesOnSync(cloudCategories)
        val localArticles = articleRepository.getAllArticles().first()
        val localPlanBackup = planRepository.exportBackup()

        // 3. Smart merge
        onProgress(SyncProgress("Merging", "Merging dictionaries...", 2, totalSteps))
        val mergedDictJsons = mutableListOf<DictionaryJsonModel>()

        for (localDict in localDicts) {
            val localJson = exportLocalDictionary(localDict)
            val cloudJson = takeMatchingCloudDictionary(localDict, cloudDicts)

            if (cloudJson == null) {
                // Only local -> upload as-is
                mergedDictJsons.add(localJson)
            } else {
                // Both exist -> merge
                val merged = mergeDictionaries(localDict, localJson, cloudJson)
                mergedDictJsons.add(merged)
            }
        }

        // Cloud-only dicts -> import to local
        for (cloudDict in cloudDicts) {
            onProgress(SyncProgress("Importing", "Importing cloud dictionary ${cloudDict.name}...", 2, totalSteps))
            importCloudDictionary(cloudDict)
            mergedDictJsons.add(cloudDict)
        }

        // Merge articles
        mergeArticles(localArticles, cloudArticles)

        // Merge question bank (after articles, so source links can resolve)
        val cloudQuestionBank = downloadJson("questionbank.json", questionBankAdapter)
        val articleUidToId = buildArticleUidToIdMap()
        mergeQuestionBank(cloudQuestionBank, articleUidToId)
        val wordUidToId = buildWordUidToIdMap()
        importWordExamples(cloudWordExamples, wordUidToId, articleUidToId)
        val mergedPlanBackup = mergePlanBackup(localPlanBackup, cloudPlan?.toDomainBackup())

        // Re-export local dicts that were merged (to pick up cloud-only words)
        val finalDictJsons = mutableListOf<DictionaryJsonModel>()
        val allLocalDicts = dictionaryRepository.getAllDictionaries().first()
        for (dict in allLocalDicts) {
            finalDictJsons.add(exportLocalDictionary(dict))
        }

        // 4. Upload merged snapshot
        onProgress(SyncProgress("Uploading", "Uploading merged snapshot...", 3, totalSteps))
        val dictionaryUpload = uploadDictionarySnapshot(
            dictionaries = finalDictJsons,
            previousManifest = cloudManifest
        )

        // Upload articles
        val allArticles = articleRepository.getAllArticles().first()
        val articleJsons = mutableListOf<ArticleJsonModel>()
        for (article in allArticles) {
            articleJsons.add(exportArticleJson(article))
        }
        val articlesExport = ArticlesExportModel(
            schemaVersion = 3,
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

        // Upload plan
        val planExport = mergedPlanBackup.toJsonModel()
        uploadJson("plan.json", planExport, planAdapter)

        // Upload manifest
        val manifest = SyncManifest(
            appVersion = BuildConfig.VERSION_NAME,
            schemaVersion = 6,
            syncedAt = System.currentTimeMillis(),
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
            dictionaries = emptyList(),
            dictionaryEntries = dictionaryUpload.entries,
            hasArticles = allArticles.isNotEmpty(),
            hasQuestionBank = questionBankExport.papers.isNotEmpty(),
            hasWordExamples = wordExamplesExport.examples.isNotEmpty(),
            hasPlan = mergedPlanBackup.isEmpty().not()
        )
        uploadJson("manifest.json", manifest, manifestAdapter)

        if (configTarget != null) {
            onProgress(SyncProgress("Config", "Synchronizing settings...", 4, totalSteps))
            syncSettingsSmart(configTarget)
        }

        settingsDataStore.setLastSyncAt(System.currentTimeMillis())
        onProgress(SyncProgress("Done", "Sync complete", totalSteps, totalSteps))
    }

    override suspend fun forceUpload(onProgress: suspend (SyncProgress) -> Unit) {
        authHeader()
        val mainTarget = mainRepoTarget()
        val configTarget = configRepoTargetOrNull()
        assertRepoAccessible(mainTarget)
        configTarget?.let { assertRepoAccessible(it) }
        val totalSteps = if (configTarget != null) 5 else 4
        onProgress(SyncProgress("Uploading", "Comparing cloud data...", 0, totalSteps))

        // Download cloud manifest for comparison
        val previousManifest = downloadJson("manifest.json", manifestAdapter)

        // Phase 1: Upload dictionaries (incremental via content hash)
        onProgress(SyncProgress("Uploading", "Syncing dictionaries...", 1, totalSteps))
        val localDicts = dictionaryRepository.getAllDictionaries().first()
        val finalDictJsons = localDicts.map { dict ->
            exportLocalDictionary(dict)
        }
        val dictionaryUpload = uploadDictionarySnapshot(
            dictionaries = finalDictJsons,
            previousManifest = previousManifest
        )

        // Phase 2: Upload articles (incremental via timestamp comparison)
        onProgress(SyncProgress("Uploading", "Syncing articles...", 2, totalSteps))
        val cloudArticles = downloadJson("articles.json", articlesAdapter)
        val cloudArticleMap = cloudArticles?.articles?.associateBy { it.articleUid } ?: emptyMap()
        val allArticles = articleRepository.getAllArticles().first()
        val articleJsons = mutableListOf<ArticleJsonModel>()
        var articlesUpdated = 0
        for (article in allArticles) {
            val cloudArticle = cloudArticleMap[article.articleUid]
            if (cloudArticle == null || article.updatedAt > cloudArticle.updatedAt) {
                articleJsons.add(exportArticleJson(article))
                articlesUpdated++
            } else {
                articleJsons.add(cloudArticle)
            }
        }
        val articlesExport = ArticlesExportModel(schemaVersion = 3, articles = articleJsons)
        uploadJson("articles.json", articlesExport, articlesAdapter)

        // Phase 3: Upload categories, question bank, word examples, plan (always full)
        onProgress(SyncProgress("Uploading", "Syncing other data...", 3, totalSteps))
        val categoriesExport = exportArticleCategories()
        uploadJson("article_categories.json", categoriesExport, articleCategoriesAdapter)

        val questionBankExport = exportQuestionBank()
        uploadJson("questionbank.json", questionBankExport, questionBankAdapter)

        val wordExamplesExport = exportWordExamples()
        uploadJson("word_examples.json", wordExamplesExport, wordExamplesAdapter)

        val planExport = planRepository.exportBackup().toJsonModel()
        uploadJson("plan.json", planExport, planAdapter)

        // Phase 4: Upload manifest
        onProgress(SyncProgress("Uploading", "Updating manifest...", 4, totalSteps))
        val manifest = SyncManifest(
            appVersion = BuildConfig.VERSION_NAME,
            schemaVersion = 6,
            syncedAt = System.currentTimeMillis(),
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
            dictionaries = emptyList(),
            dictionaryEntries = dictionaryUpload.entries,
            hasArticles = allArticles.isNotEmpty(),
            hasQuestionBank = questionBankExport.papers.isNotEmpty(),
            hasWordExamples = wordExamplesExport.examples.isNotEmpty(),
            hasPlan = planExport.templates.isNotEmpty() || planExport.items.isNotEmpty()
        )
        uploadJson("manifest.json", manifest, manifestAdapter)

        if (configTarget != null) {
            onProgress(SyncProgress("Config", "Uploading settings snapshot...", 5.coerceAtMost(totalSteps), totalSteps))
            forceUploadSettings(configTarget)
        }

        settingsDataStore.setLastSyncAt(System.currentTimeMillis())
        onProgress(
            SyncProgress(
                "Done",
                "Upload complete (incremental: $articlesUpdated articles updated)",
                totalSteps,
                totalSteps
            )
        )
    }

    override suspend fun forceDownload(onProgress: suspend (SyncProgress) -> Unit) {
        authHeader()
        val mainTarget = mainRepoTarget()
        val configTarget = configRepoTargetOrNull()
        assertRepoAccessible(mainTarget)
        configTarget?.let { assertRepoAccessible(it) }
        val totalSteps = if (configTarget != null) 5 else 4
        onProgress(SyncProgress("Downloading", "Downloading cloud data...", 0, totalSteps))

        val cloudManifest = downloadJson("manifest.json", manifestAdapter)
            ?: throw IllegalStateException("No cloud sync data")

        // Phase 1: Download dictionaries (incremental via content hash)
        onProgress(SyncProgress("Downloading", "Syncing dictionaries...", 1, totalSteps))
        val cloudDicts = downloadCloudDictionaries(cloudManifest) { current, total, entry ->
            onProgress(
                SyncProgress(
                    "Downloading",
                    "Downloading dictionary ${entry.name.ifBlank { entry.path }}",
                    current,
                    total.coerceAtLeast(1)
                )
            )
        }

        // Phase 2: Download replacement payloads
        onProgress(SyncProgress("Downloading", "Downloading articles...", 2, totalSteps))
        val cloudArticles = downloadJson("articles.json", articlesAdapter)
        val cloudCategories = downloadJson("article_categories.json", articleCategoriesAdapter)

        if (cloudArticles != null) {
            // Payload downloaded successfully; replacement happens after all cloud data is ready.
        }

        // Phase 3: Download question bank, word examples, plan
        onProgress(SyncProgress("Downloading", "Downloading remaining cloud data...", 3, totalSteps))
        val cloudQuestionBank = downloadJson("questionbank.json", questionBankAdapter)
        val cloudWordExamples = downloadJson("word_examples.json", wordExamplesAdapter)
        val cloudPlan = downloadJson("plan.json", planAdapter)

        val cloudSettingsSnapshot = configTarget?.let { target ->
            onProgress(SyncProgress("Config", "Downloading settings snapshot...", 4, totalSteps))
            downloadRequiredSettingsSnapshot(target)
        }

        // Phase 4/5: Replace local snapshot with cloud snapshot
        val importStep = if (cloudSettingsSnapshot != null || configTarget != null) totalSteps else 4
        onProgress(SyncProgress("Importing", "Replacing local data with cloud snapshot...", importStep, totalSteps))
        replaceAllArticlesFromCloud(cloudArticles, cloudCategories)
        replaceAllDictionariesFromCloud(cloudDicts)
        replaceQuestionBankFromCloud(cloudQuestionBank)
        replaceWordExamplesFromCloud(cloudWordExamples)
        planRepository.replaceFromBackup(cloudPlan?.toDomainBackup() ?: PlanBackup())

        if (configTarget != null) {
            forceDownloadSettings(cloudSettingsSnapshot)
        }

        settingsDataStore.setLastSyncAt(System.currentTimeMillis())
        onProgress(SyncProgress("Done", "Download complete (cloud replaced local snapshot)", totalSteps, totalSteps))
    }

    private suspend fun downloadSettingsSnapshot(target: GitHubRepoTarget): SettingsSyncJsonModel? {
        return downloadJson(target, "settings.json", settingsSyncAdapter)
    }

    internal suspend fun downloadRequiredSettingsSnapshot(target: GitHubRepoTarget): SettingsSyncJsonModel {
        return downloadSettingsSnapshot(target)
            ?: throw IllegalStateException("配置同步已开启，但配置仓库中缺少 settings.json")
    }

    internal suspend fun syncSettingsSmart(target: GitHubRepoTarget) {
        val localSnapshot = exportSettingsSnapshot()
        val cloudSnapshot = downloadSettingsSnapshot(target)
        if (cloudSnapshot == null) {
            uploadJson(
                target,
                "settings.json",
                prepareUploadableSettingsSnapshot(localSnapshot),
                settingsSyncAdapter
            )
            return
        }
        if (localSnapshot.exportedAt <= 0L) {
            settingsDataStore.importSyncSnapshot(cloudSnapshot)
            return
        }
        if (localSnapshot.exportedAt >= cloudSnapshot.exportedAt) {
            uploadJson(target, "settings.json", localSnapshot, settingsSyncAdapter)
            return
        }
        settingsDataStore.importSyncSnapshot(cloudSnapshot)
    }

    private suspend fun forceUploadSettings(target: GitHubRepoTarget) {
        val snapshot = prepareUploadableSettingsSnapshot()
        uploadJson(target, "settings.json", snapshot, settingsSyncAdapter)
    }

    private suspend fun forceDownloadSettings(snapshot: SettingsSyncJsonModel?) {
        if (snapshot == null) return
        settingsDataStore.importSyncSnapshot(snapshot)
    }

    private data class DictionaryUploadResult(
        val entries: List<DictionaryCloudEntryJsonModel>
    )

    private fun resolveDictionaryEntries(manifest: SyncManifest?): List<DictionaryCloudEntryJsonModel> {
        if (manifest == null) return emptyList()
        if (manifest.dictionaryEntries.isNotEmpty()) {
            return manifest.dictionaryEntries
        }
        return manifest.dictionaries.map { fileName ->
            DictionaryCloudEntryJsonModel(
                dictionaryUid = "",
                name = fileName,
                format = DictionaryCloudEntryJsonModel.FORMAT_SINGLE,
                path = "dictionaries/$fileName"
            )
        }
    }

    private suspend fun downloadCloudDictionaries(
        manifest: SyncManifest?,
        onEntry: suspend (current: Int, total: Int, entry: DictionaryCloudEntryJsonModel) -> Unit = { _, _, _ -> }
    ): List<DictionaryJsonModel> {
        val entries = resolveDictionaryEntries(manifest)
        if (entries.isEmpty()) return emptyList()

        val dictionaries = mutableListOf<DictionaryJsonModel>()
        entries.forEachIndexed { index, entry ->
            onEntry(index + 1, entries.size, entry)
            val dictionary = downloadDictionaryEntry(entry)
            dictionaries += dictionary
        }
        val duplicateNames = dictionaries.groupBy { it.name }.filterValues { it.size > 1 }.keys
        if (duplicateNames.isNotEmpty()) {
            throw IllegalStateException("Duplicate dictionary name in cloud sync manifest: ${duplicateNames.sorted().joinToString("、")}")
        }
        val duplicateUids = dictionaries
            .filter { it.dictionaryUid.isNotBlank() }
            .groupBy { it.dictionaryUid }
            .filterValues { it.size > 1 }
            .keys
        if (duplicateUids.isNotEmpty()) {
            throw IllegalStateException("Duplicate dictionary uid in cloud sync manifest: ${duplicateUids.sorted().joinToString("、")}")
        }
        return dictionaries
    }

    private suspend fun downloadDictionaryEntry(entry: DictionaryCloudEntryJsonModel): DictionaryJsonModel {
        return when (entry.format) {
            DictionaryCloudEntryJsonModel.FORMAT_SINGLE -> {
                downloadJson(entry.path, dictAdapter)
                    ?: throw IllegalStateException("Cloud sync manifest references non-existent dictionary file: ${entry.path}")
            }

            DictionaryCloudEntryJsonModel.FORMAT_SHARDED -> {
                val index = downloadJson(entry.path, dictionaryShardIndexAdapter)
                    ?: throw IllegalStateException("Cloud sync manifest references non-existent dictionary index: ${entry.path}")
                if (entry.chunkCount > 0 && index.chunks.size != entry.chunkCount) {
                    throw IllegalStateException(
                        "Dictionary chunk count mismatch for ${entry.name}: expected=${entry.chunkCount} actual=${index.chunks.size}"
                    )
                }
                val chunksByPath = linkedMapOf<String, DictionaryShardChunkJsonModel>()
                index.chunks.forEach { ref ->
                    val chunk = downloadJson(ref.file, dictionaryShardChunkAdapter)
                        ?: throw IllegalStateException("Missing dictionary chunk: ${ref.file}")
                    chunksByPath[ref.file] = chunk
                }
                dictionaryShardAssembler.assemble(index, chunksByPath).also { assembled ->
                    if (index.totalWords > 0 && assembled.words.size != index.totalWords) {
                        throw IllegalStateException(
                            "Dictionary word total mismatch for ${entry.name}: expected=${index.totalWords} actual=${assembled.words.size}"
                        )
                    }
                    if (index.totalStudyStates > 0 && assembled.studyStates.size != index.totalStudyStates) {
                        throw IllegalStateException(
                            "Dictionary study state total mismatch for ${entry.name}: expected=${index.totalStudyStates} actual=${assembled.studyStates.size}"
                        )
                    }
                }
            }

            else -> throw IllegalStateException("Unsupported dictionary cloud format: ${entry.format}")
        }.let(dictionaryWordUidNormalizer::normalize)
    }

    private suspend fun uploadDictionarySnapshot(
        dictionaries: List<DictionaryJsonModel>,
        previousManifest: SyncManifest?
    ): DictionaryUploadResult {
        val previousEntries = resolveDictionaryEntries(previousManifest)
        val previousShardedByUid = previousEntries
            .filter {
                it.format == DictionaryCloudEntryJsonModel.FORMAT_SHARDED &&
                    it.dictionaryUid.isNotBlank()
            }
            .associateBy { it.dictionaryUid }
        val previousShardedByName = previousEntries
            .filter {
                it.format == DictionaryCloudEntryJsonModel.FORMAT_SHARDED &&
                    it.name.isNotBlank()
            }
            .associateBy { it.name }

        val entries = dictionaries.map { dictionary ->
            val sharded = dictionaryShardAssembler.shard(dictionary)
            val previousEntry = dictionary.dictionaryUid
                .takeIf { it.isNotBlank() }
                ?.let(previousShardedByUid::get)
                ?: previousShardedByName[dictionary.name]
            val previousIndex = previousEntry?.let { downloadJson(it.path, dictionaryShardIndexAdapter) }
            uploadShardedDictionary(sharded, previousIndex)
            sharded.entry
        }

        return DictionaryUploadResult(
            entries = entries
        )
    }

    private suspend fun uploadShardedDictionary(
        sharded: DictionaryShardAssembler.ShardedDictionary,
        previousIndex: DictionaryShardIndexJsonModel?
    ) {
        val previousRefsByFile = previousIndex
            ?.chunks
            ?.associateBy { it.file }
            .orEmpty()

        sharded.chunks.forEach { chunk ->
            val previousRef = previousRefsByFile[chunk.path]
            if (previousRef?.contentHash == chunk.ref.contentHash) return@forEach
            uploadJson(chunk.path, chunk.payload, dictionaryShardChunkAdapter)
        }

        uploadJson(sharded.entry.path, sharded.index, dictionaryShardIndexAdapter)
    }

    // Merge Logic

    private suspend fun mergeDictionaries(
        localDict: Dictionary,
        localJson: DictionaryJsonModel,
        cloudJson: DictionaryJsonModel
    ): DictionaryJsonModel {
        val resolvedLocalDict = reconcileDictionaryMetadata(localDict, cloudJson)
        val wordPlan = dictionaryWordMergePlanner.plan(
            localWords = localJson.words,
            cloudWords = cloudJson.words
        )
        var didMutateWords = false
        val cloudOnlyWordUids = wordPlan.cloudOnlyWords
            .mapNotNull { it.wordUid.takeIf(String::isNotBlank) }
            .toSet()

        if (wordPlan.cloudOnlyWords.isNotEmpty()) {
            val importResult = importExporter.importFromJson(
                dictAdapter.toJson(cloudJson.copy(
                    words = wordPlan.cloudOnlyWords,
                    units = emptyList(),
                    studyStates = cloudJson.studyStates.filter { state ->
                        state.wordUid.isNotBlank() && state.wordUid in cloudOnlyWordUids
                    },
                    wordPools = emptyList()
                ))
            )
            transactionRunner.runInTransaction {
                importResult.words.forEach { word ->
                    val existing = wordRepository.findByNormalizedSpelling(resolvedLocalDict.id, word.normalizedSpelling)
                    if (existing == null) {
                        val wordWithDict = word.copy(
                            dictionaryId = resolvedLocalDict.id,
                            wordUid = word.wordUid
                        )
                        val wordId = wordRepository.insertWord(wordWithDict)
                        // Import study states if available
                        val stateData = importResult.studyStates.filter { it.wordUid == word.wordUid }
                        if (stateData.isNotEmpty()) {
                            stateData.forEach { importedState ->
                                studyRepository.upsertStudyState(
                                    WordStudyState(
                                        wordId = wordId,
                                        studyMode = importedState.studyMode,
                                        state = importedState.state,
                                        step = importedState.step,
                                        stability = importedState.stability,
                                        difficulty = importedState.difficulty,
                                        due = importedState.due,
                                        lastReviewAt = importedState.lastReviewAt,
                                        reps = importedState.reps,
                                        lapses = importedState.lapses
                                    )
                                )
                            }
                        }
                    }
                }
                dictionaryRepository.updateWordCount(resolvedLocalDict.id)
            }
            didMutateWords = true
        }

        if (wordPlan.cloudUpdates.isNotEmpty()) {
            val currentLocalWords = wordRepository.getWordsByDictionary(resolvedLocalDict.id).first()
            val resolvedUpdates = resolveCloudWordUpdates(
                dictionaryId = resolvedLocalDict.id,
                localWords = currentLocalWords,
                cloudUpdates = wordPlan.cloudUpdates
            )
            val updatePlan = dictionaryWordUpdatePlanner.plan(
                localWords = currentLocalWords,
                candidates = resolvedUpdates
            )

            transactionRunner.runInTransaction {
                applyWordUpdatePlan(updatePlan, currentLocalWords)
            }
            didMutateWords = true
        }

        // For study states, keep the more recent lastReviewAt
        val cloudStudyByUid = cloudJson.studyStates
            .filter { it.wordUid.isNotBlank() }
            .associateBy { it.wordUid to normalizeStudyModeName(it.mode) }
        val localStudyByUid = localJson.studyStates
            .filter { it.wordUid.isNotBlank() }
            .associateBy { it.wordUid to normalizeStudyModeName(it.mode) }
        val allStudyKeys = (cloudStudyByUid.keys + localStudyByUid.keys)
            .toSet()

        // Get local word mapping after word imports/updates
        val localWords = wordRepository.getWordsByDictionary(resolvedLocalDict.id).first()
        val wordUidToId = localWords
            .filter { it.wordUid.isNotBlank() }
            .associate { it.wordUid to it.id }

        val wordPoolPlan = dictionaryWordPoolMergePlanner.plan(
            localPools = localJson.wordPools,
            cloudPools = cloudJson.wordPools
        )
        if (wordPoolPlan.cloudSnapshotsToApply.isNotEmpty()) {
            transactionRunner.runInTransaction {
                wordPoolPlan.cloudSnapshotsToApply.forEach { snapshot ->
                    replaceWordPoolStrategy(
                        dictionaryId = resolvedLocalDict.id,
                        strategy = snapshot.strategy,
                        pools = snapshot.pools,
                        wordUidToId = wordUidToId
                    )
                }
            }
        }

        for (key in allStudyKeys) {
            val cloud = cloudStudyByUid[key]
            val local = localStudyByUid[key]
            val wordId = wordUidToId[key.first] ?: continue

            if (local == null && cloud != null) {
                // Cloud-only study state
                studyRepository.upsertStudyState(cloud.toDomain(wordId))
            } else if (local != null && cloud != null) {
                // Both exist -> keep the one with more recent lastReviewAt
                if (cloud.lastReviewAt > local.lastReviewAt) {
                    studyRepository.upsertStudyState(cloud.toDomain(wordId))
                }
            }
        }

        mergeUnits(
            dictionaryId = resolvedLocalDict.id,
            cloudDictionary = cloudJson,
            wordUidToId = wordUidToId
        )

        if (didMutateWords) {
            wordRepository.recomputeAllAssociationsForDictionary(resolvedLocalDict.id)
        }

        // Return merged JSON (will be re-exported from local DB later)
        return localJson
    }

    private suspend fun reconcileDictionaryMetadata(
        localDict: Dictionary,
        cloudJson: DictionaryJsonModel
    ): Dictionary {
        var merged = localDict
        val cloudUid = cloudJson.dictionaryUid.trim()
        if (cloudUid.isNotBlank() && cloudUid != merged.dictionaryUid) {
            merged = merged.copy(dictionaryUid = cloudUid)
        }

        val cloudUpdatedAt = cloudJson.updatedAt.takeIf { cloudJson.schemaVersion >= 8 && it > 0 } ?: 0L
        if (cloudUpdatedAt > merged.updatedAt) {
            merged = merged.copy(
                name = cloudJson.name.ifBlank { merged.name },
                description = cloudJson.description,
                color = cloudJson.color,
                createdAt = cloudJson.createdAt.takeIf { it > 0 } ?: merged.createdAt,
                updatedAt = cloudUpdatedAt
            )
        }

        if (merged != localDict) {
            dictionaryRepository.updateDictionary(merged.copy(wordCount = localDict.wordCount))
        }
        return merged.copy(wordCount = localDict.wordCount)
    }

    private suspend fun mergeUnits(
        dictionaryId: Long,
        cloudDictionary: DictionaryJsonModel,
        wordUidToId: Map<String, Long>
    ) {
        val localUnits = unitRepository.getUnitsByDictionary(dictionaryId)
        val remainingCloudUnits = cloudDictionary.units.toMutableList()

        localUnits.forEach { localUnit ->
            val cloudUnit = takeMatchingCloudUnit(localUnit, remainingCloudUnits) ?: return@forEach
            mergeUnitSnapshot(localUnit, cloudUnit, wordUidToId, cloudDictionary.schemaVersion)
        }

        remainingCloudUnits.forEach { cloudUnit ->
            importCloudUnit(dictionaryId, cloudUnit, wordUidToId)
        }
    }

    private suspend fun mergeUnitSnapshot(
        localUnit: StudyUnit,
        cloudUnit: UnitJsonModel,
        wordUidToId: Map<String, Long>,
        dictionarySchemaVersion: Int
    ) {
        val cloudWordIds = cloudUnit.wordUids.mapNotNull { wordUidToId[it] }.distinct().toSet()
        val localWordIds = unitRepository.getWordIdsInUnit(localUnit.id).toSet()
        val cloudUpdatedAt = cloudUnit.updatedAt.takeIf { dictionarySchemaVersion >= 8 && it > 0 } ?: 0L
        val localUpdatedAt = localUnit.updatedAt.takeIf { it > 0 } ?: 0L
        var nextUnit = localUnit

        val cloudUnitUid = cloudUnit.unitUid.trim()
        if (cloudUnitUid.isNotBlank() && cloudUnitUid != nextUnit.unitUid) {
            nextUnit = nextUnit.copy(unitUid = cloudUnitUid)
        }

        when {
            cloudUpdatedAt > localUpdatedAt -> {
                val toRemove = localWordIds.filter { it !in cloudWordIds }
                val toAdd = cloudWordIds.filter { it !in localWordIds }
                if (toRemove.isNotEmpty()) {
                    unitRepository.removeWordsFromUnit(localUnit.id, toRemove, touchUpdatedAt = false)
                }
                if (toAdd.isNotEmpty()) {
                    unitRepository.addWordsToUnit(localUnit.id, toAdd.toList(), touchUpdatedAt = false)
                }
                nextUnit = nextUnit.copy(
                    name = cloudUnit.name,
                    defaultRepeatCount = cloudUnit.repeatCount,
                    createdAt = cloudUnit.createdAt.takeIf { it > 0 } ?: nextUnit.createdAt,
                    updatedAt = cloudUpdatedAt
                )
            }

            cloudUpdatedAt == localUpdatedAt && cloudUpdatedAt > 0L -> {
                val toAdd = cloudWordIds.filter { it !in localWordIds }
                if (toAdd.isNotEmpty()) {
                    unitRepository.addWordsToUnit(localUnit.id, toAdd.toList(), touchUpdatedAt = false)
                }
            }

            else -> {
                if (cloudUnit.repeatCount > localUnit.defaultRepeatCount) {
                    nextUnit = nextUnit.copy(defaultRepeatCount = cloudUnit.repeatCount)
                }
                val toAdd = cloudWordIds.filter { it !in localWordIds }
                if (toAdd.isNotEmpty()) {
                    unitRepository.addWordsToUnit(localUnit.id, toAdd.toList(), touchUpdatedAt = false)
                }
            }
        }

        if (nextUnit != localUnit) {
            unitRepository.updateUnit(nextUnit)
        }
    }

    private suspend fun importCloudUnit(
        dictionaryId: Long,
        cloudUnit: UnitJsonModel,
        wordUidToId: Map<String, Long>
    ) {
        val unitId = unitRepository.insertUnit(
            StudyUnit(
                dictionaryId = dictionaryId,
                unitUid = cloudUnit.unitUid.ifBlank { UUID.randomUUID().toString() },
                name = cloudUnit.name,
                defaultRepeatCount = cloudUnit.repeatCount,
                createdAt = cloudUnit.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
                updatedAt = cloudUnit.updatedAt.takeIf { it > 0 } ?: System.currentTimeMillis()
            )
        )
        val wordIds = cloudUnit.wordUids.mapNotNull { wordUidToId[it] }.distinct()
        if (wordIds.isNotEmpty()) {
            unitRepository.addWordsToUnit(unitId, wordIds, touchUpdatedAt = false)
        }
    }

    private fun takeMatchingCloudDictionary(
        localDict: Dictionary,
        remainingCloudDicts: MutableList<DictionaryJsonModel>
    ): DictionaryJsonModel? {
        val byUidIndex = localDict.dictionaryUid
            .takeIf { it.isNotBlank() }
            ?.let { uid -> remainingCloudDicts.indexOfFirst { it.dictionaryUid == uid && it.dictionaryUid.isNotBlank() } }
            ?: -1
        if (byUidIndex >= 0) {
            return remainingCloudDicts.removeAt(byUidIndex)
        }

        val byNameIndex = remainingCloudDicts.indexOfFirst { it.name == localDict.name }
        if (byNameIndex >= 0) {
            return remainingCloudDicts.removeAt(byNameIndex)
        }
        return null
    }

    private fun takeMatchingCloudUnit(
        localUnit: StudyUnit,
        remainingCloudUnits: MutableList<UnitJsonModel>
    ): UnitJsonModel? {
        val byUidIndex = localUnit.unitUid
            .takeIf { it.isNotBlank() }
            ?.let { uid -> remainingCloudUnits.indexOfFirst { it.unitUid == uid && it.unitUid.isNotBlank() } }
            ?: -1
        if (byUidIndex >= 0) {
            return remainingCloudUnits.removeAt(byUidIndex)
        }

        val byNameIndex = remainingCloudUnits.indexOfFirst { it.name == localUnit.name }
        if (byNameIndex >= 0) {
            return remainingCloudUnits.removeAt(byNameIndex)
        }
        return null
    }

    private fun resolveCloudWordUpdates(
        dictionaryId: Long,
        localWords: List<WordDetails>,
        cloudUpdates: List<DictionaryWordMergePlanner.CloudWordUpdate>
    ): List<DictionaryWordUpdatePlanner.Candidate> {
        val localWordsByUid = localWords
            .filter { it.wordUid.isNotBlank() }
            .associateBy { it.wordUid }
        val localWordsByNormalized = localWords.associateBy { it.normalizedSpelling }

        return cloudUpdates.mapNotNull { update ->
            val cloudWord = update.cloudWord
            val existing = update.localWordUid
                ?.let(localWordsByUid::get)
                ?: localWordsByNormalized[update.localNormalizedSpelling]
                ?: throw IllegalStateException(
                    "Cloud update word not found locally: ${cloudWord.spelling}"
                )

            if (
                cloudWord.wordUid.isNotBlank() &&
                existing.wordUid.isNotBlank() &&
                cloudWord.wordUid != existing.wordUid
            ) {
                throw IllegalStateException(
                    "Cloud UID differs from local for '${cloudWord.spelling}', cannot sync safely. Please resolve this entry manually first."
                )
            }

            DictionaryWordUpdatePlanner.Candidate(
                existingWord = existing,
                finalWord = cloudWord.toWordDetails(
                    dictionaryId = dictionaryId,
                    existingId = existing.id,
                    fallbackWordUid = existing.wordUid,
                    fallbackCreatedAt = existing.createdAt,
                    fallbackUpdatedAt = existing.updatedAt
                )
            )
        }
    }

    private suspend fun applyWordUpdatePlan(
        plan: DictionaryWordUpdatePlanner.Plan,
        localWords: List<WordDetails>
    ) {
        if (plan.finalUpdates.isEmpty()) return

        val localWordsById = localWords.associateBy { it.id }

        plan.temporaryRenameIds.forEach { wordId ->
            val existing = localWordsById[wordId]
                ?: throw IllegalStateException("Local word record to rename not found during sync: $wordId")
            wordRepository.updateWord(
                existing.copy(
                    normalizedSpelling = temporaryNormalizedSpelling(existing.id)
                )
            )
        }

        plan.finalUpdates.forEach { finalWord ->
            wordRepository.updateWord(finalWord)
        }
    }

    private fun temporaryNormalizedSpelling(wordId: Long): String {
        return "__sync_tmp__${wordId}__${UUID.randomUUID()}"
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
                // Cloud-only -> import
                importArticleFromJson(cloud, null, supportsStructuredContent = (cloudArticlesModel.schemaVersion >= 2))
            } else if (local != null && cloud != null) {
                // Both exist -> keep newer
                if (cloud.updatedAt > local.updatedAt) {
                    importArticleFromJson(cloud, local, supportsStructuredContent = (cloudArticlesModel.schemaVersion >= 2))
                }
            }
            // local-only -> no action needed, will be uploaded
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

    // Export Helpers

    private suspend fun exportLocalDictionary(dict: Dictionary): DictionaryJsonModel {
        val json = exportDictionary(dict)
        val model = dictAdapter.fromJson(json) ?: throw IllegalStateException("Failed to parse exported dict")
        val normalizedModel = dictionaryWordUidNormalizer.normalize(model)

        // Enrich with word pools
        val words = ensureDictionaryWordUids(dict.id, dict.name)
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
                    algorithmVersion = algorithmVersion,
                    updatedAt = poolEntity?.updatedAt ?: 0L,
                    qualityScore = poolEntity?.qualityScore
                )
            )
        }

        return dictionaryWordUidNormalizer.normalize(
            normalizedModel.copy(wordPools = wordPools)
        )
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

        val suitabilityScore = articleJson.suitabilityScore ?: existing?.suitabilityScore
        val suitabilityReason = if (articleJson.suitabilityReason.isNotBlank()) {
            articleJson.suitabilityReason
        } else {
            existing?.suitabilityReason.orEmpty()
        }
        val suitabilityUpdatedAt = articleJson.suitabilityUpdatedAt ?: existing?.suitabilityUpdatedAt
        val suitabilityModel = if (articleJson.suitabilityModel.isNotBlank()) {
            articleJson.suitabilityModel
        } else {
            existing?.suitabilityModel.orEmpty()
        }

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
            categoryId = articleJson.categoryId,
            suitabilityScore = suitabilityScore,
            suitabilityReason = suitabilityReason,
            suitabilityUpdatedAt = suitabilityUpdatedAt,
            suitabilityModel = suitabilityModel
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
            val words = ensureDictionaryWordUids(dict.id, dict.name)
            for (word in words) {
                val previous = map.put(word.wordUid, word.id)
                if (previous != null && previous != word.id) {
                    throw IllegalStateException("Duplicate global wordUid: ${word.wordUid}")
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
        suitabilityScore = suitabilityScore,
        suitabilityReason = suitabilityReason,
        suitabilityUpdatedAt = suitabilityUpdatedAt,
        suitabilityModel = suitabilityModel,
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

        pools.groupBy { it.strategy.ifBlank { "BALANCED" } }
            .forEach { (strategy, strategyPools) ->
                replaceWordPoolStrategy(
                    dictionaryId = dictionaryId,
                    strategy = strategy,
                    pools = strategyPools,
                    wordUidToId = wordUidToId
                )
            }
    }

    private suspend fun replaceWordPoolStrategy(
        dictionaryId: Long,
        strategy: String,
        pools: List<WordPoolJsonModel>,
        wordUidToId: Map<String, Long>
    ) {
        wordPoolDao.deleteByDictionaryAndStrategy(dictionaryId, strategy)
        if (pools.isEmpty()) return

        pools.forEach { pool ->
            val memberUids = pool.memberWordUids.filter { it.isNotBlank() }.distinct()
            val memberIds = memberUids.mapNotNull { wordUidToId[it] }.distinct()
            if (memberIds.size != memberUids.size) {
                throw IllegalStateException(
                    "Pool strategy '$strategy' references non-existent words, cannot sync safely."
                )
            }
            if (memberIds.size < 2) {
                throw IllegalStateException(
                    "Pool strategy '$strategy' contains invalid pools with fewer than 2 members, cannot sync safely."
                )
            }

            val focusId = pool.focusWordUid?.let { focusUid ->
                wordUidToId[focusUid]
                    ?: throw IllegalStateException("Pool strategy '$strategy' focus word not found, cannot sync safely.")
            }
            val algorithmVersion = pool.algorithmVersion.ifBlank { "${strategy}_v1" }
            val updatedAt = pool.updatedAt.takeIf { it > 0 } ?: System.currentTimeMillis()

            val poolId = wordPoolDao.insertPool(
                WordPoolEntity(
                    dictionaryId = dictionaryId,
                    focusWordId = focusId,
                    strategy = strategy,
                    algorithmVersion = algorithmVersion,
                    updatedAt = updatedAt,
                    qualityScore = pool.qualityScore
                )
            )
            wordPoolDao.insertMembers(
                memberIds.map { WordPoolMemberEntity(wordId = it, poolId = poolId) }
            )
        }
    }

    // Import Helper

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
                            wordUid = word.wordUid
                        )
                        val wordId = wordRepository.insertWord(wordWithDict)
                        wordUidToId[wordWithDict.wordUid] = wordId
                    }
                    dictionaryRepository.updateWordCount(dictId)

                    result.units.forEach { unitData ->
                        val unitId = unitRepository.insertUnit(
                            StudyUnit(
                                dictionaryId = dictId,
                                unitUid = unitData.unitUid,
                                name = unitData.name,
                                defaultRepeatCount = unitData.repeatCount,
                                createdAt = unitData.createdAt,
                                updatedAt = unitData.updatedAt
                            )
                        )
                        val wordIds = unitData.wordUids.mapNotNull { wordUidToId[it] }
                        if (wordIds.isNotEmpty()) {
                            unitRepository.addWordsToUnit(unitId, wordIds, touchUpdatedAt = false)
                        }
                    }

                    result.studyStates.forEach { stateData ->
                        val wordId = wordUidToId[stateData.wordUid] ?: return@forEach
                        studyRepository.upsertStudyState(
                            WordStudyState(
                                wordId = wordId,
                                studyMode = stateData.studyMode,
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
            throw IllegalStateException("Failed to import dictionary '${cloudDict.name}': ${e.message}", e)
        }
    }

    private suspend fun replaceAllArticlesFromCloud(
        cloudArticles: ArticlesExportModel?,
        cloudCategories: ArticleCategoriesExportModel?
    ) {
        clearAllLocalArticles()
        replaceCategoriesFromCloud(cloudCategories)

        if (cloudArticles == null) {
            normalizeArticleCategories()
            return
        }

        val supportsStructuredContent = (cloudArticles.schemaVersion ?: 1) >= 2
        cloudArticles.articles.forEach { articleJson ->
            importArticleFromJson(articleJson, existing = null, supportsStructuredContent = supportsStructuredContent)
        }
        normalizeArticleCategories()
    }

    private suspend fun replaceAllDictionariesFromCloud(cloudDicts: List<DictionaryJsonModel>) {
        clearAllLocalDictionaries()
        cloudDicts.forEach { cloudDict ->
            importCloudDictionary(cloudDict)
        }
    }

    private suspend fun replaceQuestionBankFromCloud(cloudQuestionBank: QuestionBankExportModel?) {
        clearQuestionBank()
        if (cloudQuestionBank == null || cloudQuestionBank.papers.isEmpty()) return
        val articleUidToId = buildArticleUidToIdMap()
        importQuestionBank(cloudQuestionBank, articleUidToId)
    }

    private suspend fun replaceWordExamplesFromCloud(cloudWordExamples: WordExamplesExportModel?) {
        articleDao.deleteAllExamples()
        if (cloudWordExamples == null || cloudWordExamples.examples.isEmpty()) return
        val wordUidToId = buildWordUidToIdMap()
        val articleUidToId = buildArticleUidToIdMap()
        importWordExamples(cloudWordExamples, wordUidToId, articleUidToId)
    }

    private suspend fun clearAllLocalArticles() {
        articleDao.getAllArticleIds().forEach { articleId ->
            articleDao.deleteArticleCascade(articleId)
        }
        articleDao.deleteAllExamples()
    }

    private suspend fun clearAllLocalDictionaries() {
        val localDicts = dictionaryRepository.getAllDictionaries().first()
        localDicts.forEach { dictionary ->
            wordEdgeDao.deleteByDictionary(dictionary.id)
            wordEdgeDao.deleteExcludedByDictionary(dictionary.id)
            dictionaryRepository.deleteDictionary(dictionary.id)
        }
    }

    private suspend fun clearQuestionBank() {
        val localPapers = questionBankDao.getAllExamPapers().first()
        localPapers.forEach { paper ->
            questionBankDao.deleteExamPaper(paper.id)
        }
    }

    private suspend fun mergePlanBackup(local: PlanBackup, cloud: PlanBackup?): PlanBackup {
        if (cloud == null) return local
        if (cloud.schemaVersion > 1) {
            throw IllegalStateException("Unsupported plan schema version: ${cloud.schemaVersion}")
        }
        if (local.isEmpty() && cloud.isEmpty()) return local
        if (local.isEmpty()) {
            planRepository.replaceFromBackup(cloud)
            return cloud
        }
        if (cloud.isEmpty()) return local

        // Use business-update timestamp as the primary version signal.
        // `exportedAt` reflects export time and can be newer even when the data is older.
        val localVersionTime = if (local.latestUpdatedAt > 0L) local.latestUpdatedAt else local.exportedAt
        val cloudVersionTime = if (cloud.latestUpdatedAt > 0L) cloud.latestUpdatedAt else cloud.exportedAt
        return if (cloudVersionTime > localVersionTime) {
            planRepository.replaceFromBackup(cloud)
            cloud
        } else {
            local
        }
    }

    private fun PlanBackup.toJsonModel(): PlanExportJsonModel {
        return PlanExportJsonModel(
            schemaVersion = schemaVersion,
            exportedAt = exportedAt,
            templates = templates.map {
                PlanTemplateJsonModel(
                    id = it.id,
                    name = it.name,
                    isActive = it.isActive,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt
                )
            },
            items = items.map {
                PlanItemJsonModel(
                    id = it.id,
                    templateId = it.templateId,
                    taskType = it.taskType,
                    title = it.title,
                    targetCount = it.targetCount,
                    autoEnabled = it.autoEnabled,
                    autoSource = it.autoSource,
                    orderIndex = it.orderIndex,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt
                )
            },
            dayRecords = dayRecords.map {
                PlanDayRecordJsonModel(
                    id = it.id,
                    dayStart = it.dayStart,
                    itemId = it.itemId,
                    doneCount = it.doneCount,
                    isCompleted = it.isCompleted,
                    updatedAt = it.updatedAt,
                    completedAt = it.completedAt
                )
            },
            eventLogs = eventLogs.map {
                PlanEventLogJsonModel(
                    id = it.id,
                    dayStart = it.dayStart,
                    eventKey = it.eventKey,
                    source = it.source,
                    createdAt = it.createdAt
                )
            }
        )
    }

    private fun PlanExportJsonModel.toDomainBackup(): PlanBackup {
        return PlanBackup(
            schemaVersion = schemaVersion,
            exportedAt = exportedAt,
            templates = templates.map {
                PlanTemplateBackup(
                    id = it.id,
                    name = it.name,
                    isActive = it.isActive,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt
                )
            },
            items = items.map {
                PlanItemBackup(
                    id = it.id,
                    templateId = it.templateId,
                    taskType = it.taskType,
                    title = it.title,
                    targetCount = it.targetCount,
                    autoEnabled = it.autoEnabled,
                    autoSource = it.autoSource,
                    orderIndex = it.orderIndex,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt
                )
            },
            dayRecords = dayRecords.map {
                PlanDayRecordBackup(
                    id = it.id,
                    dayStart = it.dayStart,
                    itemId = it.itemId,
                    doneCount = it.doneCount,
                    isCompleted = it.isCompleted,
                    updatedAt = it.updatedAt,
                    completedAt = it.completedAt
                )
            },
            eventLogs = eventLogs.map {
                PlanEventLogBackup(
                    id = it.id,
                    dayStart = it.dayStart,
                    eventKey = it.eventKey,
                    source = it.source,
                    createdAt = it.createdAt
                )
            }
        )
    }

    // Question Bank Export/Import/Merge

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
            throw IllegalStateException("Unsupported question bank schema version: ${model.schemaVersion}, please upgrade the app")
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
                // Cloud-only -> import
                importQuestionBank(
                    QuestionBankExportModel(papers = listOf(cloud)),
                    articleUidToId
                )
            } else if (local != null && cloud != null) {
                // Both exist -> newer wins (using effective updatedAt that includes child changes)
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
            // local-only -> keep (will be uploaded)
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

    // GitHub API Helpers

    private suspend fun <T> downloadJson(path: String, adapter: com.squareup.moshi.JsonAdapter<T>): T? {
        return downloadJson(mainRepoTarget(), path, adapter)
    }

    private suspend fun <T> downloadJson(
        target: GitHubRepoTarget,
        path: String,
        adapter: com.squareup.moshi.JsonAdapter<T>
    ): T? {
        val response = gitHubApi.getContent(authHeader(), target.owner, target.repo, path)
        if (!response.isSuccessful) {
            if (response.code() == 404) return null
            throw IllegalStateException("GitHub API error: ${response.code()} ${response.message()}")
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
                    throw IllegalStateException("Download failed: ${rawResponse.code()} ${rawResponse.message()}")
                }
                rawResponse.body()?.string() ?: throw IllegalStateException("Download failed: empty response")
            }
            else -> return null
        }
        return adapter.fromJson(decoded)
    }

    private fun WordJsonModel.toWordDetails(
        dictionaryId: Long,
        existingId: Long = 0,
        fallbackWordUid: String = "",
        fallbackCreatedAt: Long = System.currentTimeMillis(),
        fallbackUpdatedAt: Long = System.currentTimeMillis()
    ): WordDetails {
        return WordDetails(
            id = existingId,
            dictionaryId = dictionaryId,
            spelling = spelling,
            phonetic = phonetic,
            meanings = meanings.map { Meaning(pos = it.pos, definition = it.definition) },
            rootExplanation = rootExplanation,
            normalizedSpelling = spelling.trim().lowercase(),
            wordUid = wordUid.ifBlank { fallbackWordUid },
            synonyms = synonyms.map { SynonymInfo(word = it.word, explanation = it.explanation) },
            similarWords = similarWords.map {
                SimilarWordInfo(word = it.word, meaning = it.meaning, explanation = it.explanation)
            },
            cognates = cognates.map {
                CognateInfo(word = it.word, meaning = it.meaning, sharedRoot = it.sharedRoot)
            },
            decomposition = decomposition.map {
                DecompositionPart(
                    segment = it.segment,
                    role = runCatching { MorphemeRole.valueOf(it.role) }.getOrDefault(MorphemeRole.OTHER),
                    meaning = it.meaning
                )
            },
            inflections = inflections.map { Inflection(form = it.form, formType = it.formType) },
            createdAt = createdAt.takeIf { it > 0 } ?: fallbackCreatedAt,
            updatedAt = updatedAt.takeIf { it > 0 } ?: fallbackUpdatedAt
        )
    }

    private fun StudyStateJsonModel.toDomain(wordId: Long): WordStudyState {
        return WordStudyState(
            wordId = wordId,
            studyMode = parseStudyModeName(mode),
            state = state,
            step = step,
            stability = stability,
            difficulty = difficulty,
            due = due,
            lastReviewAt = lastReviewAt,
            reps = reps,
            lapses = lapses
        )
    }

    private fun normalizeStudyModeName(raw: String): String =
        raw.trim().ifBlank { StudyMode.NORMAL.name }.uppercase()

    private fun parseStudyModeName(raw: String): StudyMode =
        StudyMode.entries.firstOrNull { it.name == normalizeStudyModeName(raw) } ?: StudyMode.NORMAL

    private suspend fun <T> uploadJson(path: String, data: T, adapter: com.squareup.moshi.JsonAdapter<T>) {
        uploadJson(mainRepoTarget(), path, data, adapter)
    }

    private suspend fun <T> uploadJson(
        target: GitHubRepoTarget,
        path: String,
        data: T,
        adapter: com.squareup.moshi.JsonAdapter<T>
    ) {
        val json = adapter.toJson(data)
        val encoded = Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

        // Get existing SHA for update (CAS)
        val existingResponse = gitHubApi.getContent(authHeader(), target.owner, target.repo, path)
        val sha = if (existingResponse.isSuccessful) existingResponse.body()?.sha else null

        val putRequest = GitHubPutRequest(
            message = "sync: update $path",
            content = encoded,
            sha = sha
        )
        val putResponse = gitHubApi.putContent(authHeader(), target.owner, target.repo, path, putRequest)
        if (!putResponse.isSuccessful) {
            throw IllegalStateException("Upload failed: $path (${putResponse.code()} ${putResponse.message()})")
        }
    }
}

