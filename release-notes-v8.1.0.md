## Bingo v8.1.0

### 新增与改进

- **词池审核体验增强**：补充审核明细页的实时状态反馈，完善词池构建阶段的流量与并发控制，让审核过程具备和构建流程一致的可观测性
- **审核边处理策略调整**：`remove` 操作不再直接删除边，而是将边的 `confidence` 置为 `0`，保留图结构与后续可追踪性
- **普通背词 / 头脑风暴记录隔离**：同一词汇在两种模式下维护并列、独立、互不影响的学习记录，头脑风暴模式不再复用普通背词进度

### 数据与兼容性

- **学习记录迁移**：现有历史学习记录会迁移到 `NORMAL` 模式，避免已有普通背词进度丢失
- **头脑风暴重新起步**：历史上混在共享记录里的头脑风暴行为无法自动拆分，因此升级后 `BRAINSTORM` 视角从空记录开始
- **导入导出与同步更新**：词典 JSON、导入校验、GitHub 同步逻辑均已补充 `studyMode` 维度，兼容新结构

### 验证

- `:app:testDebugUnitTest`
- `:app:compileDebugAndroidTestKotlin`
- `:app:assembleDebug`
- GitHub Actions `CI`
- GitHub Actions `Android Release`
