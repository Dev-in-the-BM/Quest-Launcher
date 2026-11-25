# Shared app catalog and instant drawer plan

## Objective

Maintain one authoritative, launcher-wide snapshot of installed launchable apps.
Warm it as soon as the launcher process starts, keep it current from package
change events, and make every app list consume that same snapshot. Opening a warm
drawer must not query PackageManager, wait for a worker, or wait for icon
resolution before it can display its apps.

“Instant” means the populated list is ready for the drawer's first presented
frame. Android still has to start/resume an Activity, create its window, inflate
its layout, and present a frame; those platform costs cannot be literally zero.
The application-controlled target is no catalog work on the drawer's critical
path and under 16 ms for cached lookup/filter/bind work on the main thread.

## Current state and gaps

- `QuestLauncherApplication` starts `AppRepository.preload()`, and registers a
  dynamic receiver for `PACKAGE_ADDED`, `PACKAGE_REMOVED`, `PACKAGE_CHANGED`,
  and `PACKAGE_REPLACED`; package names are debounced and reconciled selectively.
- `AppRepository` now owns a shared metadata catalog; `getAllApps()` no longer
  performs its own full icon scan. The drawer can synchronously read the warm
  cached visible snapshot. A cold/unfinished catalog still uses an asynchronous
  fallback.
- App Picker and Hide Apps now read the shared repository. Home rail, Favorites,
  Shortcut Config, and Activity Picker observe shared catalog generations;
  Activity Picker metadata is cached by package.
- A working-tree change removes eager whole-catalog icon hydration at startup
  and after invalidation. The drawer requests its visible page first and defers
  the rest until that page's icons are ready. This mitigation has build
  verification only; it has not been installed or measured on device.
- **Drawer critical-path cleanup is incomplete.** Warm repository lookup is
  measured at 0–1 ms, but the installed build delivered metadata 129–181 ms
  after Activity creation and focused at 285–367 ms. Activity setup,
  inflation/layout, binding, focus scheduling, and the first presented frame
  have not been separated or optimized. New onCreate/onResume/populated-pre-draw
  logs are pending device validation; pre-draw is only a proxy.
- Package events for a known package replace only that package's launcher
  entries, labels, `ResolveInfo` records, and icon cache entries. Bursts are
  coalesced; stale in-flight reconciliations are retried with the accumulated
  package set. Events without a package URI or a not-yet-warm catalog fall back
  to full reconciliation. If a package-scoped PackageManager query fails, the
  repository logs the error and falls back to a full catalog scan.
- Catalog generations are published on the main thread. Active drawer, list,
  shortcut, Favorites, and Activity Picker consumers observe newer snapshots
  and reject older generations.
- Icon preference changes now invalidate rendered icon state independently of
  app metadata; visible consumers request the icons they need without repeating
  the launcher-app query.
- App metadata and icons live in process memory. Process death loses that
  snapshot. `Drawable` instances are also view-stateful and should not be
  treated as universally shareable mutable objects.

## Target design

### One repository and immutable catalog snapshot

`AppRepository` remains the single source of truth. Replace the split
`getAllApps()` / `getVisibleApps()` behavior with a versioned immutable
`AppCatalogSnapshot` containing sorted launchable-app records:

- stable component key, package name, activity name, label, and sort key;
- the package version/update identity needed to validate cache entries;
- a separate icon state/reference, rather than making list consumers own a
  mutable drawable;
- a monotonically increasing catalog generation/version.

Expose snapshot-based operations for all-app, visible-app, and component
lookups. Hidden-app filtering and list-specific presentation remain consumer
concerns; they must not trigger a new PackageManager scan. Preserve ordering,
component identity, overrides, and existing icon-shape behavior.

### Startup and drawer-open behavior

1. On process start, load a valid persisted snapshot if one exists.
2. Start a background PackageManager reconciliation immediately; do not wait
   for Home to finish starting.
3. If there is no valid snapshot, publish metadata as soon as the sorted
   catalog has been built. Do not hydrate every icon during launcher startup:
   that competes with drawer entry on constrained devices. Request the visible
   drawer page first, then hydrate the remaining catalog after that page is
   ready. Other screens request icons as their rows become relevant.
4. On drawer entry, synchronously read the latest immutable in-memory snapshot,
   apply hidden filtering, and bind it before first draw. Do not start a thread
   merely to retrieve already-cached data.
5. Deliver a newer generation to an open screen when background reconciliation
   completes. Ignore callbacks from older generations.

A persisted snapshot is required if the product requirement includes instant
content after process death, not just warm navigation within a live process.
Evaluate this after the in-memory shared repository is in place. If needed,
persist labels/component metadata and rendered icon bitmaps with versioned
invalidation; never persist `Drawable` objects.

### Icon cache and settings

Keep icon resolution separate from metadata enumeration, but owned by the same
repository. Cache icon output by component plus package update identity and
relevant rendering settings (shape, wrapping, legacy background, icon pack,
per-app override). Retain existing visible-first priority. Expose fresh drawable
instances (for example from `Drawable.ConstantState`) to views rather than
sharing mutable instances between simultaneously attached views.

Icon-setting changes invalidate or re-key rendered icons, not the installed-app
metadata catalog. Hidden-app changes only affect the visible projection. Package
changes invalidate both metadata and affected icons.

### Package changes

Use one application-scoped receiver for relevant package broadcasts, including
added, removed, changed, and replaced packages. Keep the `package` data scheme.
Interpret `Intent.EXTRA_REPLACING` so an update's remove/add sequence does not
appear as a true uninstall. Coalesce bursts into one reconciliation, invalidate
stale in-flight work with the catalog generation, then publish the rebuilt
snapshot. Confirm receiver export flags and delivery behavior on the minimum
supported Android version and current Android versions.

The receiver is a correctness mechanism, not a reason to rescan at every drawer
open. Also cover app enable/disable and package replacement behavior where the
platform emits the relevant broadcasts.

## Consumer migration

| Consumer | Current working-tree implementation | Remaining verification |
|---|---|---|
| `AppDrawerActivity` | Synchronous warm visible snapshot; async cold fallback; visible-page-first icon requests | Actual Activity setup/bind/focus time and first presented frame |
| `AppPickerActivity` | Reads shared repository and observes generations | Selection/icon parity and device exercise |
| `HideAppsActivity` | Reads shared repository; hidden state is a projection | Filtering/icon parity and device exercise |
| Home rail (`MainActivity`) | Repository component resolution and catalog observation | Main-thread/visible-frame impact |
| `FavoritesActivity` | Repository component resolution and catalog observation | Icon/selection parity |
| `ShortcutConfigActivity` | Repository component resolution and catalog observation | Icon/selection parity |
| `ActivityPickerActivity` | Repository package activity cache; shared icon queue | Package invalidation and rendered icon parity |

All screens must preserve their existing selection, hidden filtering, sorting,
icon overrides, and navigation behavior during migration.

## Implementation phases

### Implemented so far

- The shared metadata snapshot, startup preload, warm drawer lookup, and async
  shared icon hydration are in place.
- App Picker, Hide Apps, Home rail, Favorites, Shortcut Config, and Activity
  Picker consume shared repository state and observe catalog updates.
- Deep activity metadata is cached per package; deep activity icons use the same
  priority-ordered icon queue and component-key icon cache as launcher entries.
- Package receiver bursts are debounced and package-scoped reconciliation is
  implemented with generation checks. End-to-end install/update/uninstall
  behavior still needs device verification.
- Icon-only invalidation preserves metadata. Disk persistence remains deferred
  until process-death measurements establish whether it is necessary.

### Phase 1 — Repository contract and warm drawer (partial)

- Define immutable catalog record/snapshot and version semantics.
- Make the drawer use the shared snapshot synchronously when warm.
- Keep the current asynchronous fallback for first-ever/cold catalog creation.
- Add drawer cache-hit/cache-miss and first-populated-frame instrumentation.
- Confirm a cache hit performs no PackageManager calls and no thread handoff.
- **Still outstanding:** profile and reduce Activity setup/inflation/bind/focus
  work; measure tap-to-presented-frame on device. A 0–1 ms cache lookup alone
  does not satisfy the instant-drawer requirement.

### Phase 2 — Shared, demand-prioritized icon hydration (implemented in working tree; device validation pending)

- Move all launchable-app icon hydration under the shared repository.
- Keep startup metadata-only. Hydrate the drawer's visible page first; continue
  with remaining icons only after the initial page is ready.
- Share results with drawer, pickers, Home, and shortcut displays.
- Preserve per-view drawable isolation and icon settings invalidation.

### Phase 3 — Migrate every list consumer (implemented in working tree; device validation pending)

- Migrate App Picker and Hide Apps away from `getAllApps()` scans.
- Migrate Home rail, Favorites, and Shortcut Config component lookups.
- Add package-scoped activity caching for Activity Picker.
- Remove duplicate/legacy scan paths only after all consumers are migrated.

### Phase 4 — Package event correctness and live updates (partially implemented)

- Expand/verify receiver actions and replacement semantics.
- Debounce package-event bursts and reconcile once.
- Publish a new generation to active consumers; reject stale work/results.
- Test install, uninstall, update, disable/enable, and app component changes.

### Phase 5 — Persistence decision

- Measure process-cold launcher and drawer entry after Phases 1–4.
- If the process-cold requirement is still missed, implement a disk snapshot
  for metadata and icon bitmaps with explicit version/settings keys.
- Define corruption, migration, package-update, and icon-preference invalidation
  behavior before enabling persistence.

## Verification and acceptance

### Responsiveness

- Warm drawer: cached lookup, filtering, and adapter submission complete in
  under 16 ms on the main thread; no PackageManager calls, joins, thread
  creation, or icon rendering in the drawer-open critical path.
- Warm drawer: populated app names/order appear on its first presented frame;
  target tap-to-populated-frame p95 is under 100 ms over at least 20 opens on
  the reference device. Record Activity/window startup separately.
- Cold/process-death case: report metadata-ready, first populated frame,
  first-page icon-ready, and full-icon-ready independently. Do not label
  metadata readiness or `OnPreDraw` as a presented frame.
- Compare visible frame/jank traces while startup metadata and icons hydrate.

### Correctness

- Same labels, ordering, filtering, app selection, and component keys across all
  consumers.
- Icon parity for platform icons, adaptive/legacy rendering, icon packs,
  per-app overrides, shapes, wrapping, and legacy backgrounds.
- Install/uninstall/update/enable-disable events update the catalog exactly
  once and remove stale rows/icons.
- Package event during an in-flight scan cannot publish stale results.
- Hidden-app and icon-setting changes invalidate only the appropriate layer.
- Notification dots, focus, activation, and list/grid modes remain correct.

### Regression tests

- Repository tests for catalog sorting/filtering, generation rejection,
  coalesced invalidation, package replacement semantics, and icon cache keys.
- Consumer tests proving each app-list screen reads the shared repository.
- Device validation on HIT 1857 with a verified tap/input path; preserve and
  restore foreground app and wakefulness.

## Risks and decisions to resolve during implementation

- Eager icon resolution can contend for CPU with Home. Measure first-frame
  impact and prioritize visible icons; do not move heavy icon work onto main.
- Returning instantly with placeholders is not the same as instant complete
  icon content. Decide whether the acceptance target is populated metadata or
  fully rendered icons on first open; persistence may be needed for the latter
  after process death.
- Package broadcasts and receiver export behavior differ across Android
  versions; verify actual delivery on API 27 and a current API.
- Keep cache invalidation scoped: package changes affect catalog/icons,
  hidden changes affect visibility, and icon preferences affect rendered icons.
