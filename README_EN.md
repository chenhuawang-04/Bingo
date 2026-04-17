<p align="center">
  <img src="bingo.ico" alt="Bingo" width="128" />
</p>

<h1 align="center">Bingo</h1>

<p align="center">
  AI-powered English exam prep platform for Android<br/>
  Vocabulary · Reading · Practice · Grading · Review
</p>

<p align="center">
  <a href="README.md">中文</a>&nbsp;&nbsp;·&nbsp;&nbsp;
  <a href="RELEASE_NOTES.md">Release Notes</a>
</p>

---

## Overview

Bingo is a native Android app designed for **postgraduate English exam (考研英语) preparation**. With deep AI integration, it unifies vocabulary management, English reading, exam practice, and spaced repetition into three tabs:

| Dictionary | Articles | Question Bank |
|:---:|:---:|:---:|
| Add words · AI organize · Root decomposition · Word pools · FSRS review | Local articles · Online reading · Paragraph translation/analysis · Word collection · TTS | Paper scanning · 10 question types · AI answers/grading · Source verification · Wrong tracking |

---

## Features

### Dictionary & Vocabulary

- **Multi-dictionary** — Create multiple dictionaries organized by source, difficulty, or purpose
- **AI auto-organize** — One-click fill: phonetics, definitions, root decomposition, synonyms, similar words, cognates, inflections
- **Background organize** — AI runs in background after save; failed tasks support retry
- **Batch photo import** — Select images → enter extraction criteria → AI extracts → preview & select → batch import
- **Root decomposition** — Structured breakdown into prefix/root/suffix/stem/linking morphemes with card visualization
- **Association network** — Auto-computed related words via morpheme Jaccard similarity; clickable navigation
- **Deduplication** — Normalized spelling detection with upsert semantics

### Word Pools

Three strategies for grouping related words to aid associative memory:

| Strategy | Description |
|----------|-------------|
| Balanced (local) | Edit distance + synonym/similar/cognate cross-refs + definition overlap via Union-Find |
| Balanced + AI | Local strategy + AI batch supplementation for unassigned words |
| Quality-first | Per-word AI matching, high token cost but most precise |

### Spaced Repetition

- **FSRS-5 algorithm** — Adaptive scheduling replacing fixed Ebbinghaus curves
- **4-level rating** — Again / Hard / Good / Easy with real-time interval preview
- **Brainstorm mode** — Pool-based associative review while maintaining FSRS scheduling
- **Study dashboard** — Retention rate, due words, daily progress, completion estimates

### Article Reading

- **Paragraph-level data model** — Articles stored and rendered by paragraph
- **AI OCR input** — Upload photos, AI extracts title, body, domain, and difficulty
- **Vocabulary highlighting** — Dictionary words (including inflected forms) auto-highlighted; click to view details
- **Bidirectional linking** — Article ↔ Word automatic links; example sentences auto-extracted with source attribution
- **Paragraph translation** — Per-paragraph AI translation with toggle and caching
- **Paragraph analysis** — AI analysis: Chinese translation, grammar points, key vocabulary, sentence breakdown
- **Word collection notebook** — Collect unfamiliar words during reading, AI quick analysis, one-click add to dictionary/unit
- **Article TTS** — Paragraph-by-paragraph reading with pause/follow-scroll/navigation
- **Suitability scoring** — AI evaluates article fitness for question generation (0-100), filterable and sortable
- **Category management** — Organize articles by category with multi-dimensional filtering
- **Question generation** — Generate exam questions from article content in the reader

### Online Reading

Three English media sources with unified browsing:

- **The Guardian** — Browse by section
- **The Atlantic** — Long-form articles
- **CS Monitor** — Current affairs

Online articles are parsed into temporary articles for immediate reading, word scanning, and one-click save to local.

### Question Bank

#### Paper Scanning

Select images or PDF → AI auto-recognizes question types/passages/questions/options → preview & edit → save. Supports auto-detection and manual correction of question types.

#### 10 Question Types

| Type | Key Features |
|------|-------------|
| **Reading Comprehension** | Integrated reading + practice, word links/TTS/translation/analysis/notebook |
| **Cloze** | Interactive inline blanks, portrait vertical / landscape horizontal split |
| **Translation** (Eng I/II) | Free-text input → AI scoring (0-2 pts) + reference translation + key points |
| **Writing** (Small/Large) | OCR handwriting → AI five-band scoring + sub-scores + deductions + suggestions |
| **Paragraph Order** | Select paragraph letters (A-H) for blank positions |
| **Sentence Insertion** | Select candidate sentences (A-G) for passage blanks, editable options |
| **Comment-Opinion Match** | Match comments to summary statements (A-G) |
| **Subheading Match** | Match paragraphs to best subheadings (A-G) |
| **Information Match** | Match descriptions to options (A-G) |

#### Deep AI Integration

- **Answer generation** — Background auto-generation of answers and explanations after saving
- **Answer scanning** — Upload answer sheet photos, AI extracts and updates
- **Source verification** — Web search for original article sources; success auto-creates linked readable articles
- **Translation scoring** — Per-sentence comparison against reference, scoring + gain/loss feedback
- **Writing grading** — Following official scoring guidelines: five-band placement → fine-tuning, including word count deductions and template penalties
- **Sample search** — Search model finds real sample essays with links
- **Wrong tracking** — Auto-increments wrong count; orange (1×) / red (2×+) border highlighting

### Import/Export & Sync

- **JSON import/export** — Full dictionary (words + units + study state), Schema v3
- **GitHub cloud sync** — Question bank data via `questionbank.json` with incremental merge

### Adaptive UI

- **Responsive layout** — Material 3 Adaptive, phone NavigationBar / tablet NavigationRail
- **List-detail split** — Tablet dictionary and study screens auto-split
- **Two-column forms** — Tablet word edit with side-by-side layout
- **Design tokens** — "Ink & Mint" color system (InkBlue + MintTeal)

### Settings

- **Multi-provider** — Messages API / Completions API compatible, custom providers with name/URL/key
- **Scoped AI** — MAIN / FAST / POOL / OCR / ARTICLE / SEARCH — 6 independent model configurations
- **Encrypted API keys** — AndroidX Security Crypto
- **Connection testing** — Per-scope one-click test

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| **Language** | Kotlin 2.1.0 |
| **UI** | Jetpack Compose + Material 3 + Adaptive |
| **Architecture** | Clean Architecture · MVVM · Use Cases |
| **DI** | Hilt 2.53.1 |
| **Database** | Room 2.6.1 (v19, 19 migrations) |
| **Preferences** | DataStore Preferences |
| **Network** | Retrofit 2.11.0 + OkHttp 4.12.0 + Moshi |
| **Images** | Coil |
| **HTML** | Jsoup |
| **Navigation** | Navigation Compose (type-safe routes) |
| **AI** | Messages API / Completions API |
| **Security** | AndroidX Security Crypto |
| **Async** | Coroutines + Flow |
| **Spaced Repetition** | FSRS-5 |
| **NLP** | Tokenization / sentence splitting / dictionary matching (precompiled regex + inflection index) |

---

## Build

**Requirements:** Android Studio Hedgehog+, JDK 17, Android SDK 35 (compileSdk), Min API 26

```bash
./gradlew assembleDebug          # Build Debug APK
./gradlew testDebugUnitTest      # Unit tests
```

---

## Quick Start

1. **Configure AI** — Settings → enter API Key → select provider & model → test connection
2. **Create dictionary** — Dictionary tab → "+" → enter name
3. **Add words** — Enter dictionary → "+" → manual add or batch photo import → AI auto-organize
4. **Generate pools** — Dictionary menu → select strategy → generate
5. **Study & review** — Select units → start study (toggle brainstorm mode)
6. **Add articles** — Articles tab → "+" → manual input or photo AI extraction
7. **Online reading** — Articles tab → online reading → browse Guardian/Atlantic/CSMonitor
8. **Scan papers** — Question Bank tab → scan → select images/PDF → AI recognition → save
9. **Practice** — Tap question group → answer → submit → view results/AI scoring/explanations

---

## License

[AGPL-3.0](LICENSE)
