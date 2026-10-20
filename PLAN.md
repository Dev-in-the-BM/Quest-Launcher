## Home UI approach

- Use the pre-carousel crescent formula from commit `a782350^` for the open wheel, keyed to each visible row's distance from the rail center; flatten only the closed offsets to expose the outer icons slightly more.
- Shift the collapsed 84dp rail left by 50dp so its 54dp center icon is about half visible.
- Add the focusable app-grid `ImageView` as the middle child of Yapchik's label row; reattach after Yapchik recreates its bar.
- Permit rail children to draw beyond its right edge so the expanded middle icon and focus halo aren't clipped.
- Center the visible five-icon rail on index 2 whenever collapsed, and request the grid focus after every rail data/layout pass so RecyclerView cannot reclaim it.
- Build with `./gradlew :app:assembleDebug` and visually verify on a device when available.
