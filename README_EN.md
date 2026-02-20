# EnglishHelper

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

### Units & Study
- **Unit grouping** — Organize words into units with multi-select assignment
- **Ebbinghaus review** — Spaced repetition algorithm based on the forgetting curve (5 min → 30 min → 12 hours → … → 30 days, 10 levels)
- **Study mode** — Select units to enter review flow with automatic scheduling of due words

### Import / Export
- **JSON format** — Import/export entire dictionaries as JSON files, including words, units, and study states
- **Schema versioning** — Current version schemaVersion: 3, includes decomposition data
- **Post-import rebuild** — Automatically batch-computes word associations after importing a dictionary

### Settings
- **API configuration** — Custom API Key, model selection (Haiku / Sonnet / Opus), custom Base URL
- **Encrypted API Key storage** — Stored using AndroidX Security Crypto
- **Connection test** — One-click API connectivity test after configuration

## Tech Stack

| Layer | Technology |
|-------|-----------|
| UI | Jetpack Compose + Material 3 |
| Architecture | Clean Architecture (Domain / Data / UI) |
| DI | Hilt |
| Local Storage | Room (SQLite), DataStore Preferences |
| Network | Retrofit + OkHttp + Moshi |
| Navigation | Navigation Compose (type-safe routes) |
| AI | Anthropic Claude API |
| Security | AndroidX Security Crypto |
| Async | Kotlin Coroutines + Flow |
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
│   ├── model/                   # Domain models
│   ├── repository/              # Repository interfaces
│   └── usecase/                 # Use cases
│       ├── ai/
│       ├── dictionary/
│       ├── importexport/
│       ├── study/
│       ├── unit/
│       └── word/
├── ui/                          # UI layer
│   ├── components/              # Reusable components
│   ├── navigation/              # Nav graph + route definitions
│   ├── screen/                  # Screen pages
│   │   ├── addword/             # Add/edit word
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
- Android SDK 34 (compileSdk)
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
6. **Start studying** — Select units to enter review mode; the system schedules reviews following the Ebbinghaus curve

## Database Versions

| Version | Changes |
|---------|---------|
| 1 | Initial schema: dictionaries, words, synonyms, similar_words, cognates |
| 2 | Added units, unit_word_cross_ref, word_study_state |
| 3 | Added normalized_spelling, word_uid columns to words + unique index |
| 4 | Added decomposition_json column to words; new word_associations table |

## License

This project is for learning and educational purposes only.
