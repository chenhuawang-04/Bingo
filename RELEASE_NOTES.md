# Release Notes

## v2.3.2 (2026-04-02)

### Fixes
- Fixed plan sync conflict resolution in `sync()`.
- Version comparison now uses real data update time (`latestUpdatedAt`) as the primary signal.
- `exportedAt` is now only used as fallback when update timestamps are unavailable.
- This prevents false wins by freshly-exported local snapshots and allows newer cloud plan data to sync back correctly.

### Technical
- Updated: `app/src/main/java/com/xty/englishhelper/data/repository/GitHubSyncRepositoryImpl.kt`
- Commit: `7e8ebe7`

---

## v2.3.1 (2026-04-02)

### New
- Added full **Plan** support in cloud sync and local import/export.

### Sync
- Added `plan.json` to GitHub sync pipeline:
  - download in `sync` / `forceDownload`
  - upload in `sync` / `forceUpload`
  - merge support for plan snapshots
- Added `hasPlan` to `manifest.json` metadata.

### Import / Export
- Added Plan JSON import/export use cases.
- Added Plan import/export actions in Import/Export screen.

### Data Model
- Added plan backup domain model:
  - `PlanBackup`
  - `PlanTemplateBackup`
  - `PlanItemBackup`
  - `PlanDayRecordBackup`
  - `PlanEventLogBackup`
- Added plan JSON models:
  - `PlanExportJsonModel`
  - `PlanTemplateJsonModel`
  - `PlanItemJsonModel`
  - `PlanDayRecordJsonModel`
  - `PlanEventLogJsonModel`

### Repository / DAO
- Extended `PlanRepository` with backup export/replace APIs.
- Implemented backup read/write in `PlanRepositoryImpl`.
- Added DAO support methods for one-shot read and full replace.

### Validation
- `assembleDebug` passed.
- `testDebugUnitTest` passed.

### Technical
- Main commit: `678d44f`

---

## v2.0.0 (2026-03-13)

### Major
- Introduced Question Bank module with scan, parse, practice, answer generation, source verification, and reader integration.
- Added new question-bank schema and navigation integration.

> Historical details before v2.3.x are summarized here to keep this file readable. If needed, split older notes into `docs/release/` files.
