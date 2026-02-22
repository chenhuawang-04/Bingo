# FSRS-5 间隔复习引擎重构计划

## Context

将 EnglishHelper 的固定间隔复习系统（艾宾浩斯 10 级）升级为 FSRS-5（Free Spaced Repetition Scheduler）驱动的自适应间隔复习引擎。FSRS 基于 DSR 三分量记忆模型（难度 D、稳定性 S、可提取性 R），通过幂律遗忘曲线预测回忆概率，并根据目标保留率自动计算最优复习间隔。

**核心升级**：
- 从二元反馈（认识/不认识）→ 四级评分（Again/Hard/Good/Easy）
- 从固定间隔表 → 基于遗忘曲线的自适应间隔
- 从"剩余复习次数"有限制 → 无限循环的长期记忆维护
- 按钮上预览下次复习间隔

DB 版本 4→5，导入导出 schemaVersion 3→4。

---

## 已完成的探索

### 当前系统分析

**现有数据模型 `WordStudyStateEntity`**（`data/local/entity/WordStudyStateEntity.kt`）：
```kotlin
wordId: Long (PK)
remainingReviews: Int       // 倒计到 0 视为"掌握"
easeLevel: Int              // 0-9，索引 EbbinghausIntervals 表
nextReviewAt: Long          // 时间戳
lastReviewedAt: Long        // 时间戳
```

**现有 Use Cases**（`domain/usecase/study/StudyUseCases.kt`）：
- `MarkKnownUseCase`: easeLevel++, remainingReviews--, nextReviewAt = now + interval
- `MarkUnknownUseCase`: easeLevel-- (min 0), nextReviewAt = 0 (立刻)
- `InitStudyStateUseCase`: 创建初始状态，remainingReviews = unit.defaultRepeatCount
- `GetDueWordsUseCase`: 查询 remaining_reviews > 0 AND next_review_at <= now
- `GetNewWordsUseCase`: 查询无 study state 的词

**现有 DAO 查询**（`data/local/dao/StudyDao.kt`）：
- `getDueWords`: JOIN unit_word_cross_ref + word_study_state, WHERE remaining_reviews > 0 AND next_review_at <= now
- `getNewWords`: LEFT JOIN word_study_state WHERE word_id IS NULL
- `countDueWords`, `countNewWords`: 计数版本

**现有 UI**（`ui/screen/study/`）：
- `StudyScreen`: 问题模式显示单词，"不认识"/"认识" 二选一。不认识→翻转答案→"下一个"处理为 unknown 并重新入队
- `StudySetupScreen`: 选择单元，显示到期/新词数量
- `StudyViewModel`: ArrayDeque 队列管理，session 内 known/unknown/mastered 统计

**现有 Mapper**（`data/mapper/UnitMapper.kt`）：
- `WordStudyStateEntity.toDomain()` / `WordStudyState.toEntity()` 简单字段映射

**现有导入导出**（`data/json/`）：
- `StudyStateJsonModel`: wordUid, remainingReviews, easeLevel, nextReviewAt, lastReviewedAt
- schemaVersion = 3

**现有工具**（`util/EbbinghausIntervals.kt`）：
- 10 级固定间隔：0, 5min, 30min, 12h, 1d, 2d, 4d, 7d, 15d, 30d

---

### FSRS-5 算法研究

#### 三分量模型
| 变量 | 符号 | 范围 | 含义 |
|------|------|------|------|
| 可提取性 | R | [0, 1] | 当前能回忆起的概率 |
| 稳定性 | S | [0.001, +∞) | R 从 100% 衰减到 90% 所需天数 |
| 难度 | D | [1, 10] | 增加记忆稳定性的难度 |

#### 评分系统
| 评分 | 值 | 含义 |
|------|---|------|
| Again | 1 | 完全忘记 |
| Hard | 2 | 回忆困难 |
| Good | 3 | 正常回忆 |
| Easy | 4 | 轻松回忆 |

#### 19 个默认参数 (w0-w18)
```
w0=0.40255, w1=1.18385, w2=3.173, w3=15.69105,   // 初始稳定性 S0(Again/Hard/Good/Easy)
w4=7.1949, w5=0.5345,                              // 初始难度
w6=1.4604, w7=0.0046,                              // 难度更新
w8=1.54575, w9=0.1192, w10=1.01925,                // 回忆后稳定性
w11=1.9395, w12=0.11, w13=0.29605, w14=2.2698,     // 遗忘后稳定性
w15=0.2315, w16=2.9898,                            // Hard 惩罚 / Easy 奖励
w17=0.51655, w18=0.6621                            // 同日复习
```

#### 核心公式

**幂律遗忘曲线**：
```
DECAY = -0.5
FACTOR = 19.0 / 81.0

R(t, S) = (1 + FACTOR * t / S) ^ DECAY
```

**间隔计算**（从稳定性和目标保留率）：
```
I(S, r) = (S / FACTOR) * (r^(1/DECAY) - 1)
// 当 r = 0.9 时，I = S（定义如此）
```

**初始稳定性**：`S0(G) = w[G-1]`

**初始难度**：`D0(G) = w4 - e^(w5 * (G-1)) + 1`，clamp [1, 10]

**回忆后稳定性更新**（Hard/Good/Easy）：
```
S'r = S * (1 + e^w8 * (11-D) * S^(-w9) * (e^(w10*(1-R)) - 1) * hard_penalty * easy_bonus)
// hard_penalty = w15 if G=Hard, else 1.0
// easy_bonus = w16 if G=Easy, else 1.0
```

**遗忘后稳定性更新**（Again）：
```
S'f = w11 * D^(-w12) * ((S+1)^w13 - 1) * e^(w14 * (1-R))
```

**难度更新**：
```
delta_D = -w6 * (G - 3)
D' = D + delta_D * (10 - D) / 9          // 线性阻尼
D'' = w7 * D0(4) + (1 - w7) * D'          // 均值回归
D_final = clamp(D'', 1, 10)
```

**同日复习**：
```
SInc = e^(w17 * (G - 3 + w18))
S' = S * SInc
```

#### 卡片状态
| 状态 | 值 | 说明 |
|------|---|------|
| Learning | 1 | 新卡首次学习，步骤调度 |
| Review | 2 | 已毕业，FSRS 日级调度 |
| Relearning | 3 | 遗忘后重新学习 |

**Learning/Relearning 步骤**：
- learning_steps = [1min, 10min]
- relearning_steps = [10min]
- Again → 回到 step 0
- Good at last step → 毕业到 Review
- Easy → 立刻毕业

#### Fuzz（仅 Review 状态，间隔 >= 2.5 天）
| 间隔范围 | 模糊系数 |
|---------|---------|
| 2.5-7 天 | 15% |
| 7-20 天 | 10% |
| 20+ 天 | 5% |

---

## Phase 1: FSRS 引擎（纯 Kotlin）

### 1.1 新建 `FsrsEngine`

**新文件**: `domain/study/FsrsEngine.kt`

纯 Kotlin 实现，无 Android 依赖。包含：

```kotlin
object FsrsConstants {
    const val DECAY = -0.5
    const val FACTOR = 19.0 / 81.0
    const val STABILITY_MIN = 0.001
    val DEFAULT_PARAMS = doubleArrayOf(
        0.40255, 1.18385, 3.173, 15.69105,
        7.1949, 0.5345, 1.4604, 0.0046,
        1.54575, 0.1192, 1.01925,
        1.9395, 0.11, 0.29605, 2.2698,
        0.2315, 2.9898,
        0.51655, 0.6621
    )
    val DEFAULT_LEARNING_STEPS = listOf(1L, 10L)        // 分钟
    val DEFAULT_RELEARNING_STEPS = listOf(10L)           // 分钟
    const val DEFAULT_DESIRED_RETENTION = 0.9
    const val MAX_INTERVAL = 36500                       // 天
}

enum class Rating(val value: Int) { Again(1), Hard(2), Good(3), Easy(4) }
enum class CardState(val value: Int) { Learning(1), Review(2), Relearning(3) }

data class SchedulingResult(
    val state: CardState,
    val step: Int?,
    val stability: Double,
    val difficulty: Double,
    val due: Long,              // 时间戳 (millis)
    val lastReviewAt: Long,
    val reps: Int,
    val lapses: Int,
    val scheduledInterval: Long  // 预览用：下次间隔 (millis)
)

class FsrsEngine(
    private val params: DoubleArray = FsrsConstants.DEFAULT_PARAMS,
    private val desiredRetention: Double = FsrsConstants.DEFAULT_DESIRED_RETENTION,
    private val learningSteps: List<Long> = FsrsConstants.DEFAULT_LEARNING_STEPS,
    private val relearningSteps: List<Long> = FsrsConstants.DEFAULT_RELEARNING_STEPS,
    private val maxInterval: Int = FsrsConstants.MAX_INTERVAL,
    private val enableFuzz: Boolean = true
)
```

**核心方法**：

```kotlin
// 首次评分（新卡）
fun reviewNew(rating: Rating, now: Long): SchedulingResult

// 后续评分（已有状态）
fun review(
    state: CardState, step: Int?, stability: Double, difficulty: Double,
    lastReviewAt: Long, reps: Int, lapses: Int,
    rating: Rating, now: Long
): SchedulingResult

// 预览所有评分的间隔（UI 按钮显示用）
fun previewIntervals(
    state: CardState, step: Int?, stability: Double, difficulty: Double,
    lastReviewAt: Long, reps: Int, lapses: Int, now: Long
): Map<Rating, Long>  // Rating → interval in millis
```

**内部方法**：
- `retrievability(elapsedDays: Double, stability: Double): Double`
- `initialStability(rating: Rating): Double`
- `initialDifficulty(rating: Rating): Double`
- `nextStabilityAfterRecall(d: Double, s: Double, r: Double, rating: Rating): Double`
- `nextStabilityAfterForget(d: Double, s: Double, r: Double): Double`
- `nextDifficulty(d: Double, rating: Rating): Double`
- `nextInterval(stability: Double): Long` // 天数
- `applyFuzz(interval: Long): Long` // 天数
- `shortTermStability(s: Double, rating: Rating): Double` // 同日复习

---

## Phase 2: 数据模型

### 2.1 修改 `WordStudyState`（Domain）

**文件**: `domain/model/WordStudyState.kt`

替换为：
```kotlin
data class WordStudyState(
    val wordId: Long,
    val state: Int = 2,            // CardState.Review.value
    val step: Int? = null,
    val stability: Double = 0.0,
    val difficulty: Double = 0.0,
    val due: Long = 0,
    val lastReviewAt: Long = 0,
    val reps: Int = 0,
    val lapses: Int = 0
)
```

### 2.2 修改 `WordStudyStateEntity`

**文件**: `data/local/entity/WordStudyStateEntity.kt`

替换为：
```kotlin
@Entity(tableName = "word_study_state", foreignKeys = [...])
data class WordStudyStateEntity(
    @PrimaryKey @ColumnInfo(name = "word_id") val wordId: Long,
    @ColumnInfo(name = "state", defaultValue = "2") val state: Int = 2,
    @ColumnInfo(name = "step") val step: Int? = null,
    @ColumnInfo(name = "stability", defaultValue = "0.0") val stability: Double = 0.0,
    @ColumnInfo(name = "difficulty", defaultValue = "0.0") val difficulty: Double = 0.0,
    @ColumnInfo(name = "due", defaultValue = "0") val due: Long = 0,
    @ColumnInfo(name = "last_review_at", defaultValue = "0") val lastReviewAt: Long = 0,
    @ColumnInfo(name = "reps", defaultValue = "0") val reps: Int = 0,
    @ColumnInfo(name = "lapses", defaultValue = "0") val lapses: Int = 0
)
```

### 2.3 DB Migration 4→5

**文件**: `data/local/AppDatabase.kt`

```sql
-- 删除旧表（无向后兼容要求，但尽量保留进度）
-- 方案：创建新表，迁移数据，删旧表，重命名

CREATE TABLE word_study_state_new (
    word_id INTEGER NOT NULL PRIMARY KEY,
    state INTEGER NOT NULL DEFAULT 2,
    step INTEGER,
    stability REAL NOT NULL DEFAULT 0.0,
    difficulty REAL NOT NULL DEFAULT 0.0,
    due INTEGER NOT NULL DEFAULT 0,
    last_review_at INTEGER NOT NULL DEFAULT 0,
    reps INTEGER NOT NULL DEFAULT 0,
    lapses INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY(word_id) REFERENCES words(id) ON DELETE CASCADE
);

-- 迁移：将旧 easeLevel 映射为近似 stability
-- easeLevel 0→0.4, 1→0.4, 2→0.5, 3→1.2, 4→3.2, 5→4, 6→7, 7→15, 8→30, 9→60
INSERT INTO word_study_state_new (word_id, state, stability, difficulty, due, last_review_at, reps)
SELECT word_id, 2,
    CASE ease_level
        WHEN 0 THEN 0.4 WHEN 1 THEN 0.4 WHEN 2 THEN 0.5
        WHEN 3 THEN 1.2 WHEN 4 THEN 3.2 WHEN 5 THEN 4.0
        WHEN 6 THEN 7.0 WHEN 7 THEN 15.0 WHEN 8 THEN 30.0
        ELSE 60.0
    END,
    5.0,
    next_review_at,
    last_reviewed_at,
    ease_level
FROM word_study_state;

DROP TABLE word_study_state;
ALTER TABLE word_study_state_new RENAME TO word_study_state;
```

### 2.4 修改 AppDatabase

- version → 5
- entities 列表中 `WordStudyStateEntity` 已有
- 新增 `MIGRATION_4_5`

### 2.5 修改 AppModule

注册 `MIGRATION_4_5`。

---

## Phase 3: Mapper + DAO + Repository

### 3.1 修改 `UnitMapper`

**文件**: `data/mapper/UnitMapper.kt`

`WordStudyStateEntity.toDomain()` / `WordStudyState.toEntity()` 改为映射新字段。

### 3.2 修改 `StudyDao`

**文件**: `data/local/dao/StudyDao.kt`

查询变更：
```kotlin
// 到期词：有 study state 且 due <= now
@Query("""
    SELECT DISTINCT w.* FROM words w
    INNER JOIN unit_word_cross_ref ref ON w.id = ref.word_id
    INNER JOIN word_study_state s ON w.id = s.word_id
    WHERE ref.unit_id IN (:unitIds)
      AND s.due <= :now
    ORDER BY s.due ASC
""")
suspend fun getDueWords(unitIds: List<Long>, now: Long): List<WordWithDetails>

// 计数也去掉 remaining_reviews > 0
@Query("""
    SELECT COUNT(DISTINCT w.id) FROM words w
    INNER JOIN unit_word_cross_ref ref ON w.id = ref.word_id
    INNER JOIN word_study_state s ON w.id = s.word_id
    WHERE ref.unit_id = :unitId
      AND s.due <= :now
""")
suspend fun countDueWords(unitId: Long, now: Long): Int
```

新词查询不变（LEFT JOIN, word_id IS NULL）。

### 3.3 修改 StudyRepository 接口

**文件**: `domain/repository/StudyRepository.kt`

不变（接口方法签名兼容）。

### 3.4 修改 StudyRepositoryImpl

**文件**: `data/repository/StudyRepositoryImpl.kt`

映射函数自动适配新字段，无需额外修改。

---

## Phase 4: UseCase 重写

### 4.1 重写 `StudyUseCases.kt`

**文件**: `domain/usecase/study/StudyUseCases.kt`

**删除**：`MarkKnownUseCase`, `MarkUnknownUseCase`, `InitStudyStateUseCase`

**新增**：

```kotlin
class ReviewWordUseCase @Inject constructor(
    private val repository: StudyRepository
) {
    private val engine = FsrsEngine()

    suspend operator fun invoke(wordId: Long, rating: Rating): SchedulingResult {
        val existing = repository.getStudyState(wordId)
        val now = System.currentTimeMillis()

        val result = if (existing == null) {
            engine.reviewNew(rating, now)
        } else {
            engine.review(
                state = CardState.entries.first { it.value == existing.state },
                step = existing.step,
                stability = existing.stability,
                difficulty = existing.difficulty,
                lastReviewAt = existing.lastReviewAt,
                reps = existing.reps,
                lapses = existing.lapses,
                rating = rating,
                now = now
            )
        }

        repository.upsertStudyState(WordStudyState(
            wordId = wordId,
            state = result.state.value,
            step = result.step,
            stability = result.stability,
            difficulty = result.difficulty,
            due = result.due,
            lastReviewAt = result.lastReviewAt,
            reps = result.reps,
            lapses = result.lapses
        ))

        return result
    }
}

class PreviewIntervalsUseCase @Inject constructor(
    private val repository: StudyRepository
) {
    private val engine = FsrsEngine()

    suspend operator fun invoke(wordId: Long): Map<Rating, Long> {
        val existing = repository.getStudyState(wordId)
        val now = System.currentTimeMillis()

        return if (existing == null) {
            Rating.entries.associateWith { rating ->
                engine.reviewNew(rating, now).scheduledInterval
            }
        } else {
            engine.previewIntervals(
                state = CardState.entries.first { it.value == existing.state },
                step = existing.step,
                stability = existing.stability,
                difficulty = existing.difficulty,
                lastReviewAt = existing.lastReviewAt,
                reps = existing.reps,
                lapses = existing.lapses,
                now = now
            )
        }
    }
}
```

保留 `GetStudyStateUseCase`, `GetDueWordsUseCase`, `GetNewWordsUseCase`, `CountDueWordsUseCase`, `CountNewWordsUseCase`。

### 4.2 删除 `EbbinghausIntervals.kt`

**文件**: `util/EbbinghausIntervals.kt` → 删除

---

## Phase 5: UI 重构

### 5.1 修改 `StudyUiState`

**文件**: `ui/screen/study/StudyUiState.kt`

```kotlin
data class StudyUiState(
    val phase: StudyPhase = StudyPhase.Loading,
    val currentWord: WordDetails? = null,
    val showAnswer: Boolean = false,
    val progress: Int = 0,
    val total: Int = 0,
    val previewIntervals: Map<Rating, Long> = emptyMap(),  // 新增
    val stats: StudyStats = StudyStats(),
    val error: String? = null
)

data class StudyStats(
    val totalWords: Int = 0,
    val againCount: Int = 0,     // 替代 unknownCount
    val hardCount: Int = 0,      // 新增
    val goodCount: Int = 0,      // 新增
    val easyCount: Int = 0       // 替代 knownCount（原 masteredCount 删除）
)
```

### 5.2 修改 `StudyViewModel`

**文件**: `ui/screen/study/StudyViewModel.kt`

核心变更：
- 注入 `ReviewWordUseCase` + `PreviewIntervalsUseCase`（替代 MarkKnown/MarkUnknown/InitStudyState）
- 删除 `wordRepeatCounts` 和 `masteredCount`
- `loadSession()`: 不再 init study state，新词直接入队
- `onRevealAnswer()`: 替代 `onUnknown()`，翻转答案后加载 `previewIntervals`
- `onRate(rating: Rating)`: 替代 `onKnown()`/`onNext()`
  - 调用 `reviewWord(wordId, rating)`
  - 如果 rating == Again → re-queue
  - 统计 rating 分布
  - showNextWord

### 5.3 修改 `StudyScreen`

**文件**: `ui/screen/study/StudyScreen.kt`

**问题模式**（showAnswer = false）：
```
    ┌──────────────────────────┐
    │       spelling           │
    │       /phonetic/         │
    │                          │
    │   [ 显示答案 ]            │  ← 单个按钮
    └──────────────────────────┘
```

**答案模式**（showAnswer = true）：
```
    ┌──────────────────────────┐
    │  spelling + 完整词义...   │
    │                          │
    │  [重来]  [困难] [良好] [简单] │  ← 四个按钮
    │  <1分    10分   1天    4天   │  ← 预览间隔
    └──────────────────────────┘
```

四个按钮的颜色：
- Again: error (红)
- Hard: tertiary (橙/棕)
- Good: primary (主色)
- Easy: secondary (蓝/绿)

每个按钮下方显示格式化的间隔预览：
- < 1 小时 → "X分"
- < 1 天 → "X时"
- < 30 天 → "X天"
- >= 30 天 → "X月"

**完成界面** — 替代原来的 known/unknown/mastered：
```
    学习完成
    总计单词  25
    重来      3
    困难      5
    良好      15
    简单      2
```

### 5.4 StudySetupScreen

基本不变，只是删除 defaultRepeatCount 在 UI 上的依赖（如果有的话）。当前 Setup 只显示 dueCount/newCount，逻辑不变。

---

## Phase 6: 导入导出

### 6.1 修改 `StudyStateJsonModel`

**文件**: `data/json/DictionaryJsonModel.kt`

```kotlin
@JsonClass(generateAdapter = true)
data class StudyStateJsonModel(
    val wordUid: String = "",
    val state: Int = 2,
    val step: Int? = null,
    val stability: Double = 0.0,
    val difficulty: Double = 0.0,
    val due: Long = 0,
    val lastReviewAt: Long = 0,
    val reps: Int = 0,
    val lapses: Int = 0
)
```

### 6.2 修改 `JsonImportExporter`

**文件**: `data/json/JsonImportExporter.kt`

- 导出：schemaVersion = 4
- 导入：只接受 schemaVersion == 4（不兼容旧版）
- 映射新的 study state 字段

### 6.3 修改 `ImportExportUseCases`

**文件**: `domain/usecase/importexport/ImportExportUseCases.kt`

study state 导入映射到新字段。

### 6.4 修改测试

**文件**: `app/src/test/java/com/xty/englishhelper/data/json/JsonImportExporterTest.kt`

所有 schemaVersion 断言 3→4。

---

## 文件变更汇总

### 新建文件（1 个）
| 文件 | Phase |
|------|-------|
| `domain/study/FsrsEngine.kt` | 1 |

### 删除文件（1 个）
| 文件 | Phase |
|------|-------|
| `util/EbbinghausIntervals.kt` | 4 |

### 修改文件（~15 个）
| 文件 | Phase | 改动摘要 |
|------|-------|---------|
| `domain/model/WordStudyState.kt` | 2 | 全部字段替换为 FSRS |
| `data/local/entity/WordStudyStateEntity.kt` | 2 | 全部列替换为 FSRS |
| `data/local/AppDatabase.kt` | 2 | v5 + MIGRATION_4_5 |
| `di/AppModule.kt` | 2 | 注册 MIGRATION_4_5 |
| `data/mapper/UnitMapper.kt` | 3 | study state 映射更新 |
| `data/local/dao/StudyDao.kt` | 3 | 查询去掉 remaining_reviews，改用 due |
| `domain/usecase/study/StudyUseCases.kt` | 4 | 删旧 UseCase，新增 ReviewWord/PreviewIntervals |
| `ui/screen/study/StudyUiState.kt` | 5 | previewIntervals + 四级统计 |
| `ui/screen/study/StudyViewModel.kt` | 5 | 四级评分逻辑 |
| `ui/screen/study/StudyScreen.kt` | 5 | 四按钮 UI + 间隔预览 |
| `data/json/DictionaryJsonModel.kt` | 6 | StudyStateJsonModel 新字段 |
| `data/json/JsonImportExporter.kt` | 6 | schemaVersion 4 |
| `domain/usecase/importexport/ImportExportUseCases.kt` | 6 | study state 映射 |
| `app/src/test/.../JsonImportExporterTest.kt` | 6 | 断言更新 |

---

## 执行顺序

```
Phase 1 (FSRS 引擎) → Phase 2 (数据模型) → Phase 3 (DAO/Mapper/Repo) → Phase 4 (UseCase) → Phase 5 (UI) → Phase 6 (导入导出)
```

每 Phase 完成后 `gradlew compileDebugKotlin`，全部完成 `gradlew assembleDebug` + `gradlew testDebugUnitTest`。

---

## FSRS 引擎关键实现细节

### review() 方法核心逻辑

```
fun review(state, step, stability, difficulty, lastReviewAt, reps, lapses, rating, now):
    elapsedMillis = now - lastReviewAt
    elapsedDays = elapsedMillis / (24*60*60*1000.0)

    CASE state:
      Learning / Relearning:
        steps = if Learning then learningSteps else relearningSteps
        CASE rating:
          Again → step=0, due=now+steps[0]*60000, state不变
          Hard → step不变, due=now+(1.5*steps[0]*60000 or midpoint), state不变
          Good at last step → 毕业: state=Review, step=null, S/D用FSRS算, due=now+interval*dayMs
          Good not last → step++, due=now+steps[step+1]*60000
          Easy → 立刻毕业: state=Review, step=null, S/D用FSRS算(带easy_bonus), due=now+interval*dayMs

      Review:
        R = retrievability(elapsedDays, stability)
        newD = nextDifficulty(difficulty, rating)
        CASE rating:
          Again → newS=nextStabilityAfterForget(D, S, R)
                  state=Relearning, step=0, lapses++
                  due=now+relearningSteps[0]*60000
          Hard → newS=nextStabilityAfterRecall(D, S, R, Hard)
                 interval=nextInterval(newS), fuzz, due=now+interval*dayMs
          Good → newS=nextStabilityAfterRecall(D, S, R, Good)
                 interval=nextInterval(newS), fuzz, due=now+interval*dayMs
          Easy → newS=nextStabilityAfterRecall(D, S, R, Easy)
                 interval=nextInterval(newS), fuzz, due=now+interval*dayMs

    // 如果 elapsedDays < 1（同日复习），S/D 用 shortTermStability

    return SchedulingResult(state, step, newS, newD, due, now, reps+1, lapses)
```

### 间隔格式化工具函数

```kotlin
fun formatInterval(millis: Long): String {
    val minutes = millis / 60_000
    return when {
        minutes < 60 -> "${minutes}分"
        minutes < 1440 -> "${minutes / 60}时"
        minutes < 43200 -> "${minutes / 1440}天"
        else -> "${"%.1f".format(minutes / 43200.0)}月"
    }
}
```
