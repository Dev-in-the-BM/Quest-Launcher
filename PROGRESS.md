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

## Current drawer / softkey follow-up (Nov 10, 2026)

- **Status: implemented; latest debug build passes.** UI behavior still needs visual confirmation for both themes.
- App drawer uses the left softkey for focused-app Options and right softkey for Search (Close search while active). Options popup is anchored to the left Yapchik label.
- Popup now uses AppCompat `PopupMenu` and theme-specific popup backgrounds; KaiOS has a dark surface, accent outline and accent focus selector. Material 2 uses the existing elevated-surface popup background.
- Material 2 app-grid focus uses an inset highlight. T9 letter feedback overlays the drawer grid and remains visible about 2.4 seconds before fading.
- Search field now overlays the grid in the drawer FrameLayout, avoiding a change to grid height or icon positions.
- Home inserts the app-grid view idempotently and measures the softkey bar to reserve bottom space for the favorites wheel; bar background remains transparent while the grid SpotlightDrawable is unchanged.
- Latest drawer changes are built and installed on TT290; see the correction-pass status below. No visual screenshot pass has yet been performed for KaiOS/Material 2 popup or inset focus.

## Current correction pass (Nov 10, 2026)

- **Status: latest refinements implemented, built, and installed.** User says the T9 overlay is still twice as big as intended and KaiOS options popup should have no colored border.
- Confirmed project theming guidance in `docs/theming.md`: Material 2 focus uses neutral gray selected fill, stronger neutral pressed fill, no frame. `m2_row_focus.xml` uses 2dp corners. Existing popup currently has no Material 2-specific inset selector; previous inset work affected the grid highlight only.
- T9 badge was initially enlarged from 72dp/32sp to 144dp/76sp, then reduced to 128dp/68sp; this latest request halves it to 64dp/34sp, still horizontally centered and bottom anchored. Bottom margin accounts for measured softkey overlap plus 18dp; repeated keys replace the prior overlay. KaiOS popup background keeps its 18dp settings-matched radius but its accent-color stroke has been removed. Material 2 AppCompat dropdown list uses an inset popup selector (8dp horizontal, 3dp vertical) with documented neutral focus/pressed colors and 2dp corners.
- `./gradlew assembleDebug` and `git diff --check` pass. Latest `app/build/outputs/apk/debug/app-debug.apk` installed successfully on TT290 serial `MTK0002608041913160`; `pm path` confirms installation. The phone was awake with `dev.alenajam.opendialer/.ui.DialerActivity` foreground before and after, so it was left undisturbed. Popup/T9 visual verification remains outstanding.
