# EnglishHelper 系统性维护升级 — 变更总结

## 概述

本次升级覆盖五个方面：架构重构、数据库 schema 升级、导入导出 v2、安全整改、自动化测试。
同时修复了实施过程中发现的环境兼容性问题和代码缺陷。

---

## Phase 1: Mapper 迁移

**目标**：消除 `data/repository/` 对 `ui/mapper/` 的反向依赖。

| 操作 | 文件 |
|------|------|
| 新建（从 ui/mapper/ 迁移） | `data/mapper/WordMapper.kt` |
| 新建（从 ui/mapper/ 迁移） | `data/mapper/DictionaryMapper.kt` |
| 新建（从 ui/mapper/ 迁移） | `data/mapper/UnitMapper.kt` |
| 删除 | `ui/mapper/WordMapper.kt`、`DictionaryMapper.kt`、`UnitMapper.kt` |
| 修改 import | `WordRepositoryImpl`、`DictionaryRepositoryImpl`、`UnitRepositoryImpl`、`StudyRepositoryImpl` |

包名统一改为 `com.xty.englishhelper.data.mapper`，`ui/mapper/` 目录已删除。

---

## Phase 2: DB Schema v3 + SaveWord Upsert

**目标**：为 words 表增加唯一约束，防止同辞书重复拼写；为每个词条分配稳定的 UUID。

### 2.1 WordEntity 新增字段

```
normalized_spelling  TEXT NOT NULL DEFAULT ''
word_uid             TEXT NOT NULL DEFAULT ''
```

新增唯一联合索引：`UNIQUE(dictionary_id, normalized_spelling)`

### 2.2 MIGRATION_2_3

在 `AppDatabase.kt` 中实现，执行流程：

1. ALTER TABLE 添加两列
2. 遍历所有已有 words，用 `trim().lowercase()` 计算 `normalized_spelling`，用 `UUID.randomUUID()` 生成 `word_uid`
3. 按 `(dictionary_id, normalized_spelling)` 分组去重，保留 `id` 最小的行
4. 创建唯一索引

### 2.3 WordDetails 领域模型

新增 `normalizedSpelling: String` 和 `wordUid: String` 字段。

### 2.4 WordMapper 适配

`toDomain()` 和 `toEntity()` 均映射新字段。

### 2.5 WordDao 新增查询

```kotlin
suspend fun findByNormalizedSpelling(dictionaryId: Long, normalizedSpelling: String): WordWithDetails?
```

### 2.6 WordRepository 新增方法

接口和实现均增加 `findByNormalizedSpelling()`。

### 2.7 SaveWordUseCase 改为 Upsert

三种路径：

| 条件 | 行为 |
|------|------|
| `id == 0` 且 DB 无同拼写 | 生成 UUID，insertWord |
| `id == 0` 且 DB 有同拼写 | 取已有 id/wordUid，updateWord（upsert） |
| `id != 0`（编辑模式） | 查 DB 恢复 wordUid 和 createdAt，updateWord |

编辑模式下 `wordUid` 和 `createdAt` 无条件取 DB 旧值，防止 ViewModel 未传递时被默认值覆盖。

---

## Phase 3: 导入导出 v2

**目标**：用 `wordUid` 替代拼写做关联键，增加 schema 版本标识和校验，事务性导入。

### 3.1 JSON 模型 (`DictionaryJsonModel.kt`)

| v1 | v2 |
|----|----|
| `version` 字段 | `schemaVersion: Int = 2` |
| 无 wordUid | `WordJsonModel.wordUid` |
| `UnitJsonModel.wordSpellings` | `UnitJsonModel.wordUids` |
| `StudyStateJsonModel.spelling` | `StudyStateJsonModel.wordUid` |

### 3.2 JsonImportExporter 重写

**导出**：输出 `schemaVersion: 2`，用 `wordUid` 关联 units 和 studyStates。

**导入校验**（按顺序）：

1. `schemaVersion` 必须为 2
2. 所有 `spelling` 非空
3. 所有 `normalizedSpelling` 不重复
4. 所有非空 `wordUid` 不重复

### 3.3 Domain 层接口抽象

新建 `domain/repository/DictionaryImportExporter.kt` 接口，定义 `ImportResult`（含 `ImportedUnit`、`ImportedStudyState`）。
`JsonImportExporter` 实现此接口，domain usecase 仅依赖接口，不反向依赖 data 层。

### 3.4 TransactionRunner

```
domain/repository/TransactionRunner.kt          — 接口
data/repository/RoomTransactionRunner.kt         — Room withTransaction 实现
```

### 3.5 ImportDictionaryUseCase

在事务中执行：解析 JSON → 插入字典 → 逐词插入（空 wordUid 自动生成）→ 按 wordUid 关联 units → 按 wordUid 关联 studyStates → 更新 wordCount。失败时整个事务回滚。

### 3.6 ExportDictionaryUseCase

收集数据 → 构建 `wordId→wordUid` 和 `unitId→wordUids` 映射 → 调用 `exportToJson()`。

### 3.7 ImportExportViewModel 重构

移除直接注入的四个 Repository，改为注入 `ImportDictionaryUseCase` 和 `ExportDictionaryUseCase`。

### 3.8 RepositoryModule 绑定

新增 `bindTransactionRunner` 和 `bindDictionaryImportExporter`。

---

## Phase 4: 安全整改

### 4.1 EncryptedApiKeyStore

使用 `androidx.security:security-crypto` 的 `EncryptedSharedPreferences`，密钥方案 AES256-GCM。
使用 `MasterKey.Builder`（非已废弃的 `MasterKeys`）。

### 4.2 SettingsDataStore 改造

`apiKey` Flow 和 `setApiKey()` 均委托给 `EncryptedApiKeyStore`。
保留 `migrateApiKeyIfNeeded()` 方法供一次性迁移。

### 4.3 API Key 迁移

`EnglishHelperApp.onCreate()` 中：检查 DataStore 有无旧明文 key → 写入加密存储 → 清除旧值 → 用 SharedPreferences flag 标记已迁移。

### 4.4 网络日志安全化

| 条件 | 行为 |
|------|------|
| Release | 不添加 HttpLoggingInterceptor |
| Debug | 级别 HEADERS（不记录 body），自定义 Logger 将 `x-api-key` 替换为 `[REDACTED]` |

需要 `buildConfig = true`（已在 `build.gradle.kts` 中启用）。

---

## Phase 5: 测试

### 新增依赖

`room-testing`、`mockk`、`androidx.test.ext:junit`、`androidx.test:runner`

### 测试文件

| 文件 | 类型 | 覆盖内容 |
|------|------|---------|
| `androidTest/.../MigrationTest.kt` | Instrumented | MIGRATION_2_3 正确性：normalized_spelling 计算、UUID 生成、重复清理、唯一约束 |
| `test/.../SaveWordUseCaseTest.kt` | Unit | 新增/upsert/编辑三条路径、normalizedSpelling 计算、UUID 生成、编辑模式从 DB 恢复 wordUid 和 createdAt |
| `test/.../JsonImportExporterTest.kt` | Unit | 导出-导入 round-trip 一致性、schemaVersion 校验、重复拼写拒绝、空 spelling 拒绝、重复 wordUid 拒绝 |
| `androidTest/.../StudyStateConstraintTest.kt` | Instrumented | 删除 word 级联删除 studyState、upsert studyState 覆盖旧值 |

---

## 环境修复

### GraalVM jlink 不兼容 AGP

**现象**：`compileDebugJavaWithJavac` 失败，`JdkImageTransform` 报 `Invalid JMOD file`。

**根因**：`JAVA_HOME` 指向 GraalVM JDK 17，其 `jlink` 严格校验 JMOD magic number，无法处理 AGP 将 `core-for-system-modules.jar` 重命名为 `.jmod` 的场景。

**修复**：`gradle.properties` 新增 `org.gradle.java.home` 指向 Eclipse Temurin JDK 17。

### Hilt 版本不兼容 Kotlin 2.1.0

**现象**：`hiltJavaCompileDebug` 报 `Unable to read Kotlin metadata due to unsupported metadata version`。

**根因**：Hilt 2.51.1 的 kapt 注解处理器不支持 Kotlin 2.1.0 生成的 metadata。

**修复**：`libs.versions.toml` 中 Hilt 版本从 `2.51.1` 升级到 `2.53.1`。

---

## 文件变更汇总

### 新建文件（11 个）

| 文件 | Phase |
|------|-------|
| `data/mapper/WordMapper.kt` | 1 |
| `data/mapper/DictionaryMapper.kt` | 1 |
| `data/mapper/UnitMapper.kt` | 1 |
| `domain/repository/TransactionRunner.kt` | 3 |
| `domain/repository/DictionaryImportExporter.kt` | 3 |
| `data/repository/RoomTransactionRunner.kt` | 3 |
| `domain/usecase/importexport/ImportExportUseCases.kt` | 3 |
| `data/preferences/EncryptedApiKeyStore.kt` | 4 |
| `androidTest/.../MigrationTest.kt` | 5 |
| `androidTest/.../StudyStateConstraintTest.kt` | 5 |
| `test/.../SaveWordUseCaseTest.kt` | 5 |
| `test/.../JsonImportExporterTest.kt` | 5 |

### 修改文件（19 个）

| 文件 | Phase | 改动摘要 |
|------|-------|---------|
| `data/local/entity/WordEntity.kt` | 2 | +normalizedSpelling, +wordUid, +unique index |
| `data/local/AppDatabase.kt` | 2 | version→3, +MIGRATION_2_3 |
| `data/local/dao/WordDao.kt` | 2 | +findByNormalizedSpelling |
| `domain/model/WordDetails.kt` | 2 | +normalizedSpelling, +wordUid |
| `domain/repository/WordRepository.kt` | 2 | +findByNormalizedSpelling |
| `data/repository/WordRepositoryImpl.kt` | 1,2 | 修 import + 实现 findByNormalizedSpelling |
| `data/repository/DictionaryRepositoryImpl.kt` | 1 | 修 mapper import |
| `data/repository/UnitRepositoryImpl.kt` | 1 | 修 mapper import |
| `data/repository/StudyRepositoryImpl.kt` | 1 | 修 mapper import |
| `domain/usecase/word/WordUseCases.kt` | 2 | SaveWordUseCase upsert + 编辑模式恢复 wordUid/createdAt |
| `data/json/DictionaryJsonModel.kt` | 3 | v2 schema 全部 model |
| `data/json/JsonImportExporter.kt` | 3 | 实现 DictionaryImportExporter 接口 + v2 导入导出 + 校验 |
| `ui/screen/importexport/ImportExportViewModel.kt` | 3 | 改用 UseCase |
| `di/RepositoryModule.kt` | 3 | +TransactionRunner, +DictionaryImportExporter 绑定 |
| `data/preferences/SettingsDataStore.kt` | 4 | API Key 委托加密存储 + 迁移方法 |
| `di/NetworkModule.kt` | 4 | Debug-only HEADERS 级别日志 + x-api-key 脱敏 |
| `EnglishHelperApp.kt` | 4 | API Key 一次性迁移 |
| `di/AppModule.kt` | 2 | 注册 MIGRATION_2_3 |
| `app/build.gradle.kts` | 4,5 | +buildConfig, +security-crypto, +test deps |
| `gradle/libs.versions.toml` | 4,5 | +securityCrypto, +mockk, +room-testing, hilt→2.53.1 |
| `gradle.properties` | env | +org.gradle.java.home 指向 Temurin JDK |

### 删除文件（3 个）

| 文件 | Phase |
|------|-------|
| `ui/mapper/WordMapper.kt` | 1 |
| `ui/mapper/DictionaryMapper.kt` | 1 |
| `ui/mapper/UnitMapper.kt` | 1 |

---

## 构建验证

```
./gradlew :app:assembleDebug    BUILD SUCCESSFUL
```

全链路通过：`compileDebugKotlin` → `compileDebugJavaWithJavac` → `hiltJavaCompileDebug` → `packageDebug`。
