package com.xty.englishhelper.data.repository

import android.os.Build
import android.util.Base64
import com.squareup.moshi.Moshi
import com.xty.englishhelper.BuildConfig
import com.xty.englishhelper.data.json.ArticleJsonModel
import com.xty.englishhelper.data.json.ArticlesExportModel
import com.xty.englishhelper.data.json.DictionaryJsonModel
import com.xty.englishhelper.data.json.InflectionJsonModel
import com.xty.englishhelper.data.json.StudyStateJsonModel
import com.xty.englishhelper.data.json.SyncManifest
import com.xty.englishhelper.data.json.UnitJsonModel
import com.xty.englishhelper.data.json.WordPoolJsonModel
import com.xty.englishhelper.data.local.dao.WordPoolDao
import com.xty.englishhelper.data.local.entity.WordPoolEntity
import com.xty.englishhelper.data.local.entity.WordPoolMemberEntity
import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.data.remote.GitHubApiService
import com.xty.englishhelper.data.remote.dto.GitHubPutRequest
import com.xty.englishhelper.domain.model.Article
import com.xty.englishhelper.domain.model.ArticleParseStatus
import com.xty.englishhelper.domain.model.ArticleSourceType
import com.xty.englishhelper.domain.model.Dictionary
import com.xty.englishhelper.domain.model.StudyUnit
import com.xty.englishhelper.domain.model.WordStudyState
import com.xty.englishhelper.domain.repository.ArticleRepository
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
import com.xty.englishhelper.domain.usecase.importexport.ImportDictionaryUseCase
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
    private val wordPoolRepository: WordPoolRepository,
    private val wordPoolDao: WordPoolDao,
    private val importExporter: DictionaryImportExporter,
    private val exportDictionary: ExportDictionaryUseCase,
    private val transactionRunner: TransactionRunner,
    private val moshi: Moshi
) : CloudSyncRepository {

    private val dictAdapter = moshi.adapter(DictionaryJsonModel::class.java).indent("  ")
    private val articlesAdapter = moshi.adapter(ArticlesExportModel::class.java).indent("  ")
    private val manifestAdapter = moshi.adapter(SyncManifest::class.java).indent("  ")

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

        // 2. Read local data
        onProgress(SyncProgress("读取中", "正在读取本地数据...", 1, 4))
        val localDicts = dictionaryRepository.getAllDictionaries().first()
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
        val articlesExport = ArticlesExportModel(
            schemaVersion = 1,
            articles = allArticles.map { it.toArticleJson() }
        )
        uploadJson("articles.json", articlesExport, articlesAdapter)

        // Upload manifest
        val manifest = SyncManifest(
            appVersion = BuildConfig.VERSION_NAME,
            schemaVersion = 5,
            syncedAt = System.currentTimeMillis(),
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
            dictionaries = fileNames,
            hasArticles = allArticles.isNotEmpty()
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
        val articlesExport = ArticlesExportModel(
            schemaVersion = 1,
            articles = allArticles.map { it.toArticleJson() }
        )
        uploadJson("articles.json", articlesExport, articlesAdapter)

        // Upload manifest
        val manifest = SyncManifest(
            appVersion = BuildConfig.VERSION_NAME,
            schemaVersion = 5,
            syncedAt = System.currentTimeMillis(),
            deviceName = "${Build.MANUFACTURER} ${Build.MODEL}",
            dictionaries = fileNames,
            hasArticles = allArticles.isNotEmpty()
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

        // Import cloud data
        onProgress(SyncProgress("导入中", "正在导入辞书...", 2, 3))
        for (dict in cloudDicts) {
            importCloudDictionary(dict)
        }

        // Import articles
        if (cloudArticles != null) {
            for (articleJson in cloudArticles.articles) {
                val article = Article(
                    articleUid = articleJson.articleUid.ifBlank { UUID.randomUUID().toString() },
                    title = articleJson.title,
                    content = articleJson.content,
                    domain = articleJson.domain,
                    difficultyAi = articleJson.difficultyAi,
                    sourceType = try { ArticleSourceType.valueOf(articleJson.sourceType) } catch (_: Exception) { ArticleSourceType.MANUAL },
                    parseStatus = ArticleParseStatus.PENDING,
                    createdAt = articleJson.createdAt,
                    updatedAt = articleJson.updatedAt
                )
                articleRepository.upsertArticle(article)
            }
        }

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
                val article = Article(
                    articleUid = cloud.articleUid,
                    title = cloud.title,
                    content = cloud.content,
                    domain = cloud.domain,
                    difficultyAi = cloud.difficultyAi,
                    sourceType = try { ArticleSourceType.valueOf(cloud.sourceType) } catch (_: Exception) { ArticleSourceType.MANUAL },
                    parseStatus = ArticleParseStatus.PENDING,
                    createdAt = cloud.createdAt,
                    updatedAt = cloud.updatedAt
                )
                articleRepository.upsertArticle(article)
            } else if (local != null && cloud != null) {
                // Both exist → keep newer
                if (cloud.updatedAt > local.updatedAt) {
                    val updated = local.copy(
                        title = cloud.title,
                        content = cloud.content,
                        domain = cloud.domain,
                        difficultyAi = cloud.difficultyAi,
                        updatedAt = cloud.updatedAt,
                        parseStatus = ArticleParseStatus.PENDING
                    )
                    articleRepository.upsertArticle(updated)
                }
            }
            // local-only → no action needed, will be uploaded
        }

        return articleRepository.getAllArticles().first()
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
        val poolVersions = wordPoolRepository.getPoolVersionInfo(dict.id)

        // We need focus word info - get pools for each word
        val wordToPoolsMap = wordPoolRepository.getWordToPoolsMap(dict.id)
        val poolFocusWords = mutableMapOf<Long, Long?>()
        for (word in words) {
            val poolIds = wordToPoolsMap[word.id] ?: continue
            for (poolId in poolIds) {
                // Pool's focus word is determined by the pool entity
                // We'll use the pools data to build this
            }
        }

        // Get pool entities via DAO - need to access through the repository
        // Since WordPoolRepository doesn't expose raw pool entities, we build pools from available data
        val wordPools = mutableListOf<WordPoolJsonModel>()
        for ((poolId, memberIds) in poolToMembers) {
            val memberUids = memberIds.mapNotNull { wordIdToUid[it] }
            // Find version info (all pools in same dict share strategy)
            val versionInfo = poolVersions.firstOrNull()
            wordPools.add(
                WordPoolJsonModel(
                    focusWordUid = null, // Pool focus is optional
                    memberWordUids = memberUids,
                    strategy = versionInfo?.first ?: "BALANCED",
                    algorithmVersion = versionInfo?.second ?: "BALANCED_v1"
                )
            )
        }

        return model.copy(wordPools = wordPools)
    }

    private fun Article.toArticleJson() = ArticleJsonModel(
        articleUid = articleUid.ifBlank { UUID.randomUUID().toString() },
        title = title,
        content = content,
        domain = domain,
        difficultyAi = difficultyAi,
        sourceType = sourceType.name,
        createdAt = createdAt,
        updatedAt = updatedAt
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
