# Bingo v1.1 Release Notes

## New Features

### Article Reading Module
- Added a dedicated **Article tab** alongside the Dictionary tab, accessible via bottom navigation bar (phone) or navigation rail (tablet)
- Create articles by **manual entry** or **photo upload** with AI OCR extraction of title, content, domain, and difficulty level
- Articles are automatically parsed in the background after saving: sentence splitting, tokenization, and word frequency statistics
- Article cards display **real-time parse status** (Pending / Processing / Done / Failed)
- Dictionary words are **automatically highlighted** in the reader, including inflection matching (plural, past tense, etc.) — tap any highlighted word to jump to its detail page
- **Long-press any sentence** for AI-powered analysis: Chinese translation, grammar points, and key vocabulary, displayed in a bottom sheet
- Analysis results are **cached per model version** to avoid duplicate API requests

### Vocabulary–Article Bidirectional Linking
- When an article is parsed, dictionary words (including inflected forms) are automatically matched and linked to specific sentences
- **Saving or editing a word** automatically scans all articles and backfills new links
- Example sentences containing the target word are **automatically extracted** from articles, labeled with source (e.g., "「Article Title」例句"), and displayed on the word detail page

### Dictionary Word List Pagination
- Word list in dictionary detail now displays **10 words per page**
- Pagination controls (previous / page indicator / next) appear at the bottom when total words exceed one page
- Pagination applies to search results as well; changing the search query resets to page 1

## Improvements

### Code Quality
- Split large screen files into **container / content / component layers**: AddWordScreen (776 → 3 files) and StudyScreen (586 → 3 files), eliminating duplicated wide/compact layout code
- Introduced `WordExampleSourceType` enum (MANUAL / ARTICLE), replacing all hardcoded `sourceType = 1` magic numbers
- Replaced silent `catch` blocks with `Log.w` calls in word-linking and save pipelines for production traceability
- Eliminated duplicate data flow subscription in ArticleReaderScreen — unified on ViewModel's `uiState`
- Changed DictionaryMatcher dedup key from `hashCode()` to string-based key, preventing hash collision false deduplication
- Unified example source label format to `「Title」例句` across ParseArticleUseCase and LinkWordToArticlesUseCase
- Refactored MainScaffold route matching from hardcoded `startsWith` chains to `KClass.qualifiedName` set-based lookup

### Testing
- Added **Migration7To8Test** (3 tests): column addition, default value, composite unique index validation
- Added **LinkWordToArticlesUseCaseTest** (10 tests): old link cleanup, sourceLabel format, enum type, inflection matching, multi-article processing, and more
- Fixed `SaveWordUseCaseTest` missing constructor parameter
- CI now includes an **instrumented test job** (android-emulator-runner) — migration tests are part of the GitHub Actions gate

## Database Changes

| Version | Changes |
|---------|---------|
| 6 | Added article module tables: articles, article_sentences, article_word_stats, article_word_links, sentence_analysis_cache, word_examples, article_images |
| 7 | Added normalized_token index to article_word_stats |
| 8 | Added model_key column + composite unique index to sentence_analysis_cache for per-model caching |
