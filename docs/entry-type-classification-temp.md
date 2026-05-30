# 临时功能：AI 词条分类 (entry_type)

> **状态**: 临时功能，仅存在于当前版本
> **创建日期**: 2026-05-30
> **目的**: 为现有辞书中未分类的词条自动标注 entry_type (word/root/phrase)
> **触发方式**: 首页顶栏 AutoAwesome 图标按钮

## 功能说明

调用 POOL AI 模型，每批 50 个词条，判断每个词条属于：
- **word**: 普通英语单词（有独立词义、词性、义项）
- **root**: 拉丁/希腊词根（不能独立使用，但派生出一串同族词）
- **phrase**: 多词表达 / 短语动词（语义 ≠ 组成词之和）

## 移除清单

以下文件/行号包含本临时功能的代码，移除时需全部清理：

### 1. DB 层

| 文件 | 行号(约) | 内容 |
|------|---------|------|
| `data/local/entity/WordEntity.kt` | 末尾 | `entryType` 字段 (`@ColumnInfo(name = "entry_type")`) |
| `data/local/AppDatabase.kt` | 版本号 | `version = 27` → 改为删除此 migration 后的版本 |
| `data/local/AppDatabase.kt` | `MIGRATION_26_27` 块 | 整个 migration 对象 |
| `data/local/dao/WordDao.kt` | 末尾 4 个方法 | `getWordsWithoutEntryType`, `updateEntryType`, `countWordsWithoutEntryType`, `countByEntryType` |
| `data/local/dao/WordDao.kt` | 末尾 data class | `EntryTypeClassificationInput` |
| `di/AppModule.kt` | migration 列表 | `AppDatabase.MIGRATION_26_27` |

### 2. Repository 层

| 文件 | 行号(约) | 内容 |
|------|---------|------|
| `domain/repository/WordPoolRepository.kt` | 末尾 | `classifyEntryTypes` 方法声明 |
| `data/repository/WordPoolRepositoryImpl.kt` | 末尾整个 section | `classifyEntryTypes` 实现 + `buildEntryTypePrompt` + `parseEntryTypeResponse` |
| `data/repository/WordPoolRepositoryImpl.kt` | companion object | `ENTRY_TYPE_PATTERN` 正则 |

### 3. UI 层

| 文件 | 行号(约) | 内容 |
|------|---------|------|
| `ui/screen/home/HomeUiState.kt` | HomeUiState | `isClassifying`, `classificationProgress` 字段 |
| `ui/screen/home/HomeViewModel.kt` | import | `WordPoolRepository` import |
| `ui/screen/home/HomeViewModel.kt` | 构造函数 | `wordPoolRepository: WordPoolRepository` 参数 |
| `ui/screen/home/HomeViewModel.kt` | 末尾 | `startEntryTypeClassification()` 函数 |
| `ui/screen/home/HomeScreen.kt` | import | `Icons.Default.AutoAwesome` import |
| `ui/screen/home/HomeScreen.kt` | TopAppBar actions | AutoAwesome IconButton |
| `ui/screen/home/HomeScreen.kt` | Scaffold 内 | `LaunchedEffect(state.classificationProgress)` snackbar |

### 4. 文档

| 文件 | 内容 |
|------|------|
| `docs/entry-type-classification-temp.md` | 本文件 |

## 移除步骤

1. 删除 `docs/entry-type-classification-temp.md`
2. 从 `WordEntity.kt` 删除 `entryType` 字段
3. 从 `WordDao.kt` 删除 4 个 entry_type 方法 + `EntryTypeClassificationInput`
4. 从 `AppDatabase.kt` 删除 `MIGRATION_26_27` 块，版本号回退到 26
5. 从 `AppModule.kt` 删除 `MIGRATION_26_27` 注册
6. 从 `WordPoolRepository.kt` 删除 `classifyEntryTypes` 声明
7. 从 `WordPoolRepositoryImpl.kt` 删除整个 TEMPORARY section + `ENTRY_TYPE_PATTERN`
8. 从 `HomeUiState.kt` 删除 `isClassifying` 和 `classificationProgress`
9. 从 `HomeViewModel.kt` 删除 `WordPoolRepository` import/参数 + `startEntryTypeClassification()`
10. 从 `HomeScreen.kt` 删除 AutoAwesome 相关 import/IconButton/LaunchedEffect
11. 编译验证
