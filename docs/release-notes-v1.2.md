# Bingo v1.2 Release Notes

## New Features

### Word Pool System
- Introduced **Word Pools** — automatic grouping of related words based on multiple linguistic signals
- Three pool generation strategies available from the dictionary page menu:
  - **Balanced (Local)**: purely local computation using Union-Find with 5 signals — edit distance, synonym/similar/cognate cross-references, indirect transitive references, Chinese meaning substring overlap, and `word_associations` similarity (>= 0.35)
  - **Balanced + AI**: extends the balanced strategy by sending orphaned words (those not assigned to any pool) to AI in batches of 40 for supplementary grouping
  - **Quality-First (High Cost)**: per-word AI call comparing against 50 random candidates for precise form/meaning similarity — requires explicit user confirmation with estimated token consumption
- Large connected components (> 15 words) are **deterministically split** into pools of 2–15 members using connection strength ranking with stable tiebreaking (`strength desc, wordId asc`)
- Pool rebuilds are **safe by design**: all computation happens in memory; the database is only written in a single atomic transaction after completion. Cancellation at any point preserves existing pool data
- Algorithm versioning: each pool stores its `algorithm_version`; the dictionary page shows a warning when existing pools were built with an outdated algorithm, prompting the user to rebuild

### Word Detail — Pool Display
- Words belonging to pools now show a **"关联池" (Related Pool)** or **"精准池" (Precise Pool)** section on the detail page
- Pool members are displayed as clickable chips — tap to navigate to that word's detail page
- Works in both single-column (phone) and two-column (tablet) layouts

### Brainstorm Study Mode
- New **"头脑风暴" (Brainstorm)** mode selectable via filter chips on the study setup screen
- Reorders the FSRS review queue using a **stable insertion algorithm**: when a word comes up, all its pool-related words are pulled forward to appear consecutively, while preserving the overall FSRS scheduling order
- Related words (up to 5) are shown as **suggestion chips** at the top of the study card for context
- Title bar displays "风暴" prefix to indicate active brainstorm mode
- FSRS scoring logic is **completely unaffected** — only presentation order changes

### Background Word Organization
- New **"保存并后台整理"** button on the Add Word page — saves the word immediately and enqueues AI organization in the background, so the user can continue adding words without waiting
- `BackgroundOrganizeManager` runs as an application-level singleton with its own `CoroutineScope`, independent of any ViewModel lifecycle
- Merge strategy: AI results only fill **blank fields** — user-entered data is never overwritten
- Dictionary page status feedback:
  - Shows **"整理中: N个"** / **"失败: N个"** clickable badge in the word list area (only for the current dictionary)
  - Each word being organized displays a **16dp spinner** next to its spelling in the word list
  - Tapping the badge opens a **detail dialog** listing all tasks with status and dismiss controls
- Success tasks are **auto-dismissed after 3 seconds**; failed tasks remain until manually cleared
- Edge cases handled: duplicate enqueue ignored, API Key not configured → immediate FAILED, network/AI errors → FAILED with error message preserved

## Bug Fixes

- **Study rating buttons unresponsive**: fixed touch event handling on study rating buttons
- **HTTP URL scheme normalization**: URLs missing a scheme are now correctly normalized

## Improvements

### Network
- Added `network_security_config.xml` with `cleartextTrafficPermitted=true`, allowing connections to **local or HTTP-only AI API endpoints** (e.g., locally hosted models)

## Database Changes

| Version | Changes |
|---------|---------|
| 9 | Added `word_pools` table (id, dictionary_id, focus_word_id, strategy, algorithm_version) and `word_pool_members` join table (word_id, pool_id) with cascading foreign keys |
