## Upcoming tasks

- **First priority: actual drawer-open path.** Inspect and time `AppDrawerActivity` setup/inflation, adapter submission, focus scheduling, and first populated pre-draw separately from repository lookup. Remove unnecessary open-path work; do not treat metadata-ready or pre-draw as a presented-frame benchmark.
- Install and test the demand-driven hydration build only when safe to interrupt the currently foreground drawer; compare repeated warm/cold opens and capture visible frame timing.
- Verify icon appearance, ordering, focus/activation, page navigation, grid/list/search, and stale results with the mitigation installed.
- Add repository regression coverage and verify package install/update/uninstall reconciliation on device.
- Verify incremental updates preserve icon overrides/shapes, notification dots, grid/list behavior, and cache/request generations without rebinding the whole grid per icon.
- Remove duplicate Icon Pack loading; coalesce/cancel stale refreshes.
- Verify whether notification recomputation and Home softkey label resolution cause main-thread stalls; optimize only if traces confirm the cost.
- Rerun held-D-pad and frame-time checks using a physical/verified input path; injected keys on HIT 1857 were intercepted by its key-mapping utility. Profile icon-picker and wallpaper-picker scrolling if time permits.

Implementation state, the drawer slowdown evidence, unverified mitigation, caveats, and next steps are documented in `status.md` and `docs/performance-responsiveness-review.md`.
