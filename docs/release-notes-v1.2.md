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

## Improvements

### Network
- Added `network_security_config.xml` with `cleartextTrafficPermitted=true`, allowing connections to **local or HTTP-only AI API endpoints** (e.g., locally hosted models)

## Database Changes

| Version | Changes |
|---------|---------|
| 9 | Added `word_pools` table (id, dictionary_id, focus_word_id, strategy, algorithm_version) and `word_pool_members` join table (word_id, pool_id) with cascading foreign keys |
