# Task and Resource Management Audit

## Management Model

The application uses three task lifetimes. Persistent work is stored in Room and dispatched by `BackgroundTaskManager`; it survives process death and resumes `RUNNING` tasks as `PENDING`. Foreground work is owned by `viewModelScope` or Compose scope and is canceled with its screen. Process services use application-scoped `SupervisorJob`s and must expose bounded queues or flows.

`AppResourceCoordinator` is the process-wide resource registry. Background dispatch considers both persistent task demand and live foreground leases before launching work. The background task screen exposes the current memory-heavy, CPU-heavy, network, database-writer, audio, and exclusive usage totals.

Suspend-based foreground work passes through process-wide memory, CPU, and database-writer admission gates. Multi-resource operations acquire permits in a fixed order and release them in reverse order. OkHttp traffic uses the shared dispatcher because synchronous interceptors cannot suspend on coroutine permits.

Aggregate transactions that call lower-level managed components use observation-only leases to avoid recursively acquiring the same permit. PDF rendering and image compression run as separate admission-controlled phases for the same reason.

## Covered Resources

- All OkHttp traffic shares one dispatcher (8 global requests, 4 per host) and automatically acquires a network lease. This covers AI providers, GitHub, Guardian, CS Monitor, Atlantic, Cambridge, and OED HTTP traffic.
- OED WebView WAF solving registers its non-OkHttp network and memory use.
- Image reading/compression registers memory and CPU use, limits batches to 50 images, rejects files over 30 MiB, and caps retained batch output at 80 MiB.
- PDF rendering registers memory/CPU use and rejects more than 40 pages or pages over 12 million rendered pixels.
- TTS prewarming uses a bounded worker pool, one active article session, readiness/speaking timeouts, cancellation-safe callback cleanup, asynchronous media preparation, and a 200 MiB disk-cache limit.
- TTS playback holds an audio lease for the lifetime of Android AudioFocus, so exclusive heavy tasks wait until narration releases the device audio resource.
- Pool edge writes register database-writer use; graph building and cloud sync register memory/CPU use. Import operations register memory, CPU, and database writes.

## Scheduling and Reliability Invariants

- Task launch is registered before coroutine start, preventing completed-job slot leaks.
- Manager startup is guarded by an atomic one-time latch.
- User-initiated work outranks maintenance work; FIFO order is stable within a priority.
- Resource budgets prevent overlapping exclusive, memory-heavy, CPU-heavy, and database-writer tasks.
- Cancellation is rethrown before ordinary UI error handling; lifecycle cancellation is not reported as a business failure.
- Persistent task types remain string-backed. Hidden update checks require no Room schema change, so database version 36 remains compatible.

## Task Inventory

- Persistent: word organization, note linking, pool build/review, phrase organization, question/answer/source/sample generation, online article scoring, cloud sync, and app update checks.
- Foreground bounded: OCR and image compression, PDF rendering, import/export, online article hydration/evaluation, quick dictionary lookup, TTS playback/prewarming, and reader translations.
- Process bootstrap: locale application, settings/API-key migration, debug-mode observation, task-manager startup, and delayed daily-sync enqueueing.

Any new persistent operation must define payload serialization, dedupe behavior, priority, visibility, resource demand, cancellation/retry behavior, and UI labels. Any new foreground heavy operation must use a bounded lifecycle scope and acquire an `AppResourceCoordinator` lease directly or through a managed network/image/database entry point.
