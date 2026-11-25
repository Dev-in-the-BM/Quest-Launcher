# Current task: performance and responsiveness review

**Status:** Shared-catalog and package-event work is implemented in the working tree, but the drawer-open path has **not** been adequately cleaned up or verified. The user reported the drawer was slower. Logs from the installed build show an unnecessary startup sweep hydrating all 32 icons over ~1.6 s. Warm metadata lookup itself took 0–1 ms, but metadata was delivered 129–181 ms after Activity creation and first focus was 285–367 ms; these are not presented-frame timings. A mitigation removes startup/all-catalog icon sweeps and defers remaining drawer icon hydration until its initial page is ready. That build passes but is **not installed or device-verified**; the drawer was left foreground rather than interrupting the user's test.

**Architecture direction:** See `docs/shared-app-catalog-plan.md`. Immediate priority is measuring and reducing actual drawer Activity setup/inflation/bind time, then testing the mitigation on device. Other gaps include process-death persistence assessment, repository regression tests, and package-event device verification.

## Goal and scope

Determine why cold startup/drawer readiness is slow, identify which work dominates, and improve perceived readiness without changing icon appearance, order, focus, or cache correctness. The broader review also covers:

- D-pad and other input responsiveness
- App drawer/menu loading
- Home and secondary-screen launch/readiness
- Background work that can interfere with UI frames
- Repeatable performance measurement and regression coverage

## What is complete

- Static review of startup/preload, drawer, secondary app-list screens, input/navigation, notifications, picker binds, and available performance instrumentation.
- On-device profiling on HIT 1857, including process-cold scans and a shape-disabled A/B. Full measurements, code references, interpretation, caveats, and next-stage validation are in `docs/performance-responsiveness-review.md`.
- Debug-only scan-stage timers added to `app/src/main/java/quest/devinthebm/launcher/data/AppRepository.kt` and `app/src/main/java/quest/devinthebm/launcher/data/IconShapeRenderer.kt`. They report PackageManager query, labels, platform icon loads, adaptive/legacy icon resolution, legacy Palette generation, sorting, counts, and total time. They are guarded by `FLAG_DEBUGGABLE`; the Palette timer is only active when its callback is supplied.
- `./gradlew :app:assembleDebug` passed for the latest demand-driven hydration and drawer-timing-instrumentation build. Its APK is built but not installed on 1857 because the drawer was the foreground activity; the currently installed APK still has eager startup icon hydration.
- The shape-disabled A/B temporarily changed only `icon_shape=none`. The original `flip_launcher_prefs.xml` was restored byte-for-byte and verified with `cmp`. Original foreground `com.along.anote/.NoteEditActivity` and awake state were restored after testing.
- The device was HIT 1857 (Android 8.1/API 27, 240×320 physical at 120 dpi), with 29 launcher entries, no active icon pack, default circle shape, wrapping on, and legacy backgrounds on.

## Prototype implementation

- `AppRepository.preload()` now builds a sorted metadata catalog (labels + retained `ResolveInfo`) without loading every app icon. The drawer receives placeholder-backed `AppInfo` records from `getVisibleApps()`.
- `requestIcons()` uses a single priority-ordered worker. The currently displayed drawer page is queued first; remaining drawer icons are queued only after the initial page resolves. A later page request promotes matching queued work.
- Successfully resolved icons are cached by component key and update the partial repository catalog. Cache invalidation clears pending subscribers and queued jobs; generation checks prevent an in-flight old scan/icon load from repopulating the new cache.
- `AppDrawerActivity` binds metadata and requests icons asynchronously, updating only the matching row as each result arrives. It records metadata-ready, first-focus, first-page-icons-ready, and all-icons-ready milestones. `AppGridAdapter` updates individual cells; list mode updates one row.
- Softkey/settings label-only paths now use `resolveLabel()` so a missing icon does not force synchronous icon resolution merely to show text. Full `resolveComponent()` continues returning a real icon for shortcut/rail consumers.
- Icon hydration warms the notification-dot Palette cache on the background worker before publishing an icon; `NotificationDotColor` cache access is synchronized to avoid races with binds. This avoids moving its sampling work into a row/focus callback, while adding it to background hydration latency.
- The Application starts metadata preload while Home is visible. The short-lived 300 ms deferral experiment was removed because it could make an early drawer open wait for the catalog.
- `AppRepository.getCachedVisibleApps()` returns a ready in-memory snapshot without PackageManager work. `AppDrawerActivity.refresh()` now uses it synchronously on the UI thread when available, so warm drawer entry skips thread creation and main-queue delivery; uncached/cold paths retain the background fallback.
- `AppRepository.getSnapshot()` exposes an immutable generation + app-list view. `getAllApps()` now reuses that same metadata catalog and no longer performs its own full icon scan. Startup no longer schedules an all-app icon sweep; App Picker and Hide Apps request icons as their screens open and update individual rows when ready. Activity Picker results are cached per package and cleared on package invalidation.
- Package added/removed/changed/replaced events are coalesced over 400 ms. Known package changes replace only that package's metadata/`ResolveInfo`/icons; overlapping in-flight changes accumulate and stale passes retry. A missing package URI, cold catalog, or package-query failure falls back to full preload.
- `observeCatalog()` publishes snapshots on the main thread. Drawer, App Picker, Hide Apps, Home shortcut rail, Favorites, Shortcut Config, and Activity Picker subscribe while active and reject older generations; icon-only preference changes now advance/publish a generation and leave visible consumers to request only needed icons.
- After the slower-drawer report, startup and cache invalidation no longer queue all icons. The drawer hydrates its visible page first; remaining icons are queued only after that page is ready. Added a populated-content pre-draw timing proxy, but it has not yet been observed on device.
- A non-exported drawer cannot be launched directly by `adb am start`. Attempting to inspect Home surfaced an unrelated `com.google.android.gms` crash dialog (`co.g.App` missing); no Quest Launcher crash was recorded.
- **Not done:** no systematic cleanup of `AppDrawerActivity.onCreate()`/layout inflation/adapter binding/focus path; no captured click-to-first-populated-frame or frame trace; no repeated warm/cold drawer benchmark; no device verification of the demand-driven mitigation; no package install/update/uninstall test; no repository regression tests; no persisted snapshot.

## Evidence and conclusions

### Instrumented baseline (process-cold; system caches retained)

- Normal shape settings, 29 entries: **1233, 1352, 1257 ms** total.
- Stage ranges: query **35–43 ms**; labels **230–253 ms**; platform icons **509–531 ms**; icon resolve/render **418–495 ms**; sort **17–18 ms**.
- Mix: **14 adaptive**, **15 legacy**. Adaptive resolve **55–61 ms**; legacy resolve **363–435 ms**; Palette generation inside the legacy path **281–337 ms**.
- Shape disabled (`icon_shape=none`), three runs: **824, 834, 840 ms**. Icon resolve fell to **43–50 ms**, Palette to **0 ms**; platform icon loads remained **474–478 ms**, labels **221–248 ms**.
- Conclusion: rendering/transformation adds about **0.38–0.45 s**, with Palette the largest measured sub-cost. Platform icon loading (~0.5 s) and label loading (~0.24 s) remain after removing shaping. The query and sorting are not worthwhile first targets.

### Important qualifications

- Three force-stopped process-cold scans do not clear Android PackageManager/resource caches. They are not pristine-install cold-start results, nor a randomized benchmark.
- Earlier `am start -W MainActivity` reported **1543 ms** and a non-instrumented scan of **1295 ms** for 29 entries. Drawer retrieval logs were **31, 53, 133, 55 ms** across entries/rebinds. These are component timings, not first-frame/page-ready latency.
- Prototype smoke run on 1857 (installed debug build, process force-stopped, then `MainActivity` started): `am start -W` reported **1649 ms**; the `OnPreDraw` proxy logged **765 ms from Activity onCreate** (not a guaranteed presented-frame time), catalog metadata work took about **495 ms** (query 30 ms, labels 244 ms, sort 28 ms), drawer metadata was ready **536 ms from drawer creation** (**60 ms** for the request after entering), first-page focus settled at **1108 ms**, first-page icons were ready at **1582 ms**, and all visible-catalog icons were ready at **2378 ms**. These numbers are from one exploratory run and are not directly comparable to the earlier scan-only profile.
- Icon hydration logs showed icons loading asynchronously after metadata delivery. Several first icons took around 65–156 ms each; the slowest single icon in this run took about 352 ms. This supports prioritizing visible items, but the run is too small to establish stable latency.
- Notification-dot cache warming was added after this smoke run; its impact on worker completion and readiness milestones is not yet measured.
- The device’s HIT key utility still redirected/intercepted key activity; a `com.hit.keys` hotkey path crashed in its own `CmdActionMenu` with an invalid menu category. The launcher emitted its drawer metrics, but no reliable D-pad test or drawer screenshot/visual parity confirmation was obtained. Do not attribute that utility crash to Quest Launcher.
- The 8-frame `gfxinfo` sample (6 janky) is too small for conclusions; a larger historical sample was from hidden launcher windows.
- Injected MENU/D-pad events were handled by system apps on this HIT build, so navigation timing was invalid. Notification callback duration/thread and picker scrolling remain unmeasured.
- The device scan confirms expensive work; it does not yet prove a specific first usable frame or a UI-thread stall. All numerical values are device/catalog/configuration-specific.

## Current working-tree state

- Profiling changes remain in `AppRepository.kt` and `IconShapeRenderer.kt`; staged hydration changes are in `AppRepository.kt`, `AppInfo.kt`, `AppDrawerActivity.kt`, and `ui/AppGridAdapter.kt`. Label-only call sites changed in `MainActivity.kt` and `SettingsCategoryActivity.kt`. Keep the debug timers while validating so A/B stages remain observable.
- `docs/performance-responsiveness-review.md` is the full findings and evidence record.
- `status.md` is this exact task ledger; `PROGRESS.md` is intentionally just the upcoming-work summary.
- No notification optimization, general cache redesign, D-pad behavior change, or release build has been made. The prior broad review findings remain hypotheses/opportunities, not proven bottlenecks.
- The repository also contains earlier project history; do not discard unrelated existing work while maintaining `PROGRESS.md` as a summary of upcoming tasks.

## Next work: verify drawer first frame

Measure a physical/tap-opened drawer and verify the new warm-cache fast path:

1. Use a safe physical/tap input path to open the drawer; preserve/restore foreground and awake state.
2. Record click-to-first-populated-frame and compare warm versus first/cold entry. The Activity/window inflation still has OS cost; isolate that from metadata delivery.
3. Capture a visible-window frame trace, then verify icon parity, focus/activation/page changes, notification dots, grid/list, and stale-result invalidation.

### Acceptance/measurement for the prototype

- On 1857, repeat process-cold and warm runs (at least 3 each, same 29-app catalog) and report separately: Home first frame, sorted metadata ready, first drawer page populated/focused, first-page icons ready, and all icons ready.
- Capture a longer visible-window Perfetto/System Trace or another valid frame trace. Do not use hidden-window historical `gfxinfo` totals or injected D-pad events that the HIT utility intercepts.
- Compare against the current baseline and verify visual icon parity, stable ordering, focus/activation, page changes during loading, and stale-result behavior.
- Only proceed to broader cache reuse, notification tracing, D-pad tuning, or picker scrolling after this critical path is measured; these remain follow-up work.
