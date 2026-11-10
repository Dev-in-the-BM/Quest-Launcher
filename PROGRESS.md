## Current state

- Restored the historical open crescent offsets in `MainActivity.updateWheelCurve()` and flattened the closed crescent slightly (center +9dp, outer -5dp); retained wheel depth/selection animation.
- Aligned the collapsed rail's initial XML translation with its runtime offset.
- Moved the app-grid control into Yapchik's bar row rather than over it.
- Disabled rail child clipping so the expanded center icon and its halo can extend past the rail's right edge.
- On-device follow-up found the grid icon can lose focus after Home's rail data/layout pass. The closed rail now centers on its middle shortcut and Home reclaims grid focus after those passes. On the TT290, UI Automator confirmed the grid `ImageView` has focus and its spotlight is visible both on launch and after opening/closing All Apps.
- `./gradlew :app:assembleDebug` and `git diff --check` pass. Installed the APK on a wirelessly connected TT290; screenshots confirmed the grid action in Yapchik's bar and the expanded wheel center icon fully visible after a swipe and D-pad navigation. Restored the prior launcher and sleep state afterward.

## Theme integration

- Added a Settings theme picker for KaiOS (the default and fallback) and Material 2 (`material2`), with the choice stored durably in `LauncherPrefs` and applied before each Activity inflates its views.
- Mapped Material 2 light/night colors and component presentation across Home, lists/settings, app drawer, search, dialogs, popups, and Yapchik softkeys. The Home layout stays wallpaper-backed in KaiOS and uses a solid Material 2 surface in that theme.
- Material 2 focus uses a contrasting neutral-grey fill around the focused row/icon; app icons keep their source colors and focus has no outline frame. KaiOS focus resources remain unchanged.
- `./gradlew assembleDebug test` and `git diff --check` pass (`test` has no source tests). Installed and visually checked on a DOOV device; restored the prior default theme preference, notification preference, Main Activity, and awake state.
