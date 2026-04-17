# Article Parser 实现状态与修复指引

**生成时间**: 2025-02-22
**项目**: EnglishHelper
**功能模块**: Article Parser（文章阅读与词汇匹配）

---

## 📊 实现进度总结

### 整体状态

| 阶段 | 状态 | 完成度 |
|------|------|--------|
| 阶段 0: 词形扩展 | ✅ 完成 | 100% |
| 阶段 1: 数据层 | ✅ 完成 | 100% |
| 阶段 2: Domain层 | ✅ 完成 | 100% |
| 阶段 3: 文本处理 | ✅ 完成 | 100% |
| 阶段 4+8: AI+DI | ✅ 完成 | 100% |
| 阶段 5: UI 屏幕 | ⚠️ 部分完成 | 60% |
| 阶段 6+7: 导航集成 | ⚠️ 部分完成 | 70% |
| **总体** | **⚠️ 进行中** | **~75%** |

---

## ✅ 已完成修复清单 (10项)

### 性能与稳定性缺陷

| # | 缺陷 | 修复内容 | 文件 | 状态 |
|---|------|---------|------|------|
| 1 | 词令化长度限制 | 删除 `length >= 2` 限制，保留所有长度词（a, I） | ArticleTokenizer.kt | ✅ |
| 2 | JSON 转义 bug | 修复 `replace("\n", "\n")` → `replace("\n", "\\n")` | ArticleAiRepositoryImpl.kt | ✅ |
| 3 | O(n²) 嵌套查询 | 用 `Map<sentenceIndex, sentenceId>` 替代 `find()` | ArticleUseCases.kt:160 | ✅ |
| 4 | word_examples 重复 | 新增 `deleteExamplesByArticle()` 解析前清理 | ArticleUseCases.kt:171 | ✅ |
| 5 | word_stats 残留 | 解析前清理旧词频数据 | ArticleUseCases.kt:131 | ✅ |
| 6 | 例句标签格式 | 改为 `"{文章名} 例句"` 格式 | ArticleUseCases.kt:168 | ✅ |
| 7 | OCR 难度丢失 | 添加 `difficulty` 字段到 UiState | ArticleEditorViewModel.kt:28 | ✅ |
| 8 | 难度落库 | 修改 CreateArticleUseCase 接收 `difficultyAi` 参数 | ArticleUseCases.kt:25 | ✅ |
| 9 | Repository 缺口 | 添加 `deleteExamplesByArticle()` 接口与实现 | ArticleRepository.kt + ArticleRepositoryImpl.kt | ✅ |
| 10 | DAO 缺口 | 添加 `deleteExamplesByArticle()` 查询 | ArticleDao.kt | ✅ |

**编译状态**: ✅ `BUILD SUCCESSFUL in 7s`

---

## 🟡 剩余关键缺陷 (6项 - 需优先处理)

### 关键功能缺陷

#### 1. 🔴 ArticleReaderScreen 未使用数据库数据
**位置**: `app/src/main/java/com/xty/englishhelper/ui/screen/article/ArticleReaderScreen.kt:120-160`

**问题描述**:
- ❌ 当前仍使用 `article.content.split()` 简单切分
- ❌ 没有加载 `ArticleSentence` 数据库记录
- ❌ 没有加载 `ArticleWordLink` 实现词高亮
- ❌ `onWordClick` 回调未使用

**影响范围**:
- 无法渲染高亮词汇
- 点击词汇无法跳转词详情
- 例句回跳定位不准确（见缺陷 #3）

**修复方案**:
```kotlin
// 应该改为：
// 1. 从 uiState 读取 sentences: List<ArticleSentence>
// 2. 构建 wordLinkMap: Map<sentenceId, List<ArticleWordLink>>
// 3. 渲染时：
//    - 用 buildAnnotatedString 高亮匹配词
//    - 点击词汇调用 onWordClick(wordId, dictionaryId)
//    - 点击句子调用 onAnalyzeSentence(sentenceId, text)
```

**优先级**: 🔴 **CRITICAL** - 核心功能

---

#### 2. 🔴 SentenceAnalysisSheet 未接入
**位置**: `app/src/main/java/com/xty/englishhelper/ui/screen/article/SentenceAnalysisSheet.kt` (已写但未用)

**问题描述**:
- ❌ 组件已完整实现
- ❌ ArticleReaderScreen 中未使用
- ❌ 点击句子无分析反应

**影响范围**:
- 无法查看句子语法分析
- 无法查看中文翻译

**修复方案**:
```kotlin
// ArticleReaderScreen 中应该：
var showSentenceAnalysis by remember { mutableStateOf(false) }
var selectedSentenceId by remember { mutableStateOf(0L) }
var selectedSentenceText by remember { mutableStateOf("") }

// 在 Scaffold 后添加：
if (showSentenceAnalysis) {
    SentenceAnalysisSheet(
        sentenceText = selectedSentenceText,
        analysis = uiState.sentenceAnalysis[selectedSentenceId],
        isLoading = uiState.isAnalyzing == selectedSentenceId,
        onDismiss = { showSentenceAnalysis = false }
    )
}

// 点击句子时：
onAnalyzeSentence = { sentenceId, text ->
    selectedSentenceId = sentenceId
    selectedSentenceText = text
    showSentenceAnalysis = true
    viewModel.analyzeSentence(sentenceId, text)
}
```

**优先级**: 🔴 **CRITICAL** - 核心交互

---

#### 3. 🔴 句子 ID 使用错误
**位置**: `app/src/main/java/com/xty/englishhelper/ui/screen/article/ArticleReaderScreen.kt:155`

**问题描述**:
- ❌ 当前使用 `index.toLong()` 作为句子 ID
- ✅ 应该使用数据库 `sentence.id`

**影响范围**:
- 例句来源标签点击回跳时，无法准确定位到原句子
- 句子分析缓存 key 错误

**修复方案**:
```kotlin
// 错误方式（当前）:
val sentences = article.content.split(Regex("[.!?]+"))
    .mapIndexed { index, text -> text.trim() to index.toLong() }

// 正确方式:
val sentences: List<ArticleSentence> = uiState.sentences ?: emptyList()
// 直接使用 sentence.id 而非 index
```

**优先级**: 🔴 **CRITICAL** - 数据一致性

---

#### 4. 🟠 词详情页无文章例句展示
**位置**: `app/src/main/java/com/xty/englishhelper/ui/screen/word/WordDetailScreen.kt` 和 `WordDetailViewModel.kt`

**问题描述**:
- ❌ WordDetailViewModel 未加载文章例句
- ❌ WordDetailScreen 未渲染例句区块
- ❌ 无回跳入口（点击例句回到文章）

**影响范围**:
- 看不到词汇在文章中的使用上下文
- 无法快速返回原文章阅读

**修复方案**:
```kotlin
// WordDetailViewModel 中添加：
private val _examples = MutableStateFlow<List<WordExample>>(emptyList())
val examples = _examples.asStateFlow()

init {
    loadExamples()
}

private fun loadExamples() {
    viewModelScope.launch {
        _examples.value = getWordExamples(wordId)
    }
}

// WordDetailScreen 中添加（在相关词之后）：
if (examples.isNotEmpty()) {
    item {
        WordDetailSection(title = "文章例句") {
            LazyColumn {
                items(examples) { example ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // 回跳到文章
                                onArticleClick(example.sourceArticleId ?: 0L, example.sourceSentenceId ?: 0L)
                            }
                            .padding(8.dp)
                    ) {
                        Column {
                            Text(example.sourceLabel ?: "", style = Typography.labelSmall)
                            Text(example.sentence, style = Typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
```

**优先级**: 🟠 **HIGH** - 学习体验

---

#### 5. 🟠 轮询协程重复启动（协程泄漏）
**位置**: `app/src/main/java/com/xty/englishhelper/ui/screen/article/ArticleReaderViewModel.kt:87`

**问题描述**:
- ❌ 每次 `loadArticle()` 都会在 `init` 中被调用
- ❌ `getArticleFlow().collect()` 每次都启动新协程
- ❌ 轮询 Job 会累积

**影响范围**:
- 内存泄漏
- CPU 占用增加
- 协程数量持续增长

**修复方案**:
```kotlin
// 添加防重复标志:
private val pollStarted = mutableStateOf(false)

private fun startParseStatusPolling() {
    if (pollStarted.value) return  // 防止重复启动
    pollStarted.value = true

    viewModelScope.launch {
        while (true) {
            val article = _uiState.value.article
            if (article?.parseStatus == ArticleParseStatus.DONE ||
                article?.parseStatus == ArticleParseStatus.FAILED) {
                break
            }
            delay(2000)
            loadArticle()
        }
    }
}
```

**优先级**: 🟠 **HIGH** - 稳定性

---

#### 6. 🟡 Room 数据库索引缺失
**位置**: `app/src/main/java/com/xty/englishhelper/data/local/entity/ArticleWordLinkEntity.kt:31`

**问题描述**:
- ⚠️ `sentence_id` 列是外键但没有单列索引
- ⚠️ Room 编译时会警告

**影响范围**:
- 按 sentence_id 查询性能差
- 完整表扫描

**修复方案**:
```kotlin
// ArticleWordLinkEntity 中修改 indices:
indices = [
    Index("article_id"),
    Index("word_id"),
    Index("dictionary_id"),
    Index("sentence_id"),  // ← 新增
    Index(value = ["article_id", "sentence_id", "word_id"], unique = true)
]
```

**优先级**: 🟡 **MEDIUM** - 性能优化

---

## 📋 修复优先级排序

### 第一阶段（今天）- 🔴 CRITICAL
1. ✅ 所有稳定性修复 → **已完成**
2. ⏳ **ArticleReaderScreen 改写**（使用数据库数据）
3. ⏳ **接入 SentenceAnalysisSheet**
4. ⏳ **修复句子 ID**

### 第二阶段（明天）- 🟠 HIGH
5. ⏳ **词详情页集成文章例句**
6. ⏳ **防止轮询协程泄漏**

### 第三阶段（可选）- 🟡 MEDIUM
7. ⏳ **添加 Room 索引**

---

## 🔨 修复步骤详细指南

### 步骤 1: 修改 ArticleReaderViewModel

**文件**: `app/src/main/java/com/xty/englishhelper/ui/screen/article/ArticleReaderViewModel.kt`

```kotlin
// 添加到 ArticleReaderUiState:
data class ArticleReaderUiState(
    val article: Article? = null,
    val statistics: ArticleStatistics? = null,
    val sentences: List<ArticleSentence>? = null,  // ← 新增
    val wordLinks: List<ArticleWordLink>? = null,  // ← 新增
    val sentenceAnalysis: Map<Long, SentenceAnalysisResult> = emptyMap(),
    val isAnalyzing: Long = 0L,
    val analyzeError: String? = null
)

// 在 loadArticle() 中也加载句子和词链接:
private fun loadArticle() {
    viewModelScope.launch {
        getArticleDetail(articleId).collect { article ->
            _uiState.update { it.copy(article = article) }
            // 加载句子和词链接
            if (article != null) {
                val sentences = repository.getSentences(articleId)
                val wordLinks = repository.getWordLinks(articleId)
                _uiState.update {
                    it.copy(sentences = sentences, wordLinks = wordLinks)
                }
            }
        }
    }
}
```

需要注入 `ArticleRepository`:
```kotlin
@HiltViewModel
class ArticleReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getArticleDetail: GetArticleDetailUseCase,
    private val getStatistics: GetArticleStatisticsUseCase,
    private val analyzeSentence: AnalyzeSentenceUseCase,
    private val settingsDataStore: SettingsDataStore,
    private val repository: ArticleRepository,  // ← 新增
    // ...
) : ViewModel()
```

---

### 步骤 2: 改写 ArticleReaderScreen

**文件**: `app/src/main/java/com/xty/englishhelper/ui/screen/article/ArticleReaderScreen.kt`

核心改动（见文件末尾的完整示例实现）:

```kotlin
// 在 Scaffold 后添加 SentenceAnalysisSheet:
if (showSentenceAnalysis) {
    SentenceAnalysisSheet(
        sentenceText = selectedSentenceText,
        analysis = uiState.sentenceAnalysis[selectedSentenceId],
        isLoading = uiState.isAnalyzing == selectedSentenceId,
        error = uiState.analyzeError,
        sheetState = analysisSheetState,
        onDismiss = { showSentenceAnalysis = false }
    )
}

// 在 ArticleReaderContent 中渲染数据库句子:
items(uiState.sentences ?: emptyList()) { sentence ->
    SentenceRow(
        sentenceId = sentence.id,  // ← 使用数据库 ID
        sentenceText = sentence.text,
        wordLinks = uiState.wordLinks?.filter { it.sentenceId == sentence.id }
            ?.map { it.wordId to it.dictionaryId } ?: emptyList(),
        isAnalyzing = uiState.isAnalyzing == sentence.id,
        analysis = uiState.sentenceAnalysis[sentence.id],
        onAnalyze = {
            selectedSentenceId = sentence.id
            selectedSentenceText = sentence.text
            showSentenceAnalysis = true
            viewModel.analyzeSentence(sentence.id, sentence.text)
        },
        onWordClick = onWordClick
    )
}
```

---

### 步骤 3: 修改 WordDetailViewModel 加载例句

**文件**: `app/src/main/java/com/xty/englishhelper/ui/screen/word/WordDetailViewModel.kt`

```kotlin
// 添加到 ViewModel init 或状态中:
private val getWordExamples: GetWordExamplesUseCase,  // 注入

// 加载例句:
private fun loadExamples(wordId: Long) {
    viewModelScope.launch {
        try {
            val examples = getWordExamples(wordId)
            _uiState.update { it.copy(examples = examples) }
        } catch (e: Exception) {
            // 例句加载失败不影响主流程
        }
    }
}
```

---

### 步骤 4: 修改 WordDetailScreen 显示例句

**文件**: `app/src/main/java/com/xty/englishhelper/ui/screen/word/WordDetailScreen.kt`

在 WordDetailContent 中（通常在相关词之后）添加:

```kotlin
// 显示文章例句区块
if (state.examples.isNotEmpty()) {
    item {
        WordDetailSection(title = "文章例句") {
            LazyColumn {
                items(state.examples) { example ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // 点击回跳到文章
                                onArticleClick(
                                    example.sourceArticleId ?: 0L,
                                    example.sourceSentenceId ?: 0L
                                )
                            }
                            .padding(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                example.sourceLabel ?: "例句",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                example.sentence,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}
```

---

### 步骤 5: 添加 Room 索引

**文件**: `app/src/main/java/com/xty/englishhelper/data/local/entity/ArticleWordLinkEntity.kt`

修改 `indices` 参数:

```kotlin
indices = [
    Index("article_id"),
    Index("word_id"),
    Index("dictionary_id"),
    Index("sentence_id"),  // ← 新增单列索引
    Index(value = ["article_id", "sentence_id", "word_id"], unique = true)
]
```

**注意**: 修改后需要创建新的数据库迁移（版本 6→7）

---

## 📱 完整测试流程

完成上述修复后，按以下流程验证：

### 1. 文章创建与解析
```
[ ] 创建新文章 → 选择图片 → 点击"AI 识别并填充"
[ ] 验证：标题/内容/领域/难度都填充成功
[ ] 验证：解析状态从 PENDING → PROCESSING → DONE
```

### 2. 文章阅读（核心功能）
```
[ ] 进入 ArticleReader
[ ] 验证：句子逐句显示（来自数据库 ArticleSentence）
[ ] 验证：匹配的词被高亮（来自 ArticleWordLink）
[ ] 验证：点击高亮词 → 跳转到词详情页面
[ ] 验证：点击句子 → SentenceAnalysisSheet 弹出
[ ] 验证：BottomSheet 显示中文翻译、语法要点、重点词汇
[ ] 验证：二次点击同一句子 → 缓存命中（≤200ms）
```

### 3. 词详情集成
```
[ ] 打开词详情页
[ ] 验证：底部显示"文章例句"区块
[ ] 验证：例句标签格式为 "{文章名} 例句"
[ ] 验证：点击例句 → 回跳到原文章并定位到该句子
[ ] 验证：ArticleReaderScreen 收到 scrollToSentenceId 参数
```

### 4. 性能与稳定性
```
[ ] 打开 ArticleReader → 后退 → 重新打开（防重复）
[ ] 观察：没有多个轮询任务同时运行（logcat 观察）
[ ] 创建多篇文章，验证：例句不重复、不残留
```

---

## 🏗️ 已修改文件列表

| 文件路径 | 修改内容 | 状态 |
|---------|---------|------|
| `ArticleTokenizer.kt` | 删除词长度限制 | ✅ |
| `ArticleAiRepositoryImpl.kt` | 修复 JSON 转义 | ✅ |
| `ArticleUseCases.kt` | 清理、标签、优化 | ✅ |
| `ArticleRepository.kt` | 新增接口 | ✅ |
| `ArticleRepositoryImpl.kt` | 实现新方法 | ✅ |
| `ArticleDao.kt` | 新增 DAO 查询 | ✅ |
| `ArticleEditorViewModel.kt` | 难度字段 | ✅ |
| `CreateArticleUseCase` | difficultyAi 参数 | ✅ |
| `ArticleReaderScreen.kt` | ⏳ 待改写（重点） | 🟡 |
| `ArticleReaderViewModel.kt` | ⏳ 待扩展 | 🟡 |
| `WordDetailScreen.kt` | ⏳ 待集成例句 | 🟡 |
| `WordDetailViewModel.kt` | ⏳ 待加载例句 | 🟡 |
| `ArticleWordLinkEntity.kt` | ⏳ 待添加索引 | 🟡 |

---

## 📝 提交建议

完成各阶段修复后，建议分阶段提交：

```bash
# 第一次：稳定性修复（已完成）
git commit -m "fix: Article Parser 稳定性与性能改进

- 删除词令化长度限制，支持单字符词
- 修复多模态 JSON 转义 bug
- 优化 O(n²) 句子匹配查询
- 添加重解析前清理 word_examples 和 word_stats
- 修复例句标签格式为 '{文章名} 例句'
- OCR 识别的难度值落库


# 第二次：UI 功能集成（待完成）
git commit -m "feat: 完整集成 Article Reader UI 数据库渲染

- ArticleReaderScreen 使用数据库句子和词链接渲染
- 接入 SentenceAnalysisSheet 语法分析面板
- 修复句子 ID 为数据库 ID
- 词高亮与点击跳转完整实现


# 第三次：词详情集成（待完成）
git commit -m "feat: 词详情页集成文章例句展示

- WordDetailScreen 显示相关文章例句
- 支持例句来源点击回跳文章
- 防止轮询协程重复启动
- 添加数据库索引优化查询性能

```

---

## 🚀 快速启动指南

### 对于开发者

1. **拉取最新代码**
   ```bash
   git pull
   ```

2. **编译验证**（已通过）
   ```bash
   ./gradlew compileDebugKotlin  # ✅ BUILD SUCCESSFUL
   ```

3. **按优先级修复**
   - 第一阶段：ArticleReaderScreen（最关键）
   - 第二阶段：WordDetail 集成
   - 第三阶段：索引优化

4. **分阶段测试**
   ```bash
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

5. **验证**
   按"完整测试流程"部分逐项检查

---

## ⚠️ 已知限制与注意事项

1. **后台任务管理**
   - 当前解析任务在 ViewModelScope 中，页面返回时会被取消
   - **建议**: 考虑迁移到 WorkManager 或使用 GlobalScope（需权衡）

2. **句子分析缓存**
   - 缓存 key 为 `(articleId, sentenceId, sentenceHash)`
   - 如果同一句子在多篇文章中出现，会分别缓存（符合设计）

3. **词链接唯一性**
   - 同一句子中重复的词只链接一次（设计决策）
   - 可在 UI 层按需展示多次

4. **Database Migration**
   - 添加索引不需要新版本，可直接修改 entity
   - 如需重大改动，遵循 Room 迁移规范

---

## 📞 需要帮助？

如果在修复过程中遇到问题，请检查：

1. **编译错误**
   ```bash
   ./gradlew clean compileDebugKotlin
   ```

2. **运行时错误**
   - 检查 logcat 中的异常堆栈
   - 验证数据库迁移是否成功

3. **UI 不显示**
   - 验证 ViewModel 中数据是否加载
   - 检查 composable 是否正确接收状态

4. **性能问题**
   - 使用 Android Profiler 检查内存和 CPU
   - 观察协程数量和 Job 状态

---

## 📊 功能完成度预期

修复完成后的预期功能覆盖：

| 功能 | 当前 | 修复后 |
|------|------|--------|
| 文章创建 | ✅ | ✅ |
| OCR 识别 | ✅ | ✅ |
| 自动解析 | ✅ | ✅ |
| 句子渲染 | ⚠️ 简单切分 | ✅ 数据库 |
| 词汇高亮 | ❌ | ✅ |
| 词汇点击 | ❌ | ✅ |
| 句子分析 | ❌ | ✅ |
| 词详情例句 | ❌ | ✅ |
| 例句回跳 | ❌ | ✅ |
| **整体功能** | **~60%** | **~95%** |

---

**最后更新**: 2025-02-22
**下次更新**: 修复完成后
