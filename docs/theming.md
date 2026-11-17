# Launcher themes

Quest Launcher ships KaiOS, Material 2, and Aeris presentations in the same
APK. The KaiOS presentation remains the default for existing installs and for
unknown stored IDs. Users choose a presentation in **Options → Appearance →
Theme**.

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
  `res/values/attrs.xml` map to each theme's resources in
  `res/values/themes.xml`; Material 2 and Aeris colors have light and
  `values-night` variants. Keep complete theme styles in the unqualified
  resource set—put palette/system-bar differences in qualified color/bool
  resources rather than shadowing the theme with a partial night style.

## Presentation behavior

- Both Home presentations show the device wallpaper behind the clock and
  shortcuts, with a window-level scrim for legibility. The app drawer also
  shows the wallpaper behind its grid with a translucent overlay.
  `MainActivity` and `AppDrawerActivity` select window-specific styles at
  runtime because `ThemedActivity` otherwise replaces manifest themes;
  preserve `windowShowWallpaper` in those styles.
- KaiOS keeps its existing wallpaper, accent, and focus behavior.
- Material 2 uses the device's day/night configuration, a transparent Home
  surface over the wallpaper,
  Material 2 semantic colors, and themed list/drawer/search/dialog/popup and
  Yapchik softkey presentation.
- The Material 2 light Home surface is standard white. Home clock, date, and
  notification-count text use no drop shadow in this presentation; the KaiOS
  wallpaper-oriented shadows remain unchanged. Keep foreground styling suited
  to its surface so text remains crisp and legible.
- Material 2 focus is a neutral-grey shade behind the selected control or app
  icon, with the app-drawer highlight extending 4dp beyond the icon frame on
  each side to give icons breathing room. Do not tint/recolor app-owned icons,
  and do not draw a focus frame. KaiOS focus remains on its existing resource
  path.
- Aeris uses cool-neutral surfaces and Google blue (`#0B57D0` light,
  `#A8C7FA` dark), while retaining wallpaper-backed Home and drawer behavior.
  Its focused/selected states deliberately use the same neutral colors as M2:
  `#E0E0E0` light and `#484A4D` dark. Pressed feedback likewise matches M2:
  `#C4C7C5` light and `#5F6368` dark. Aeris changes geometry, not focus color:
  app tiles have 18dp corners, list rows 12dp, popup/dialog surfaces 20dp,
  search fields 14dp, and utility rows a 56dp minimum height. Screen titles are
  28sp. These are Aeris-only resources and styles; do not alter M2 tokens or
  KaiOS styling to implement Aeris.
- The Home rail uses a separate Aeris focus fill with 50% opacity because its
  selected surface extends over wallpaper beyond the icon and otherwise reads
  much darker than the drawer focus in device screenshots. Its focus tile uses
  18dp corners and extends about 10dp beyond the rail icon, matching the
  drawer tile's breathing room as closely as the tighter rail allows. Keep
  this a focused-item surface, not a decorative halo.

When changing theme colors or foreground treatment, follow Android's
[color guidance](https://developer.android.com/design/ui/mobile/guides/styles/color):
use semantic color roles and check foreground/background contrast.

## Verification

```bash
./gradlew assembleDebug test
git diff --check
```

The app has no unit-test sources currently, so Gradle reports the unit-test
tasks as `NO-SOURCE`. Aeris was built and installed on the attached HIT device;
the light-mode App Drawer and focused app tile were inspected over ADB. Dark
mode and the full theme-switch/persistence matrix still need separate
verification. For UI changes, install the debug APK with
`adb install -r app/build/outputs/apk/debug/app-debug.apk`; this updates the
existing installation without clearing app data. Verify theme persistence
after a process restart, both day and night palettes, focus/pressed states,
and the KaiOS default path. Do not change the device's default Home app as part
of theme verification.
