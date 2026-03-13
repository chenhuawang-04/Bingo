<p align="center">
  <img src="bingo.ico" alt="Bingo" width="128" />
</p>

<h1 align="center">Bingo</h1>

<p align="center">
  <a href="README_EN.md">English</a>
</p>

一款基于 AI 的英语词汇学习 Android 应用，帮助你从「查词 → 拆词 → 阅读 → 刷题 → 复习」全流程系统性掌握英语词汇。

## 功能特性

### 词汇管理
- **辞书系统** — 创建多本辞书，分类管理不同来源的词汇
- **单词录入** — 手动添加单词，支持拼写、音标、词性与释义、词根解释、词形变化等字段
- **AI 自动整理** — 一键调用 AI，自动填充音标、释义、词根拆解、近义词、形近词、同根词、词形变化
- **后台整理** — 「保存并后台整理」按钮保存后台 AI 整理，无需等待即可继续添加；失败任务支持一键重试
- **批量拍照导入** — 选择图片 → 输入提取条件（如“蓝色字体”） → AI 提取单词列表 → 预览勾选 → 批量导入并自动后台整理
- **去重保护** — 基于规范化拼写（小写 + 去空格）自动检测重复，支持 upsert 语义
- **词形变化** — 记录复数、过去式、过去分词、现在分词、第三人称、比较级、最高级等变形
- **语音朗读（单词）** — 支持单词 TTS 朗读与播放控制

### 词根拆解
- **结构化拆解** — 将单词分解为前缀、词根、后缀、词干、连接成分等构词成分，标注类型和含义
- **可视化展示** — 详情页以结构化卡片展示拆解，词根类型加粗高亮

### 关联词网络
- **联想词** — 基于词根拆解片段的 Jaccard 相似度 + 词根加权自动计算词条间的关联关系，保存后自动重算
- **关联词跳转** — 近义词、形近词、同根词中已存在于辞书的词条可点击直接跳转至详情页
- **联想词展示** — 详情页底部展示与当前词共享构词成分的联想词列表，均可点击跳转

### 题库模块（新）
- **试卷扫描** — 选择图片或 PDF，AI 自动识别题型、文章段落、题目与选项，支持预览编辑后保存
- **PDF 支持** — 基于 Android PdfRenderer 将 PDF 逐页渲染为图片，走统一扫描链路
- **题库列表** — 按试卷分组展示题组，显示题型标签、题数/字数/难度统计、来源验证与答案状态
- **阅读做题** — 单页同时呈现文章阅读（词链高亮、TTS、翻译、段落分析、收纳本）与做题（选答案、提交、对错判定）
- **错题追踪** — 每次答错自动累加 `wrong_count`；历史错题以橙色（1次）/红色（2次+）边框高亮，右上角显示错误次数
- **AI 答案生成** — 保存后后台自动调用快速模型为每道题生成答案与解析
- **扫描答案** — 拍照上传答案图片，AI 提取答案并覆盖更新
- **来源验证** — 保存后后台自动验证来源 URL（需搜索模型）；验证成功自动创建关联文章并可跳转阅读原文；验证失败支持编辑 URL 重试或重新搜索
- **分域 AI 设置** — 新增扫描模型（SCAN）和搜索模型（SEARCH）两个独立 AI 配置

### 文章阅读与词汇链接（重构）
- **段落级数据模型** — 文章以段落为中心存储与渲染，阅读器按段落结构化显示
- **文章管理** — 手动录入或拍照上传图片，通过 AI OCR 自动提取英文文章标题、正文、领域和难度
- **自动解析** — 文章保存后自动进入后台解析流程：拆句、分词、词频统计
- **词汇高亮** — 阅读器中辞书已收录词汇自动高亮显示（含词形变化匹配），点击高亮词直接跳转单词详情
- **双向链接** — 文章中匹配到的词汇自动建立 Article ↔ Word 双向链接；保存/修改单词时自动回填新链接
- **例句提取** — 从文章中自动提取包含目标单词的例句，标注来源（如「文章标题」例句），在单词详情页展示
- **段落翻译** — 段落级 AI 翻译，支持一键开关与缓存
- **段落分析** — 段落级 AI 分析：中文翻译、语法点、关键词汇、句子拆解，分析结果缓存
- **生词收纳本** — 阅读中可将词汇加入收纳本，快速分析并一键加入指定词典/单元
- **语音朗读（文章）** — 文章 TTS 朗读，支持暂停/继续、上一段/下一段、停止与跟随滚动

### Guardian 在线阅读
- **栏目浏览** — 按栏目分组浏览 Guardian 热门文章
- **在线解析** — 在线文章解析为临时文章，可直接阅读
- **保存到本地** — 一键保存为本地文章，进入完整解析与统计流程
- **在线词汇扫描** — 在线文章阅读时即时扫描词汇并高亮

### 词池系统
- **词池** — 基于多维语言信号自动将相关词汇分组，辅助联想记忆
- **三种生成策略**：
  - **均衡（本地）**：基于编辑距离、同义/形近/同根交叉引用、中文释义重叠、联想词相似度的 Union-Find 聚类
  - **均衡 + AI**：在均衡策略基础上，将未分配词汇按批次发送 AI 补充分组
  - **质量优先（高消耗）**：逐词调用 AI 精确比对，需用户确认 token 消耗
- **算法版本追踪** — 当现有词池使用旧版算法生成时，页面提示建议重建

### 间隔重复学习
- **单元分组** — 将单词按单元归类，支持多选分配
- **FSRS-5 算法** — 基于自适应间隔重复算法 FSRS-5 调度复习，替代固定艾宾浩斯曲线
- **学习仪表板** — 首页展示留存率、到期词数、今日进度、清空预估等 FSRS 统计
- **学习模式** — 选择单元后进入复习流程，四级评分（重来/困难/良好/简单）并实时预览下次间隔
- **头脑风暴模式** — 利用词池将相关词汇聚合到一起连续复习，在保持 FSRS 调度的同时增强联想记忆

### 自适应 UI
- **响应式布局** — 基于 Material 3 Adaptive 框架，按 WindowSizeClass 自动切换手机/平板布局
- **底部导航** — 辞书 Tab + 文章 Tab + 题库 Tab 三标签切换，手机使用 NavigationBar，平板使用 NavigationRail
- **首页自适应** — 手机：仪表板卡片 + 辞书列表；平板：侧栏仪表板 + 辞书网格
- **列表-详情分栏** — 平板上辞书页面展示列表-详情分栏，点击单词在右侧面板显示详情
- **双列表单** — 平板上添加/编辑单词页面采用左右双列布局
- **学习分栏** — 平板上学习页面左侧主窗格 + 右侧进度/统计面板
- **阅读器分栏** — 平板上文章阅读页面主窗格 + 右侧统计信息面板
- **大屏限宽** — 设置和导入导出页面在大屏上居中限宽，避免表单过度拉伸
- **设计令牌系统** — “墨与薄荷”色彩令牌（InkBlue + MintTeal）+ 语义色 + 间距体系 + 响应式 Typography

### 导入导出
- **JSON 格式** — 以 JSON 文件导入/导出整本辞书，包含单词、单元、学习状态
- **Schema 版本控制** — 当前版本 schemaVersion: 3，包含词根拆解数据
- **导入后自动重建** — 导入辞书后自动批量计算联想词关联

### 设置
- **API 配置** — 支持 Anthropic 和 OpenAI 兼容两种提供商，自定义 API Key、模型选择、自定义 Base URL
- **AI 分域设置** — 可为词池生成（POOL）、OCR 识别（OCR）、文章分析（ARTICLE）、题库扫描（SCAN）与来源搜索（SEARCH）配置独立 AI 模型
- **API Key 加密存储** — 使用 AndroidX Security Crypto 加密保存
- **连接测试** — 配置后可一键测试 API 连通性（主设置和分域设置各自独立测试）

## 技术栈

| 层级 | 技术 |
|------|------|
| UI | Jetpack Compose + Material 3 + Material 3 Adaptive |
| 架构 | Clean Architecture（Domain / Data / UI），Screen 三层拆分（容器/内容/组件） |
| 依赖注入 | Hilt |
| 本地存储 | Room（SQLite），DataStore Preferences |
| 网络 | Retrofit + OkHttp + Moshi |
| 图片 | Coil |
| HTML 解析 | Jsoup |
| 导航 | Navigation Compose（类型安全路由 + 三 Tab 导航） |
| AI | Anthropic Claude API / OpenAI 兼容 API（自动整理、OCR 提取、句子分析、词池生成、批量导入、段落分析/翻译、题库扫描/答案生成/来源验证） |
| 安全 | AndroidX Security Crypto |
| 异步 | Kotlin Coroutines + Flow |
| 间隔重复 | FSRS-5 自适应算法 |
| NLP | 文章分句、分词、词典匹配（Regex 预编译 + 词形变化索引） |
| 测试 | JUnit 4 + MockK + Room Testing + CI（GitHub Actions） |

## 项目结构

```
com.xty.englishhelper/
├── data/                        # 数据层
│   ├── json/                    # JSON 导入导出
│   ├── local/                   # Room 数据库
│   │   ├── converter/           # 类型转换器
│   │   ├── dao/                 # 数据访问对象
│   │   ├── entity/              # 数据库实体
│   │   └── relation/            # 关系查询
│   ├── mapper/                  # Entity ↔ Domain 映射
│   ├── preferences/             # DataStore + 加密存储
│   ├── remote/                  # AI 客户端 + Guardian 解析
│   │   ├── dto/                 # 请求/响应 DTO
│   │   ├── guardian/            # Guardian 抓取与解析
│   │   └── interceptor/         # OkHttp 拦截器
│   └── repository/              # Repository 实现
├── di/                          # Hilt 依赖注入模块
├── domain/                      # 领域层
│   ├── article/                 # 文章解析工具（分句、分词、词典匹配）
│   ├── model/                   # 领域模型（含 AiSettingsScope 枚举）
│   ├── organize/                # 后台整理管理器
│   ├── pool/                    # 词池引擎
│   ├── repository/              # Repository 接口
│   ├── study/                   # FSRS 间隔重复引擎
│   ├── tts/                     # 语音朗读能力
│   └── usecase/                 # 用例
│       ├── ai/                  # AI 自动整理
│       ├── article/             # 文章解析、段落分析/翻译、词汇回填
│       ├── dictionary/          # 辞书 CRUD
│       ├── importexport/        # 导入导出
│       ├── questionbank/        # 题库用例（缓存 ID 隔离、扫描结果转换）
│       ├── study/               # 学习调度
│       ├── unit/                # 单元管理
│       └── word/                # 单词保存（含自动文章链接）
├── ui/                          # UI 层
│   ├── adaptive/                # WindowSizeClass 工具
│   ├── components/              # 可复用组件
│   │   └── reading/             # 共享阅读组件（ParagraphBlock, TtsPlaybackBar 等）
│   ├── designsystem/            # 设计系统
│   │   ├── components/          # 通用组件库（EhCard, EhStatTile, EhStudyRatingBar 等）
│   │   └── tokens/              # 设计令牌（色彩/间距/Typography）
│   ├── navigation/              # 导航图 + 路由定义（三 Tab）
│   ├── screen/                  # 各页面（容器/内容/组件三层拆分）
│   │   ├── addword/             # 添加/编辑单词
│   │   ├── article/             # 文章列表/编辑/阅读器/段落分析
│   │   ├── batchimport/         # 拍照批量导入
│   │   ├── dictionary/          # 辞书详情
│   │   ├── guardian/            # Guardian 浏览与阅读
│   │   ├── home/                # 首页 + 仪表板
│   │   ├── importexport/        # 导入导出
│   │   ├── main/                # 主框架 + 底部/侧边导航
│   │   ├── questionbank/        # 题库（列表/扫描/阅读做题）
│   │   ├── settings/            # 设置
│   │   ├── study/               # 学习模式
│   │   ├── unitdetail/          # 单元详情
│   │   └── word/                # 单词详情（含文章例句）
│   └── theme/                   # Material 主题
└── util/                        # 工具类
```

## 构建

### 环境要求

- Android Studio Hedgehog 或更高版本
- JDK 17
- Android SDK 35（compileSdk）
- 最低支持 Android 8.0（API 26）

### 编译运行

```bash
# 编译 Debug APK
./gradlew assembleDebug

# 运行单元测试
./gradlew testDebugUnitTest

# 运行 Instrumented 测试（需连接设备或模拟器）
./gradlew connectedDebugAndroidTest
```

## 使用说明

1. **配置 API** — 进入设置页面，填入 API Key，选择提供商和模型，点击测试连接
2. **创建辞书** — 在辞书 Tab 首页点击「+」创建辞书
3. **添加单词** — 进入辞书，点击「+」选择「手动添加」输入单词拼写，点击「AI 自动整理」一键填充
4. **批量导入** — 进入辞书，点击「+」选择「拍照批量导入」，选图片、输入条件、AI 提取、勾选导入
5. **浏览关联** — 在单词详情页查看词根拆解、点击近义词/形近词/同根词跳转、查看联想词和词池
6. **创建单元** — 在辞书页面管理单元，将单词分配到不同单元
7. **生成词池** — 在辞书页面菜单选择词池生成策略，为联想记忆做准备
8. **开始学习** — 选择单元进入复习模式，可切换头脑风暴模式，系统按 FSRS-5 算法自适应调度
9. **添加文章** — 切换到文章 Tab，点击「+」手动输入或上传图片 AI 提取
10. **阅读文章** — 点击文章卡片进入阅读器，已收录词汇自动高亮；点击高亮词跳转详情
11. **段落翻译/分析** — 阅读器顶部开启翻译，或点击段落「整理」进行段落分析
12. **收纳生词** — 点击未收录单词可加入收纳本，并一键加入词典/单元
13. **Guardian 阅读** — 进入 Guardian 页面浏览在线文章并保存到本地
14. **语音朗读** — 在文章阅读器点击播放，支持跟随滚动与段落切换
15. **题库扫描** — 切换到题库 Tab，点击「扫描」按钮，选择图片或 PDF，AI 自动识别题目
16. **做题练习** — 点击题组进入阅读做题页，文章阅读功能齐全，下方做题并查看对错与解析
17. **分域设置**（可选）— 在设置页开启词池/ OCR / 文章 / 扫描 / 搜索 AI，配置独立模型

## 数据库版本

| 版本 | 变更 |
|------|------|
| 1 | 初始 schema：dictionaries, words, synonyms, similar_words, cognates |
| 2 | 新增 units, unit_word_cross_ref, word_study_state |
| 3 | words 表新增 normalized_spelling, word_uid 列 + 唯一索引 |
| 4 | words 表新增 decomposition_json 列；新增 word_associations 表 |
| 5 | word_study_state 迁移至 FSRS-5 字段（stability/difficulty/due/reps/lapses） |
| 6 | words 新增 inflections_json；新增文章模块（articles, article_sentences, article_word_stats, article_word_links, sentence_analysis_cache, word_examples, article_images） |
| 7 | article_word_stats 新增 normalized_token 索引 |
| 8 | sentence_analysis_cache 新增 model_key 列 + 复合唯一索引 |
| 9 | 新增 word_pools 表（id, dictionary_id, focus_word_id, strategy, algorithm_version）和 word_pool_members 联结表（word_id, pool_id），含级联外键 |
| 10 | 文章改为段落结构化存储，新增 paragraph 分析缓存与段落级关系 |
| 11 | 文章段落分析/翻译缓存完善，Guardian 在线阅读与临时文章支持 |
| 12 | 题库模块：exam_papers, question_groups, question_group_paragraphs, question_items, practice_records, question_source_articles 六表 |

## 许可证

本项目基于 [AGPL-3.0 license](LICENSE) 开源。
