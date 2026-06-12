# 文章列表页 UI 重构设计

> [!NOTE]
> This document may not reflect the current implementation.
> See the final report for up-to-date state:
> [Final Report](../reports/article-list-ui-redesign.md)

## [S1] 问题

当前文章列表页 UI "太重"：
1. **顶部卡片占用过多空间** — LibraryOverviewCard + OnlineScanStatusCard 占据约 60% 首屏
2. **文章卡片信息密度高** — 缩略图、来源、标题、摘要、字数、评分、分类全部展示
3. **整体布局缺乏呼吸感** — 间距、圆角、颜色层级不够现代

## [S2] 设计目标

- 顶部空间释放 60%+
- 保留缩略图（美观）
- 保留评分+字数（筛选依据）
- 分类切换一眼可见
- 扫描状态内联，点击跳转详情页
- 配色使用项目 MintTeal (#0F766E) 为主色调

## [S3] 整体布局

页面从上到下：

| 区域 | 内容 |
|------|------|
| TopAppBar | 标题"文章阅读" + 在线阅读入口 + 设置入口 |
| CategoryTabs | 文字 Tab 切换分类，MintTeal 下划线指示选中 |
| FilterRow | 评分/长度/排序筛选入口（文字链接式） |
| ScanProgress | 细进度条内联显示扫描状态 |
| ArticleList | 卡片列表，白色背景 + 6px 圆角 |

**移除**：LibraryOverviewCard、OnlineScanStatusCard
**替换为**：内联式组件

## [S4] 分类 Tab

- 文字形式，选中项用 MintTeal 下划线（2px）+ 加粗
- 未选中项：`#9CA3AF`，普通字重
- 支持"新建分类"入口
- 分类多时可横向滚动
- 底部分隔线：1px solid `#E7EBEF`

## [S5] 筛选行

- 位于 Tab 下方，一行排列
- 筛选项：`评分 ▾`、`长度 ▾`、`排序 ▾`（文字链接，点击弹出 DropdownMenu）
- 右侧：`重置` 链接（MintTeal 色），仅在有筛选条件时显示
- 筛选逻辑复用现有 `ArticleFilterControls.kt`

## [S6] 扫描状态

- 内联为细进度条（3px 高，MintTeal 色）
- 右侧显示 `12/48 在线扫描`
- 点击跳转 → **扫描详情页**（新页面）

## [S7] 扫描详情页

新页面，展示：
- 任务状态（运行中/暂停/完成/失败）+ 状态指示器
- 进度条 + 当前/总数
- 配置面板（每栏目数量、重评间隔）
- 操作按钮（开始/暂停/继续/停止/清除）
- 错误信息（如有）

## [S8] 文章卡片

```
┌──────────────────────────────────────────┐
│ ┌──────┐  标题文字（titleSmall, 500）     │
│ │ 缩略 │  来源·栏目（labelMedium, 灰色） │
│ │ 图   │  摘要（bodySmall, 灰色, 1行省略）│
│ │40×52 │  520词 · 82分 · 默认分类        │
│ └──────┘                                 │
└──────────────────────────────────────────┘
```

- **缩略图**：40×52dp，4px 圆角，保持现有渐变色+首字母逻辑
- **标题**：`titleSmall`，`#111827`，最多 2 行
- **来源**：`labelMedium`，`#9CA3AF`，一行
- **摘要**：`bodySmall`，`#9CA3AF`，最多 1 行省略
- **元数据行**：字数 + 评分（MintTeal 色） + 分类名，`labelSmall`
- **卡片**：白色背景，6px 圆角，无边框，间距 8dp
- **操作**：保留右上角三点菜单（重新评估/移动/删除）

## [S9] 空状态

- 简洁文字提示："还没有文章进入这个分类"
- 两个文字按钮：`新建文章`（MintTeal 色）/ `切换分类`

## [S10] 操作

- 保留右下角 FAB，点击跳转文章编辑页
- 文章操作菜单（三点菜单）：重新评估/移动分类/删除

## [S11] 设计规范

**色彩**：
- 主色 #0F766E — Tab 下划线、重置、评分、进度条
- 辅助 #2563EB — 可选用于次要操作
- 背景 #F3F5F7 — 页面底色
- 卡片 — 白色背景 + 6px 圆角
- 文字 — #111827(标题) / #4B5563(正文) / #9CA3AF(辅助)

**尺寸**：
- 圆角 6dp（卡片）/ 4dp（缩略图）/ 2dp（进度条）
- 缩略图 40×52dp
- 间距 12dp（卡片内）/ 8dp（卡片间）
- Tab 下划线 2px solid #0F766E

## [S12] 涉及文件

- `ArticleListScreen.kt` — 主列表页重构
- `ArticleEditorialComponents.kt` — 移除/简化组件
- `ArticleFilterControls.kt` — 筛选逻辑复用
- `ArticleListViewModel.kt` — 新增扫描详情导航
- `BackgroundTaskManager.kt` — 扫描详情页数据
- 新增 `ScanDetailScreen.kt` — 扫描详情页
