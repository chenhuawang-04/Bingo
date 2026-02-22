# 文章解析器（Article Parser）完整项目设计书

## 1. 文档信息
- 项目：EnglishHelper
- 模块：文章解析器（Article + OCR + 词典联动 + 句子分析）
- 文档版本：v1.0
- 目标：在保持现有架构风格（Clean Architecture + Room + Compose + Hilt）的前提下，落地高性能文章学习能力。

---

## 2. 目标与范围

### 2.1 产品目标
1. 用户可创建“文章”，支持手动编辑与拍照导入（多图）。
2. AI 忠实转写文章全文并返回元信息（领域、难度）。
3. 本地提取文章词汇并与辞书匹配。
4. 命中词自动写入词条例句，标签格式：`{文章名} 例句`，支持回跳文章。
5. 文章阅读页中对命中词做标记，并支持跳转词详情。
6. 文章页面展示统计信息：词频、文章长度、文章难度。
7. 阅读时点击句子，弹窗展示句意、语法分析、重点词汇。

### 2.2 非目标（本期不做）
1. 在线多人协作编辑文章。
2. 文章自动配音/朗读（可作为后续扩展）。
3. 全量语法树可视化编辑器。

---

## 3. 关键设计原则（必须高性能）
1. **本地优先**：分句、分词、词频、匹配均本地完成；AI 只做 OCR 与语义分析。
2. **异步流水线**：文章保存与解析解耦，解析在后台任务执行，不阻塞主线程。
3. **批处理写库**：大文本处理采用 chunk 批量事务写入，避免频繁单条 SQL。
4. **强索引 + 去重约束**：所有高频查询字段建索引，跨表写入建立唯一键防止污染。
5. **缓存优先**：句子分析结果与文章解析结果缓存化，重复打开秒级展示。
6. **可回溯**：所有“例句来源”都可追溯到文章与句子。

---

## 4. 需求细化与验收定义

### 4.1 创建与导入文章
- 支持创建文章标题。
- 支持手动输入/编辑正文。
- 支持上传多张图片交给 AI 转写。
- AI 返回：
  - 忠实全文 `content`
  - `domain`（文章领域）
  - `difficulty`（文章难度，建议 1~10）

### 4.2 词典联动
- 从文章正文提取 token（含归一化）。
- 与本地辞书按归一化拼写匹配。
- 若命中：
  - 在文章中标记词为“辞书已存在”
  - 点击词可跳转词详情
  - 在词详情自动新增例句，内容为该词所在句
  - 例句来源显示：`{文章名} 例句`
  - 点击来源跳转到文章对应句子

### 4.3 文章统计
- 词频统计（Top N + 全量）。
- 文章长度（字符数、词数、句数）。
- 难度展示（AI 元信息 + 本地可解释指标合成）。

### 4.4 句子分析
- 点击任一句子 -> 打开 BottomSheet/Dialog。
- 展示：
  - 句子释义
  - 语法分析（关键结构）
  - 重点词汇
- 分析结果缓存，避免重复调用 AI。

---

## 5. 总体架构设计

### 5.1 分层映射
- `ui/`：文章列表、编辑页、阅读页、句子分析弹窗。
- `domain/`：
  - `model/`：Article、Sentence、WordLink、Stats、SentenceAnalysis
  - `repository/`：ArticleRepository、ArticleAiRepository
  - `usecase/article/`：Create/Update/Parse/Link/GetStats/AnalyzeSentence 等
- `data/`：
  - `local/entity/`：Room 实体
  - `local/dao/`：查询/批处理写入
  - `repository/`：实现 domain repository
  - `remote/`：AI DTO + API 调用

### 5.2 核心流程
1. 用户创建文章（手动或 AI）。
2. 保存 Article 基本数据。
3. 后台执行 `ParseArticleUseCase`：
   - 分句 -> 分词 -> 归一化 -> 词频聚合
   - 批量匹配辞书
   - 落库关联与例句
4. 阅读页显示内容与高亮词。
5. 句子点击触发分析（优先读缓存）。

---

## 6. 数据库设计（Room）

> 当前 DB 已到 v5（FSRS），本功能建议升级为 **v6**。  
> 你已明确“无需向后兼容旧版”，但建议保留一次性迁移能力（仅从当前线上版本迁移）。

### 6.1 新增表

#### `article`
- `id: Long PK`
- `title: String`
- `content: String`
- `domain: String?`
- `difficulty_ai: Int?`（1~10）
- `difficulty_local: Int?`（1~10）
- `difficulty_final: Int?`（1~10）
- `source_type: Int`（1 manual / 2 ai）
- `parse_status: Int`（0 pending / 1 processing / 2 done / 3 failed）
- `created_at: Long`
- `updated_at: Long`

索引：
- `idx_article_updated_at`
- `idx_article_title`

#### `article_image`
- `id: Long PK`
- `article_id: Long FK(article.id)`
- `local_uri: String`
- `order_index: Int`
- `created_at: Long`

索引：
- `idx_article_image_article_id_order`

#### `article_sentence`
- `id: Long PK`
- `article_id: Long FK(article.id)`
- `sentence_index: Int`
- `text: String`
- `char_start: Int`
- `char_end: Int`

唯一约束：
- `(article_id, sentence_index)`

索引：
- `idx_sentence_article_id`

#### `article_token_occurrence`
- `id: Long PK`
- `article_id: Long FK(article.id)`
- `sentence_id: Long FK(article_sentence.id)`
- `token: String`
- `normalized_token: String`
- `position_in_sentence: Int`

索引：
- `idx_occ_article_id`
- `idx_occ_sentence_id`
- `idx_occ_normalized_token`

#### `article_word_stats`
- `id: Long PK`
- `article_id: Long FK(article.id)`
- `normalized_token: String`
- `display_token: String`
- `frequency: Int`

唯一约束：
- `(article_id, normalized_token)`

索引：
- `idx_stats_article_id_frequency`

#### `article_word_link`
- `id: Long PK`
- `article_id: Long FK(article.id)`
- `sentence_id: Long FK(article_sentence.id)`
- `word_id: Long FK(word.id)`
- `dictionary_id: Long`
- `matched_token: String`

唯一约束：
- `(article_id, sentence_id, word_id)`

索引：
- `idx_link_article_id`
- `idx_link_word_id`
- `idx_link_dict_id`

#### `sentence_analysis_cache`
- `id: Long PK`
- `article_id: Long FK(article.id)`
- `sentence_id: Long FK(article_sentence.id)`
- `sentence_hash: String`
- `meaning_zh: String`
- `grammar_json: String`
- `keywords_json: String`
- `created_at: Long`

唯一约束：
- `(article_id, sentence_id, sentence_hash)`

索引：
- `idx_analysis_article_sentence`

### 6.2 现有表扩展

#### `word_example`（或你现有例句表）
新增字段：
- `source_type: Int`（0 manual / 1 ai / 2 article）
- `source_article_id: Long?`
- `source_sentence_id: Long?`
- `source_label: String?`（如 `Economist-2026-02 例句`）

唯一约束建议：
- `(word_id, source_type, source_article_id, source_sentence_id)`

---

## 7. Domain 模型设计

### 7.1 核心模型
- `Article`
- `ArticleSentence`
- `ArticleWordStat`
- `ArticleWordLink`
- `ArticleStatistics`（wordCount/sentenceCount/charCount/topFrequencies/difficulty）
- `SentenceAnalysisResult`（meaning/grammarPoints/keyVocabulary）

### 7.2 枚举
- `ArticleSourceType { MANUAL, AI }`
- `ParseStatus { PENDING, PROCESSING, DONE, FAILED }`
- `DifficultyLevel { VERY_EASY ... VERY_HARD }`（可选展示层映射）

---

## 8. UseCase 设计

### 8.1 文章写入
1. `CreateArticleUseCase`
2. `UpdateArticleUseCase`
3. `DeleteArticleUseCase`
4. `SaveArticleImagesUseCase`

### 8.2 解析与联动
1. `ParseArticleUseCase`（核心）
2. `LinkArticleWordsToDictionaryUseCase`
3. `SyncArticleExamplesToWordsUseCase`
4. `ReparseArticleUseCase`（重跑）

### 8.3 查询
1. `GetArticleListUseCase`
2. `GetArticleDetailUseCase`
3. `GetArticleStatisticsUseCase`
4. `GetArticleWordFrequencyUseCase`
5. `GetWordArticleExamplesUseCase`

### 8.4 句子分析
1. `AnalyzeSentenceUseCase`（先查 cache）
2. `GetSentenceAnalysisCacheUseCase`

### 8.5 AI
1. `ExtractArticleFromImagesUseCase`
2. `AnalyzeSentenceByAiUseCase`

---

## 9. AI 接口协议设计

## 9.1 多图 OCR + 元信息抽取（必须忠实）
输入：
- 图片列表（按顺序）
- 可选标题

输出 JSON（严格）：
```json
{
  "title": "string",
  "content": "string",
  "domain": "news|tech|finance|science|literature|exam|other",
  "difficulty": 1,
  "confidence": 0.0
}
```

约束：
1. content 必须忠实转写，不润色、不删改。
2. 保留段落顺序。
3. 难度范围固定 1~10。

## 9.2 句子分析
输出 JSON（严格）：
```json
{
  "meaning_zh": "string",
  "grammar_points": [
    {"name": "string", "explain": "string"}
  ],
  "key_vocabulary": [
    {"word": "string", "level": "B2", "explain_zh": "string"}
  ]
}
```

---

## 10. 高性能实现方案（重点）

### 10.1 解析算法
1. `SentenceSplitter`：一次扫描分句，返回 `List<SentenceSpan>`。
2. `Tokenizer`：按句分词，输出 token + normalizedToken + position。
3. `FrequencyAggregator`：在内存 `MutableMap<String, Int>` 聚合词频。
4. `DictionaryMatcher`：
   - 一次性加载当前词库 `normalizedWord -> List<WordRef>`
   - O(n) 扫描 token 命中映射
5. 结果批量写入：
   - sentence chunk 500
   - occurrence chunk 1000
   - link chunk 500
   - example chunk 300

### 10.2 SQL 优化
1. 所有阅读态查询走索引字段。
2. 文章详情统计优先读取 `article_word_stats`，避免实时 `GROUP BY`。
3. 重解析前先删除旧关联（按 `article_id` 批删）再写新值，保证一致性与速度。

### 10.3 线程模型
1. UI 线程仅发起任务与渲染结果。
2. 解析任务使用 `Dispatchers.Default + IO` 混合。
3. 大任务放 WorkManager（可恢复）。

### 10.4 缓存策略
1. 句子分析缓存键：`(articleId, sentenceId, sentenceHash)`。
2. 若 sentence 文本改变，hash 变化自动失效。
3. 可设置 TTL（如 30 天）+ 手动清理入口。

### 10.5 性能指标（SLO）
1. 3000 词文章：本地解析 + 联动 <= 3 秒（中高端机）。
2. 阅读页首屏 <= 300ms（缓存命中）。
3. 已分析句子二次打开 <= 200ms。
4. 大文章滚动无明显掉帧（>= 55fps 目标）。

---

## 11. UI/UX 详细设计

## 11.1 文章列表页 `ArticleListScreen`
- 搜索（标题/内容关键字）
- 排序：最近更新 / 难度 / 长度
- 卡片信息：标题、领域、难度、词数、更新时间

## 11.2 新建/编辑页 `ArticleEditorScreen`
- 标题输入
- 正文编辑
- 图片导入（多图，支持排序/删除）
- “AI 识别并填充”按钮
- 保存后显示解析状态（处理中/完成/失败）

## 11.3 阅读页 `ArticleReaderScreen`
- 顶部：标题、领域、难度、统计概览
- 正文按句渲染
- 命中辞书词高亮 + 可点击
- 点击句子弹出分析面板

## 11.4 统计页（可与阅读页同页）
- 词频 Top N（可点击词）
- 文章长度：字符/词/句
- 难度解释：AI 与本地评分组成

## 11.5 词详情联动
- 新增“文章例句”分组
- 每条例句显示来源：`{文章名} 例句`
- 点击来源跳转文章并定位句子

---

## 12. 导航与跳转

### 12.1 新增路由
- `ArticleListRoute`
- `ArticleEditorRoute(articleId?)`
- `ArticleDetailRoute(articleId, sentenceId?)`

### 12.2 深链/参数
- 词详情 -> 文章句子：传 `articleId + sentenceId`
- 文章词点击 -> 词详情：传 `wordId + dictionaryId`

---

## 13. 导入导出（JSON）设计

> 现有 schemaVersion 已到 4（FSRS）。本功能后建议升级至 **schemaVersion 5**。

新增导出内容：
1. article 基础信息
2. article_sentence（可选）
3. article_word_stats（可选）
4. article_word_link（可选）
5. word_example 中 article 类型来源

建议策略：
- 默认导出 article + article_word_stats + article-linked examples。
- occurrence 可不导出（体积大，可重建）。

---

## 14. 异常处理与回退
1. OCR 失败：保留原图与手动编辑能力，不影响文章保存。
2. 解析失败：`parse_status=FAILED`，允许重试。
3. 分析失败：弹窗提示并可重试，不阻塞阅读。
4. 跳转失效（文章被删）：显示“来源不可用”。

---

## 15. 安全与隐私
1. 图片仅本地保存 URI，不默认上传云端。
2. 上传 AI 前需用户显式触发。
3. 可增加“导入后删除原图缓存”开关。
4. 建议将签名密码等敏感信息移出代码，统一使用 Secrets/本地安全配置。

---

## 16. 测试方案

### 16.1 单元测试
1. 分句、分词、归一化规则。
2. 词频统计正确性。
3. 辞书匹配正确性（大小写、标点、复数/时态）。
4. 例句去重与来源字段正确性。
5. 句子缓存命中/失效逻辑。

### 16.2 数据层测试（Room）
1. 索引查询性能回归。
2. 唯一约束验证。
3. v5 -> v6 迁移测试。

### 16.3 UI 测试
1. 文章创建与编辑流程。
2. 词跳转与回跳。
3. 句子分析弹窗展示。

### 16.4 集成测试
1. 多图 OCR -> 保存文章 -> 解析 -> 词例句联动全链路。
2. 重解析不产生重复数据。

---

## 17. 里程碑计划

## M1（1 周）：数据与基础流程
- Room 实体/DAO/迁移
- Article CRUD
- 本地分句分词与词频

## M2（1 周）：词典联动
- 文章词匹配
- 词条例句落库
- 双向跳转

## M3（1 周）：AI OCR + 元信息
- 多图输入
- 忠实转写协议
- 失败重试与编辑修正

## M4（1 周）：句子分析与缓存
- 点击句子分析
- 缓存策略
- 性能收尾与测试补齐

---

## 18. 开发清单（按你当前项目结构）

### 18.1 建议新增文件
- `domain/model/Article.kt`
- `domain/model/ArticleSentence.kt`
- `domain/model/ArticleWordStat.kt`
- `domain/model/SentenceAnalysisResult.kt`
- `domain/repository/ArticleRepository.kt`
- `domain/repository/ArticleAiRepository.kt`
- `domain/usecase/article/ArticleUseCases.kt`
- `data/local/entity/ArticleEntity.kt`
- `data/local/entity/ArticleSentenceEntity.kt`
- `data/local/entity/ArticleTokenOccurrenceEntity.kt`
- `data/local/entity/ArticleWordStatEntity.kt`
- `data/local/entity/ArticleWordLinkEntity.kt`
- `data/local/entity/SentenceAnalysisCacheEntity.kt`
- `data/local/dao/ArticleDao.kt`
- `data/repository/ArticleRepositoryImpl.kt`
- `data/repository/ArticleAiRepositoryImpl.kt`
- `ui/screen/article/ArticleListScreen.kt`
- `ui/screen/article/ArticleEditorScreen.kt`
- `ui/screen/article/ArticleReaderScreen.kt`
- `ui/screen/article/ArticleViewModel.kt`
- `ui/screen/article/SentenceAnalysisSheet.kt`

### 18.2 需要修改文件
- `data/local/AppDatabase.kt`（v5 -> v6）
- `di/AppModule.kt`（Repository/UseCase 绑定）
- `ui/navigation/NavGraph.kt`（新增文章路由）
- `word` 相关详情页与例句区块（支持 article source 跳转）
- 导入导出 JSON 模型与实现（schemaVersion 5）

---

## 19. 接受标准（最终验收）
1. 用户可创建/编辑/OCR 导入文章并保存成功。
2. 文章可展示领域、难度、词频、长度。
3. 命中辞书词可双向跳转，词详情出现 `{文章名} 例句`。
4. 点击句子可获得句意+语法+重点词，且有缓存。
5. 3000 词文章解析与联动不超过 3 秒（目标机型）。
6. 无重复例句污染、无明显卡顿、无主线程阻塞 ANR。

---

## 20. 后续可扩展方向（非本期）
1. 文章生词本（按文章自动生成学习计划）。
2. 句式模板抽取与复习。
3. 文章对比阅读（原文/译文对照）。
4. 朗读与跟读评分。

