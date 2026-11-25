# Performance and responsiveness review

**Reviewed:** November 24, 2026  
**Repository revision:** `4f77c9a` (`Refine home clock and app drawer layout`)  
**Scope:** Static architecture/code review, debug-only stage timing, on-device profiling, and a staged icon-hydration prototype. The prototype changes runtime behavior; it is not yet accepted as a verified performance improvement.

**Document status:** The November 25 follow-up below supersedes older statements
in this review about current cache behavior and drawer timings. Most measurements
later in this file are historical exploratory runs from earlier builds. The
latest shared-catalog/demand-driven changes are not fully device-verified; see
`status.md` and `docs/shared-app-catalog-plan.md` for the current implementation
state and outstanding work.

## Drawer slowdown follow-up: HIT 1857 (November 25, 2026)

After the user reported the drawer felt slower, `QuestPerf` logs from the
currently installed build showed that startup metadata preload was immediately
followed by a sweep of all 32 app icons. The scan took about **627 ms**, and
icon hydration continued for roughly **1.6 s**, with individual entries taking
25–241 ms. This is confirmed unnecessary background work and can contend with
Home/drawer transitions. It is not, by itself, proof that the full sweep
overlapped the later measured drawer taps.

The same logs show warm drawer metadata delivery in **129–181 ms from drawer
creation** (repository request itself was 0–1 ms), and first-focus logs in
**285–367 ms**. These timings include Activity/layout/focus work and are not
presented-frame measurements. They indicate the catalog lookup is not the
remaining 100+ ms cost; the pre-draw instrumentation added in the follow-up
build will separate populated-content readiness from focus timing.

The follow-up build removes whole-catalog icon hydration from startup and from
package/icon invalidation. The drawer now requests the initial visible page
first and queues remaining icons only after that page resolves. Build passed;
the updated APK was **not installed** while the drawer was the active foreground
screen, to avoid disrupting the user's test session. The installed build remains
the pre-fix version.

**Scope correction:** this is not yet the promised drawer-open cleanup. Work
focused heavily on shared catalog/repository/package-event architecture and did
not systematically reduce `AppDrawerActivity` setup, layout inflation, adapter
binding, or focus scheduling. The new build adds onCreate/onResume and
populated-content pre-draw logs to isolate those stages, but it has not been
installed or measured. The earlier 129–181 ms metadata delivery and 285–367 ms
first-focus observations therefore remain unresolved; they are not
first-presented-frame measurements.

## Device follow-up: HIT 1857 (November 24, 2026)

A limited exploratory check was run on USB device `KHV1A12012022A01857` (HIT, Android 8.1/API 27, 240×320 physical resolution at 120 dpi). The installed launcher reported version `1.0`, updated November 24, 2026. The original foreground app was a note editor and the device was awake; after the user authorized temporary interruption, the note editor was restored and the device remained awake.

### Observations

- After `am force-stop` of the launcher package, `am start -W` of `MainActivity` reported `ThisTime=1543 ms` and `TotalTime=1543 ms`. This is Activity launch timing, **not** first-frame or usable-Home latency.
- The debug `QuestPerf` log recorded a cold app scan of **29 entries in 1295 ms**.
- The drawer logged retrieval of 29 apps in **31, 53, 133, and 55 ms** across several drawer entries/rebinds. These are internal elapsed times, not end-to-end drawer readiness; the runs were not controlled enough to treat them as a benchmark.
- A screenshot confirmed the drawer populated (eight visible cells on the current page in the device’s rotated 320×204 app window).
- A `dumpsys gfxinfo` sample after a short drawer interaction contained only **8 frames**, of which 6 were marked janky (75%), with 5 missed-vsync and 5 slow-UI-thread frames. The sample is too small and includes launch/settling and instrumentation effects; it is a warning signal only, not representative performance evidence.
- The pre-test `gfxinfo` history had 307 frames and 65.47% janky, but both launcher windows were `visibility=8` (hidden). It is accumulated historical data and must not be used to characterize current visible performance.

### Scan-stage profile

After adding debug-only stage timings, three process-cold scans of the same 29-entry app set on 1857 took **1233, 1352, and 1257 ms**. Force-stopping the process does not clear Android’s PackageManager/resource caches, so these are process-cold runs, not pristine device-cache runs.

| Stage | Observed range | Interpretation |
|---|---:|---|
| PackageManager launcher query | 35–43 ms | Not the bottleneck |
| Load labels | 230–253 ms | Significant per-entry metadata work |
| Load platform icons | 509–531 ms | Largest single stage, roughly 40–43% of total |
| Resolve/transform icons | 418–495 ms | Significant; 363–435 ms for 15 legacy icons |
| Within legacy resolution: `Palette.generate()` | 281–337 ms | Roughly 21–25% of total and 77–79% of legacy resolution |
| Sort labels | 17–18 ms | Negligible |

The scan had **14 adaptive and 15 legacy icons**. Adaptive resolution cost 55–61 ms total; the legacy path cost 363–435 ms. The Palette timer covers the synchronous `Palette.from(sample).generate()` call, not the whole renderer. Platform icon loading plus Palette generation account for about **61–68%** of total scan time. Skipping Palette alone would still leave substantial icon loading and transformation work.

### Shape-disabled A/B

I temporarily set only `icon_shape=none` in the saved device preferences, ran three process-cold scans, then restored the original XML byte-for-byte and verified it with `cmp`. With shape rendering disabled, scans took **824, 834, and 840 ms**, versus **1233, 1352, and 1257 ms** in the shaped runs. The per-run comparison is not perfectly controlled for OS cache/thermal variance, but the stage timings agree:

- `iconResolve` fell from **418–495 ms** to **43–50 ms**.
- Palette time fell from **281–337 ms** to **0 ms**.
- Platform icon loading remained **474–478 ms** and labels remained **221–248 ms**.

This confirms that icon transformation is responsible for roughly **0.38–0.45 s** of the cold scan, and that Palette is most of that transformation cost. It also shows that eliminating shaping alone leaves an **~0.83 s** scan, so the remaining platform icon loads and label resolution must be taken off the path to a usable first page.

### Invalid/incomplete checks

- Injected `KEYCODE_MENU` did not open the drawer; the foreground became the system Dialer call-log Activity. The drawer was opened by tapping its visible Home control instead.
- Injected D-pad input was intercepted by the device’s `com.hit.keys/.preferences.screens.debug.TestInputActivity`. Therefore, the attempted long-press/repeat navigation data is invalid for evaluating launcher key handling. Do not infer a launcher navigation defect from this behavior.
- Notification callback duration/thread identity was not instrumented by the installed build. Icon-picker and wallpaper-picker scrolling were not reached. No Perfetto/atrace trace or controlled input-latency test was captured.

### Conclusion and follow-up

The on-device scan confirms a cold package/icon resolution cost near 1.3 seconds on this particular device, while drawer data retrieval is generally tens of milliseconds once cached. It does **not** establish first-frame latency, identify a jank cause, or validate held-D-pad behavior. For meaningful frame and input measurements, use a physical/external key path that bypasses the HIT key-mapping utility (or configure a safe test mapping), then collect a longer trace. Add narrow duration/thread instrumentation for notification recomputation and softkey label lookup before making claims about those paths. Keep these exploratory values separate from repeatable benchmark results.

## Task state and implementation handoff

### Work performed

- Reviewed the launcher’s startup/preload path, app repository, app drawer, other app-list screens, key navigation, Home rail/softkeys, notification recomputation, icon/wallpaper picker binds, and existing timing coverage.
- Ran an initial device check, then added stage timing and installed a debug APK on HIT 1857 for the component-level scan profile and shape-disabled A/B described above.
- The instrumentation currently lives in `app/src/main/java/quest/devinthebm/launcher/data/AppRepository.kt` and `app/src/main/java/quest/devinthebm/launcher/data/IconShapeRenderer.kt`. `AppRepository.getAllApps()` logs total/query/label/icon-load/icon-resolve/adaptive/legacy/Palette/sort times and icon counts under `QuestPerf`, only when the app is debuggable. The optional Palette callback times `Palette.from(sample).generate()` only; it does not change rendering output. The counters classify the loaded source drawable as adaptive vs legacy.
- `./gradlew :app:assembleDebug` passed and the instrumented debug APK was installed. `git diff --check` passed. These validate the instrumentation build, not a performance improvement.
- Device preferences and user state were restored: only `icon_shape=none` was changed for the A/B, original preferences were restored byte-for-byte and verified with `cmp`, and the original note editor was returned to the foreground with the device awake.
- Subsequent working-tree implementation unified app-list metadata/icon access, added package-scoped reconciliation and active-screen generation updates, and migrated the drawer, app pickers, Home rail, Favorites, Shortcut Config, and Activity Picker. These changes have build verification, but package-event correctness has not been verified with actual install/update/uninstall operations.
- After user feedback that drawer opening was slower, logcat from the installed build showed eager hydration of all 32 icons over roughly 1.6 seconds after the catalog scan. The working-tree mitigation removes this startup/invalidation sweep and hydrates the visible drawer page before the rest. The updated APK has not been installed because the drawer was foreground.
- **Not done:** a focused, measured cleanup of drawer Activity startup/inflation/binding/focus; a valid click-to-first-presented-frame benchmark; repeatable warm/cold comparison; visual parity verification of the latest implementation; package-broadcast device tests; or repository regression tests.

### Root cause established so far

Before the prototype, `AppRepository.getAllApps()` did a serial pipeline on a single background executor: query launcher activities; load every label and platform icon; apply icon overrides/icon-pack resolution and icon shaping; sort by label. `QuestLauncherApplication` starts preload during startup. This avoids doing the full scan on the UI thread, but Home startup overlaps it; the drawer could remain empty until its worker received the fully resolved list. The prototype now preloads metadata/placeholders first and hydrates icons separately. The process-local cache still loses its state when the process dies.

Measurements show PackageManager query and sort are small relative to per-item resource work. Palette generation in legacy icon rendering is a confirmed large sub-cost, but simply disabling shape rendering leaves a scan around 0.83 seconds because platform icon loads and labels remain. This led to the staged-icon-hydration prototype below; it remains an unverified optimization direction until repeated readiness/frame measurements and visual correctness checks pass.

### Staged-hydration prototype implemented

The current working tree implements the first staged version:

- `AppRepository.preload()` now queries/sorts labels into a lightweight catalog and retains its `ResolveInfo`s. It does not load every platform icon or render every shape before the drawer can receive the list.
- Catalog entries use a generic placeholder drawable and `AppInfo.iconResolved=false`. Existing full-icon APIs such as `getAllApps()` and `resolveComponent()` still return resolved icons for screens/shortcut consumers that need them.
- The drawer receives sorted, hidden-filtered metadata first. In the latest working tree it requests the current grid page at visible priority, then queues the rest of the catalog only after that page resolves. A later page request promotes queued icon tasks for that page.
- Icon work is serialized on a priority queue and runs off the UI thread. Successful results update the component-keyed resolved-icon cache and catalog. `AppDrawerActivity` applies only the matching app update; the grid/list adapters rebind the affected row rather than replacing the entire list for each icon.
- Cache generation checks prevent a stale metadata scan or icon job from repopulating a newly invalidated cache. The drawer also ignores older overlapping refresh deliveries. On destroy it detaches its callback while already-running work may still warm the cache.
- Softkey/settings label-only lookups use `resolveLabel()` so they do not force icon resolution merely to display text.

This is a prototype, not yet a visually accepted optimization. A placeholder is temporarily shown until its app icon resolves. The icon hydration worker also warms `NotificationDotColor`’s Palette cache before publishing each icon, so the subsequent row bind/focus update should not perform that analysis on the UI thread. That cost now contributes to worker completion time and should be included in future measurements. Placeholder-to-icon transition, all preference combinations, list scrolling, and stale-result paths have not yet had full verification.

Preserve alphabetical order, hidden-app filtering, per-app/icon-pack overrides, shape/wrap/legacy-background preferences, notification-dot presence/color, focus retention, activation, and cache invalidation. Do not do expensive PackageManager loads, Palette, or bitmap rendering in `onBindViewHolder()`. Avoid full-grid submissions per icon.

### Initial prototype device smoke (one run, exploratory only)

On HIT 1857, after force-stopping the launcher package, the installed first prototype build reported:

- `am start -W MainActivity`: **1649 ms**.
- Home `OnPreDraw` callback proxy: **765 ms from Activity `onCreate()`**. This is not a guaranteed presented-frame timestamp.
- Catalog load: about **495 ms** (query **30 ms**, labels **244 ms**, sort **28 ms**).
- Drawer metadata ready: **536 ms from drawer creation**, of which the repository request itself took **60 ms** after entry.
- First-page focus settled: **1108 ms** from drawer creation.
- First-page icons resolved: **1582 ms**; all visible catalog icons resolved: **2378 ms**.
- Individual icon tasks took roughly **10–156 ms** for most entries; one observed task took about **352 ms**.

This confirms the stage instrumentation and that the code delivered metadata and icon results asynchronously in this run. It is one exploratory run (system package/resource caches retained), and Activity/data milestones are not identical to pixels presented on screen. It is not a benchmark, and no valid D-pad or visual screenshot pass was obtained. The final APK after stale-generation/thread-safety hardening was rebuilt and installed but was not re-exercised interactively. Notification-dot cache warming was added afterward and is not represented by these timings.

### Next validation gate — drawer open path first

The current priority is the work that should have led this effort: identify and
remove unnecessary work between drawer Activity creation and its first
populated/presented frame. The metadata lookup being 0–1 ms does not explain or
resolve the measured 129–181 ms to metadata delivery or 285–367 ms to first
focus. Inspect Activity setup, inflation/layout, adapter submission, and focus
scheduling with the new timing points; then install and validate the resulting
change when it is safe to interrupt the active drawer session.

Before extending this into other screens or optimizing unrelated paths:

1. Verify click-to-first-populated-frame on 1857 for both warm and cold entry; record Activity/window startup separately from catalog delivery.
2. Measure milestones separately: Home first rendered frame; sorted metadata ready; first drawer page populated and focused; first-page icons ready; all icons ready. Repository-return time is not a substitute for these.
3. Capture a sufficiently long trace while the launcher window is visible. Use a real/verified input path; the previously injected D-pad events were intercepted by `com.hit.keys` and are not usable evidence.
4. Verify appearance and correctness while navigating during load: icon parity, ordering, focus/activation, page changes, placeholders, no stale recycled-holder updates, and invalidation on package/icon-setting changes.
5. Compare both latency and frame-time/allocations with the current profile. Keep the debug stage timers until the staged implementation has been measured.

Do not claim that notification callbacks, softkey label lookup, held-key handling, or picker binds cause jank until their thread/duration/frame impact is captured. Their static-analysis findings remain follow-up work; the main measurable cold-scan cost is the current priority.

## Code-level diagnosis: cold app scan

The 1295 ms scan is not one PackageManager query. `AppRepository.getAllApps()` (lines 64–90) times the entire pipeline as one interval:

1. `PackageManager.queryIntentActivities(MAIN/LAUNCHER, 0)` obtains launcher activities.
2. For each returned activity, the pipeline calls `ResolveInfo.loadLabel()` and `ResolveInfo.loadIcon()`.
3. Each icon goes through `resolveIcon()` and `IconShapeRenderer.render()`.
4. Results are sorted by lowercased label and materialized into a list.

The work is serial: Kotlin maps each activity in order, and the icon-rendering work completes before the next item is processed. The executor moves this work off the main thread but does not make it cheaper or parallel. The measured 1295 ms across 29 entries is about **45 ms per entry on average**, including the initial PackageManager query and sorting.

### Confirmed cost: loading and rendering every icon on a cold process

Device 1857’s `flip_launcher_prefs.xml` has no explicit icon-shape, icon-pack, or per-app wrap-disable entries. The code defaults to `CIRCLE`, wrapping enabled for every app, legacy backgrounds enabled, and no active icon pack (`LauncherPrefs.kt:254–280`, `AppRepository.kt:191–203`). Thus this scan transforms the platform icon for essentially every entry:

- Adaptive icons allocate a 108dp-scale ARGB bitmap and a second output bitmap, draw the adaptive layers, and apply a mask (`IconShapeRenderer.kt:56–74, 96–103`).
- Legacy icons additionally allocate and draw a 48×48 sample bitmap and synchronously run `Palette.generate()` to derive the background color (`IconShapeRenderer.kt:76–93, 106–114`), then allocate/render the icon and mask.

On this device’s 120 dpi density, the nominal 108dp canvas becomes 81×81 pixels. Repeated bitmap allocation and drawable rendering add work, while the scan profile confirms `Palette.generate()` alone consumes about 0.28–0.34 seconds. Platform `loadIcon()` is even larger at about 0.51–0.53 seconds. The source and measurements establish that loading and transforming every app icon before returning the list is the principal avoidable cost.

### Other contributors and factors

- Cross-package resource loading is significant: labels cost about 0.23–0.25 seconds and platform icons about 0.51–0.53 seconds. Cold resource/PackageManager caches may make first resolution slower; repeated force-stop runs do not clear system caches.
- Per item, `resolveIcon()` constructs a `LauncherPrefs` wrapper, reads override/shape/wrap settings, and `isIconWrapEnabled()` copies the wrap-disabled set. These are avoidable repeated preference operations, but are a lower-confidence/lower-likelihood primary cost than loading and rendering icons.
- No icon pack was active in the captured device preferences, so icon-pack XML parsing and external pack drawable resolution do **not** explain this particular run.
- Sorting 29 strings is unlikely to explain a 1.3 s total.
- The completed list is cached only in process memory (`preloadedApps`/`preloadedByKey`). A process death loses resolved `Drawable`s and repeats the whole pipeline. There is no persisted transformed-icon cache.
- `QuestLauncherApplication.onCreate()` starts preload on a single-thread executor. Home startup and metadata scan overlap and may compete for CPU. Before staged hydration, drawer refresh waited for the fully resolved list through `preloadFuture.join()` on its worker and stayed unpopulated until all icons finished. The prototype completes preload with labels/order and placeholders; a separate priority worker hydrates icons.
- The scan overlaps Home startup; it does **not** prove the drawer waited for the full scan. It establishes an expensive cold-process workload and likely startup contention; first usable frame and drawer-ready timing are still unmeasured.

### What remains to confirm

Next validate the staged-hydration prototype on a safe HIT input path. Record at least three cold-process and warm runs, separating Home’s first presented frame, sorted metadata delivery, first populated/focused drawer page, first-page icon completion, and all-icon completion. The current one-run proxy values above are not enough to claim an end-to-end improvement. Verify visible update smoothness and stale-result behavior while changing pages/searching and while invalidating package/icon preferences.

The timing instrumentation is guarded by the debuggable flag. It does not change release scan behavior; `IconShapeRenderer` only takes Palette timestamps when a debug callback is supplied.

## Executive summary

The shared app catalog and most consumer migrations described in
[`shared-app-catalog-plan.md`](shared-app-catalog-plan.md) are now implemented
in the working tree and build successfully. They are not fully device-verified.
In particular, the actual drawer Activity setup/bind/focus path remains
unmeasured and unoptimized; shared caching by itself has not met the user's
instant-opening expectation.

An older, separate exploratory run recorded a fast warm data lookup and an
expensive cold package/icon scan:

- Cold scan of 41 entries: about **2.65 s**
- Time the drawer waited for that scan in the recorded cold-launch test: about **1.85 s**
- Warm drawer data retrieval: about **30 ms**

Those older values (41 entries / 2.65 s / 1.85 s wait / ~30 ms retrieval) are
historical and do not describe the current 32-entry device run. The newer
November 25 measurements above show a 0–1 ms repository lookup, but still
129–181 ms from Activity creation to metadata delivery and 285–367 ms to first
focus. Neither set is a presented-frame benchmark.

The highest-value follow-up areas are:

1. Profile and reduce the drawer's own Activity setup/inflation/bind/focus path.
2. Install and validate the demand-driven hydration build, then measure repeated
   warm/cold click-to-populated-frame latency and frame pacing.
3. Verify shared-catalog package reconciliation and add regression coverage.
4. Profile D-pad, notification, softkey-label, and picker work only after the
   main drawer path has evidence and a repeatable baseline.

## Historical architecture overview (initial review)

- The app is primarily Activity-based. Home is `MainActivity`; the drawer is `AppDrawerActivity`; options and settings are separate screens. `BaseListActivity` provides shared scaffolding for several vertical list screens.
- RecyclerView is used for Home’s shortcut rail, drawer grid/list, favorites, settings and picker lists.
- `AppRepository` is a singleton with a single-thread preload executor, an in-flight preload future, a list cache, and a component-keyed cache. Package-change broadcasts invalidate and restart preload.
- Preferences are stored in `SharedPreferences` through `LauncherPrefs`; writes mostly use `apply()`.
- Background work is generally started with raw `Thread` instances. There is no common lifecycle-aware task abstraction apparent in the reviewed paths.
- App content is inflated synchronously in Activity `onCreate()`. Theme resources set `android:windowAnimationStyle` to `@null`; activity transitions are not an obvious configured animation bottleneck.
- The project has no source test or Android benchmark files in the inspected `app/src/test` and `app/src/androidTest` paths. The Gradle dependencies do not show a benchmark harness. Existing debug instrumentation is elapsed-time logging in the app repository and drawer.

## Findings — original static review (historical; recheck against current tree)

The following findings describe the initial review state. Some have since been
addressed in the working tree (especially duplicate app-list catalog paths and
stale refresh handling); others are unmeasured hypotheses. Do not read them as
claims about the latest build without checking the current code and the status
ledger above.

### 1. App cache was not shared by all app-list screens (addressed in working tree)

**Priority: High for picker visits; high confidence.**

`QuestLauncherApplication.onCreate()` starts `AppRepository.preload()`. `AppRepository.getAllApps()` queries launcher activities, loads labels and icons, resolves icon overrides/packs, applies icon-shape rendering, and sorts the results. `AppDrawerActivity.refresh()` uses `getVisibleApps()`, which can share the preload future and use the cached list.

Several other screens bypass that cached list:

- `AppPickerActivity.loadApps()` calls `getAllApps()` on a new thread.
- `HideAppsActivity.loadApps()` calls `getAllApps()` on a new thread.
- `ActivityPickerActivity` loads the exported activities for the selected package and resolves their labels/icons separately.
- `MainActivity.refreshShortcuts()` resolves each configured component. Main launcher entries may hit the component cache; deep-activity shortcuts can require fresh PackageManager resolution and icon work.

This means warm drawer performance does not ensure warm picker performance. A shared repository snapshot/API for the drawer, App Picker, and Hide Apps should be considered. Package-specific deep-activity results could be cached separately. Cache invalidation must continue to cover package and icon-setting changes.

**Measure:** count full app scans, package queries, icon resolutions, and adapter submissions when each screen is opened once and reopened. Include a deep-activity shortcut.

### 2. Duplicate initial icon-pack load

**Priority: Medium; high confidence.**

`IconPackActivity.onCreate()` calls `load()`, and `onResume()` also calls `load()`. The normal Activity lifecycle invokes `onResume()` after creation, so initial screen entry can start two background icon-pack queries and post two list updates.

Avoid the duplicate initial request or coalesce requests. Also check other resume/result callbacks for overlapping loads.

**Measure:** log request IDs and result submissions during first entry, re-entry, and returning from the picker.

### 3. D-pad repeat can trigger repeated page/focus work

**Priority: High for input feel; high confidence.**

`AppDrawerActivity` handles directional navigation and page keys at Activity level. The reviewed arrow/page paths do not gate navigation on `event.repeatCount`. A held key can therefore trigger repeated page changes or focus moves. At a page boundary this can submit pages, post focus work, and update the highlight repeatedly.

Define the intended held-key behavior—single step, bounded acceleration, or continuous repeat—and implement it explicitly. Coalesce work so the final focus target is not delayed behind obsolete page binds.

**Measure:** record `repeatCount`, `bindPage()` calls, focus requests, and settled focus for a single press versus a held press. Use a frame trace to compare jank.

### 4. Home wheel and highlight animations can be restarted during rapid navigation

**Priority: Medium; medium-high confidence.**

`MainActivity` animates rail movement and focus highlighting separately. Rapid key presses can cancel/restart the wheel animation while the new target is still being moved into position, and can overlap highlight updates. This may make navigation feel laggy even when key dispatch itself is quick.

Consider coalescing to the latest target, shortening duration based on distance, or reducing animation work while input repeats. Preserve current focus semantics and verify both horizontal and vertical rail modes.

**Measure:** count animator starts/cancels per navigation sequence; inspect frame timing while navigating rapidly and while holding a direction.

### 5. Notification recomputation may interfere with UI responsiveness

**Priority: Potentially high during notification bursts; callback-thread impact needs measurement.**

`NotificationCountService.onListenerConnected()`, `onNotificationPosted()`, and `onNotificationRemoved()` call `recompute()` directly. Each recompute obtains the active notifications, rebuilds counts and package sets, constructs notice items, may resolve app labels and load icons, and sorts the full list. This is O(number of active notifications) per callback, with PackageManager/resource work along the way.

The callbacks are synchronous in the service callback path. Whether this stalls the UI thread in the target runtime, and by how much, must be verified with tracing; the code alone is not evidence of a measured UI stall. If expensive, coalesce bursts and move recomputation off the UI-critical path. Cache app labels/icons and avoid rebuilding unchanged notice data where practical.

Home’s notification listener also calls `adapter.notifyItemRangeChanged()` for the entire rail after updates. Prefer updating only rows whose notification state changed if traces show material bind/layout cost.

**Measure:** callback duration, thread identity, active-notification count, and main-thread frame timeline while posting/removing a burst. Also measure Home rail binds per notification change.

### 6. Cold scan and icon rendering still dominate cold drawer readiness

**Priority: High for first open; high confidence.**

`AppRepository.getAllApps()` performs one launcher-intent query, then loads each result’s label and icon and resolves icon presentation before sorting. This is a reasonable query strategy, and a previous optimization removed a redundant launch-intent lookup for each launcher entry. However, icon shaping can still be expensive:

- `IconShapeRenderer` allocates bitmaps for wrapped icons and masks.
- Legacy icon wrapping can draw a 48×48 sample and synchronously generate a Palette color.
- Icon-pack lookup may read mappings and load external package resources.

This work runs on the preload thread, not the UI thread, but it consumes CPU during startup. If the drawer opens before preload completes, `getVisibleApps()` waits for the future on the drawer’s own worker and the screen remains unpopulated until the result arrives.

Potential directions, to be chosen based on profiling:

- Split total scan time into PackageManager query, label/icon loading, icon-pack mapping, shape rendering, and sorting.
- Compare whether beginning preload at process start competes with the first Home frame. Delaying it until after Home’s first frame could improve startup rendering but make an immediate drawer open colder.
- Cache transformed icons in memory using component and icon-setting generations as keys. Only consider disk caching if measurements justify its persistence/invalidation complexity.
- Give the drawer a clear loading state or progressively usable content rather than an empty screen while the full scan completes.

**Measure:** report cold/warm p50 and p95 for first frame, data ready, and focus ready. Compare icon wrapping on/off, icon packs, and a larger installed-app set.

### 7. Search and page binding repeat linear work on the UI thread

**Priority: Medium-to-low at current list sizes; high confidence.**

`AppDrawerActivity.matchingApps()` filters the full app list for each query. Page binding and page counting call it repeatedly. Search text changes rebuild the current results; list mode maps the entire filtered list into `Row` objects on the UI thread. The normal grid is paged, which limits visible cells, but list/search mode still does full-list filtering and row creation.

`AppGridAdapter.submit()` and `ListRowAdapter.submit()` replace their backing arrays and call `notifyDataSetChanged()`. `setGridMetrics()` can also request a full grid rebind when dimensions change. Layout changes that alter page size trigger refresh, so transient size changes around search/IME or orientation deserve checking.

Cache filtered results for the current query and app-data generation, compute page count from the cached result, avoid identical submissions, and only add diff-based updates if measurements justify their focus-management complexity.

**Measure:** filter time, row-construction time, page-bind time, adapter bind count, and frame timing while typing, changing pages, opening/closing search, and rotating.

### 8. Home softkey labels can resolve components synchronously

**Priority: Medium if cache misses occur on UI path; needs call-frequency tracing.**

`MainActivity.keyLabel()`, `leftKeyLabel()`, and `rightKeyLabel()` can call `AppRepository.resolveComponent()` to obtain app labels. That can perform PackageManager and icon resolution when the component is not in the in-memory cache, especially for deep-activity shortcuts. Softkey invalidation and label evaluation occur around Home resume; the exact timing depends on the softkey library’s label evaluation behavior.

Trace label callback duration and thread identity. If it runs on the UI thread and misses are costly, cache labels independently or render a fallback label while resolving asynchronously.

### 9. Icon picker loads icons during binds

**Priority: Low-to-medium; high confidence that the work is synchronous, impact unmeasured.**

`IconGridAdapter.onBindViewHolder()` invokes its `iconLoader`. In `IconPickerActivity`, this loads built-in or external icon-pack drawables. RecyclerView limits work to attached/rebound cells, but scrolling, rebinding, or a large pack can repeat resource lookups on the UI thread.

Profile large packs first. If binding is a frame-time issue, preload/cache drawable resources or immutable bitmap/constant-state representations off the UI path, and bind prepared values.

### 10. Wallpaper thumbnail binds load resources synchronously

**Priority: Low-to-medium; impact depends on asset sizes.**

`WallpaperGridAdapter.onBindViewHolder()` loads the thumbnail drawable synchronously. Applying a selected full wallpaper is already performed on a worker in `WallpaperPickerActivity`; the unmeasured question is the cost of binding and scrolling through bundled thumbnails.

**Measure:** scroll frame time and allocations across the full wallpaper set. Optimize only if resource size/decode work causes missed frames.

### 11. Secondary screens may look unresponsive while async loads complete

**Priority: Medium for perceived responsiveness; high confidence.**

Several screens inflate promptly but populate their lists after background work. App Picker, Hide Apps, Activity Picker, and Icon Picker can therefore show an empty list/dialog gap while loading. This is different from a blocked UI thread, but can still feel like input was ignored.

Add a clear loading/empty state and preserve cached content on re-entry. Ensure focus is moved to the first item when results become available, and do not let an older request overwrite a newer screen state.

### 12. Raw worker threads are not coalesced or lifecycle-aware

**Priority: Medium; high confidence as an architectural risk.**

Several screens create a new `Thread` for each load. `isDestroyed` prevents some posts after destruction, but does not cancel work or prevent an older still-alive Activity request from posting stale results after a newer request. Drawer refreshes and settings reloads can overlap around layout/lifecycle changes.

Use a shared executor or lifecycle-aware jobs with request generations/cancellation. This is primarily a correctness and resource-control improvement; warm-path speed gain is uncertain.

**Measure:** rapid resume, orientation/configuration changes, package invalidation during preload, and repeated navigation away/back. Assert that only the latest result updates the visible screen.

## Measurement and regression plan

### Separate the user-visible milestones

For each screen, record:

1. Input event to Activity launch request.
2. Input event to first rendered frame.
3. Input event to content populated.
4. Input event to focus settled and ready for activation.

Do not treat repository-return time as the same thing as screen readiness.

### Capture cold and warm cases

Measure process-cold and process-warm launches separately. Include:

- Home startup with preload active
- Drawer with app cache cold and warm
- App Picker and Hide Apps on first and repeated entry
- Activity Picker for packages with different activity counts
- Search typing and page flips
- Large icon packs and full wallpaper thumbnail scrolling
- Notification bursts with varying active-notification counts

### Instrumentation and tracing still needed

- Existing debug `QuestPerf` slices cover full scans, metadata query/labels/sort, icon load/shape/Palette, a Home `OnPreDraw` proxy, and drawer metadata/focus/icon-ready milestones. Add detailed bind/search/page/notification timings only if those paths remain in scope.
- Record counters for duplicate/overlapping loads, adapter submissions, page binds, focus requests, and animation restarts where traces identify a concern.
- Capture Perfetto/System Trace or another visible-window frame trace for main-thread stalls, frame deadlines, and first-presented-frame/fully-ready latency. `OnPreDraw` is only a proxy, not proof of presentation.
- Add repeatable on-device cold/warm runs with p50/p95 results; component `QuestPerf` timings do not equal interaction latency.

## Suggested implementation order

1. Validate staged icon hydration: visible placeholder-to-icon transition, ordering, focus, overrides, notifications, and stale-result/cache invalidation.
2. Capture repeatable cold/warm readiness and visible-window frame traces; fix any jank or correctness regressions before widening scope.
3. Reuse app-list cache in App Picker and Hide Apps; remove duplicate Icon Pack loading.
4. Trace notification callback thread/duration and Home softkey label resolution.
5. Define repeat-key behavior and coalesce drawer page/focus and Home animation updates; profile picker scrolling.
6. Add regression checks for cache invalidation, stale result suppression, navigation focus, and measured screen readiness.

## Limits and caveats

- The scan-stage profile is from three process-cold runs on HIT 1857 with Android system caches retained; it is not a pristine-install test or a macrobenchmark.
- Debug APKs containing profiling instrumentation and the staged-hydration prototype were built and installed on 1857. The final hardening build passes and is installed, but was not re-exercised interactively.
- The prototype briefly displays placeholders before icons arrive. Notification-dot Palette cache warming is done on the icon worker and `NotificationDotColor` cache access is synchronized; these follow-ups have build/install verification but no new interactive measurement. The prototype is not yet a verified performance fix: only one exploratory smoke run was captured, with no successful visual/frame-trace or D-pad acceptance pass.
- The timings are specific to one device, installed app set, and current icon preferences.
- Notification processing and softkey label resolution are plausible risks, not proven jank sources until thread/timing traces are captured.
- UI-thread bind costs for icon and wallpaper resources are real code paths, but their user-visible impact depends on the resources and hardware.
