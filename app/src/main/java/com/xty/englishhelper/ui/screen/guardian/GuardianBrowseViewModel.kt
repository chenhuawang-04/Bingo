package com.xty.englishhelper.ui.screen.guardian

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xty.englishhelper.data.preferences.SettingsDataStore
import com.xty.englishhelper.domain.model.ArticleParagraph
import com.xty.englishhelper.domain.model.OnlineReadingSource
import com.xty.englishhelper.domain.repository.ArticleAiRepository
import com.xty.englishhelper.domain.repository.ArticleRepository
import com.xty.englishhelper.domain.repository.AtlanticRepository
import com.xty.englishhelper.domain.repository.CsMonitorRepository
import com.xty.englishhelper.domain.repository.GuardianRepository
import com.xty.englishhelper.ui.screen.article.ArticleLengthFilter
import com.xty.englishhelper.ui.screen.article.ArticleScoreFilter
import com.xty.englishhelper.ui.screen.article.ArticleSortOption
import com.xty.englishhelper.ui.screen.article.applyArticlePresentation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

data class GuardianSection(val key: String, val label: String, val group: String = "")

data class GuardianBrowseItem(
    val title: String,
    val url: String,
    val trailText: String? = null,
    val thumbnailUrl: String? = null,
    val author: String? = null,
    val coverImageUrl: String? = null,
    val wordCount: Int? = null,
    val isAuthorLoading: Boolean = false,
    val isWordCountLoading: Boolean = false,
    val detailError: String? = null,
    val suitabilityScore: Int? = null,
    val suitabilityReason: String? = null,
    val suitabilityEvaluatedAt: Long? = null,
    val isEvaluating: Boolean = false,
    val evaluationExcerpt: String? = null
)

val guardianSections = listOf(
    // Main
    GuardianSection("international", "\u9996\u9875", "Main"),
    GuardianSection("world", "World", "Main"),
    GuardianSection("us-news", "US News", "Main"),
    GuardianSection("uk-news", "UK News", "Main"),
    // Topics
    GuardianSection("science", "Science", "Topics"),
    GuardianSection("uk/technology", "Tech", "Topics"),
    GuardianSection("uk/environment", "Environment", "Topics"),
    GuardianSection("global-development", "Development", "Topics"),
    GuardianSection("uk/business", "Business", "Topics"),
    // Culture
    GuardianSection("books", "Books", "Culture"),
    GuardianSection("uk/culture", "Culture", "Culture"),
    GuardianSection("uk/film", "Film", "Culture"),
    GuardianSection("music", "Music", "Culture"),
    GuardianSection("stage", "Stage", "Culture"),
    GuardianSection("uk/tv-and-radio", "TV & Radio", "Culture"),
    GuardianSection("artanddesign", "Art", "Culture"),
    GuardianSection("games", "Games", "Culture"),
    // Lifestyle
    GuardianSection("food", "Food", "Lifestyle"),
    GuardianSection("fashion", "Fashion", "Lifestyle"),
    GuardianSection("uk/travel", "Travel", "Lifestyle"),
    // Sport
    GuardianSection("uk/sport", "Sport", "Sport"),
    GuardianSection("football", "Football", "Sport"),
    GuardianSection("sport/cricket", "Cricket", "Sport"),
    GuardianSection("sport/tennis", "Tennis", "Sport"),
    GuardianSection("sport/formulaone", "F1", "Sport"),
    // Opinion
    GuardianSection("uk/commentisfree", "Opinion", "Opinion"),
    // World regions
    GuardianSection("world/middleeast", "Middle East", "World"),
    GuardianSection("world/ukraine", "Ukraine", "World"),
    GuardianSection("us-news/us-politics", "US Politics", "World")
)

val csMonitorSections = listOf(
    GuardianSection("", "首页", "Main"),
    GuardianSection("World", "World", "Main"),
    GuardianSection("USA", "USA", "Main"),
    GuardianSection("Business", "Business", "Main"),
    GuardianSection("Environment", "Environment", "Topics"),
    GuardianSection("Editorials", "Editorials", "Opinion"),
    GuardianSection("The-Culture", "Culture", "Culture"),
    GuardianSection("The-Culture/Faith-Religion", "Faith & Religion", "Culture"),
    GuardianSection("Podcasts", "Podcasts", "Media"),
    GuardianSection("magazine", "Magazine", "Media")
)

val atlanticSections = listOf(
    GuardianSection("", "首页", "Main"),
    GuardianSection("latest", "Latest", "Main"),
    GuardianSection("most-popular", "Popular", "Main"),
    GuardianSection("politics", "Politics", "Topics"),
    GuardianSection("ideas", "Ideas", "Topics"),
    GuardianSection("technology", "Technology", "Topics"),
    GuardianSection("science", "Science", "Topics"),
    GuardianSection("health", "Health", "Topics"),
    GuardianSection("education", "Education", "Topics"),
    GuardianSection("economy", "Economy", "Topics"),
    GuardianSection("culture", "Culture", "Culture"),
    GuardianSection("books", "Books", "Culture"),
    GuardianSection("family", "Family", "Culture"),
    GuardianSection("international", "Global", "World"),
    GuardianSection("national-security", "National Security", "World"),
    GuardianSection("photo", "Photo", "Media"),
    GuardianSection("projects", "Projects", "Media")
)

data class GuardianBrowseUiState(
    val sources: List<OnlineReadingSource> = OnlineReadingSource.values().toList(),
    val selectedSource: OnlineReadingSource = OnlineReadingSource.GUARDIAN,
    val sections: List<GuardianSection> = guardianSections,
    val selectedSection: String = "international",
    val allArticles: List<GuardianBrowseItem> = emptyList(),
    val articles: List<GuardianBrowseItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingArticle: Boolean = false,
    val error: String? = null,
    val isEvaluating: Boolean = false,
    val evaluatingCount: Int = 0,
    val lengthFilter: ArticleLengthFilter = ArticleLengthFilter.ALL,
    val scoreFilter: ArticleScoreFilter = ArticleScoreFilter.ALL,
    val sortOption: ArticleSortOption = ArticleSortOption.DEFAULT
)

@HiltViewModel
class GuardianBrowseViewModel @Inject constructor(
    private val guardianRepository: GuardianRepository,
    private val csMonitorRepository: CsMonitorRepository,
    private val atlanticRepository: AtlanticRepository,
    private val settingsDataStore: SettingsDataStore,
    private val articleRepository: ArticleRepository,
    private val articleAiRepository: ArticleAiRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GuardianBrowseUiState())
    val uiState: StateFlow<GuardianBrowseUiState> = _uiState.asStateFlow()

    private var detailJob: Job? = null
    private var sourceInitialized = false
    private val evaluationSemaphore = Semaphore(2)
    private val autoEvaluationJobs = ConcurrentHashMap<String, Job>()
    private val lastSectionBySource = mutableMapOf(
        OnlineReadingSource.GUARDIAN to "international",
        OnlineReadingSource.CSMONITOR to "",
        OnlineReadingSource.ATLANTIC to ""
    )

    init {
        viewModelScope.launch {
            settingsDataStore.onlineReadingSource.collectLatest { key ->
                val source = OnlineReadingSource.fromKey(key)
                if (!sourceInitialized || _uiState.value.selectedSource != source) {
                    sourceInitialized = true
                    applySource(source, forceReload = true)
                }
            }
        }
    }

    fun loadSection(section: String) {
        val source = _uiState.value.selectedSource
        detailJob?.cancel()
        detailJob = null
        cancelAutoEvaluationJobsExcept(emptySet())
        _uiState.update { it.copy(selectedSection = section, isLoading = true, error = null) }
        lastSectionBySource[source] = section

        viewModelScope.launch {
            try {
                val items = when (source) {
                    OnlineReadingSource.GUARDIAN -> {
                        guardianRepository.getSectionArticles(section).map { preview ->
                            GuardianBrowseItem(
                                title = preview.title,
                                url = preview.url,
                                trailText = preview.trailText,
                                thumbnailUrl = preview.thumbnailUrl,
                                author = preview.author,
                                isAuthorLoading = true,
                                isWordCountLoading = true
                            )
                        }
                    }
                    OnlineReadingSource.CSMONITOR -> {
                        csMonitorRepository.getSectionArticles(section).map { preview ->
                            GuardianBrowseItem(
                                title = preview.title,
                                url = preview.url,
                                trailText = preview.trailText,
                                thumbnailUrl = preview.thumbnailUrl,
                                author = preview.author,
                                isAuthorLoading = true,
                                isWordCountLoading = true
                            )
                        }
                    }
                    OnlineReadingSource.ATLANTIC -> {
                        atlanticRepository.getSectionArticles(section).map { preview ->
                            GuardianBrowseItem(
                                title = preview.title,
                                url = preview.url,
                                trailText = preview.trailText,
                                thumbnailUrl = preview.thumbnailUrl,
                                author = preview.author,
                                isAuthorLoading = true,
                                isWordCountLoading = true
                            )
                        }
                    }
                }
                val hydrated = hydrateSuitability(items)
                _uiState.update { it.withPresentedArticles(hydrated).copy(isLoading = false) }
                maybeEvaluateVisibleArticles(source = source, sectionKey = section)
                detailJob = viewModelScope.launch {
                    loadArticleDetails(hydrated, source)
                }
            } catch (e: Exception) {
                Log.e("GuardianBrowseVM", "Failed to load section: $section", e)
                _uiState.update { it.copy(isLoading = false, error = "加载失败：${e.message}") }
            }
        }
    }

    private suspend fun loadArticleDetails(items: List<GuardianBrowseItem>, source: OnlineReadingSource) {
        if (items.isEmpty()) return
        val concurrency = settingsDataStore.guardianDetailConcurrency.first().coerceIn(1, 10)
        val semaphore = Semaphore(concurrency)
        coroutineScope {
            items.forEach { item ->
                launch {
                    semaphore.withPermit {
                        try {
                            val detail = fetchArticleDetail(source, item.url)
                            val wordCount = detail.paragraphs
                                .joinToString(" ") { it.text }
                                .split(Regex("\\s+"))
                                .count { it.isNotBlank() }
                            val excerpt = buildExcerpt(detail.paragraphs)
                            _uiState.update { state ->
                                state.updateSourceArticle(item.url) { current ->
                                    current.copy(
                                        author = detail.author.takeIf { it.isNotBlank() },
                                        coverImageUrl = detail.coverImageUrl,
                                        wordCount = wordCount,
                                        isAuthorLoading = false,
                                        isWordCountLoading = false,
                                        detailError = null,
                                        evaluationExcerpt = excerpt
                                    )
                                }
                            }
                            maybeEvaluateVisibleArticles(source = source, sectionKey = _uiState.value.selectedSection)
                        } catch (e: Exception) {
                            Log.w("GuardianBrowseVM", "Detail load failed: ${item.url}", e)
                            _uiState.update { state ->
                                state.updateSourceArticle(item.url) { current ->
                                    current.copy(
                                        isAuthorLoading = false,
                                        isWordCountLoading = false,
                                        detailError = e.message
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun hydrateSuitability(items: List<GuardianBrowseItem>): List<GuardianBrowseItem> {
        val hydrated = mutableListOf<GuardianBrowseItem>()
        for (item in items) {
            val existing = articleRepository.getArticleBySourceUrl(item.url)
            if (existing == null) {
                hydrated.add(item)
            } else {
                hydrated.add(
                    item.copy(
                        author = item.author ?: existing.author.takeIf { it.isNotBlank() },
                        coverImageUrl = item.coverImageUrl ?: existing.coverImageUrl,
                        wordCount = item.wordCount ?: existing.wordCount.takeIf { it > 0 },
                        isAuthorLoading = item.isAuthorLoading && existing.author.isBlank(),
                        isWordCountLoading = item.isWordCountLoading && existing.wordCount <= 0,
                        suitabilityScore = existing.suitabilityScore,
                        suitabilityReason = existing.suitabilityReason.takeIf { it.isNotBlank() },
                        suitabilityEvaluatedAt = existing.suitabilityUpdatedAt
                    )
                )
            }
        }
        return hydrated
    }

    fun refresh() {
        loadSection(_uiState.value.selectedSection)
    }

    fun selectSource(source: OnlineReadingSource) {
        if (source == _uiState.value.selectedSource) return
        viewModelScope.launch { settingsDataStore.setOnlineReadingSource(source.key) }
        applySource(source, forceReload = true)
    }

    fun setLengthFilter(filter: ArticleLengthFilter) {
        _uiState.update { it.copy(lengthFilter = filter).applyPresentation() }
        maybeEvaluateVisibleArticles(
            source = _uiState.value.selectedSource,
            sectionKey = _uiState.value.selectedSection
        )
    }

    fun setScoreFilter(filter: ArticleScoreFilter) {
        _uiState.update { it.copy(scoreFilter = filter).applyPresentation() }
        maybeEvaluateVisibleArticles(
            source = _uiState.value.selectedSource,
            sectionKey = _uiState.value.selectedSection
        )
    }

    fun setSortOption(option: ArticleSortOption) {
        _uiState.update { it.copy(sortOption = option).applyPresentation() }
        maybeEvaluateVisibleArticles(
            source = _uiState.value.selectedSource,
            sectionKey = _uiState.value.selectedSection
        )
    }

    fun reEvaluate(url: String) {
        cancelAutoEvaluationJob(url)
        viewModelScope.launch {
            ensureDetailLoadedForEvaluation(url, _uiState.value.selectedSource)
            maybeEvaluateSuitability(url, _uiState.value.selectedSource, sectionKey = _uiState.value.selectedSection, force = true)
        }
    }

    fun openArticle(
        articleUrl: String,
        onNavigate: (Long) -> Unit
    ) {
        if (_uiState.value.isLoadingArticle) return
        _uiState.update { it.copy(isLoadingArticle = true, error = null) }
        val source = _uiState.value.selectedSource

        viewModelScope.launch {
            try {
                val articleId = when (source) {
                    OnlineReadingSource.GUARDIAN -> {
                        val detail = guardianRepository.getArticleDetail(articleUrl)
                        guardianRepository.createTemporaryArticle(detail)
                    }
                    OnlineReadingSource.CSMONITOR -> {
                        val detail = csMonitorRepository.getArticleDetail(articleUrl)
                        csMonitorRepository.createTemporaryArticle(detail)
                    }
                    OnlineReadingSource.ATLANTIC -> {
                        val detail = atlanticRepository.getArticleDetail(articleUrl)
                        atlanticRepository.createTemporaryArticle(detail)
                    }
                }
                _uiState.update { it.copy(isLoadingArticle = false) }
                onNavigate(articleId)
            } catch (e: Exception) {
                Log.e("GuardianBrowseVM", "Failed to open article: $articleUrl", e)
                _uiState.update { it.copy(isLoadingArticle = false, error = "文章加载失败：${e.message}") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun applySource(source: OnlineReadingSource, forceReload: Boolean) {
        val sections = when (source) {
            OnlineReadingSource.GUARDIAN -> guardianSections
            OnlineReadingSource.CSMONITOR -> csMonitorSections
            OnlineReadingSource.ATLANTIC -> atlanticSections
        }
        val preferred = lastSectionBySource[source]?.takeIf { key ->
            sections.any { it.key == key }
        } ?: sections.firstOrNull()?.key.orEmpty()

        if (!forceReload && _uiState.value.selectedSource == source) return

        cancelAutoEvaluationJobsExcept(emptySet())
        _uiState.update {
            it.copy(
                selectedSource = source,
                sections = sections,
                selectedSection = preferred,
                allArticles = emptyList(),
                articles = emptyList(),
                isLoading = true,
                error = null
            )
        }
        loadSection(preferred)
    }

    private suspend fun maybeEvaluateSuitability(
        url: String,
        source: OnlineReadingSource,
        sectionKey: String,
        force: Boolean = false
    ) {
        val item = _uiState.value.articles.firstOrNull { it.url == url } ?: return
        if (!force && !isCurrentlyVisible(url)) return
        if (!force && item.suitabilityScore != null) return
        if (item.isEvaluating) return

        val config = settingsDataStore.getFastAiConfig()
        if (config.apiKey.isBlank() || config.model.isBlank()) {
            if (force) {
                _uiState.update { it.copy(error = "快速模型未配置，无法评估") }
            }
            return
        }

        val excerpt = item.evaluationExcerpt?.takeIf { it.isNotBlank() }
        if (excerpt.isNullOrBlank()) {
            if (force) {
                _uiState.update { it.copy(error = "文章详情尚未加载完成，无法准确评估") }
            }
            return
        }

        updateItemEvaluating(url, true)
        evaluationSemaphore.withPermit {
            if (!force) {
                currentCoroutineContext().ensureActive()
                if (!isCurrentlyVisible(url)) return@withPermit
            }
            try {
                val result = articleAiRepository.evaluateArticleSuitability(
                    title = item.title,
                    excerpt = excerpt,
                    trailText = item.trailText,
                    source = source.label,
                    section = resolveSectionLabel(source, sectionKey),
                    wordCount = item.wordCount,
                    url = item.url,
                    apiKey = config.apiKey,
                    model = config.model,
                    baseUrl = config.baseUrl,
                    provider = config.provider
                )
                val now = System.currentTimeMillis()
                val modelKey = "${config.providerName}|${config.baseUrl.trimEnd('/')}|${config.model}"

                if (!force) {
                    currentCoroutineContext().ensureActive()
                    if (!isCurrentlyVisible(url)) return@withPermit
                }

                val updated = articleRepository.updateSuitabilityBySourceUrl(
                    sourceUrl = item.url,
                    score = result.score,
                    reason = result.reason,
                    evaluatedAt = now,
                    modelKey = modelKey
                )

                if (updated == 0) {
                    val article = com.xty.englishhelper.domain.model.Article(
                        title = item.title,
                        content = "",
                        articleUid = java.util.UUID.randomUUID().toString(),
                        sourceType = com.xty.englishhelper.domain.model.ArticleSourceType.MANUAL,
                        sourceTypeV2 = com.xty.englishhelper.domain.model.ArticleSourceTypeV2.ONLINE,
                        parseStatus = com.xty.englishhelper.domain.model.ArticleParseStatus.DONE,
                        summary = item.trailText.orEmpty(),
                        author = item.author.orEmpty(),
                        source = source.label,
                        coverImageUrl = item.coverImageUrl,
                        domain = item.url,
                        isSaved = false,
                        wordCount = item.wordCount ?: 0,
                        suitabilityScore = result.score,
                        suitabilityReason = result.reason,
                        suitabilityUpdatedAt = now,
                        suitabilityModel = modelKey
                    )
                    articleRepository.upsertArticle(article)
                }

                updateItemScore(url, result.score, result.reason, now)
            } catch (e: Exception) {
                Log.w("GuardianBrowseVM", "Suitability evaluation failed: $url", e)
                if (force) {
                    _uiState.update { it.copy(error = "评估失败：${e.message}") }
                }
            } finally {
                updateItemEvaluating(url, false)
            }
        }
    }

    private fun maybeEvaluateVisibleArticles(
        source: OnlineReadingSource,
        sectionKey: String
    ) {
        val visibleUrls = _uiState.value.articles.map { it.url }.toSet()
        cancelAutoEvaluationJobsExcept(visibleUrls)
        visibleUrls.forEach { url ->
            if (autoEvaluationJobs.containsKey(url)) return@forEach
            val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
                maybeEvaluateSuitability(
                    url = url,
                    source = source,
                    sectionKey = sectionKey
                )
            }
            val existing = autoEvaluationJobs.putIfAbsent(url, job)
            if (existing == null) {
                job.invokeOnCompletion { autoEvaluationJobs.remove(url, job) }
                job.start()
            } else {
                job.cancel()
            }
        }
    }

    private fun cancelAutoEvaluationJobsExcept(visibleUrls: Set<String>) {
        autoEvaluationJobs.entries.toList().forEach { (url, job) ->
            if (url !in visibleUrls && autoEvaluationJobs.remove(url, job)) {
                job.cancel()
            }
        }
    }

    private fun cancelAutoEvaluationJob(url: String) {
        autoEvaluationJobs.remove(url)?.cancel()
    }

    private fun isCurrentlyVisible(url: String): Boolean {
        return _uiState.value.articles.any { it.url == url }
    }

    private suspend fun ensureDetailLoadedForEvaluation(url: String, source: OnlineReadingSource) {
        val item = _uiState.value.articles.firstOrNull { it.url == url } ?: return
        if (!item.evaluationExcerpt.isNullOrBlank()) return

        val detail = fetchArticleDetail(source, url)
        val wordCount = detail.paragraphs
            .joinToString(" ") { it.text }
            .split(Regex("\\s+"))
            .count { it.isNotBlank() }
        val excerpt = buildExcerpt(detail.paragraphs)
        updateItemDetail(
            url = url,
            author = detail.author.takeIf { it.isNotBlank() },
            coverImageUrl = detail.coverImageUrl,
            wordCount = wordCount,
            evaluationExcerpt = excerpt,
            detailError = null,
            isAuthorLoading = false,
            isWordCountLoading = false
        )
    }

    private fun updateItemDetail(
        url: String,
        author: String?,
        coverImageUrl: String?,
        wordCount: Int?,
        evaluationExcerpt: String?,
        detailError: String?,
        isAuthorLoading: Boolean,
        isWordCountLoading: Boolean
    ) {
        _uiState.update { state ->
            state.updateSourceArticle(url) { current ->
                current.copy(
                    author = author,
                    coverImageUrl = coverImageUrl,
                    wordCount = wordCount,
                    evaluationExcerpt = evaluationExcerpt,
                    detailError = detailError,
                    isAuthorLoading = isAuthorLoading,
                    isWordCountLoading = isWordCountLoading
                )
            }
        }
    }

    private fun updateItemEvaluating(url: String, evaluating: Boolean) {
        _uiState.update { state ->
            val updatedState = state.updateSourceArticle(url) { current ->
                current.copy(isEvaluating = evaluating)
            }
            val newCount = updatedState.allArticles.count { it.isEvaluating }
            updatedState.copy(
                evaluatingCount = newCount,
                isEvaluating = newCount > 0
            )
        }
    }

    private fun updateItemScore(url: String, score: Int, reason: String, evaluatedAt: Long) {
        _uiState.update { state ->
            state.updateSourceArticle(url) { current ->
                current.copy(
                    suitabilityScore = score,
                    suitabilityReason = reason,
                    suitabilityEvaluatedAt = evaluatedAt
                )
            }
        }
    }

    private fun GuardianBrowseUiState.updateSourceArticle(
        url: String,
        transform: (GuardianBrowseItem) -> GuardianBrowseItem
    ): GuardianBrowseUiState {
        val idx = allArticles.indexOfFirst { it.url == url }
        if (idx < 0) return this
        val updated = allArticles.toMutableList()
        updated[idx] = transform(updated[idx])
        return copy(allArticles = updated).applyPresentation()
    }

    private fun GuardianBrowseUiState.withPresentedArticles(items: List<GuardianBrowseItem>): GuardianBrowseUiState {
        return copy(allArticles = items).applyPresentation()
    }

    private fun GuardianBrowseUiState.applyPresentation(): GuardianBrowseUiState {
        val presented = applyArticlePresentation(
            items = allArticles,
            lengthFilter = lengthFilter,
            scoreFilter = scoreFilter,
            sortOption = sortOption,
            wordCountOf = { it.wordCount },
            scoreOf = { it.suitabilityScore },
            titleOf = { it.title }
        )
        return copy(articles = presented)
    }

    private fun buildExcerpt(paragraphs: List<ArticleParagraph>): String {
        if (paragraphs.isEmpty()) return ""
        val selected = if (paragraphs.size <= 3) paragraphs else paragraphs.take(3)
        val joined = selected.joinToString("\n\n") { it.text.trim() }.trim()
        return if (joined.length > 2200) joined.take(2200) else joined
    }

    private fun resolveSectionLabel(source: OnlineReadingSource, sectionKey: String): String? {
        val sections = when (source) {
            OnlineReadingSource.GUARDIAN -> guardianSections
            OnlineReadingSource.CSMONITOR -> csMonitorSections
            OnlineReadingSource.ATLANTIC -> atlanticSections
        }
        return sections.firstOrNull { it.key == sectionKey }?.label
    }

    private data class OnlineDetail(
        val author: String,
        val coverImageUrl: String?,
        val paragraphs: List<ArticleParagraph>
    )

    private suspend fun fetchArticleDetail(source: OnlineReadingSource, url: String): OnlineDetail {
        return when (source) {
            OnlineReadingSource.GUARDIAN -> {
                val detail = guardianRepository.getArticleDetail(url)
                OnlineDetail(detail.author, detail.coverImageUrl, detail.paragraphs)
            }
            OnlineReadingSource.CSMONITOR -> {
                val detail = csMonitorRepository.getArticleDetail(url)
                OnlineDetail(detail.author, detail.coverImageUrl, detail.paragraphs)
            }
            OnlineReadingSource.ATLANTIC -> {
                val detail = atlanticRepository.getArticleDetail(url)
                OnlineDetail(detail.author, detail.coverImageUrl, detail.paragraphs)
            }
        }
    }
}
