# Launcher themes

Quest Launcher ships KaiOS and Material 2 presentations in the same APK. The
KaiOS presentation remains the default for existing installs and for unknown
stored IDs. Users choose a presentation in **Launcher Settings → Theme**.

## Architecture

- `theme/LauncherTheme.kt` owns `ThemeId`, the typed catalog/descriptors,
  component variants, selection resolution, and the `ThemedActivity` lifecycle
  adapter.
- `LauncherPrefs` stores the stable ID in the existing
  `flip_launcher_prefs` preferences file under `launcher_theme_id`. Writes are
  synchronous (`commit()`); Activities recreate only after persistence
  succeeds. A missing or unrecognized ID resolves to `kaios` without changing
  unrelated preferences.
- Themed Activities apply the catalog style before `super.onCreate()` and
  refresh stale back-stack instances when they resume after a theme change.
- Android resources are the rendering layer. Shared semantic attributes in
  `res/values/attrs.xml` map to KaiOS or Material 2 resources in
  `res/values/themes.xml`; Material 2 colors have light and `values-night`
  variants. Keep complete theme styles in the unqualified resource set—put
  palette/system-bar differences in qualified color/bool resources rather than
  shadowing the theme with a partial night style.

## Presentation behavior

- KaiOS keeps its existing wallpaper, accent, and focus behavior.
- Material 2 uses the device's day/night configuration, a solid Home surface,
  Material 2 semantic colors, and themed list/drawer/search/dialog/popup and
  Yapchik softkey presentation.
- Material 2 focus is a neutral-grey shade behind the selected control or app
  icon, with a stronger shade for pressed state. Do not tint/recolor app-owned
  icons, and do not draw a focus frame. KaiOS focus remains on its existing
  resource path.

## Verification

```bash
./gradlew assembleDebug test
git diff --check
```

The app has no unit-test sources currently, so Gradle reports the unit-test
tasks as `NO-SOURCE`. For UI changes, install the debug APK with
`adb install -r app/build/outputs/apk/debug/app-debug.apk`; this updates the
existing installation without clearing app data. Verify theme persistence
after a process restart, both day and night palettes, focus/pressed states,
and the KaiOS default path. Do not change the device's default Home app as part
of theme verification.
