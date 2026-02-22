# Bingo

[中文](README.md)

An AI-powered English vocabulary learning app for Android that helps you systematically memorize and understand English words.

## Features

### Vocabulary Management
- **Dictionary system** — Create multiple dictionaries to organize vocabulary from different sources
- **Word entry** — Manually add words with spelling, phonetics, POS & definitions, root explanation, etc.
- **AI auto-organize** — One-click Claude API call to auto-fill phonetics, definitions, decomposition, synonyms, similar words, and cognates
- **Deduplication** — Automatic duplicate detection based on normalized spelling (lowercase + trimmed), with upsert semantics

### Morpheme Decomposition
- **Structured decomposition** — Break words into prefixes, roots, suffixes and other morphemes, each labeled with role and meaning
- **Visual display** — Detail page shows decomposition as structured cards with root segments bolded and highlighted

### Word Association Network
- **Associated words** — Automatically computes inter-word associations using Jaccard similarity on decomposition segments with a root bonus, recalculated on every save
- **Linked word navigation** — Synonyms, similar words, and cognates that exist in the dictionary are clickable, navigating directly to their detail page
- **Association display** — Bottom of detail page shows associated words sharing morphemes with the current word, all clickable

### Spaced Repetition Study
- **Unit grouping** — Organize words into units with multi-select assignment
- **FSRS-5 algorithm** — Adaptive spaced repetition scheduling using FSRS-5, replacing fixed Ebbinghaus intervals
- **Learning dashboard** — Home screen shows retention rate, due word count, today's progress, and estimated clear time
- **Study mode** — Select units to enter review flow with four-level ratings (Again/Hard/Good/Easy) and real-time next-interval preview

### Adaptive UI
- **Responsive layouts** — Built on Material 3 Adaptive framework, automatically switches between phone and tablet layouts based on WindowSizeClass
- **Adaptive home** — Phone: dashboard card + dictionary list; Tablet: sidebar dashboard + dictionary grid
- **List-detail split** — Tablet dictionary screen shows list-detail split pane; tapping a word displays details in the right panel
- **Dual-column form** — Tablet add/edit word screen uses side-by-side two-column layout
- **Study split pane** — Tablet study screen with main pane on the left + progress/stats panel on the right
- **Large screen max-width** — Settings and import/export screens are centered and width-constrained on large screens
- **Design token system** — "Ink & Mint" color tokens (InkBlue + MintTeal) + semantic colors + spacing system + responsive Typography

### Import / Export
- **JSON format** — Import/export entire dictionaries as JSON files, including words, units, and study states
- **Schema versioning** — Current version schemaVersion: 3, includes decomposition data
- **Post-import rebuild** — Automatically batch-computes word associations after importing a dictionary

### Article Reading
- **Article management** — Manually enter or use AI OCR to extract English articles, with automatic sentence splitting and word frequency statistics
- **Vocabulary linking** — Automatically matches dictionary words (including inflections) found in article sentences, creating bidirectional links
- **Example extraction** — Automatically extracts example sentences containing target words from articles, labeled with source article
- **Sentence analysis** — AI-powered sentence meaning, grammar points and key vocabulary analysis, cached per model version

### Settings
- **API configuration** — Custom API Key, model selection (Haiku / Sonnet / Opus), custom Base URL
- **Encrypted API Key storage** — Stored using AndroidX Security Crypto
- **Connection test** — One-click API connectivity test after configuration

## Tech Stack

| Layer | Technology |
|-------|-----------|
| UI | Jetpack Compose + Material 3 + Material 3 Adaptive |
| Architecture | Clean Architecture (Domain / Data / UI) |
| DI | Hilt |
| Local Storage | Room (SQLite), DataStore Preferences |
| Network | Retrofit + OkHttp + Moshi |
| Navigation | Navigation Compose (type-safe routes) |
| AI | Anthropic Claude API |
| Security | AndroidX Security Crypto |
| Async | Kotlin Coroutines + Flow |
| Spaced Repetition | FSRS-5 adaptive algorithm |
| Testing | JUnit 4 + MockK + Room Testing |

## Project Structure

```
com.xty.englishhelper/
├── data/                        # Data layer
│   ├── json/                    # JSON import/export
│   ├── local/                   # Room database
│   │   ├── converter/           # Type converters
│   │   ├── dao/                 # Data access objects
│   │   ├── entity/              # Database entities
│   │   └── relation/            # Relation queries
│   ├── mapper/                  # Entity ↔ Domain mapping
│   ├── preferences/             # DataStore + encrypted storage
│   ├── remote/                  # Anthropic API client
│   │   ├── dto/                 # Request/response DTOs
│   │   └── interceptor/         # OkHttp interceptors
│   └── repository/              # Repository implementations
├── di/                          # Hilt DI modules
├── domain/                      # Domain layer
│   ├── article/                 # Article parsing tools (sentence splitting, tokenization, dictionary matching)
│   ├── model/                   # Domain models
│   ├── repository/              # Repository interfaces
│   ├── study/                   # FSRS spaced repetition engine
│   └── usecase/                 # Use cases
│       ├── ai/
│       ├── article/
│       ├── dictionary/
│       ├── importexport/
│       ├── study/
│       ├── unit/
│       └── word/
├── ui/                          # UI layer
│   ├── adaptive/                # WindowSizeClass utilities
│   ├── components/              # Reusable components
│   ├── designsystem/            # Design system
│   │   ├── components/          # Common component library (EhCard, EhStatTile, etc.)
│   │   └── tokens/              # Design tokens (color/spacing/Typography)
│   ├── navigation/              # Nav graph + route definitions
│   ├── screen/                  # Screen pages (container/content/component split)
│   │   ├── addword/             # Add/edit word
│   │   ├── article/             # Article list/editor/reader
│   │   ├── dictionary/          # Dictionary detail
│   │   ├── home/                # Home
│   │   ├── importexport/        # Import/export
│   │   ├── settings/            # Settings
│   │   ├── study/               # Study mode
│   │   ├── unitdetail/          # Unit detail
│   │   └── word/                # Word detail
│   └── theme/                   # Material theme
└── util/                        # Utilities
```

## Build

### Requirements

- Android Studio Hedgehog or later
- JDK 17
- Android SDK 35 (compileSdk)
- Minimum Android 8.0 (API 26)

### Compile & Run

```bash
# Build Debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew testDebugUnitTest

# Run instrumented tests
./gradlew connectedDebugAndroidTest
```

## Usage

1. **Configure API** — Go to Settings, enter your Anthropic API Key, select a model, and test the connection
2. **Create a dictionary** — Tap "+" on the home page to create a dictionary
3. **Add words** — Enter the dictionary, tap "+", type a word, and tap "AI Auto-Organize" to auto-fill
4. **Browse associations** — On the word detail page, view morpheme decomposition, tap synonyms/similar words/cognates to navigate, and browse associated words
5. **Create units** — Manage units on the dictionary page and assign words to different units
6. **Start studying** — Select units to enter review mode; the system schedules reviews using the FSRS-5 adaptive algorithm

## Database Versions

| Version | Changes |
|---------|---------|
| 1 | Initial schema: dictionaries, words, synonyms, similar_words, cognates |
| 2 | Added units, unit_word_cross_ref, word_study_state |
| 3 | Added normalized_spelling, word_uid columns to words + unique index |
| 4 | Added decomposition_json column to words; new word_associations table |
| 5 | Migrated word_study_state to FSRS-5 fields (stability/difficulty/due/reps/lapses) |
| 6 | Added inflections_json to words; new article module (articles, article_sentences, article_word_stats, article_word_links, sentence_analysis_cache, word_examples, article_images) |
| 7 | Added normalized_token index to article_word_stats |
| 8 | Added model_key column + composite unique index to sentence_analysis_cache |

## License

This project is for learning and educational purposes only.
