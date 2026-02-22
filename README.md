<p align="center">
  <img src="bingo.ico" alt="Bingo" width="128" />
</p>

<h1 align="center">Bingo</h1>

<p align="center">
  <a href="README_EN.md">English</a>
</p>

一款基于 AI 的英语词汇学习 Android 应用，帮助你从「查词 → 拆词 → 阅读 → 复习」全流程系统性掌握英语词汇。

## 功能特性

### 词汇管理
- **辞书系统** — 创建多本辞书，分类管理不同来源的词汇
- **单词录入** — 手动添加单词，支持拼写、音标、词性与释义、词根解释、词形变化等字段
- **AI 自动整理** — 一键调用 Claude API，自动填充音标、释义、词根拆解、近义词、形近词、同根词、词形变化
- **去重保护** — 基于规范化拼写（小写 + 去空格）自动检测重复，支持 upsert 语义
- **词形变化** — 记录复数、过去式、过去分词、现在分词、第三人称、比较级、最高级等变形

### 词根拆解
- **结构化拆解** — 将单词分解为前缀、词根、后缀、词干、连接成分等构词成分，标注类型和含义
- **可视化展示** — 详情页以结构化卡片展示拆解，词根类型加粗高亮

### 关联词网络
- **联想词** — 基于词根拆解片段的 Jaccard 相似度 + 词根加权自动计算词条间的关联关系，保存后自动重算
- **关联词跳转** — 近义词、形近词、同根词中已存在于辞书的词条可点击直接跳转至详情页
- **联想词展示** — 详情页底部展示与当前词共享构词成分的联想词列表，均可点击跳转

### 文章阅读与词汇链接
- **文章管理** — 手动录入或拍照上传图片，通过 AI OCR 自动提取英文文章标题、正文、领域和难度
- **自动解析** — 文章保存后自动进入后台解析流程：拆句、分词、词频统计
- **词汇高亮** — 阅读器中辞书已收录词汇自动高亮显示（含词形变化匹配），点击高亮词直接跳转单词详情
- **双向链接** — 文章中匹配到的词汇自动建立 Article ↔ Word 双向链接；保存/修改单词时自动回填新链接
- **例句提取** — 从文章中自动提取包含目标单词的例句，标注来源（如「文章标题」例句），在单词详情页展示
- **句子分析** — 长按任意句子调用 AI 分析：中文翻译、语法点、关键词汇，分析结果按模型版本缓存避免重复请求
- **解析状态** — 文章卡片实时显示解析进度（待解析 / 解析中 / 已完成 / 解析失败）

### 间隔重复学习
- **单元分组** — 将单词按单元归类，支持多选分配
- **FSRS-5 算法** — 基于自适应间隔重复算法 FSRS-5 调度复习，替代固定艾宾浩斯曲线
- **学习仪表板** — 首页展示留存率、到期词数、今日进度、清空预估等 FSRS 统计
- **学习模式** — 选择单元后进入复习流程，四级评分（重来/困难/良好/简单）并实时预览下次间隔

### 自适应 UI
- **响应式布局** — 基于 Material 3 Adaptive 框架，按 WindowSizeClass 自动切换手机/平板布局
- **底部导航** — 辞书 Tab + 文章 Tab 双标签切换，手机使用 NavigationBar，平板使用 NavigationRail
- **首页自适应** — 手机：仪表板卡片 + 辞书列表；平板：侧栏仪表板 + 辞书网格
- **列表-详情分栏** — 平板上辞书页面展示列表-详情分栏，点击单词在右侧面板显示详情
- **双列表单** — 平板上添加/编辑单词页面采用左右双列布局
- **学习分栏** — 平板上学习页面左侧主窗格 + 右侧进度/统计面板
- **阅读器分栏** — 平板上文章阅读页面主窗格 + 右侧统计信息面板
- **大屏限宽** — 设置和导入导出页面在大屏上居中限宽，避免表单过度拉伸
- **设计令牌系统** — "墨与薄荷"色彩令牌（InkBlue + MintTeal）+ 语义色 + 间距体系 + 响应式 Typography

### 导入导出
- **JSON 格式** — 以 JSON 文件导入/导出整本辞书，包含单词、单元、学习状态
- **Schema 版本控制** — 当前版本 schemaVersion: 3，包含词根拆解数据
- **导入后自动重建** — 导入辞书后自动批量计算联想词关联

### 设置
- **API 配置** — 支持自定义 API Key、模型选择（Haiku / Sonnet / Opus）、自定义 Base URL
- **API Key 加密存储** — 使用 AndroidX Security Crypto 加密保存
- **连接测试** — 配置后可一键测试 API 连通性

## 技术栈

| 层级 | 技术 |
|------|------|
| UI | Jetpack Compose + Material 3 + Material 3 Adaptive |
| 架构 | Clean Architecture（Domain / Data / UI），Screen 三层拆分（容器/内容/组件） |
| 依赖注入 | Hilt |
| 本地存储 | Room（SQLite），DataStore Preferences |
| 网络 | Retrofit + OkHttp + Moshi |
| 导航 | Navigation Compose（类型安全路由 + 双 Tab 导航） |
| AI | Anthropic Claude API（自动整理、OCR 提取、句子分析） |
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
│   ├── remote/                  # Anthropic API 客户端
│   │   ├── dto/                 # 请求/响应 DTO
│   │   └── interceptor/         # OkHttp 拦截器
│   └── repository/              # Repository 实现
├── di/                          # Hilt 依赖注入模块
├── domain/                      # 领域层
│   ├── article/                 # 文章解析工具（分句、分词、词典匹配）
│   ├── model/                   # 领域模型（含 WordExampleSourceType 枚举）
│   ├── repository/              # Repository 接口
│   ├── study/                   # FSRS 间隔重复引擎
│   └── usecase/                 # 用例
│       ├── ai/                  # AI 自动整理
│       ├── article/             # 文章解析、句子分析、词汇回填
│       ├── dictionary/          # 辞书 CRUD
│       ├── importexport/        # 导入导出
│       ├── study/               # 学习调度
│       ├── unit/                # 单元管理
│       └── word/                # 单词保存（含自动文章链接）
├── ui/                          # UI 层
│   ├── adaptive/                # WindowSizeClass 工具
│   ├── components/              # 可复用组件
│   ├── designsystem/            # 设计系统
│   │   ├── components/          # 通用组件库（EhCard, EhStatTile, EhStudyRatingBar 等）
│   │   └── tokens/              # 设计令牌（色彩/间距/Typography）
│   ├── navigation/              # 导航图 + 路由定义（双 Tab）
│   ├── screen/                  # 各页面（容器/内容/组件三层拆分）
│   │   ├── addword/             # 添加/编辑单词
│   │   ├── article/             # 文章列表/编辑/阅读器/句子分析
│   │   ├── dictionary/          # 辞书详情
│   │   ├── home/                # 首页 + 仪表板
│   │   ├── importexport/        # 导入导出
│   │   ├── main/                # 主框架 + 底部/侧边导航
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

1. **配置 API** — 进入设置页面，填入 Anthropic API Key，选择模型，点击测试连接
2. **创建辞书** — 在辞书 Tab 首页点击「+」创建辞书
3. **添加单词** — 进入辞书，点击「+」输入单词拼写，点击「AI 自动整理」一键填充
4. **浏览关联** — 在单词详情页查看词根拆解、点击近义词/形近词/同根词跳转、查看联想词
5. **创建单元** — 在辞书页面管理单元，将单词分配到不同单元
6. **开始学习** — 选择单元进入复习模式，系统按 FSRS-5 算法自适应调度
7. **添加文章** — 切换到文章 Tab，点击「+」手动输入或上传图片 AI 提取
8. **阅读文章** — 点击文章卡片进入阅读器，已收录词汇自动高亮；点击高亮词跳转详情
9. **分析句子** — 在阅读器中长按任意句子，AI 自动分析翻译、语法和关键词

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

## 许可证

本项目基于 [MIT License](LICENSE) 开源。
