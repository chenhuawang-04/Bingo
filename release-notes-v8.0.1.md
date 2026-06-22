## Bingo v8.0.1

### 修复

- **文章评分说明显示**：修复文章评分原因未在 UI 展示的问题，现在本地文章和在线文章的评分说明都会默认展开显示（斜体，最多 3 行）
- **重新评分按钮**：修复在线文章缺失重新评分功能的问题，三点菜单现已正确显示
- **代码优化**：统一本地文章和在线文章卡片组件，消除 ~320 行重复代码，提升可维护性

### 技术细节

- 新增 `UnifiedArticleCard` 组件，同时服务于 `ArticleListScreen`（本地文章）和 `GuardianBrowseScreen`（在线文章）
- 评分说明采用 `bodySmall` + `FontStyle.Italic` 样式，空白时自动隐藏
- 本地文章支持：重新评分 / 移至分类 / 删除
- 在线文章支持：重新评分

---

**完整 Changelog**: https://github.com/chenhuawang-04/Bingo/compare/v8.0.0...v8.0.1
