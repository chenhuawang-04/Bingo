# EnglishHelper Android UI Refactor - Final Design

## 1) Design Goal

Build one adaptive, high-performance UI system that works across:
- Phone (portrait and landscape)
- Tablet (landscape first-class)
- Foldable (single screen and tabletop posture)
- ChromeOS and large-window multi-window mode

The app should feel refined but simple: clean color hierarchy, readable typography, and focused interactions.

## 2) Product Principles

- Adaptive first: layout changes by window size class, not by device name.
- Fewer jumps: list-detail and multi-pane layouts on medium/expanded windows.
- Fast by default: performance budgets and profiling are required, not optional.
- Visual calm: low-noise surfaces, one strong brand hue, one support hue, neutral background.
- Accessible: touch targets >= 48dp, strong contrast, scalable typography, keyboard support.

## 3) Platform Matrix

Use Android window size classes:
- Compact (< 600dp width): single-pane mobile flow.
- Medium (600-839dp): two-pane layout where useful.
- Expanded (>= 840dp): three-region information architecture.

For foldables:
- Respect hinge and posture with `WindowInfoTracker`.
- In tabletop mode, place content on top pane and controls on bottom pane.

For ChromeOS / freeform windows:
- Keep all layouts responsive to runtime window resize.
- Avoid hardcoded screen assumptions.

## 4) Information Architecture and Navigation

### 4.1 Global Navigation

- Compact: bottom navigation (if later expanded), current top app bar actions remain.
- Medium: `NavigationRail`.
- Expanded: permanent left navigation panel + content pane(s).

Use `NavigationSuiteScaffold` so navigation pattern switches automatically by size class.

### 4.2 Screen-Level Layout Templates

- Home
  - Compact: dashboard card + dictionary list.
  - Medium/Expanded: dashboard in a dedicated summary panel; dictionaries in grid (2-4 columns).

- Dictionary / Word list
  - Compact: list -> push to detail screen.
  - Medium: list + detail side-by-side.
  - Expanded: filters/search rail + list + detail.

- Add/Edit Word
  - Compact: sectioned single column.
  - Medium/Expanded: two columns:
    - Left: base info (spelling, phonetic, meanings)
    - Right: decomposition, synonym/similar/cognate, association preview

- Study
  - Compact: prompt -> reveal -> rating flow.
  - Medium/Expanded: split panes:
    - Main pane: question/answer card
    - Side pane: FSRS next intervals and queue status
  - Keep rating controls fixed and stable in position to reduce pointer travel.

- Import/Export and Settings
  - Medium/Expanded: settings-style list-detail.

## 5) Visual Language (Refined, Not Complex)

### 5.1 Color Direction

Style: "Ink and Mint"
- Primary: `#2563EB` (clear blue for focus and actions)
- Secondary: `#0F766E` (calm teal for supporting highlights)
- Accent-Warn: `#F59E0B` (attention)
- Accent-Error: `#DC2626` (error)
- Neutral background ramp:
  - `#FAFBFC` (app background)
  - `#F3F5F7` (surface container)
  - `#E7EBEF` (outline)
  - `#111827` (primary text)
  - `#4B5563` (secondary text)

Rules:
- 70% neutral surfaces, 20% content color, 10% accent/action color.
- No high-saturation multi-color cards by default.
- Keep dashboard semantic colors only where needed (retention, due pressure, warning).

### 5.2 Theme Model

- Keep Material 3 dynamic color support for Android 12+.
- Add a static fallback scheme based on the palette above.
- Introduce semantic tokens:
  - `success`, `warning`, `danger`, `info`
  - `studyAgain`, `studyHard`, `studyGood`, `studyEasy`
- Map tokens to light/dark variants once, then consume everywhere.

### 5.3 Typography and Spacing

- Maintain current typography base but add responsive scaling:
  - Compact: current scale
  - Medium: +1 step for headline/title
  - Expanded: +2 steps for headline, +1 for body
- Spacing system: 4/8/12/16/24/32
- Max readable line length:
  - body text <= 72 chars equivalent
  - forms capped by max width container

## 6) Component System

Create reusable UI primitives and stop screen-by-screen styling drift:

- `AdaptiveScaffold` (window class, pane slots)
- `EhCard` (surface levels, consistent elevation)
- `EhStatTile` (dashboard metrics)
- `EhSectionHeader`
- `EhSplitPane` (list/detail)
- `EhStudyRatingBar` (fixed metrics and colors)
- `EhEmptyState`, `EhErrorBanner`, `EhSkeleton`

All stateful lists must provide stable `key` and `contentType`.

## 7) Performance Architecture

## 7.1 Performance Budgets

- Cold start to first interactive frame: <= 1200 ms on mid-tier device.
- Home scroll jank: < 3% slow frames.
- Study interaction latency (button tap to state update): <= 100 ms target.

## 7.2 Compose Runtime Rules

- Prefer immutable UI models and stable data classes.
- Use `derivedStateOf` for computed UI values.
- Avoid expensive recomputation in composables.
- Hoist state to ViewModel; keep composables mostly stateless.
- Use `remember` and `rememberSaveable` where needed.
- Avoid nested lazy lists when possible.

## 7.3 Rendering and Layout

- Minimize overdraw with flat surface layers.
- Keep elevation levels limited (0, 1, 2) except modal/fab.
- Use lazy grids for dictionary cards on large screens.
- Avoid per-item heavy animation in large lists.

## 7.4 Data and Threading

- Dashboard queries should remain aggregated and indexed.
- Keep expensive calculations in ViewModel/IO dispatcher.
- Use pagination strategy if dictionary counts grow.

## 7.5 Startup and Scroll Tooling

- Add Baseline Profile module and generate profiles for:
  - app startup
  - home open and scroll
  - dictionary open
  - study first card and first rating
- Add Macrobenchmark checks in CI for regression gates.

## 8) Implementation Blueprint (Code-Level)

## 8.1 New Packages

- `ui/adaptive/`
  - `WindowSize.kt`
  - `AdaptiveScaffold.kt`
  - `PaneDefaults.kt`

- `ui/designsystem/`
  - `tokens/ColorTokens.kt`
  - `tokens/Spacing.kt`
  - `components/*.kt`

- `benchmark/` (new module for macrobenchmark + baseline profile)

## 8.2 Existing Files to Refactor First

- `app/src/main/java/com/xty/englishhelper/ui/theme/Color.kt`
- `app/src/main/java/com/xty/englishhelper/ui/theme/Theme.kt`
- `app/src/main/java/com/xty/englishhelper/ui/screen/home/HomeScreen.kt`
- `app/src/main/java/com/xty/englishhelper/ui/screen/word/WordDetailScreen.kt`
- `app/src/main/java/com/xty/englishhelper/ui/screen/addword/AddWordScreen.kt`
- `app/src/main/java/com/xty/englishhelper/ui/screen/study/StudyScreen.kt`

## 8.3 Dependency Additions

- Material 3 adaptive navigation suite (Compose)
- WindowManager (foldable posture and window features)
- Macrobenchmark + baseline profile tooling

## 9) Rollout Plan

### Phase A - Foundation (1-2 weeks)
- Adaptive scaffold and navigation switch
- Tokenized color and spacing
- Shared stat tile and card components

### Phase B - Core Screens (2-3 weeks)
- Home adaptive grid and dashboard polish
- Word list/detail split-pane
- Add/Edit two-column forms

### Phase C - Study Experience (1-2 weeks)
- Study split layout and stable action bar
- FSRS side insights on large screens

### Phase D - Performance Hardening (1 week)
- Baseline profile generation
- Macrobenchmark thresholds in CI
- Recomposition and jank cleanup

## 10) Acceptance Criteria

- UI adapts correctly across compact, medium, expanded window classes.
- No visual break in fold/unfold and window resize.
- Dashboard, dictionary, and study flows are fully usable on tablet landscape without extra navigation.
- Performance budgets are met in benchmark runs.
- Accessibility checks pass for contrast, size, and talkback focus order.

## 11) References (Official)

- Build adaptive apps (Android): https://developer.android.com/develop/ui/compose/layouts/adaptive/build-adaptive-apps
- Use window size classes: https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes
- Adaptive navigation suite: https://developer.android.com/develop/ui/compose/layouts/adaptive/build-adaptive-navigation
- Compose performance best practices: https://developer.android.com/develop/ui/compose/performance
- Baseline profiles overview: https://developer.android.com/topic/performance/baselineprofiles/overview
- Improve app performance with Macrobenchmark: https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview
- Material 3 color system guidance: https://m3.material.io/styles/color/system/overview

