# Bingo v2.0.0 Release Notes

## New Features

### Retry Failed Organize Tasks
- Added a **"重试全部失败" (Retry All Failed)** button in the organize task detail dialog
- Retries are **scoped to the current dictionary** — tasks from other dictionaries are not affected
- Failed tasks are re-enqueued with the same word ID and dictionary context

### Scoped AI Settings (Pool AI / OCR AI)
- Introduced **AiSettingsScope** mechanism with three scopes: `MAIN`, `POOL`, `OCR`
- Each scope stores its own Provider, API Key, Model, and Base URL independently
- **Pool AI**: configure a cheaper model for word pool generation (Balanced+AI / Quality-First strategies)
- **OCR AI**: configure a multimodal-capable model for article image recognition and batch import
- **Automatic fallback**: when a scope is not configured (or its API key is blank), the system transparently falls back to the MAIN settings — zero configuration required
- Settings page adds a **Switch toggle** per scope, with full provider/apiKey/model/baseUrl form and independent **Test Connection** button
- Provider switch race condition handled: local UI state updates synchronously before persisting, ensuring API key writes always target the correct provider

### Batch Photo Import
- Dictionary page FAB now opens a **dropdown menu** with two options: "手动添加" (Manual Add) and "拍照批量导入" (Batch Photo Import)
- New **BatchImportScreen** flow:
  1. Select one or more images from gallery
  2. Enter extraction conditions (free text, e.g., "蓝色字体", "黑体", "标题中的")
  3. Tap **"AI 提取"** — uses the OCR AI scope to call `sendMultimodalMessage`
  4. Preview extracted words with **checkboxes** (toggle selection) and **editable spelling**
  5. Tap **"全部导入"** — saves each word via `SaveWordUseCase` and enqueues background AI organization
- Empty/blank word entries are **filtered out** before import
- Import progress shown with `LinearProgressIndicator`
- On completion, navigates back with a snackbar confirmation

## Architecture Changes

### AiSettingsScope
- New enum `AiSettingsScope(prefix: String)` in domain layer — `MAIN("")`, `POOL("pool_")`, `OCR("ocr_")`
- `EncryptedApiKeyStore` — new overloads: `getApiKey(scope, provider)`, `setApiKey(scope, provider, key)`, `clearScopedApiKeys(scope)` with key format `api_key_{prefix}{provider_lower}`
- `SettingsDataStore` — new `data class AiConfig(provider, apiKey, model, baseUrl)` + `suspend fun getAiConfig(scope)` with fallback logic + scoped Flow getters/setters + `clearScopedSettings(scope)`
- `WordPoolRepositoryImpl.callAi()` now reads config via `getAiConfig(AiSettingsScope.POOL)`
- `ArticleEditorViewModel.extractWithAi()` now reads config via `getAiConfig(AiSettingsScope.OCR)`
- `ArticleReaderViewModel.analyzeSentence()` remains on MAIN (text analysis, not OCR)

### ArticleAiRepository
- New interface method: `extractWordsFromImages(imageBytes, conditions, apiKey, model, baseUrl, provider): List<String>`
- Implementation builds a Chinese prompt with conditions interpolated, sends via `sendMultimodalMessage`, and parses the JSON string array response

## Files Changed

| Type | File |
|------|------|
| Added | `domain/model/AiSettingsScope.kt` |
| Added | `ui/screen/batchimport/BatchImportScreen.kt` |
| Added | `ui/screen/batchimport/BatchImportViewModel.kt` |
| Modified | `data/preferences/EncryptedApiKeyStore.kt` |
| Modified | `data/preferences/SettingsDataStore.kt` |
| Modified | `data/repository/ArticleAiRepositoryImpl.kt` |
| Modified | `data/repository/WordPoolRepositoryImpl.kt` |
| Modified | `domain/organize/BackgroundOrganizeManager.kt` |
| Modified | `domain/repository/ArticleAiRepository.kt` |
| Modified | `ui/navigation/Screen.kt` |
| Modified | `ui/navigation/NavGraph.kt` |
| Modified | `ui/screen/article/ArticleEditorViewModel.kt` |
| Modified | `ui/screen/dictionary/DictionaryScreen.kt` |
| Modified | `ui/screen/dictionary/DictionaryViewModel.kt` |
| Modified | `ui/screen/settings/SettingsScreen.kt` |
| Modified | `ui/screen/settings/SettingsUiState.kt` |
| Modified | `ui/screen/settings/SettingsViewModel.kt` |
| Modified | `app/build.gradle.kts` |
