# EnglishHelper 全面修复与优化指南（v2）

> 本文档基于代码库实际探索结果更新。P0、P1-1 已由第二轮修复完成，本版本重点精化 P1-2 至 P4 的可执行方案。

---

## 1. 目标与范围

系统性修复当前项目在**可维护性、代码质量、性能、稳定性**四个方面的问题，覆盖：

- 数据层与迁移（Room schema / DAO / Migration / 事务）
- AI 调用链路（句子分析缓存、Prompt 管理）
- 文章解析与回填性能（Regex 预编译、I/O 分发、嵌套循环）
- Reader 渲染性能（AnnotatedString 缓存、LazyColumn key）
- 代码质量（大文件拆分、错误处理标准化、常量枚举化）
- 测试与 CI（迁移测试、UseCase 测试、自动化门禁）

---

## 2. 总体原则

1. **先稳定后优化**：先消除数据错误，再做性能和体验优化。
2. **单向依赖**：UI → UseCase → Repository → DAO，禁止反向耦合。
3. **事务原子性**：所有跨表写操作必须可回滚。
4. **增量优化**：每个阶段可独立上线，避免大爆炸式重构。
5. **可回归验证**：每项修复必须配套自动化测试与验收标准。

---

## 3. 完成状态总览

| Phase | 内容 | 状态 |
|-------|------|------|
| P0-1 | Room 迁移链完整（MIGRATION_6_7 注册） | ✅ 已完成 |
| P0-2 | 顶层导航修复（popUpTo 类型安全） | ✅ 已完成 |
| P0-3 | 文章删除原子事务（@Transaction 下沉 DAO） | ✅ 已完成 |
| P1-1 | 回填链路完善（sourceLabel、先删后建） | ✅ 已完成 |
| P1-2 | 句子分析缓存结构修复 | 🔴 待实现 |
| P2-1 | 文章解析与回填性能 | 🔴 待实现 |
| P2-2 | Reader 渲染优化 | 🔴 待实现 |
| P3-1 | 大文件拆分 | 🟡 可选 |
| P3-2 | 错误处理标准化 | 🔴 待实现 |
| P3-3 | 业务常量与本地化 | 🟡 可选 |
| P4-1 | 测试矩阵补全 | 🔴 待实现 |
| P4-2 | CI 门禁 | 🔴 待实现 |

---

## Phase 1（P1）：数据一致性修复

### ✅ P1-1 回填链路完善 — 已完成

（由第二轮修复实现：sourceLabel 填充、先删后建策略）

---

### 🔴 P1-2 句子分析缓存结构修复

#### 问题定位

**问题 A：`sentenceHash` 使用 `hashCode()`，碰撞风险高且跨版本不稳定**

`app/src/main/java/com/xty/englishhelper/domain/usecase/article/ArticleUseCases.kt`（第 198 行）：

```kotlin
val hash = sentenceText.hashCode().toString()  // ← 32-bit，存在碰撞风险
```

**问题 B：`grammarJson`/`keywordsJson` 用自定义分隔符，字段名具误导性**

`ArticleUseCases.kt`（第 230-251 行）：

```kotlin
// 序列化：拼接 "|||" 和 "::" 而非标准 JSON
return points.joinToString(separator = "|||") { "${it.title}::${it.explanation}" }
// 反序列化：若 title/explanation 含 "::" 或 "|||" 则静默解析错误
return json.split("|||").mapNotNull { entry ->
    val parts = entry.split("::", limit = 2)
    ...
}
```

**问题 C：Cache key 不含 model/promptVersion，换模型后旧缓存污染新结果**

`app/src/main/java/com/xty/englishhelper/data/local/ArticleDao.kt`（第 77 行）cache 查询仅依赖 `(articleId, sentenceId, sentenceHash)` 三元组，
未包含模型名或 Prompt 版本。更换模型后命中旧缓存，返回错误模型生成的内容。

#### 修复方案

**步骤 1：修改 Entity，增加 `model_key` 列，升级 DB 至 v8**

```kotlin
// app/src/main/java/com/xty/englishhelper/data/local/entity/SentenceAnalysisCacheEntity.kt
@Entity(
    tableName = "sentence_analysis_cache",
    indices = [Index(value = ["article_id", "sentence_id", "sentence_hash", "model_key"], unique = true)]
)
data class SentenceAnalysisCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "article_id") val articleId: Long,
    @ColumnInfo(name = "sentence_id") val sentenceId: Long,
    @ColumnInfo(name = "sentence_hash") val sentenceHash: String,
    @ColumnInfo(name = "model_key") val modelKey: String = "",  // ← 新增
    @ColumnInfo(name = "meaning_zh") val meaningZh: String,
    @ColumnInfo(name = "grammar_json") val grammarJson: String,
    @ColumnInfo(name = "keywords_json") val keywordsJson: String
)
```

**步骤 2：AppDatabase.kt 增加 MIGRATION_7_8**

```kotlin
// app/src/main/java/com/xty/englishhelper/data/local/AppDatabase.kt
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE sentence_analysis_cache ADD COLUMN model_key TEXT NOT NULL DEFAULT ''")
        db.execSQL("DROP INDEX IF EXISTS index_sentence_analysis_cache_article_id_sentence_id_sentence_hash")
        db.execSQL("CREATE UNIQUE INDEX index_cache_full ON sentence_analysis_cache(article_id, sentence_id, sentence_hash, model_key)")
    }
}
```

**步骤 3：改 hash 算法，改序列化格式**

```kotlin
// app/src/main/java/com/xty/englishhelper/domain/usecase/article/ArticleUseCases.kt
import java.security.MessageDigest

// Hash：SHA-256(sentenceText + model + PROMPT_VERSION)
private fun computeHash(sentenceText: String, model: String): String {
    val input = "$sentenceText|$model|${PROMPT_VERSION}"
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
}

const val PROMPT_VERSION = "v1"  // Prompt 变更时递增

// 序列化：改用 Moshi JSON
private fun grammarPointsToJson(points: List<GrammarPoint>): String =
    moshi.adapter(listOf<GrammarPoint>()::class.java).toJson(points)  // 标准 JSON

private fun parseGrammarPoints(json: String): List<GrammarPoint> =
    runCatching { moshi.adapter(listOf<GrammarPoint>()::class.java).fromJson(json) ?: emptyList() }
        .getOrDefault(emptyList())
```

**步骤 4：ArticleDao、ArticleRepository 接口、Impl 同步更新 cache 查询签名**（新增 `modelKey` 参数）

**步骤 5：AppModule.kt 注册 MIGRATION_7_8**

#### 验收标准

- 含 `::` 或 `|||` 的句子能正确缓存和回放
- 切换模型后旧缓存不命中，重新生成结果
- `Migration7To8Test` 通过

---

## Phase 2（P2）：性能优化

### 🔴 P2-1 文章解析与回填性能

#### 问题定位

**问题 A：Regex 在三重嵌套最内层每次 new（严重）**

`app/src/main/java/com/xty/englishhelper/domain/usecase/article/LinkWordToArticlesUseCase.kt`（第 36-79 行）存在三层嵌套循环：

```kotlin
for (articleId in articleIds) {            // M 篇文章
    val sentences = repository.getSentences(articleId)
    for (sentence in sentences) {          // N 句
        for (token in normalizedTokens) {  // K 个词形
            // ← 每次都 new Regex，共 M*N*K 次
            val pattern = Regex("\\b${Regex.escape(token)}\\b", RegexOption.IGNORE_CASE)
        }
    }
}
```

最坏情形：50 篇文章 × 200 句 × 7 个词形 = 70,000 次 Regex 对象创建。

**问题 B：`getSentences()` 每篇单独查询，N 篇文章产生 N 次 Room 查询**

**问题 C：混合 I/O 操作使用 `Dispatchers.Default`**

```kotlin
withContext(Dispatchers.Default) {  // ← Default 不适合 I/O 密集型操作
    repository.getSentences(articleId)  // Room I/O
    ...
}
```

#### 修复方案

**步骤 1：在循环外预编译 Regex，改 Dispatcher**

```kotlin
// app/src/main/java/com/xty/englishhelper/domain/usecase/article/LinkWordToArticlesUseCase.kt
suspend operator fun invoke(...) {
    withContext(Dispatchers.IO) {  // ← 改为 IO
        try {
            repository.deleteWordLinksByWord(wordId)
            repository.deleteExamplesByWord(wordId)

            val normalizedTokens = buildSet {
                add(spelling.trim().lowercase())
                inflections.forEach { add(it.form.trim().lowercase()) }
            }

            // ← 在循环外预编译所有 token 的 Regex，仅执行 K 次
            val regexByToken = normalizedTokens.associateWith { token ->
                Regex("\\b${Regex.escape(token)}\\b", RegexOption.IGNORE_CASE)
            }

            val articleIds = repository.getArticleIdsByTokens(normalizedTokens.toList())
            if (articleIds.isEmpty()) return@withContext

            val wordLinks = mutableListOf<ArticleWordLink>()
            val examples = mutableListOf<WordExample>()

            for (articleId in articleIds) {
                val article = repository.getArticleByIdOnce(articleId) ?: continue
                val sourceLabel = "「${article.title}」"
                val sentences = repository.getSentences(articleId)

                for (sentence in sentences) {
                    for ((token, pattern) in regexByToken) {  // ← 复用预编译 Regex
                        val matchResult = pattern.find(sentence.text)
                        if (matchResult != null) {
                            wordLinks.add(ArticleWordLink(...))
                            examples.add(WordExample(..., sourceLabel = sourceLabel))
                            break
                        }
                    }
                }
            }

            if (wordLinks.isNotEmpty()) repository.upsertWordLinks(wordLinks)
            if (examples.isNotEmpty()) repository.insertExamples(examples)

        } catch (e: Exception) {
            // Linkage failure is non-critical
        }
    }
}
```

**步骤 2（可选进阶）：增加批量加载句子的 DAO 方法，减少 N 次查询到 1 次**

```kotlin
// app/src/main/java/com/xty/englishhelper/data/local/ArticleDao.kt
@Query("SELECT * FROM article_sentences WHERE article_id IN (:articleIds) ORDER BY article_id, sentence_index ASC")
suspend fun getSentencesByArticleIds(articleIds: List<Long>): List<ArticleSentenceEntity>
```

然后在 UseCase 中按 `articleId` groupBy 后统一使用，避免循环内单独查询。

#### 验收标准

- 大词典（>5k 词）下保存单词无明显卡顿
- Regex 对象创建次数从 M*N*K 降至 K
- 同等数据量下回填耗时下降 ≥ 30%

---

### 🔴 P2-2 Reader 渲染优化

#### 问题定位

**问题 A：`buildAnnotatedString` 无 `remember`，每次重组都重建**

`app/src/main/java/com/xty/englishhelper/ui/screen/article/ArticleReaderScreen.kt`（第 292-310 行）：`Text(buildAnnotatedString { ... })` 直接在
composable 函数体中调用，任何父级状态变化（如 `isAnalyzing`）都触发全列表重组并重建。

**问题 B：`onTap` 内完整重复构造 AnnotatedString（严重）**

`ArticleReaderScreen.kt`（第 316-333 行）：在手势回调里重建了与 `Text(...)` 完全相同的
AnnotatedString，仅为了调用 `getStringAnnotations`，存在完全冗余的对象创建。

**问题 C：`LazyColumn.items` 缺少 `key` 参数**

`ArticleReaderScreen.kt`（第 234 行）：无 `key` 参数，列表任何变化触发全量重组。

**问题 D：`groupBy` 每次重组产生新 List 引用，导致 `pointerInput(wordLinks)` 频繁重启**

`ArticleReaderContent`（第 219 行）：`val wordLinksBySentence = wordLinks.groupBy { it.sentenceId }`
无 `remember` 包裹，每次重组产生新 Map 对象。

#### 修复方案

```kotlin
// app/src/main/java/com/xty/englishhelper/ui/screen/article/ArticleReaderScreen.kt
@Composable
private fun ArticleReaderContent(
    ...
    wordLinks: List<ArticleWordLink>,
    ...
) {
    // ← 修复 D：remember 包裹 groupBy，避免每次重组重建 Map
    val wordLinksBySentence = remember(wordLinks) { wordLinks.groupBy { it.sentenceId } }

    LazyColumn(
        state = listState,
        ...
    ) {
        items(
            sentences,
            key = { it.id }   // ← 修复 C：指定稳定 key
        ) { sentence ->
            SentenceRow(...)
        }
    }
}

@Composable
private fun SentenceRow(
    sentenceId: Long,
    sentenceText: String,
    wordLinks: List<ArticleWordLink>,
    ...
) {
    // ← 修复 A：remember 缓存 parts 列表，避免每次重组重建
    val parts = remember(sentenceId, wordLinks) {
        buildParts(sentenceText, wordLinks)
    }

    // ← 修复 A + B：remember 缓存 AnnotatedString，同时预建 annotation offset map
    val (annotatedString, annotationMap) = remember(parts) {
        val sb = buildAnnotatedString {
            parts.forEach { (text, link) ->
                if (link != null) {
                    pushStringAnnotation("word", "${link.wordId}:${link.dictionaryId}")
                    withStyle(...) { append(text) }
                    pop()
                } else {
                    append(text)
                }
            }
        }
        // annotationMap 供 onTap 直接查找，无需重建 AnnotatedString
        sb to sb
    }

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        annotatedString,
        onTextLayout = { textLayoutResult = it },
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(wordLinks) {  // key 不变，仍用 wordLinks
                detectTapGestures(
                    onTap = { tapOffset ->
                        textLayoutResult?.let { layout ->
                            val charOffset = layout.getOffsetForPosition(tapOffset)
                            // ← 修复 B：直接在已缓存的 annotatedString 上查找，无需重建
                            annotatedString
                                .getStringAnnotations("word", charOffset, charOffset)
                                .firstOrNull()?.let { ann ->
                                    val (wId, dId) = ann.item.split(":")
                                    onWordClick(wId.toLong(), dId.toLong())
                                }
                        }
                    },
                    onLongPress = { onAnalyze() }
                )
            }
    )
}

// 抽取 buildParts 为纯函数（无 composable 依赖）
private fun buildParts(
    sentenceText: String,
    wordLinks: List<ArticleWordLink>
): List<Pair<String, ArticleWordLink?>> {
    val parts = mutableListOf<Pair<String, ArticleWordLink?>>()
    var lastEnd = 0
    wordLinks.forEach { link ->
        val lowerText = sentenceText.lowercase()
        val matchedToken = link.matchedToken.lowercase()
        val startPos = lowerText.indexOf(matchedToken, startIndex = lastEnd)
        if (startPos >= 0 && startPos < sentenceText.length) {
            val endPos = minOf(startPos + matchedToken.length, sentenceText.length)
            if (lastEnd < startPos) parts.add(sentenceText.substring(lastEnd, startPos) to null)
            parts.add(sentenceText.substring(startPos, endPos) to link)
            lastEnd = endPos
        }
    }
    if (lastEnd < sentenceText.length) parts.add(sentenceText.substring(lastEnd) to null)
    return parts
}
```

#### 验收标准

- 长文（200+ 句）滚动流畅，无明显抖动
- 点击高亮词响应无卡顿
- `isAnalyzing` 状态变化不触发所有 SentenceRow 的 AnnotatedString 重建

---

## Phase 3（P3）：可维护性与代码质量

### 🟡 P3-1 大文件拆分

当前超大文件：

| 文件 | 行数 | 主要 Composable 数 |
|------|------|--------------------|
| `ui/screen/addword/AddWordScreen.kt` | **776 行** | 8 个 Composable，宽屏/紧凑布局大量重复 |
| `ui/screen/study/StudyScreen.kt` | **586 行** | 3 个 Composable |

建议拆分结构（以 `addword` 为例）：

```
ui/screen/addword/
├── AddWordScreen.kt          ← 仅做路由与状态收集（<80 行）
├── AddWordContent.kt         ← 主表单布局（宽/窄通用）
└── components/
    ├── MeaningSection.kt     ← MeaningRow 等含义相关组件
    ├── InflectionSection.kt  ← InflectionRow 等词形变化组件
    └── SimilarWordSection.kt ← 近义词/同根词等组件
```

---

### 🔴 P3-2 错误处理标准化

#### 问题定位

当前状态：
- 无统一 `AppError`/`Result` 模型，各 ViewModel 自定义 `error: String?` 字段
- **6 处** `catch (_: Exception) { // non-critical }` 完全静默，包括：
  - `app/src/main/java/com/xty/englishhelper/ui/screen/article/ArticleReaderViewModel.kt`（第 90、108、118 行）
  - `app/src/main/java/com/xty/englishhelper/ui/screen/addword/AddWordViewModel.kt`（第 76 行）
  - `app/src/main/java/com/xty/englishhelper/ui/screen/article/ArticleEditorViewModel.kt`（第 172 行）
  - `app/src/main/java/com/xty/englishhelper/ui/screen/worddetail/WordDetailViewModel.kt`（第 76 行）
- JSON 解析失败被 `runCatching.getOrNull()` 静默降级，上层无感知
- `ArticleListViewModel.kt`（第 34 行）：注释称"Error handled by UI state"，但 UI state 实际未被更新

#### 修复方案

**步骤 1：建立 `AppError` 模型**

```kotlin
// app/src/main/java/com/xty/englishhelper/domain/model/AppError.kt
sealed class AppError(val message: String, val cause: Throwable? = null) {
    class DataLoad(message: String, cause: Throwable? = null) : AppError(message, cause)
    class Network(message: String, cause: Throwable? = null) : AppError(message, cause)
    class Parse(message: String, cause: Throwable? = null) : AppError(message, cause)
    class Database(message: String, cause: Throwable? = null) : AppError(message, cause)
}
```

**步骤 2：对"可忽略但需记录"的 catch 块增加日志**

```kotlin
// 改前（完全静默）
} catch (_: Exception) {
    // Data loading failure is non-critical
}

// 改后（日志可观测，不向用户暴露）
} catch (e: Exception) {
    Log.w("ArticleReaderVM", "Data loading failed for articleId=$articleId", e)
}
```

**步骤 3：修复 `ArticleListViewModel.kt` 实际未更新 UI state 的问题**

```kotlin
// app/src/main/java/com/xty/englishhelper/ui/screen/article/ArticleListViewModel.kt
fun deleteArticle(articleId: Long) {
    viewModelScope.launch {
        try {
            repository.deleteArticle(articleId)
        } catch (e: Exception) {
            Log.e("ArticleListVM", "Delete article failed: $articleId", e)
            _error.value = "删除失败：${e.message}"  // ← 补充实际的错误通知
        }
    }
}
```

#### 验收标准

- 无无日志的完全静默 catch 块
- `ArticleListViewModel` 删除失败时用户收到错误提示

---

### 🟡 P3-3 业务常量与本地化

#### 问题定位

**魔法数字**：`sourceType = 1`（Article 来源）出现在：
- `app/src/main/java/com/xty/englishhelper/domain/usecase/article/ArticleUseCases.kt`（第 169 行）
- `app/src/main/java/com/xty/englishhelper/domain/usecase/article/LinkWordToArticlesUseCase.kt`（第 69 行，有注释但仍为裸数字）

**字符串资源**：`strings.xml` 已有 40+ 条资源定义，但约 110 处 `Text("中文字符串")` 绕过资源直接硬编码。

#### 修复方案

**步骤 1：增加 `ExampleSourceType` 枚举**

```kotlin
// app/src/main/java/com/xty/englishhelper/domain/model/WordExampleSourceType.kt
enum class WordExampleSourceType(val value: Int) {
    MANUAL(0),
    ARTICLE(1);

    companion object {
        fun fromValue(value: Int) = entries.firstOrNull { it.value == value } ?: MANUAL
    }
}
```

**步骤 2（渐进式）：高频 UI 文案优先迁移至 `strings.xml`**

优先迁移错误消息类字符串（分散在 ViewModel 中，可通过 `stringResource` 在 Screen 传入）：

```kotlin
// 改前（ViewModel 直接拼接中文）
_uiState.update { it.copy(error = "AI 整理失败：${e.message}") }

// 改后（ViewModel 只暴露错误类型和 cause，Screen 负责本地化）
_uiState.update { it.copy(error = AppError.Network("AI 整理失败", e)) }
// Screen 中：stringResource(R.string.error_ai_failed, error.cause?.message ?: "")
```

---

## Phase 4（P4）：测试与质量门禁

### 🔴 P4-1 测试矩阵补全

当前状态：
- **单元测试**：仅 3 个文件（`JsonImportExporterTest`、`FsrsEngineTest`、`SaveWordUseCaseTest`）
- **集成测试**：仅 2 个文件（`MigrationTest`（v2→v3）、`StudyStateConstraintTest`）
- 核心业务 UseCase（LinkWordToArticles、DeleteArticle、AnalyzeSentence）均无测试覆盖
- **`Migration6To7Test` 缺失**（P0 修复已上线但迁移测试未补充）

**必须补充的测试：**

```kotlin
// app/src/androidTest/java/com/xty/englishhelper/data/local/Migration6To7Test.kt
class Migration6To7Test {
    @get:Rule val helper = MigrationTestHelper(...)

    @Test
    fun migrate_v6_to_v7_index_exists() {
        helper.createDatabase(TEST_DB, 6).apply { close() }
        val db = helper.runMigrationsAndValidate(TEST_DB, 7, true, AppDatabase.MIGRATION_6_7)
        // 验证 article_word_stats.normalized_token 索引存在且可命中
        val cursor = db.query("EXPLAIN QUERY PLAN SELECT * FROM article_word_stats WHERE normalized_token = 'test'")
        assertTrue(cursor.moveToFirst())
        val plan = cursor.getString(cursor.getColumnIndex("detail"))
        assertTrue("索引未命中", plan.contains("USING INDEX"))
    }
}

// app/src/test/java/com/xty/englishhelper/domain/usecase/article/LinkWordToArticlesUseCaseTest.kt
class LinkWordToArticlesUseCaseTest {
    private val repository = mockk<ArticleRepository>()
    private val useCase = LinkWordToArticlesUseCase(repository)

    @Test
    fun `invoke clears old links before inserting new ones`() = runTest {
        // Arrange
        coEvery { repository.deleteWordLinksByWord(any()) } just Runs
        coEvery { repository.deleteExamplesByWord(any()) } just Runs
        coEvery { repository.getArticleIdsByTokens(any()) } returns emptyList()
        // Act
        useCase(wordId = 1L, dictionaryId = 1L, spelling = "test", inflections = emptyList())
        // Assert
        coVerify { repository.deleteWordLinksByWord(1L) }
        coVerify { repository.deleteExamplesByWord(1L) }
    }

    @Test
    fun `sourceLabel contains article title`() = runTest {
        val article = Article(id = 1L, title = "TestTitle", ...)
        coEvery { repository.getArticleByIdOnce(1L) } returns article
        coEvery { repository.getSentences(1L) } returns listOf(
            ArticleSentence(id = 10L, articleId = 1L, text = "test sentence", ...)
        )
        coEvery { repository.getArticleIdsByTokens(any()) } returns listOf(1L)
        val examplesSlot = slot<List<WordExample>>()
        coEvery { repository.insertExamples(capture(examplesSlot)) } just Runs
        coEvery { repository.upsertWordLinks(any()) } just Runs

        useCase(wordId = 1L, dictionaryId = 1L, spelling = "test", inflections = emptyList())

        assertEquals("「TestTitle」", examplesSlot.captured.first().sourceLabel)
    }
}
```

---

### 🔴 P4-2 CI 门禁

#### 问题定位

`.github/workflows/android-release.yml` 仅手动触发，无 PR 自动测试步骤。

#### 修复方案

新增 `android-ci.yml`：

```yaml
# .github/workflows/android-ci.yml
name: CI

on:
  push:
    branches: [ master ]
  pull_request:
    branches: [ master ]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Run Unit Tests
        run: ./gradlew testDebugUnitTest
      - name: Upload Test Results
        if: failure()
        uses: actions/upload-artifact@v4
        with:
          name: test-results
          path: app/build/reports/tests/
```

---

## 4. 关键文件一览

| 文件 | 涉及 Phase |
|------|-----------|
| `data/local/entity/SentenceAnalysisCacheEntity.kt` | P1-2 |
| `data/local/AppDatabase.kt` | P1-2（MIGRATION_7_8） |
| `di/AppModule.kt` | P1-2 |
| `domain/usecase/article/ArticleUseCases.kt` | P1-2（hash、序列化） |
| `domain/usecase/article/LinkWordToArticlesUseCase.kt` | P2-1 |
| `ui/screen/article/ArticleReaderScreen.kt` | P2-2 |
| `ui/screen/addword/AddWordScreen.kt` | P3-1 |
| `ui/screen/study/StudyScreen.kt` | P3-1 |
| `ui/screen/article/ArticleListViewModel.kt` | P3-2（未更新 UI state） |
| `ui/screen/article/ArticleReaderViewModel.kt` | P3-2（静默 catch） |
| `domain/model/AppError.kt` | P3-2（新增） |
| `domain/model/WordExampleSourceType.kt` | P3-3（新增） |
| `app/src/androidTest/data/local/Migration6To7Test.kt` | P4-1（新增） |
| `app/src/test/.../LinkWordToArticlesUseCaseTest.kt` | P4-1（新增） |
| `.github/workflows/android-ci.yml` | P4-2（新增） |

---

## 5. 建议执行顺序

```
P1-2  → 缓存结构（需 DB 迁移，需先做）
P4-1  → Migration7To8Test + 已有 UseCase 测试补充
P2-1  → LinkWordToArticlesUseCase 性能优化
P2-2  → Reader 渲染优化
P3-2  → 错误处理标准化（补日志，补 UI state 更新）
P4-2  → CI 门禁
P3-1  → 大文件拆分（可选，工作量大）
P3-3  → 常量枚举化 + 字符串资源（可选，渐进推进）
```

---

## 6. 验收清单（DoD）

- [ ] 句子分析缓存 hash 改为 SHA-256，序列化改为标准 JSON
- [ ] 切换模型后旧缓存不命中
- [ ] `LinkWordToArticlesUseCase` 性能：同等数据量耗时下降 ≥ 30%
- [ ] Reader 长文滚动流畅，点击词响应无抖动
- [ ] 无完全静默的 catch 块（至少有 Log.w）
- [ ] `ArticleListViewModel` 删除失败时用户可见错误提示
- [ ] `Migration7To8Test` 通过
- [ ] `LinkWordToArticlesUseCaseTest` 通过（含 sourceLabel 断言）
- [ ] PR 自动触发单元测试，失败时阻断合并

---

## 7. 风险与回滚策略

- P1-2 涉及 DB schema 变更（v8），必须先灰度测试包验证迁移正确性后再上线
- P2-1 的 Dispatcher 从 Default 改 IO：如果大量 CPU 计算（Regex 匹配）使 IO 线程占满，可改回 Default + 在 IO 调用处单独 withContext(IO)
- P3-1 大文件拆分风险最高，建议单独分支 + 仅做结构性移动不改逻辑

---

## 8. 结语

经代码库深度探索，当前主要工程债集中在：
1. **AI 缓存健壮性**（hashCode 碰撞、自定义分隔符、无模型版本）
2. **Reader 渲染冗余**（AnnotatedString 每次重建、onTap 内重复构造）
3. **回填性能**（O(M×N×K) 嵌套循环内 Regex new）
4. **测试覆盖与 CI 缺失**（核心 UseCase 无测试，PR 无自动验证）

按本指南 P1-2 → P4 顺序执行后，项目将进入可持续迭代状态。
