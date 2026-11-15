## Home UI approach

- Use the pre-carousel crescent formula from commit `a782350^` for the open wheel, keyed to each visible row's distance from the rail center; flatten only the closed offsets to expose the outer icons slightly more.
- Shift the collapsed 84dp rail left by 50dp so its 54dp center icon is about half visible.
- Add the focusable app-grid `ImageView` as the middle child of Yapchik's label row; reattach after Yapchik recreates its bar.
- Permit rail children to draw beyond its right edge so the expanded middle icon and focus halo aren't clipped.
- Center the visible five-icon rail on index 2 whenever collapsed, and request the grid focus after every rail data/layout pass so RecyclerView cannot reclaim it.
- Build with `./gradlew :app:assembleDebug` and visually verify on a device when available.

## Drawer / theme follow-up

- Use AppCompat `PopupMenu` so the activity's KaiOS or Material 2 popup theme is honored; KaiOS popup surface and focus selector are explicit resources.
- Apply Material 2 focus inset at the moving highlight bounds, preserving the existing icon-scale tracking.
- Keep the T9 letter overlay inside drawer content and leave the grid's weighted layout height unchanged when search is shown.
- Make Home's app-grid insertion idempotent across Yapchik bar refreshes and set the favorites rail bottom margin from the measured softkey bar height.
- Verify with `./gradlew assembleDebug`; install to the attached TT290 only after checking foreground and restore Home afterward if install changes it.

## Popup / T9 correction pass

- T9 overlay: large single-letter badge, centered horizontally and anchored near the bottom but above Yapchik's measured bar; remove/reuse the prior badge on rapid key presses.
- KaiOS popup container and focused row should use the same generous corner language as KaiOS settings (`bg_item_focus`, 18dp radius), not the current 4dp / 3dp treatment.
- Material 2 popup focus must be a true inset list selector: neutral settings-focus fill when focused/selected, stronger neutral pressed fill, 2dp corners consistent with `m2_row_focus`, and no focus frame. Configure AppCompat's dropdown list selector, not just the app-grid highlight.
- Verify build and, if TT290 remains attached, inspect foreground/wakefulness before install; update progress with the exact result.
