## Current state

- Restored the historical open crescent offsets in `MainActivity.updateWheelCurve()` and flattened the closed crescent slightly (center +9dp, outer -5dp); retained wheel depth/selection animation.
- Aligned the collapsed rail's initial XML translation with its runtime offset.
- Moved the app-grid control into Yapchik's bar row rather than over it.
- Disabled rail child clipping so the expanded center icon and its halo can extend past the rail's right edge.
- On-device follow-up found the grid icon can lose focus after Home's rail data/layout pass. The closed rail now centers on its middle shortcut and Home reclaims grid focus after those passes. On the TT290, UI Automator confirmed the grid `ImageView` has focus and its spotlight is visible both on launch and after opening/closing All Apps.
- `./gradlew :app:assembleDebug` and `git diff --check` pass. Installed the APK on a wirelessly connected TT290; screenshots confirmed the grid action in Yapchik's bar and the expanded wheel center icon fully visible after a swipe and D-pad navigation. Restored the prior launcher and sleep state afterward.
