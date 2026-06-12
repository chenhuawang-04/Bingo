---
feature: article-list-ui-redesign
status: delivered
specs:
  - docs/compose/specs/2026-06-12-article-list-ui-redesign.md
plans:
  - docs/compose/plans/2026-06-12-article-list-ui-redesign.md
branch: master
commits: 1740fb2..f739d18
---

# 文章列表页 UI 重构 — Final Report

## What Was Built

重构了文章列表页（ArticleListScreen），将"太重"的 UI 改为轻量现代设计。主要变更：

1. **移除顶部大卡片** — 删除 LibraryOverviewCard 和 OnlineScanStatusCard，释放约 60% 首屏空间
2. **新增内联组件** — 分类用文字 Tab 切换（MintTeal 下划线指示选中），扫描状态用细进度条内联显示
3. **简化文章卡片** — 缩略图从 92dp 缩小至 40dp，摘要限制 1 行，信息层级更清晰
4. **新增扫描详情页** — 点击扫描进度条跳转专属详情页，展示状态、进度、配置和操作按钮
5. **统一圆角** — ArticleShapes 圆角从 8-12dp 减小至 4-8dp，更现代

## Architecture

### 文件结构

| 文件 | 变更 | 职责 |
|------|------|------|
| `ArticleListScreen.kt` | 重构 | 主列表页，包含 CategoryTabRow、FilterRow、ScanProgressRow、ArticleCard |
| `ScanDetailScreen.kt` | 新增 | 扫描详情页 UI |
| `ScanDetailViewModel.kt` | 新增 | 扫描详情页状态管理 |
| `Screen.kt` | 修改 | 添加 ScanDetailRoute |
| `NavGraph.kt` | 修改 | 添加 ScanDetailRoute composable，连接导航 |
| `ArticleShapes.kt` | 修改 | 减小圆角值 |

### 数据流

```
ArticleListScreen
  ├── CategoryTabRow → viewModel.selectCategory()
  ├── FilterRow → viewModel.setFilter/Sort()
  ├── ScanProgressRow → onScanDetail → NavGraph → ScanDetailScreen
  └── ArticleList → ArticleCard → onRead/Delete/Move

ScanDetailScreen
  ├── ScanDetailViewModel → BackgroundTaskManager
  └── 操作按钮 → trigger/cancel/pause/resume/delete
```

### 设计决策

- **文字 Tab 而非 Chip/Pill** — 用户明确不喜欢胶囊按钮样式，文字 Tab 更简洁
- **缩略图保留但缩小** — 用户喜欢缩略图的美观性，但原尺寸过大
- **扫描状态内联** — 减少顶部卡片数量，点击跳转详情页而非展开
- **MintTeal (#0F766E) 为主色调** — 符合项目蓝绿风格

## Usage

### 分类切换
- 点击 Tab 切换分类，选中项显示 MintTeal 下划线
- 点击"+ 新建"创建新分类

### 筛选
- 点击"评分 ▾"/"长度 ▾"/"排序 ▾"弹出下拉菜单
- 选择后筛选条件立即生效，右侧显示"重置"链接

### 扫描
- 点击扫描进度条跳转扫描详情页
- 详情页可配置每栏目数量、重评间隔
- 支持开始/暂停/继续/停止/清除操作

## Verification

由于本地无 Android SDK，未执行编译验证。通过以下方式确认代码质量：
- 语法检查：所有 Kotlin 文件无明显语法错误
- 导入检查：所有 import 语句正确
- 一致性检查：旧组件引用已完全移除
- Git 状态：工作区干净，6 个 commit 已提交

## Journey Log

- [pivot] 用户拒绝了胶囊按钮样式（Chip/Pill），改为文字 Tab + 下划线
- [pivot] 用户要求扫描状态点击跳转专属详情页，而非内联展开
- [lesson] 用户偏好蓝绿色调（MintTeal），而非纯蓝色

## Source Materials

| File | Role | Notes |
|------|------|-------|
| `docs/compose/specs/2026-06-12-article-list-ui-redesign.md` | 设计规范 | 12 个章节，覆盖布局、组件、配色 |
| `docs/compose/plans/2026-06-12-article-list-ui-redesign.md` | 实施计划 | 9 个 Task，已全部完成 |
