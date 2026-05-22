<p align="center">
  <img src="bingo.ico" alt="Bingo" width="128" />
</p>

<h1 align="center">Bingo</h1>

<p align="center">
  AI 驱动的考研英语全流程学习平台<br/>
  查词 · 拆词 · 阅读 · 刷题 · 批改 · 复习
</p>

<p align="center">
  <a href="README_EN.md">English</a>&nbsp;&nbsp;·&nbsp;&nbsp;
  <a href="RELEASE_NOTES.md">Release Notes</a>
</p>

---

## 概览

Bingo 是一款 Android 原生应用，围绕**考研英语备考**全链路设计。通过 AI 深度集成，将词汇管理、英文阅读、真题练习和间隔复习统一在三个 Tab 中：

| 辞书 | 文章 | 题库 |
|:---:|:---:|:---:|
| 词汇录入 · AI 整理 · 词根拆解 · 词池聚类 · FSRS 复习 | 本地文章 · 在线阅读 · 段落翻译/分析 · 生词收纳 · TTS | 试卷扫描 · 10 种题型 · AI 答案/批改 · 来源验证 · 错题追踪 |

---

## 功能特性

### 辞书与词汇

- **多辞书管理** — 创建多本辞书，按来源、难度或用途分类管理
- **AI 自动整理** — 一键填充音标、释义、词根拆解、近义词、形近词、同根词、词形变化
- **后台整理** — 保存后 AI 后台整理，无需等待；失败任务支持重试
- **批量拍照导入** — 选图片 → 输入提取条件 → AI 提取 → 预览勾选 → 批量导入
- **词根拆解** — 结构化分解为前缀/词根/后缀/词干/连接成分，卡片式可视化展示
- **关联词网络** — 基于构词成分 Jaccard 相似度自动计算联想词，近义词/形近词/同根词可点击跳转
- **去重保护** — 基于规范化拼写自动检测重复，支持 upsert 语义

### 词池系统

三种生成策略将相关词汇自动分组，辅助联想记忆：

| 策略 | 说明 |
|------|------|
| 均衡（本地） | 编辑距离 + 同义/形近/同根交叉引用 + 释义重叠的 Union-Find 聚类 |
| 均衡 + AI | 本地策略基础上，未分配词汇按批次发送 AI 补充分组 |
| 质量优先 | 逐词 AI 精确比对，高消耗但分组最精准 |

### 间隔重复学习

- **FSRS-5 算法** — 自适应调度，替代固定艾宾浩斯曲线
- **四级评分** — 重来 / 困难 / 良好 / 简单，实时预览下次间隔
- **头脑风暴模式** — 利用词池将关联词汇聚合连续复习，FSRS 调度 + 联想记忆
- **学习仪表板** — 留存率、到期词数、今日进度、清空预估

### 文章阅读

- **段落级数据模型** — 文章以段落为单位存储与渲染
- **AI OCR 录入** — 拍照上传图片，AI 自动提取标题、正文、领域和难度
- **词汇高亮** — 辞书已收录词汇（含词形变化）自动高亮，点击跳转详情
- **双向链接** — Article ↔ Word 自动建立，例句自动提取并标注来源
- **段落翻译** — 段落级 AI 翻译，一键开关，结果缓存
- **段落分析** — AI 分析：中文翻译、语法点、关键词汇、句子拆解
- **生词收纳本** — 阅读中将陌生词汇加入收纳本，AI 快速分析并一键加入词典/单元
- **文章 TTS** — 逐段朗读，支持暂停/跟随滚动/段落切换
- **适配度评分** — AI 评估文章是否适合出题（0-100 分），支持按评分筛选排序
- **分类管理** — 文章按分类组织，支持长度/评分/排序多维筛选
- **文章出题** — 在阅读页一键出题，选择题型与卷名，AI 自动生成题干

### 在线阅读

三大英文媒体源，统一浏览入口：

- **The Guardian**（卫报）— 按栏目浏览热门文章
- **The Atlantic** — 深度长文
- **CS Monitor** — 时事评论

在线文章即时解析为临时文章，可阅读、词汇扫描、一键保存到本地。

### 题库模块

#### 试卷扫描

选择图片或 PDF → AI 自动识别题型/文章段落/题目/选项 → 预览编辑 → 保存。支持题型自动识别与手动修正。

#### 10 种题型全覆盖

| 题型 | 特色功能 |
|------|----------|
| **阅读理解** | 单页阅读 + 做题，词链高亮/TTS/翻译/段落分析/收纳本 |
| **完形填空** | 文中空格交互式填写，竖屏上下/横屏左右分栏 |
| **翻译**（英语一/二） | 自由文本输入 → AI 评分（0-2 分）+ 参考译文 + 翻译要点 |
| **写作**（小/大作文） | OCR 识别手写 → AI 五档评分 + 分维度打分 + 扣分明细 + 改进建议 |
| **段落排序** | 选择段落字母（A-H）填入空位 |
| **句子插入** | 选择候选句（A-G）插入文中空白处，支持手动编辑选项 |
| **评论观点匹配** | 将评论匹配到总结性观点（A-G） |
| **小标题匹配** | 为段落选择最佳小标题（A-G） |
| **信息匹配** | 将描述匹配到选项（A-G） |

#### AI 深度集成

- **答案生成** — 保存后后台自动为每题生成答案与解析
- **答案扫描** — 拍照上传答案页，AI 提取并更新
- **来源验证** — 联网搜索阅读理解原文出处，验证成功自动创建可阅读的关联文章
- **翻译评分** — 逐句对比参考译文，打分 + 得/失分点反馈
- **写作批改** — 遵循考研评分手册，五档定位 → 细则微调，含字数扣分/模板降档等规则
- **范文检索** — 搜索模型联网查找真实范文与链接
- **错题追踪** — 答错自动累加 `wrong_count`，橙色（1次）/红色（2次+）边框高亮

### 学习计划

- **计划模板** — 创建自定义学习计划模板，按天分配任务
- **日记录** — 记录每日学习事件与完成情况
- **云同步** — 计划数据通过 `plan.json` 同步至 GitHub，支持增量合并
- **本地导入导出** — 计划数据独立导出/导入，Schema 与辞书/题库一致

### 导入导出与同步

- **JSON 导入导出** — 整本辞书（单词 + 单元 + 学习状态），Schema v3；计划数据独立导出
- **GitHub 云同步** — 题库数据通过 `questionbank.json`、计划数据通过 `plan.json` 同步，支持增量合并
- **冲突解决** — 以真实数据更新时间（`latestUpdatedAt`）为主信号，防止本地快照误胜

### 自适应 UI

- **响应式布局** — Material 3 Adaptive，手机 NavigationBar / 平板 NavigationRail
- **列表-详情分栏** — 平板辞书页面、学习页面自动分栏
- **双列表单** — 平板添加/编辑单词页面左右双列
- **设计令牌** — "墨与薄荷"色彩体系（InkBlue + MintTeal）

### 设置

- **多提供商** — Messages API / Completions API 兼容，支持添加多个自定义提供商
- **分域 AI** — MAIN / FAST / POOL / OCR / ARTICLE / SEARCH 六域独立配置模型
- **API Key 加密** — AndroidX Security Crypto
- **连接测试** — 各域独立一键测试

---

## 技术栈

| 层级 | 技术 |
|------|------|
| **语言** | Kotlin 2.1.0 |
| **UI** | Jetpack Compose + Material 3 + Adaptive |
| **架构** | Clean Architecture · MVVM · Use Cases |
| **依赖注入** | Hilt 2.53.1 |
| **数据库** | Room 2.6.1（v19，19 次迁移） |
| **偏好** | DataStore Preferences |
| **网络** | Retrofit 2.11.0 + OkHttp 4.12.0 + Moshi |
| **图片** | Coil |
| **HTML** | Jsoup |
| **导航** | Navigation Compose（类型安全路由） |
| **AI** | Messages API / Completions API |
| **安全** | AndroidX Security Crypto |
| **异步** | Coroutines + Flow |
| **间隔重复** | FSRS-5 |
| **NLP** | 分句/分词/词典匹配（正则预编译 + 词形变化索引） |

---

## 项目结构

```
com.xty.englishhelper/
├── data/
│   ├── debug/                  # AI 调试事件追踪
│   ├── image/                  # 图片压缩
│   ├── json/                   # JSON 导入导出
│   ├── local/                  # Room 数据库
│   │   ├── converter/          #   类型转换器
│   │   ├── dao/                #   数据访问对象（9 个 DAO）
│   │   ├── entity/             #   数据库实体（28+ 张表）
│   │   └── relation/           #   关系查询
│   ├── mapper/                 # Entity ↔ Domain 映射
│   ├── preferences/            # DataStore + 加密存储
│   ├── remote/                 # AI 客户端 + 在线文章解析
│   │   ├── dto/                #   请求/响应 DTO
│   │   ├── guardian/           #   Guardian 抓取
│   │   ├── atlantic/           #   Atlantic 抓取
│   │   ├── csmonitor/          #   CS Monitor 抓取
│   │   └── interceptor/        #   OkHttp 拦截器
│   ├── repository/             # Repository 实现（18 个）
│   ├── sync/                   # GitHub 同步与合并
│   └── tts/                    # TTS 管理
├── di/                         # Hilt 模块
├── domain/
│   ├── article/                # 分句/分词/词典匹配
│   ├── background/             # 后台任务管理
│   ├── model/                  # 领域模型（45+ 类型）
│   ├── organize/               # AI 词汇整理引擎
│   ├── pool/                   # 词池聚类引擎
│   ├── repository/             # Repository 接口
│   ├── study/                  # FSRS-5 引擎
│   └── usecase/                # 用例（按领域分包）
├── ui/
│   ├── adaptive/               # WindowSizeClass 工具
│   ├── components/reading/     # 共享阅读组件
│   ├── debug/                  # AI 调试对话框
│   ├── designsystem/           # 设计令牌 + 通用组件
│   ├── navigation/             # 路由 + 导航图
│   ├── screen/                 # 15 个功能模块
│   │   ├── addword/            #   添加/编辑单词
│   │   ├── article/            #   文章列表/编辑/阅读
│   │   ├── backgroundtask/     #   后台任务监控
│   │   ├── batchimport/        #   拍照批量导入
│   │   ├── dictionary/         #   辞书浏览
│   │   ├── guardian/            #   在线阅读
│   │   ├── home/               #   首页仪表板
│   │   ├── importexport/       #   导入导出
│   │   ├── main/               #   主框架 + 三 Tab 导航
│   │   ├── plan/               #   学习计划
│   │   ├── questionbank/       #   题库（列表/扫描/做题）
│   │   ├── settings/           #   设置 + TTS 诊断
│   │   ├── study/              #   学习模式
│   │   ├── unitdetail/         #   单元详情
│   │   └── word/               #   单词详情
│   └── theme/                  # Material 主题
└── util/                       # 工具类
```

---

## 构建

**环境要求：** Android Studio Hedgehog+, JDK 17, Android SDK 35 (compileSdk), Min API 26

```bash
./gradlew assembleDebug              # 编译 Debug APK
./gradlew testDebugUnitTest          # 单元测试
./gradlew connectedDebugAndroidTest  # 仪器测试
./gradlew lintDebug                  # Lint 检查
```

发布签名通过环境变量配置：`ANDROID_KEYSTORE_PATH`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD`。

---

## 快速上手

1. **配置 AI** — 设置 → 填入 API Key → 选择提供商和模型 → 测试连接
2. **创建辞书** — 辞书 Tab →「+」→ 输入名称
3. **添加单词** — 进入辞书 →「+」→ 手动添加或拍照批量导入 → AI 自动整理
4. **生成词池** — 辞书菜单 → 选择策略 → 生成
5. **学习复习** — 选择单元 → 开始学习（可切换头脑风暴模式）
6. **添加文章** — 文章 Tab →「+」→ 手动录入或拍照 AI 提取
7. **在线阅读** — 文章 Tab → 在线阅读入口 → 浏览 Guardian/Atlantic/CSMonitor
8. **扫描试卷** — 题库 Tab → 扫描 → 选择图片/PDF → AI 识别 → 保存
9. **做题练习** — 点击题组 → 做题 → 提交 → 查看对错/AI 评分/解析
10. **学习计划** — 计划 Tab → 创建模板 → 分配任务 → 记录每日进度

---

## 数据库版本

| 版本 | 变更 |
|:---:|------|
| 1 | 初始 schema：dictionaries, words, synonyms, similar_words, cognates |
| 2 | units, unit_word_cross_ref, word_study_state |
| 3 | normalized_spelling, word_uid + 唯一索引 |
| 4 | decomposition_json；word_associations 表 |
| 5 | FSRS-5 字段迁移（stability/difficulty/due/reps/lapses） |
| 6 | inflections_json；文章模块 7 张表 |
| 7 | normalized_token 索引 |
| 8 | model_key + 复合唯一索引 |
| 9 | word_pools, word_pool_members |
| 10 | 段落结构化存储、段落分析缓存 |
| 11 | Guardian 在线阅读、临时文章 |
| 12 | 题库模块 6 张表 |
| 13 | linkedArticleUid 列 |
| 14–19 | 题型扩展、写作模块、文章分类、适配度评分、后台任务扩展 |

## 效果展示
<img width="1080" height="2412" alt="Screenshot_2026-04-16-23-28-39-96_73853985f12a74a" src="https://github.com/user-attachments/assets/e6c87392-f28f-4785-95d0-c0650bf1edbd" /><img width="1080" height="2412" alt="Screenshot_2026-04-16-23-28-51-83_73853985f12a74a" src="https://github.com/user-attachments/assets/3fb53442-4a27-4dd5-9112-6d83750fb87b" />
<img width="1080" height="2412" alt="Screenshot_2026-04-16-23-28-44-66_73853985f12a74a" src="https://github.com/user-attachments/assets/b76ec6b5-d5fa-41a5-a2f5-53579413e9fb" />
<img width="1080" height="2412" alt="Screenshot_2026-04-16-23-28-43-07_73853985f12a74a" src="https://github.com/user-<img width="1440" height="2200" alt="Screenshot_20260408_175023_com xty englishhelper" src="https://github.com/user-attachments/assets/8ce186d1-8a22-4be1-9506-6e634babe037" />
<img width="1440" height="2200" alt="Screenshot_20260408_175019_com xty englishhelper" src="https://github.com/user-attachments/assets/15283ce6-fcd7-4117-833d-dc108badac69" />
<img width="1440" height="2200" alt="Screenshot_20260408_175008_com xty englishhelper" src="https://github.com/user-attachments/assets/1b59493b-b156-4a1c-b9b5-35398ed67abe" />
<img width="1080" height="2412" alt="Screenshot_2026-04-16-23-29-05-92_73853985f12a74a" src="https://github.com/user-attachments/assets/83bb1af5-79cd-4e3e-b50e-fcfd897dfa5e" />
attachments/assets/1ee3c052-0f4d-4595-bad8-afbec0b2d82f" />



---

## 许可证
<img width="1080" height="2412" alt="Screenshot_2026-04-16-23-28-35-84_73853985f12a74a" src="https://github.com/user-attachments/assets/16fde003-5a33-48c3-9e0c-3496fe9d1710" />
<img width="1080" height="2412" alt="Screenshot_2026-04-16-23-28-32-73_73853985f12a74a" src="https://github.com/user-attachments/assets/e5afa637-e766-49a8-85f3-db8f9a7308bb" />
<img width="1080" height="2412" alt="Screenshot_2026-04-16-23-06-14-59_bac3ad8ab1bdcd5" src="https://github.com/user-attachments/assets/fc15406e-1ac1-48b3-98e8-ec157e6b8346" />
<img width="2200" height="1440" alt="Screenshot_20260416_231242_com xty englishhelper" src="https://github.com/user-attachments/assets/fae31c7e-5d29-45cf-a659-e099c558148d" />
<img width="2200" height="1440" alt="Screenshot_20260416_230949_com xty englishhelper" src="https://github.com/user-attachments/assets/fd3f1bf2-f627-47a2-8765-b1d075d2b00a" />
<img width="2200" height="1440" alt="Screenshot_20260416_230926_com xty englishhelper" src="https://github.com/user-attachments/assets/51097471-d43a-4679-bc5f-691c29f0f2b0" />
<img width="2200" height="1440" alt="Screenshot_20260416_230900_com xty englishhelper" src="https://github.com/user-attachments/assets/4a41561f-548a-435a-a12a-73b963e427e8" />
<img width="2200" height="1440" alt="Screenshot_20260416_230846_com xty englishhelper" src="https://github.com/user-attachments/assets/88883c52-5354-46e1-bba7-8a7a6ef9f9d5" />
<img width="2200" height="1440" alt="Screenshot_20260416_230836_com xty englishhelper" src="https://github.com/user-attachments/assets/82ff1303-8445-40e1-b3f4-c89068d928c8" />
<img width="2200" height="1440" alt="Screenshot_20260416_230813_com xty englishhelper" src="https://github.com/user-attachments/assets/2cc1e82a-d181-40f5-81a9-f2968f9ad388" />

[AGPL-3.0](LICENSE)
